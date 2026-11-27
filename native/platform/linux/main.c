/* Bo chay thu tren Linux: kich ban lenh (giong pmdrive), chup khung hinh ra PPM.
 *   army2n <jar> <lop MIDlet> <rong> <cao> <tep lenh> <thu muc ra>
 * Lenh: key N | down N | up N | type:abc | sleep MS | shot TEN | tap X Y | drag X1 Y1 X2 Y2 |
 *       text:chuoi (OK hop nhap chu) | textcancel | quit
 *       rec TEP.mp4 RONG CAO FPS (quay video bang ffmpeg, dung thoi gian that) | recstop
 * Tep lenh ket thuc ".live": doc tiep dong moi ghi them.
 */
#include <errno.h>
#include <stdint.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include "platform.h"

static char g_jar[1024], g_out[1024], g_data[1100];
static int W, H;
static volatile int textbox_open;

/* quay video (lenh rec): moi khung trinh chieu -> ffmpeg; nhan / bo khung de video dung nhip thoi gian that */
static pthread_mutex_t rec_mu = PTHREAD_MUTEX_INITIALIZER;
static FILE *rec_pipe;
static int rec_w, rec_h, rec_fps;
static long long rec_t0, rec_n;
static uint32_t *rec_buf;

static long long now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (long long)t.tv_sec * 1000LL + t.tv_nsec / 1000000L;
}

void plat_present(const uint32_t *fb, int w, int h) {
    /* J2N_FPS=1: in so khung / giay + khoang cach lon nhat giua 2 khung (do do muot) */
    static int fps_on = -1, fps_n;
    static long long fps_t0, fps_last, fps_gap;
    if (fps_on < 0) fps_on = getenv("J2N_FPS") != NULL;
    if (fps_on) {
        long long t = now_ms();
        if (fps_t0 == 0) fps_t0 = fps_last = t;
        if (t - fps_last > fps_gap) fps_gap = t - fps_last;
        fps_last = t;
        fps_n++;
        if (t - fps_t0 >= 1000) {
            fprintf(stderr, "[fps] %d khung/giay, khoang ho lon nhat %lld ms\n", fps_n, fps_gap);
            fps_n = 0; fps_gap = 0; fps_t0 = t;
        }
    }
    pthread_mutex_lock(&rec_mu);
    if (rec_pipe && fb && w > 0 && h > 0) {
        /* khac co: phong / thu nho gan nhat ve co video */
        for (int y = 0; y < rec_h; y++) {
            const uint32_t *src = fb + (size_t)(y * h / rec_h) * w;
            uint32_t *dst = rec_buf + (size_t)y * rec_w;
            for (int x = 0; x < rec_w; x++) dst[x] = src[x * w / rec_w] | 0xFF000000u;
        }
        long long can = (now_ms() - rec_t0) * rec_fps / 1000 + 1;
        while (rec_n < can) {
            fwrite(rec_buf, 4, (size_t)rec_w * rec_h, rec_pipe);
            rec_n++;
        }
    }
    pthread_mutex_unlock(&rec_mu);
}

static void rec_start(const char *file, int w, int h, int fps) {
    pthread_mutex_lock(&rec_mu);
    if (!rec_pipe && w > 0 && h > 0) {
        char cmd[1600];
        snprintf(cmd, sizeof cmd,
                 "ffmpeg -loglevel error -y -f rawvideo -pix_fmt bgra -s %dx%d -r %d -i - -c:v libx264 -preset veryfast "
                 "-crf 20 -pix_fmt yuv420p -movflags +faststart '%s'", w, h, fps, file);
        rec_buf = (uint32_t *)malloc((size_t)w * h * 4);
        rec_pipe = rec_buf ? popen(cmd, "w") : 0;
        rec_w = w;
        rec_h = h;
        rec_fps = fps > 0 ? fps : 30;
        rec_t0 = now_ms();
        rec_n = 0;
        fprintf(stderr, "[harness] rec %s %dx%d %d khung/giay\n", file, w, h, rec_fps);
    }
    pthread_mutex_unlock(&rec_mu);
}

static void rec_stop(void) {
    pthread_mutex_lock(&rec_mu);
    if (rec_pipe) {
        pclose(rec_pipe);
        rec_pipe = 0;
        free(rec_buf);
        rec_buf = 0;
        fprintf(stderr, "[harness] recstop %lld khung\n", rec_n);
    }
    pthread_mutex_unlock(&rec_mu);
}

void plat_show_textbox(const char *title, const char *text, int max, int cons) {
    fprintf(stderr, "[harness] TextBox '%s' text='%s' max=%d cons=0x%x\n", title, text, max, cons);
    textbox_open = 1;
}

int plat_open_url(const char *url) {
    fprintf(stderr, "[harness] platformRequest %s\n", url);
    return 0;
}

int plat_sms(const char *n, const char *t) {
    fprintf(stderr, "[harness] sms %s: %s\n", n, t);
    return 1;
}

void plat_vibrate(int ms) { (void)ms; }

void plat_exit(void) {
    fprintf(stderr, "[harness] notifyDestroyed -> thoat\n");
    exit(0);
}

const char *plat_data_dir(void) { return g_data; }
const char *plat_jar_path(void) { return g_jar; }

static int g_sounds;
int plat_sound_create(const uint8_t *d, int len, const char *type) {
    (void)d;
    fprintf(stderr, "[harness] sound %s (%d byte)\n", type, len);
    return g_sounds++;
}
void plat_sound_start(int h, int loops) { (void)h; (void)loops; }
void plat_sound_stop(int h) { (void)h; }
void plat_sound_close(int h) { (void)h; }
void plat_sound_volume(int h, int l) { (void)h; (void)l; }

const uint8_t *plat_jar_mem(size_t *len) {
    (void)len;
    return NULL;
}

void plat_log(const char *s) {
    fprintf(stderr, "%s\n", s);
}

void plat_log_raw(const char *s) {
    size_t n = strlen(s);
    if (write(2, s, n) < 0 || write(2, "\n", 1) < 0) return;
}

static void msleep(int ms) {
    struct timespec ts = {ms / 1000, (long)(ms % 1000) * 1000000L};
    while (nanosleep(&ts, &ts) != 0 && errno == EINTR) {
    }
}

static void shot(const char *name) {
    int w = 0, h = 0, f = 0;
    uint32_t *buf = j2n_snapshot_any(&w, &h, &f);   /* kich thuoc that (man phong to co the lon hon W x H) */
    char p[1200];
    snprintf(p, sizeof p, "%s/%s.ppm", g_out, name);
    FILE *o = buf ? fopen(p, "wb") : 0;
    if (o) {
        fprintf(o, "P6\n%d %d\n255\n", w, h);
        for (int i = 0; i < w * h; i++) {
            uint8_t px[3] = {(uint8_t)(buf[i] >> 16), (uint8_t)(buf[i] >> 8), (uint8_t)buf[i]};
            fwrite(px, 1, 3, o);
        }
        fclose(o);
    }
    fprintf(stderr, "[harness] shot %s %dx%d (khung %d)\n", name, w, h, f);
    free(buf);
}

static void run_cmd(char *line) {
    size_t n = strlen(line);
    while (n && (line[n - 1] == '\n' || line[n - 1] == '\r')) line[--n] = 0;
    if (!n || line[0] == '#') return;
    if (strncmp(line, "type:", 5) == 0) {
        /* giai UTF-8 -> ma Unicode (nhu ban phim that cua iOS/Android gui tung ky tu) */
        const unsigned char *c = (const unsigned char *)line + 5;
        while (*c) {
            int cp = *c++;
            if (cp >= 0xE0 && c[0] && c[1]) { cp = ((cp & 0x0F) << 12) | ((c[0] & 0x3F) << 6) | (c[1] & 0x3F); c += 2; }
            else if (cp >= 0xC0 && c[0]) { cp = ((cp & 0x1F) << 6) | (c[0] & 0x3F); c += 1; }
            j2n_post(J2N_KEY_PRESS, cp, 0);
            msleep(60);
            j2n_post(J2N_KEY_RELEASE, cp, 0);
            msleep(60);
        }
        return;
    }
    if (strncmp(line, "text:", 5) == 0) {
        textbox_open = 0;
        j2n_text_done(1, line + 5);
        return;
    }
    if (strcmp(line, "textcancel") == 0) {
        textbox_open = 0;
        j2n_text_done(0, 0);
        return;
    }
    char op[32];
    int a = 0, b = 0, c = 0, d = 0;
    char name[256];
    if (sscanf(line, "shot %255s", name) == 1) {
        shot(name);
        return;
    }
    {
        char f[512];
        int rw = 0, rh = 0, rf = 30;
        if (sscanf(line, "rec %511s %d %d %d", f, &rw, &rh, &rf) >= 3) {
            rec_start(f, rw, rh, rf);
            return;
        }
        if (strcmp(line, "recstop") == 0) {
            rec_stop();
            return;
        }
    }
    int k = sscanf(line, "%31s %d %d %d %d", op, &a, &b, &c, &d);
    if (k < 1) return;
    if (!strcmp(op, "key")) {
        j2n_post(J2N_KEY_PRESS, a, 0);
        msleep(80);
        j2n_post(J2N_KEY_RELEASE, a, 0);
    } else if (!strcmp(op, "down")) {
        j2n_post(J2N_KEY_PRESS, a, 0);
    } else if (!strcmp(op, "up")) {
        j2n_post(J2N_KEY_RELEASE, a, 0);
    } else if (!strcmp(op, "sleep")) {
        msleep(a);
    } else if (!strcmp(op, "tap")) {
        j2n_post(J2N_POINTER_PRESS, a, b);
        msleep(60);
        j2n_post(J2N_POINTER_RELEASE, a, b);
    } else if (!strcmp(op, "pdown")) {
        j2n_post(J2N_POINTER_PRESS, a, b);
    } else if (!strcmp(op, "pmove")) {
        j2n_post(J2N_POINTER_DRAG, a, b);
    } else if (!strcmp(op, "pup")) {
        j2n_post(J2N_POINTER_RELEASE, a, b);
    } else if (!strcmp(op, "drag")) {
        j2n_post(J2N_POINTER_PRESS, a, b);
        for (int i = 1; i <= 10; i++) {
            msleep(20);
            j2n_post(J2N_POINTER_DRAG, a + (c - a) * i / 10, b + (d - b) * i / 10);
        }
        j2n_post(J2N_POINTER_RELEASE, c, d);
    } else if (!strcmp(op, "frames")) {
        /* do toc do ve: so khung da trinh chieu + dong ho (ms) */
        struct timespec t;
        clock_gettime(CLOCK_MONOTONIC, &t);
        fprintf(stderr, "[frames] %d %lld\n", j2n_frames(), (long long)t.tv_sec * 1000LL + t.tv_nsec / 1000000L);
    } else if (!strcmp(op, "quit")) {
        exit(0);
    }
}

/* v8h-31: J2N_VSYNC_HZ=60 (90, 120...) -> gia lap nhip quet man hinh nhu Android Choreographer (thu nhip khung theo nhip quet).
   J2N_VSYNC_MODE: 1 khoa 30 khung/giay (mac dinh), 2 trung binh 30 ms, 0 tat */
static void *vsync_thread(void *arg) {
    long hz = (long)(intptr_t)arg;
    int64_t per = 1000000000LL / hz;
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    for (;;) {
        ts.tv_nsec += per;
        while (ts.tv_nsec >= 1000000000L) {
            ts.tv_sec++;
            ts.tv_nsec -= 1000000000L;
        }
        clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, 0);
        j2n_vsync((int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec);
    }
    return 0;
}

static void kb_hook_log(int show, int area_bottom) {
    char b[96];
    snprintf(b, sizeof b, "[banphim] %s%s (vung game toi y=%d)", show ? "hien" : "an", show == 2 ? " - o mat khau" : "",
             area_bottom);
    plat_log(b);
}

int main(int argc, char **argv) {
    const char *vhz = getenv("J2N_VSYNC_HZ");
    if (getenv("J2N_VSYNC_MODE")) {
        j2n_vsync_mode = atoi(getenv("J2N_VSYNC_MODE"));
    }
    if (vhz && atoi(vhz) > 0) {
        pthread_t vt;
        pthread_create(&vt, 0, vsync_thread, (void *)(intptr_t)atoi(vhz));
        fprintf(stderr, "[harness] gia lap nhip quet %d Hz, che do %d\n", atoi(vhz), j2n_vsync_mode);
    }
    if (argc < 7) {
        fprintf(stderr, "dung: %s <jar> <midlet> <rong> <cao> <tep lenh> <thu muc ra>\n", argv[0]);
        return 2;
    }
    snprintf(g_jar, sizeof g_jar, "%s", argv[1]);
    W = atoi(argv[3]);
    H = atoi(argv[4]);
    snprintf(g_out, sizeof g_out, "%s", argv[6]);
    mkdir(g_out, 0755);
    snprintf(g_data, sizeof g_data, "%s/data", g_out);
    mkdir(g_data, 0755);
    /* Avatar: J2N_SERVER=host[:cong] chuyen ket noi 127.0.0.1; J2N_HD=TenLop[,TenLop]:lan (vd FishGame:2) */
    const char *srv = getenv("J2N_SERVER");
    if (srv && srv[0]) j2n_set_server(srv);
    /* J2N_SERVER_DAT=tep: dia chi may chu ma hoa (giong server.dat trong APK / IPA) */
    const char *sd = getenv("J2N_SERVER_DAT");
    if (sd && sd[0]) {
        unsigned char buf[512];
        FILE *fd = fopen(sd, "rb");
        int nr = fd ? (int)fread(buf, 1, sizeof buf, fd) : 0;
        if (fd) fclose(fd);
        fprintf(stderr, "[harness] server.dat: %s\n", j2n_set_server_enc(buf, nr) ? "hop le" : "KHONG hop le");
    }
    const char *ha = getenv("J2N_HDANH");
    if (ha && ha[0] == '1') j2n_set_hd_anh(1);
    const char *hk = getenv("J2N_HDK");
    if (hk && hk[0]) j2n_set_hd_k(atoi(hk));
    const char *hd = getenv("J2N_HD");
    if (hd && hd[0]) {
        char hb[256];
        snprintf(hb, sizeof hb, "%s", hd);
        char *c = strrchr(hb, ':');
        int sc = 2;
        if (c) {
            sc = atoi(c + 1);
            *c = 0;
        }
        j2n_set_hd(hb, sc);
    }
    j2n_set_keyboard_hook(kb_hook_log);
    if (!j2n_start(argv[2], W, H)) {
        fprintf(stderr, "khong khoi dong duoc\n");
        return 1;
    }
    const char *cmdf = argv[5];
    int live = strlen(cmdf) > 5 && strcmp(cmdf + strlen(cmdf) - 5, ".live") == 0;
    FILE *f = fopen(cmdf, "r");
    if (!f) {
        fprintf(stderr, "khong mo duoc %s\n", cmdf);
        for (;;) msleep(1000);
    }
    char line[4096];
    for (;;) {
        if (fgets(line, sizeof line, f)) {
            run_cmd(line);
        } else {
            if (!live) break;
            clearerr(f);
            msleep(100);
        }
    }
    for (;;) msleep(1000);
    return 0;
}
