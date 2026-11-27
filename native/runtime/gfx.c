/* gfx.c - man hinh (bo dem khung), Graphics, Image (giai ma PNG), Font. Ngu nghia ve bam theo bo chay phoneME cu. */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "natives.h"

#define STBI_ONLY_PNG
#define STBI_ONLY_JPEG
#define STBI_ONLY_GIF
#define STBI_ONLY_BMP
#define STBI_NO_STDIO
#define STBI_NO_HDR
#define STBI_NO_LINEAR
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#include "font_data.h"
#include "hdscale.c"   /* may HD: phong to anh (xBRZ / bicubic). Gop vao day de cac kich ban build khong phai them tep */

#define G(o) ((struct O_javax_microedition_lcdui_Graphics *)(o))
#define IMG(o) ((struct O_javax_microedition_lcdui_Image *)(o))
#define FNT(o) ((struct O_javax_microedition_lcdui_Font *)(o))

/* ------------------------------------------------------------ bo dem man hinh */
uint32_t *gfx_fb;
int gfx_w, gfx_h;

void gfx_init(int w, int h) {
    gfx_w = w;
    gfx_h = h;
    gfx_fb = (uint32_t *)calloc((size_t)w * (size_t)h, 4);
    for (int i = 0; i < w * h; i++) gfx_fb[i] = 0xFF000000u;
}

typedef struct tgt {
    uint32_t *px;
    int w, h;            /* kich thuoc diem anh that cua dich (hang = w) */
    int k;               /* Avatar: diem anh that cho 1 diem logic (man net cao x3), con lai 1 */
    int x0, y0, x1, y1;  /* vung cat [x0,x1) x [y0,y1) trong toa do LOGIC cua dich */
    int tx, ty;
    uint32_t color;      /* 0xFFRRGGBB */
    int hdp;             /* Avatar: ve hinh o diem anh that (man net cao, lop phu dau truong bat) */
} tgt;

/* Avatar: khung nay co ve anh net cao (anh dt3/, hd > 1) -> j2n.Ui ve khung sau o man net cao */
volatile int gfx_hd_seen;

/* ------------------------------------------------------------ may HD (ban "Avatar 250 HD")
 * gfx_hdk = K (2..4): moi dich ve (man hinh, anh mutable) co K diem anh that cho 1 diem logic; game van ve theo toa do
 * logic. Anh bat bien duoc phong to thong minh len dung he so cua dich lan dau duoc ve (Image.big), hinh ve min o diem anh
 * that. 0 = tat (moi thu y nhu truoc). Nen tang dat truoc khi chay game: j2n_set_hd_k(). */
int gfx_hdk;

void j2n_set_hd_k(int k) { gfx_hdk = (k >= 2 && k <= 4) ? k : 0; }   /* truoc khi chay game; dang chay: gfx_hd_set_k */

/* anh lon hon muc nay (diem anh that sau phong to) thi phong to it hon / giu nguyen: tranh ngon bo nho */
#include "hdkho.c"
#include "hd3d.c"

/* lay (va ghim) ban he so rq cua anh bat bien; NULL = chua co (dang lam / khong lam duoc) -> ve tam bang diem anh goc */
static hd_ent *hd_get(struct O_javax_microedition_lcdui_Image *im, int rq) {
    int s = im->f_hd_I > 1 ? im->f_hd_I : 1;
    {
        /* duong nhanh khong khoa (hau het lan ve): ghim truoc, kiem tra sau. Ben thu hoi (hd_drop_try) doi trang thai
         * truoc roi moi xem so ghim -> it nhat 1 ben thay ben kia (thu tu toan cuc) nen khong bao gio ve ban da bi bo */
        int sl = im->f_bslot_I - 1;
        if (sl >= 0 && sl < HD_SLOTS) {
            hd_ent *f = &hd_tab[sl];
            __atomic_add_fetch(&f->pins, 1, __ATOMIC_SEQ_CST);
            int st = __atomic_load_n(&f->state, __ATOMIC_SEQ_CST);
            if ((st == HD_READY || st == HD_NONE) && f->gen == (uint32_t)im->f_bgen_I && f->rq == rq
                && (f->mode | gfx_hd_mode) == f->mode) {
                f->last = hd_frame_no;
                if (st == HD_READY) return f;
                __atomic_sub_fetch(&f->pins, 1, __ATOMIC_SEQ_CST);
                return 0;               /* khong lam duoc ban net cao: ve ban goc */
            }
            __atomic_sub_fetch(&f->pins, 1, __ATOMIC_SEQ_CST);
        }
    }
    int want_mode = gfx_hd_mode;
    pthread_mutex_lock(&hd_mu);
    if (hd_free_top == -2) {
        hd_free_top = -1;
        for (int i = HD_SLOTS - 1; i >= 0; i--) hd_free_stk[++hd_free_top] = i;
    }
    int slot = im->f_bslot_I - 1;
    hd_ent *e = 0;
    if (slot >= 0 && slot < HD_SLOTS) {
        e = &hd_tab[slot];
        if (e->gen != (uint32_t)im->f_bgen_I || e->state == HD_FREE) e = 0;
        else if (e->rq != rq || ((e->mode | gfx_hd_mode) != e->mode && (e->state == HD_READY || e->state == HD_NONE))) {
            /* doi he so (hiem: K vua doi) / anh tung ve o giao dien nay duoc ve trong the gioi (can khoi noi, bong):
             * bo ban cu neu duoc roi lam lai */
            want_mode = e->mode | gfx_hd_mode;
            if (!hd_drop_try(slot)) {
                pthread_mutex_unlock(&hd_mu);
                return 0;
            }
            e = 0;
        }
    }
    if (!e) {
        if (!im->f_argb__5b || im->f_w_I <= 0 || im->f_h_I <= 0 || im->f_w_I % s || im->f_h_I % s) {
            pthread_mutex_unlock(&hd_mu);
            return 0;
        }
        int lw = im->f_w_I / s, lh = im->f_h_I / s, k = rq;
        while (k > 1 && (int64_t)lw * k * lh * k > HD_BIG_MAX) k--;
        size_t est = (size_t)lw * k * lh * k * 4;
        int md = (gfx_hd_fx && im->f_smooth_I != 1) ? want_mode : 0;
        int ok = k > 1 && (k != s || md != 0);        /* cung he so van lam neu can hieu ung (ban sao da xu ly) */
        if (ok && !hd_room(est)) {
            /* het han muc va moi thu dang duoc dung: ve tam ban goc, thu lai o khung sau */
            pthread_mutex_unlock(&hd_mu);
            return 0;
        }
        if (hd_free_top < 0) {
            for (int thu = 0; thu < 8 && hd_free_top < 0; thu++) {
                int v = hd_oldest(3, 0);
                if (v < 0) break;
                hd_drop_try(v);
            }
            if (hd_free_top < 0) {
                pthread_mutex_unlock(&hd_mu);
                return 0;
            }
        }
        slot = hd_free_stk[hd_free_top--];
        e = &hd_tab[slot];
        im->f_bslot_I = slot + 1;
        im->f_bgen_I = (int32_t)e->gen;
        e->rq = rq;
        e->k = k;
        e->px = 0;
        e->shd = 0;
        e->shm = 0;
        e->pins = 0;
        e->last = hd_frame_no;
        if (!ok) {
            e->mode = want_mode;        /* khong lam duoc: nho che do de khong thu lai moi lan ve */
            e->state = HD_NONE;
            pthread_mutex_unlock(&hd_mu);
            return 0;
        }
        e->src = AD(im->f_argb__5b, uint32_t);
        e->sw = im->f_w_I;
        e->sh = im->f_h_I;
        e->s = s;
        e->smooth = im->f_smooth_I;
        e->mode = md;
        e->est = est;
        e->next = -1;
        hd_pin[slot] = im->f_argb__5b;
        hd_pend += est;
        if (hd_nworker == 0) hd_start_workers();
        if (hd_nworker < 0 || (lw * lh <= HD_SYNC_PX && hd_sync_left > 0)) {
            /* anh nho: lam ngay (net tu khung dau), co han muc thoi gian moi khung */
            e->state = HD_BUILD;
            const uint32_t *src = e->src;
            int mode = e->mode, smooth = e->smooth, sw = e->sw, sh = e->sh;
            pthread_mutex_unlock(&hd_mu);
            int64_t t0 = hd_now_us();
            int ow = 0, oh = 0, kind = 0, shm = 0;
            uint8_t *shp = 0;
            uint32_t *px = hd_do(src, sw, sh, s, k, smooth, mode, &ow, &oh, &kind, &shp, &shm);
            int64_t dt = hd_now_us() - t0;
            pthread_mutex_lock(&hd_mu);
            hd_sync_left -= (int)dt;
            hd_pend -= est;
            hd_pin[slot] = 0;
            e->src = 0;
            if (!px) {
                e->state = HD_NONE;
                pthread_mutex_unlock(&hd_mu);
                return 0;
            }
            e->px = px;
            e->w = ow;
            e->h = oh;
            e->kind = kind;
            e->shd = shp;
            e->shm = shm;
            hd_bytes += (size_t)ow * oh * 4 + (shp ? (size_t)ow * (oh + shm) : 0);
            hd_nready++;
            __atomic_store_n(&e->state, HD_READY, __ATOMIC_SEQ_CST);
        } else {
            e->state = HD_WAIT;
            if (hd_qt >= 0) hd_tab[hd_qt].next = slot;
            else hd_qh = slot;
            hd_qt = slot;
            hd_qn++;
            pthread_cond_signal(&hd_cv);
            pthread_mutex_unlock(&hd_mu);
            return 0;
        }
    }
    e->last = hd_frame_no;
    if (e->state != HD_READY) {
        pthread_mutex_unlock(&hd_mu);
        return 0;
    }
    __atomic_add_fetch(&e->pins, 1, __ATOMIC_SEQ_CST);
    pthread_mutex_unlock(&hd_mu);
    return e;
}

/* bo dem ve cua anh cho dich he so k: diem anh, be rong hang, he so, kieu alpha. *pin != NULL: ve xong phai hd_put(*pin) */
static const uint32_t *img_px(jctx *t, struct O_javax_microedition_lcdui_Image *im, int k, int *stride, int *sc, int *kind,
                              hd_ent **pin) {
    (void)t;
    int s = im->f_hd_I > 1 ? im->f_hd_I : 1;
    *pin = 0;
    if (gfx_hdk > 1 && k > 1 && !im->f_mutable_Z) {
        int rq = k > gfx_hdk ? gfx_hdk : k;
        if (s != rq || (gfx_hd_mode && im->f_smooth_I != 1) || im->f_bslot_I) {
            hd_ent *e = hd_get(im, rq);
            if (e) {
                *pin = e;
                *stride = e->w;
                *sc = e->k;
                *kind = e->kind;
                return e->px;
            }
        }
    }
    *stride = im->f_w_I;
    *sc = s;
    *kind = im->f_akind_I;
    return AD(im->f_argb__5b, uint32_t);
}

static int target(jctx *t, jobj g, tgt *o) {
    struct O_javax_microedition_lcdui_Graphics *gr = G(g);
    jobj img = gr->f_img_L;
    if (img) {
        jobj arr = IMG(img)->f_argb__5b;
        if (!arr) {
            rt_npe(t);
            return 0;
        }
        o->px = AD(arr, uint32_t);
        o->w = IMG(img)->f_w_I;
        o->h = IMG(img)->f_h_I;
    } else {
        o->px = gfx_fb;
        o->w = gfx_w;
        o->h = gfx_h;
    }
    int k = gr->f_k_I;
    if (k < 1 || k > 8) k = 1;
    o->k = k;
    int lw = o->w / k, lh = o->h / k;   /* kich thuoc logic */
    int x0 = gr->f_cx_I, y0 = gr->f_cy_I;
    int x1 = x0 + gr->f_cw_I, y1 = y0 + gr->f_ch_I;
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    if (x1 > lw) x1 = lw;
    if (y1 > lh) y1 = lh;
    o->x0 = x0;
    o->y0 = y0;
    o->x1 = x1;
    o->y1 = y1;
    o->tx = gr->f_tx_I;
    o->ty = gr->f_ty_I;
    o->hdp = gr->f_hdp_Z && k > 1;
    o->color = 0xFF000000u | ((uint32_t)gr->f_color_I & 0xFFFFFFu);
    return x1 > x0 && y1 > y0;
}

#if defined(__aarch64__)
#include <arm_neon.h>
#define ROW_NEON 1
#elif defined(__SSE2__)
#include <emmintrin.h>
#define ROW_SSE2 1
#endif

static inline uint32_t blend(uint32_t s, uint32_t d) {
    uint32_t a = s >> 24;
    if (a == 255) return s;
    if (a == 0) return d;
    uint32_t ia = 255 - a;
    uint32_t rb = (s & 0xFF00FFu) * a + (d & 0xFF00FFu) * ia + 0x800080u;
    rb = ((rb + ((rb >> 8) & 0xFF00FFu)) >> 8) & 0xFF00FFu;
    uint32_t gg = (s & 0x00FF00u) * a + (d & 0x00FF00u) * ia + 0x008000u;
    gg = ((gg + ((gg >> 8) & 0x00FF00u)) >> 8) & 0x00FF00u;
    return 0xFF000000u | rb | gg;
}

/* dem diem anh ve theo loai (thu nghiem, J2N_HDLOG): 0 dac, 1 khoa mau, 2 tron, 3 to mau */
int64_t gfx_dem[4];

/* to 1 hang cung mau (nhanh) */
static inline void row_fill(uint32_t *d, uint32_t c, int n) {
    int i = 0;
#if defined(__aarch64__)
    {
        uint32_t v4[4] = {c, c, c, c};
        uint32x4_t v = vld1q_u32(v4);
        for (; i + 8 <= n; i += 8) {
            vst1q_u32(d + i, v);
            vst1q_u32(d + i + 4, v);
        }
    }
#elif defined(__SSE2__)
    {
        __m128i v = _mm_set1_epi32((int)c);
        for (; i + 8 <= n; i += 8) {
            _mm_storeu_si128((__m128i *)(d + i), v);
            _mm_storeu_si128((__m128i *)(d + i + 4), v);
        }
    }
#endif
    for (; i < n; i++) d[i] = c;
}

static inline void pset(tgt *o, int x, int y) {
    if (o->k == 1) {
        o->px[(size_t)y * o->w + x] = o->color;
        return;
    }
    int k = o->k;
    uint32_t c = o->color;
    uint32_t *p = o->px + (size_t)y * k * o->w + (size_t)x * k;
    for (int r = 0; r < k; r++, p += o->w)
        for (int i = 0; i < k; i++) p[i] = c;
}

/* ghi gia tri v (da xu ly alpha theo kind) vao diem logic (x, y) */
static inline void pput(tgt *o, int x, int y, uint32_t v, int kind) {
    if (o->k == 1) {
        uint32_t *d = o->px + (size_t)y * o->w + x;
        if (kind == 0) *d = v | 0xFF000000u;
        else if (kind == 1) {
            if (v >> 24) *d = v | 0xFF000000u;
        } else *d = blend(v, *d);
        return;
    }
    if (kind != 0 && (v >> 24) == 0) return;
    int k = o->k;
    uint32_t *p = o->px + (size_t)y * k * o->w + (size_t)x * k;
    if (kind == 2 && (v >> 24) != 255) {
        for (int r = 0; r < k; r++, p += o->w)
            for (int i = 0; i < k; i++) p[i] = blend(v, p[i]);
        return;
    }
    v |= 0xFF000000u;
    for (int r = 0; r < k; r++, p += o->w)
        for (int i = 0; i < k; i++) p[i] = v;
}

static inline void plot(tgt *o, int x, int y) {
    if (x >= o->x0 && x < o->x1 && y >= o->y0 && y < o->y1) pset(o, x, y);
}

/* Avatar: chuyen dich sang toa do diem anh that (hinh ve min tren man net cao); tra ve he so cu */
static int phys(tgt *o) {
    int k = o->k;
    o->k = 1;
    o->x0 *= k;
    o->y0 *= k;
    o->x1 *= k;
    o->y1 *= k;
    o->tx *= k;
    o->ty *= k;
    o->hdp = 0;
    return k;
}

/* neo: 0 -> TOP|LEFT; phai co dung 1 bit ngang + 1 bit doc (anh: TOP/BOTTOM/VCENTER, chu: TOP/BOTTOM/BASELINE) */
static int anchor_pos(jctx *t, int anchor, int text, int x, int y, int w, int h, int base, int *ox, int *oy) {
    if (anchor == 0) anchor = 4 | 16;
    int hz = anchor & (1 | 4 | 8);
    int vmask = text ? (16 | 32 | 64) : (16 | 32 | 2);
    int vt = anchor & vmask;
    int allowed = 1 | 4 | 8 | vmask;
    if (!hz || (hz & (hz - 1)) || !vt || (vt & (vt - 1)) || (anchor & ~allowed)) {
        rt_err(t, "java/lang/IllegalArgumentException", "anchor");
        return 0;
    }
    if (hz == 8) x -= w;
    else if (hz == 1) x -= w / 2;
    if (vt == 32) y -= h;
    else if (vt == 2) y -= h / 2;
    else if (vt == 64) y -= base;
    *ox = x;
    *oy = y;
    return 1;
}

/* ------------------------------------------------------------ duong thang (giong draw_line_absolute) */
static void line_abs(tgt *o, int x1, int y1, int x2, int y2) {
    int64_t sx0 = x1, sy0 = y1;
    uint64_t dx = (uint64_t)llabs((int64_t)x2 - sx0), dy = (uint64_t)llabs((int64_t)y2 - sy0);
    int64_t stx = x1 <= x2 ? 1 : -1, sty = y1 <= y2 ? 1 : -1;
    int64_t cl = o->x0, ct = o->y0, cr = (int64_t)o->x1 - 1, cb = (int64_t)o->y1 - 1;
    int xmaj = dx >= dy;
    uint64_t maj = xmaj ? dx : dy, mnr = xmaj ? dy : dx;
    int64_t start = xmaj ? sx0 : sy0, step = xmaj ? stx : sty;
    int64_t lo = xmaj ? cl : ct, hi = xmaj ? cr : cb;
    int64_t first = 0, last = (int64_t)maj;
    if (step > 0) {
        if (lo - start > first) first = lo - start;
        if (hi - start < last) last = hi - start;
    } else {
        if (start - hi > first) first = start - hi;
        if (start - lo < last) last = start - lo;
    }
    if (first > last || last < 0) return;
    if (first < 0) first = 0;
    for (uint64_t s = (uint64_t)first; s <= (uint64_t)last; s++) {
        uint64_t m = 0;
        if (maj != 0 && mnr != 0) {
            uint64_t p = s * mnr;
            m = p / maj + ((p % maj) * 2 >= maj ? 1 : 0);
        }
        int64_t cx = xmaj ? sx0 + stx * (int64_t)s : sx0 + stx * (int64_t)m;
        int64_t cy = xmaj ? sy0 + sty * (int64_t)m : sy0 + sty * (int64_t)s;
        if (cx >= cl && cx <= cr && cy >= ct && cy <= cb) pset(o, (int)cx, (int)cy);
    }
}

void N_javax_microedition_lcdui_Graphics_drawLine(jctx *t, jobj self, int32_t x1, int32_t y1, int32_t x2, int32_t y2) {
    tgt o;
    if (!target(t, self, &o)) return;
    if (o.hdp) {
        int lx1 = x1 + o.tx, ly1 = y1 + o.ty, lx2 = x2 + o.tx, ly2 = y2 + o.ty;
        int k = phys(&o), c = k / 2;
        int X1 = lx1 * k + c, Y1 = ly1 * k + c, X2 = lx2 * k + c, Y2 = ly2 * k + c;
        int xmaj = abs(X2 - X1) >= abs(Y2 - Y1);
        for (int d = -(k - 1) / 2; d <= k / 2; d++) {
            if (xmaj) line_abs(&o, X1, Y1 + d, X2, Y2 + d);
            else line_abs(&o, X1 + d, Y1, X2 + d, Y2);
        }
        return;
    }
    line_abs(&o, x1 + o.tx, y1 + o.ty, x2 + o.tx, y2 + o.ty);
}

static void fill_abs(tgt *o, int x, int y, int w, int h) {
    int x0 = x, y0 = y, x1 = x + w, y1 = y + h;
    if (x0 < o->x0) x0 = o->x0;
    if (y0 < o->y0) y0 = o->y0;
    if (x1 > o->x1) x1 = o->x1;
    if (y1 > o->y1) y1 = o->y1;
    if (x1 <= x0 || y1 <= y0) return;
    uint32_t c = o->color;
    int k = o->k;
    x0 *= k;
    x1 *= k;
    y0 *= k;
    y1 *= k;
    gfx_dem[3] += (int64_t)(x1 - x0) * (y1 - y0);
    for (int yy = y0; yy < y1; yy++) row_fill(o->px + (size_t)yy * o->w + x0, c, x1 - x0);
}

void N_javax_microedition_lcdui_Graphics_fillRect(jctx *t, jobj self, int32_t x, int32_t y, int32_t w, int32_t h) {
    if (w <= 0 || h <= 0) return;
    tgt o;
    if (!target(t, self, &o)) return;
    fill_abs(&o, x + o.tx, y + o.ty, w, h);
}

void N_javax_microedition_lcdui_Graphics_drawRect(jctx *t, jobj self, int32_t x, int32_t y, int32_t w, int32_t h) {
    if (w < 0 || h < 0) return;
    tgt o;
    if (!target(t, self, &o)) return;
    int ax = x + o.tx, ay = y + o.ty;
    line_abs(&o, ax, ay, ax + w, ay);
    line_abs(&o, ax, ay, ax, ay + h);
    line_abs(&o, ax + w, ay, ax + w, ay + h);
    line_abs(&o, ax, ay + h, ax + w, ay + h);
}

static int half_mag(int v, int max) {
    int64_t m = v < 0 ? -(int64_t)v : v;
    m /= 2;
    return (int)(m < max ? m : max);
}

static int rounded_contains(int lx, int ly, int w, int h, int rx, int ry) {
    if (lx < 0 || ly < 0 || lx >= w || ly >= h) return 0;
    if (rx <= 0 || ry <= 0 || (lx >= rx && lx < w - rx) || (ly >= ry && ly < h - ry)) return 1;
    double cx = lx < rx ? rx - 0.5 : (w - rx) - 0.5;
    double cy = ly < ry ? ry - 0.5 : (h - ry) - 0.5;
    double nx = (lx + 0.5 - cx) / rx, ny = (ly + 0.5 - cy) / ry;
    return nx * nx + ny * ny <= 1.0;
}

/* doan [L, R] cua hang ly nam trong hinh chu nhat bo goc (dung dung dieu kien rounded_contains); 0 = hang rong */
static int rr_span(int ly, int w, int h, int rx, int ry, int *L, int *R) {
    if (w <= 0 || h <= 0 || ly < 0 || ly >= h) return 0;
    if (rx <= 0 || ry <= 0 || (ly >= ry && ly < h - ry)) {
        *L = 0;
        *R = w - 1;
        return 1;
    }
    int a = 0;
    while (a < w && !rounded_contains(a, ly, w, h, rx, ry)) a++;
    if (a >= w) return 0;
    int b = w - 1;
    while (b > a && !rounded_contains(b, ly, w, h, rx, ry)) b--;
    *L = a;
    *R = b;
    return 1;
}

static void round_rect(jctx *t, jobj self, int x, int y, int w, int h, int aw, int ah, int fill) {
    if (w <= 0 || h <= 0) return;
    tgt o;
    if (!target(t, self, &o)) return;
    int th = 1;   /* do day vien (diem anh dich) */
    if (o.hdp) {
        int k = phys(&o);
        x *= k;
        y *= k;
        w *= k;
        h *= k;
        aw *= k;
        ah *= k;
        th = k;
    }
    int rx = half_mag(aw, w / 2), ry = half_mag(ah, h / 2);
    int ax = x + o.tx, ay = y + o.ty;
    int y0 = ay > o.y0 ? ay : o.y0;
    int y1 = ay + h < o.y1 ? ay + h : o.y1;
    if (ax >= o.x1 || ax + w <= o.x0) return;
    /* v20: tinh theo doan ngang tung hang (hinh chu nhat bo goc la hinh loi -> moi hang la 1 doan lien) thay vi thu
       tung diem cua ca khung - khung vien toan man hinh truoc ton ~3 ms / lan ve, nay ~0,05 ms. Ket qua giong het. */
    int inner = !fill && w > 2 * th && h > 2 * th;
    int rx1 = rx - th > 0 ? rx - th : 0, ry1 = ry - th > 0 ? ry - th : 0;
    for (int yy = y0; yy < y1; yy++) {
        int ly = yy - ay, L, R;
        if (!rr_span(ly, w, h, rx, ry, &L, &R)) continue;
        int iL, iR;
        if (inner && rr_span(ly - th, w - 2 * th, h - 2 * th, rx1, ry1, &iL, &iR)) {
            iL += th;
            iR += th;
            if (iL - 1 >= L) fill_abs(&o, ax + L, yy, (iL - 1 < R ? iL - 1 : R) - L + 1, 1);
            if (iR + 1 <= R) {
                int a = iR + 1 > L ? iR + 1 : L;
                fill_abs(&o, ax + a, yy, R - a + 1, 1);
            }
        } else {
            fill_abs(&o, ax + L, yy, R - L + 1, 1);
        }
    }
}

void N_javax_microedition_lcdui_Graphics_drawRoundRect(jctx *t, jobj self, int32_t x, int32_t y, int32_t w, int32_t h,
                                                       int32_t aw, int32_t ah) {
    round_rect(t, self, x, y, w, h, aw, ah, 0);
}

void N_javax_microedition_lcdui_Graphics_fillRoundRect(jctx *t, jobj self, int32_t x, int32_t y, int32_t w, int32_t h,
                                                       int32_t aw, int32_t ah) {
    round_rect(t, self, x, y, w, h, aw, ah, 1);
}

static int rnd(double v) {
    if (!isfinite(v)) return 0;
    if (v < -2147483648.0) v = -2147483648.0;
    if (v > 2147483647.0) v = 2147483647.0;
    return (int)llround(v);
}

static void arc_draw(tgt *o0, int x, int y, int w, int h, int start, int arcang, int fill);

static void arc(jctx *t, jobj self, int x, int y, int w, int h, int start, int arcang, int fill) {
    if (w <= 0 || h <= 0 || arcang == 0) return;
    tgt o;
    if (!target(t, self, &o)) return;
    if (o.hdp) {
        int k = phys(&o);
        if (fill) {
            arc_draw(&o, x * k, y * k, w * k, h * k, start, arcang, 1);
        } else {
            for (int i = 0; i < k && w * k - 2 * i > 0 && h * k - 2 * i > 0; i++)
                arc_draw(&o, x * k + i, y * k + i, w * k - 2 * i, h * k - 2 * i, start, arcang, 0);
        }
        return;
    }
    arc_draw(&o, x, y, w, h, start, arcang, fill);
}

static void arc_draw(tgt *o0, int x, int y, int w, int h, int start, int arcang, int fill) {
    if (w <= 0 || h <= 0) return;
    tgt o = *o0;
    int ax = x + o.tx, ay = y + o.ty;
    double cx = ax + (w - 1) / 2.0, cy = ay + (h - 1) / 2.0;
    double rx = (w - 1) / 2.0, ry = (h - 1) / 2.0;
    if (fill && (arcang >= 360 || arcang <= -360) && w >= 3 && h >= 3) {
        /* v8h-33: elip day du to theo hang ngang - quat tu tam de lai ke ho o vong lon (chop trang toan man hinh) */
        int64_t ry0 = (int64_t)ay, ry1 = (int64_t)ay + h;
        if (ry0 < o.y0) ry0 = o.y0;
        if (ry1 > o.y1) ry1 = o.y1;
        for (int64_t yy = ry0; yy < ry1; yy++) {
            double ty = ((double)yy - cy) / (ry + 0.5);
            double q = 1.0 - ty * ty;
            if (q < 0) continue;
            double half = (rx + 0.5) * sqrt(q);
            int64_t xa = (int64_t)ceil(cx - half - 1e-9), xb = (int64_t)floor(cx + half + 1e-9);
            if (xa < o.x0) xa = o.x0;
            if (xb > (int64_t)o.x1 - 1) xb = (int64_t)o.x1 - 1;
            if (xb >= xa) fill_abs(&o, (int)xa, (int)yy, (int)(xb - xa + 1), 1);
        }
        return;
    }
    if (fill && w >= 3 && h >= 3) {
        /* v8h-33: hinh quat to theo diem anh (goc theo quy uoc MIDP: 45 do nam tren duong cheo khung) - khong ho */
        double a0 = (double)(start % 360), sweep = (double)(arcang < -360 ? -360 : arcang > 360 ? 360 : arcang);
        if (sweep < 0) {
            a0 += sweep;
            sweep = -sweep;
        }
        while (a0 < 0) a0 += 360.0;
        int64_t ry0 = (int64_t)ay, ry1 = (int64_t)ay + h, rx0 = (int64_t)ax, rx1 = (int64_t)ax + w;
        if (ry0 < o.y0) ry0 = o.y0;
        if (ry1 > o.y1) ry1 = o.y1;
        if (rx0 < o.x0) rx0 = o.x0;
        if (rx1 > o.x1) rx1 = o.x1;
        const double RAD = 180.0 / 3.14159265358979323846;
        for (int64_t yy = ry0; yy < ry1; yy++) {
            double ty = ((double)yy - cy) / (ry + 0.5);
            for (int64_t xx = rx0; xx < rx1; xx++) {
                double tx = ((double)xx - cx) / (rx + 0.5);
                if (tx * tx + ty * ty > 1.0) continue;
                double ang = (tx == 0 && ty == 0) ? a0 : atan2(-ty, tx) * RAD;
                if (ang < 0) ang += 360.0;
                double d = ang - a0;
                if (d < 0) d += 360.0;
                if (d <= sweep) pset(&o, (int)xx, (int)yy);
            }
        }
        return;
    }
    int ns = start % 360;
    int ca = arcang < -360 ? -360 : arcang > 360 ? 360 : arcang;
    double desired = ceil(fabs((double)ca) * (double)(w > h ? w : h) / 90.0);
    double budget = 8.0 * (o.w / o.k + o.h / o.k);
    if (budget < 64) budget = 64;
    if (budget > 1000000) budget = 1000000;
    int steps = (int)(desired < 1 ? 1 : desired > budget ? budget : desired);
    const double PI = 3.14159265358979323846;
    int px = rnd(cx + rx * cos(ns * PI / 180.0)), py = rnd(cy - ry * sin(ns * PI / 180.0));
    for (int s = 0; s <= steps; s++) {
        double pr = (double)s / steps;
        double a = (ns + ca * pr) * PI / 180.0;
        int qx = rnd(cx + rx * cos(a)), qy = rnd(cy - ry * sin(a));
        line_abs(&o, px, py, qx, qy);
        if (fill) line_abs(&o, rnd(cx), rnd(cy), qx, qy);
        px = qx;
        py = qy;
    }
}

void N_javax_microedition_lcdui_Graphics_fillArc(jctx *t, jobj self, int32_t x, int32_t y, int32_t w, int32_t h,
                                                 int32_t s, int32_t a) {
    arc(t, self, x, y, w, h, s, a, 1);
}

void N_javax_microedition_lcdui_Graphics_drawArc(jctx *t, jobj self, int32_t x, int32_t y, int32_t w, int32_t h,
                                                 int32_t s, int32_t a) {
    arc(t, self, x, y, w, h, s, a, 0);
}

static double edge(int ax, int ay, int bx, int by, int px, int py) {
    return ((double)bx - ax) * ((double)py - ay) - ((double)by - ay) * ((double)px - ax);
}

static int top_left(int ax, int ay, int bx, int by) {
    int64_t ddx = (int64_t)bx - ax, ddy = (int64_t)by - ay;
    return ddy < 0 || (ddy == 0 && ddx > 0);
}

void N_javax_microedition_lcdui_Graphics_fillTriangle(jctx *t, jobj self, int32_t x1, int32_t y1, int32_t x2,
                                                      int32_t y2, int32_t x3, int32_t y3) {
    tgt o;
    if (!target(t, self, &o)) return;
    x1 += o.tx; y1 += o.ty; x2 += o.tx; y2 += o.ty; x3 += o.tx; y3 += o.ty;
    double area = edge(x1, y1, x2, y2, x3, y3);
    if (area == 0) {
        line_abs(&o, x1, y1, x2, y2);
        line_abs(&o, x2, y2, x3, y3);
        line_abs(&o, x3, y3, x1, y1);
        return;
    }
    if (area < 0) {
        int tx_ = x2; x2 = x3; x3 = tx_;
        int ty_ = y2; y2 = y3; y3 = ty_;
    }
    int minx = x1 < x2 ? (x1 < x3 ? x1 : x3) : (x2 < x3 ? x2 : x3);
    int maxx = x1 > x2 ? (x1 > x3 ? x1 : x3) : (x2 > x3 ? x2 : x3);
    int miny = y1 < y2 ? (y1 < y3 ? y1 : y3) : (y2 < y3 ? y2 : y3);
    int maxy = y1 > y2 ? (y1 > y3 ? y1 : y3) : (y2 > y3 ? y2 : y3);
    if (minx < o.x0) minx = o.x0;
    if (miny < o.y0) miny = o.y0;
    if (maxx > o.x1 - 1) maxx = o.x1 - 1;
    if (maxy > o.y1 - 1) maxy = o.y1 - 1;
    int t1 = top_left(x1, y1, x2, y2), t2 = top_left(x2, y2, x3, y3), t3 = top_left(x3, y3, x1, y1);
    for (int yy = miny; yy <= maxy; yy++) {
        for (int xx = minx; xx <= maxx; xx++) {
            double e1 = edge(x1, y1, x2, y2, xx, yy), e2 = edge(x2, y2, x3, y3, xx, yy), e3 = edge(x3, y3, x1, y1, xx, yy);
            if ((e1 > 0 || (e1 == 0 && t1)) && (e2 > 0 || (e2 == 0 && t2)) && (e3 > 0 || (e3 == 0 && t3)))
                pset(&o, xx, yy);
        }
    }
}

/* ------------------------------------------------------------ anh */
static void draw_img(tgt *o, const uint32_t *src, int sstride, int s, int xs, int ys, int w, int h, int tr, int px, int py,
                     int kind);

/* ---- ve 1 hang diem anh (nhanh, 4 diem / buoc bang NEON / SSE2). Ket qua giong het cong thuc blend() tung diem.
 * May HD ve o do net x4 nen so diem anh gap 16 lan: phan lon thoi gian ve khung nam o day. Nhom 4 diem trong suot het
 * thi bo qua, dac het thi chep thang - anh nhan vat (trong dac, ngoai trong suot, vien mong) gan nhu chi con chep. */

/* kind 0: dac (dat alpha 255) */
static inline void row_opaque(uint32_t *d, const uint32_t *s, int n) {
    int i = 0;
#if defined(ROW_NEON)
    const uint32x4_t am = vdupq_n_u32(0xFF000000u);
    for (; i + 4 <= n; i += 4) vst1q_u32(d + i, vorrq_u32(vld1q_u32(s + i), am));
#elif defined(ROW_SSE2)
    const __m128i am = _mm_set1_epi32((int)0xFF000000u);
    for (; i + 4 <= n; i += 4)
        _mm_storeu_si128((__m128i *)(d + i), _mm_or_si128(_mm_loadu_si128((const __m128i *)(s + i)), am));
#endif
    for (; i < n; i++) d[i] = s[i] | 0xFF000000u;
}

/* kind 1: alpha 0 = bo, con lai = dac */
static inline void row_key(uint32_t *d, const uint32_t *s, int n) {
    int i = 0;
#if defined(ROW_NEON)
    const uint32x4_t am = vdupq_n_u32(0xFF000000u);
    for (; i + 4 <= n; i += 4) {
        uint32x4_t sv = vld1q_u32(s + i);
        uint32x4_t a = vshrq_n_u32(sv, 24);
        if (vmaxvq_u32(a) == 0) continue;
        uint32x4_t m = vtstq_u32(sv, am);            /* alpha != 0 */
        vst1q_u32(d + i, vbslq_u32(m, vorrq_u32(sv, am), vld1q_u32(d + i)));
    }
#elif defined(ROW_SSE2)
    const __m128i am = _mm_set1_epi32((int)0xFF000000u), zero = _mm_setzero_si128();
    for (; i + 4 <= n; i += 4) {
        __m128i sv = _mm_loadu_si128((const __m128i *)(s + i));
        __m128i tr = _mm_cmpeq_epi32(_mm_and_si128(sv, am), zero);     /* alpha == 0 */
        int mm = _mm_movemask_epi8(tr);
        if (mm == 0xFFFF) continue;
        __m128i v = _mm_or_si128(sv, am);
        if (mm) {
            __m128i dv = _mm_loadu_si128((const __m128i *)(d + i));
            v = _mm_or_si128(_mm_and_si128(tr, dv), _mm_andnot_si128(tr, v));
        }
        _mm_storeu_si128((__m128i *)(d + i), v);
    }
#endif
    for (; i < n; i++) {
        uint32_t v = s[i];
        if (v >> 24) d[i] = v | 0xFF000000u;
    }
}

/* kind 2: ban trong suot */
static inline void row_blend(uint32_t *d, const uint32_t *s, int n) {
    int i = 0;
#if defined(ROW_NEON)
    const uint32x4_t am = vdupq_n_u32(0xFF000000u);
    const uint16x8_t c128 = vdupq_n_u16(128);
    for (; i + 4 <= n; i += 4) {
        uint32x4_t sv = vld1q_u32(s + i);
        uint32x4_t a = vshrq_n_u32(sv, 24);
        if (vmaxvq_u32(a) == 0) continue;
        if (vminvq_u32(a) == 255) {
            vst1q_u32(d + i, sv);
            continue;
        }
        uint8x16_t s8 = vreinterpretq_u8_u32(sv), d8 = vreinterpretq_u8_u32(vld1q_u32(d + i));
        uint8x16_t a8 = vreinterpretq_u8_u32(vmulq_n_u32(a, 0x01010101u));
        uint8x16_t i8 = vmvnq_u8(a8);
        uint16x8_t lo = vaddq_u16(vaddq_u16(vmull_u8(vget_low_u8(s8), vget_low_u8(a8)), vmull_u8(vget_low_u8(d8), vget_low_u8(i8))), c128);
        uint16x8_t hi = vaddq_u16(vaddq_u16(vmull_u8(vget_high_u8(s8), vget_high_u8(a8)), vmull_u8(vget_high_u8(d8), vget_high_u8(i8))), c128);
        lo = vshrq_n_u16(vaddq_u16(lo, vshrq_n_u16(lo, 8)), 8);
        hi = vshrq_n_u16(vaddq_u16(hi, vshrq_n_u16(hi, 8)), 8);
        uint8x16_t r = vcombine_u8(vmovn_u16(lo), vmovn_u16(hi));
        vst1q_u32(d + i, vorrq_u32(vreinterpretq_u32_u8(r), am));
    }
#elif defined(ROW_SSE2)
    const __m128i am = _mm_set1_epi32((int)0xFF000000u), zero = _mm_setzero_si128();
    const __m128i c255 = _mm_set1_epi16(255), c128 = _mm_set1_epi16(128);
    for (; i + 4 <= n; i += 4) {
        __m128i sv = _mm_loadu_si128((const __m128i *)(s + i));
        __m128i a = _mm_and_si128(sv, am);
        if (_mm_movemask_epi8(_mm_cmpeq_epi32(a, zero)) == 0xFFFF) continue;
        if (_mm_movemask_epi8(_mm_cmpeq_epi32(a, am)) == 0xFFFF) {
            _mm_storeu_si128((__m128i *)(d + i), sv);
            continue;
        }
        __m128i dv = _mm_loadu_si128((const __m128i *)(d + i));
        __m128i slo = _mm_unpacklo_epi8(sv, zero), shi = _mm_unpackhi_epi8(sv, zero);
        __m128i dlo = _mm_unpacklo_epi8(dv, zero), dhi = _mm_unpackhi_epi8(dv, zero);
        __m128i alo = _mm_shufflehi_epi16(_mm_shufflelo_epi16(slo, 0xFF), 0xFF);
        __m128i ahi = _mm_shufflehi_epi16(_mm_shufflelo_epi16(shi, 0xFF), 0xFF);
        __m128i tlo = _mm_add_epi16(_mm_add_epi16(_mm_mullo_epi16(slo, alo), _mm_mullo_epi16(dlo, _mm_sub_epi16(c255, alo))), c128);
        __m128i thi = _mm_add_epi16(_mm_add_epi16(_mm_mullo_epi16(shi, ahi), _mm_mullo_epi16(dhi, _mm_sub_epi16(c255, ahi))), c128);
        tlo = _mm_srli_epi16(_mm_add_epi16(tlo, _mm_srli_epi16(tlo, 8)), 8);
        thi = _mm_srli_epi16(_mm_add_epi16(thi, _mm_srli_epi16(thi, 8)), 8);
        _mm_storeu_si128((__m128i *)(d + i), _mm_or_si128(_mm_packus_epi16(tlo, thi), am));
    }
#endif
    for (; i < n; i++) d[i] = blend(s[i], d[i]);
}

static inline void row_put(uint32_t *d, const uint32_t *s, int n, int kind) {
    gfx_dem[kind & 3] += n;
    if (kind == 0) row_opaque(d, s, n);
    else if (kind == 1) row_key(d, s, n);
    else row_blend(d, s, n);
}

static void blit(tgt *o, const uint32_t *src, int sstride, int sx, int sy, int dx, int dy, int w, int h, int kind) {
    /* cat theo vung cat */
    if (dx < o->x0) { sx += o->x0 - dx; w -= o->x0 - dx; dx = o->x0; }
    if (dy < o->y0) { sy += o->y0 - dy; h -= o->y0 - dy; dy = o->y0; }
    if (dx + w > o->x1) w = o->x1 - dx;
    if (dy + h > o->y1) h = o->y1 - dy;
    if (w <= 0 || h <= 0) return;
    for (int r = 0; r < h; r++) {
        const uint32_t *s = src + (size_t)(sy + r) * sstride + sx;
        uint32_t *d = o->px + (size_t)(dy + r) * o->w + dx;
        row_put(d, s, w, kind);
    }
}

/* May HD: moc ve the gioi. pha 1 = bat dau ve the gioi (anh duoc ve tu day co khoi noi + bong do);
 * pha 2 = ve xong the gioi: tat che do + hau ky canh (loa sang, tia nang, toi goc, bui sang) len dich hien tai */
void N_javax_microedition_lcdui_Graphics_hdCanh(jctx *t, jobj self, int32_t pha) {
    if (gfx_hdk <= 1) return;
    if (pha == 1) {
        gfx_world = gfx_hd_fx >= 1;
        gfx_hd_mode = gfx_hd_fx >= 1 ? 3 : 0;
        return;
    }
    int dang = gfx_world;
    gfx_world = 0;
    gfx_hd_mode = 0;
    if (!dang || gfx_hd_fx < 2) return;
    tgt o;
    if (!target(t, self, &o)) return;
    if (o.k != gfx_hdk) return;
    gfx_hd_scene(o.px, o.w, o.h, o.x0 * o.k, o.y0 * o.k, o.x1 * o.k, o.y1 * o.k, o.k);
}

void N_javax_microedition_lcdui_Graphics_drawImage0(jctx *t, jobj self, jobj img, int32_t x, int32_t y, int32_t anchor) {
    struct O_javax_microedition_lcdui_Image *im = IMG(img);
    tgt o;
    int ok = target(t, self, &o);
    if (t->exc) return;
    int sc = im->f_hd_I > 1 ? im->f_hd_I : 1;
    int lw = im->f_w_I / sc, lh = im->f_h_I / sc;
    int px, py;
    if (!anchor_pos(t, anchor, 0, x + o.tx, y + o.ty, lw, lh, 0, &px, &py)) return;
    if (!ok || !im->f_argb__5b) return;
    const uint32_t *src = AD(im->f_argb__5b, uint32_t);
    if (src == o.px) {
        /* ve anh len chinh no: chep tam */
        size_t n = (size_t)im->f_w_I * im->f_h_I;
        uint32_t *tmp = (uint32_t *)malloc(n * 4);
        memcpy(tmp, src, n * 4);
        draw_img(&o, tmp, im->f_w_I, sc, 0, 0, lw, lh, 0, px, py, 2);
        free(tmp);
        return;
    }
    int stride, kind;
    hd_ent *pin;
    src = img_px(t, im, o.k, &stride, &sc, &kind, &pin);
    if (gfx_world && pin && pin->shd && sc == o.k) sh_draw(&o, pin, 0, 0, lw, lh, 0, px, py);
    draw_img(&o, src, stride, sc, 0, 0, lw, lh, 0, px, py, kind);
    if (pin) hd_put(pin);
}

/* bien doi: (lx, ly) dich -> (sx, sy) nguon = base + col*lx + row*ly (giong transform_steps cua bo chay cu) */
static void tsteps(int w, int h, int tr, int *bx, int *by, int *cx, int *cy, int *rx, int *ry) {
    *bx = 0; *by = 0; *cx = 1; *cy = 0; *rx = 0; *ry = 1;
    switch (tr) {
    case 1: *by = h - 1; *ry = -1; break;                                   /* MIRROR_ROT180 */
    case 2: *bx = w - 1; *cx = -1; break;                                   /* MIRROR */
    case 3: *bx = w - 1; *by = h - 1; *cx = -1; *ry = -1; break;            /* ROT180 */
    case 4: *cx = 0; *cy = 1; *rx = 1; *ry = 0; break;                      /* MIRROR_ROT270 */
    case 5: *by = h - 1; *cx = 0; *cy = -1; *rx = 1; *ry = 0; break;        /* ROT90 */
    case 6: *bx = w - 1; *cx = 0; *cy = 1; *rx = -1; *ry = 0; break;        /* ROT270 */
    case 7: *bx = w - 1; *by = h - 1; *cx = 0; *cy = -1; *rx = -1; *ry = 0; break; /* MIRROR_ROT90 */
    default: break;
    }
}

static void region_draw(tgt *o, const uint32_t *src, int sstride, int xs, int ys, int w, int h, int tr, int px, int py,
                        int kind) {
    if (tr == 0) {
        blit(o, src, sstride, xs, ys, px, py, w, h, kind);
        return;
    }
    int ow = (tr >= 4) ? h : w, oh = (tr >= 4) ? w : h;
    int vx0 = px > o->x0 ? px : o->x0, vy0 = py > o->y0 ? py : o->y0;
    int vx1 = px + ow < o->x1 ? px + ow : o->x1, vy1 = py + oh < o->y1 ? py + oh : o->y1;
    if (vx1 <= vx0 || vy1 <= vy0) return;
    int bx, by, cx, cy, rx, ry;
    tsteps(w, h, tr, &bx, &by, &cx, &cy, &rx, &ry);
    const uint32_t *base = src + (size_t)ys * sstride + xs;
    int64_t colstep = (int64_t)cy * sstride + cx, rowstep = (int64_t)ry * sstride + rx;
    int64_t lx0 = vx0 - px, ly0 = vy0 - py;
    int64_t first = ((int64_t)by + (int64_t)cy * lx0 + (int64_t)ry * ly0) * sstride + bx + (int64_t)cx * lx0 + (int64_t)rx * ly0;
    if (tr == 2 || tr == 3) {
        /* lat ngang (nhan vat quay mat) / quay 180: dao hang nguon ra bo dem tam roi ve hang nhanh */
        uint32_t tam[1024];
        int n = vx1 - vx0;
        for (int yy = vy0; yy < vy1; yy++) {
            const uint32_t *sp = base + first + rowstep * (yy - vy0);    /* diem nguon cua cot dau, di lui */
            uint32_t *d = o->px + (size_t)yy * o->w + vx0;
            for (int c = 0; c < n; c += 1024) {
                int m = n - c < 1024 ? n - c : 1024;
                const uint32_t *q = sp - c;
                for (int i = 0; i < m; i++) tam[i] = q[-i];
                row_put(d + c, tam, m, kind);
            }
        }
        return;
    }
    if (tr == 1) {
        /* lat doc: hang nguon di nguoc, cot xuoi */
        for (int yy = vy0; yy < vy1; yy++)
            row_put(o->px + (size_t)yy * o->w + vx0, base + first + rowstep * (yy - vy0), vx1 - vx0, kind);
        return;
    }
    for (int yy = vy0; yy < vy1; yy++) {
        int64_t si = first + rowstep * (yy - vy0);
        uint32_t *d = o->px + (size_t)yy * o->w + vx0;
        for (int xx = vx0; xx < vx1; xx++, si += colstep, d++) {
            uint32_t v = base[si];
            if (kind == 0) *d = v | 0xFF000000u;
            else if (kind == 1) {
                if (v >> 24) *d = v | 0xFF000000u;
            } else *d = blend(v, *d);
        }
    }
}

/* Avatar: ve vung anh len dich voi he so rieng. xs, ys, w, h: toa do LOGIC trong anh; s = diem anh that / diem logic
 * cua anh (anh dt3/: 3); px, py: toa do logic tren dich (he so o->k). k = s = 1 -> dung y nhu cu. */
static void draw_img(tgt *o, const uint32_t *src, int sstride, int s, int xs, int ys, int w, int h, int tr, int px, int py,
                     int kind) {
    if (s < 1) s = 1;
    if (s > gfx_hd_seen) gfx_hd_seen = s;   /* he so lon nhat trong khung: j2n.Ui chon do net man */
    int k = o->k;
    if (s == k) {
        tgt p = *o;
        p.k = 1;
        p.x0 = o->x0 * k;
        p.y0 = o->y0 * k;
        p.x1 = o->x1 * k;
        p.y1 = o->y1 * k;
        region_draw(&p, src, sstride, xs * s, ys * s, w * s, h * s, tr, px * k, py * k, kind);
        return;
    }
    int ow = (tr >= 4) ? h : w, oh = (tr >= 4) ? w : h;
    int bx, by, cx, cy, rx, ry;
    if (s == 1) {
        /* anh thuong len man net cao: moi diem anh thanh o k x k (giong het phong to theo diem anh) */
        int vx0 = px > o->x0 ? px : o->x0, vy0 = py > o->y0 ? py : o->y0;
        int vx1 = px + ow < o->x1 ? px + ow : o->x1, vy1 = py + oh < o->y1 ? py + oh : o->y1;
        if (vx1 <= vx0 || vy1 <= vy0) return;
        tsteps(w, h, tr, &bx, &by, &cx, &cy, &rx, &ry);
        const uint32_t *base = src + (size_t)ys * sstride + xs;
        for (int yy = vy0; yy < vy1; yy++) {
            int ly = yy - py;
            for (int xx = vx0; xx < vx1; xx++) {
                int lx = xx - px;
                int sx = bx + cx * lx + rx * ly, sy = by + cy * lx + ry * ly;
                pput(o, xx, yy, base[(size_t)sy * sstride + sx], kind);
            }
        }
        return;
    }
    /* chung (anh net cao len dich he so khac): lay mau gan nhat theo diem anh that cua dich */
    int64_t X0 = (int64_t)px * k, Y0 = (int64_t)py * k;
    int64_t vx0 = X0 > (int64_t)o->x0 * k ? X0 : (int64_t)o->x0 * k;
    int64_t vy0 = Y0 > (int64_t)o->y0 * k ? Y0 : (int64_t)o->y0 * k;
    int64_t vx1 = X0 + (int64_t)ow * k < (int64_t)o->x1 * k ? X0 + (int64_t)ow * k : (int64_t)o->x1 * k;
    int64_t vy1 = Y0 + (int64_t)oh * k < (int64_t)o->y1 * k ? Y0 + (int64_t)oh * k : (int64_t)o->y1 * k;
    if (vx1 <= vx0 || vy1 <= vy0) return;
    tsteps(w * s, h * s, tr, &bx, &by, &cx, &cy, &rx, &ry);
    const uint32_t *base = src + (size_t)ys * s * sstride + (size_t)xs * s;
    for (int64_t Y = vy0; Y < vy1; Y++) {
        int ly = (int)((Y - Y0) * s / k);
        uint32_t *d = o->px + (size_t)Y * o->w;
        for (int64_t X = vx0; X < vx1; X++) {
            int lx = (int)((X - X0) * s / k);
            int sx = bx + cx * lx + rx * ly, sy = by + cy * lx + ry * ly;
            uint32_t v = base[(size_t)sy * sstride + sx];
            if (kind == 0) d[X] = v | 0xFF000000u;
            else if (kind == 1) {
                if (v >> 24) d[X] = v | 0xFF000000u;
            } else d[X] = blend(v, d[X]);
        }
    }
}

void N_javax_microedition_lcdui_Graphics_drawRegion0(jctx *t, jobj self, jobj img, int32_t xs, int32_t ys, int32_t w,
                                                     int32_t h, int32_t tr, int32_t xd, int32_t yd, int32_t anchor) {
    struct O_javax_microedition_lcdui_Image *im = IMG(img);
    tgt o;
    int ok = target(t, self, &o);
    if (t->exc) return;
    int px, py;
    /* bo chay cu: neo tinh theo kich thuoc vung CHUA bien doi */
    if (!anchor_pos(t, anchor, 0, xd + o.tx, yd + o.ty, w, h, 0, &px, &py)) return;
    if (!ok || !im->f_argb__5b) return;
    const uint32_t *src = AD(im->f_argb__5b, uint32_t);
    int sc = im->f_hd_I > 1 ? im->f_hd_I : 1;
    if (src == o.px) {
        size_t n = (size_t)im->f_w_I * im->f_h_I;
        uint32_t *tmp = (uint32_t *)malloc(n * 4);
        memcpy(tmp, src, n * 4);
        draw_img(&o, tmp, im->f_w_I, sc, xs, ys, w, h, tr, px, py, 2);
        free(tmp);
        return;
    }
    int stride, kind;
    hd_ent *pin;
    src = img_px(t, im, o.k, &stride, &sc, &kind, &pin);
    if (gfx_world && pin && pin->shd && sc == o.k) sh_draw(&o, pin, xs, ys, w, h, tr, px, py);
    draw_img(&o, src, stride, sc, xs, ys, w, h, tr, px, py, kind);
    if (pin) hd_put(pin);
}

void N_javax_microedition_lcdui_Graphics_drawRGB(jctx *t, jobj self, jobj rgb, int32_t off, int32_t scan, int32_t x,
                                                 int32_t y, int32_t w, int32_t h, int32_t alpha) {
    if (!rgb) {
        rt_npe(t);
        return;
    }
    if (w <= 0 || h <= 0) return;
    int64_t len = ALEN(rgb);
    int64_t lo = off + (scan < 0 ? (int64_t)scan * (h - 1) : 0);
    int64_t hi = off + (scan < 0 ? w : (int64_t)scan * (h - 1) + w);
    if (off < 0 || lo < 0 || hi > len) {
        rt_err(t, "java/lang/ArrayIndexOutOfBoundsException", "drawRGB");
        return;
    }
    tgt o;
    if (!target(t, self, &o)) return;
    const uint32_t *src = AD(rgb, uint32_t);
    int dx = x + o.tx, dy = y + o.ty;
    for (int r = 0; r < h; r++) {
        int yy = dy + r;
        if (yy < o.y0 || yy >= o.y1) continue;
        const uint32_t *s = src + off + (int64_t)scan * r;
        if (o.k == 1) {
            uint32_t *d = o.px + (size_t)yy * o.w;
            for (int i = 0; i < w; i++) {
                int xx = dx + i;
                if (xx < o.x0 || xx >= o.x1) continue;
                d[xx] = alpha ? blend(s[i], d[xx]) : (s[i] | 0xFF000000u);
            }
            continue;
        }
        for (int i = 0; i < w; i++) {
            int xx = dx + i;
            if (xx < o.x0 || xx >= o.x1) continue;
            pput(&o, xx, yy, s[i], alpha ? 2 : 0);
        }
    }
}

void N_javax_microedition_lcdui_Graphics_copyArea(jctx *t, jobj self, int32_t xs, int32_t ys, int32_t w, int32_t h,
                                                  int32_t xd, int32_t yd, int32_t anchor) {
    tgt o;
    int ok = target(t, self, &o);
    if (t->exc) return;
    if (G(self)->f_img_L == 0 && 0) return;
    int ax = xs + o.tx, ay = ys + o.ty;
    if (ax < 0 || ay < 0 || w < 0 || h < 0 || ax + w > o.w / o.k || ay + h > o.h / o.k) {
        rt_err(t, "java/lang/IllegalArgumentException", "copyArea");
        return;
    }
    int px, py;
    if (!anchor_pos(t, anchor, 0, xd + o.tx, yd + o.ty, w, h, 0, &px, &py)) return;
    if (!ok || w == 0 || h == 0) return;
    int k = o.k;
    tgt p = o;
    p.k = 1;
    p.x0 = o.x0 * k;
    p.y0 = o.y0 * k;
    p.x1 = o.x1 * k;
    p.y1 = o.y1 * k;
    int W = w * k, H = h * k;
    size_t n = (size_t)W * H;
    uint32_t *tmp = (uint32_t *)malloc(n * 4);
    for (int r = 0; r < H; r++) memcpy(tmp + (size_t)r * W, o.px + (size_t)(ay * k + r) * o.w + (size_t)ax * k, (size_t)W * 4);
    blit(&p, tmp, W, 0, 0, px * k, py * k, W, H, 0);
    free(tmp);
}

/* ------------------------------------------------------------ chu */
static int glyph_index(uint32_t cp) {
    int lo = 0, hi = FONT_N - 1;
    while (lo <= hi) {
        int m = (lo + hi) >> 1;
        if (FONT_CP[m] == cp) return m;
        if (FONT_CP[m] < cp) lo = m + 1;
        else hi = m - 1;
    }
    return -1;
}

static int char_adv(uint32_t cp) {
    int i = glyph_index(cp);
    if (i < 0) i = glyph_index(0xFFFD);
    return i < 0 ? 7 : FONT_ADV[i];
}

static int text_width(const uint16_t *s, int n) {
    int w = 0;
    for (int i = 0; i < n; i++) w += char_adv(s[i]);
    return w;
}

void N_javax_microedition_lcdui_Graphics_drawText(jctx *t, jobj self, jobj str, int32_t x, int32_t y, int32_t anchor) {
    int n = rt_str_len(str);
    const uint16_t *s = rt_str_chars(str);
    tgt o;
    int ok = target(t, self, &o);
    if (t->exc) return;
    int w = text_width(s, n);
    int px, py;
    if (!anchor_pos(t, anchor, 1, x + o.tx, y + o.ty, w, FONT_H, FONT_BASE, &px, &py)) return;
    if (!ok) return;
    uint32_t col = o.color & 0xFFFFFFu;
    int cx = px;
    for (int i = 0; i < n; i++) {
        int gi = glyph_index(s[i]);
        if (gi < 0) gi = glyph_index(0xFFFD);
        if (gi < 0) {
            cx += 7;
            continue;
        }
        int gw = FONT_W[gi];
        const uint8_t *bits = FONT_BITS + FONT_OFF[gi];
        for (int r = 0; r < FONT_H; r++) {
            int yy = py + r;
            if (yy < o.y0 || yy >= o.y1) continue;
            for (int c = 0; c < gw; c++) {
                int xx = cx + c;
                if (xx < o.x0 || xx >= o.x1) continue;
                uint8_t a = bits[r * gw + c];
                if (!a) continue;
                pput(&o, xx, yy, ((uint32_t)a << 24) | col, 2);
            }
        }
        cx += FONT_ADV[gi];
    }
}

int32_t N_javax_microedition_lcdui_Font_getHeight(jctx *t, jobj self) { (void)t; (void)self; return FONT_H; }
int32_t N_javax_microedition_lcdui_Font_getBaselinePosition(jctx *t, jobj self) { (void)t; (void)self; return FONT_BASE; }
int32_t N_javax_microedition_lcdui_Font_charWidth(jctx *t, jobj self, int32_t c) { (void)t; (void)self; return char_adv((uint32_t)c & 0xFFFF); }

int32_t N_javax_microedition_lcdui_Font_stringWidth(jctx *t, jobj self, jobj s) {
    (void)self;
    if (!s) {
        rt_npe(t);
        return 0;
    }
    return text_width(rt_str_chars(s), rt_str_len(s));
}

/* ------------------------------------------------------------ giai ma anh */
static jobj make_image(jctx *t, int w, int h, jobj arr, int kind) {
    return JM_javax_microedition_lcdui_Image_make(t, w, h, arr, kind);
}

jobj N_javax_microedition_lcdui_Image_decode0(jctx *t, jobj data, int32_t off, int32_t len) {
    if (!data) {
        rt_npe(t);
        return 0;
    }
    int w, h, comp;
    unsigned char *px = stbi_load_from_memory(AD(data, unsigned char) + off, len, &w, &h, &comp, 4);
    if (!px) return 0;
    jobj arr = rt_newarr(t, &rt_arr_I, w * h);
    if (!arr) {
        stbi_image_free(px);
        return 0;
    }
    uint32_t *d = AD(arr, uint32_t);
    int kind = 0;
    for (int i = 0; i < w * h; i++) {
        uint32_t r = px[i * 4], g = px[i * 4 + 1], b = px[i * 4 + 2], a = px[i * 4 + 3];
        d[i] = (a << 24) | (r << 16) | (g << 8) | b;
        if (a != 255) {
            if (a != 0) kind = 2;
            else if (kind == 0) kind = 1;
        }
    }
    stbi_image_free(px);
    return make_image(t, w, h, arr, kind);
}

jobj N_javax_microedition_lcdui_Image_createImage_6f34(jctx *t, jobj src, int32_t x, int32_t y, int32_t w, int32_t h,
                                                       int32_t tr) {
    if (!src) {
        rt_npe(t);
        return 0;
    }
    struct O_javax_microedition_lcdui_Image *im = IMG(src);
    int sc = im->f_hd_I > 1 ? im->f_hd_I : 1;
    if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > im->f_w_I / sc || y + h > im->f_h_I / sc || tr < 0 || tr > 7) {
        rt_err(t, "java/lang/IllegalArgumentException", "createImage");
        return 0;
    }
    /* anh net cao: lam viec tren diem anh that roi danh dau anh moi cung he so */
    x *= sc;
    y *= sc;
    w *= sc;
    h *= sc;
    int ow = tr >= 4 ? h : w, oh = tr >= 4 ? w : h;
    jobj arr = rt_newarr(t, &rt_arr_I, ow * oh);
    if (!arr) return 0;
    tgt o;
    o.px = AD(arr, uint32_t);
    o.k = 1;
    o.w = ow;
    o.h = oh;
    o.x0 = 0;
    o.y0 = 0;
    o.x1 = ow;
    o.y1 = oh;
    o.tx = o.ty = 0;
    o.color = 0;
    /* chep nguyen gia tri (ke ca alpha): ve "dac" roi chep alpha */
    const uint32_t *s = AD(im->f_argb__5b, uint32_t);
    int bx, by, cx, cy, rx, ry;
    tsteps(w, h, tr, &bx, &by, &cx, &cy, &rx, &ry);
    for (int ly = 0; ly < oh; ly++) {
        for (int lx = 0; lx < ow; lx++) {
            int sx = bx + cx * lx + rx * ly, sy = by + cy * lx + ry * ly;
            o.px[ly * ow + lx] = s[(size_t)(y + sy) * im->f_w_I + x + sx];
        }
    }
    jobj r = make_image(t, ow, oh, arr, im->f_akind_I);
    if (r && sc > 1) IMG(r)->f_hd_I = sc;
    if (r) IMG(r)->f_smooth_I = im->f_smooth_I;
    return r;
}
