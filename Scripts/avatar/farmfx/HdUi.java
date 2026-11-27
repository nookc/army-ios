import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Ban "Avatar 250 HD" (app native bat may ve net cao: System.getProperty("j2n.hdk") khac null): giao dien cham truc tiep.
 * - Khong co nut di chuyen ao: an nut bat / tat ban phim ao o goc trai tren (gw.a(Graphics) ve hj.ap; gy.l() bat vung
 *   cham 50x50), khong tu bat ban phim ao o khu cau ca (gw.a(int) voi map 14 / 15 / 16).
 * - Bong chat (hj.aq) doi sang cho nut cu.
 * Ban thuong va ban J2ME (khong co thuoc tinh j2n.hdk): moi ham giu nguyen hanh vi goc.
 * PatchFarmFx noi cac ham nay vao gw / gy.
 */
public final class HdUi {
    private static int hd = -1;
    /** tam bong chat o ban HD (ban goc: 75) */
    static final int CHAT_X = 44;

    private HdUi() {
    }

    public static boolean hd() {
        if (hd < 0) {
            String s = null;
            try {
                s = System.getProperty("j2n.hdk");
            } catch (Throwable e) {
            }
            hd = s != null ? 1 : 0;
        }
        return hd == 1;
    }

    /** gw.a(Graphics): nut bat / tat ban phim ao */
    public static void vePhimAo(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            return;
        }
        g.drawImage(img, x, y, neo);
    }

    /** gw.a(Graphics): bong chat */
    public static void veChat(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            x = CHAT_X;
        }
        g.drawImage(img, x, y, neo);
    }

    /** gy.l(): vung cham bat / tat ban phim ao (0, 0, 50, 50) */
    public static boolean vungPhimAo(int x, int y, int w, int h) {
        if (hd()) {
            return false;
        }
        return main.a.a(x, y, w, h);
    }

    /** gy.l(): vung cham bong chat (50, 0, 50, 50) */
    public static boolean vungChat(int x, int y, int w, int h) {
        if (hd()) {
            x = CHAT_X - 25;
        }
        return main.a.a(x, y, w, h);
    }

    /** gw.a(int map): ban goc tu bat ban phim ao o map 14 / 15 / 16 (khu cau ca). Ban HD: khong bao gio */
    public static int mapPhim(int map) {
        return hd() ? -1 : map;
    }

    /*
     * Ve the gioi (ban do + vat the + nhan vat) cua moi man khu: ir.b(g) = nen, ... cac vat the ..., ir.d(g) = lop tren.
     * Ban HD bao cho may ve biet doan nay (Graphics.setStrokeStyle voi ma rieng): anh ve trong doan nay co khoi noi + bong
     * do, ve xong thi lam hau ky canh (loa sang, tia nang, toi goc, bui sang) truoc khi ve ten / HUD.
     * PatchFarmFx doi moi cho goi ir.b / ir.d sang 2 ham nay.
     */
    public static void nenTg(ir o, Graphics g) {
        o.b(g);         // nen (o gach mat dat): giu phang, khong do bong
        if (hd()) {
            try {
                g.setStrokeStyle(0x4850);
            } catch (Throwable e) {
            }
        }
    }

    public static void trenTg(ir o, Graphics g) {
        o.d(g);
        if (hd()) {
            try {
                g.setStrokeStyle(0x4851);
            } catch (Throwable e) {
            }
        }
    }
}
