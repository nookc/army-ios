/* io.c - tai nguyen trong jar (zlib), RMS (tep), socket TCP, he thong, SMS, am thanh */
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <zlib.h>
#include "natives.h"
#include "platform.h"

/* ============================================================ jar */
typedef struct zent {
    char *name;
    uint32_t loff, csize, usize;
    uint16_t method;
} zent;

static uint8_t *jar;
static size_t jar_len;
static zent *ents;
static int nents;
static int *hidx;   /* bang bam ten -> chi so */
static int hcap;
static pthread_once_t jar_once = PTHREAD_ONCE_INIT;

static uint32_t rd16(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1] << 8); }
static uint32_t rd32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static uint32_t hstr(const char *s, size_t n) {
    uint32_t h = 2166136261u;
    for (size_t i = 0; i < n; i++) h = (h ^ (uint8_t)s[i]) * 16777619u;
    return h;
}

static void jar_load(void) {
    const char *path = plat_jar_path();
    FILE *f = path ? fopen(path, "rb") : 0;
    if (!f) {
        rt_log("[jar] khong mo duoc %s", path ? path : "(null)");
        return;
    }
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    jar = (uint8_t *)malloc((size_t)n);
    if (fread(jar, 1, (size_t)n, f) != (size_t)n) {
        fclose(f);
        free(jar);
        jar = 0;
        return;
    }
    fclose(f);
    jar_len = (size_t)n;
    /* tim end of central directory */
    long e = (long)jar_len - 22;
    while (e >= 0 && !(jar[e] == 'P' && jar[e + 1] == 'K' && jar[e + 2] == 5 && jar[e + 3] == 6)) e--;
    if (e < 0) {
        rt_log("[jar] hong (khong co EOCD)");
        return;
    }
    uint32_t count = rd16(jar + e + 10), cdoff = rd32(jar + e + 16);
    ents = (zent *)calloc(count, sizeof(zent));
    hcap = 1;
    while (hcap < (int)count * 2) hcap <<= 1;
    hidx = (int *)malloc(sizeof(int) * (size_t)hcap);
    for (int i = 0; i < hcap; i++) hidx[i] = -1;
    size_t p = cdoff;
    for (uint32_t i = 0; i < count && p + 46 <= jar_len; i++) {
        if (rd32(jar + p) != 0x02014b50u) break;
        uint16_t method = (uint16_t)rd16(jar + p + 10);
        uint32_t csize = rd32(jar + p + 20), usize = rd32(jar + p + 24);
        uint32_t nl = rd16(jar + p + 28), xl = rd16(jar + p + 30), cl = rd16(jar + p + 32);
        uint32_t loff = rd32(jar + p + 42);
        zent *z = &ents[nents];
        z->name = (char *)malloc(nl + 1);
        memcpy(z->name, jar + p + 46, nl);
        z->name[nl] = 0;
        z->method = method;
        z->csize = csize;
        z->usize = usize;
        z->loff = loff;
        uint32_t h = hstr(z->name, nl) & (uint32_t)(hcap - 1);
        while (hidx[h] >= 0) h = (h + 1) & (uint32_t)(hcap - 1);
        hidx[h] = nents;
        nents++;
        p += 46 + nl + xl + cl;
    }
}

static zent *jar_find(const char *name) {
    pthread_once(&jar_once, jar_load);
    if (!jar || !hidx) return 0;
    size_t n = strlen(name);
    uint32_t h = hstr(name, n) & (uint32_t)(hcap - 1);
    while (hidx[h] >= 0) {
        zent *z = &ents[hidx[h]];
        if (strcmp(z->name, name) == 0) return z;
        h = (h + 1) & (uint32_t)(hcap - 1);
    }
    return 0;
}

/* doc tep trong jar vao bo dem moi (malloc); NULL neu khong co */
uint8_t *j2n_jar_read(const char *name, size_t *len) {
    zent *z = jar_find(name);
    if (!z) return 0;
    size_t lp = z->loff;
    if (lp + 30 > jar_len || rd32(jar + lp) != 0x04034b50u) return 0;
    size_t data = lp + 30 + rd16(jar + lp + 26) + rd16(jar + lp + 28);
    if (data + z->csize > jar_len) return 0;
    uint8_t *out = (uint8_t *)malloc(z->usize ? z->usize : 1);
    if (z->method == 0) {
        memcpy(out, jar + data, z->usize);
    } else if (z->method == 8) {
        z_stream s;
        memset(&s, 0, sizeof s);
        if (inflateInit2(&s, -MAX_WBITS) != Z_OK) {
            free(out);
            return 0;
        }
        s.next_in = jar + data;
        s.avail_in = z->csize;
        s.next_out = out;
        s.avail_out = z->usize;
        int r = inflate(&s, Z_FINISH);
        inflateEnd(&s);
        if (r != Z_STREAM_END && s.avail_out != 0) {
            free(out);
            return 0;
        }
    } else {
        free(out);
        return 0;
    }
    *len = z->usize;
    return out;
}

jobj N_j2n_Res_read(jctx *t, jobj name) {
    if (!name) {
        rt_npe(t);
        return 0;
    }
    char *n = rt_str_cstr(name);
    size_t len = 0;
    uint8_t *d = j2n_jar_read(n, &len);
    if (!d) return 0;
    jobj arr = rt_newarr(t, &rt_arr_B, (int32_t)len);
    if (arr) memcpy(AD(arr, uint8_t), d, len);
    free(d);
    return arr;
}

jobj N_j2n_Res_appProperty(jctx *t, jobj key) {
    if (!key) return 0;
    char *k = rt_str_cstr(key);
    size_t len = 0;
    uint8_t *m = j2n_jar_read("META-INF/MANIFEST.MF", &len);
    if (!m) return 0;
    char *s = (char *)realloc(m, len + 1);
    s[len] = 0;
    size_t kl = strlen(k);
    jobj res = 0;
    char *line = s;
    while (line && *line) {
        char *nl = strchr(line, '\n');
        if (nl) *nl = 0;
        size_t ll = strlen(line);
        if (ll && line[ll - 1] == '\r') line[--ll] = 0;
        if (ll > kl && strncmp(line, k, kl) == 0 && line[kl] == ':') {
            char *v = line + kl + 1;
            while (*v == ' ') v++;
            res = rt_str_utf8(t, v);
            break;
        }
        line = nl ? nl + 1 : 0;
    }
    free(s);
    return res;
}

/* ============================================================ RMS */
static void rms_path(const char *name, char *out, size_t n) {
    char hex[200];
    int k = 0;
    for (const uint8_t *p = (const uint8_t *)name; *p && k < 190; p++) k += snprintf(hex + k, sizeof hex - (size_t)k, "%02x", *p);
    hex[k] = 0;
    snprintf(out, n, "%s/rms_%s.db", plat_data_dir(), hex);
}

jobj N_j2n_Rms_load(jctx *t, jobj name) {
    char p[1024];
    rms_path(rt_str_cstr(name), p, sizeof p);
    FILE *f = fopen(p, "rb");
    if (!f) return 0;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    jobj arr = rt_newarr(t, &rt_arr_B, (int32_t)n);
    if (arr && n > 0 && fread(AD(arr, uint8_t), 1, (size_t)n, f) != (size_t)n) arr = 0;
    fclose(f);
    return arr;
}

int32_t N_j2n_Rms_save(jctx *t, jobj name, jobj data) {
    (void)t;
    char p[1024], tmp[1100];
    rms_path(rt_str_cstr(name), p, sizeof p);
    snprintf(tmp, sizeof tmp, "%s.tmp", p);
    FILE *f = fopen(tmp, "wb");
    if (!f) return 0;
    size_t n = data ? (size_t)ALEN(data) : 0;
    int ok = n == 0 || fwrite(AD(data, uint8_t), 1, n, f) == n;
    ok = (fflush(f) == 0) && ok;
    fsync(fileno(f));
    fclose(f);
    if (!ok) {
        remove(tmp);
        return 0;
    }
    return rename(tmp, p) == 0;   /* ghi nguyen tu: mat dien giua chung khong hong kho cu */
}

int32_t N_j2n_Rms_delete(jctx *t, jobj name) {
    (void)t;
    char p[1024];
    rms_path(rt_str_cstr(name), p, sizeof p);
    return remove(p) == 0;
}

jobj N_j2n_Rms_list(jctx *t) {
    DIR *d = opendir(plat_data_dir());
    if (!d) return rt_newarr(t, rt_arrof(t, &K_java_lang_String), 0);
    char *names[256];
    int n = 0;
    struct dirent *e;
    while ((e = readdir(d)) && n < 256) {
        const char *s = e->d_name;
        size_t l = strlen(s);
        if (l > 7 && strncmp(s, "rms_", 4) == 0 && strcmp(s + l - 3, ".db") == 0) {
            size_t hl = (l - 7) / 2;
            char *nm = (char *)malloc(hl + 1);
            for (size_t i = 0; i < hl; i++) {
                unsigned v = 0;
                sscanf(s + 4 + 2 * i, "%2x", &v);
                nm[i] = (char)v;
            }
            nm[hl] = 0;
            names[n++] = nm;
        }
    }
    closedir(d);
    jobj arr = rt_newarr(t, rt_arrof(t, &K_java_lang_String), n);
    for (int i = 0; i < n; i++) {
        if (arr) AD(arr, jobj)[i] = rt_str_utf8(t, names[i]);
        free(names[i]);
    }
    return arr;
}

/* ============================================================ socket */
int32_t N_j2n_Net_connect(jctx *t, jobj host, int32_t port) {
    (void)t;
    char *h = rt_str_cstr(host);
    char ps[16];
    snprintf(ps, sizeof ps, "%d", port);
    struct addrinfo hints, *res = 0;
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
#ifdef AI_DEFAULT
    hints.ai_flags = AI_DEFAULT;     /* iOS: tu tong hop dia chi IPv6 tren mang NAT64 */
#endif
    if (getaddrinfo(h, ps, &hints, &res) != 0 || !res) {
        rt_log("[net] khong phan giai duoc %s", h);
        return -1;
    }
    int fd = -1;
    for (struct addrinfo *a = res; a; a = a->ai_next) {
        fd = socket(a->ai_family, a->ai_socktype, a->ai_protocol);
        if (fd < 0) continue;
        int fl = fcntl(fd, F_GETFL, 0);
        fcntl(fd, F_SETFL, fl | O_NONBLOCK);
        int r = connect(fd, a->ai_addr, a->ai_addrlen);
        if (r != 0 && errno == EINPROGRESS) {
            struct pollfd pf = {fd, POLLOUT, 0};
            r = poll(&pf, 1, 15000);
            if (r == 1) {
                int err = 0;
                socklen_t el = sizeof err;
                getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &el);
                r = err == 0 ? 0 : -1;
            } else {
                r = -1;
            }
        }
        if (r == 0) {
            fcntl(fd, F_SETFL, fl & ~O_NONBLOCK);
            int one = 1;
            setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);
            setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &one, sizeof one);
#ifdef SO_NOSIGPIPE
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, sizeof one);
#endif
            break;
        }
        close(fd);
        fd = -1;
    }
    freeaddrinfo(res);
    if (fd < 0) rt_log("[net] khong ket noi duoc %s:%d", h, port);
    return fd;
}

int32_t N_j2n_Net_read(jctx *t, int32_t fd, jobj b, int32_t off, int32_t len) {
    if (!b) {
        rt_npe(t);
        return -2;
    }
    for (;;) {
        ssize_t n = recv(fd, AD(b, uint8_t) + off, (size_t)len, 0);
        if (n > 0) return (int32_t)n;
        if (n == 0) return -1;
        if (errno == EINTR) continue;
        return -2;
    }
}

int32_t N_j2n_Net_write(jctx *t, int32_t fd, jobj b, int32_t off, int32_t len) {
    if (!b) {
        rt_npe(t);
        return -2;
    }
#ifdef MSG_NOSIGNAL
    int fl = MSG_NOSIGNAL;
#else
    int fl = 0;
#endif
    for (;;) {
        ssize_t n = send(fd, AD(b, uint8_t) + off, (size_t)len, fl);
        if (n >= 0) return (int32_t)n;
        if (errno == EINTR) continue;
        return -2;
    }
}

int32_t N_j2n_Net_available(jctx *t, int32_t fd) {
    (void)t;
    int n = 0;
    if (ioctl(fd, FIONREAD, &n) != 0) return 0;
    return n;
}

void N_j2n_Net_shutdown(jctx *t, int32_t fd) {
    (void)t;
    shutdown(fd, SHUT_RDWR);
}

void N_j2n_Net_close(jctx *t, int32_t fd) {
    (void)t;
    close(fd);
}

jobj N_j2n_Net_localAddress(jctx *t, int32_t fd) {
    struct sockaddr_storage ss;
    socklen_t l = sizeof ss;
    char b[64] = "0.0.0.0";
    if (getsockname(fd, (struct sockaddr *)&ss, &l) == 0) {
        if (ss.ss_family == AF_INET) inet_ntop(AF_INET, &((struct sockaddr_in *)&ss)->sin_addr, b, sizeof b);
        else if (ss.ss_family == AF_INET6) inet_ntop(AF_INET6, &((struct sockaddr_in6 *)&ss)->sin6_addr, b, sizeof b);
    }
    return rt_str_utf8(t, b);
}

int32_t N_j2n_Net_localPort(jctx *t, int32_t fd) {
    (void)t;
    struct sockaddr_storage ss;
    socklen_t l = sizeof ss;
    if (getsockname(fd, (struct sockaddr *)&ss, &l) != 0) return 0;
    if (ss.ss_family == AF_INET) return ntohs(((struct sockaddr_in *)&ss)->sin_port);
    if (ss.ss_family == AF_INET6) return ntohs(((struct sockaddr_in6 *)&ss)->sin6_port);
    return 0;
}

/* ============================================================ he thong */
jobj N_j2n_Sys_property0(jctx *t, jobj key) {
    (void)t; (void)key;
    return 0;
}

int32_t N_j2n_Sys_tzOffset(jctx *t) {
    (void)t;
    time_t now = time(0);
    struct tm lt;
    localtime_r(&now, &lt);
    return (int32_t)(lt.tm_gmtoff * 1000);
}

jobj N_j2n_Sys_tzId(jctx *t) {
    int32_t off = N_j2n_Sys_tzOffset(t) / 60000;
    char b[32];
    snprintf(b, sizeof b, "GMT%c%02d:%02d", off < 0 ? '-' : '+', abs(off) / 60, abs(off) % 60);
    return rt_str_utf8(t, b);
}

void N_j2n_Sys_log(jctx *t, jobj s) {
    (void)t;
    if (s) plat_log(rt_str_cstr(s));
}

/* ============================================================ SMS / am thanh */
int32_t N_j2n_SmsConn_sms(jctx *t, jobj num, jobj text) {
    (void)t;
    return plat_sms(num ? rt_str_cstr(num) : "", text ? rt_str_cstr(text) : "");
}

int32_t N_j2n_SoundPlayer_create(jctx *t, jobj data, jobj type) {
    (void)t;
    if (!data) return -1;
    return plat_sound_create(AD(data, uint8_t), ALEN(data), type ? rt_str_cstr(type) : "");
}

void N_j2n_SoundPlayer_start0(jctx *t, int32_t h, int32_t loops) { (void)t; plat_sound_start(h, loops); }
void N_j2n_SoundPlayer_stop0(jctx *t, int32_t h) { (void)t; plat_sound_stop(h); }
void N_j2n_SoundPlayer_close0(jctx *t, int32_t h) { (void)t; plat_sound_close(h); }
void N_j2n_SoundPlayer_volume0(jctx *t, int32_t h, int32_t l) { (void)t; plat_sound_volume(h, l); }
