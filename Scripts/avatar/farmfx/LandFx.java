import java.io.DataInputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * O dat nong trai 10 cap: 1 thuong, 2 dat xop (tile goc), 3 dong, 4 bac, 5 vang, 6 ngoc luc bao, 7 hong ngoc, 8 lam ngoc,
 * 9 tu ngoc, 10 kim cuong cau vong (doi mau lien tuc). Tu cap 5: hat lap lanh + vet sang chay qua.
 * - May chu gui cap that bang lenh 118 ngay sau goi vao nong trai (goi 61 chi gui toi da 2 de app cu van gieo duoc).
 * - App goc chi biet cap 1 / 2: luat gieo hat va chon hinh dat so sanh p == 1 / p == 2, luat nang cap chi cho o dau
 *   khi o dau con cap 1. PatchFarmFx sua cac cho do trong lop go: p dung cho gieo / hinh dat qua logic(), luat nang cap
 *   o dau qua dau() (moi o cung cap va duoi cap 5 -> nang tiep duoc toi cap 5).
 * - Hinh: lay chinh o dat trong bo tile cua ban do, doi mau theo do sang (giu nguyen hoa van dat), nen dep tu nhien.
 */
public final class LandFx {

    public static final int CAP_MAX = 10;
    private static final int MAP_NONG_TRAI = 24;
    /** 3 nac mau (toi, giua, sang): cap 3..9, roi 6 mau cau vong cho cap 10 */
    private static final int[][] MAU = {
            {0x5A2814, 0xBE6432, 0xF5B478},     // 3 dong
            {0x50555F, 0xAAB0BC, 0xF0F4FC},     // 4 bac
            {0x6E460A, 0xE1AA28, 0xFFF096},     // 5 vang anh kim
            {0x0A4628, 0x28B46E, 0xA0FFD2},     // 6 ngoc luc bao
            {0x500A1E, 0xD2284B, 0xFFAABE},     // 7 hong ngoc
            {0x0A1E5A, 0x2864D2, 0xAAD2FF},     // 8 lam ngoc
            {0x320A50, 0x8C3CD2, 0xE6BEFF},     // 9 tu ngoc
            {0x5A0A1E, 0xF03C50, 0xFFC8C8}, {0x5A320A, 0xF0961E, 0xFFE6A0}, {0x464600, 0xE6DC28, 0xFFFFB4},
            {0x0A4628, 0x32C86E, 0xBEFFD2}, {0x0A2864, 0x3C78F0, 0xBED7FF}, {0x3C0A5A, 0xA050E6, 0xF0C8FF}};

    private static final Hashtable anh = new Hashtable();

    private LandFx() {
    }

    // ---------------------------------------------------------------- tin tu may chu

    /** kz.a(jr): lenh 118 = cap that cua tung o dat. true = da xu ly (khong cho lop goc doc nua). */
    public static boolean tin(jr m) {
        if (m == null || m.a != 118) {
            return false;
        }
        try {
            DataInputStream d = m.b();
            int n = d.readByte() & 0xFF;
            Vector v = go.c;
            for (int i = 0; i < n; i++) {
                byte cap = d.readByte();
                if (v != null && i < v.size()) {
                    Object o = v.elementAt(i);
                    if (o instanceof cf) {
                        ((cf) o).p = cap;
                    }
                }
            }
        } catch (Throwable t) {
        }
        return true;
    }

    // ---------------------------------------------------------------- luat (thay cho cf.p trong lop go)

    /** Cap dung cho luat gieo hat / chon hinh dat goc: cap 2 tro len deu la "dat xop" (bo tile cap 2). */
    public static int logic(cf c) {
        return c.p >= 2 ? 2 : c.p;
    }

    /** Thay "o dau con cap 1" trong luat nang cap: 1 neu moi o cung cap va chua toi cap 5 (bat dau dot nang moi). */
    public static int dau(cf c) {
        if (c == null || c.p >= CAP_MAX) {
            return 0;
        }
        Vector v = go.c;
        if (v != null) {
            for (int i = 0; i < v.size(); i++) {
                Object o = v.elementAt(i);
                if (o instanceof cf && ((cf) o).p != c.p) {
                    return 0;
                }
            }
        }
        return 1;
    }

    // ---------------------------------------------------------------- ve

    /** ir: thay cho ir.c.b(cot, hang, x, y, g) khi ve tile ban do. Ve tile goc roi phu mau neu la o dat cap 3+. */
    public static void oDat(ht h, int cot, int hang, int x, int y, Graphics g) {
        h.b(cot, hang, x, y, g);
        if (ir.a != MAP_NONG_TRAI) {
            return;
        }
        Vector v = go.c;
        if (v == null) {
            return;
        }
        int s = ir.i * gy.Y;
        if (s <= 0) {
            return;
        }
        int tc = x / s, th = y / s;
        cf c = null;
        for (int i = 0; i < v.size(); i++) {
            Object o = v.elementAt(i);
            if (o instanceof cf && ((cf) o).a == tc && ((cf) o).b == th) {
                c = (cf) o;
                break;
            }
        }
        if (c == null || c.p < 3) {
            return;
        }
        int m = c.p >= CAP_MAX ? 7 + (int) ((System.currentTimeMillis() / 420 + tc + th) % 6) : Math.min(6, c.p - 3);
        Image im = toMau(h, cot, hang, m);
        if (im != null) {
            g.drawImage(im, x, y, 0);
        }
        if (c.p >= 5) {
            lapLanh(g, x, y, s, tc, th, c.p >= CAP_MAX ? 5 : 3);
        }
    }

    private static int tron(int a, int b, int t) {
        return a + (b - a) * t / 255;
    }

    private static Image toMau(ht h, int cot, int hang, int m) {
        Integer k = new Integer((cot * 4096 + hang) * 16 + m);
        Object o = anh.get(k);
        if (o instanceof Image) {
            return (Image) o;
        }
        if (o != null) {
            return null;
        }
        try {
            int w = h.a, hh = h.b;
            int[] px = new int[w * hh];
            h.d.getRGB(px, 0, w, cot * w, hang * hh, w, hh);
            int[] p = MAU[m];
            for (int i = 0; i < px.length; i++) {
                int a = px[i] >>> 24;
                if (a == 0) {
                    continue;
                }
                int r = (px[i] >> 16) & 255, gg = (px[i] >> 8) & 255, b = px[i] & 255;
                int l = (r * 30 + gg * 59 + b * 11) / 100;
                l = (l - 25) * 255 / 175;                       // tang tuong phan: dat toi -> ra mau kim loai ro
                if (l < 0) {
                    l = 0;
                } else if (l > 255) {
                    l = 255;
                }
                int c1, c2, t;
                if (l < 128) {
                    c1 = p[0];
                    c2 = p[1];
                    t = l * 2;
                } else {
                    c1 = p[1];
                    c2 = p[2];
                    t = (l - 128) * 2;
                }
                int nr = tron((c1 >> 16) & 255, (c2 >> 16) & 255, t);
                int ng = tron((c1 >> 8) & 255, (c2 >> 8) & 255, t);
                int nb = tron(c1 & 255, c2 & 255, t);
                px[i] = (a << 24) | (nr << 16) | (ng << 8) | nb;
            }
            Image im = Image.createRGBImage(px, w, hh, true);
            anh.put(k, im);
            return im;
        } catch (Throwable e) {
            anh.put(k, new Object());
            return null;
        }
    }

    /** Tu cap 5: vai hat lap lanh (sang dan roi tat) o vi tri rieng cua moi o + 1 vet sang cheo chay qua moi 3 giay. */
    private static void lapLanh(Graphics g, int x, int y, int s, int tc, int th, int soHat) {
        long t = System.currentTimeMillis();
        int seed = (tc * 7919 + th * 104729) & 0x7fffffff;
        int d = gy.Y > 0 ? gy.Y : 1;
        for (int i = 0; i < soHat; i++) {
            int chu = 1500 + ((seed >> (i * 3)) & 7) * 170;
            int pha = (int) ((t + ((seed >> (i * 5)) & 1023) * 3) % chu);
            int song = chu / 3;
            if (pha >= song) {
                continue;
            }
            int to = pha < song / 2 ? pha : song - pha;              // 0..song/2
            int sz = (1 + to * 4 / (song / 2 + 1)) * d;              // 1..5
            int px = x + 3 * d + ((seed / (i + 3)) * 31 & 0x7fffffff) % Math.max(1, s - 6 * d);
            int py = y + 3 * d + ((seed / (i + 5)) * 17 & 0x7fffffff) % Math.max(1, s - 6 * d);
            g.setColor(0xFFFFF0);
            g.fillRect(px - sz / 2, py, sz, d);
            g.fillRect(px, py - sz / 2, d, sz);
            if (sz >= 3 * d) {
                g.setColor(0xFFF6B0);
                g.fillRect(px - d, py - d, 2 * d + 1, 2 * d + 1);
            }
        }
        int chu = 3000;
        int p = (int) ((t + tc * 420 + th * 260) % chu);
        if (p < 700) {
            int dd = p * (s * 2) / 700;
            g.setColor(0xFFF8C8);
            for (int j = 0; j < s; j += d) {
                int xx = x + dd - j;
                if (xx >= x && xx < x + s - d) {
                    g.fillRect(xx, y + j, d, d);
                }
            }
        }
    }
}
