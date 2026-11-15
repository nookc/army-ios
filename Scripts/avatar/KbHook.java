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
        if (p == null || p.a || p.v < 0 || p.u == null || p.v >= p.u.length) {
            return;
        }
        if (!inPad(p, main.a.j, main.a.k) || !inPad(p, main.a.h, main.a.i) || p.f <= 0 || p.g <= 0) {
            return;
        }
        int nv = (main.a.i - p.c) / p.g * p.l + (main.a.h - p.b) / p.f;
        if (nv == p.v || nv < 0 || nv >= p.u.length || !isDir(p.u[p.v]) || !isDir(p.u[nv])) {
            return;
        }
        int old = p.u[p.v];
        p.v = nv;
        main.a.a.keyReleased(old);
        main.a.a.keyPressed(p.u[nv]);
    }

    /** sau main.a.pointerReleased: nhan trong d-pad ma tha tay ngoai -> dua diem tha ve o dang nhan de game nha phim */
    public static void release() {
        b p = main.a.z;
        if (p == null || p.a || p.v < 0 || p.f <= 0 || p.g <= 0 || p.l <= 0) {
            return;
        }
        if (!inPad(p, main.a.j, main.a.k) || inPad(p, main.a.h, main.a.i)) {
            return;
        }
        main.a.h = p.b + (p.v % p.l) * p.f + p.f / 2;
        main.a.i = p.c + (p.v / p.l) * p.g + p.g / 2;
    }
}
