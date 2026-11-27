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

    /*
     * Giao dien 3D (ban HD): PatchFarmFx doi cac cho goi ham ve khung cua giao dien (ew) sang cac ham duoi day - ve bong do
     * quanh khung truoc, goi ham goc, roi phu lop bong sang (kinh) len tren. May ve (Graphics cua app native) nhan kieu ve
     * qua setStrokeStyle(0x4861..0x4863) cho lan fillRoundRect ke tiep: 1 = bong do, 2 = lop bong sang, 3 = nut kinh toi.
     */
    private static void kieu(Graphics g, int k, int x, int y, int w, int h, int r, int mau) {
        try {
            int cu = g.getColor();
            g.setStrokeStyle(0x4860 + k);
            g.setColor(mau);
            g.fillRoundRect(x, y, w, h, r * 2, r * 2);
            g.setColor(cu);
        } catch (Throwable e) {
        }
    }

    /** hop thoai (dx): ew.a(Graphics, x, y, w, h, dx, boolean) */
    public static void hopThoai(ew o, Graphics g, int x, int y, int w, int h, dx d, boolean b) {
        boolean hd = hd();
        if (hd) {
            kieu(g, 1, x, y, w, h, 5, 0);
        }
        o.a(g, x, y, w, h, d, b);
        if (hd) {
            kieu(g, 2, x, y, w, h, 5, 0);
        }
    }

    /** khung cua so: ew.a(Graphics, x, y, w, h, mau, mau, kieu) */
    public static void khung7(ew o, Graphics g, int x, int y, int w, int h, int a, int b, int c) {
        boolean hd = hd();
        if (hd) {
            kieu(g, 1, x, y, w, h, 5, 0);
        }
        o.a(g, x, y, w, h, a, b, c);
        if (hd) {
            kieu(g, 2, x, y, w, h, 5, 0);
        }
    }

    /** menu / danh sach noi: ew.a(Graphics, x, y, CAO, RONG, ...) - chu y thu tu cao truoc rong */
    public static void menu(ew o, Graphics g, int x, int y, int h, int w, int a5, int a6, int a7, int a8, int a9, int a10, int a11,
            int[] m, int[] n, String s) {
        boolean hd = hd();
        if (hd) {
            kieu(g, 1, x, y, w, h, 5, 0);
        }
        o.a(g, x, y, h, w, a5, a6, a7, a8, a9, a10, a11, m, n, s);
        if (hd) {
            kieu(g, 2, x, y, w, h, 5, 0);
        }
    }

    /** 3 phim mem (trai / giua / phai): ban HD ve nut kinh toi bo tron sau chu */
    public static void phimMem(ew o, Graphics g, by l, by c, by r) {
        if (hd()) {
            try {
                int cao = 19, T = main.a.T, dem = 7;
                for (int i = 0; i < 3; i++) {
                    by p = i == 0 ? l : i == 1 ? c : r;
                    if (p == null || p.a == null || p.a.length() == 0 || main.a.ae == null || main.a.ae[i] == null) {
                        continue;
                    }
                    int tw = main.a.L.a(p.a);
                    int y = main.a.ae[i].b + T / 2 - cao / 2 - 1;
                    int x;
                    if (i == 0) {
                        x = main.a.ae[0].a + 2 - dem;
                    } else if (i == 1) {
                        x = main.a.ae[1].a + hj.au / 2 - tw / 2 - dem;
                    } else {
                        x = main.a.ae[2].a + hj.au - 2 - tw - dem;
                    }
                    int w = tw + dem * 2;
                    if (x < 1) {
                        w -= 1 - x;
                        x = 1;
                    }
                    if (x + w > main.a.m - 1) {
                        w = main.a.m - 1 - x;
                    }
                    kieu(g, 3, x, y, w, cao, cao / 2, 0x16324A);
                }
            } catch (Throwable e) {
            }
        }
        o.a(g, l, c, r);
    }

    /**
     * gy.l(): dai phim mem duoi day man hinh (ca be ngang, chia 3). Ban HD (man ngang, ban do cao gan bang man): chi nhan
     * cham quanh 3 nhan (goc trai, giua, goc phai) - phan con lai cua dai de cham vao ban do (o cua ra, vat the o hang
     * duoi cung khong bi phim mem che).
     */
    public static boolean vungPhimMem(int x, int y, int w, int h) {
        if (!main.a.b(x, y, w, h)) {
            return false;
        }
        if (!hd()) {
            return true;
        }
        int px = main.a.h, W = main.a.m;
        return px < 86 || px > W - 86 || (px > W / 2 - 26 && px < W / 2 + 26);
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
            cauTick();
        }
    }

    /*
     * Cau ca bang cham (ban HD khong co ban phim ao). Game goc: cho cau la cac tang da ven dao (o loai 54) - quang can khi
     * nhan vat BUOC vao tang da (bam phim huong ve phia no: ir.a(x, y) -> kv.b). Cham vao tang da thi nhan vat khong di
     * vao duoc nen khong bao gio quang. Ban HD: cham vao tang da (hoac sat no) o khu cau ca (ban do 14 / 15 / 16) ->
     * nhan vat di toi o dat ke ben, dung lai roi tu "buoc" vao tang da y nhu bam phim huong.
     * ChamVat.cham() goi chamNuoc() luc game nhan diem cham (truoc khi dat diem di toi); trenTg() goi cauTick() moi khung.
     */
    private static final int O_CAU = 54, O_DAT = 80;
    private static boolean cauCho;
    private static int cauX, cauY, cauGx, cauGy, cauPx, cauPy;
    private static long cauTu, cauDung;

    private static int oLoai(int cot, int hang) {
        return ir.c(cot * ir.i + ir.i / 2, hang * ir.i + ir.i / 2);
    }

    public static void chamNuoc() {
        cauCho = false;
        try {
            if (!hd() || (ir.a != 14 && ir.a != 15 && ir.a != 16) || main.GameMidlet.i == null || ir.u == null || ir.i <= 0) {
                return;
            }
            int s = js.ay <= 0 ? 1 : js.ay, T = ir.i;
            int tx = ir.u.a / s, ty = ir.u.b / s;
            int cot = tx / T, hang = ty / T;
            // tang da gan diem cham nhat trong 3 x 3 o
            int fc = -1, fh = -1;
            long gan = Long.MAX_VALUE;
            for (int dh = -1; dh <= 1; dh++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (oLoai(cot + dc, hang + dh) != O_CAU) {
                        continue;
                    }
                    long ex = (cot + dc) * T + T / 2 - tx, ey = (hang + dh) * T + T / 2 - ty, d = ex * ex + ey * ey;
                    if (d < gan) {
                        gan = d;
                        fc = cot + dc;
                        fh = hang + dh;
                    }
                }
            }
            if (fc < 0) {
                return;
            }
            // o dat ke ngang tang da (trai / phai) de dung
            hn me = main.GameMidlet.i;
            int gc = -1;
            if (oLoai(fc + 1, fh) == O_DAT && oLoai(fc - 1, fh) == O_DAT) {
                gc = me.aw >= fc * T + T / 2 ? fc + 1 : fc - 1;
            } else if (oLoai(fc + 1, fh) == O_DAT) {
                gc = fc + 1;
            } else if (oLoai(fc - 1, fh) == O_DAT) {
                gc = fc - 1;
            }
            if (gc < 0) {
                return;
            }
            cauX = gc > fc ? (fc + 1) * T - 6 : fc * T + 6;       // diem "buoc vao" tang da tu phia nguoi dung
            cauY = fh * T + T / 2;
            cauGx = gc > fc ? gc * T + 5 : (gc + 1) * T - 5;      // dung sat mep tang da
            cauGy = fh * T + T / 2;
            ir.u.a = cauGx * s;                                   // game se di toi day (thay cho diem cham tren da)
            ir.u.b = cauGy * s;
            cauPx = me.aw;
            cauPy = me.ax;
            cauTu = System.currentTimeMillis();
            cauDung = cauTu;
            cauCho = true;
        } catch (Throwable e) {
            cauCho = false;
        }
    }

    private static void cauTick() {
        if (!cauCho) {
            return;
        }
        try {
            hn me = main.GameMidlet.i;
            long now = System.currentTimeMillis();
            if (me == null || (ir.a != 14 && ir.a != 15 && ir.a != 16) || now - cauTu > 9000) {
                cauCho = false;
                return;
            }
            if (me.aw != cauPx || me.ax != cauPy) {
                cauPx = me.aw;              // con dang di: doi dung han
                cauPy = me.ax;
                cauDung = now;
                return;
            }
            if (now - cauDung < 300 || main.a.v != null || main.a.A != null || main.a.x == null) {
                return;
            }
            cauCho = false;
            int T = ir.i;
            int dx = me.aw - cauGx, dy = me.ax - cauGy;
            if (dx > T || dx < -T || dy > T || dy < -T) {
                return;                     // khong toi duoc canh tang da
            }
            main.a.x.a(cauX, cauY);
        } catch (Throwable e) {
            cauCho = false;
        }
    }
}
