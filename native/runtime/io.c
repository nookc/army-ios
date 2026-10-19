/* io.c - tai nguyen trong jar (zlib), RMS (tep), socket TCP, he thong, SMS, am thanh */
#ifdef _WIN32
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <io.h>
#include <wchar.h>
#else
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>
#endif
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#ifdef J2N_STB_ZLIB
/* khong co zlib he thong (Windows): dung bo giai nen deflate cua stb_image (gfx.c) */
int stbi_zlib_decode_noheader_buffer(char *obuffer, int olen, const char *ibuffer, int ilen);
#else
#include <zlib.h>
#endif
#include "natives.h"
#include "platform.h"

/* ============================================================ tep (Windows: duong dan UTF-8 -> UTF-16) */
#ifdef _WIN32
static wchar_t *u8w(const char *s) {
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, 0, 0);
    wchar_t *w = (wchar_t *)malloc(sizeof(wchar_t) * (size_t)(n > 0 ? n : 1));
    if (n <= 0 || !MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n)) w[0] = 0;
    return w;
}
static FILE *u8fopen(const char *path, const char *mode) {
    wchar_t *wp = u8w(path), *wm = u8w(mode);
    FILE *f = _wfopen(wp, wm);
    free(wp);
    free(wm);
    return f;
}
static int u8remove(const char *path) {
    wchar_t *wp = u8w(path);
    int r = DeleteFileW(wp) ? 0 : -1;
    free(wp);
    return r;
}
static int u8rename_over(const char *from, const char *to) {
    wchar_t *a = u8w(from), *b = u8w(to);
    int r = MoveFileExW(a, b, MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH) ? 0 : -1;
    free(a);
    free(b);
    return r;
}
static void file_sync(FILE *f) { _commit(_fileno(f)); }
#else
#define u8fopen fopen
#define u8remove remove
#define u8rename_over rename
static void file_sync(FILE *f) { fsync(fileno(f)); }
#endif

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
    size_t ml = 0;
    const uint8_t *mem = plat_jar_mem(&ml);
    if (mem && ml > 22) {
        jar = (uint8_t *)mem;              /* jar nam san trong bo nho (tai nguyen exe / asset) */
        jar_len = ml;
    } else {
        const char *path = plat_jar_path();
        FILE *f = path ? u8fopen(path, "rb") : 0;
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
    }
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
#ifdef J2N_STB_ZLIB
        int r = stbi_zlib_decode_noheader_buffer((char *)out, (int)z->usize, (const char *)jar + data, (int)z->csize);
        if (r != (int)z->usize) {
            free(out);
            return 0;
        }
#else
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
#endif
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
    FILE *f = u8fopen(p, "rb");
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
    FILE *f = u8fopen(tmp, "wb");
    if (!f) return 0;
    size_t n = data ? (size_t)ALEN(data) : 0;
    int ok = n == 0 || fwrite(AD(data, uint8_t), 1, n, f) == n;
    ok = (fflush(f) == 0) && ok;
    file_sync(f);
    fclose(f);
    if (!ok) {
        u8remove(tmp);
        return 0;
    }
    return u8rename_over(tmp, p) == 0;   /* ghi nguyen tu: mat dien giua chung khong hong kho cu */
}

int32_t N_j2n_Rms_delete(jctx *t, jobj name) {
    (void)t;
    char p[1024];
    rms_path(rt_str_cstr(name), p, sizeof p);
    return u8remove(p) == 0;
}

jobj N_j2n_Rms_list(jctx *t) {
#ifdef _WIN32
    wchar_t *wd = u8w(plat_data_dir());
    _WDIR *d = _wopendir(wd);
    free(wd);
#else
    DIR *d = opendir(plat_data_dir());
#endif
    if (!d) return rt_newarr(t, rt_arrof(t, &K_java_lang_String), 0);
    char *names[256];
    int n = 0;
#ifdef _WIN32
    struct _wdirent *e;
    char sb[600];
    while ((e = _wreaddir(d)) && n < 256) {
        if (!WideCharToMultiByte(CP_UTF8, 0, e->d_name, -1, sb, sizeof sb, 0, 0)) continue;
        const char *s = sb;
#else
    struct dirent *e;
    while ((e = readdir(d)) && n < 256) {
        const char *s = e->d_name;
#endif
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
#ifdef _WIN32
    _wclosedir(d);
#else
    closedir(d);
#endif
    jobj arr = rt_newarr(t, rt_arrof(t, &K_java_lang_String), n);
    for (int i = 0; i < n; i++) {
        if (arr) AD(arr, jobj)[i] = rt_str_utf8(t, names[i]);
        free(names[i]);
    }
    return arr;
}

/* ============================================================ socket */
#ifdef _WIN32
static pthread_once_t wsa_once = PTHREAD_ONCE_INIT;
static void wsa_init(void) {
    WSADATA w;
    WSAStartup(MAKEWORD(2, 2), &w);
}
#define SOCK_CLOSE closesocket
#else
#define SOCK_CLOSE close
#endif

/* loi mang gan nhat cua luong nay: dua vao thong bao ngoai le (Net.error) + nhat ky */
static __thread char net_err[200];

static void net_seterr(const char *what, const char *h, int port, int code, const char *desc) {
    if (desc && *desc) {
        snprintf(net_err, sizeof net_err, "%s %s:%d (loi %d: %s)", what, h, port, code, desc);
    } else {
        snprintf(net_err, sizeof net_err, "%s %s:%d (loi %d)", what, h, port, code);
    }
    rt_log("[net] %s", net_err);
}

#define NET_CONNECT_MS 15000

static int64_t net_now_ms(void) {
#ifdef _WIN32
    return (int64_t)GetTickCount64();
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
#endif
}

int32_t N_j2n_Net_connect(jctx *t, jobj host, int32_t port) {
    (void)t;
#ifdef _WIN32
    pthread_once(&wsa_once, wsa_init);
#endif
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
    int gr = EAI_AGAIN;
    for (int k = 0; k < 3; k++) {        /* EAI_AGAIN (DNS tam loi / bi ngat): thu lai */
        gr = getaddrinfo(h, ps, &hints, &res);
        if (gr == 0 && res) break;
        if (res) {
            freeaddrinfo(res);
            res = 0;
        }
        if (gr != EAI_AGAIN) break;
    }
    if (gr != 0 || !res) {
#ifdef _WIN32
        net_seterr("khong phan giai duoc", h, port, gr, 0);
#else
        net_seterr("khong phan giai duoc", h, port, gr, gai_strerror(gr));
#endif
        return -1;
    }
    int fd = -1, err = 0;
    for (struct addrinfo *a = res; a; a = a->ai_next) {
#ifdef _WIN32
        SOCKET sk = socket(a->ai_family, a->ai_socktype, a->ai_protocol);
        if (sk == INVALID_SOCKET) {
            err = WSAGetLastError();
            continue;
        }
        fd = (int)sk;
        u_long nb = 1;
        ioctlsocket(sk, FIONBIO, &nb);
        int r = connect(sk, a->ai_addr, (int)a->ai_addrlen);
        if (r != 0 && WSAGetLastError() == WSAEWOULDBLOCK) {
            fd_set wf, ef;
            FD_ZERO(&wf);
            FD_ZERO(&ef);
            FD_SET(sk, &wf);
            FD_SET(sk, &ef);
            struct timeval tv = {NET_CONNECT_MS / 1000, 0};
            r = select(0, 0, &wf, &ef, &tv);
            if (r == 1 && FD_ISSET(sk, &wf)) {
                int e2 = 0;
                int el = sizeof e2;
                getsockopt(sk, SOL_SOCKET, SO_ERROR, (char *)&e2, &el);
                r = e2 == 0 ? 0 : -1;
                if (e2) err = e2;
            } else {
                if (r == 0) {
                    err = WSAETIMEDOUT;
                } else {
                    int e2 = 0;
                    int el = sizeof e2;
                    getsockopt(sk, SOL_SOCKET, SO_ERROR, (char *)&e2, &el);
                    err = e2 ? e2 : WSAGetLastError();
                }
                r = -1;
            }
        } else if (r != 0) {
            err = WSAGetLastError();
        }
        if (r == 0) {
            nb = 0;
            ioctlsocket(sk, FIONBIO, &nb);
            BOOL one = TRUE;
            setsockopt(sk, IPPROTO_TCP, TCP_NODELAY, (const char *)&one, sizeof one);
            setsockopt(sk, SOL_SOCKET, SO_KEEPALIVE, (const char *)&one, sizeof one);
            break;
        }
        closesocket(sk);
        fd = -1;
#else
        fd = socket(a->ai_family, a->ai_socktype, a->ai_protocol);
        if (fd < 0) {
            err = errno;
            continue;
        }
        int fl = fcntl(fd, F_GETFL, 0);
        fcntl(fd, F_SETFL, fl | O_NONBLOCK);
        int r = connect(fd, a->ai_addr, a->ai_addrlen);
        if (r != 0 && (errno == EINPROGRESS || errno == EINTR || errno == EAGAIN)) {
            /* Boehm GC dung cac luong bang tin hieu (Linux / Android): poll bi ngat (EINTR) thi doi tiep
               phan thoi gian con lai, khong coi la ket noi hong */
            int64_t end = net_now_ms() + NET_CONNECT_MS;
            for (;;) {
                int64_t left = end - net_now_ms();
                if (left < 0) left = 0;
                struct pollfd pf = {fd, POLLOUT, 0};
                r = poll(&pf, 1, (int)left);
                if (r < 0 && errno == EINTR) continue;
                break;
            }
            if (r == 1) {
                int e2 = 0;
                socklen_t el = sizeof e2;
                getsockopt(fd, SOL_SOCKET, SO_ERROR, &e2, &el);
                r = e2 == 0 ? 0 : -1;
                if (e2) err = e2;
            } else {
                err = r == 0 ? ETIMEDOUT : errno;
                r = -1;
            }
        } else if (r != 0) {
            err = errno;
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
#endif
    }
    freeaddrinfo(res);
    if (fd < 0) {
#ifdef _WIN32
        net_seterr("khong ket noi duoc", h, port, err, 0);
#else
        net_seterr("khong ket noi duoc", h, port, err, strerror(err));
#endif
    }
    return fd;
}

jobj N_j2n_Net_error(jctx *t) { return rt_str_utf8(t, net_err[0] ? net_err : "loi mang"); }

/* thoi gian cho toi da moi lan doc / ghi (ms); 0 = cho mai (mac dinh) */
void N_j2n_Net_timeout(jctx *t, int32_t fd, int32_t ms) {
    (void)t;
    if (ms < 0) ms = 0;
#ifdef _WIN32
    DWORD v = (DWORD)ms;
    setsockopt((SOCKET)fd, SOL_SOCKET, SO_RCVTIMEO, (const char *)&v, sizeof v);
    setsockopt((SOCKET)fd, SOL_SOCKET, SO_SNDTIMEO, (const char *)&v, sizeof v);
#else
    struct timeval tv;
    tv.tv_sec = ms / 1000;
    tv.tv_usec = (ms % 1000) * 1000;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof tv);
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof tv);
#endif
}

int32_t N_j2n_Net_read(jctx *t, int32_t fd, jobj b, int32_t off, int32_t len) {
    if (!b) {
        rt_npe(t);
        return -2;
    }
    for (;;) {
#ifdef _WIN32
        int n = recv((SOCKET)fd, (char *)AD(b, uint8_t) + off, len, 0);
        if (n > 0) return n;
        if (n == 0) return -1;
        int we = WSAGetLastError();
        if (we == WSAEINTR) continue;
        snprintf(net_err, sizeof net_err, "loi doc mang (loi %d%s)", we, we == WSAETIMEDOUT ? ": het thoi gian cho" : "");
        return -2;
#else
        ssize_t n = recv(fd, AD(b, uint8_t) + off, (size_t)len, 0);
        if (n > 0) return (int32_t)n;
        if (n == 0) return -1;
        if (errno == EINTR) continue;
        snprintf(net_err, sizeof net_err, "loi doc mang (loi %d: %s)", errno,
                 errno == EAGAIN || errno == EWOULDBLOCK ? "het thoi gian cho" : strerror(errno));
        return -2;
#endif
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
#ifdef _WIN32
        int n = send((SOCKET)fd, (const char *)AD(b, uint8_t) + off, len, fl);
        if (n >= 0) return n;
        if (WSAGetLastError() == WSAEINTR) continue;
        return -2;
#else
        ssize_t n = send(fd, AD(b, uint8_t) + off, (size_t)len, fl);
        if (n >= 0) return (int32_t)n;
        if (errno == EINTR) continue;
        return -2;
#endif
    }
}

int32_t N_j2n_Net_available(jctx *t, int32_t fd) {
    (void)t;
#ifdef _WIN32
    u_long n = 0;
    if (ioctlsocket((SOCKET)fd, FIONREAD, &n) != 0) return 0;
    return (int32_t)n;
#else
    int n = 0;
    if (ioctl(fd, FIONREAD, &n) != 0) return 0;
    return n;
#endif
}

void N_j2n_Net_shutdown(jctx *t, int32_t fd) {
    (void)t;
#ifdef _WIN32
    shutdown((SOCKET)fd, SD_BOTH);
#else
    shutdown(fd, SHUT_RDWR);
#endif
}

void N_j2n_Net_close(jctx *t, int32_t fd) {
    (void)t;
    SOCK_CLOSE(fd);
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
#ifdef _WIN32
    struct tm g;
    gmtime_s(&g, &now);
    g.tm_isdst = -1;
    time_t gt = mktime(&g);              /* gio dia phuong hieu nhu UTC -> do lech mui gio */
    return (int32_t)((now - gt) * 1000);
#else
    struct tm lt;
    localtime_r(&now, &lt);
    return (int32_t)(lt.tm_gmtoff * 1000);
#endif
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

int midi_to_wav(const uint8_t *m, int n, uint8_t **out, int *outlen);

int32_t N_j2n_SoundPlayer_create(jctx *t, jobj data, jobj type) {
    (void)t;
    if (!data) return -1;
    const char *ty = type ? rt_str_cstr(type) : "";
    const uint8_t *d = AD(data, uint8_t);
    int n = ALEN(data);
    if (strstr(ty, "mid") || (n > 4 && memcmp(d, "MThd", 4) == 0)) {
        /* MIDI: dung san thanh WAV (nen tang chi can phat PCM) */
        uint8_t *w = 0;
        int wl = 0;
        if (midi_to_wav(d, n, &w, &wl)) {
            int h = plat_sound_create(w, wl, "audio/x-wav");
            free(w);
            return h;
        }
        return -1;
    }
    return plat_sound_create(d, n, ty);
}

void N_j2n_SoundPlayer_start0(jctx *t, int32_t h, int32_t loops) { (void)t; plat_sound_start(h, loops); }
void N_j2n_SoundPlayer_stop0(jctx *t, int32_t h) { (void)t; plat_sound_stop(h); }
void N_j2n_SoundPlayer_close0(jctx *t, int32_t h) { (void)t; plat_sound_close(h); }
void N_j2n_SoundPlayer_volume0(jctx *t, int32_t h, int32_t l) { (void)t; plat_sound_volume(h, l); }
