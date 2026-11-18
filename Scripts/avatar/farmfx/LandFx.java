import java.io.DataInputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * O dat nong trai 10 cap, cap cang cao dat cang tot (mau dat that, khong mau me): 1 thuong, 2 dat xop (tile goc),
 * 3 dat thit, 4 dat phu sa, 5 dat do bazan, 6 dat mun, 7 dat mun toi xop, 8 dat den, 9 dat den mau mo, 10 dat Than Nong.
 * Moi cap mot mau co dinh, giu nguyen vien co xanh cua tile goc, khong doi mau / khong nhap nhay.
 * - May chu gui cap that bang lenh 118 ngay sau goi vao nong trai (goi 61 chi gui toi da 2 de app cu van gieo duoc).
 * - App goc chi biet cap 1 / 2: luat gieo hat va chon hinh dat so sanh p == 1 / p == 2, luat nang cap chi cho o dau
 *   khi o dau con cap 1. PatchFarmFx sua cac cho do trong lop go: p dung cho gieo / hinh dat qua logic(), luat nang cap
 *   o dau qua dau() (moi o cung cap va duoi cap 5 -> nang tiep duoc toi cap 5).
 * - Hinh: lay chinh o dat trong bo tile cua ban do, doi mau theo do sang (giu nguyen hoa van dat), nen dep tu nhien.
 */
public final class LandFx {

    public static final int CAP_MAX = 10;
    private static final int MAP_NONG_TRAI = 24;
    /** 3 nac mau dat (toi, giua, sang) cho cap 3..10: dam dan, mau mo dan */
    private static final int[][] MAU = {
            {0x4D3112, 0x9A6224, 0xD78932},     // 3 dat thit
            {0x472713, 0x8E4E26, 0xC66D35},     // 4 dat phu sa
            {0x432015, 0x87412A, 0xBD5B3A},     // 5 dat do bazan
            {0x352110, 0x6A4220, 0x9F6330},     // 6 dat mun
            {0x2F170C, 0x5E2E18, 0x914725},     // 7 dat mun toi xop
            {0x23170E, 0x4E3420, 0x7C5333},     // 8 dat den
            {0x1B120A, 0x3E2818, 0x6C462A},     // 9 dat den mau mo
            {0x160D08, 0x382216, 0x75472E}};    // 10 dat Than Nong

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
        int m = Math.max(0, Math.min(MAU.length - 1, c.p - 3));
        Image im = toMau(h, cot, hang, m);
        if (im != null) {
            g.drawImage(im, x, y, 0);
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
                if (gg > r + 12 && gg > b + 12) {
                    continue;                                   // vien co xanh cua tile goc: giu nguyen
                }
                int l = (r * 30 + gg * 59 + b * 11) / 100;
                l = (l - 15) * 255 / 190;
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

}
