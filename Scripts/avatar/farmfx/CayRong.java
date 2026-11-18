import java.util.Hashtable;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Cay khe 1000 cap - cang len cao cay cang dep, co than thu ho cay bay vong quanh:
 *   cap 100: Rong bang (xanh)           cap 300: + Phuong hoang lua
 *   cap 600: + Ky lan bay               cap 900: + Rong lua
 *   dang cay (mau tu nhien, khong mau me, khong nhap nhay): cap 250 cay truong thanh, 500 cay lau nam (la xanh tham),
 *   750 cay co thu (la xanh rung gia, vo cay xam co reu), 1000 co thu ngan nam (tan la xanh dam, vo xam reu) + la roi nhe
 *   cap toi da 1000: rong xanh + rong do thoi bay vong rieng, chuyen sang dua nhau (xoay quanh nhau) roi cung bay toi
 *   qua khe gia vo an (nhai, vun khe roi, bay tim) - qua that tren cay khong mat.
 * - Than thu bay theo elip quanh tan; nua tren elip = phia sau cay (ve truoc cay -> bi tan cay che), nua duoi = phia truoc.
 * - Hinh than thu = anh vat pham co san (tai tu may chu nhu anh do thuong), mau cay = to lai chinh anh cay (khong can anh moi).
 * - PatchFarmFx: kc.a(Graphics): sau() dau ham, veCay() thay lenh ve anh cay, truoc() truoc moi return.
 */
public final class CayRong {

    private static final short[] SIN = {0, 100, 200, 297, 392, 483, 569, 650, 724, 792, 851, 903, 946, 980, 1004, 1019,
            1024, 1019, 1004, 980, 946, 903, 851, 792, 724, 650, 569, 483, 392, 297, 200, 100, 0, -100, -200, -297, -392,
            -483, -569, -650, -724, -792, -851, -903, -946, -980, -1004, -1019, -1024, -1019, -1004, -980, -946, -903,
            -851, -792, -724, -650, -569, -483, -392, -297, -200, -100};

    /** than thu: cap mo, anh 1, anh 2 (dap canh), ms 1 vong, chieu (1 / -1), ban kinh ngang %, doc %, do cao tam % chieu cao cay, lech pha */
    private static final int[][] THU = {
            {100, 10895, 10896, 6400, 1, 64, 22, 58, 0},      // Rong bang
            {300, 7372, 7373, 5200, -1, 52, 16, 82, 21},      // Phuong hoang lua (cao, nguoc chieu)
            {600, 2898, 2900, 8800, 1, 82, 26, 40, 40},       // Ky lan bay (vong rong, cham)
            {900, 9174, 9175, 7400, -1, 74, 20, 66, 10},      // Rong lua
    };
    private static final int VET = 12;
    private static final int[][] vx = new int[THU.length][VET], vy = new int[THU.length][VET];
    private static final int[] vn = new int[THU.length];
    private static long vt = 0;
    private static final Hashtable anhCay = new Hashtable();

    // 4 nac mau (toi -> sang) cho la va vo cay theo bac: 250 truong thanh, 500 lau nam, 750 co thu, 1000 co thu ngan nam
    private static final int[][] LA = {
            {0x0E3410, 0x1F5A20, 0x3A8434, 0x69B24E},
            {0x0C2C14, 0x1B4C24, 0x2F7034, 0x5C9A50},
            {0x0B2612, 0x173F1F, 0x2A5E2C, 0x528646},
            {0x0A2010, 0x15381C, 0x265429, 0x4C7E40}};
    private static final int[][] VO = {
            {0x24190C, 0x4A321A, 0x6C4C2C, 0x94704A},
            {0x201912, 0x3E3024, 0x5C4A36, 0x806C54},
            {0x1C1812, 0x352E24, 0x504536, 0x766A52},
            {0x181510, 0x2F2920, 0x494032, 0x6C624C}};
    /** % pha mau reu vao vo cay (cay co thu) */
    private static final int[] REU = {0, 0, 35, 45};
    private static final int MAU_REU = 0x5A6430;

    private CayRong() {
    }

    // ---------------------------------------------------------------- anh cay doi mau

    /** Bac dang cay theo cap: -1 = goc (cay non), 0 truong thanh (250), 1 lau nam (500), 2 co thu (750), 3 co thu ngan nam (1000). */
    private static int bac(int cap) {
        if (cap >= 1000) {
            return 3;
        }
        if (cap >= 750) {
            return 2;
        }
        if (cap >= 500) {
            return 1;
        }
        if (cap >= 250) {
            return 0;
        }
        return -1;
    }

    /** kc.a: thay lenh gb.a(g, anhCay, x, y, neo). */
    public static void veCay(Graphics g, int id, int x, int y, int neo, kc k) {
        int b = k == null ? -1 : bac(k.a);
        if (b >= 0) {
            f a = gb.a((short) id);
            if (a != null && a.a != null) {
                Image im = toMau(a.a, id, b);
                if (im != null) {
                    g.drawImage(im, x, y, neo);
                    return;
                }
            }
        }
        gb.a(g, id, x, y, neo);
    }

    /** Mau tren day 4 nac, t 0..1023. */
    private static int doc(int[] st, int t) {
        if (t < 0) {
            t = 0;
        } else if (t > 1023) {
            t = 1023;
        }
        int seg = t * 3 / 1024;
        if (seg > 2) {
            seg = 2;
        }
        int u = (t - seg * 1024 / 3) * 3;
        if (u < 0) {
            u = 0;
        } else if (u > 1023) {
            u = 1023;
        }
        int a = st[seg], b = st[seg + 1];
        int r = ((a >> 16) & 255) + (((((b >> 16) & 255) - ((a >> 16) & 255)) * u) >> 10);
        int g = ((a >> 8) & 255) + (((((b >> 8) & 255) - ((a >> 8) & 255)) * u) >> 10);
        int bl = (a & 255) + ((((b & 255) - (a & 255)) * u) >> 10);
        return (r << 16) | (g << 8) | bl;
    }

    /** Pha mau m vao c, k phan tram. */
    private static int pha(int c, int m, int k) {
        int r = (((c >> 16) & 255) * (100 - k) + ((m >> 16) & 255) * k) / 100;
        int g = (((c >> 8) & 255) * (100 - k) + ((m >> 8) & 255) * k) / 100;
        int b = ((c & 255) * (100 - k) + (m & 255) * k) / 100;
        return (r << 16) | (g << 8) | b;
    }

    private static Image toMau(Image goc, int id, int b) {
        Integer key = new Integer(id * 16 + b);
        Object o = anhCay.get(key);
        if (o instanceof Image) {
            return (Image) o;
        }
        if (o != null) {
            return null;
        }
        try {
            int w = goc.getWidth(), h = goc.getHeight();
            int[] px = new int[w * h];
            goc.getRGB(px, 0, w, 0, 0, w, h);
            for (int i = 0; i < px.length; i++) {
                int a = px[i] >>> 24;
                if (a == 0) {
                    continue;
                }
                int r = (px[i] >> 16) & 255, gg = (px[i] >> 8) & 255, bb = px[i] & 255;
                int l = (r * 30 + gg * 59 + bb * 11) / 100;
                int c;
                if (gg > r && gg >= bb) {                       // la
                    c = doc(LA[b], (l - 25) * 1024 / 105);
                } else if (r > bb && r >= gg) {                 // vo cay, canh
                    int t = (l - 22) * 1024 / 110;
                    c = doc(VO[b], t);
                    if (REU[b] > 0 && t > 250 && t < 850) {
                        c = pha(c, MAU_REU, REU[b]);
                    }
                } else {
                    continue;                                   // net vien, bong: giu nguyen
                }
                px[i] = (a << 24) | c;
            }
            Image im = Image.createRGBImage(px, w, h, true);
            anhCay.put(key, im);
            return im;
        } catch (Throwable e) {
            anhCay.put(key, new Object());
            return null;
        }
    }

    // ---------------------------------------------------------------- than thu

    public static void sau(Graphics g, kc k) {
        ve(g, k, false);
    }

    public static void truoc(Graphics g, kc k) {
        ve(g, k, true);
    }

    private static void ve(Graphics g, kc k, boolean truoc) {
        if (k == null || k.a < THU[0][0]) {
            return;
        }
        long t = System.currentTimeMillis();
        int d = gy.Y > 0 ? gy.Y : 1;
        f cay = gb.a(k.b);
        int w = (cay != null && cay.b > 0) ? cay.b : 70 * d;
        int h = (cay != null && cay.c > 0) ? cay.c : 66 * d;
        int cx = k.aw * js.ay, day = k.ax * js.ay;
        boolean ghi = truoc && t - vt >= 70;
        if (ghi) {
            vt = t;
        }
        boolean toiDa = k.a >= CAP_MAX;
        if (truoc && toiDa) {
            haoQuang(g, t, cx, day - h * 58 / 100, w * 55 / 100, h * 30 / 100, d);
        }
        for (int i = 0; i < THU.length; i++) {
            int[] c = THU[i];
            if (k.a < c[0]) {
                break;
            }
            if (toiDa && (i == 0 || i == 3)) {
                continue;                       // 2 rong dang choi voi nhau (choi())
            }
            int ph = (int) (((t % c[3]) * 64 / c[3]) * c[4] + c[8]) & 63;
            int sn = SIN[ph], cs = SIN[(ph + 16) & 63];
            int ra = w * c[5] / 100, rb = h * c[6] / 100;
            int x = cx + ra * cs / 1024;
            int y = day - h * c[7] / 100 + rb * sn / 1024 + SIN[(int) ((t / 45 + i * 9) & 63)] * 3 * d / 1024;
            // chieu bay ngang: dx ~ -sin * chieu -> > 0 la sang phai (anh goc quay phai)
            boolean phai = -sn * c[4] > 0;
            boolean phiaSau = sn < 0;
            if (truoc) {
                if (ghi) {
                    vx[i][vn[i] % VET] = x;
                    vy[i][vn[i] % VET] = y;
                    vn[i]++;
                }
                veVet(g, t, d, i);
            }
            if (truoc == phiaSau) {
                continue;
            }
            f a = fx.b((short) ((t / 170) % 2 == 0 ? c[1] : c[2]));
            if (a == null || a.a == null) {
                continue;
            }
            Image im = a.a;
            if (phai) {
                g.drawImage(im, x, y, 3);
            } else {
                g.drawRegion(im, 0, 0, im.getWidth(), im.getHeight(), 2, x, y, 3);
            }
        }
        if (truoc && toiDa) {
            choi(g, k, t, d, w, h, cx, day, ghi);
        }
    }

    // ---------------------------------------------------------------- cap toi da: rong xanh + rong do dua nhau, gia vo an khe

    public static final int CAP_MAX = 1000;
    /** 1 vong dien 12 giay: 0-5s dua nhau, 5-6.5s bay toi qua, 6.5-9.5s nhai khe, 9.5-12s lui ra bay ve */
    private static final int CHU = 12000;
    private static final int[] p0 = new int[4], p1 = new int[4];
    private static boolean xanhPhai = true, doPhai = false;

    private static int muot(int u) {
        if (u <= 0) {
            return 0;
        }
        if (u >= 1024) {
            return 1024;
        }
        return u * u / 1024 * (3072 - 2 * u) / 1024;
    }

    /** 0 = dang dua nhau, 1024 = dang o qua khe */
    private static int trongSo(int p) {
        if (p < 5000) {
            return 0;
        }
        if (p < 6500) {
            return muot((p - 5000) * 1024 / 1500);
        }
        if (p < 9500) {
            return 1024;
        }
        return 1024 - muot((p - 9500) * 1024 / 2500);
    }

    private static int rong(f a, boolean ngang, int macDinh) {
        if (a == null || a.a == null) {
            return macDinh;
        }
        return ngang ? a.a.getWidth() : a.a.getHeight();
    }

    /** Diem 2 rong ngam: qua khe trai nhat (rong xanh) / phai nhat (rong do); cay chua co qua thi ngam tan la. */
    private static void diemAn(kc k, int d, int w, int h, int cx, int day, int[] o) {
        byte[] qi = k.i, qj = k.j;
        if (k.d > 0 && qi != null && qj != null && qi.length > 0 && qj.length >= qi.length) {
            int t = 0, ph = 0;
            for (int n = 1; n < qi.length; n++) {
                if (qi[n] < qi[t]) {
                    t = n;
                }
                if (qi[n] > qi[ph]) {
                    ph = n;
                }
            }
            int y0 = day - (h / 2 + 5);
            o[0] = cx + qi[t];
            o[1] = y0 + qj[t];
            o[2] = cx + qi[ph];
            o[3] = y0 + qj[ph];
            if (t == ph) {                      // 1 qua: 2 rong tranh nhau 1 qua
                o[0] -= 2 * d;
                o[2] += 2 * d;
            }
            return;
        }
        o[0] = cx - w / 6;
        o[1] = day - h * 60 / 100;
        o[2] = cx + w / 6;
        o[3] = o[1];
    }

    private static final int[] an = new int[4];

    /** Vi tri tam 2 rong luc t: o[0],o[1] rong xanh; o[2],o[3] rong do. */
    private static void viTri(long t, kc k, int d, int w, int h, int cx, int day, int[] o) {
        int p = (int) (t % CHU);
        // tam diem nhay: bay cham vong quanh tan cay
        int tc = (int) ((t % 9000) * 64 / 9000);
        int mx = cx + (w * 48 / 100) * SIN[(tc + 16) & 63] / 1024;
        int my = day - h * 66 / 100 + (h * 14 / 100) * SIN[tc] / 1024;
        // 2 rong doi dien nhau, xoay vong quanh nhau nhu duoi bat nhau
        int ph = (int) ((t % 2400) * 64 / 2400);
        int ox = 24 * d * SIN[(ph + 16) & 63] / 1024, oy = 11 * d * SIN[ph] / 1024;
        o[0] = mx + ox;
        o[1] = my + oy;
        o[2] = mx - ox;
        o[3] = my - oy;
        int wt = trongSo(p);
        if (wt == 0) {
            return;
        }
        diemAn(k, d, w, h, cx, day, an);
        f qua = gb.a(k.c);
        int q3 = (qua != null && qua.b > 0 ? qua.b : 10 * d) / 3;
        f fb = fx.b((short) 10895), fr = fx.b((short) 9174);
        // mieng rong (anh quay phai) ~ 45% chieu ngang tu tam, hoi duoi tam
        int bx = an[0] - q3 - rong(fb, true, 47 * d) * 45 / 100, by = an[1] - rong(fb, false, 38 * d) * 3 / 100;
        int rx = an[2] + q3 + rong(fr, true, 39 * d) * 45 / 100, ry = an[3] - rong(fr, false, 31 * d) * 8 / 100;
        if (p >= 6500 && p < 9500) {
            int nb = SIN[((p - 6500) * 64 / 520) & 63];   // nhai: chui toi can roi lui lai
            if (nb < 0) {
                nb = -nb;
            }
            bx += 3 * d * nb / 1024;
            rx -= 3 * d * nb / 1024;
            by += d * nb / 1024;
            ry += d * nb / 1024;
        }
        o[0] += (bx - o[0]) * wt / 1024;
        o[1] += (by - o[1]) * wt / 1024;
        o[2] += (rx - o[2]) * wt / 1024;
        o[3] += (ry - o[3]) * wt / 1024;
    }

    private static void choi(Graphics g, kc k, long t, int d, int w, int h, int cx, int day, boolean ghi) {
        int p = (int) (t % CHU);
        int wt = trongSo(p);
        viTri(t, k, d, w, h, cx, day, p0);
        viTri(t + 80, k, d, w, h, cx, day, p1);
        if (wt >= 700) {                        // dang ngam qua: luon quay mat vao qua
            xanhPhai = true;
            doPhai = false;
        } else {
            if (p1[0] != p0[0]) {
                xanhPhai = p1[0] > p0[0];
            }
            if (p1[2] != p0[2]) {
                doPhai = p1[2] > p0[2];
            }
        }
        if (ghi) {
            vx[0][vn[0] % VET] = p0[0];
            vy[0][vn[0] % VET] = p0[1];
            vn[0]++;
            vx[3][vn[3] % VET] = p0[2];
            vy[3][vn[3] % VET] = p0[3];
            vn[3]++;
        }
        veVet(g, t, d, 0);
        veVet(g, t, d, 3);
        boolean dangAn = p >= 6500 && p < 9500;
        int nhip = dangAn ? 320 : 170;              // luc nhai vo canh cham lai
        f fb = fx.b((short) ((t / nhip) % 2 == 0 ? 10895 : 10896));
        f fr = fx.b((short) ((t / nhip + 1) % 2 == 0 ? 9174 : 9175));
        // con o xa (y nho) ve truoc
        if (p0[1] <= p0[3]) {
            veRong(g, fb, p0[0], p0[1], xanhPhai);
            veRong(g, fr, p0[2], p0[3], doPhai);
        } else {
            veRong(g, fr, p0[2], p0[3], doPhai);
            veRong(g, fb, p0[0], p0[1], xanhPhai);
        }
        if (dangAn) {
            vun(g, p - 6500, d, p0[0] + rong(fb, true, 47 * d) * 45 / 100, p0[1], p0[2] - rong(fr, true, 39 * d) * 45 / 100, p0[3]);
        }
        if (p >= 9100 && p < 10500) {           // an xong: tim bay len giua 2 con
            int q = p - 9100;
            int tx = (p0[0] + p0[2]) / 2, ty = Math.min(p0[1], p0[3]) - 14 * d - q * 22 * d / 1400;
            tim(g, tx + SIN[(q / 40) & 63] * 2 * d / 1024, ty, d);
        }
    }

    private static void veRong(Graphics g, f a, int x, int y, boolean phai) {
        if (a == null || a.a == null) {
            return;
        }
        Image im = a.a;
        if (phai) {
            g.drawImage(im, x, y, 3);
        } else {
            g.drawRegion(im, 0, 0, im.getWidth(), im.getHeight(), 2, x, y, 3);
        }
    }

    /** Vun khe roi tu mieng 2 rong khi nhai. */
    private static void vun(Graphics g, int q, int d, int x1, int y1, int x2, int y2) {
        for (int c = 0; c < 8; c++) {
            int bd = c * 360, tuoi = q - bd;
            if (tuoi < 0 || tuoi >= 650) {
                continue;
            }
            boolean xanh = (c & 1) == 0;
            int x = (xanh ? x1 : x2) + (((c * 37) % 7) - 3) * d + (xanh ? -1 : 1) * tuoi * 4 * d / 650;
            int y = (xanh ? y1 : y2) + 2 * d + tuoi * tuoi / 650 * 16 * d / 650;
            g.setColor((c % 3) == 0 ? 0xB4DC3C : 0xF5E15A);
            int sz = (tuoi < 400 ? 2 : 1) * d;
            g.fillRect(x, y, sz, sz);
        }
    }

    private static final String[] TIM = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};

    private static void tim(Graphics g, int x, int y, int d) {
        int x0 = x - 7 * d / 2;
        for (int r = 0; r < TIM.length; r++) {
            String s = TIM[r];
            for (int c = 0; c < s.length(); c++) {
                if (s.charAt(c) == 'X') {
                    g.setColor(r == 1 && c == 1 ? 0xFFFFFF : 0xFF4F86);
                    g.fillRect(x0 + c * d, y + r * d, d, d);
                }
            }
        }
    }

    /** Mau vet bay moi than thu (mot mau co dinh, khong nhap nhay). */
    private static final int[] MAU_VET = {0xA8F4FF, 0xFFC864, 0xF4ECFF, 0xFF8C5A};

    /** Vet bay: cac diem cu nho dan theo tuoi, dung yen cho, khong doi mau / khong rung. */
    private static void veVet(Graphics g, long t, int d, int k) {
        int n = vn[k] < VET ? vn[k] : VET;
        g.setColor(MAU_VET[k]);
        for (int i = 1; i < n; i++) {
            int j = (vn[k] - 1 - i) % VET;
            if (j < 0) {
                j += VET;
            }
            int sz = (4 - i * 4 / VET) * d;
            if (sz <= 0) {
                continue;
            }
            g.fillRect(vx[k][j] - sz / 2, vy[k][j] - sz / 2, sz, sz);
        }
    }

    /** Cay co thu ngan nam: vai chiec la gia roi cham, du dua (mau co dinh, khong nhap nhay). */
    private static void haoQuang(Graphics g, long t, int cx, int cy, int ra, int rb, int d) {
        for (int i = 0; i < 5; i++) {
            int chu = 4200 + i * 470;
            int p = (int) ((t + i * 917) % chu);
            int x = cx + (((i * 7919) % (2 * ra + 1)) - ra) + SIN[(p * 64 / chu * 2) & 63] * 3 * d / 1024;
            int y = cy - rb + p * (rb * 3) / chu;
            g.setColor((i & 1) == 0 ? 0x4C7E40 : 0x8C8A3C);
            g.fillRect(x, y, 2 * d, d);
            g.setColor((i & 1) == 0 ? 0x2E5A2A : 0x6A6A2C);
            g.fillRect(x + d, y + d, d, d);
        }
    }
}
