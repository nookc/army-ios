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

/* bo dem trinh bay (de chup / nen tang doc) */
static uint32_t *front;
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

void N_j2n_Disp_present(jctx *t) {
    (void)t;
    pthread_mutex_lock(&fmu);
    if (!front) front = (uint32_t *)malloc((size_t)gfx_w * gfx_h * 4);
    memcpy(front, gfx_fb, (size_t)gfx_w * gfx_h * 4);
    frames++;
    pthread_mutex_unlock(&fmu);
    plat_present(gfx_fb, gfx_w, gfx_h);
}

int j2n_snapshot(uint32_t *out, int w, int h) {
    pthread_mutex_lock(&fmu);
    int f = frames;
    if (front && w == gfx_w && h == gfx_h) memcpy(out, front, (size_t)w * h * 4);
    pthread_mutex_unlock(&fmu);
    return f;
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
static int64_t vs_tick, vs_next, vs_last_ns, vs_period_ns, vs_seen_ms, vs_due_ns;
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
    if (!j2n_vsync_mode || !t || t != vs_game || !vs_mark || ms <= 0 || ms > 40) {
        return 0;
    }
    vs_mark = 0;
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
        int k = (int)((33333333LL + per / 2) / per);
        if (k < 1) {
            k = 1;
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
    return 1;
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
