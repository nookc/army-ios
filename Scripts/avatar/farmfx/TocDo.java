import javax.microedition.lcdui.Canvas;

/**
 * Toc do game x1..x10 (Cai dat > "Toc do game", hang thu 5 truoc day an - "Ngon ngu").
 * PatchFarmFx sua vong lap main.a.run(): khung hinh 50 ms -> TocDo.khung(), this.repaint() -> TocDo.ve(this),
 * 2 lenh Thread.sleep cuoi vong -> TocDo.ngu(ms). x = 1 giong het ban goc. Luu trong RMS "avatarTocDo".
 * x > 1: logic chay x vong moi 50 ms theo LICH dong ho (khong troi), con viec ve thi theo suc may:
 *  - ve khi logic da kip dong ho, nhanh nhat ~60 khung / giay (app: khop nhip quet man hinh, moi nhip 1 khung)
 *  - logic bi cham (ve nang) -> chay bu cac vong logic lien tiep khong ve, khong ngu; van ve it nhat 10 khung / giay
 * Truoc day: ve 1 trong x vong (x4..x7 chi con ~20 khung / giay ma moi khung nhay 4..7 buoc -> giat).
 * CLDC 1.0: khong co so thuc - lich tinh bang micro giay (long).
 */
public final class TocDo {

    public static int x = 1;
    private static boolean daDoc;
    /** thoi diem (micro giay) vong logic ke tiep den han (x > 1) */
    private static long lich;
    /** ms lan xin ve gan nhat */
    private static long veLuc;
    /** 2 lan ve cach nhau it nhat (ms): ~60 khung / giay */
    private static final long VE_NHANH = 15;
    /** logic khong kip dong ho van ve it nhat 1 lan moi 100 ms */
    private static final long VE_CHAM = 100;

    private TocDo() {
    }

    /** thay hang so 50 (ms moi khung) trong vong lap game */
    public static long khung() {
        doc();
        return x <= 1 ? 50 : 50 / x;
    }

    /** thay Canvas.repaint() trong vong lap game (sau phan logic cua vong) */
    public static void ve(Canvas c) {
        if (HdUi.GO) {
            HdUi.goSauLogic();
        }
        if (HdUi.THU_LOI && main.a.l % 40 == 5) {
            throw new RuntimeException("thu loi logic (J2N_THULOI)");       // CHI ban thu Linux: kiem TocDo.loiVong
        }
        if (x <= 1) {
            c.repaint();
            return;
        }
        long bay = System.currentTimeMillis();
        long nay = bay * 1000L;
        if (lich <= 0 || nay - lich > 250000L || lich - nay > 250000L) {
            lich = nay;                       // vua doi toc do / app ra nen / may treo lau: tinh lai lich tu bay gio
        }
        lich += 50000L / x;                   // vong logic vua chay xong -> han vong ke tiep
        long cach = bay - veLuc;
        if (cach < 0) {
            cach = VE_CHAM;                   // dong ho bi chinh lui
        }
        if ((lich >= nay && cach >= VE_NHANH) || cach >= VE_CHAM) {
            veLuc = bay;
            c.repaint();
        }
    }

    private static int loiDem;
    private static long loiLuc;

    /**
     * Thay Throwable.printStackTrace() trong vong lap game (main.a.run). Game goc: 1 loi o phan logic la bo luon ca phan con
     * lai cua vong - khong xoa co "vua cham / vua nha", khong ve, KHONG NGU. Loi lap lai moi vong (vd co cham chua duoc xoa)
     * thi vong lap quay het toc luc: man dung hinh, may nong, nhat ky day (da gap o nong trai: cham o dat chua mua). O day:
     * ghi loi (co gioi han), xoa co cham nhu cuoi vong binh thuong, van xin ve va ngu 1 nhip -> game khong bao gio "dung
     * hinh quay vong" vi 1 loi logic.
     */
    public static void loiVong(Throwable e) {
        try {
            loiDem++;
            long nay = System.currentTimeMillis();
            if (loiDem <= 20 || nay - loiLuc >= 5000) {
                loiLuc = nay;
                Object man = main.a.r;
                System.out.println("[vong] LOI LOGIC #" + loiDem + " man " + (man == null ? "null" : man.getClass().getName()) + " ban do "
                        + ir.a + ": " + e);
                if (loiDem <= 3 && e != null) {
                    e.printStackTrace();
                }
            }
            main.a.g = false;
            main.a.f = false;
            Canvas c = main.a.a;
            if (c != null) {
                c.repaint();
            }
            Thread.sleep(40);
        } catch (Throwable t) {
        }
    }

    /** thay Thread.sleep(ms) cuoi vong lap game */
    public static void ngu(long ms) throws InterruptedException {
        if (x <= 1) {
            Thread.sleep(ms);
            return;
        }
        long con = (lich - System.currentTimeMillis() * 1000L) / 1000L;
        if (con >= 1) {
            Thread.sleep(con > 50 ? 50 : con);
        } else {
            Thread.yield();                   // logic dang cham hon dong ho: chay bu vong ke tiep ngay
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
        lich = 0;
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
