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
    jctx *c = (jctx *)GC_MALLOC_UNCOLLECTABLE(sizeof(jctx));
    memset(c, 0, sizeof *c);
    c->id = 1;
    rt_attach_thread(c);
    return java_main(arg);
}

int j2n_start(const char *midlet_class, int w, int h) {
    rt_init();
    rt_thread_run_hook = thread_hook;
    gfx_init(w, h);
    g_midlet = strdup(midlet_class);
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, 16u << 20);
    pthread_attr_setdetachstate(&at, PTHREAD_CREATE_DETACHED);
    pthread_t th;
    int r = pthread_create(&th, &at, main_entry, 0);
    pthread_attr_destroy(&at);
    return r == 0;
}
