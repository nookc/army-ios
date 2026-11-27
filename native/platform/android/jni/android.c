/*
 * android.c - lop nen tang Android cho client Army2.5.0 dich san (JNI <-> runtime C).
 *
 * Luong:
 *  - luong UI Android: ve khung (copyFrame), cham/phim (post), hop nhap chu (textDone) - KHONG dung GC
 *  - luong "game" (pthread rieng, tao o start): khoi tao GC + chay game Java dich san (j2n_run, khong tra ve)
 *  - goi nguoc len Java (am thanh, hop nhap chu, mo link...) tu luong game: tu gan luong vao JVM,
 *    tu go ra khi luong ket thuc (pthread_key)
 */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <unwind.h>
#include "platform.h"

/* dung chung cho nhieu game: ban Avatar build voi -DJ2N_JNI_CLASS=vn_avatar_diemky_Native -DJ2N_JNI_PATH=... */
#ifndef J2N_JNI_CLASS
#define J2N_JNI_CLASS vn_army250_thuan_Native
#define J2N_JNI_PATH "vn/army250/thuan/Native"
#endif
#ifndef J2N_MIDLET
#define J2N_MIDLET "coreLG.TerrainMidlet"
#endif
#ifndef J2N_JAR_ASSET
#define J2N_JAR_ASSET "Army250.jar"
#endif
#ifndef J2N_TAG
#define J2N_TAG "Army250"
#endif
#define JCAT_(a, b) a##_##b
#define JCAT(a, b) JCAT_(a, b)
#define JNIFN(n) JCAT(JCAT(Java, J2N_JNI_CLASS), n)

#define TAG J2N_TAG

#if defined(__arm__) || defined(__i386__)
/* Boehm GC dung tkill() tren Android 32 bit API < 23 (USE_TKILL_ON_ANDROID) nhung libc API 21 cua NDK
   moi khong con xuat ham nay: tu cung cap bang syscall */
#include <sys/syscall.h>
int tkill(int tid, int sig) { return (int)syscall(__NR_tkill, tid, sig); }
#endif

static JavaVM *gVm;
static jclass gBridge;                 /* J2N_JNI_PATH (vn/army250/thuan/Native) */
static jmethodID mShowText, mSndCreate, mSndStart, mSndStop, mSndClose, mSndVolume, mOpenUrl, mSms, mVibrate, mExit;
static pthread_key_t gEnvKey;
static AAsset *gJarAsset;
static const uint8_t *gJar;
static size_t gJarLen;
static char gDataDir[1024];
static int gW = 320, gH = 466;
static int gStarted;

/* khung trinh bay (kich thuoc theo khung moi nhat: man phong to cua Avatar lon hon gW x gH) */
static uint32_t *gFront;
static int gFrontW, gFrontH;
static size_t gFrontCap;
static pthread_mutex_t gFrontMu = PTHREAD_MUTEX_INITIALIZER;
static volatile int gFrameNo;

/* nhat ky */
static FILE *gLog;
static int gLogFd = -1, gCrashFd = -1;
static pthread_mutex_t gLogMu = PTHREAD_MUTEX_INITIALIZER;

/* ============================================================ JNI tien ich */
static void env_detach(void *p) {
    (void)p;
    if (gVm) (*gVm)->DetachCurrentThread(gVm);
}

static JNIEnv *env(void) {
    JNIEnv *e = 0;
    if ((*gVm)->GetEnv(gVm, (void *)&e, JNI_VERSION_1_6) == JNI_OK) return e;
    JavaVMAttachArgs a = {JNI_VERSION_1_6, "Army2Game", 0};
    if ((*gVm)->AttachCurrentThreadAsDaemon(gVm, (void *)&e, &a) != JNI_OK) return 0;
    pthread_setspecific(gEnvKey, (void *)1);   /* go ra khi luong ket thuc */
    return e;
}

static jstring jstr(JNIEnv *e, const char *s) {
    /* NewStringUTF can UTF-8 "modified" (ky tu ngoai BMP se hong): tu doi UTF-8 -> UTF-16 */
    if (!s) s = "";
    size_t n = strlen(s);
    jchar *u = (jchar *)malloc((n + 1) * sizeof(jchar));
    size_t k = 0;
    const unsigned char *p = (const unsigned char *)s;
    while (*p) {
        uint32_t c = *p++;
        int more = c >= 0xF0 ? 3 : c >= 0xE0 ? 2 : c >= 0xC0 ? 1 : 0;
        if (c >= 0x80 && c < 0xC0) c = 0xFFFD;       /* byte le */
        else if (more) c &= (0x3F >> more);
        for (int i = 0; i < more && (*p & 0xC0) == 0x80; i++) c = (c << 6) | (*p++ & 0x3F);
        if (c >= 0x10000) {
            c -= 0x10000;
            u[k++] = (jchar)(0xD800 + (c >> 10));
            u[k++] = (jchar)(0xDC00 + (c & 0x3FF));
        } else {
            u[k++] = (jchar)c;
        }
    }
    jstring r = (*e)->NewString(e, u, (jsize)k);
    free(u);
    return r;
}

static void clear_exc(JNIEnv *e) {
    if ((*e)->ExceptionCheck(e)) {
        (*e)->ExceptionDescribe(e);
        (*e)->ExceptionClear(e);
    }
}

static jmethodID mWantKb;

static void kb_hook(int show, int area_bottom) {
    JNIEnv *e = env();
    if (!e || !mWantKb) return;
    (*e)->CallStaticVoidMethod(e, gBridge, mWantKb, (jint)show, (jint)area_bottom);
    clear_exc(e);
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    gVm = vm;
    pthread_key_create(&gEnvKey, env_detach);
    JNIEnv *e = 0;
    if ((*vm)->GetEnv(vm, (void *)&e, JNI_VERSION_1_6) != JNI_OK) return -1;
    jclass c = (*e)->FindClass(e, J2N_JNI_PATH);
    if (!c) return -1;
    gBridge = (jclass)(*e)->NewGlobalRef(e, c);
    mShowText = (*e)->GetStaticMethodID(e, c, "showTextBox", "(Ljava/lang/String;Ljava/lang/String;II)V");
    mSndCreate = (*e)->GetStaticMethodID(e, c, "soundCreate", "([BLjava/lang/String;)I");
    mSndStart = (*e)->GetStaticMethodID(e, c, "soundStart", "(II)V");
    mSndStop = (*e)->GetStaticMethodID(e, c, "soundStop", "(I)V");
    mSndClose = (*e)->GetStaticMethodID(e, c, "soundClose", "(I)V");
    mSndVolume = (*e)->GetStaticMethodID(e, c, "soundVolume", "(II)V");
    mOpenUrl = (*e)->GetStaticMethodID(e, c, "openUrl", "(Ljava/lang/String;)Z");
    mSms = (*e)->GetStaticMethodID(e, c, "sms", "(Ljava/lang/String;Ljava/lang/String;)Z");
    mVibrate = (*e)->GetStaticMethodID(e, c, "vibrate", "(I)V");
    mExit = (*e)->GetStaticMethodID(e, c, "exitApp", "()V");
    clear_exc(e);
    /* Avatar: cau noi co wantKeyboard(II)V -> game xin ban phim dien thoai khi vao o go chu (ban Army khong co: bo qua) */
    mWantKb = (*e)->GetStaticMethodID(e, c, "wantKeyboard", "(II)V");
    if ((*e)->ExceptionCheck(e)) {
        (*e)->ExceptionClear(e);
        mWantKb = 0;
    }
    if (mWantKb) j2n_set_keyboard_hook(kb_hook);
    return JNI_VERSION_1_6;
}

/* ============================================================ nhat ky + bat loi sap */
void plat_log(const char *s) {
    __android_log_write(ANDROID_LOG_INFO, TAG, s);
    pthread_mutex_lock(&gLogMu);
    if (gLog) {
        time_t now = time(0);
        struct tm tm;
        localtime_r(&now, &tm);
        fprintf(gLog, "%02d/%02d %02d:%02d:%02d %s\n", tm.tm_mday, tm.tm_mon + 1, tm.tm_hour, tm.tm_min, tm.tm_sec, s);
        fflush(gLog);
    }
    pthread_mutex_unlock(&gLogMu);
}

static void raw_write(int fd, const char *s) {
    if (fd < 0) return;
    size_t n = strlen(s);
    while (n > 0) {
        ssize_t w = write(fd, s, n);
        if (w <= 0) return;
        s += w;
        n -= (size_t)w;
    }
}

void plat_log_raw(const char *s) {
    __android_log_write(ANDROID_LOG_WARN, TAG, s);
    raw_write(gLogFd, s);
    raw_write(gLogFd, "\n");
}

typedef struct bt_state {
    char *buf;
    size_t cap, len;
    int n;
} bt_state;

static _Unwind_Reason_Code bt_cb(struct _Unwind_Context *ctx, void *arg) {
    bt_state *st = (bt_state *)arg;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (!pc || st->n >= 40) return _URC_END_OF_STACK;
    Dl_info di;
    const char *lib = "?", *sym = "";
    uintptr_t off = 0;
    if (dladdr((void *)pc, &di) && di.dli_fname) {
        lib = strrchr(di.dli_fname, '/') ? strrchr(di.dli_fname, '/') + 1 : di.dli_fname;
        off = pc - (uintptr_t)di.dli_fbase;
        if (di.dli_sname) sym = di.dli_sname;
    }
    if (st->len < st->cap) {
        int k = snprintf(st->buf + st->len, st->cap - st->len, "  #%02d %s+0x%lx %s\n", st->n, lib, (unsigned long)off, sym);
        if (k > 0) st->len += (size_t)k;
    }
    st->n++;
    return _URC_NO_REASON;
}

static struct sigaction gOld[32];

static void on_crash(int sig, siginfo_t *si, void *uc) {
    (void)uc;
    char b[4096];
    int k = snprintf(b, sizeof b, "\n=== SAP: tin hieu %d ma %d dia chi %p luc %ld\n", sig, si ? si->si_code : 0, si ? si->si_addr : 0,
                     (long)time(0));
    bt_state st = {b + k, sizeof b - (size_t)k, 0, 0};
    _Unwind_Backtrace(bt_cb, &st);
    raw_write(gCrashFd, b);
    raw_write(gLogFd, b);
    __android_log_write(ANDROID_LOG_FATAL, TAG, b);
    sigaction(sig, &gOld[sig], 0);
    raise(sig);
}

static void crash_install(void) {
    int sigs[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT};
    for (size_t i = 0; i < sizeof sigs / sizeof sigs[0]; i++) {
        struct sigaction sa;
        memset(&sa, 0, sizeof sa);
        sa.sa_sigaction = on_crash;
        sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
        sigaction(sigs[i], &sa, &gOld[sigs[i]]);
    }
}

/* ============================================================ duong dan / jar */
const char *plat_data_dir(void) { return gDataDir; }
const char *plat_jar_path(void) { return NULL; }
const uint8_t *plat_jar_mem(size_t *len) {
    if (!gJar) return NULL;
    *len = gJarLen;
    return gJar;
}

/* ============================================================ khung hinh */
void plat_present(const uint32_t *fb, int w, int h) {
    size_t n = (size_t)w * (size_t)h;
    pthread_mutex_lock(&gFrontMu);
    if (!gFront || n > gFrontCap) {
        free(gFront);
        gFront = (uint32_t *)malloc(n * 4);
        gFrontCap = gFront ? n : 0;
    }
    if (gFront) {
        memcpy(gFront, fb, n * 4);
        gFrontW = w;
        gFrontH = h;
    }
    __atomic_add_fetch(&gFrameNo, 1, __ATOMIC_RELEASE);
    pthread_mutex_unlock(&gFrontMu);
}

/* ============================================================ goi nguoc len Java */
void plat_show_textbox(const char *title, const char *text, int max, int cons) {
    char b[200];
    snprintf(b, sizeof b, "[app] hop nhap chu (kieu %d, toi da %d)", cons, max);
    plat_log(b);
    JNIEnv *e = env();
    if (!e) {
        j2n_text_done(0, 0);
        return;
    }
    jstring t = jstr(e, title), x = jstr(e, text);
    (*e)->CallStaticVoidMethod(e, gBridge, mShowText, t, x, (jint)max, (jint)cons);
    clear_exc(e);
    (*e)->DeleteLocalRef(e, t);
    (*e)->DeleteLocalRef(e, x);
}

int plat_sound_create(const uint8_t *data, int len, const char *type) {
    JNIEnv *e = env();
    if (!e) return -1;
    jbyteArray a = (*e)->NewByteArray(e, len);
    if (!a) {
        clear_exc(e);
        return -1;
    }
    (*e)->SetByteArrayRegion(e, a, 0, len, (const jbyte *)data);
    jstring t = jstr(e, type);
    jint h = (*e)->CallStaticIntMethod(e, gBridge, mSndCreate, a, t);
    clear_exc(e);
    (*e)->DeleteLocalRef(e, a);
    (*e)->DeleteLocalRef(e, t);
    return h;
}

void plat_sound_start(int h, int loops) {
    JNIEnv *e = env();
    if (!e) return;
    (*e)->CallStaticVoidMethod(e, gBridge, mSndStart, (jint)h, (jint)loops);
    clear_exc(e);
}

void plat_sound_stop(int h) {
    JNIEnv *e = env();
    if (!e) return;
    (*e)->CallStaticVoidMethod(e, gBridge, mSndStop, (jint)h);
    clear_exc(e);
}

void plat_sound_close(int h) {
    JNIEnv *e = env();
    if (!e) return;
    (*e)->CallStaticVoidMethod(e, gBridge, mSndClose, (jint)h);
    clear_exc(e);
}

void plat_sound_volume(int h, int level) {
    JNIEnv *e = env();
    if (!e) return;
    (*e)->CallStaticVoidMethod(e, gBridge, mSndVolume, (jint)h, (jint)level);
    clear_exc(e);
}

int plat_open_url(const char *url) {
    JNIEnv *e = env();
    if (!e) return 0;
    jstring u = jstr(e, url);
    jboolean r = (*e)->CallStaticBooleanMethod(e, gBridge, mOpenUrl, u);
    clear_exc(e);
    (*e)->DeleteLocalRef(e, u);
    return r ? 1 : 0;
}

int plat_sms(const char *number, const char *text) {
    JNIEnv *e = env();
    if (!e) return 0;
    jstring n = jstr(e, number), t = jstr(e, text);
    jboolean r = (*e)->CallStaticBooleanMethod(e, gBridge, mSms, n, t);
    clear_exc(e);
    (*e)->DeleteLocalRef(e, n);
    (*e)->DeleteLocalRef(e, t);
    return r ? 1 : 0;
}

void plat_vibrate(int ms) {
    JNIEnv *e = env();
    if (!e) return;
    (*e)->CallStaticVoidMethod(e, gBridge, mVibrate, (jint)ms);
    clear_exc(e);
}

void plat_exit(void) {
    JNIEnv *e = env();
    if (e) {
        (*e)->CallStaticVoidMethod(e, gBridge, mExit);
        clear_exc(e);
    }
    _exit(0);
}

/* ============================================================ JNI: Java -> C */
static void *game_main(void *arg) {
    (void)arg;
    plat_log("[app] luong game bat dau");
    j2n_run(J2N_MIDLET, gW, gH);
    plat_log("[app] game ket thuc");
    return 0;
}

JNIEXPORT jboolean JNICALL JNIFN(start)(JNIEnv *e, jclass cls, jobject assets, jstring dataDir, jint w, jint h) {
    (void)cls;
    if (gStarted) return JNI_TRUE;
    const char *d = (*e)->GetStringUTFChars(e, dataDir, 0);
    snprintf(gDataDir, sizeof gDataDir, "%s", d);
    (*e)->ReleaseStringUTFChars(e, dataDir, d);
    char p[1100];
    snprintf(p, sizeof p, "%s/../log.txt", gDataDir);
    gLog = fopen(p, "a");
    if (gLog) gLogFd = fileno(gLog);
    snprintf(p, sizeof p, "%s/../crash.txt", gDataDir);
    gCrashFd = open(p, O_WRONLY | O_CREAT | O_APPEND, 0644);
    AAssetManager *am = AAssetManager_fromJava(e, assets);
    gJarAsset = am ? AAssetManager_open(am, J2N_JAR_ASSET, AASSET_MODE_BUFFER) : 0;
    if (gJarAsset) {
        gJar = (const uint8_t *)AAsset_getBuffer(gJarAsset);
        gJarLen = (size_t)AAsset_getLength(gJarAsset);
    }
    if (!gJar) {
        plat_log("[app] khong doc duoc " J2N_JAR_ASSET " trong apk");
        return JNI_FALSE;
    }
    gW = w;
    gH = h;
    char b[200];
    snprintf(b, sizeof b, "[app] khoi dong %s (Android), man %dx%d, jar %zu byte", J2N_MIDLET, gW, gH, gJarLen);
    plat_log(b);
    crash_install();
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, 16u << 20);
    pthread_attr_setdetachstate(&at, PTHREAD_CREATE_DETACHED);
    pthread_t th;
    int r = pthread_create(&th, &at, game_main, 0);
    pthread_attr_destroy(&at);
    if (r != 0) return JNI_FALSE;
    gStarted = 1;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL JNIFN(post)(JNIEnv *e, jclass cls, jint type, jint a, jint b) {
    (void)e;
    (void)cls;
    j2n_post(type, a, b);
}

JNIEXPORT void JNICALL JNIFN(textDone)(JNIEnv *e, jclass cls, jboolean ok, jstring text) {
    (void)cls;
    if (!ok || !text) {
        j2n_text_done(0, 0);
        return;
    }
    /* GetStringUTFChars tra UTF-8 "modified" (ky tu ngoai BMP thanh cap surrogate): doi tu UTF-16 cho chuan */
    const jchar *u = (*e)->GetStringChars(e, text, 0);
    jsize n = (*e)->GetStringLength(e, text);
    char *s = (char *)malloc((size_t)n * 4 + 1);
    size_t k = 0;
    for (jsize i = 0; i < n; i++) {
        uint32_t c = u[i];
        if (c >= 0xD800 && c < 0xDC00 && i + 1 < n && u[i + 1] >= 0xDC00 && u[i + 1] < 0xE000) {
            c = 0x10000 + ((c - 0xD800) << 10) + (u[i + 1] - 0xDC00);
            i++;
        }
        if (c < 0x80) {
            s[k++] = (char)c;
        } else if (c < 0x800) {
            s[k++] = (char)(0xC0 | (c >> 6));
            s[k++] = (char)(0x80 | (c & 63));
        } else if (c < 0x10000) {
            s[k++] = (char)(0xE0 | (c >> 12));
            s[k++] = (char)(0x80 | ((c >> 6) & 63));
            s[k++] = (char)(0x80 | (c & 63));
        } else {
            s[k++] = (char)(0xF0 | (c >> 18));
            s[k++] = (char)(0x80 | ((c >> 12) & 63));
            s[k++] = (char)(0x80 | ((c >> 6) & 63));
            s[k++] = (char)(0x80 | (c & 63));
        }
    }
    s[k] = 0;
    (*e)->ReleaseStringChars(e, text, u);
    j2n_text_done(1, s);
    free(s);
}

/* chep khung moi nhat vao Bitmap ARGB_8888 (bo nho RGBA) neu co khung moi; tra ve so khung */
JNIEXPORT jint JNICALL JNIFN(copyFrame)(JNIEnv *e, jclass cls, jobject bmp, jint last) {
    (void)cls;
    int f = __atomic_load_n(&gFrameNo, __ATOMIC_ACQUIRE);
    if (f == last || !gFront) return f;
    AndroidBitmapInfo bi;
    if (AndroidBitmap_getInfo(e, bmp, &bi) != 0) return last;
    void *px = 0;
    if (AndroidBitmap_lockPixels(e, bmp, &px) != 0 || !px) return last;
    pthread_mutex_lock(&gFrontMu);
    if ((int)bi.width != gFrontW || (int)bi.height != gFrontH) {
        /* khung moi khac kich thuoc (vao / ra ban ban ca): Java tao Bitmap moi theo frameSize() */
        pthread_mutex_unlock(&gFrontMu);
        AndroidBitmap_unlockPixels(e, bmp);
        return last;
    }
    f = gFrameNo;
    int fw = gFrontW, fh = gFrontH;
    for (int y = 0; y < fh; y++) {
        const uint32_t *src = gFront + (size_t)y * fw;
        uint32_t *dst = (uint32_t *)((uint8_t *)px + (size_t)y * bi.stride);
        for (int x = 0; x < fw; x++) {
            uint32_t c = src[x];   /* 0xAARRGGBB -> bo nho R,G,B,A (0xAABBGGRR) */
            dst[x] = 0xFF000000u | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
        }
    }
    pthread_mutex_unlock(&gFrontMu);
    AndroidBitmap_unlockPixels(e, bmp);
    return f;
}

/* v8h-31: nhip quet man hinh (Choreographer.doFrame) -> vong lap game ve khung theo nhip (disp.c j2n_frame_sleep) */
JNIEXPORT void JNICALL JNIFN(vsync)(JNIEnv *e, jclass cls, jlong t) {
    (void)e;
    (void)cls;
    j2n_vsync((int64_t)t);
}

JNIEXPORT jint JNICALL JNIFN(frames)(JNIEnv *e, jclass cls) {
    (void)e;
    (void)cls;
    return __atomic_load_n(&gFrameNo, __ATOMIC_ACQUIRE);
}

JNIEXPORT jstring JNICALL JNIFN(gcStats)(JNIEnv *e, jclass cls) {
    (void)cls;
    unsigned seen = 0, kept = 0;
    rt_gc_roots_stats(&seen, &kept);
    char b[160];
    snprintf(b, sizeof b, "GC xet %u vung du lieu (giu %u cua app)", seen, kept);
    return (*e)->NewStringUTF(e, b);
}

/* kich thuoc khung moi nhat: (rong << 16) | cao; 0 = chua co khung */
JNIEXPORT jint JNICALL JNIFN(frameSize)(JNIEnv *e, jclass cls) {
    (void)e;
    (void)cls;
    pthread_mutex_lock(&gFrontMu);
    jint r = gFront ? (jint)(((uint32_t)gFrontW << 16) | ((uint32_t)gFrontH & 0xFFFF)) : 0;
    pthread_mutex_unlock(&gFrontMu);
    return r;
}

/* Avatar: may chu thay cho 127.0.0.1 ("host" hoac "host:cong") - goi luc nao cung duoc */
JNIEXPORT void JNICALL JNIFN(setServer)(JNIEnv *e, jclass cls, jstring spec) {
    (void)cls;
    if (!spec) {
        j2n_set_server(0);
        return;
    }
    const char *s = (*e)->GetStringUTFChars(e, spec, 0);
    j2n_set_server(s);
    (*e)->ReleaseStringUTFChars(e, spec, s);
}

/* Avatar: dia chi may chu ma hoa (assets/server.dat) - giai ma o day, dia chi khong qua Java, khong ghi log */
JNIEXPORT jboolean JNICALL JNIFN(setServerEnc)(JNIEnv *e, jclass cls, jbyteArray d) {
    (void)cls;
    if (!d) return JNI_FALSE;
    jsize n = (*e)->GetArrayLength(e, d);
    if (n <= 0 || n > 512) return JNI_FALSE;
    jbyte buf[512];
    (*e)->GetByteArrayRegion(e, d, 0, n, buf);
    int ok = j2n_set_server_enc((const unsigned char *)buf, (int)n);
    memset(buf, 0, sizeof buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/* Avatar 250 HD: may ve net cao (k = 2..4, 0 tat) + xin bo anh x2 cua may chu - goi truoc start */
JNIEXPORT void JNICALL JNIFN(setHdK)(JNIEnv *e, jclass cls, jint k) {
    (void)e;
    (void)cls;
    j2n_set_hd_k(k);
}

JNIEXPORT void JNICALL JNIFN(setHdAnh)(JNIEnv *e, jclass cls, jboolean on) {
    (void)e;
    (void)cls;
    j2n_set_hd_anh(on ? 1 : 0);
}

/* Avatar 250 HD: chep khung moi nhat NGUYEN byte (0xAARRGGBB, khong dao kenh - Java ve bang bo loc mau doi R <-> B tren GPU).
 * Khung net cao 2-3 trieu diem: bo vong lap doi kenh tung diem cua copyFrame */
JNIEXPORT jint JNICALL JNIFN(copyFrameRaw)(JNIEnv *e, jclass cls, jobject bmp, jint last) {
    (void)cls;
    int f = __atomic_load_n(&gFrameNo, __ATOMIC_ACQUIRE);
    if (f == last || !gFront) return f;
    AndroidBitmapInfo bi;
    if (AndroidBitmap_getInfo(e, bmp, &bi) != 0) return last;
    void *px = 0;
    if (AndroidBitmap_lockPixels(e, bmp, &px) != 0 || !px) return last;
    pthread_mutex_lock(&gFrontMu);
    if ((int)bi.width != gFrontW || (int)bi.height != gFrontH) {
        pthread_mutex_unlock(&gFrontMu);
        AndroidBitmap_unlockPixels(e, bmp);
        return last;
    }
    f = gFrameNo;
    int fw = gFrontW, fh = gFrontH;
    if ((int)bi.stride == fw * 4) {
        memcpy(px, gFront, (size_t)fw * fh * 4);
    } else {
        for (int y = 0; y < fh; y++) memcpy((uint8_t *)px + (size_t)y * bi.stride, gFront + (size_t)y * fw, (size_t)fw * 4);
    }
    pthread_mutex_unlock(&gFrontMu);
    AndroidBitmap_unlockPixels(e, bmp);
    return f;
}

/* Avatar: Canvas ve phong to (vd "FishGame", 2) - goi truoc start */
JNIEXPORT void JNICALL JNIFN(setHd)(JNIEnv *e, jclass cls, jstring names, jint scale) {
    (void)cls;
    const char *s = names ? (*e)->GetStringUTFChars(e, names, 0) : 0;
    j2n_set_hd(s, scale);
    if (s) (*e)->ReleaseStringUTFChars(e, names, s);
}
