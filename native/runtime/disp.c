/* disp.c - hang doi su kien, ve lai, trinh bay khung hinh, hop nhap chu, khoi dong game */
#define GC_THREADS 1
#include "gc.h"
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "natives.h"
#include "platform.h"

void j2n_midi_san(void);
extern uint32_t *gfx_fb;
extern int gfx_w, gfx_h;
void gfx_init(int w, int h);

#define QN 512
typedef struct ev {
    int type, a, b;
} ev;
static ev q[QN];
static int qh, qt;
static int repaint_flag;
static pthread_mutex_t mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cv = PTHREAD_COND_INITIALIZER;
static char *text_result;
static int text_active;

/* bo dem trinh bay (de chup / nen tang doc); kich thuoc theo khung moi nhat (man phong to co the lon hon) */
static uint32_t *front;
static size_t front_cap;
static int front_w, front_h;
static pthread_mutex_t fmu = PTHREAD_MUTEX_INITIALIZER;
static int frames;

static void push(int type, int a, int b) {
    pthread_mutex_lock(&mu);
    int n = (qt + 1) % QN;
    if (n != qh) {
        /* gop cac su kien keo lien tiep */
        if (type == J2N_POINTER_DRAG && qt != qh) {
            int last = (qt + QN - 1) % QN;
            if (q[last].type == J2N_POINTER_DRAG) {
                q[last].a = a;
                q[last].b = b;
                pthread_cond_signal(&cv);
                pthread_mutex_unlock(&mu);
                return;
            }
        }
        q[qt].type = type;
        q[qt].a = a;
        q[qt].b = b;
        qt = n;
    }
    pthread_cond_signal(&cv);
    pthread_mutex_unlock(&mu);
}

void j2n_post(int type, int a, int b) { push(type, a, b); }

void j2n_text_done(int ok, const char *utf8) {
    pthread_mutex_lock(&mu);
    free(text_result);
    text_result = utf8 ? strdup(utf8) : 0;
    text_active = 0;
    pthread_mutex_unlock(&mu);
    push(7, ok ? 1 : 0, 0);
}

int32_t N_j2n_Disp_width(jctx *t) { (void)t; return gfx_w; }
int32_t N_j2n_Disp_height(jctx *t) { (void)t; return gfx_h; }

int32_t N_j2n_Disp_waitEvent(jctx *t, jobj arr) {
    int type, a, b;
    pthread_mutex_lock(&mu);
    for (;;) {
        if (qh != qt) {
            type = q[qh].type;
            a = q[qh].a;
            b = q[qh].b;
            qh = (qh + 1) % QN;
            break;
        }
        if (repaint_flag) {
            type = 6;
            a = b = 0;
            break;
        }
        pthread_cond_wait(&cv, &mu);
    }
    pthread_mutex_unlock(&mu);
    if (arr && ALEN(arr) >= 3) {
        AD(arr, int32_t)[0] = type;
        AD(arr, int32_t)[1] = a;
        AD(arr, int32_t)[2] = b;
    }
    (void)t;
    return type;
}

void N_j2n_Disp_requestRepaint(jctx *t) {
    (void)t;
    pthread_mutex_lock(&mu);
    if (!repaint_flag) {
        repaint_flag = 1;
        pthread_cond_signal(&cv);
    }
    pthread_mutex_unlock(&mu);
}

int32_t N_j2n_Disp_repaintPending(jctx *t) {
    (void)t;
    return __atomic_load_n(&repaint_flag, __ATOMIC_ACQUIRE);
}

void N_j2n_Disp_clearRepaint(jctx *t) {
    (void)t;
    pthread_mutex_lock(&mu);
    repaint_flag = 0;
    pthread_mutex_unlock(&mu);
}

void N_j2n_Disp_postEvent(jctx *t, int32_t type, int32_t a, int32_t b) {
    (void)t;
    push(type, a, b);
}

static void present_px(const uint32_t *px, int w, int h) {
    size_t n = (size_t)w * (size_t)h;
    pthread_mutex_lock(&fmu);
    if (!front || n > front_cap) {
        free(front);
        front = (uint32_t *)malloc(n * 4);
        front_cap = front ? n : 0;
    }
    if (front) {
        memcpy(front, px, n * 4);
        front_w = w;
        front_h = h;
    }
    frames++;
    pthread_mutex_unlock(&fmu);
    plat_present(px, w, h);
}

void N_j2n_Disp_present(jctx *t) {
    (void)t;
    present_px(gfx_fb, gfx_w, gfx_h);
}

/* ------------------------------------------------------------ Avatar: Canvas ve phong to (ban ban ca)
 * Nen tang dat ten lop (cach nhau dau phay) + so lan phong to truoc khi chay game. Canvas do bao kich thuoc
 * rong x cao gap 'scale' lan, ve vao anh rieng; cham duoc nhan len trong j2n.Ui. Khung trinh bay lon hon man thuong. */
static char hd_names[256];
static int hd_scale = 1;

void j2n_set_hd(const char *names, int scale) {
    snprintf(hd_names, sizeof hd_names, "%s", names ? names : "");
    hd_scale = scale < 1 ? 1 : scale > 4 ? 4 : scale;
}

int32_t N_j2n_Disp_hdScale(jctx *t) {
    (void)t;
    return hd_names[0] ? hd_scale : 1;
}

jobj N_j2n_Disp_hdNames(jctx *t) { return hd_names[0] ? rt_str_utf8(t, hd_names) : 0; }

/* Avatar: co "khung vua ve co anh net cao" (gfx.c) -> j2n.Ui ve khung sau o man net cao */
extern volatile int gfx_hd_seen;

int32_t N_j2n_Disp_hdSeen(jctx *t) {
    (void)t;
    int v = gfx_hd_seen;
    gfx_hd_seen = 0;
    return v != 0;
}

void N_j2n_Disp_presentImage(jctx *t, jobj argb, int32_t w, int32_t h) {
    if (!argb) {
        rt_npe(t);
        return;
    }
    if (w <= 0 || h <= 0 || (int64_t)w * h > (int64_t)ALEN(argb)) return;
    present_px((const uint32_t *)AD(argb, int32_t), w, h);
}

int j2n_snapshot(uint32_t *out, int w, int h) {
    pthread_mutex_lock(&fmu);
    int f = frames;
    if (front && w == front_w && h == front_h) memcpy(out, front, (size_t)w * h * 4);
    pthread_mutex_unlock(&fmu);
    return f;
}

uint32_t *j2n_snapshot_any(int *w, int *h, int *frame_no) {
    uint32_t *r = 0;
    pthread_mutex_lock(&fmu);
    if (frame_no) *frame_no = frames;
    if (front && front_w > 0 && front_h > 0) {
        r = (uint32_t *)malloc((size_t)front_w * front_h * 4);
        if (r) {
            memcpy(r, front, (size_t)front_w * front_h * 4);
            *w = front_w;
            *h = front_h;
        }
    }
    pthread_mutex_unlock(&fmu);
    return r;
}

int j2n_frames(void) { return frames; }

/* ------------------------------------------------------------ v8h-31: nhip khung theo nhip quet man hinh
 * Vong lap game (coreLG.a.run) ve 1 khung (serviceRepaints) roi ngu "30 - thoi gian da chay" ms. Man hinh quet 60 / 90 / 120 Hz
 * khong chia het cho ~33 khung/giay + Android danh thuc tre -> moi khung hien 1 hoac 2 nhip quet ngau nhien (giat nhe lien tuc).
 * Nen tang co nhip quet (Android: Choreographer) goi j2n_vsync() moi nhip; lan ngu dau tien sau serviceRepaints cua luong game
 * (Disp.frameMark) doi toi nhip quet hen thay vi ngu theo dong ho:
 *   che do 1: dung K nhip moi khung (K = 33,3 ms / chu ky quet: 60 Hz 2, 90 Hz 3, 120 Hz 4) -> 30 khung/giay deu tuyet doi
 *   che do 2: trung binh 30 ms moi khung nhu cu (~33 khung/giay) nhung khop nhip quet (60 Hz: 2-2-2-2-1 deu dan)
 * Khong co nhip quet gan day (app ra nen, nen tang khong goi) -> ngu nhu cu. */
int j2n_vsync_mode = 1;
static pthread_mutex_t vs_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t vs_cv;
static pthread_once_t vs_once = PTHREAD_ONCE_INIT;
static int64_t vs_tick, vs_next, vs_last_ns, vs_period_ns, vs_seen_ms, vs_due_ns, vs_wake_ms;
static void *volatile vs_game;
static volatile int vs_mark;

static int64_t vs_mono_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void vs_init(void) {
    pthread_condattr_t a;
    pthread_condattr_init(&a);
#if !defined(__APPLE__) && !defined(_WIN32)
    pthread_condattr_setclock(&a, CLOCK_MONOTONIC);
#endif
    pthread_cond_init(&vs_cv, &a);
    pthread_condattr_destroy(&a);
}

/* nen tang: 1 nhip quet man hinh (t_ns: thoi diem nhip, dong ho don dieu) */
void j2n_vsync(int64_t t_ns) {
    pthread_once(&vs_once, vs_init);
    pthread_mutex_lock(&vs_mu);
    if (vs_last_ns > 0) {
        int64_t d = t_ns - vs_last_ns;
        if (d > 3000000 && d < 60000000) {
            vs_period_ns = vs_period_ns > 0 ? (vs_period_ns * 7 + d) / 8 : d;
        }
    }
    vs_last_ns = t_ns;
    vs_tick++;
    vs_seen_ms = vs_mono_ms();
    pthread_cond_broadcast(&vs_cv);
    pthread_mutex_unlock(&vs_mu);
}

/* Ui.serviceRepaints (luong game): vua xong 1 khung */
void N_j2n_Disp_frameMark(jctx *t) {
    vs_game = t;
    vs_mark = 1;
}

/* rt_sleep: lan ngu giua 2 khung cua luong game -> doi nhip quet. 1 = da doi, 0 = ngu theo dong ho nhu cu */
int j2n_frame_sleep(void *t, int64_t ms) {
    if (!j2n_vsync_mode || !t || t != vs_game || !vs_mark || ms <= 0 || ms > 100) {
        return 0;
    }
    vs_mark = 0;
    /* chu ky khung game muon = thoi gian da chay tu lan thuc day truoc + thoi gian xin ngu
       (Army 30 ms -> 2 nhip 60 Hz nhu cu, Avatar 50 ms -> 3 nhip deu, ban ban ca 16 ms -> moi nhip) */
    int64_t now_ms = vs_mono_ms();
    int64_t want_ms = ms + ((vs_wake_ms > 0 && now_ms - vs_wake_ms < 250) ? now_ms - vs_wake_ms : 0);
    pthread_once(&vs_once, vs_init);
    pthread_mutex_lock(&vs_mu);
    int64_t per = vs_period_ns;
    if (per <= 0 || vs_mono_ms() - vs_seen_ms > 100) {
        pthread_mutex_unlock(&vs_mu);
        return 0;
    }
    struct timespec dl;
#if defined(__APPLE__) || defined(_WIN32)
    clock_gettime(CLOCK_REALTIME, &dl);
#else
    clock_gettime(CLOCK_MONOTONIC, &dl);
#endif
    dl.tv_nsec += 100 * 1000000L;
    if (dl.tv_nsec >= 1000000000L) {
        dl.tv_sec++;
        dl.tv_nsec -= 1000000000L;
    }
    if (j2n_vsync_mode == 2) {
        /* trung binh 30 ms / khung: hen 'due' cong 30 ms moi khung, thuc o nhip quet dau tien gan due nhat */
        if (vs_due_ns <= 0 || vs_due_ns < vs_last_ns - 30000000LL || vs_due_ns > vs_last_ns + 60000000LL) {
            vs_due_ns = vs_last_ns + 30000000LL;
        }
        while (vs_last_ns + per / 2 < vs_due_ns) {
            if (pthread_cond_timedwait(&vs_cv, &vs_mu, &dl) != 0) {
                break;
            }
        }
        vs_due_ns += 30000000LL;
    } else {
        int k = (int)((want_ms * 1000000LL + per / 2) / per);
        if (k < 1) {
            k = 1;
        }
        if (k > 12) {
            k = 12;
        }
        /* khung nang qua K nhip / lan dau: khung sau bat dau o nhip ke tiep (khong don khung) */
        if (vs_next <= vs_tick || vs_next > vs_tick + k) {
            vs_next = vs_tick + 1;
        }
        while (vs_tick < vs_next) {
            if (pthread_cond_timedwait(&vs_cv, &vs_mu, &dl) != 0) {
                break;
            }
        }
        vs_next = vs_tick + k;
    }
    pthread_mutex_unlock(&vs_mu);
    vs_wake_ms = vs_mono_ms();
    return 1;
}

/* Avatar: ban phim he thong theo o go chu cua game */
static void (*kb_hook)(int, int);

void j2n_set_keyboard_hook(void (*fn)(int show, int area_bottom)) {
    kb_hook = fn;
}

void N_j2n_Disp_wantKeyboard(jctx *t, int32_t show, int32_t area_bottom) {
    (void)t;
    if (kb_hook) {
        kb_hook(show, area_bottom);
    }
}

void N_j2n_Disp_showTextBox(jctx *t, jobj title, jobj text, int32_t max, int32_t cons) {
    (void)t;
    pthread_mutex_lock(&mu);
    text_active = 1;
    pthread_mutex_unlock(&mu);
    plat_show_textbox(title ? rt_str_cstr(title) : "", text ? rt_str_cstr(text) : "", max, cons);
}

jobj N_j2n_Disp_textResult(jctx *t) {
    pthread_mutex_lock(&mu);
    char *s = text_result ? strdup(text_result) : 0;
    pthread_mutex_unlock(&mu);
    if (!s) return 0;
    jobj r = rt_str_utf8(t, s);
    free(s);
    return r;
}

int32_t N_j2n_Disp_platformRequest(jctx *t, jobj url) {
    (void)t;
    return url ? plat_open_url(rt_str_cstr(url)) : 0;
}

int32_t N_j2n_Disp_vibrate(jctx *t, int32_t ms) {
    (void)t;
    plat_vibrate(ms);
    return 1;
}

void N_j2n_Disp_exit(jctx *t) {
    (void)t;
    plat_exit();
}

/* ------------------------------------------------------------ khoi dong */
static char *g_midlet;

static void thread_hook(jctx *t, jobj thread) { JM_java_lang_Thread_entry(t, thread); }

static void *java_main(void *arg) {
    (void)arg;
    jctx *t = rt_cur();
    if (!t) {
        rt_log("[j2n] luong chinh khong co ngu canh");
        return 0;
    }
    jobj name = rt_str_utf8(t, g_midlet);
    JM_j2n_Boot_main(t, name);
    if (t->exc) rt_print_exc(t, t->exc, "Boot.main");
    return 0;
}

typedef struct mstart {
    int dummy;
} mstart;

static void *main_entry(void *arg) {
    /* luong Java chinh: tao ngu canh rieng */
    extern int rt_attach_thread(jctx *ctx);
    extern void rt_thread_qos(void);
    rt_thread_qos();
    jctx *c = (jctx *)GC_MALLOC_UNCOLLECTABLE(sizeof(jctx));
    memset(c, 0, sizeof *c);
    c->id = 1;
    rt_attach_thread(c);
    return java_main(arg);
}

/* chay game ngay tren luong hien tai (Android: luong rieng do lop nen tang tao, KHONG phai luong UI;
   GC dang ky luong nay lam luong chinh nen no phai song suot doi app - ham khong tra ve khi game con chay) */
int j2n_run(const char *midlet_class, int w, int h) {
    extern int rt_attach_thread(jctx *ctx);
    extern void rt_thread_qos(void);
    rt_init();
    rt_thread_run_hook = thread_hook;
    gfx_init(w, h);
    g_midlet = strdup(midlet_class);
    j2n_midi_san();
    rt_thread_qos();
    jctx *c = (jctx *)GC_MALLOC_UNCOLLECTABLE(sizeof(jctx));
    memset(c, 0, sizeof *c);
    c->id = 1;
    rt_attach_thread(c);
    java_main(0);
    return 1;
}

int j2n_start(const char *midlet_class, int w, int h) {
    rt_init();
    rt_thread_run_hook = thread_hook;
    gfx_init(w, h);
    g_midlet = strdup(midlet_class);
    j2n_midi_san();
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, 16u << 20);
    pthread_attr_setdetachstate(&at, PTHREAD_CREATE_DETACHED);
    pthread_t th;
    int r = pthread_create(&th, &at, main_entry, 0);
    pthread_attr_destroy(&at);
    return r == 0;
}
