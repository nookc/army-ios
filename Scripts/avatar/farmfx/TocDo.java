import javax.microedition.lcdui.Canvas;

/**
 * Toc do game x1..x10 (Cai dat > "Toc do game", hang thu 5 truoc day an - "Ngon ngu").
 * PatchFarmFx sua vong lap main.a.run(): khung hinh 50 ms -> TocDo.khung() (50 / x ms), this.repaint() ->
 * TocDo.ve(this) (x > 1: logic chay du moi khung, chi ve 1 trong x khung). Ket qua: di lai, hieu ung, NPC... nhanh gap x
 * lan ma may khong phai ve them. x = 1 giong het ban goc. Luu trong RMS "avatarTocDo".
 */
public final class TocDo {

    public static int x = 1;
    private static int dem;
    private static boolean daDoc;

    private TocDo() {
    }

    /** thay hang so 50 (ms moi khung) trong vong lap game */
    public static long khung() {
        doc();
        return 50 / x;
    }

    /** thay Canvas.repaint() trong vong lap game */
    public static void ve(Canvas c) {
        if (x <= 1 || ++dem >= x) {
            dem = 0;
            c.repaint();
        }
    }

    /** dau dm.e() (dung man Cai dat): hien hang "Toc do game" */
    public static void caiDat(dm d) {
        doc();
        if (d.l != null && d.l.length > 4) {
            d.l[4] = true;
        }
        if (d.b != null && d.b.length > 4) {
            d.b[4] = 0;
        }
        nhan();
    }

    /** dau dm.e(int) (doi gia tri dong dang chon): dong "Toc do game" -> x1..x10 (vong) */
    public static boolean doi(dm d, int n) {
        if (d.g != 4) {
            return false;
        }
        x += n > 0 ? 1 : -1;
        if (x < 1) {
            x = 10;
        }
        if (x > 10) {
            x = 1;
        }
        dem = 0;
        if (d.b != null && d.b.length > 4) {
            d.b[4] = 0;
        }
        nhan();
        try {
            aw.a("avatarTocDo", new byte[]{(byte) x});
        } catch (Throwable t) {
        }
        return true;
    }

    private static void nhan() {
        String s = "x" + x;
        kf.eG[4] = new String[]{s, s, "Tốc độ game"};
    }

    private static void doc() {
        if (daDoc) {
            return;
        }
        daDoc = true;
        try {
            byte[] b = aw.a("avatarTocDo");
            if (b != null && b.length > 0 && b[0] >= 1 && b[0] <= 10) {
                x = b[0];
            }
        } catch (Throwable t) {
        }
    }
}
