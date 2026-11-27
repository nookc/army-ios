/*
 * hdscale.c - phong to anh cho ban Avatar 250 HD (may HD cua runtime).
 *
 *  - hd_xbrz():    phong to "theo luat" 2x / 3x / 4x cho pixel art (co kenh alpha). Day la ban viet lai bang C cua thuat toan
 *                  xBRZ (Zenju) - xem giay phep ben duoi. Bo tham so mac dinh cua xBRZ; khoang cach mau tinh truc tiep
 *                  (ban goc dung bang tra 64 MB - khong hop voi dien thoai).
 *  - hd_bicubic(): phong to min (Catmull-Rom, alpha nhan truoc) cho anh ve 3D / anh co chuyen mau min.
 *  - hd_area():    doi co bang trung binh dien tich (thu nho x4 -> x3, x2 -> x1 ...).
 *  - hd_make():    tu anh nguon (he so s) tao anh he so K theo dung quy trinh cua may HD.
 *
 * Dinh dang diem anh: 0xAARRGGBB (alpha KHONG nhan truoc), giong bo dem cua gfx.c.
 *
 * ****************************************************************************
 * * xBRZ: "Scale by rules" - high quality image upscaling filter by Zenju     *
 * * This file contains a C port of xBRZ, which is part of the HqMAME project. *
 * * It is distributed under GNU General Public License:                       *
 * * http://www.gnu.org/licenses/gpl.html                                      *
 * * Copyright (C) Zenju (zenju AT gmx DOT de) - All Rights Reserved           *
 * *                                                                           *
 * * Additionally and as a special exception, the author gives permission      *
 * * to link the code of this program with the MAME library (or with modified  *
 * * versions of MAME that use the same license as MAME), and distribute       *
 * * linked combinations including the two. You must obey the GNU General      *
 * * Public License in all respects for all of the code used other than MAME.  *
 * * If you modify this file, you may extend this exception to your version    *
 * * of the file, but you are not obligated to do so. If you do not wish to    *
 * * do so, delete this exception statement from your version.                 *
 * ****************************************************************************
 */
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "hdscale.h"

#define PA(p) ((int)((p) >> 24))
#define PR(p) ((int)(((p) >> 16) & 0xFF))
#define PG(p) ((int)(((p) >> 8) & 0xFF))
#define PB(p) ((int)((p) & 0xFF))

/* ------------------------------------------------------------------ xBRZ */
#define XB_LUMA 1.0f
#define XB_EQ_TOL 30.0f
#define XB_DOMINANT 3.6f
#define XB_STEEP 2.2f

enum { BL_NONE = 0, BL_NORMAL = 1, BL_DOMINANT = 2 };

static inline float dist_ycbcr(uint32_t p1, uint32_t p2) {
    int rd = PR(p1) - PR(p2), gd = PG(p1) - PG(p2), bd = PB(p1) - PB(p2);
    if ((rd | gd | bd) == 0) return 0.0f;
    /* ITU-R BT.2020 */
    const float k_b = 0.0593f, k_r = 0.2627f, k_g = 1.0f - 0.0593f - 0.2627f;
    const float scale_b = 0.5f / (1.0f - k_b), scale_r = 0.5f / (1.0f - k_r);
    float y = k_r * rd + k_g * gd + k_b * bd;
    float cb = scale_b * (bd - y), cr = scale_r * (rd - y);
    y *= XB_LUMA;
    return sqrtf(y * y + cb * cb + cr * cr);
}

/* khoang cach mau co alpha (ColorDistanceARGB) */
static inline float cdist(uint32_t p1, uint32_t p2) {
    if (p1 == p2) return 0.0f;
    float a1 = PA(p1) * (1.0f / 255.0f), a2 = PA(p2) * (1.0f / 255.0f);
    float d = dist_ycbcr(p1, p2);
    return a1 < a2 ? a1 * d + 255.0f * (a2 - a1) : a2 * d + 255.0f * (a1 - a2);
}

/* mau trung gian giua 2 mau co alpha, ti le M / N cho mau "front" (gradientARGB) */
static inline uint32_t cgrad(uint32_t front, uint32_t back, unsigned M, unsigned N) {
    unsigned wf = (unsigned)PA(front) * M, wb = (unsigned)PA(back) * (N - M), ws = wf + wb;
    if (ws == 0) return 0;
    unsigned r = ((unsigned)PR(front) * wf + (unsigned)PR(back) * wb) / ws;
    unsigned g = ((unsigned)PG(front) * wf + (unsigned)PG(back) * wb) / ws;
    unsigned b = ((unsigned)PB(front) * wf + (unsigned)PB(back) * wb) / ws;
    return ((ws / N) << 24) | (r << 16) | (g << 8) | b;
}

/* mot buoc tron: o (hang i, cot j) cua khoi scale x scale, ti le m / n (n = 0: ghi de mau) */
typedef struct xb_op {
    uint8_t i, j, m, n;
} xb_op;

#define XB_END {255, 255, 0, 0}

/* [kieu] : 0 thoai (shallow), 1 doc (steep), 2 doc + thoai, 3 cheo, 4 goc */
static const xb_op OPS2[5][4] = {
    {{1, 0, 1, 4}, {1, 1, 3, 4}, XB_END},
    {{0, 1, 1, 4}, {1, 1, 3, 4}, XB_END},
    {{1, 0, 1, 4}, {0, 1, 1, 4}, {1, 1, 5, 6}, XB_END},
    {{1, 1, 1, 2}, XB_END},
    {{1, 1, 21, 100}, XB_END},
};
static const xb_op OPS3[5][6] = {
    {{2, 0, 1, 4}, {1, 2, 1, 4}, {2, 1, 3, 4}, {2, 2, 0, 0}, XB_END},
    {{0, 2, 1, 4}, {2, 1, 1, 4}, {1, 2, 3, 4}, {2, 2, 0, 0}, XB_END},
    {{2, 0, 1, 4}, {0, 2, 1, 4}, {2, 1, 3, 4}, {1, 2, 3, 4}, {2, 2, 0, 0}, XB_END},
    {{1, 2, 1, 8}, {2, 1, 1, 8}, {2, 2, 7, 8}, XB_END},
    {{2, 2, 45, 100}, XB_END},
};
static const xb_op OPS4[5][9] = {
    {{3, 0, 1, 4}, {2, 2, 1, 4}, {3, 1, 3, 4}, {2, 3, 3, 4}, {3, 2, 0, 0}, {3, 3, 0, 0}, XB_END},
    {{0, 3, 1, 4}, {2, 2, 1, 4}, {1, 3, 3, 4}, {3, 2, 3, 4}, {2, 3, 0, 0}, {3, 3, 0, 0}, XB_END},
    {{3, 1, 3, 4}, {1, 3, 3, 4}, {3, 0, 1, 4}, {0, 3, 1, 4}, {2, 2, 1, 3}, {3, 3, 0, 0}, {3, 2, 0, 0}, {2, 3, 0, 0}, XB_END},
    {{3, 2, 1, 2}, {2, 3, 1, 2}, {3, 3, 0, 0}, XB_END},
    {{3, 3, 68, 100}, {3, 2, 9, 100}, {2, 3, 9, 100}, XB_END},
};

/* chi so nhan 3x3 (a b c / d e f / g h i) sau khi xoay */
static const uint8_t KROT[4][9] = {
    {0, 1, 2, 3, 4, 5, 6, 7, 8},
    {6, 3, 0, 7, 4, 1, 8, 5, 2},
    {8, 7, 6, 5, 4, 3, 2, 1, 0},
    {2, 5, 8, 1, 4, 7, 0, 3, 6},
};

static inline unsigned rot_info(unsigned b, int rot) {
    switch (rot) {
    case 1: return ((b << 2) | (b >> 6)) & 0xFF;
    case 2: return ((b << 4) | (b >> 4)) & 0xFF;
    case 3: return ((b << 6) | (b >> 2)) & 0xFF;
    default: return b;
    }
}

static inline int ceq(uint32_t p1, uint32_t p2) { return cdist(p1, p2) < XB_EQ_TOL; }

static void xb_apply(const xb_op *ops, uint32_t col, uint32_t *out, int stride, int N, int rot) {
    for (; ops->i != 255; ops++) {
        int I = ops->i, J = ops->j, io, jo;
        switch (rot) {
        case 1: io = N - 1 - J; jo = I; break;
        case 2: io = N - 1 - I; jo = N - 1 - J; break;
        case 3: io = J; jo = N - 1 - I; break;
        default: io = I; jo = J; break;
        }
        uint32_t *p = out + jo + (size_t)io * stride;
        *p = ops->n ? cgrad(col, *p, ops->m, ops->n) : col;
    }
}

static void xb_blend_pixel(int N, const uint32_t *ker, uint32_t *out, int stride, unsigned info, int rot) {
    unsigned blend = rot_info(info, rot);
    unsigned bR = (blend >> 4) & 3;
    if (bR < BL_NORMAL) return;
    const uint8_t *m = KROT[rot];
    uint32_t b = ker[m[1]], c = ker[m[2]], d = ker[m[3]], e = ker[m[4]], f = ker[m[5]], g = ker[m[6]], h = ker[m[7]],
             i = ker[m[8]];
    int line;
    if (bR >= BL_DOMINANT) line = 1;
    else if (((blend >> 2) & 3) != BL_NONE && !ceq(e, g)) line = 0;      /* TopR */
    else if (((blend >> 6) & 3) != BL_NONE && !ceq(e, c)) line = 0;      /* BottomL */
    else if (!ceq(e, i) && ceq(g, h) && ceq(h, i) && ceq(i, f) && ceq(f, c)) line = 0;
    else line = 1;
    uint32_t px = cdist(e, f) <= cdist(e, h) ? f : h;
    const xb_op *tab;
    int kind;
    if (line) {
        float fg = cdist(f, g), hc = cdist(h, c);
        int shallow = XB_STEEP * fg <= hc && e != g && d != g;
        int steep = XB_STEEP * hc <= fg && e != c && b != c;
        kind = shallow ? (steep ? 2 : 0) : (steep ? 1 : 3);
    } else {
        kind = 4;
    }
    tab = N == 2 ? OPS2[kind] : N == 3 ? OPS3[kind] : OPS4[kind];
    xb_apply(tab, px, out, stride, N, rot);
}

void hd_xbrz(int N, const uint32_t *src, uint32_t *dst, int w, int h) {
    if (w <= 0 || h <= 0) return;
    if (N < 2) N = 2;
    if (N > 4) N = 4;
    int dw = w * N;
    uint8_t *info = (uint8_t *)calloc((size_t)w * h, 1);
    if (!info) {
        /* het bo nho: phong to theo diem anh */
        for (int y = 0; y < h * N; y++)
            for (int x = 0; x < dw; x++) dst[(size_t)y * dw + x] = src[(size_t)(y / N) * w + x / N];
        return;
    }
    for (int y = 0; y < h; y++) {
        const uint32_t *s0 = src + (size_t)w * (y > 0 ? y - 1 : 0);
        const uint32_t *s1 = src + (size_t)w * y;
        const uint32_t *s2 = src + (size_t)w * (y + 1 < h ? y + 1 : h - 1);
        const uint32_t *s3 = src + (size_t)w * (y + 2 < h ? y + 2 : h - 1);
        for (int x = 0; x < w; x++) {
            int xm = x > 0 ? x - 1 : 0, xp = x + 1 < w ? x + 1 : w - 1, xq = x + 2 < w ? x + 2 : w - 1;
            uint32_t kf = s1[x], kg = s1[xp], kj = s2[x], kk = s2[xp];
            if ((kf == kg && kj == kk) || (kf == kj && kg == kk)) continue;
            uint32_t kb = s0[x], kc = s0[xp], ke = s1[xm], kh = s1[xq], ki = s2[xm], kl = s2[xq], kn = s3[x], ko = s3[xp];
            float jg = cdist(ki, kf) + cdist(kf, kc) + cdist(kn, kk) + cdist(kk, kh) + 4.0f * cdist(kj, kg);
            float fk = cdist(ke, kj) + cdist(kj, ko) + cdist(kb, kg) + cdist(kg, kl) + 4.0f * cdist(kf, kk);
            unsigned bf = 0, bg = 0, bj = 0, bk = 0;
            if (jg < fk) {
                unsigned v = XB_DOMINANT * jg < fk ? BL_DOMINANT : BL_NORMAL;
                if (kf != kg && kf != kj) bf = v;
                if (kk != kj && kk != kg) bk = v;
            } else if (fk < jg) {
                unsigned v = XB_DOMINANT * fk < jg ? BL_DOMINANT : BL_NORMAL;
                if (kj != kf && kj != kk) bj = v;
                if (kg != kf && kg != kk) bg = v;
            }
            info[(size_t)y * w + x] |= (uint8_t)(bf << 4);                       /* goc duoi phai cua (x, y) */
            if (x + 1 < w) info[(size_t)y * w + x + 1] |= (uint8_t)(bg << 6);     /* goc duoi trai cua (x+1, y) */
            if (y + 1 < h) {
                info[(size_t)(y + 1) * w + x] |= (uint8_t)(bj << 2);             /* goc tren phai cua (x, y+1) */
                if (x + 1 < w) info[(size_t)(y + 1) * w + x + 1] |= (uint8_t)bk; /* goc tren trai cua (x+1, y+1) */
            }
        }
    }
    for (int y = 0; y < h; y++) {
        const uint32_t *s0 = src + (size_t)w * (y > 0 ? y - 1 : 0);
        const uint32_t *s1 = src + (size_t)w * y;
        const uint32_t *s2 = src + (size_t)w * (y + 1 < h ? y + 1 : h - 1);
        uint32_t *out = dst + (size_t)N * y * dw;
        for (int x = 0; x < w; x++, out += N) {
            uint32_t e = s1[x];
            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++) out[(size_t)r * dw + c] = e;
            unsigned bi = info[(size_t)y * w + x];
            if (!bi) continue;
            int xm = x > 0 ? x - 1 : 0, xp = x + 1 < w ? x + 1 : w - 1;
            uint32_t ker[9] = {s0[xm], s0[x], s0[xp], s1[xm], e, s1[xp], s2[xm], s2[x], s2[xp]};
            xb_blend_pixel(N, ker, out, dw, bi, 0);
            xb_blend_pixel(N, ker, out, dw, bi, 1);
            xb_blend_pixel(N, ker, out, dw, bi, 2);
            xb_blend_pixel(N, ker, out, dw, bi, 3);
        }
    }
    free(info);
}

/* ------------------------------------------------------------------ bicubic (Catmull-Rom), alpha nhan truoc */
static inline float cr_w(float t) {
    t = fabsf(t);
    if (t < 1.0f) return 1.5f * t * t * t - 2.5f * t * t + 1.0f;
    if (t < 2.0f) return -0.5f * t * t * t + 2.5f * t * t - 4.0f * t + 2.0f;
    return 0.0f;
}

static inline uint8_t clamp8(float v) { return v <= 0.0f ? 0 : v >= 255.0f ? 255 : (uint8_t)(v + 0.5f); }

void hd_bicubic(int N, const uint32_t *src, uint32_t *dst, int w, int h) {
    if (w <= 0 || h <= 0 || N < 1) return;
    if (N > 8) N = 8;
    int dw = w * N, dh = h * N;
    /* trong so theo pha: diem dich X ung voi toa do nguon (X + 0.5) / N - 0.5 */
    float wt[8][4];
    int base[8];
    for (int p = 0; p < N; p++) {
        float sx = (p + 0.5f) / N - 0.5f;
        int i0 = (int)floorf(sx);
        float fr = sx - i0, sum = 0;
        base[p] = i0 - 1;
        for (int k = 0; k < 4; k++) {
            wt[p][k] = cr_w((float)(k - 1) - fr);
            sum += wt[p][k];
        }
        for (int k = 0; k < 4; k++) wt[p][k] /= sum;
    }
    /* buoc 1: ngang, ra bo dem float 4 kenh (nhan truoc alpha) co dw x h */
    float *tmp = (float *)malloc((size_t)dw * h * 4 * sizeof(float));
    if (!tmp) {
        for (int y = 0; y < dh; y++)
            for (int x = 0; x < dw; x++) dst[(size_t)y * dw + x] = src[(size_t)(y / N) * w + x / N];
        return;
    }
    for (int y = 0; y < h; y++) {
        const uint32_t *s = src + (size_t)y * w;
        float *o = tmp + (size_t)y * dw * 4;
        for (int x = 0; x < w; x++) {
            for (int p = 0; p < N; p++, o += 4) {
                float a = 0, r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int xi = x + base[p] + k;
                    if (xi < 0) xi = 0;
                    else if (xi >= w) xi = w - 1;
                    uint32_t v = s[xi];
                    float wa = wt[p][k] * PA(v);
                    a += wa;
                    wa *= (1.0f / 255.0f);
                    r += wa * PR(v);
                    g += wa * PG(v);
                    b += wa * PB(v);
                }
                o[0] = a;
                o[1] = r;
                o[2] = g;
                o[3] = b;
            }
        }
    }
    /* buoc 2: doc */
    for (int y = 0; y < h; y++) {
        for (int p = 0; p < N; p++) {
            uint32_t *d = dst + (size_t)(y * N + p) * dw;
            const float *rows[4];
            for (int k = 0; k < 4; k++) {
                int yi = y + base[p] + k;
                if (yi < 0) yi = 0;
                else if (yi >= h) yi = h - 1;
                rows[k] = tmp + (size_t)yi * dw * 4;
            }
            const float w0 = wt[p][0], w1 = wt[p][1], w2 = wt[p][2], w3 = wt[p][3];
            for (int x = 0; x < dw; x++) {
                const float *q0 = rows[0] + x * 4, *q1 = rows[1] + x * 4, *q2 = rows[2] + x * 4, *q3 = rows[3] + x * 4;
                float a = w0 * q0[0] + w1 * q1[0] + w2 * q2[0] + w3 * q3[0];
                if (a < 0.5f) {
                    d[x] = 0;
                    continue;
                }
                if (a > 255.0f) a = 255.0f;
                float inv = 255.0f / a;
                float r = (w0 * q0[1] + w1 * q1[1] + w2 * q2[1] + w3 * q3[1]) * inv;
                float g = (w0 * q0[2] + w1 * q1[2] + w2 * q2[2] + w3 * q3[2]) * inv;
                float b = (w0 * q0[3] + w1 * q1[3] + w2 * q2[3] + w3 * q3[3]) * inv;
                d[x] = ((uint32_t)clamp8(a) << 24) | ((uint32_t)clamp8(r) << 16) | ((uint32_t)clamp8(g) << 8) | clamp8(b);
            }
        }
    }
    free(tmp);
}

/* ------------------------------------------------------------------ doi co bang trung binh dien tich */
void hd_area(const uint32_t *src, int sw, int sh, uint32_t *dst, int dw, int dh) {
    if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
    if (sw == dw && sh == dh) {
        memcpy(dst, src, (size_t)sw * sh * 4);
        return;
    }
    double fx = (double)sw / dw, fy = (double)sh / dh;
    for (int y = 0; y < dh; y++) {
        double y0 = y * fy, y1 = (y + 1) * fy;
        int iy0 = (int)y0, iy1 = (int)ceil(y1);
        if (iy1 > sh) iy1 = sh;
        for (int x = 0; x < dw; x++) {
            double x0 = x * fx, x1 = (x + 1) * fx;
            int ix0 = (int)x0, ix1 = (int)ceil(x1);
            if (ix1 > sw) ix1 = sw;
            double a = 0, r = 0, g = 0, b = 0, ws = 0;
            for (int yy = iy0; yy < iy1; yy++) {
                double wy = (yy + 1 < y1 ? yy + 1 : y1) - (yy > y0 ? yy : y0);
                if (wy <= 0) continue;
                const uint32_t *s = src + (size_t)yy * sw;
                for (int xx = ix0; xx < ix1; xx++) {
                    double wx = (xx + 1 < x1 ? xx + 1 : x1) - (xx > x0 ? xx : x0);
                    if (wx <= 0) continue;
                    double wgt = wx * wy;
                    uint32_t v = s[xx];
                    double wa = wgt * PA(v);
                    ws += wgt;
                    a += wa;
                    r += wa * PR(v);
                    g += wa * PG(v);
                    b += wa * PB(v);
                }
            }
            if (ws <= 0 || a <= 0) {
                dst[(size_t)y * dw + x] = 0;
                continue;
            }
            double al = a / ws;
            uint32_t A = (uint32_t)(al + 0.5), R = (uint32_t)(r / a + 0.5), G = (uint32_t)(g / a + 0.5), B = (uint32_t)(b / a + 0.5);
            if (A > 255) A = 255;
            if (R > 255) R = 255;
            if (G > 255) G = 255;
            if (B > 255) B = 255;
            dst[(size_t)y * dw + x] = A ? (A << 24) | (R << 16) | (G << 8) | B : 0;
        }
    }
}

/* ------------------------------------------------------------------ nhan dang */
/* anh x2 chi la anh x1 nhan doi tung diem (moi khoi 2x2 thang hang dong mau)? cho phep toi da ~0,5% khoi lech */
int hd_doubled(const uint32_t *src, int w, int h) {
    if (w < 2 || h < 2 || (w & 1) || (h & 1)) return 0;
    long bad = 0, tot = 0;
    for (int y = 0; y < h; y += 2) {
        const uint32_t *a = src + (size_t)y * w, *b = a + w;
        for (int x = 0; x < w; x += 2) {
            uint32_t p = a[x], q = a[x + 1], r = b[x], s = b[x + 1];
            if (!((p | q | r | s) >> 24)) continue;      /* khoi trong suot */
            tot++;
            if (p != q || p != r || p != s) bad++;
        }
    }
    return tot > 0 && bad * 200 <= tot;
}

/* anh "min" (ve 3D, anh chup, chuyen mau lien tuc): it cap diem ke nhau trung mau -> phong to bicubic thay vi xBRZ */
int hd_smooth(const uint32_t *src, int w, int h) {
    if (w < 8 || h < 8) return 0;
    long same = 0, tot = 0;
    for (int y = 0; y < h; y++) {
        const uint32_t *s = src + (size_t)y * w;
        for (int x = 0; x + 1 < w; x++) {
            uint32_t p = s[x], q = s[x + 1];
            if ((p >> 24) < 250 || (q >> 24) < 250) continue;
            tot++;
            if (p == q) same++;
        }
    }
    if (tot < 200) return 0;
    return same * 100 < tot * 12;     /* duoi 12% cap ke nhau trung mau */
}

static void norm_alpha(uint32_t *p, size_t n) {
    for (size_t i = 0; i < n; i++)
        if (!(p[i] >> 24)) p[i] = 0;
}

/* phong to noi suy 2 chieu (alpha nhan truoc) - anh min, ti le khong nguyen (vd x3 -> x4) */
void hd_lin(const uint32_t *src, int sw, int sh, uint32_t *dst, int dw, int dh) {
    if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
    int *ix = (int *)malloc((size_t)dw * 2 * sizeof(int));
    if (!ix) {
        hd_area(src, sw, sh, dst, dw, dh);
        return;
    }
    for (int x = 0; x < dw; x++) {
        double u = (x + 0.5) * sw / dw - 0.5;
        if (u < 0) u = 0;
        if (u > sw - 1) u = sw - 1;
        int x0 = (int)u;
        ix[x * 2] = x0;
        ix[x * 2 + 1] = x0 + 1 < sw ? (int)((u - x0) * 256.0 + 0.5) : 0;
    }
    for (int y = 0; y < dh; y++) {
        double v = (y + 0.5) * sh / dh - 0.5;
        if (v < 0) v = 0;
        if (v > sh - 1) v = sh - 1;
        int y0 = (int)v;
        int ty = y0 + 1 < sh ? (int)((v - y0) * 256.0 + 0.5) : 0;
        const uint32_t *r0 = src + (size_t)y0 * sw, *r1 = ty ? r0 + sw : r0;
        uint32_t *d = dst + (size_t)y * dw;
        for (int x = 0; x < dw; x++) {
            int x0 = ix[x * 2], tx = ix[x * 2 + 1];
            int x1 = tx ? x0 + 1 : x0;
            uint32_t c00 = r0[x0], c10 = r0[x1], c01 = r1[x0], c11 = r1[x1];
            uint32_t w00 = (uint32_t)(256 - tx) * (256 - ty), w10 = (uint32_t)tx * (256 - ty), w01 = (uint32_t)(256 - tx) * ty,
                     w11 = (uint32_t)tx * ty;
            uint32_t a00 = c00 >> 24, a10 = c10 >> 24, a01 = c01 >> 24, a11 = c11 >> 24;
            if ((a00 & a10 & a01 & a11) == 255) {
                uint32_t r = (((c00 >> 16) & 255) * w00 + ((c10 >> 16) & 255) * w10 + ((c01 >> 16) & 255) * w01 + ((c11 >> 16) & 255) * w11 + 32768) >> 16;
                uint32_t g = (((c00 >> 8) & 255) * w00 + ((c10 >> 8) & 255) * w10 + ((c01 >> 8) & 255) * w01 + ((c11 >> 8) & 255) * w11 + 32768) >> 16;
                uint32_t b = ((c00 & 255) * w00 + (c10 & 255) * w10 + (c01 & 255) * w01 + (c11 & 255) * w11 + 32768) >> 16;
                d[x] = 0xFF000000u | (r << 16) | (g << 8) | b;
                continue;
            }
            uint64_t A = (uint64_t)a00 * w00 + (uint64_t)a10 * w10 + (uint64_t)a01 * w01 + (uint64_t)a11 * w11;
            if (A < 32768) {
                d[x] = 0;
                continue;
            }
            uint64_t q00 = (uint64_t)a00 * w00, q10 = (uint64_t)a10 * w10, q01 = (uint64_t)a01 * w01, q11 = (uint64_t)a11 * w11;
            uint32_t r = (uint32_t)((((c00 >> 16) & 255) * q00 + ((c10 >> 16) & 255) * q10 + ((c01 >> 16) & 255) * q01 + ((c11 >> 16) & 255) * q11 + A / 2) / A);
            uint32_t g = (uint32_t)((((c00 >> 8) & 255) * q00 + ((c10 >> 8) & 255) * q10 + ((c01 >> 8) & 255) * q01 + ((c11 >> 8) & 255) * q11 + A / 2) / A);
            uint32_t b = (uint32_t)(((c00 & 255) * q00 + (c10 & 255) * q10 + (c01 & 255) * q01 + (c11 & 255) * q11 + A / 2) / A);
            uint32_t al = (uint32_t)((A + 32768) >> 16);
            if (al > 255) al = 255;
            d[x] = (al << 24) | (r << 16) | (g << 8) | b;
        }
    }
    free(ix);
}

uint32_t *hd_make(const uint32_t *src, int w, int h, int s, int K, int smooth, int *ow, int *oh) {
    if (s < 1) s = 1;
    if (K < 1) K = 1;
    if (w <= 0 || h <= 0 || w % s || h % s) return 0;
    int lw = w / s, lh = h / s;                     /* kich thuoc logic */
    size_t n = (size_t)w * h;
    uint32_t *cur = (uint32_t *)malloc(n * 4);
    if (!cur) return 0;
    memcpy(cur, src, n * 4);
    norm_alpha(cur, n);
    int cw = w, ch = h, cs = s;
    if (smooth < 0) smooth = hd_smooth(cur, cw, ch);
    if (!smooth && cs == 2 && hd_doubled(cur, cw, ch)) {
        /* x2 nhan doi khoi -> ve x1 de xBRZ lam tron dung bac thang */
        uint32_t *t = (uint32_t *)malloc((size_t)lw * lh * 4);
        if (t) {
            for (int y = 0; y < lh; y++)
                for (int x = 0; x < lw; x++) t[(size_t)y * lw + x] = cur[(size_t)(y * 2) * cw + x * 2];
            free(cur);
            cur = t;
            cw = lw;
            ch = lh;
            cs = 1;
        }
    }
    if (smooth && cs < K && K % cs != 0) {
        /* anh min, ti le khong nguyen (anh dau truong x3 len man x4...): noi suy thang ve dung co */
        uint32_t *t = (uint32_t *)malloc((size_t)lw * K * lh * K * 4);
        if (t) {
            hd_lin(cur, cw, ch, t, lw * K, lh * K);
            free(cur);
            *ow = lw * K;
            *oh = lh * K;
            return t;
        }
    }
    /* phong to len he so >= K bang cac buoc nguyen */
    while (cs < K) {
        int f = K / cs;                 /* 4/1 -> 4, 4/2 -> 2, 3/1 -> 3, 3/2 -> 1 (-> 2 roi thu nho), 4/3 -> 1 */
        if (f < 2) f = 2;
        if (f > 4) f = 4;
        uint32_t *t = (uint32_t *)malloc((size_t)cw * f * ch * f * 4);
        if (!t) break;
        if (smooth) hd_bicubic(f, cur, t, cw, ch);
        else hd_xbrz(f, cur, t, cw, ch);
        free(cur);
        cur = t;
        cw *= f;
        ch *= f;
        cs *= f;
    }
    if (cs != K) {
        uint32_t *t = (uint32_t *)malloc((size_t)lw * K * lh * K * 4);
        if (t) {
            hd_area(cur, cw, ch, t, lw * K, lh * K);
            free(cur);
            cur = t;
            cw = lw * K;
            ch = lh * K;
        } else {
            free(cur);
            return 0;
        }
    }
    *ow = cw;
    *oh = ch;
    return cur;
}
