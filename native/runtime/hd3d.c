/* hd3d.c - hieu ung "3D" cua may HD (nam trong gfx.c, sau hdkho.c).
 *
 * 1) Khoi noi (hd_post, luong phu, luc phong to anh): moi vat the / nhan vat duoc danh sang theo hinh khoi - vien phia
 *    trai-tren sang len, phia phai-duoi toi di (do cao tinh tu khoang cach toi mep hinh) -> hinh phang thanh hinh co khoi.
 * 2) Bong do (hd_shadow_make + hdbong.c sh_cast): moi vat the do bong theo huong nang xuong mat dat (mat na 8 bit tinh
 *    san luc phong to, chi phan lo ra ngoai hinh) -> cac lop tach nhau co chieu sau.
 * 3) Hau ky canh (gfx_hd_scene, sau khi ve xong the gioi, truoc HUD): anh sang loa (bloom) quanh vung sang, tia nang
 *    chuyen dong, toi goc (vignette), bui sang bay.
 * Chi chay o ban "Avatar 250 HD" (gfx_hdk > 1) va chi voi anh ve trong luc ve the gioi (Graphics.hdCanh).
 */
#include <math.h>

int gfx_world;                      /* dang ve the gioi (giua hdCanh(1) va hdCanh(2)) */

/* chu de (hdtheme.c, gop sau tep nay) */
extern int gfx_theme;
static void th_ground(uint32_t *px, int w, int h, int th);
static void th_snowcap(uint32_t *px, int w, int h, int k);
static void th_autumn(uint32_t *px, int w, int h);
static void hd_bump(uint32_t *px, int w, int h, int k, int manh, int luoi);
static int fx_dem_cu;
static void th_draw(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int k, int dem);
static void th_life(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int k, int tx);
/* co cua canh dang ve (client HdUi bao moi khung qua Graphics.setStrokeStyle(0x48A0 + co)): 1 = ngoai troi (co nang / trang:
 * tia nang, quang sang, bong may, nuoc lap lanh), 2 = canh mat dat co su song (buom, la roi, suong xa), 4 = ban dem */
int gfx_canh_co;

/* ------------------------------------------------------------ 1) khoi noi */
static void hd_relief(uint32_t *px, int w, int h, int k) {
    size_t n = (size_t)w * h;
    if (w < 5 * k || h < 5 * k || n > 5000000) return;
    int R = 5 * k;                              /* be rong vung bo tron mep (diem anh that) */
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
    uint8_t lut[3 * 5 * 4 + 1];
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
                t = t * 108 / 255 * lf / 256;               /* toi da ~42% ve phia trang */
                r += (255 - r) * t / 256;
                g += (255 - g) * t / 256;
                b += (255 - b) * t / 256;
            } else {
                int t = -e * 2;
                if (t > 255) t = 255;
                t = 256 - t * 100 / 255;                    /* toi da ~-39% */
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

/* mode: bit 0 khoi noi, bit 2-4 chu de, bit 5 (32) = anh nen (o gach / phong nen), bit 6 (64) = vat the tinh cua ban do,
 * bit 7 (128) = van noi */
static void hd_post(uint32_t *px, int w, int h, int k, int mode) {
    int th = (mode >> 2) & 7;
    if (mode & 32) {
        if (th) th_ground(px, w, h, th);
        /* bang o 24 x 24 (rong, cao chia het cho 24 diem logic): khong tinh van noi qua ranh o */
        if (mode & 128) hd_bump(px, w, h, k, 64, (w % (24 * k) == 0 && h % (24 * k) == 0) ? 24 * k : 0);
        return;
    }
    if ((mode & 64) && th == 4) th_autumn(px, w, h);           /* vat the ban do: tan cay xanh -> la thu vang cam */
    if ((mode & 128) && hd_is_sprite(px, w, h)) hd_bump(px, w, h, k, 78, 0);
    if ((mode & 1) && hd_is_sprite(px, w, h)) {
        hd_relief(px, w, h, k);
        if (th == 1 && (mode & 64)) th_snowcap(px, w, h, k);    /* tuyet dong chi tren vat the ban do (khong len dau nguoi) */
    }
}

/* ------------------------------------------------------------ 2) bong do theo huong nang: MAT NA tinh san cho moi anh
 * (luong nen lam 1 lan). Bong cua vat tren mat dat = hinh dang cua vat ep det theo chieu doc con 1/3 (roi xo nghieng luc
 * ve - hdbong.c sh_cast). Mat na: rong w, cao mh = ceil(h / 3) hang; hang r (0 = sat day anh) = do phu trung binh cua 3
 * hang anh [h - 3(r+1), h - 3r), lam nhoe ngang (mep bong mem). Sau mh * w byte la bang mh cap uint16 (x dau, x cuoi + 1)
 * cua doan co bong tren moi hang (hang trong: 0, 0) de luc ve bo qua nhanh phan trong suot.
 * *omg = mh. Dung luong: SH_BYTES(w, mh). */
#define SH_OFF(w, mh) ((((size_t)(w) * (size_t)(mh)) + 1) & ~(size_t)1)       /* bang doan bat dau o dia chi chan */
#define SH_BYTES(w, mh) (SH_OFF(w, mh) + (size_t)(mh) * 4)
static uint8_t *hd_shadow_make(const uint32_t *px, int w, int h, int k, int *omg) {
    *omg = 0;
    if (w < 2 * k || h < 2 * k || w > 2400 || h > 2400) return 0;
    int mh = (h + 2) / 3;
    uint8_t *m = (uint8_t *)calloc(SH_BYTES(w, mh), 1);
    uint16_t *acc = (uint16_t *)malloc((size_t)(w + 1) * sizeof(uint16_t));
    uint8_t *tam = (uint8_t *)malloc((size_t)w);
    if (!m || !acc || !tam) {
        free(m);
        free(acc);
        free(tam);
        return 0;
    }
    uint16_t *sp = (uint16_t *)(void *)(m + SH_OFF(w, mh));
    int R = k + 1;                          /* ban kinh nhoe ngang (2 luot hop -> mep mem ~2(k+1) diem anh) */
    int co = 0;
    for (int r = 0; r < mh; r++) {
        uint8_t *row = m + (size_t)r * w;
        int ya = h - 3 * (r + 1), yb = h - 3 * r;
        if (ya < 0) ya = 0;
        int any = 0;
        for (int y = ya; y < yb; y++) {
            const uint32_t *p = px + (size_t)y * w;
            for (int x = 0; x < w; x++)
                if ((p[x] >> 24) >= 128) {
                    row[x] += 85;
                    any = 1;
                }
        }
        if (!any) continue;
        co = 1;
        for (int luot = 0; luot < 2; luot++) {
            acc[0] = 0;
            for (int x = 0; x < w; x++) acc[x + 1] = (uint16_t)(acc[x] + row[x]);       /* tong don (tran vong 16 bit: hieu van dung) */
            for (int x = 0; x < w; x++) {
                int a = x - R < 0 ? 0 : x - R, b = x + R + 1 > w ? w : x + R + 1;
                tam[x] = (uint8_t)((uint16_t)(acc[b] - acc[a]) / (2 * R + 1));
            }
            memcpy(row, tam, (size_t)w);
        }
        int xa = 0, xb = w;
        while (xa < w && row[xa] < 6) xa++;
        while (xb > xa && row[xb - 1] < 6) xb--;
        sp[r * 2] = (uint16_t)xa;
        sp[r * 2 + 1] = (uint16_t)xb;
    }
    free(acc);
    free(tam);
    if (!co) {
        free(m);
        return 0;
    }
    *omg = mh;
    return m;
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

/* cham sang mem (bui sang / dom dom): cong mau (mr, mg, mb) vao dich */
static void fx_dot_mau(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int cx, int cy, int rad, int amp, int mr,
                       int mg, int mb) {
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
            *p = fx_add(*p, a * mr >> 8, a * mg >> 8, a * mb >> 8);
        }
    }
}

/* ngay hay dem? (game doi nen troi theo gio): do sang trung binh cua dai tren cung. 0 ngay, 1 dem (khong tia nang, bui
 * sang thanh dom dom), 2 den kit = trong nha (phong nho giua man den: khong tia nang, khong hat) */
static int fx_dem(const uint32_t *fb, int W, int x0, int y0, int x1, int y1, int k) {
    long tong = 0;
    int n = 0;
    int yh = y0 + (y1 - y0) / 7;
    for (int y = y0 + 2 * k; y < yh; y += 6 * k)
        for (int x = x0 + 3 * k; x < x1; x += 9 * k) {
            uint32_t c = fb[(size_t)y * W + x];
            tong += (((c >> 16) & 255) * 77 + ((c >> 8) & 255) * 150 + (c & 255) * 29) >> 8;
            n++;
        }
    if (n <= 0) return 0;
    return tong / n < 14 ? 2 : tong / n < 96 ? 1 : 0;
}

/* nhieu gia tri 2 chieu (0..1), noi suy tron */
static float fx_vn(float x, float y) {
    int xi = (int)floorf(x), yi = (int)floorf(y);
    float fx = x - (float)xi, fy = y - (float)yi;
    fx = fx * fx * (3.0f - 2.0f * fx);
    fy = fy * fy * (3.0f - 2.0f * fy);
    float v[4];
    for (int i = 0; i < 4; i++) {
        unsigned hh = (((unsigned)(xi + (i & 1)) * 73856093u) ^ ((unsigned)(yi + (i >> 1)) * 19349663u)) * 2654435761u;
        v[i] = (float)((hh >> 16) & 1023u) / 1023.0f;
    }
    return v[0] + (v[1] - v[0]) * fx + (v[2] - v[0]) * fy + (v[0] - v[1] - v[2] + v[3]) * fx * fy;
}

/* le trong 2 ben cua ban do hep hon man hinh (diem logic; client bao moi khung qua setStrokeStyle 0x4Dxxxxxx): truoc khi
 * hau ky, lat guong phan mep ban do ra le -> canh kin man hinh, khong con 2 dai den */
int gfx_le_trai, gfx_le_phai;

static void fx_lat_le(uint32_t *fb, int W, int H, int y0, int y1, int k) {
    int L = gfx_le_trai * k, R = gfx_le_phai * k;
    if (y0 < 0) y0 = 0;
    if (y1 > H) y1 = H;
    if (L > 0 && L * 2 <= W)
        for (int y = y0; y < y1; y++) {
            uint32_t *row = fb + (size_t)y * W;
            for (int i = 0; i < L; i++) row[L - 1 - i] = row[L + i];
        }
    if (R > 0 && R * 2 <= W)
        for (int y = y0; y < y1; y++) {
            uint32_t *row = fb + (size_t)y * W;
            for (int i = 0; i < R; i++) row[W - R + i] = row[W - R - 1 - i];
        }
}

/* nguon sang ban dem cua khung dang ve: client (HdUi / HdVat) bao qua Graphics.setStrokeStyle(0x4Cxxxxxx) luc ve cot den,
 * den long, nen...; hau ky canh ve quang sang am quanh den + vung sang tren mat dat. Toa do: diem logic tren man hinh
 * (chan cua vat). kieu 1 = cot den cao (bong den cach chan ~38 diem), 2 = den nho / nen sat mat dat, 3 = den treo cao vua */
#define DEN_MAX 64
static struct {
    short x, y;
    uint8_t kieu;
} fx_den[DEN_MAX];
static int fx_den_n;

/* cong 1 quang sang elip (tam cx, cy; ban kinh rx, ry - don vi O) vao bo dem loa: cuong do giam dan tu tam, mau (r, g, b) */
static void fx_quang(uint16_t *bl, int bw, int bh, float cx, float cy, float rx, float ry, int r, int g, int b) {
    int xa = (int)floorf(cx - rx), xb = (int)ceilf(cx + rx), ya = (int)floorf(cy - ry), yb = (int)ceilf(cy + ry);
    if (xa < 0) xa = 0;
    if (ya < 0) ya = 0;
    if (xb > bw - 1) xb = bw - 1;
    if (yb > bh - 1) yb = bh - 1;
    for (int y = ya; y <= yb; y++) {
        float dy = ((float)y - cy) / ry;
        uint16_t *d = bl + ((size_t)y * bw + xa) * 3;
        for (int x = xa; x <= xb; x++, d += 3) {
            float dx = ((float)x - cx) / rx;
            float q = 1.0f - (dx * dx + dy * dy);
            if (q <= 0) continue;
            q = q * q;
            int v0 = d[0] + (int)(q * r), v1 = d[1] + (int)(q * g), v2 = d[2] + (int)(q * b);
            d[0] = (uint16_t)(v0 > 600 ? 600 : v0);
            d[1] = (uint16_t)(v1 > 600 ? 600 : v1);
            d[2] = (uint16_t)(v2 > 600 ? 600 : v2);
        }
    }
}

#define FXC 8                       /* luoi tho cua bong may: moi nut 8 o (64 diem anh that) */
static int32_t *fx_may;             /* do toi cua bong may tai nut luoi tho (0 .. 38 * 256) */
static int fx_mw, fx_mh;
static uint8_t *fx_nuoc;            /* o la mat nuoc (1) */
static int fx_nn;
static uint8_t *fx_lum;             /* do sang trung binh cua o (ban dem: o sang khong bi lam toi) */
static int fx_ln;

/* fb: bo dem W x H (diem anh that), vung [x0,x1) x [y0,y1), k = diem anh that / diem logic; (tx, ty) = do doi cua the gioi
 * tren man hinh (diem logic) de hieu ung bam theo ban do khi cuon */
static void gfx_hd_scene(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int k, int tx, int ty) {
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    if (x1 > W) x1 = W;
    if (y1 > H) y1 = H;
    int rw = x1 - x0, rh = y1 - y0;
#if defined(__linux__) && !defined(__ANDROID__)
    {
        static int dem;
        if (getenv("J2N_BONGLOG") && (dem++ % 90) == 0)
            fprintf(stderr, "[canh] vung %d..%d x %d..%d (W %d H %d k %d) co %d tx %d ty %d\n", x0, x1, y0, y1, W, H, k, gfx_canh_co, tx, ty);
    }
#endif
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
    int ngoai = gfx_canh_co & 1, song = gfx_canh_co & 2;
    int dem = ngoai ? ((gfx_canh_co & 4) ? 1 : 0) : fx_dem(fb, W, x0, y0, x1, y1, k);
    fx_dem_cu = dem;
    int nguong = 218;                           /* nguong sang de loa */
    /* BAN DEM ngoai troi: ca canh toi di + nga xanh, cot den toa vung sang am. O SANG (bong thoai trang, bong den, bang hieu)
     * thi khong lam toi - he so toi tinh theo do sang trung binh cua o (fx_lum) - nen chu trong bong thoai van ro net */
    int toi_dem = (ngoai && dem) ? (song ? 186 : 226) : 256;
    if (toi_dem < 256) {
        if (fx_ln < bw * bh) {
            free(fx_lum);
            fx_lum = (uint8_t *)malloc((size_t)bw * bh);
            fx_ln = fx_lum ? bw * bh : 0;
        }
        if (!fx_lum || gfx_fx_bloom <= 0) toi_dem = 256;
    }
    double tt = (double)(now % 6000000) / 1000.0;
    /* --- bong may troi tren mat dat (ngoai troi, ban ngay): nhieu tan so thap tinh o luoi tho roi noi suy theo o */
    int co_may = ngoai && !dem && gfx_fx_ray > 0;
    int mw = bw / FXC + 2, mh = bh / FXC + 2;
    if (co_may) {
        if (mw != fx_mw || mh != fx_mh) {
            free(fx_may);
            fx_may = (int32_t *)malloc((size_t)mw * mh * sizeof(int32_t));
            fx_mw = mw;
            fx_mh = mh;
        }
        if (!fx_may) co_may = 0;
    }
    if (co_may) {
        float cu = (float)(tt * 0.016), cv = (float)(tt * 0.006);
        for (int gy = 0; gy < mh; gy++)
            for (int gx = 0; gx < mw; gx++) {
                float wx = (float)(x0 + gx * FXC * FXB) / k - (float)tx, wy = (float)(y0 + gy * FXC * FXB) / k - (float)ty;
                float u = wx / 170.0f + cu, v = wy / 105.0f + cv;
                float n = 0.65f * fx_vn(u, v) + 0.35f * fx_vn(u * 2.3f + 7.0f, v * 2.3f + 3.0f);
                float q = (n - 0.46f) / 0.28f;
                q = q < 0 ? 0 : q > 1 ? 1 : q;
                q = q * q * (3.0f - 2.0f * q);
                fx_may[gy * mw + gx] = (int32_t)(q * 38.0f * 256.0f * gfx_fx_ray / 100);
            }
    }
    /* --- mat nuoc (ngoai troi): danh dau o co mau nuoc de them anh lap lanh */
    int co_nuoc = ngoai && gfx_fx_bloom > 0;
    if (co_nuoc) {
        if (fx_nn < bw * bh) {
            free(fx_nuoc);
            fx_nuoc = (uint8_t *)malloc((size_t)bw * bh);
            fx_nn = fx_nuoc ? bw * bh : 0;
        }
        if (!fx_nuoc) co_nuoc = 0;
    }
    int thay_nuoc = 0;
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
                if (co_nuoc) {
                    /* nuoc dam (bien, ho tren ban do thanh pho) / nuoc nhat (song o khu sinh thai); troi nhat hon nen khong dinh */
                    int nc = (b > 185 && b - r > 95 && r < 140 && g < 195 && g > r)
                             || (b > 215 && g > 200 && r > 130 && r < 190 && b - r > 52);
                    fx_nuoc[(size_t)by * bw + bx] = (uint8_t)nc;
                    thay_nuoc |= nc;
                }
                int key = (r * 77 + g * 150 + b * 29) >> 8;     /* do sang (troi xanh / co khong tinh, trang va vang moi loa) */
                if (toi_dem < 256) fx_lum[(size_t)by * bw + bx] = (uint8_t)key;
                if (key <= nguong) {
                    d[0] = d[1] = d[2] = 0;
                } else {
                    int wgt = (key - nguong) * 256 / (255 - nguong);       /* 0..256 */
                    wgt = wgt * wgt >> 8;
                    wgt = wgt * gfx_fx_bloom / 100 * (dem ? 140 : 126) / 256;      /* cuong do loa (cong bao hoa) */
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
        if (co_nuoc && thay_nuoc) {
            /* NUOC LAP LANH: nhung vet sang nho nam ngang nhap nhay ngau nhien tren mat nuoc (moi o luoi 4 x 2 diem logic cua
             * ban do co 10% la "diem lap lanh", sang / tat theo nhip rieng), tu lai thanh dai theo song cham. Chi o nam trong
             * vung nuoc rong (4 o ke cung la nuoc). Cong vao bo dem loa SAU khi lam nhoe -> noi suy trong o, khong bi vuong. */
            float tn = (float)fmod(tt, 3600.0);
            int manh = dem ? 70 : 150, co = 0;
            for (int by = 1; by < bh - 1; by++) {
                const uint8_t *nr = fx_nuoc + (size_t)by * bw;
                int wy = (y0 + by * FXB) / k - ty;
                int cy = wy >> 1;
                for (int bx = 1; bx < bw - 1; bx++) {
                    if (!nr[bx] || !nr[bx - 1] || !nr[bx + 1] || !nr[bx - bw] || !nr[bx + bw]) continue;
                    int wx = (x0 + bx * FXB) / k - tx;
                    int cx = wx >> 2;
                    unsigned hh = (((unsigned)cx * 73856093u) ^ ((unsigned)cy * 19349663u)) * 2654435761u;
                    hh ^= hh >> 15;
                    if ((hh & 1023u) > 102u) continue;
                    float pha = (float)((hh >> 10) & 1023u) * (6.2831853f / 1023.0f);
                    float nhip = 2.0f + (float)((hh >> 20) & 255u) * (2.6f / 255.0f);
                    float sv = sinf(tn * nhip + pha);
                    if (sv <= 0.5f) continue;
                    float q = (sv - 0.5f) * 2.0f;
                    float dai = 0.55f + 0.45f * sinf((float)wx * 0.045f + (float)wy * 0.10f - tn * 0.5f);
                    int am = (int)(q * q * dai * manh);
                    uint16_t *d = fx_bl + ((size_t)by * bw + bx) * 3;
                    d[0] = (uint16_t)(d[0] + am * 215 / 256);
                    d[1] = (uint16_t)(d[1] + am * 240 / 256);
                    d[2] = (uint16_t)(d[2] + am);
                    co = 1;
                }
            }
            if (co) do_bloom = 1;
        }
        if (ngoai && dem && fx_den_n > 0) {
            /* DEN BAN DEM: quang sang quanh bong den + vung sang elip tren mat dat duoi chan den (nhap nhay rat nhe) */
            float sk = (float)k / FXB;                  /* diem logic -> o */
            for (int i = 0; i < fx_den_n; i++) {
                float lx = ((float)fx_den[i].x * k - x0) / FXB, ly = ((float)fx_den[i].y * k - y0) / FXB;
                float nh = 0.93f + 0.07f * sinf((float)tt * (2.1f + (i % 5) * 0.37f) + i * 1.7f);
                int kieu = fx_den[i].kieu;
                if (kieu == 1) {
                    fx_quang(fx_bl, bw, bh, lx, ly - 38.0f * sk, 24.0f * sk, 24.0f * sk, (int)(112 * nh), (int)(88 * nh), (int)(42 * nh));
                    fx_quang(fx_bl, bw, bh, lx, ly + 4.0f * sk, 82.0f * sk, 34.0f * sk, (int)(118 * nh), (int)(96 * nh), (int)(52 * nh));
                } else if (kieu == 3) {
                    fx_quang(fx_bl, bw, bh, lx, ly - 22.0f * sk, 22.0f * sk, 22.0f * sk, (int)(120 * nh), (int)(90 * nh), (int)(42 * nh));
                    fx_quang(fx_bl, bw, bh, lx, ly + 3.0f * sk, 56.0f * sk, 24.0f * sk, (int)(90 * nh), (int)(72 * nh), (int)(38 * nh));
                } else {
                    fx_quang(fx_bl, bw, bh, lx, ly - 7.0f * sk, 20.0f * sk, 18.0f * sk, (int)(124 * nh), (int)(88 * nh), (int)(34 * nh));
                    fx_quang(fx_bl, bw, bh, lx, ly + 2.0f * sk, 40.0f * sk, 17.0f * sk, (int)(74 * nh), (int)(56 * nh), (int)(26 * nh));
                }
            }
            do_bloom = 1;
        }
    }
    /* --- tron vao khung: loa sang (noi suy trong o) + tia nang + toi goc. Moi o 8 x 8: tinh san 8 gia tri cong cua
     * 1 hang (buoc cong don, khong nhan chia), roi nhan he so toi goc + cong bao hoa bang lenh vector */
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
        /* suong xa: phan tren cua canh (xa nguoi nhin) hoi nhat / xanh hon -> co chieu sau */
        int hz = 0;
        if (ngoai && song && !dem && gfx_fx_ray > 0) {
            int lim = bh * 36 / 100;
            if (by < lim) {
                hz = (lim - by) * 256 / lim;
                hz = hz * hz * 15 >> 16;
            }
        }
        const int32_t *m0 = 0, *m1 = 0;
        int mfy = 0;
        if (co_may) {
            m0 = fx_may + (by / FXC) * mw;
            m1 = m0 + mw;
            mfy = by % FXC;
        }
        for (int bx = 0; bx < bw; bx++) {
            int px0 = x0 + bx * FXB, px1 = px0 + FXB < x1 ? px0 + FXB : x1;
            int bx1 = bx + 1 < bw ? bx + 1 : bx;
            int vg = (fx_vg && gfx_fx_vig > 0) ? 256 - (256 - fx_vg[(size_t)by * bw + bx]) * gfx_fx_vig / 100 : 256;
            if (toi_dem < 256) {
                int L = fx_lum[(size_t)by * bw + bx], f = toi_dem;
                if (L > 196) {
                    int tq = (L - 196) * 256 / 48;
                    f = toi_dem + ((256 - toi_dem) * (tq > 256 ? 256 : tq) >> 8);
                }
                vg = vg * f >> 8;
            }
            if (co_may) {
                int gx = bx / FXC, mfx = bx % FXC;
                int v0 = m0[gx] * (FXC - mfx) + m0[gx + 1] * mfx, v1 = m1[gx] * (FXC - mfx) + m1[gx + 1] * mfx;
                int mv = (v0 * (FXC - mfy) + v1 * mfy) >> 14;      /* / 64 (noi suy) / 256 */
                if (mv > 0) vg = vg * (256 - mv) >> 8;
            }
            int ray = 0;
            if (gfx_fx_ray > 0 && fade > 8 && !dem && ngoai) {
                /* pha u = (x + 0,55 y) / (46 diem logic) + troi theo thoi gian; mau lap lai sau 10 don vi -> bang 4096 o */
                unsigned iu = (unsigned)((bx * FXB + by * FXB * 0.55f) * ray_sc + ray_t) & 4095u;
                ray = fx_ray_lut[iu] * fade * gfx_fx_ray / 16000;
            }
            /* quang sang: ngay = nang am tu goc trai-tren (cung huong voi bong do), dem = anh trang lanh tu goc phai-tren */
            int qr = 0, qg = 0, qb = 0;
            if (ngoai && gfx_fx_ray > 0) {
                float qx = dem ? (float)(bw - bx) / bw : (float)bx / bw, qy = (float)by / bh;
                float r2 = qx * qx * 0.9f + qy * qy * 1.9f;
                if (r2 < 1.0f) {
                    float q = (1.0f - r2) * (1.0f - r2);
                    if (dem) {
                        qr = (int)(q * 16);
                        qg = (int)(q * 26);
                        qb = (int)(q * 46);
                    } else {
                        qr = (int)(q * 46);
                        qg = (int)(q * 34);
                        qb = (int)(q * 10);
                    }
                }
            }
            int ar0 = ray + qr + (hz * 220 >> 8), ag0 = (ray * 240 >> 8) + qg + (hz * 240 >> 8), ab0 = (ray * 200 >> 8) + qb + hz;
            if (toi_dem < 256) {
                ag0 += 3;               /* dem: nga xanh lam */
                ab0 += song ? 16 : 6;
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
            if (!lit && vg >= 256 && (ar0 | ag0 | ab0) == 0) continue;
            int n = px1 - px0;
            uint32_t add[FXB];
            uint32_t rayc = ((uint32_t)ar0 << 16) | ((uint32_t)ag0 << 8) | (uint32_t)ab0;
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
                        int R = (acc[0] >> 6) + ar0, G = (acc[1] >> 6) + ag0, B = (acc[2] >> 6) + ab0;
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
    if (gfx_fx_bui > 0 && dem != 2 && gfx_theme == 0) {
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
            if (dem == 1 && !ngoai) continue;           /* trong nha ban dem: khong co dom dom */
            if (dem == 1) {
                /* dom dom: nhap nhay manh hon, mau vang xanh */
                double nh = sin(t * (1.6 + fx * 2.1) + fz * 50.0);
                amp = nh > 0.1 ? (int)((60 + fz * 90) * nh * gfx_fx_bui / 100) : 0;
                fx_dot_mau(fb, W, H, x0, y0, x1, y1, cx, cy, rad + k / 2, amp, 190, 255, 110);
            } else {
                fx_dot_mau(fb, W, H, x0, y0, x1, y1, cx, cy, rad, amp, 255, 245, 215);
            }
        }
    }
    if (ngoai && song && !dem && gfx_theme == 0 && gfx_fx_bui > 0) th_life(fb, W, H, x0, y0, x1, y1, k, tx);
    th_draw(fb, W, H, x0, y0, x1, y1, k, dem);
#if defined(__linux__) && !defined(__ANDROID__)
    {
        /* thu nghiem: J2N_CANHDUMP=<tep> ghi khung ngay sau hau ky (moi ~2 giay) */
        static int dem2;
        const char *tep = getenv("J2N_CANHDUMP");
        if (tep && (dem2++ % 24) == 23) {
            FILE *f = fopen(tep, "wb");
            if (f) {
                fprintf(f, "P6\n%d %d\n255\n", W, H);
                for (int i = 0; i < W * H; i++) {
                    unsigned char c[3] = {(unsigned char)(fb[i] >> 16), (unsigned char)(fb[i] >> 8), (unsigned char)fb[i]};
                    fwrite(c, 1, 3, f);
                }
                fclose(f);
            }
        }
    }
#endif
}

/* ------------------------------------------------------------ 4) giao dien 3D (HdUi qua Graphics.setStrokeStyle 0x486x
 * roi fillRoundRect): bong do quanh khung, lop bong sang (kinh) phu len khung, nut kieu kinh toi.
 * Toa do: x, y, w, h, r tinh theo diem anh THAT cua dich (da cong tinh tien), cat theo vung cat cua dich. */
static inline float ui_sdf(float px, float py, float hw, float hh, float r) {
    float qx = fabsf(px) - (hw - r), qy = fabsf(py) - (hh - r);
    float ax = qx > 0 ? qx : 0, ay = qy > 0 ? qy : 0;
    float in = qx > qy ? qx : qy;
    return sqrtf(ax * ax + ay * ay) + (in < 0 ? in : 0) - r;
}

static inline uint32_t ui_mix(uint32_t d, int r, int g, int b, int a) {       /* tron mau (r,g,b) len d voi do duc a (0..255) */
    int R = (d >> 16) & 255, G = (d >> 8) & 255, B = d & 255;
    R += (r - R) * a / 255;
    G += (g - G) * a / 255;
    B += (b - B) * a / 255;
    return 0xFF000000u | ((uint32_t)R << 16) | ((uint32_t)G << 8) | (uint32_t)B;
}

static inline float ui_buoc(float a, float b, float x) {                       /* smoothstep */
    float t = (x - a) / (b - a);
    t = t < 0 ? 0 : t > 1 ? 1 : t;
    return t * t * (3.0f - 2.0f * t);
}

/* Cac kieu giao dien dot 4 (HdUi.kieu 5..10), toa do diem anh that:
 *  5 quang sang quanh nut (chi phan NGOAI hinh), mau color
 *  6 ban choi boc ni: bong do + vien go + mat ni mau color (sang o giua, toi dan ve vien)
 *  7 lam sau nen: toi dan ve 4 goc + sang nhe o giua (phu len nen hoa van cua cac man OnGame)
 *  8 vong ghe trong: vien mau color + long mo
 *  9 bang sang (hop thoai kieu moi): bong do, vien trang, than chuyen tu color (tren) sang nhat hon (duoi)
 * 10 dai mau phang bo goc (nen cho dong chu / the thong tin), color, do duc ~62%
 * 11 lam mo ca vung (sau hop thoai): phu mau color ~34% */
static void hd_ui2(tgt *o, int X, int Y, int Wd, int Hd, int R, int kieu, uint32_t color, int K) {
    int cx0 = o->x0, cy0 = o->y0, cx1 = o->x1, cy1 = o->y1;
    float hw = Wd * 0.5f, hh = Hd * 0.5f, mx = X + hw, my = Y + hh;
    int br = (color >> 16) & 255, bg = (color >> 8) & 255, bb = color & 255;
    if (kieu == 11) {
        /* lam mo nen sau hop thoai: phu mau color ~34% len ca vung (khong bo goc) */
        int y0 = Y < cy0 ? cy0 : Y, y1 = Y + Hd > cy1 ? cy1 : Y + Hd, x0 = X < cx0 ? cx0 : X, x1 = X + Wd > cx1 ? cx1 : X + Wd;
        for (int y = y0; y < y1; y++) {
            uint32_t *row = o->px + (size_t)y * o->w;
            for (int x = x0; x < x1; x++) {
                uint32_t d = row[x];
                int R0 = (d >> 16) & 255, G0 = (d >> 8) & 255, B0 = d & 255;
                R0 += (br - R0) * 87 >> 8;
                G0 += (bg - G0) * 87 >> 8;
                B0 += (bb - B0) * 87 >> 8;
                row[x] = 0xFF000000u | ((uint32_t)R0 << 16) | ((uint32_t)G0 << 8) | (uint32_t)B0;
            }
        }
        return;
    }
    if (kieu == 7) {
        int y0 = Y < cy0 ? cy0 : Y, y1 = Y + Hd > cy1 ? cy1 : Y + Hd, x0 = X < cx0 ? cx0 : X, x1 = X + Wd > cx1 ? cx1 : X + Wd;
        for (int y = y0; y < y1; y++) {
            uint32_t *row = o->px + (size_t)y * o->w;
            float ny = (y + 0.5f - my) / hh;
            float ny2 = ny * ny;
            for (int x = x0; x < x1; x++) {
                float nx = (x + 0.5f - mx) / hw;
                float r2 = nx * nx * 0.82f + ny2;                  /* elip hoi det ngang */
                if (r2 < 0.30f) {
                    int a = (int)(30 * (1.0f - r2 / 0.30f));
                    if (a > 0) row[x] = ui_mix(row[x], 255, 255, 255, a);
                } else if (r2 > 0.42f) {
                    float t = (r2 - 0.42f) / 1.25f;
                    if (t > 1) t = 1;
                    int a = (int)(150 * t * (2.0f - t));
                    if (a > 0) row[x] = ui_mix(row[x], 0, 10, 12, a);
                }
            }
        }
        return;
    }
    int M = (kieu == 6 ? 10 : kieu == 9 ? 9 : kieu == 5 ? 7 : 0) * K;       /* tam bong / quang ben ngoai */
    int oy = (kieu == 6 ? 4 : kieu == 9 ? 3 : 0) * K;
    int y0 = Y - M - 1, y1 = Y + Hd + M + oy + 1, x0 = X - M - 1, x1 = X + Wd + M + 1;
    if (y0 < cy0) y0 = cy0;
    if (y1 > cy1) y1 = cy1;
    if (x0 < cx0) x0 = cx0;
    if (x1 > cx1) x1 = cx1;
    float vien = (kieu == 6 ? 5.0f : 1.6f) * K;                             /* do day vien go / vien trang */
    for (int y = y0; y < y1; y++) {
        uint32_t *row = o->px + (size_t)y * o->w;
        float py = y + 0.5f - my;
        float ty = (float)(y - Y) / (float)Hd;
        ty = ty < 0 ? 0 : ty > 1 ? 1 : ty;
        for (int x = x0; x < x1; x++) {
            float px = x + 0.5f - mx;
            float d = ui_sdf(px, py, hw, hh, (float)R);
            if (kieu == 5) {
                if (d <= 0 || d >= M) continue;
                float t = 1.0f - d / M;
                int a = (int)(170 * t * t);
                if (a > 0) row[x] = ui_mix(row[x], br + (255 - br) / 3, bg + (255 - bg) / 3, bb + (255 - bb) / 3, a);
                continue;
            }
            if (kieu == 8) {
                float e = fabsf(d + 1.0f * K) - 0.8f * K;                    /* duong vien nam ben trong mep 1K */
                if (e < 0.5f) {
                    float cov = 0.5f - e;
                    if (cov > 1) cov = 1;
                    row[x] = ui_mix(row[x], br, bg, bb, (int)(165 * cov));
                } else if (d < 0) {
                    row[x] = ui_mix(row[x], br, bg, bb, 26);
                }
                continue;
            }
            if (kieu == 10) {
                if (d >= 0.5f) continue;
                float cov = 0.5f - d;
                if (cov > 1) cov = 1;
                row[x] = ui_mix(row[x], br, bg, bb, (int)(158 * cov));
                continue;
            }
            /* 6 / 9: co bong do ben ngoai */
            if (d >= 0.5f) {
                float ds = ui_sdf(px, py - oy, hw, hh, (float)R);
                if (ds >= M) continue;
                float tq = ds <= 0 ? 1.0f : 1.0f - ds / M;
                int a = (int)((kieu == 6 ? 135 : 96) * tq * tq);
                if (a > 0) row[x] = ui_mix(row[x], 0, 8, 14, a);
                continue;
            }
            float cov = 0.5f - d;
            if (cov > 1) cov = 1;
            float sau = -d;
            int cr, cg, cb;
            if (kieu == 6) {
                if (sau < vien) {
                    /* vien go: sang phia tren, 2 duong ke dam o mep ngoai va mep trong */
                    float v = sau / vien;                               /* 0 mep ngoai .. 1 mep trong */
                    float sg = 1.0f - 0.45f * ty + 0.22f * sinf(v * 3.1416f);
                    cr = (int)(168 * sg);
                    cg = (int)(104 * sg);
                    cb = (int)(48 * sg);
                    if (sau < 0.9f * K || sau > vien - 0.9f * K) {
                        cr = cr * 120 >> 8;
                        cg = cg * 112 >> 8;
                        cb = cb * 104 >> 8;
                    }
                } else {
                    /* mat ni: sang o giua, toi dan ve vien + bong trong sat vien (phia tren dam hon) */
                    float nx = px / hw, ny = py / hh;
                    float r2 = nx * nx + ny * ny;
                    float sg = 1.16f - 0.34f * r2;
                    float trong = sau - vien;
                    float bt = trong < 7.0f * K ? 1.0f - trong / (7.0f * K) : 0;
                    sg *= 1.0f - bt * bt * (0.42f - 0.20f * ty);
                    /* van ni rat nhe */
                    unsigned hsh = ((unsigned)x * 73856093u) ^ ((unsigned)y * 19349663u);
                    sg += ((int)((hsh >> 9) & 15) - 7) * 0.0022f;
                    cr = (int)(br * sg);
                    cg = (int)(bg * sg);
                    cb = (int)(bb * sg);
                }
            } else {
                /* 9: bang sang */
                if (sau < vien) {
                    cr = 255;
                    cg = 255;
                    cb = 255;
                    if (sau > vien - 0.6f * K) {                        /* duong ke mong sat vien trang */
                        cr = br * 200 >> 8;
                        cg = bg * 214 >> 8;
                        cb = bb * 232 >> 8;
                    }
                } else {
                    float f = 1.0f - ty;                                /* tren dung mau color, duoi nhat dan ve trang */
                    cr = br + (int)((255 - br) * (0.50f - 0.50f * f));
                    cg = bg + (int)((255 - bg) * (0.50f - 0.50f * f));
                    cb = bb + (int)((255 - bb) * (0.50f - 0.50f * f));
                    if (ty < 0.16f) {                                   /* mang sang sat mep tren */
                        int sgw = (int)(70 * (1.0f - ty / 0.16f));
                        cr += (255 - cr) * sgw / 255;
                        cg += (255 - cg) * sgw / 255;
                        cb += (255 - cb) * sgw / 255;
                    }
                }
            }
            cr = cr < 0 ? 0 : cr > 255 ? 255 : cr;
            cg = cg < 0 ? 0 : cg > 255 ? 255 : cg;
            cb = cb < 0 ? 0 : cb > 255 ? 255 : cb;
            if (cov < 1.0f) {
                float ds = ui_sdf(px, py - oy, hw, hh, (float)R);
                if (ds < M) {
                    float tq = ds <= 0 ? 1.0f : 1.0f - ds / M;
                    row[x] = ui_mix(row[x], 0, 8, 14, (int)((kieu == 6 ? 135 : 96) * tq * tq));
                }
            }
            row[x] = ui_mix(row[x], cr, cg, cb, (int)(255 * cov));
        }
    }
}

static void hd_ui(tgt *o, int X, int Y, int Wd, int Hd, int R, int kieu, uint32_t color) {
    int k = o->k;                                   /* dich da chuyen sang diem anh that -> k = 1; lay ti le tu gfx_hdk */
    (void)k;
    int K = gfx_hdk > 1 ? gfx_hdk : 1;
    int cx0 = o->x0, cy0 = o->y0, cx1 = o->x1, cy1 = o->y1;
    if (R > Wd / 2) R = Wd / 2;
    if (R > Hd / 2) R = Hd / 2;
    float hw = Wd * 0.5f, hh = Hd * 0.5f, mx = X + hw, my = Y + hh;
    if (kieu >= 5 && kieu <= 11) {
        hd_ui2(o, X, Y, Wd, Hd, R, kieu, color, K);
        return;
    }
    if (kieu == 4) {
        /* NUT MAU (giao dien dot 3): bong mo ben duoi, vien trang, than chuyen mau tu sang (tren) xuong dam (duoi), mang sang
         * nua tren, 1 duong vien dam mong sat vien trang. color = mau than. Mep khu rang cua bang khoang cach co dau. */
        float bo = 1.5f * K, dam = 0.7f * K;
        int M = 3 * K, oy = (int)(1.5f * K);
        int br = (color >> 16) & 255, bg = (color >> 8) & 255, bb = color & 255;
        int y0 = Y - 1, y1 = Y + Hd + M + oy + 1, x0 = X - M - 1, x1 = X + Wd + M + 1;
        if (y0 < cy0) y0 = cy0;
        if (y1 > cy1) y1 = cy1;
        if (x0 < cx0) x0 = cx0;
        if (x1 > cx1) x1 = cx1;
        for (int y = y0; y < y1; y++) {
            uint32_t *row = o->px + (size_t)y * o->w;
            float py = y + 0.5f - my;
            float ty = (float)(y - Y) / (float)Hd;
            ty = ty < 0 ? 0 : ty > 1 ? 1 : ty;
            /* mau than o hang nay */
            int f = (int)(52 - 104 * ty);
            int r = br + f, g = bg + f, b = bb + f;
            if (ty < 0.5f) {
                int sg = (int)(34 * (1.0f - ty / 0.5f));            /* mang sang nua tren */
                r += (255 - r) * sg / 255;
                g += (255 - g) * sg / 255;
                b += (255 - b) * sg / 255;
            }
            r = r < 0 ? 0 : r > 255 ? 255 : r;
            g = g < 0 ? 0 : g > 255 ? 255 : g;
            b = b < 0 ? 0 : b > 255 ? 255 : b;
            for (int x = x0; x < x1; x++) {
                float px = x + 0.5f - mx;
                float d = ui_sdf(px, py, hw, hh, (float)R);
                if (d < 0.5f) {
                    float cov = 0.5f - d;                           /* do phu cua hinh nut tai diem nay */
                    if (cov > 1.0f) cov = 1.0f;
                    float sau = -d;                                 /* do sau tinh tu mep */
                    int cr, cg, cb;
                    if (sau < bo - 0.5f) {
                        int xam = 255 - (int)(22 * ty);             /* vien trang, duoi hoi xam */
                        cr = xam;
                        cg = xam;
                        cb = 255;
                    } else {
                        cr = r;
                        cg = g;
                        cb = b;
                        if (sau < bo + dam) {                       /* duong vien dam sat vien trang */
                            cr = cr * 170 >> 8;
                            cg = cg * 180 >> 8;
                            cb = cb * 205 >> 8;
                        }
                        if (sau < bo + 0.5f) {                      /* chuyen mem tu vien trang vao than */
                            float w = sau - (bo - 0.5f);
                            int xam = 255 - (int)(22 * ty);
                            cr = (int)(xam + (cr - xam) * w);
                            cg = (int)(xam + (cg - xam) * w);
                            cb = (int)(255 + (cb - 255) * w);
                        }
                    }
                    if (cov < 1.0f) {
                        /* mep ngoai: bong ben duoi truoc, roi phu mau nut theo do phu */
                        float ds = ui_sdf(px, py - oy, hw, hh, (float)R);
                        if (ds < M) {
                            float tq = ds <= 0 ? 1.0f : 1.0f - ds / M;
                            row[x] = ui_mix(row[x], 6, 22, 48, (int)(104 * tq * tq));
                        }
                    }
                    row[x] = ui_mix(row[x], cr, cg, cb, (int)(255 * cov));
                    continue;
                }
                float ds = ui_sdf(px, py - oy, hw, hh, (float)R);
                if (ds >= M) continue;
                float tq = ds <= 0 ? 1.0f : 1.0f - ds / M;
                int a = (int)(104 * tq * tq);
                if (a > 0) row[x] = ui_mix(row[x], 6, 22, 48, a);
            }
        }
        return;
    }
    if (kieu == 1 || kieu == 3) {
        /* bong do mem quanh khung (lech xuong) - chi phan nam ngoai khung */
        int M = (kieu == 1 ? 8 : 4) * K, oy = (kieu == 1 ? 3 : 2) * K, amax = kieu == 1 ? 120 : 110;
        int y0 = Y - M + oy, y1 = Y + Hd + M + oy, x0 = X - M, x1 = X + Wd + M;
        if (y0 < cy0) y0 = cy0;
        if (y1 > cy1) y1 = cy1;
        if (x0 < cx0) x0 = cx0;
        if (x1 > cx1) x1 = cx1;
        for (int y = y0; y < y1; y++) {
            uint32_t *row = o->px + (size_t)y * o->w;
            float py = y + 0.5f - my;
            for (int x = x0; x < x1; x++) {
                float px = x + 0.5f - mx;
                if (ui_sdf(px, py, hw, hh, (float)R) < -1.0f) {
                    /* ben trong khung: nhay qua phan giua */
                    int xr = (int)(mx + (mx - x)) - 1;
                    if (py > -hh + R && py < hh - R && xr > x && xr < x1) x = xr;
                    continue;
                }
                float d = ui_sdf(px, py - oy, hw, hh, (float)R);
                if (d >= M) continue;
                float t = d <= 0 ? 1.0f : 1.0f - d / M;
                int a = (int)(amax * t * t);
                if (a > 0) row[x] = ui_mix(row[x], 0, 0, 0, a);
            }
        }
        if (kieu == 1) return;
    }
    if (kieu == 2 || kieu == 3) {
        /* kieu 3: than nut kinh toi (mau color, hoi trong) truoc; roi lop bong sang chung */
        int y0 = Y < cy0 ? cy0 : Y, y1 = Y + Hd > cy1 ? cy1 : Y + Hd;
        int br = (color >> 16) & 255, bg = (color >> 8) & 255, bb = color & 255;
        for (int y = y0; y < y1; y++) {
            float py = y + 0.5f - my;
            /* doan ngang ben trong o hang nay (goc tron) */
            float inset = 0;
            float dyc = fabsf(py) - (hh - R);
            if (dyc > 0) {
                float q = (float)R * R - dyc * dyc;
                inset = R - (q > 0 ? sqrtf(q) : 0);
            }
            int xa = (int)(X + inset + 0.5f), xb = (int)(X + Wd - inset + 0.5f);
            if (xa < cx0) xa = cx0;
            if (xb > cx1) xb = cx1;
            if (xb <= xa) continue;
            uint32_t *row = o->px + (size_t)y * o->w;
            float ty = (float)(y - Y) / (float)Hd;                 /* 0 tren .. 1 duoi */
            if (kieu == 3) {
                /* than nut: tren sang hon, duoi toi hon */
                int f = (int)(40 - 70 * ty);
                int r = br + f, g = bg + f, b = bb + f;
                r = r < 0 ? 0 : r > 255 ? 255 : r;
                g = g < 0 ? 0 : g > 255 ? 255 : g;
                b = b < 0 ? 0 : b > 255 ? 255 : b;
                for (int x = xa; x < xb; x++) row[x] = ui_mix(row[x], r, g, b, 200);
            }
            /* bong sang: nua tren trang mo dan, 1/4 duoi toi dan */
            int aw = 0, ab = 0;
            float cao = kieu == 3 ? 0.5f : 0.42f;
            if (ty < cao) {
                float q = 1.0f - ty / cao;
                aw = (int)((kieu == 3 ? 78 : 44) * q * q);
            }
            if (ty > 0.72f) {
                float q = (ty - 0.72f) / 0.28f;
                ab = (int)((kieu == 3 ? 40 : 30) * q * q);
            }
            if (aw > 0)
                for (int x = xa; x < xb; x++) row[x] = ui_mix(row[x], 255, 255, 255, aw);
            if (ab > 0)
                for (int x = xa; x < xb; x++) row[x] = ui_mix(row[x], 0, 0, 0, ab);
            /* vien trong: mep trai / tren sang, mep phai / duoi toi (rong K diem) */
            int e = K < 2 ? 1 : K / 2 + 1;
            for (int x = xa; x < xa + e && x < xb; x++) row[x] = ui_mix(row[x], 255, 255, 255, 46);
            for (int x = xb - e; x < xb; x++)
                if (x >= xa) row[x] = ui_mix(row[x], 0, 0, 0, 40);
            if (y - Y < e)
                for (int x = xa; x < xb; x++) row[x] = ui_mix(row[x], 255, 255, 255, 60);
            if (Y + Hd - 1 - y < e)
                for (int x = xa; x < xb; x++) row[x] = ui_mix(row[x], 0, 0, 0, 50);
        }
    }
}
