/* Bo chay thu tren Linux: kich ban lenh (giong pmdrive), chup khung hinh ra PPM.
 *   army2n <jar> <lop MIDlet> <rong> <cao> <tep lenh> <thu muc ra>
 * Lenh: key N | down N | up N | type:abc | sleep MS | shot TEN | tap X Y | drag X1 Y1 X2 Y2 |
 *       text:chuoi (OK hop nhap chu) | textcancel | quit
 * Tep lenh ket thuc ".live": doc tiep dong moi ghi them.
 */
#include <errno.h>
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

void plat_present(const uint32_t *fb, int w, int h) { (void)fb; (void)w; (void)h; }

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
    uint32_t *buf = (uint32_t *)malloc((size_t)W * H * 4);
    int f = j2n_snapshot(buf, W, H);
    char p[1200];
    snprintf(p, sizeof p, "%s/%s.ppm", g_out, name);
    FILE *o = fopen(p, "wb");
    if (o) {
        fprintf(o, "P6\n%d %d\n255\n", W, H);
        for (int i = 0; i < W * H; i++) {
            uint8_t px[3] = {(uint8_t)(buf[i] >> 16), (uint8_t)(buf[i] >> 8), (uint8_t)buf[i]};
            fwrite(px, 1, 3, o);
        }
        fclose(o);
    }
    fprintf(stderr, "[harness] shot %s (khung %d)\n", name, f);
    free(buf);
}

static void run_cmd(char *line) {
    size_t n = strlen(line);
    while (n && (line[n - 1] == '\n' || line[n - 1] == '\r')) line[--n] = 0;
    if (!n || line[0] == '#') return;
    if (strncmp(line, "type:", 5) == 0) {
        for (char *c = line + 5; *c; c++) {
            j2n_post(J2N_KEY_PRESS, (unsigned char)*c, 0);
            msleep(60);
            j2n_post(J2N_KEY_RELEASE, (unsigned char)*c, 0);
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
    } else if (!strcmp(op, "drag")) {
        j2n_post(J2N_POINTER_PRESS, a, b);
        for (int i = 1; i <= 10; i++) {
            msleep(20);
            j2n_post(J2N_POINTER_DRAG, a + (c - a) * i / 10, b + (d - b) * i / 10);
        }
        j2n_post(J2N_POINTER_RELEASE, c, d);
    } else if (!strcmp(op, "quit")) {
        exit(0);
    }
}

int main(int argc, char **argv) {
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
