/* hdbong.c - them chieu sau cho may HD (nam trong gfx.c, sau hdtheme.c):
 *  1) Bong do theo huong nang (sh_cast): moi vat the / mon do cua nhan vat do bong DAI xuong mat dat phia truoc - ben phai
 *     (nguon sang o sau - trai - tren, cung huong voi khoi noi). Bong = hinh dang cua chinh vat, ep det + xo nghieng, nhat
 *     dan ve phia ngon. Bang danh dau (stencil) moi khung giu cho cho bong chong nhau khong bi toi gap doi.
 *     Client (HdUi) bao "duong chan" cua vat qua Graphics.setStrokeStyle(0x4A00yyyy).
 *  2) Cay lay theo gio (draw_img_sway): vat the tinh co nhieu mau xanh la (cay, bui) duoc ve lech tung hang theo hinh sin,
 *     goc dung yen, ngon lay nhieu nhat.
 *  3) Van noi (hd_bump): moi net ve trong anh nen / vat the tinh thanh go noi (mep trai-tren sang, mep phai-duoi toi).
 *  4) Nen thay cho mau den tron khi game xoa man hinh (ban do / phong nho hon man hinh): chuyen mau xanh dem.
 */
static inline void row_put(uint32_t *d, const uint32_t *s, int n, int kind);
static inline void row_fill(uint32_t *d, uint32_t c, int n);

#define HDF_PLANT 1
#define HDF_GLOW 2
#define NO_FOOT ((int32_t)0x80000000)
#define SH_SQ 0.34f
#define SH_SHX 0.44f

static uint8_t *sh_st;
static int sh_stw, sh_sth;
static const uint32_t *sh_tgt;
static int sh_cx0, sh_cy0, sh_cx1, sh_cy1;
static int fx_dem_cu;               /* ngay / dem cua khung truoc (gfx_hd_scene ghi) */
int gfx_fx_bong = 100;              /* % do dam cua bong do (0 = tat) */

/* bat dau ve the gioi: xoa bang danh dau, nho dich + vung ve (diem anh that) */
static void sh_begin(tgt *o) {
    size_t n = (size_t)o->w * o->h;
    if (!sh_st || sh_stw != o->w || sh_sth != o->h) {
        free(sh_st);
        sh_st = (uint8_t *)malloc(n);
        sh_stw = o->w;
        sh_sth = o->h;
    }
    if (sh_st) memset(sh_st, 0, n);
    sh_tgt = o->px;
    sh_cx0 = o->x0 * o->k;
    sh_cy0 = o->y0 * o->k;
    sh_cx1 = o->x1 * o->k;
    sh_cy1 = o->y1 * o->k;
    if (sh_cx0 < 0) sh_cx0 = 0;
    if (sh_cy0 < 0) sh_cy0 = 0;
    if (sh_cx1 > o->w) sh_cx1 = o->w;
    if (sh_cy1 > o->h) sh_cy1 = o->h;
}

/* do bong cua vung anh (toa do logic xs, ys, w, h trong anh e; ve tai px, py tren dich) xuong mat dat duoi duong chan foot_l.
 * nho = manh cua nhan vat (dau, ao, quan... moi manh chi vai diem): khong ap nguong kich thuoc.
 * Dung mat na tinh san cua anh (e->shd: hinh dang ep det con 1/3 chieu cao, mep da lam mem): moi hang mat na -> 1 hang bong,
 * dich ngang (xo nghieng) + nhat dan theo do cao so voi chan. Bang danh dau giu muc toi DA ap o moi diem -> bong chong nhau
 * chi lay muc toi nhat, khong cong don. */
static void sh_cast(tgt *o, const hd_ent *e, int xs, int ys, int w, int h, int tr, int px, int py, int foot_l, int nho) {
    if (!sh_st || o->px != sh_tgt || o->w != sh_stw || gfx_fx_bong <= 0 || !e->shd) return;
    if (w > 460 || h > 460 || (tr != 0 && tr != 2)) return;
    if (nho ? (h < 2 || w < 2) : (h < 16 || w < 6)) return;
    int k = e->k;
#if defined(__linux__) && !defined(__ANDROID__)
    {
        static int lg = -1, dem;
        if (lg < 0) lg = getenv("J2N_BONGLOG") ? atoi(getenv("J2N_BONGLOG")) : 0;
        if (lg > 0 && (dem++ % lg) == 0)
            fprintf(stderr, "[bong] chan %d ve (%d,%d) %dx%d tr %d ty %d nho %d\n", foot_l, px, py, w, h, tr, o->ty, nho);
    }
#endif
    int FY = (foot_l + o->ty) * k;
    int DX = px * k, DY = py * k, PW = w * k, PH = h * k;
    int IH = e->h, IW = e->w, mh = e->shm;
    int X0 = xs * k, Y0 = ys * k;                       /* goc vung trong anh (diem anh that) */
    if (X0 < 0 || Y0 < 0 || X0 + PW > IW || Y0 + PH > IH) return;
    if (DY >= FY) return;                               /* ca vung nam duoi duong chan */
    {
        int day = DY + PH < FY ? DY + PH : FY;          /* mep duoi cua phan do bong */
        if (FY - day > (nho ? 90 : 40) * k) return;     /* vat lo lung cao tren mat dat (bong bay, chu noi...): khong do bong */
        if (DX + PW + (int)((FY - DY) * SH_SHX) + 4 * k < sh_cx0 || DX - 4 * k > sh_cx1) return;
    }
    int yeu = fx_dem_cu || !(gfx_canh_co & 1);          /* dem / trong nha: bong nhat hon */
    int a0 = (yeu ? 70 : 126) * gfx_fx_bong / 100, a1 = (yeu ? 26 : 52) * gfx_fx_bong / 100;
    int hmax = 64 * k;
    const uint8_t *m = e->shd;
    const uint16_t *sp = (const uint16_t *)(const void *)(m + SH_OFF(IW, mh));
    /* hang mat na r <-> hang anh giua yc = IH - 3r - 2; chi cac hang nam trong vung va tren duong chan */
    int r0 = (IH - (Y0 + PH)) / 3, r1 = (IH - Y0 + 2) / 3;
    if (r0 < 0) r0 = 0;
    if (r1 > mh) r1 = mh;
    for (int r = r0; r < r1; r++) {
        int yc = IH - 3 * r - 2;
        if (yc < Y0) yc = Y0;
        if (yc >= Y0 + PH) continue;
        int sy = DY + (yc - Y0);                        /* hang man hinh cua hang anh do */
        int hg = FY - sy;                               /* do cao so voi chan (diem anh that) */
        if (hg <= 0) continue;
        int Y = FY + hg / 3;
        if (Y < sh_cy0 || Y >= sh_cy1) continue;
        int ua = sp[r * 2], ub = sp[r * 2 + 1];         /* doan co bong tren hang (cot anh) */
        if (ua < X0) ua = X0;
        if (ub > X0 + PW) ub = X0 + PW;
        if (ub <= ua) continue;
        int lech = (int)(hg * SH_SHX);
        int a = hg >= hmax ? a1 : a0 - (a0 - a1) * hg / hmax;
        const uint8_t *mr = m + (size_t)r * IW;
        uint32_t *dp = o->px + (size_t)Y * o->w;
        uint8_t *st = sh_st + (size_t)Y * o->w;
        /* cot anh c -> X man hinh: khong lat: DX + (c - X0) + lech; lat ngang: DX + (X0 + PW - 1 - c) + lech */
        int xa, xb;
        if (tr == 2) {
            xa = DX + (X0 + PW - ub) + lech;
            xb = DX + (X0 + PW - ua) + lech;
        } else {
            xa = DX + (ua - X0) + lech;
            xb = DX + (ub - X0) + lech;
        }
        int ca = xa < sh_cx0 ? sh_cx0 : xa, cb = xb > sh_cx1 ? sh_cx1 : xb;
        if (cb <= ca) continue;
        /* con tro mat na ung voi X = ca, buoc +1 (khong lat) / -1 (lat ngang) */
        const uint8_t *mp = tr == 2 ? mr + (X0 + PW - 1 - (ca - lech - DX)) : mr + (X0 + (ca - lech - DX));
        int buoc = tr == 2 ? -1 : 1;
        for (int X = ca; X < cb; X++, mp += buoc) {
            int cv = *mp;
            if (cv < 6) continue;
            int na = a * cv >> 8;
            int cu = st[X];
            if (na <= cu) continue;
            st[X] = (uint8_t)na;
            /* tu muc toi cu len muc moi (hau het: chua co bong -> khong phai chia) */
            uint32_t f = cu ? (uint32_t)(((256 - na) << 8) / (256 - cu)) : (uint32_t)(256 - na);
            uint32_t fb = f + ((256 - f) * 5 >> 4);                       /* kenh xanh duong toi it hon: bong hoi xanh (anh troi) */
            uint32_t v = dp[X];
            dp[X] = 0xFF000000u | ((((v & 0xFF0000u) * f) >> 8) & 0xFF0000u) | ((((v & 0x00FF00u) * f) >> 8) & 0x00FF00u)
                    | (((v & 0xFFu) * fb) >> 8);
        }
    }
}

/* cay lay theo gio: ve tung hang, hang cang cao lech cang nhieu (goc dung yen). 1 = da ve */
static int draw_img_sway(tgt *o, const uint32_t *src, int sstride, int k, int xs, int ys, int w, int h, int px, int py,
                         int kind) {
    if (w <= 0 || h < 20) return 0;
    int W0 = w * k, H0 = h * k;
    float tg = (float)(fx_ms() % 3600000) / 1000.0f;
    float amp = h * 0.03f;
    if (amp > 2.6f) amp = 2.6f;
    amp *= k;
    float ph = px * 0.045f + py * 0.02f;
    float s1 = sinf(tg * 1.25f + ph) * 0.68f + sinf(tg * 2.9f + ph * 1.7f) * 0.32f;
    int cx0 = o->x0 * k, cy0 = o->y0 * k, cx1 = o->x1 * k, cy1 = o->y1 * k;
    const uint32_t *base = src + (size_t)ys * k * sstride + (size_t)xs * k;
    for (int v = 0; v < H0; v++) {
        int Y = py * k + v;
        if (Y < cy0 || Y >= cy1) continue;
        float q = (float)(H0 - 1 - v) / (float)H0;
        int dx = (int)lroundf(amp * q * q * s1);
        int X0 = px * k + dx;
        int xa = X0 > cx0 ? X0 : cx0, xb = X0 + W0 < cx1 ? X0 + W0 : cx1;
        if (xb <= xa) continue;
        row_put(o->px + (size_t)Y * o->w + xa, base + (size_t)v * sstride + (xa - X0), xb - xa, kind);
    }
    return 1;
}

/* co cua anh da phong to: cay (nhieu xanh la) / hao quang (nhieu diem ban trong suot) */
static int hd_flags(const uint32_t *px, int w, int h, int k, int mode) {
    size_t n = (size_t)w * h, xanh = 0, dac = 0, mo = 0;
    size_t buoc = n > 300000 ? 5 : 1;
    for (size_t i = 0; i < n; i += buoc) {
        uint32_t c = px[i];
        int a = c >> 24;
        if (a < 8) continue;
        if (a < 235) {
            mo++;
            continue;
        }
        dac++;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (g > r + 12 && g > b + 12) xanh++;
    }
    int fl = 0;
    if (mo * 100 > (mo + dac) * 34) fl |= HDF_GLOW;
    if ((mode & 64) && h >= 24 * k && dac > 0 && xanh * 100 >= dac * 30) fl |= HDF_PLANT;
    return fl;
}

/* van noi: sang = sang - manh * (L(x-d, y-d) - L(x+d, y+d)). luoi > 0: anh la bang o (canh o = luoi diem anh that) - khong
 * tinh qua ranh o (o canh nhau trong bang khong lien quan gi nhau) */
static void hd_bump(uint32_t *px, int w, int h, int k, int manh, int luoi) {
    size_t n = (size_t)w * h;
    if (n > 6000000 || w < 4 * k || h < 4 * k) return;
    uint8_t *L = (uint8_t *)malloc(n);
    if (!L) return;
    for (size_t i = 0; i < n; i++) {
        uint32_t c = px[i];
        L[i] = (uint8_t)((((c >> 16) & 255) * 77 + ((c >> 8) & 255) * 150 + (c & 255) * 29) >> 8);
    }
    int d = k >= 4 ? 2 : 1;
    for (int y = d; y < h - d; y++) {
        if (luoi > 0) {
            int my = y % luoi;
            if (my < d || my >= luoi - d) continue;
        }
        uint32_t *row = px + (size_t)y * w;
        for (int x = d; x < w - d; x++) {
            uint32_t c = row[x];
            if ((c >> 24) < 128) continue;
            if (luoi > 0) {
                int mx = x % luoi;
                if (mx < d || mx >= luoi - d) continue;
            }
            size_t ia = (size_t)(y - d) * w + (x - d), ib = (size_t)(y + d) * w + (x + d);
            if ((px[ia] >> 24) < 128 || (px[ib] >> 24) < 128) continue;
            int e = (int)L[ia] - (int)L[ib];
            if (e > 72) e = 72;
            else if (e < -72) e = -72;
            int dl = -e * manh / 256;
            if (!dl) continue;
            int r = (int)((c >> 16) & 255) + dl, g = (int)((c >> 8) & 255) + dl, b = (int)(c & 255) + dl;
            r = r < 0 ? 0 : r > 255 ? 255 : r;
            g = g < 0 ? 0 : g > 255 ? 255 : g;
            b = b < 0 ? 0 : b > 255 ? 255 : b;
            row[x] = (c & 0xFF000000u) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
        }
    }
    free(L);
}

/* nen thay cho mau den khi game xoa ca man hinh (dich da o diem anh that) */
static void fill_nen(tgt *o) {
    for (int y = 0; y < o->h; y++) {
        int t = y * 256 / (o->h > 1 ? o->h - 1 : 1);
        /* rat toi (gan den, nga xanh): hoa voi cac o den cua ban do trong nha, khong con den tron */
        uint32_t r = (uint32_t)(7 + (5 - 7) * t / 256), g = (uint32_t)(10 + (19 - 10) * t / 256), b = (uint32_t)(23 + (28 - 23) * t / 256);
        row_fill(o->px + (size_t)y * o->w, 0xFF000000u | (r << 16) | (g << 8) | b, o->w);
    }
}
