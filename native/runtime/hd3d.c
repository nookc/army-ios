/* hd3d.c - hieu ung "3D" cua may HD (nam trong gfx.c, sau hdkho.c).
 *
 * 1) Khoi noi (hd_post, luong phu, luc phong to anh): moi vat the / nhan vat duoc danh sang theo hinh khoi - vien phia
 *    trai-tren sang len, phia phai-duoi toi di (do cao tinh tu khoang cach toi mep hinh) -> hinh phang thanh hinh co khoi.
 * 2) Bong do (hd_shadow_make + sh_draw): moi vat the co bong mem do xuong duoi / len vat phia sau (mat na 8 bit tinh
 *    san luc phong to, chi phan lo ra ngoai hinh) -> cac lop tach nhau co chieu sau.
 * 3) Hau ky canh (gfx_hd_scene, sau khi ve xong the gioi, truoc HUD): anh sang loa (bloom) quanh vung sang, tia nang
 *    chuyen dong, toi goc (vignette), bui sang bay.
 * Chi chay o ban "Avatar 250 HD" (gfx_hdk > 1) va chi voi anh ve trong luc ve the gioi (Graphics.hdCanh).
 */
#include <math.h>

int gfx_world;                      /* dang ve the gioi (giua hdCanh(1) va hdCanh(2)) */

/* ------------------------------------------------------------ 1) khoi noi */
static void hd_relief(uint32_t *px, int w, int h, int k) {
    size_t n = (size_t)w * h;
    if (w < 5 * k || h < 5 * k || n > 5000000) return;
    int R = 4 * k;                              /* be rong vung bo tron mep (diem anh that) */
    int dmax = 3 * R;
    uint16_t *D = (uint16_t *)malloc(n * 2);
    uint8_t *Hg = (uint8_t *)malloc(n);
    if (!D || !Hg) {
        free(D);
        free(Hg);
        return;
    }
    for (size_t i = 0; i < n; i++) D[i] = (px[i] >> 24) >= 128 ? 60000 : 0;
    /* khoang cach toi mep (chamfer 3-4); ngoai bien anh coi nhu con la than hinh (anh cat sat / manh ghep khong bi vien) */
    for (int y = 0; y < h; y++) {
        uint16_t *r = D + (size_t)y * w, *u = y > 0 ? r - w : 0;
        for (int x = 0; x < w; x++) {
            unsigned v = r[x];
            if (!v) continue;
            if (x > 0 && r[x - 1] + 3u < v) v = r[x - 1] + 3u;
            if (u) {
                if (u[x] + 3u < v) v = u[x] + 3u;
                if (x > 0 && u[x - 1] + 4u < v) v = u[x - 1] + 4u;
                if (x + 1 < w && u[x + 1] + 4u < v) v = u[x + 1] + 4u;
            }
            r[x] = (uint16_t)v;
        }
    }
    for (int y = h - 1; y >= 0; y--) {
        uint16_t *r = D + (size_t)y * w, *u = y + 1 < h ? r + w : 0;
        for (int x = w - 1; x >= 0; x--) {
            unsigned v = r[x];
            if (!v) continue;
            if (x + 1 < w && r[x + 1] + 3u < v) v = r[x + 1] + 3u;
            if (u) {
                if (u[x] + 3u < v) v = u[x] + 3u;
                if (x > 0 && u[x - 1] + 4u < v) v = u[x - 1] + 4u;
                if (x + 1 < w && u[x + 1] + 4u < v) v = u[x + 1] + 4u;
            }
            r[x] = (uint16_t)v;
        }
    }
    /* do cao: 1/4 hinh sin tu mep vao (mep doc, vao trong phang dan) */
    uint8_t lut[3 * 4 * 4 + 1];
    for (int d = 0; d <= dmax; d++) lut[d] = (uint8_t)(sin(1.5707963 * d / dmax) * 255.0 + 0.5);
    for (size_t i = 0; i < n; i++) Hg[i] = lut[D[i] > dmax ? dmax : D[i]];
    free(D);
    /* danh sang tu trai-tren: e = do cao phia xa nguon sang - phia gan nguon sang */
    int sx = k >= 4 ? 1 : 1, sy = k >= 4 ? 2 : 1;
    for (int y = 0; y < h; y++) {
        int ya = y + sy < h ? y + sy : y, yb = y - sy >= 0 ? y - sy : y;
        uint32_t *row = px + (size_t)y * w;
        const uint8_t *ha = Hg + (size_t)ya * w, *hb = Hg + (size_t)yb * w, *hc = Hg + (size_t)y * w;
        for (int x = 0; x < w; x++) {
            uint32_t c = row[x];
            if ((c >> 24) < 8) continue;
            int xa = x + sx < w ? x + sx : x, xb = x - sx >= 0 ? x - sx : x;
            int e = (int)ha[xa] - (int)hb[xb];
            if (ya == y || yb == y || xa == x || xb == x) e = 0;       /* sat bien anh: khong danh sang */
            if (e == 0) continue;
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (e > 0) {
                int m = r > g ? r : g;
                if (b > m) m = b;
                int lf = m <= 40 ? 0 : m >= 160 ? 256 : (m - 40) * 256 / 120;      /* net vien toi giu nguyen */
                int t = e * 2;
                if (t > 255) t = 255;
                t = t * 80 / 255 * lf / 256;                /* toi da ~31% ve phia trang */
                r += (255 - r) * t / 256;
                g += (255 - g) * t / 256;
                b += (255 - b) * t / 256;
            } else {
                int t = -e * 2;
                if (t > 255) t = 255;
                t = 256 - t * 78 / 255;                     /* toi da ~-30% */
                r = r * t / 256;
                g = g * t / 256;
                b = b * t / 256;
            }
            (void)hc;
            row[x] = (c & 0xFF000000u) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
        }
    }
    free(Hg);
}

/* anh co phai "vat the" (co nen trong suot, than dac)? 0 = o nen / anh nen dac kin hoac khoi / hao quang ban trong suot */
static int hd_is_sprite(const uint32_t *px, int w, int h) {
    size_t n = (size_t)w * h, tr = 0, semi = 0, op = 0;
    size_t step = n > 400000 ? 3 : 1;
    for (size_t i = 0; i < n; i += step) {
        uint32_t a = px[i] >> 24;
        if (a < 8) tr++;
        else if (a < 235) semi++;
        else op++;
    }
    size_t tot = tr + semi + op;
    if (tr * 100 < tot * 5) return 0;
    if (op == 0 || semi * 100 > (semi + op) * 34) return 0;
    return 1;
}

static void hd_post(uint32_t *px, int w, int h, int k, int mode) {
    if ((mode & 1) && hd_is_sprite(px, w, h)) hd_relief(px, w, h, k);
}

/* ------------------------------------------------------------ 2) bong do
 * mat na 8 bit rong w, cao h + mg: do toi cua bong tai tung diem (chi phan nam ngoai than hinh). */
static uint8_t *hd_shadow_make(const uint32_t *px, int w, int h, int k, int *omg) {
    size_t n = (size_t)w * h;
    if (w < 4 * k || h < 4 * k || n > 3000000 || !hd_is_sprite(px, w, h)) return 0;
    int dy = 2 * k, r = k + k / 2;              /* do lech xuong, ban kinh lam mem */
    int mg = dy + r;
    int H2 = h + mg;
    uint8_t *A = (uint8_t *)calloc((size_t)w * H2, 1), *T = (uint8_t *)malloc((size_t)w * H2);
    if (!A || !T) {
        free(A);
        free(T);
        return 0;
    }
    /* alpha lech xuong dy */
    for (int y = 0; y < h; y++) {
        const uint32_t *s = px + (size_t)y * w;
        uint8_t *d = A + (size_t)(y + dy) * w;
        for (int x = 0; x < w; x++) d[x] = (uint8_t)(s[x] >> 24);
    }
    /* lam mem: hop ngang roi hop doc (ban kinh r) */
    int win = 2 * r + 1;
    for (int y = 0; y < H2; y++) {
        const uint8_t *s = A + (size_t)y * w;
        uint8_t *d = T + (size_t)y * w;
        int sum = 0;
        for (int x = -r; x <= r; x++) sum += (x >= 0 && x < w) ? s[x] : 0;
        for (int x = 0; x < w; x++) {
            d[x] = (uint8_t)(sum / win);
            int o = x - r, i = x + r + 1;
            if (o >= 0) sum -= s[o];
            if (i < w) sum += s[i];
        }
    }
    int any = 0;
    for (int x = 0; x < w; x++) {
        int sum = 0;
        for (int y = -r; y <= r; y++) sum += (y >= 0 && y < H2) ? T[(size_t)y * w + x] : 0;
        for (int y = 0; y < H2; y++) {
            int v = sum / win;
            int o = y - r, i = y + r + 1;
            if (o >= 0) sum -= T[(size_t)o * w + x];
            if (i < H2) sum += T[(size_t)i * w + x];
            /* chi phan lo ra ngoai than hinh; do toi toi da ~40%. Duoi day anh: coi nhu than hinh keo dai tiep (anh bi
             * cat ngang than - dau, manh ghep, goc khung - khong do bong xuong phan ben duoi) */
            int own = (int)(px[(size_t)(y < h ? y : h - 1) * w + x] >> 24);
            v = v * (255 - own) / 255 * 104 / 255;
            A[(size_t)y * w + x] = (uint8_t)v;
            any |= v;
        }
    }
    free(T);
    if (!any) {
        free(A);
        return 0;
    }
    *omg = mg;
    return A;
}

/* lam toi 1 hang theo mat na (thua: phan lon la 0): d = d * (256 - m) / 256, 4 diem / buoc bang lenh vector */
static inline void row_shade(uint32_t *d, const uint8_t *m, int n) {
    int i = 0;
    while (i + 8 <= n) {
        uint64_t q;
        memcpy(&q, m + i, 8);
        if (!q) {
            i += 8;
            continue;
        }
#if defined(__aarch64__)
        {
            static const uint8_t idx[16] = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3};
            const uint8x16_t ix = vld1q_u8(idx);
            const uint32x4_t am = vdupq_n_u32(0xFF000000u);
            for (int j = 0; j < 8; j += 4) {
                uint32_t m4;
                memcpy(&m4, m + i + j, 4);
                if (!m4) continue;
                uint8x16_t f = vmvnq_u8(vqtbl1q_u8(vreinterpretq_u8_u32(vdupq_n_u32(m4)), ix));        /* 255 - m */
                uint8x16_t v = vreinterpretq_u8_u32(vld1q_u32(d + i + j));
                uint8x8_t lo = vshrn_n_u16(vmull_u8(vget_low_u8(v), vget_low_u8(f)), 8);
                uint8x8_t hi = vshrn_n_u16(vmull_u8(vget_high_u8(v), vget_high_u8(f)), 8);
                vst1q_u32(d + i + j, vorrq_u32(vreinterpretq_u32_u8(vcombine_u8(lo, hi)), am));
            }
        }
#elif defined(__SSE2__)
        {
            const __m128i z = _mm_setzero_si128(), c255 = _mm_set1_epi16(255), am = _mm_set1_epi32((int)0xFF000000u);
            for (int j = 0; j < 8; j += 4) {
                int m4;
                memcpy(&m4, m + i + j, 4);
                if (!m4) continue;
                __m128i mw = _mm_unpacklo_epi8(_mm_cvtsi32_si128(m4), z);       /* 4 tu: m0 m1 m2 m3 */
                mw = _mm_unpacklo_epi16(mw, mw);                                /* m0 m0 m1 m1 m2 m2 m3 m3 */
                __m128i flo = _mm_sub_epi16(c255, _mm_unpacklo_epi32(mw, mw));  /* m0 x4, m1 x4 */
                __m128i fhi = _mm_sub_epi16(c255, _mm_unpackhi_epi32(mw, mw));
                __m128i v = _mm_loadu_si128((const __m128i *)(d + i + j));
                __m128i lo = _mm_srli_epi16(_mm_mullo_epi16(_mm_unpacklo_epi8(v, z), flo), 8);
                __m128i hi = _mm_srli_epi16(_mm_mullo_epi16(_mm_unpackhi_epi8(v, z), fhi), 8);
                _mm_storeu_si128((__m128i *)(d + i + j), _mm_or_si128(_mm_packus_epi16(lo, hi), am));
            }
        }
#else
        for (int j = 0; j < 8; j++) {
            uint32_t a = m[i + j];
            if (a) {
                uint32_t v = d[i + j], f = 255 - a;
                d[i + j] = 0xFF000000u | ((((v & 0xFF00FFu) * f) >> 8) & 0xFF00FFu) | ((((v & 0x00FF00u) * f) >> 8) & 0x00FF00u);
            }
        }
#endif
        i += 8;
    }
    for (; i < n; i++) {
        uint32_t a = m[i];
        if (a) {
            uint32_t v = d[i], f = 255 - a;
            d[i] = 0xFF000000u | ((((v & 0xFF00FFu) * f) >> 8) & 0xFF00FFu) | ((((v & 0x00FF00u) * f) >> 8) & 0x00FF00u);
        }
    }
}

/* ve bong cua vung anh (toa do logic xs, ys, w, h cua anh) truoc khi ve chinh no. e->k phai bang he so dich. */
static void sh_draw(tgt *o, hd_ent *e, int xs, int ys, int w, int h, int tr, int px, int py) {
    if (!e->shd || (tr != 0 && tr != 2)) return;
    int k = e->k;
    int X0 = xs * k, Y0 = ys * k, PW = w * k, PH = h * k;
    if (X0 < 0 || Y0 < 0 || X0 + PW > e->w || Y0 + PH > e->h) return;
    if (Y0 + PH == e->h) PH += e->shm;          /* vung cham day anh: bong do tiep xuong duoi */
    int DX = px * k, DY = py * k;
    int cx0 = o->x0 * k, cy0 = o->y0 * k, cx1 = o->x1 * k, cy1 = o->y1 * k;
    int vx0 = DX > cx0 ? DX : cx0, vy0 = DY > cy0 ? DY : cy0;
    int vx1 = DX + PW < cx1 ? DX + PW : cx1, vy1 = DY + PH < cy1 ? DY + PH : cy1;
    if (vx1 <= vx0 || vy1 <= vy0) return;
    int n = vx1 - vx0;
    for (int yy = vy0; yy < vy1; yy++) {
        const uint8_t *m = e->shd + (size_t)(Y0 + (yy - DY)) * e->w;
        uint32_t *d = o->px + (size_t)yy * o->w + vx0;
        if (tr == 0) {
            row_shade(d, m + X0 + (vx0 - DX), n);
        } else {
            /* lat ngang: cot dich c <-> cot nguon X0 + PW - 1 - c */
            const uint8_t *q = m + X0 + PW - 1 - (vx0 - DX);
            uint8_t tam[512];
            for (int c = 0; c < n; c += 512) {
                int mm = n - c < 512 ? n - c : 512;
                for (int i = 0; i < mm; i++) tam[i] = q[-(c + i)];
                row_shade(d + c, tam, mm);
            }
        }
    }
}

/* ------------------------------------------------------------ 3) hau ky canh */
#define FXB 8                       /* o 8 x 8 diem anh that */
static uint16_t *fx_bl, *fx_tm;     /* anh sang loa: 3 kenh / o */
static uint16_t *fx_vg;             /* he so toi goc / o (256 = giu nguyen) */
static int fx_bw, fx_bh, fx_vw, fx_vh;
static uint8_t fx_ray_lut[4096];
static int fx_ray_ok;
int gfx_fx_bloom = 100, gfx_fx_vig = 100, gfx_fx_ray = 100, gfx_fx_bui = 100;      /* % cuong do (0 = tat) */

static int64_t fx_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void fx_blur(uint16_t *a, uint16_t *t, int bw, int bh, int r) {
    int win = 2 * r + 1;
    unsigned inv = 65536u / (unsigned)win + 1;      /* sum / win ~ sum * inv >> 16 (sum < 65536 / ... du chinh xac cho loa sang) */
    for (int y = 0; y < bh; y++) {
        const uint16_t *s = a + (size_t)y * bw * 3;
        uint16_t *d = t + (size_t)y * bw * 3;
        for (int c = 0; c < 3; c++) {
            int sum = 0;
            for (int x = -r; x <= r; x++) sum += s[(x < 0 ? 0 : x >= bw ? bw - 1 : x) * 3 + c];
            for (int x = 0; x < bw; x++) {
                d[x * 3 + c] = (uint16_t)(((unsigned)sum * inv) >> 16);
                int o = x - r, i = x + r + 1;
                sum -= s[(o < 0 ? 0 : o) * 3 + c];
                sum += s[(i >= bw ? bw - 1 : i) * 3 + c];
            }
        }
    }
    for (int x = 0; x < bw; x++) {
        for (int c = 0; c < 3; c++) {
            int sum = 0;
            for (int y = -r; y <= r; y++) sum += t[((size_t)(y < 0 ? 0 : y >= bh ? bh - 1 : y) * bw + x) * 3 + c];
            for (int y = 0; y < bh; y++) {
                a[((size_t)y * bw + x) * 3 + c] = (uint16_t)(((unsigned)sum * inv) >> 16);
                int o = y - r, i = y + r + 1;
                sum -= t[((size_t)(o < 0 ? 0 : o) * bw + x) * 3 + c];
                sum += t[((size_t)(i >= bh ? bh - 1 : i) * bw + x) * 3 + c];
            }
        }
    }
}

static inline uint32_t fx_add(uint32_t v, int r, int g, int b) {
    int R = (int)((v >> 16) & 255) + r, G = (int)((v >> 8) & 255) + g, B = (int)(v & 255) + b;
    if (R > 255) R = 255;
    if (G > 255) G = 255;
    if (B > 255) B = 255;
    return 0xFF000000u | ((uint32_t)R << 16) | ((uint32_t)G << 8) | (uint32_t)B;
}

/* cham sang mem (bui sang): cong mau vao dich */
static void fx_dot(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int cx, int cy, int rad, int amp) {
    int r2 = rad * rad;
    for (int y = cy - rad; y <= cy + rad; y++) {
        if (y < y0 || y >= y1 || y < 0 || y >= H) continue;
        for (int x = cx - rad; x <= cx + rad; x++) {
            if (x < x0 || x >= x1 || x < 0 || x >= W) continue;
            int d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (d2 >= r2) continue;
            int f = (r2 - d2) * 256 / r2;
            f = f * f >> 8;
            int a = amp * f >> 8;
            if (a <= 0) continue;
            uint32_t *p = fb + (size_t)y * W + x;
            *p = fx_add(*p, a, a * 245 >> 8, a * 215 >> 8);
        }
    }
}

/* fb: bo dem W x H (diem anh that), vung [x0,x1) x [y0,y1), k = diem anh that / diem logic */
static void gfx_hd_scene(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int k) {
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    if (x1 > W) x1 = W;
    if (y1 > H) y1 = H;
    int rw = x1 - x0, rh = y1 - y0;
    if (rw < 160 * k || rh < 120 * k) return;           /* vung nho (cua so con): bo qua */
    int bw = (rw + FXB - 1) / FXB, bh = (rh + FXB - 1) / FXB;
    if (bw != fx_bw || bh != fx_bh) {
        free(fx_bl);
        free(fx_tm);
        fx_bl = (uint16_t *)malloc((size_t)bw * bh * 3 * 2);
        fx_tm = (uint16_t *)malloc((size_t)bw * bh * 3 * 2);
        fx_bw = bw;
        fx_bh = bh;
    }
    if (!fx_bl || !fx_tm) return;
    if (bw != fx_vw || bh != fx_vh) {
        free(fx_vg);
        fx_vg = (uint16_t *)malloc((size_t)bw * bh * 2);
        fx_vw = bw;
        fx_vh = bh;
        if (fx_vg)
            for (int by = 0; by < bh; by++)
                for (int bx = 0; bx < bw; bx++) {
                    double dx = (bx + 0.5) / bw * 2 - 1, dy = (by + 0.5) / bh * 2 - 1;
                    double rr = sqrt(dx * dx * 0.85 + dy * dy * 1.0);
                    double t = (rr - 0.62) / (1.25 - 0.62);
                    if (t < 0) t = 0;
                    if (t > 1) t = 1;
                    t = t * t * (3 - 2 * t);
                    fx_vg[(size_t)by * bw + bx] = (uint16_t)(256 - t * 62);        /* goc toi ~24% */
                }
    }
    int64_t now = fx_ms();
    /* --- lay mau vung sang (moi o 4 diem), nguong mem */
    int do_bloom = gfx_fx_bloom > 0, co_sang = 0;
    if (do_bloom) {
        for (int by = 0; by < bh; by++) {
            int py = y0 + by * FXB;
            int ya = py + 2 < y1 ? py + 2 : y1 - 1, yb = py + 6 < y1 ? py + 6 : y1 - 1;
            uint16_t *d = fx_bl + (size_t)by * bw * 3;
            for (int bx = 0; bx < bw; bx++, d += 3) {
                int pxx = x0 + bx * FXB;
                int xa = pxx + 2 < x1 ? pxx + 2 : x1 - 1, xb = pxx + 6 < x1 ? pxx + 6 : x1 - 1;
                uint32_t c0 = fb[(size_t)ya * W + xa], c1 = fb[(size_t)ya * W + xb], c2 = fb[(size_t)yb * W + xa],
                         c3 = fb[(size_t)yb * W + xb];
                int r = (int)(((c0 >> 16) & 255) + ((c1 >> 16) & 255) + ((c2 >> 16) & 255) + ((c3 >> 16) & 255)) >> 2;
                int g = (int)(((c0 >> 8) & 255) + ((c1 >> 8) & 255) + ((c2 >> 8) & 255) + ((c3 >> 8) & 255)) >> 2;
                int b = (int)((c0 & 255) + (c1 & 255) + (c2 & 255) + (c3 & 255)) >> 2;
                int key = (r * 77 + g * 150 + b * 29) >> 8;     /* do sang (troi xanh / co khong tinh, trang va vang moi loa) */
                if (key <= 218) {
                    d[0] = d[1] = d[2] = 0;
                } else {
                    int wgt = (key - 218) * 256 / 37;       /* 0..256 */
                    wgt = wgt * wgt >> 8;
                    wgt = wgt * gfx_fx_bloom / 100 * 118 / 256;     /* cuong do loa (cong bao hoa) */
                    d[0] = (uint16_t)(r * wgt >> 8);
                    d[1] = (uint16_t)(g * wgt >> 8);
                    d[2] = (uint16_t)(b * wgt >> 8);
                    co_sang = 1;
                }
            }
        }
        int r = (5 * k + 4) / FXB;
        if (r < 1) r = 1;
        if (co_sang) {
            fx_blur(fx_bl, fx_tm, bw, bh, r);
            fx_blur(fx_bl, fx_tm, bw, bh, r);
        } else {
            do_bloom = 0;
        }
    }
    /* --- tron vao khung: loa sang (noi suy trong o) + tia nang + toi goc. Moi o 8 x 8: tinh san 8 gia tri cong cua
     * 1 hang (buoc cong don, khong nhan chia), roi nhan he so toi goc + cong bao hoa bang lenh vector */
    double tt = (double)(now % 6000000) / 1000.0;
    if (!fx_ray_ok) {
        fx_ray_ok = 1;
        for (int i = 0; i < 4096; i++) {
            double u = i * 10.0 / 4096.0;
            double sv = sin(u * 6.2831853) * 0.5 + sin(u * 2.3 * 6.2831853 + 1.7) * 0.5;
            double q = sv > 0.25 ? (sv - 0.25) / 0.75 : 0;
            fx_ray_lut[i] = (uint8_t)(q * q * 13.0 + 0.5);
        }
    }
    float ray_sc = 409.6f / (float)(k * 46);
    float ray_t = (float)(fmod(tt * 0.07, 10.0) * 409.6);
    for (int by = 0; by < bh; by++) {
        int py0 = y0 + by * FXB, py1 = py0 + FXB < y1 ? py0 + FXB : y1;
        int by1 = by + 1 < bh ? by + 1 : by;
        /* tia nang: dai sang cheo, troi cham, manh o tren va tat dan xuong duoi */
        int fade = 256 - by * 256 / bh;
        fade = fade * fade >> 8;
        for (int bx = 0; bx < bw; bx++) {
            int px0 = x0 + bx * FXB, px1 = px0 + FXB < x1 ? px0 + FXB : x1;
            int bx1 = bx + 1 < bw ? bx + 1 : bx;
            int vg = (fx_vg && gfx_fx_vig > 0) ? 256 - (256 - fx_vg[(size_t)by * bw + bx]) * gfx_fx_vig / 100 : 256;
            int ray = 0;
            if (gfx_fx_ray > 0 && fade > 8) {
                /* pha u = (x + 0,55 y) / (46 diem logic) + troi theo thoi gian; mau lap lai sau 10 don vi -> bang 4096 o */
                unsigned iu = (unsigned)((bx * FXB + by * FXB * 0.55f) * ray_sc + ray_t) & 4095u;
                ray = fx_ray_lut[iu] * fade * gfx_fx_ray / 25600;
            }
            const uint16_t *b00 = 0, *b10 = 0, *b01 = 0, *b11 = 0;
            int lit = 0;
            if (do_bloom) {
                b00 = fx_bl + ((size_t)by * bw + bx) * 3;
                b10 = fx_bl + ((size_t)by * bw + bx1) * 3;
                b01 = fx_bl + ((size_t)by1 * bw + bx) * 3;
                b11 = fx_bl + ((size_t)by1 * bw + bx1) * 3;
                lit = (b00[0] | b00[1] | b00[2] | b10[0] | b10[1] | b10[2] | b01[0] | b01[1] | b01[2] | b11[0] | b11[1] | b11[2]) > 1;
            }
            if (!lit && vg >= 256 && ray <= 0) continue;
            int n = px1 - px0;
            uint32_t add[FXB];
            uint32_t rayc = ((uint32_t)ray << 16) | ((uint32_t)(ray * 240 >> 8) << 8) | (uint32_t)(ray * 200 >> 8);
            if (!lit)
                for (int i = 0; i < FXB; i++) add[i] = rayc;
            for (int y = py0; y < py1; y++) {
                uint32_t *p = fb + (size_t)y * W + px0;
                if (lit) {
                    int fy = y - py0;
                    int acc[3], st[3];
                    for (int c = 0; c < 3; c++) {
                        int l = b00[c] * (FXB - fy) + b01[c] * fy, r = b10[c] * (FXB - fy) + b11[c] * fy;     /* x8 */
                        acc[c] = l * FXB;
                        st[c] = r - l;
                    }
                    for (int i = 0; i < FXB; i++) {
                        int R = (acc[0] >> 6) + ray, G = (acc[1] >> 6) + (ray * 240 >> 8), B = (acc[2] >> 6) + (ray * 200 >> 8);
                        if (R > 255) R = 255;
                        if (G > 255) G = 255;
                        if (B > 255) B = 255;
                        add[i] = ((uint32_t)R << 16) | ((uint32_t)G << 8) | (uint32_t)B;
                        acc[0] += st[0];
                        acc[1] += st[1];
                        acc[2] += st[2];
                    }
                }
                int i = 0;
#if defined(__aarch64__)
                {
                    uint8x8_t f8 = vdup_n_u8((uint8_t)(vg > 255 ? 255 : vg));
                    const uint32x4_t am = vdupq_n_u32(0xFF000000u);
                    for (; i + 4 <= n; i += 4) {
                        uint8x16_t v = vreinterpretq_u8_u32(vld1q_u32(p + i));
                        if (vg < 256) {
                            uint8x8_t lo = vshrn_n_u16(vmull_u8(vget_low_u8(v), f8), 8), hi = vshrn_n_u16(vmull_u8(vget_high_u8(v), f8), 8);
                            v = vreinterpretq_u8_u32(vorrq_u32(vreinterpretq_u32_u8(vcombine_u8(lo, hi)), am));
                        }
                        v = vqaddq_u8(v, vreinterpretq_u8_u32(vld1q_u32(add + i)));
                        vst1q_u32(p + i, vreinterpretq_u32_u8(v));
                    }
                }
#elif defined(__SSE2__)
                {
                    const __m128i z = _mm_setzero_si128(), f = _mm_set1_epi16((short)vg), am = _mm_set1_epi32((int)0xFF000000u);
                    for (; i + 4 <= n; i += 4) {
                        __m128i v = _mm_loadu_si128((const __m128i *)(p + i));
                        if (vg < 256) {
                            __m128i lo = _mm_srli_epi16(_mm_mullo_epi16(_mm_unpacklo_epi8(v, z), f), 8);
                            __m128i hi = _mm_srli_epi16(_mm_mullo_epi16(_mm_unpackhi_epi8(v, z), f), 8);
                            v = _mm_or_si128(_mm_packus_epi16(lo, hi), am);
                        }
                        v = _mm_adds_epu8(v, _mm_loadu_si128((const __m128i *)(add + i)));
                        _mm_storeu_si128((__m128i *)(p + i), v);
                    }
                }
#endif
                for (; i < n; i++) {
                    uint32_t v = p[i];
                    if (vg < 256)
                        v = ((((v & 0xFF00FFu) * (uint32_t)vg) >> 8) & 0xFF00FFu) | ((((v & 0x00FF00u) * (uint32_t)vg) >> 8) & 0x00FF00u);
                    p[i] = fx_add(v, (int)((add[i] >> 16) & 255), (int)((add[i] >> 8) & 255), (int)(add[i] & 255));
                }
            }
        }
    }
    /* --- bui sang bay (vi tri tinh theo thoi gian, khong can trang thai) */
    if (gfx_fx_bui > 0) {
        int np = 26;
        double t = (double)(now % 3600000) / 1000.0;
        for (int i = 0; i < np; i++) {
            unsigned hsh = (unsigned)i * 2654435761u;
            double fx = (hsh & 1023) / 1023.0, fy = ((hsh >> 10) & 1023) / 1023.0, fz = ((hsh >> 20) & 1023) / 1023.0;
            double sp = 5.0 + fz * 9.0;                         /* diem logic / giay */
            double lx = fx * (rw / k) + sin(t * (0.25 + fz * 0.35) + fx * 40.0) * (8.0 + fy * 10.0) + t * (1.5 + fz * 2.0);
            double ly = fy * (rh / k) - t * sp;
            double mw = rw / (double)k + 24, mh = rh / (double)k + 24;
            lx = fmod(lx, mw);
            if (lx < 0) lx += mw;
            ly = fmod(ly, mh);
            if (ly < 0) ly += mh;
            int cx = x0 + (int)((lx - 12) * k), cy = y0 + (int)((ly - 12) * k);
            double tw = 0.55 + 0.45 * sin(t * (0.9 + fx * 1.3) + fy * 30.0);    /* lap lanh */
            int rad = (int)((1.1 + fz * 1.5) * k);
            int amp = (int)((38 + fz * 50) * tw * gfx_fx_bui / 100);
            fx_dot(fb, W, H, x0, y0, x1, y1, cx, cy, rad, amp);
        }
    }
}
