import javax.microedition.lcdui.Graphics;

/**
 * Ban native (j2c): ban phim dien thoai thay ban phim T9 cua game + d-pad cam ung muot hon.
 * Duoc goi tu cac lop client da va bang PatchNative (main.a, b).
 */
public final class KbHook {
    /** dang xin ban phim he thong (game o che do go chu) */
    static boolean shown;
    /** luc mo ban phim dang o o chat (im.c) -> chat dong thi tu tat ban phim + ban phim ao */
    static boolean chat;
    /** phim dang giu tren ban phim ao (0 = khong). Luu rieng vi game TAO LAI ban phim ao (main.a.z = new b())
     *  moi lan doi khu vuc / man hinh: giu nut di chuyen di vao cua -> ban phim moi co v = -1 -> lan tha bi mat,
     *  main.a.d[] van "dang giu" -> sang khu moi nhan vat tu chay thang mot huong (vd ve nong trai chay thang len cay). */
    static int held;

    static int cellOf(b p, int key) {
        if (p == null || p.u == null) {
            return -1;
        }
        for (int i = 0; i < p.u.length; i++) {
            if (i != 2 && p.u[i] == key) {
                return i;
            }
        }
        return -1;
    }

    static boolean isDir(int k) {
        return k == -1 || k == -2 || k == -3 || k == -4;
    }

    static boolean inPad(b p, int x, int y) {
        return x >= p.b && y >= p.c && x < p.b + p.d && y < p.c + p.e;
    }

    /** dau b.a() moi khung khi co ban phim ao. true = bo qua xu ly goc (go chu: ban phim dien thoai lo) */
    public static boolean tick(b p) {
        if (p.a) {
            if (!shown) {
                shown = true;
                chat = im.c;
                dx.m = true; // go thang ky tu (khong T9)
                j2n.Disp.wantKeyboard(1, main.a.n);
            } else if (chat && !im.c) {
                // da gui / dong o chat: tat che do go chu nhu khi bam lai bong chat (ban phim ao + ban phim dien thoai)
                chat = false;
                dm.d = false;
                dm.b().b[4] = 0;
                main.a.a.b(); // -> sync() tat ban phim dien thoai
            }
            return true;
        }
        if (shown) {
            shown = false;
            j2n.Disp.wantKeyboard(0, 0);
        }
        return false;
    }

    /** dau b.a(Graphics): o che do go chu khong ve ban phim T9 (to den vung do, nam duoi ban phim dien thoai) */
    public static boolean paint(b p, Graphics g) {
        if (!p.a) {
            return false;
        }
        int tx = g.getTranslateX(), ty = g.getTranslateY();
        g.translate(-tx, -ty);
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.setClip(0, 0, p.b + p.d + 8, p.c + p.e + 8);
        g.setColor(0);
        g.fillRect(0, main.a.n, p.b + p.d + 8, p.c + p.e + 8 - main.a.n);
        g.setClip(cx, cy, cw, ch);
        g.translate(tx, ty);
        return true;
    }

    /** cuoi main.a.b() (tao / xoa ban phim ao): het che do go chu thi tat ban phim dien thoai */
    public static void sync() {
        b p = main.a.z;
        if (held != 0 && p != null && !p.a && p.v < 0) {
            p.v = cellOf(p, held); // ban phim ao vua tao lai: van biet ngon tay dang giu o nao
        }
        if (shown && (p == null || !p.a)) {
            shown = false;
            chat = false;
            j2n.Disp.wantKeyboard(0, 0);
        }
    }

    /** ba.b() (hieu ung thoi tiet / hat): xoa phan tu 0 cua danh sach da rong -> truoc day nem loi MOI KHUNG
     *  (vong lap game bo do ca khung, in log lien tuc = giat). Rong thi bo qua, game tu tat hieu ung o dong sau. */
    public static void removeAt(java.util.Vector v, int i) {
        if (v != null && i >= 0 && i < v.size()) {
            v.removeElementAt(i);
        }
    }

    /** sau main.a.pointerDragged: truot ngon tren d-pad sang o huong khac -> doi huong ngay */
    public static void drag() {
        b p = main.a.z;
        if (held == 0 || !isDir(held) || p == null || p.a || p.u == null || p.f <= 0 || p.g <= 0 || p.l <= 0) {
            return;
        }
        if (!inPad(p, main.a.h, main.a.i)) {
            return;
        }
        int nv = (main.a.i - p.c) / p.g * p.l + (main.a.h - p.b) / p.f;
        if (nv < 0 || nv >= p.u.length || !isDir(p.u[nv]) || p.u[nv] == held) {
            return;
        }
        int old = held;
        p.v = nv;
        held = p.u[nv];
        main.a.a.keyReleased(old);
        main.a.a.keyPressed(held);
    }

    /** sau main.a.pointerPressed: nhan vao o d-pad -> nhan phim NGAY tren luong su kien.
     *  Truoc day ban phim ao chi xu ly co nhan/tha (main.a.e / f) 1 lan moi khung o dau vong lap; cham toi giua khung thi
     *  man ban do xu ly truoc (coi la cham ban do de di toi) va xoa co -> phim khong duoc nhan/nha, nhan vat tu chay. */
    public static void press() {
        b p = main.a.z;
        if (p == null || p.a || p.u == null || p.f <= 0 || p.g <= 0 || p.l <= 0) {
            return;
        }
        if (!inPad(p, main.a.h, main.a.i)) {
            return;
        }
        int v = (main.a.i - p.c) / p.g * p.l + (main.a.h - p.b) / p.f;
        if (v < 0 || v >= p.u.length || v == 2) { // o "ABC" (giu de doi kieu go): de game tu xu ly
            return;
        }
        if (held != 0 && held != p.u[v]) {
            main.a.a.keyReleased(held); // phim cu con giu (lo mat lan tha) -> nha truoc
        }
        p.v = v;
        held = p.u[v];
        main.a.e = false; // lan nhan nay thuoc ve d-pad, man ban do khong duoc coi la cham de di
        main.a.a.keyPressed(held);
    }

    /** sau main.a.pointerReleased: nhan bat dau trong d-pad -> nha phim NGAY (tha o dau cung dung lai) */
    public static void release() {
        b p = main.a.z;
        if (held != 0) {
            // nha DUNG phim da nhan, ke ca khi ban phim ao da bi tao lai / an giua chung (doi khu vuc, mo hop thoai...)
            int k = held;
            held = 0;
            if (p != null) {
                p.v = -1;
            }
            main.a.f = false; // lan tha thuoc ve d-pad
            main.a.a.keyReleased(k);
            return;
        }
        if (p == null || p.a || p.u == null) {
            return;
        }
        if (!inPad(p, main.a.j, main.a.k)) {
            return;
        }
        if (p.v >= 0 && p.v < p.u.length && p.v != 2) {
            int k = p.u[p.v];
            p.v = -1;
            main.a.f = false; // lan tha thuoc ve d-pad
            main.a.a.keyReleased(k);
            return;
        }
        if (p.v >= 0 && p.f > 0 && p.g > 0 && p.l > 0 && !inPad(p, main.a.h, main.a.i)) {
            // o ABC: dua diem tha ve o dang nhan de game xu ly nhu cu
            main.a.h = p.b + (p.v % p.l) * p.f + p.f / 2;
            main.a.i = p.c + (p.v / p.l) * p.g + p.g / 2;
        }
    }
}
