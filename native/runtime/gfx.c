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
    int w, h;
    int x0, y0, x1, y1;  /* vung cat [x0,x1) x [y0,y1) trong toa do dich */
    int tx, ty;
    uint32_t color;      /* 0xFFRRGGBB */
} tgt;

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
    int x0 = gr->f_cx_I, y0 = gr->f_cy_I;
    int x1 = x0 + gr->f_cw_I, y1 = y0 + gr->f_ch_I;
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    if (x1 > o->w) x1 = o->w;
    if (y1 > o->h) y1 = o->h;
    o->x0 = x0;
    o->y0 = y0;
    o->x1 = x1;
    o->y1 = y1;
    o->tx = gr->f_tx_I;
    o->ty = gr->f_ty_I;
    o->color = 0xFF000000u | ((uint32_t)gr->f_color_I & 0xFFFFFFu);
    return x1 > x0 && y1 > y0;
}

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

static inline void plot(tgt *o, int x, int y) {
    if (x >= o->x0 && x < o->x1 && y >= o->y0 && y < o->y1) o->px[y * o->w + x] = o->color;
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
        if (cx >= cl && cx <= cr && cy >= ct && cy <= cb) o->px[cy * o->w + cx] = o->color;
    }
}

void N_javax_microedition_lcdui_Graphics_drawLine(jctx *t, jobj self, int32_t x1, int32_t y1, int32_t x2, int32_t y2) {
    tgt o;
    if (!target(t, self, &o)) return;
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
    for (int yy = y0; yy < y1; yy++) {
        uint32_t *p = o->px + yy * o->w + x0;
        for (int n = x1 - x0; n > 0; n--) *p++ = c;
    }
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

static void round_rect(jctx *t, jobj self, int x, int y, int w, int h, int aw, int ah, int fill) {
    if (w <= 0 || h <= 0) return;
    tgt o;
    if (!target(t, self, &o)) return;
    int rx = half_mag(aw, w / 2), ry = half_mag(ah, h / 2);
    int ax = x + o.tx, ay = y + o.ty;
    int x0 = ax > o.x0 ? ax : o.x0, y0 = ay > o.y0 ? ay : o.y0;
    int x1 = ax + w < o.x1 ? ax + w : o.x1, y1 = ay + h < o.y1 ? ay + h : o.y1;
    for (int yy = y0; yy < y1; yy++) {
        int ly = yy - ay;
        for (int xx = x0; xx < x1; xx++) {
            int lx = xx - ax;
            if (!rounded_contains(lx, ly, w, h, rx, ry)) continue;
            if (!fill) {
                int rx1 = rx - 1 > 0 ? rx - 1 : 0, ry1 = ry - 1 > 0 ? ry - 1 : 0;
                if (w > 2 && h > 2 && rounded_contains(lx - 1, ly - 1, w - 2, h - 2, rx1, ry1)) continue;
            }
            o.px[yy * o.w + xx] = o.color;
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

static void arc(jctx *t, jobj self, int x, int y, int w, int h, int start, int arcang, int fill) {
    if (w <= 0 || h <= 0 || arcang == 0) return;
    tgt o;
    if (!target(t, self, &o)) return;
    int ax = x + o.tx, ay = y + o.ty;
    double cx = ax + (w - 1) / 2.0, cy = ay + (h - 1) / 2.0;
    double rx = (w - 1) / 2.0, ry = (h - 1) / 2.0;
    int ns = start % 360;
    int ca = arcang < -360 ? -360 : arcang > 360 ? 360 : arcang;
    double desired = ceil(fabs((double)ca) * (double)(w > h ? w : h) / 90.0);
    double budget = 8.0 * (o.w + o.h);
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
                o.px[yy * o.w + xx] = o.color;
        }
    }
}

/* ------------------------------------------------------------ anh */
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
        if (kind == 0) {
            for (int i = 0; i < w; i++) d[i] = s[i] | 0xFF000000u;
        } else if (kind == 1) {
            for (int i = 0; i < w; i++) {
                uint32_t v = s[i];
                if (v >> 24) d[i] = v | 0xFF000000u;
            }
        } else {
            for (int i = 0; i < w; i++) d[i] = blend(s[i], d[i]);
        }
    }
}

void N_javax_microedition_lcdui_Graphics_drawImage0(jctx *t, jobj self, jobj img, int32_t x, int32_t y, int32_t anchor) {
    struct O_javax_microedition_lcdui_Image *im = IMG(img);
    tgt o;
    int ok = target(t, self, &o);
    if (t->exc) return;
    int px, py;
    if (!anchor_pos(t, anchor, 0, x + o.tx, y + o.ty, im->f_w_I, im->f_h_I, 0, &px, &py)) return;
    if (!ok || !im->f_argb__5b) return;
    const uint32_t *src = AD(im->f_argb__5b, uint32_t);
    if (src == o.px) {
        /* ve anh len chinh no: chep tam */
        size_t n = (size_t)im->f_w_I * im->f_h_I;
        uint32_t *tmp = (uint32_t *)malloc(n * 4);
        memcpy(tmp, src, n * 4);
        blit(&o, tmp, im->f_w_I, 0, 0, px, py, im->f_w_I, im->f_h_I, 2);
        free(tmp);
        return;
    }
    blit(&o, src, im->f_w_I, 0, 0, px, py, im->f_w_I, im->f_h_I, im->f_akind_I);
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
    if (src == o.px) {
        size_t n = (size_t)im->f_w_I * im->f_h_I;
        uint32_t *tmp = (uint32_t *)malloc(n * 4);
        memcpy(tmp, src, n * 4);
        region_draw(&o, tmp, im->f_w_I, xs, ys, w, h, tr, px, py, 2);
        free(tmp);
        return;
    }
    region_draw(&o, src, im->f_w_I, xs, ys, w, h, tr, px, py, im->f_akind_I);
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
        uint32_t *d = o.px + (size_t)yy * o.w;
        for (int i = 0; i < w; i++) {
            int xx = dx + i;
            if (xx < o.x0 || xx >= o.x1) continue;
            d[xx] = alpha ? blend(s[i], d[xx]) : (s[i] | 0xFF000000u);
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
    if (ax < 0 || ay < 0 || w < 0 || h < 0 || ax + w > o.w || ay + h > o.h) {
        rt_err(t, "java/lang/IllegalArgumentException", "copyArea");
        return;
    }
    int px, py;
    if (!anchor_pos(t, anchor, 0, xd + o.tx, yd + o.ty, w, h, 0, &px, &py)) return;
    if (!ok || w == 0 || h == 0) return;
    size_t n = (size_t)w * h;
    uint32_t *tmp = (uint32_t *)malloc(n * 4);
    for (int r = 0; r < h; r++) memcpy(tmp + (size_t)r * w, o.px + (size_t)(ay + r) * o.w + ax, (size_t)w * 4);
    blit(&o, tmp, w, 0, 0, px, py, w, h, 0);
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
                uint32_t *d = o.px + (size_t)yy * o.w + xx;
                *d = blend(((uint32_t)a << 24) | col, *d);
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
    if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > im->f_w_I || y + h > im->f_h_I || tr < 0 || tr > 7) {
        rt_err(t, "java/lang/IllegalArgumentException", "createImage");
        return 0;
    }
    int ow = tr >= 4 ? h : w, oh = tr >= 4 ? w : h;
    jobj arr = rt_newarr(t, &rt_arr_I, ow * oh);
    if (!arr) return 0;
    tgt o;
    o.px = AD(arr, uint32_t);
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
    return make_image(t, ow, oh, arr, im->f_akind_I);
}
