/* hdtheme.c - CHU DE cua may HD (nam trong gfx.c, sau hd3d.c). Quan tri chon chu de trong trang quan tri, may chu bao
 * cho app (HdUi -> Graphics.setStrokeStyle(0x4870 + ma)):
 *   1 Giang sinh : tuyet roi 3 lop, mat dat / co phu trang, dinh vat the co tuyet dong
 *   2 Tinh nhan  : trai tim bay len, sao lap lanh
 *   3 Vui nhon   : phao giay roi, bong bay
 *   4 Halloween  : doi bay, hon ma lo lung, suong tim, canh nga chieu toi
 * Hat ve bang "con dau" (anh nho ve bang cong thuc, co khoi sang toi) to mau luc ve; vi tri hat tinh theo thoi gian
 * (khong can trang thai) nen khong ton gi khi tat.
 */
int gfx_theme;                      /* chu de dang bat (0 = khong) */

/* ------------------------------------------------------------ con dau: moi diem = do sang L (180 = mau goc, 255 = trang)
 * + do duc A */
typedef struct th_stamp {
    uint8_t *la;                    /* w * h * 2 byte: L, A */
    int w, h;
} th_stamp;

enum {
    TS_FLAKE = 0, TS_HEART, TS_STAR, TS_BALLOON, TS_GHOST, TS_BAT0, TS_BAT1, TS_BAT2, TS_CONF0,
    TS_BUOM0 = TS_CONF0 + 8, TS_BUOM1, TS_BUOM2,        /* buom: canh mo het / mo vua / khep */
    TS_LA0, TS_LA1, TS_LA2, TS_LA3,                     /* canh hoa / la nho, 4 goc xoay */
    TS_N
};
static th_stamp th_st[TS_N];
static int th_k;

/* ham hinh: (u, v) trong [-1, 1] -> do sang L (0..255), tra ve 0 neu ngoai hinh, 1 neu trong */
static int th_shape(int id, float u, float v, int *L) {
    switch (id) {
    case TS_BUOM0:
    case TS_BUOM1:
    case TS_BUOM2: {                /* buom nhin tu tren: than + 2 doi canh, be ngang canh theo muc mo */
        static const float mo[3] = {1.0f, 0.62f, 0.26f};
        float sx = mo[id - TS_BUOM0];
        if (fabsf(u) < 0.085f && v > -0.50f && v < 0.56f) {
            *L = 46;
            return 1;
        }
        if (v < -0.48f && v > -0.84f && fabsf(fabsf(u) - (0.06f + (-0.48f - v) * 0.55f)) < 0.045f) {     /* rau */
            *L = 46;
            return 1;
        }
        float x = fabsf(u) / sx;
        if (x > 1.0f) return 0;
        float a1 = (x - 0.47f) / 0.47f, b1 = (v + 0.30f) / 0.44f;          /* canh tren */
        float a2 = (x - 0.36f) / 0.34f, b2 = (v - 0.36f) / 0.32f;          /* canh duoi */
        float d1 = a1 * a1 + b1 * b1, d2 = a2 * a2 + b2 * b2;
        float d = d1 < d2 ? d1 : d2;
        if (d > 1.0f) return 0;
        float sh;
        if (d > 0.70f) sh = 76.0f;                                         /* vien toi */
        else if (d1 < 0.15f) sh = 246.0f;                                  /* dom sang giua canh */
        else if (d2 < 0.13f) sh = 234.0f;
        else sh = 182.0f - d * 26.0f;
        *L = (int)sh;
        return 1;
    }
    case TS_LA0:
    case TS_LA1:
    case TS_LA2:
    case TS_LA3: {                  /* canh hoa / la nho hinh thoi tron */
        float g = (float)(id - TS_LA0) * 0.7853982f;
        float c = cosf(g), sn = sinf(g);
        float x = u * c + v * sn, y = -u * sn + v * c;
        float d = x * x / 0.80f + y * y / 0.20f;
        if (d > 1.0f) return 0;
        float sh = 190.0f + x * 44.0f - (d > 0.68f ? 42.0f : 0.0f);
        *L = sh < 60 ? 60 : sh > 255 ? 255 : (int)sh;
        return 1;
    }
    case TS_FLAKE: {                /* bong tuyet 6 canh */
        float r = sqrtf(u * u + v * v);
        if (r > 1.0f) return 0;
        float a = atan2f(v, u);
        float sp = fabsf(sinf(a * 3.0f));                   /* 6 canh */
        float wdt = 0.16f + 0.10f * (1.0f - r);
        int in = r < 0.22f || sp < wdt || (r > 0.45f && r < 0.62f && sp < 0.42f);
        if (!in) return 0;
        *L = 255;
        return 1;
    }
    case TS_HEART: {
        float x = u * 1.25f, y = -v * 1.25f + 0.22f;
        float q = x * x + y * y - 1.0f;
        float f = q * q * q - x * x * y * y * y;
        if (f > 0) return 0;
        float hl = (u + 0.38f) * (u + 0.38f) * 5.0f + (v + 0.40f) * (v + 0.40f) * 9.0f;       /* dom sang trai-tren */
        float sh = 180.0f + (-v) * 26.0f - u * 14.0f;
        if (hl < 1.0f) sh += (1.0f - hl) * 70.0f;
        if (f > -0.06f) sh -= 46.0f;                        /* vien toi */
        *L = sh < 60 ? 60 : sh > 255 ? 255 : (int)sh;
        return 1;
    }
    case TS_STAR: {                 /* sao 4 canh lap lanh */
        float f = sqrtf(fabsf(u)) + sqrtf(fabsf(v));
        if (f > 1.0f) return 0;
        *L = 255;
        return 1;
    }
    case TS_BALLOON: {
        float x = u / 0.74f, y = (v + 0.14f) / 0.84f;
        float r = x * x + y * y;
        if (r <= 1.0f) {
            float hl = (u + 0.26f) * (u + 0.26f) * 9.0f + (v + 0.50f) * (v + 0.50f) * 6.0f;
            float sh = 176.0f - u * 22.0f - v * 24.0f;
            if (hl < 1.0f) sh += (1.0f - hl) * 76.0f;
            if (r > 0.86f) sh -= 40.0f;
            *L = sh < 50 ? 50 : sh > 255 ? 255 : (int)sh;
            return 1;
        }
        if (v > 0.68f && v < 0.84f && fabsf(u) < (v - 0.66f) * 0.9f) {        /* nut buoc */
            *L = 130;
            return 1;
        }
        return 0;
    }
    case TS_GHOST: {
        float top = sqrtf(u * u / 0.5184f + (v + 0.28f) * (v + 0.28f) / 0.5184f);     /* dau tron ban kinh 0,72 */
        int in = 0;
        if (v <= -0.28f) in = top <= 1.0f;
        else if (fabsf(u) <= 0.72f) {
            float day = 0.80f + 0.12f * sinf(u * 13.0f);                               /* vien duoi luon song */
            in = v <= day;
        }
        if (!in) return 0;
        /* mat + mieng */
        float e1 = (u + 0.26f) * (u + 0.26f) / 0.016f + (v + 0.30f) * (v + 0.30f) / 0.03f;
        float e2 = (u - 0.26f) * (u - 0.26f) / 0.016f + (v + 0.30f) * (v + 0.30f) / 0.03f;
        float m = u * u / 0.02f + (v - 0.06f) * (v - 0.06f) / 0.03f;
        if (e1 < 1.0f || e2 < 1.0f || m < 1.0f) {
            *L = 20;
            return 1;
        }
        float sh = 200.0f - u * 30.0f - v * 18.0f;
        *L = sh < 120 ? 120 : sh > 255 ? 255 : (int)sh;
        return 1;
    }
    case TS_BAT0:
    case TS_BAT1:
    case TS_BAT2: {
        float lift = id == TS_BAT0 ? -0.34f : id == TS_BAT1 ? 0.0f : 0.30f;            /* canh gio len / ngang / cup xuong */
        float ax = fabsf(u);
        /* than + tai */
        if (u * u / 0.026f + v * v / 0.085f <= 1.0f) {
            *L = 150;
            return 1;
        }
        if (ax < 0.15f && v < -0.20f && v > -0.50f && ax > 0.03f && (v + 0.50f) > (ax - 0.03f) * 1.6f) {
            *L = 150;
            return 1;
        }
        if (ax < 0.12f || ax > 1.0f) return 0;
        float t = (ax - 0.12f) / 0.88f;                     /* 0 sat than .. 1 dau canh */
        float tren = -0.10f + lift * t - 0.34f * sinf(t * 3.14159f);                   /* mep tren cong */
        float duoi = 0.10f + lift * t + 0.20f * (1.0f - t) + 0.10f * fabsf(sinf(t * 9.42f));   /* mep duoi rang cua */
        if (v < tren || v > duoi) return 0;
        *L = 170 - (int)(t * 40);
        return 1;
    }
    default: {                      /* phao giay: hinh chu nhat xoay (8 goc) */
        float a = (float)(id - TS_CONF0) * 0.3927f;
        float c = cosf(a), s = sinf(a);
        float x = u * c + v * s, y = -u * s + v * c;
        if (fabsf(x) > 0.86f || fabsf(y) > 0.40f) return 0;
        *L = 150 + (int)((y + 0.4f) * 90.0f);
        return 1;
    }
    }
}

static void th_build(int k) {
    static const int co[TS_N] = {7, 12, 6, 15, 16, 14, 14, 14, 5, 5, 5, 5, 5, 5, 5, 5,
                                 10, 10, 10, 5, 5, 5, 5};      /* canh (diem logic) */
    for (int i = 0; i < TS_N; i++) {
        free(th_st[i].la);
        int n = co[i] * k;
        th_st[i].w = th_st[i].h = n;
        th_st[i].la = (uint8_t *)calloc((size_t)n * n, 2);
        if (!th_st[i].la) continue;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int cnt = 0, sum = 0;
                for (int sy = 0; sy < 3; sy++)
                    for (int sx = 0; sx < 3; sx++) {
                        float u = ((x + (sx + 0.5f) / 3.0f) / n) * 2.0f - 1.0f, v = ((y + (sy + 0.5f) / 3.0f) / n) * 2.0f - 1.0f;
                        int L = 0;
                        if (th_shape(i, u, v, &L)) {
                            cnt++;
                            sum += L;
                        }
                    }
                if (cnt) {
                    th_st[i].la[((size_t)y * n + x) * 2] = (uint8_t)(sum / cnt);
                    th_st[i].la[((size_t)y * n + x) * 2 + 1] = (uint8_t)(cnt * 255 / 9);
                }
            }
    }
    th_k = k;
}

/* dong con dau tam (cx, cy), mau (r, g, b), do duc 0..256, lat ngang neu flip */
static void th_blit(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, const th_stamp *s, int cx, int cy, int r, int g,
                    int b, int alpha, int flip) {
    if (!s->la || alpha <= 0) return;
    int ox = cx - s->w / 2, oy = cy - s->h / 2;
    if (x0 < 0) x0 = 0;
    if (y0 < 0) y0 = 0;
    if (x1 > W) x1 = W;
    if (y1 > H) y1 = H;
    int ya = oy < y0 ? y0 : oy, yb = oy + s->h > y1 ? y1 : oy + s->h;
    int xa = ox < x0 ? x0 : ox, xb = ox + s->w > x1 ? x1 : ox + s->w;
    for (int y = ya; y < yb; y++) {
        const uint8_t *m = s->la + (size_t)(y - oy) * s->w * 2;
        uint32_t *d = fb + (size_t)y * W;
        for (int x = xa; x < xb; x++) {
            int sx = flip ? s->w - 1 - (x - ox) : x - ox;
            int A = m[sx * 2 + 1];
            if (!A) continue;
            int L = m[sx * 2];
            int R, G, B;
            if (L <= 180) {
                R = r * L / 180;
                G = g * L / 180;
                B = b * L / 180;
            } else {
                int t = (L - 180) * 256 / 75;
                R = r + (255 - r) * t / 256;
                G = g + (255 - g) * t / 256;
                B = b + (255 - b) * t / 256;
            }
            A = A * alpha >> 8;
            uint32_t v = d[x];
            int dr = (v >> 16) & 255, dg = (v >> 8) & 255, db = v & 255;
            dr += (R - dr) * A / 255;
            dg += (G - dg) * A / 255;
            db += (B - db) * A / 255;
            d[x] = 0xFF000000u | ((uint32_t)dr << 16) | ((uint32_t)dg << 8) | (uint32_t)db;
        }
    }
}

static inline unsigned th_hash(unsigned i) {
    i = i * 2654435761u;
    i ^= i >> 15;
    i *= 2246822519u;
    i ^= i >> 13;
    return i;
}

/* ve hat cua chu de len khung (sau hau ky canh). dem: 0 ngay, 1 dem, 2 trong nha (khong ve) */
/* Su song cua canh ngoai troi ban ngay (khi khong bat chu de): buom bay luon + canh hoa / la roi cheo theo gio. Vi tri tinh
 * theo THE GIOI (tx = do doi ngang cua the gioi tren man hinh, diem logic): cuon ban do thi buom / la o lai cho cu, khong
 * dinh theo man hinh. Khong can trang thai: moi con la 1 ham cua thoi gian. */
static void th_life(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int k, int tx) {
    if (th_k != k) th_build(k);
    int rw = x1 - x0, rh = y1 - y0;
    float lw = (float)rw / k, lh = (float)rh / k;
    double t = (double)(fx_ms() % 36000000) / 1000.0;
    float P = lw + 120.0f;                                  /* chu ky lap theo chieu ngang (rong hon man hinh) */
    static const int mb[5][3] = {{255, 214, 60}, {250, 250, 255}, {255, 150, 50}, {120, 190, 255}, {255, 150, 200}};
    for (int i = 0; i < 7; i++) {
        unsigned h = th_hash((unsigned)i + 4201);
        float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
        float w1 = 0.31f * (0.7f + fz), w2 = 0.47f * (0.7f + fx);
        float x = fx * P + 30.0f * sinf((float)t * w1 + fx * 11.0f) + 9.0f * sinf((float)t * 1.9f + fy * 5.0f);
        float y = lh * (0.36f + 0.40f * fy) + 12.0f * sinf((float)t * w2 + fy * 7.0f) + 4.0f * sinf((float)t * 2.7f + fx * 3.0f);
        x = fmodf(x + (float)tx, P);
        if (x < 0) x += P;
        x -= 60.0f;
        int kh = (int)(t * 13.0 + i * 3) & 3;               /* vo canh: mo - vua - khep - vua */
        const int *c = mb[h % 5];
        th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_BUOM0 + (kh == 3 ? 1 : kh)], x0 + (int)(x * k), y0 + (int)(y * k), c[0], c[1],
                c[2], 255, 0);
    }
    static const int ml[4][3] = {{255, 196, 214}, {255, 236, 242}, {168, 214, 96}, {244, 214, 96}};
    for (int i = 0; i < 12; i++) {
        unsigned h = th_hash((unsigned)i + 5303);
        float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
        float sp = 9.0f + fz * 8.0f, mh = lh + 30.0f;
        float y = fmodf(fy * mh + (float)t * sp, mh) - 15.0f;
        float x = fx * P + (float)fmod(t * (5.0 + fz * 5.0), (double)P) + sinf((float)t * (0.9f + fz) + fy * 20.0f) * 7.0f;
        x = fmodf(x + (float)tx, P);
        if (x < 0) x += P;
        x -= 60.0f;
        int quay = (int)((float)t * (2.2f + fz * 2.0f) + fx * 8.0f) & 3;
        const int *c = ml[h % 4];
        th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_LA0 + quay], x0 + (int)(x * k), y0 + (int)(y * k), c[0], c[1], c[2], 228, 0);
    }
}

static void th_draw(uint32_t *fb, int W, int H, int x0, int y0, int x1, int y1, int k, int dem) {
    int th = gfx_theme;
    if (th <= 0 || dem == 2) return;
    if (th_k != k) th_build(k);
    int rw = x1 - x0, rh = y1 - y0;
    float lw = (float)rw / k, lh = (float)rh / k;           /* kich thuoc logic cua vung */
    double t = (double)(fx_ms() % 36000000) / 1000.0;
    if (th == 1) {
        /* --- tuyet: xa (nho, cham), giua, gan (bong tuyet to) */
        for (int i = 0; i < 150; i++) {
            unsigned h = th_hash((unsigned)i + 17);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            int lop = i < 80 ? 0 : i < 132 ? 1 : 2;
            float sp = lop == 0 ? 11.0f + fz * 7.0f : lop == 1 ? 20.0f + fz * 12.0f : 34.0f + fz * 16.0f;
            float mh = lh + 30.0f, mw = lw + 30.0f;
            float y = fmodf(fy * mh + (float)t * sp, mh) - 15.0f;
            float x = fmodf(fx * mw + sinf((float)t * (0.5f + fz) + fx * 30.0f) * (6.0f + lop * 5.0f) + (float)t * (3.0f + lop * 3.0f), mw) - 15.0f;
            int cx = x0 + (int)(x * k), cy = y0 + (int)(y * k);
            if (lop == 2) {
                th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_FLAKE], cx, cy, 255, 255, 255, 215, 0);
            } else {
                int rad = (int)((lop == 0 ? 0.8f + fz * 0.5f : 1.3f + fz * 0.8f) * k);
                fx_dot_mau(fb, W, H, x0, y0, x1, y1, cx, cy, rad < 2 ? 2 : rad, lop == 0 ? 150 : 215, 255, 255, 255);
            }
        }
    } else if (th == 2) {
        /* --- trai tim bay len + sao lap lanh */
        static const int mau[4][3] = {{255, 70, 120}, {255, 120, 170}, {236, 40, 80}, {255, 170, 200}};
        for (int i = 0; i < 30; i++) {
            unsigned h = th_hash((unsigned)i + 91);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            float sp = 9.0f + fz * 13.0f, mh = lh + 40.0f;
            float y = lh + 20.0f - fmodf(fy * mh + (float)t * sp, mh);
            float x = fx * lw + sinf((float)t * (0.6f + fz * 0.8f) + fy * 20.0f) * (7.0f + fz * 9.0f);
            float life = 1.0f - (lh + 20.0f - y) / mh;                          /* 1 moi sinh .. 0 len toi dinh */
            int a = (int)(256 * (life < 0.2f ? life / 0.2f : life > 0.85f ? (1.0f - life) / 0.15f : 1.0f) * (0.55f + fz * 0.4f));
            const int *c = mau[h % 4];
            th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_HEART], x0 + (int)(x * k), y0 + (int)(y * k), c[0], c[1], c[2], a, 0);
        }
        for (int i = 0; i < 22; i++) {
            unsigned h = th_hash((unsigned)i + 333);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            float tw = sinf((float)t * (1.5f + fz * 2.5f) + fx * 40.0f);
            if (tw <= 0.2f) continue;
            th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_STAR], x0 + (int)(fx * lw * k), y0 + (int)(fy * lh * 0.8f * k), 255, 235, 245,
                    (int)(230 * tw), 0);
        }
    } else if (th == 3) {
        /* --- phao giay roi (xoay) + bong bay */
        static const int mau[7][3] = {{255, 80, 80}, {255, 190, 40}, {70, 200, 110}, {70, 150, 255}, {190, 110, 255}, {255, 120, 200}, {60, 215, 220}};
        for (int i = 0; i < 70; i++) {
            unsigned h = th_hash((unsigned)i + 505);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            float sp = 22.0f + fz * 26.0f, mh = lh + 24.0f;
            float y = fmodf(fy * mh + (float)t * sp, mh) - 12.0f;
            float x = fx * lw + sinf((float)t * (1.2f + fz * 1.6f) + fy * 25.0f) * (8.0f + fz * 10.0f);
            int quay = (int)((float)t * (5.0f + fz * 7.0f) + fx * 16.0f) & 7;
            const int *c = mau[h % 7];
            th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_CONF0 + quay], x0 + (int)(x * k), y0 + (int)(y * k), c[0], c[1], c[2], 240, 0);
        }
        for (int i = 0; i < 9; i++) {
            unsigned h = th_hash((unsigned)i + 777);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            float sp = 10.0f + fz * 8.0f, mh = lh + 70.0f;
            float y = lh + 35.0f - fmodf(fy * mh + (float)t * sp, mh);
            float x = fx * lw + sinf((float)t * (0.5f + fz * 0.5f) + fy * 15.0f) * 9.0f;
            const int *c = mau[(h >> 3) % 7];
            int cx = x0 + (int)(x * k), cy = y0 + (int)(y * k);
            /* day bong */
            for (int j = 0; j < 14 * k; j++) {
                int px = cx + (int)(sinf(j * 0.35f / k + (float)t * 2.0f + fx * 9.0f) * 1.2f * k), py = cy + 12 * k + j;
                if (px >= x0 && px < x1 && py >= y0 && py < y1) fb[(size_t)py * W + px] = fx_add(fb[(size_t)py * W + px], 60, 60, 60);
            }
            th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_BALLOON], cx, cy, c[0], c[1], c[2], 238, 0);
        }
    } else if (th == 4) {
        /* --- suong tim sat day (do day theo cot tinh 1 lan / khung, moi o 4 diem ngang), hon ma lo lung, doi bay */
        {
            static uint8_t *cot;
            static int cot_n;
            int nc = rw / 4 + 1;
            if (cot_n < nc) {
                free(cot);
                cot = (uint8_t *)malloc((size_t)nc);
                cot_n = cot ? nc : 0;
            }
            if (cot) {
                for (int i = 0; i < nc; i++) {
                    float x = (float)(i * 4);
                    float n = sinf(x * 0.048f / k + (float)t * 0.35f) * 0.5f + sinf(x * 0.124f / k - (float)t * 0.22f) * 0.5f;
                    cot[i] = (uint8_t)(34.0f + n * 22.0f);
                }
                int ytop = y1 - rh / 4;
                for (int y = ytop < y0 ? y0 : ytop; y < y1; y++) {
                    int q = (y - ytop) * 256 / (rh / 4);
                    q = q * q >> 8;
                    uint32_t *d = fb + (size_t)y * W + x0;
                    for (int x = 0; x < rw; x++) {
                        uint32_t a = (uint32_t)(cot[x >> 2] * q) >> 8;
                        if (!a) continue;
                        uint32_t v = d[x], ia = 256 - a;
                        uint32_t rb = (((v & 0xFF00FFu) * ia + 0x00A800C8u * a) >> 8) & 0xFF00FFu;
                        uint32_t gg = (((v & 0x00FF00u) * ia + 0x00009600u * a) >> 8) & 0x00FF00u;
                        d[x] = 0xFF000000u | rb | gg;
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            unsigned h = th_hash((unsigned)i + 1201);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            float chu = 26.0f + fz * 14.0f;                                     /* chu ky hien / an (giay) */
            float ph = fmodf((float)t + fx * chu, chu) / chu;
            float al = ph < 0.15f ? ph / 0.15f : ph > 0.7f ? (ph < 0.85f ? (0.85f - ph) / 0.15f : 0) : 1.0f;
            if (al <= 0) continue;
            float x = fmodf(fx * lw + (float)t * (5.0f + fz * 4.0f), lw + 40.0f) - 20.0f;
            float y = lh * (0.25f + fy * 0.45f) + sinf((float)t * (0.8f + fz) + fx * 9.0f) * 7.0f;
            th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_GHOST], x0 + (int)(x * k), y0 + (int)(y * k), 236, 240, 255, (int)(150 * al), 0);
        }
        for (int i = 0; i < 9; i++) {
            unsigned h = th_hash((unsigned)i + 1777);
            float fx = (h & 1023) / 1023.0f, fy = ((h >> 10) & 1023) / 1023.0f, fz = ((h >> 20) & 1023) / 1023.0f;
            int trai = h & 1;                                                   /* huong bay */
            float sp = 34.0f + fz * 30.0f, mw = lw + 60.0f;
            float x = fmodf(fx * mw + (float)t * sp, mw) - 30.0f;
            if (trai) x = lw - x;
            float y = lh * (0.08f + fy * 0.42f) + sinf((float)t * (2.0f + fz * 2.0f) + fx * 12.0f) * 9.0f;
            int pose = (int)((float)t * (9.0f + fz * 4.0f) + fx * 7.0f) % 4;
            pose = pose == 3 ? 1 : pose;
            th_blit(fb, W, H, x0, y0, x1, y1, &th_st[TS_BAT0 + pose], x0 + (int)(x * k), y0 + (int)(y * k), 44, 26, 62, 235, 0);
        }
    }
}

/* ------------------------------------------------------------ xu ly anh theo chu de (luong phu, luc phong to)
 * nen (o gach, anh nen): nhuom mau theo chu de; vat the: tuyet dong tren dinh (Giang sinh) */
static void th_ground(uint32_t *px, int w, int h, int th) {
    size_t n = (size_t)w * h;
    for (size_t i = 0; i < n; i++) {
        uint32_t c = px[i];
        if ((c >> 24) == 0) continue;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (th == 1) {
            /* phu tuyet: co xanh trang han, cho khac trang nhe */
            int xanh = g - (r > b ? r : b);
            int t = xanh > 18 ? 168 : xanh > 4 ? 120 : 62;
            int lum = (r * 77 + g * 150 + b * 29) >> 8;
            if (lum < 60) t = t * lum / 60;                                     /* net vien / bong toi giu lai */
            int sr = 236 + (lum - 128) / 9, sg = 242 + (lum - 128) / 9, sb = 252;
            sr = sr < 200 ? 200 : sr > 255 ? 255 : sr;
            sg = sg < 205 ? 205 : sg > 255 ? 255 : sg;
            r += (sr - r) * t / 256;
            g += (sg - g) * t / 256;
            b += (sb - b) * t / 256;
        } else if (th == 4) {
            /* mua thu: co nga vang, moi thu am len (van sang, khong toi) */
            int xanh = g - (r > b ? r : b);
            if (xanh > 10) {
                r += (g - r) * 150 / 256 + 10;                                    /* xanh la -> vang o liu */
                b = b * 200 / 256;
            } else {
                r += (255 - r) * 14 / 256;
                b = b * 226 / 256;
            }
            r = r > 255 ? 255 : r;
        } else if (th == 2) {
            r += (255 - r) * 14 / 256;                                           /* am hong nhe */
            b += (255 - b) * 6 / 256;
        }
        px[i] = (c & 0xFF000000u) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
    }
}

/* tuyet dong tren dinh vat the: diem dac ma phia tren (cach 1 doan) la trong suot -> to trang, mem dan xuong duoi */
static void th_snowcap(uint32_t *px, int w, int h, int k) {
    if (w < 8 * k || h < 8 * k) return;
    int day = 2 * k + k / 2;                    /* do day lop tuyet */
    for (int x = 0; x < w; x++) {
        int trong = day + 1;                    /* so diem lien tiep phia tren la trong suot (tinh ca ngoai anh? khong: bien = than) */
        trong = 0;
        for (int y = 0; y < h; y++) {
            uint32_t c = px[(size_t)y * w + x];
            int a = c >> 24;
            if (a < 128) {
                trong++;
                continue;
            }
            if (trong > 0 && y > 0) {
                /* gap mep tren cua 1 khoi: to trang 'day' diem (giam dan), can khoang trong phia tren du rong */
                int rong = trong >= 2 * k ? day : 0;
                for (int j = 0; j < rong && y + j < h; j++) {
                    uint32_t v = px[(size_t)(y + j) * w + x];
                    if ((v >> 24) < 128) break;
                    int t = 236 - j * 150 / day;
                    int r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
                    r += (250 - r) * t / 256;
                    g += (252 - g) * t / 256;
                    b += (255 - b) * t / 256;
                    px[(size_t)(y + j) * w + x] = (v & 0xFF000000u) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
                }
            }
            trong = 0;
        }
    }
}

/* Halloween - tan cay / bui cay cua ban do: xanh la -> la thu (do nau -> cam -> vang theo do sang) */
static void th_autumn(uint32_t *px, int w, int h) {
    size_t n = (size_t)w * h;
    for (size_t i = 0; i < n; i++) {
        uint32_t c = px[i];
        if ((c >> 24) == 0) continue;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (g < r + 14 || g < b + 14) continue;                 /* chi mau xanh la ro rang */
        int lum = (r * 77 + g * 150 + b * 29) >> 8;
        int R, G, B;
        if (lum < 90) {                                         /* toi: nau do */
            R = 70 + lum * 110 / 90;
            G = 20 + lum * 50 / 90;
            B = 14 + lum * 12 / 90;
        } else if (lum < 170) {                                 /* giua: cam */
            int t = lum - 90;
            R = 180 + t * 64 / 80;
            G = 70 + t * 70 / 80;
            B = 26 + t * 10 / 80;
        } else {                                                /* sang: vang */
            int t = lum - 170;
            if (t > 85) t = 85;
            R = 244 + t * 11 / 85;
            G = 140 + t * 84 / 85;
            B = 36 + t * 50 / 85;
        }
        px[i] = (c & 0xFF000000u) | ((uint32_t)R << 16) | ((uint32_t)G << 8) | (uint32_t)B;
    }
}
