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

    /**
     * main.a.b() vua tao ban phim ao (main.a.z) - game vao che do go chu (bam bong chat). Ban HD go bang ban phim dien thoai:
     * tra lai kich thuoc man hinh day du (ban phim ao man ngang da lay 1/3 ben phai) va cho vung ban phim ao bang 0 de no
     * khong nuot cham vao phim mem / ban do.
     */
    public static void sauPhimAo() {
        if (!hd()) {
            return;
        }
        try {
            b p = main.a.z;
            if (p == null) {
                return;
            }
            main.a.m = main.a.a.getWidth();
            main.a.n = main.a.a.getHeight();
            main.a.G = false;
            p.b = 30000;
            p.c = 30000;
            p.d = 0;
            p.e = 0;
        } catch (Throwable e) {
        }
    }

    /**
     * KbHook: day vung game phai nam ngay tren ban phim dien thoai (diem logic, tinh tu dinh man hinh). Ban thuong: ca man
     * (main.a.n). Ban HD man ngang: ban phim che hon nua man hinh nen o DANG GO cua form (dang nhap, dang ky...) phai nam
     * ngay tren ban phim; o chat thi giu ca hang phim mem (Chat / Xoa) ben duoi no.
     */
    public static int vungPhim(dx o) {
        int n = main.a.n;
        if (!hd() || o == null) {
            return n;
        }
        try {
            if (im.c || (im.a != null && im.a.b == o)) {
                return n;
            }
            int y = o.b + o.d + 12;
            return y < 40 ? 40 : y > n ? n : y;
        } catch (Throwable e) {
            return n;
        }
    }

    // ---- Ban HD khong co nut "Aa": cham vao o nhap chu cua form (dang nhap, dang ky, ma qua...) thi tu xin ban phim dien
    // thoai; cham ra ngoai o / form dong thi tra. (O chat di duong ban phim ao cua game -> KbHook lo.) formTick() chay moi
    // khung (ve12); cham() ghi diem cham.
    private static long chamLuc;
    private static int chamFx, chamFy;
    private static boolean formPhim;
    private static dx formO;
    private static int formKieu;

    /**
     * bat (kieu 1 chu thuong / 2 mat khau) / tat (0) ban phim dien thoai; vung = day vung game phai nam tren ban phim. Di
     * qua System.getProperty de lop nay khong dinh vao lop rieng cua app native (ban J2ME khong co j2n.*).
     */
    private static void xinPhim(int kieu, int vung) {
        try {
            System.getProperty("j2n.phim:" + kieu + ":" + vung);
        } catch (Throwable e) {
        }
    }

    private static boolean trongO(dx o, int x, int y) {
        return x >= o.a - 4 && x < o.a + o.c + 4 && y >= o.b - 4 && y < o.b + o.d + 4;
    }

    private static void formAn() {
        formPhim = false;
        formO = null;
        xinPhim(0, 0);
    }

    static void formTick() {
        try {
            if (main.a.z != null) {
                // dang o che do ban phim ao cua game (o chat): KbHook quan ly ban phim
                formPhim = false;
                formO = null;
                chamLuc = 0;
                return;
            }
            dx o = KeyTap.oDangGo();
            long now = System.currentTimeMillis();
            if (!formPhim) {
                if (chamLuc == 0) {
                    return;
                }
                if (now - chamLuc > 600) {
                    chamLuc = 0;
                } else if (o != null && trongO(o, chamFx, chamFy)) {
                    chamLuc = 0;
                    formPhim = true;
                    formO = o;
                    formKieu = o.H == 2 ? 2 : 1;
                    dx.m = true;        // go thang ky tu (khong T9)
                    xinPhim(formKieu, vungPhim(o));
                }
                return;
            }
            if (o == null) {
                formAn();               // form da dong / o khong con tren man
                return;
            }
            if (chamLuc != 0) {
                if (trongO(o, chamFx, chamFy)) {
                    chamLuc = 0;        // cham vao o dang go / o moi vua duoc chon
                } else if (now - chamLuc > 320) {
                    chamLuc = 0;
                    formAn();           // cham ra ngoai o nhap: tra ban phim
                    return;
                }
            }
            int k = o.H == 2 ? 2 : 1;
            if (o != formO || k != formKieu) {
                formO = o;
                formKieu = k;
                xinPhim(k, vungPhim(o));      // sang o khac: bao lai (kieu o, vi tri)
            }
        } catch (Throwable e) {
        }
    }

    /** gw.a(Graphics): nut bat / tat ban phim ao */
    public static void vePhimAo(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            return;
        }
        g.drawImage(img, x, y, neo);
    }

    /** gw.a(Graphics): bong chat - ban HD ve nut chat kieu moi trong cum nut tren (phimMem), o day khong ve */
    public static void veChat(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            return;
        }
        g.drawImage(img, x, y, neo);
    }

    /** hj.a(Graphics): nhan o goc phai tren (phien ban / luu luong) - ban HD lui vao trong le man hinh (goc bo tron) */
    public static void chuGoc(cs f, Graphics g, String s, int x, int y, int can) {
        if (hd()) {
            docLe();
            x -= gocP - 2;
            y += 1;
        }
        f.a(g, s, x, y, can);
    }

    /** gw.a(Graphics): hinh thu + so thu o goc phai tren - ban HD ve nut thu kieu moi (co cham do) trong phimMem */
    public static void veMail(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            return;
        }
        g.drawImage(img, x, y, neo);
    }

    public static void chuMail(cs f, Graphics g, String s, int x, int y, int can) {
        if (hd()) {
            return;
        }
        f.a(g, s, x, y, can);
    }

    /** main.a.a(Graphics): nhan "12+" - ban HD xuong goc trai duoi (goc tren trai la nut MENU) */
    public static void ve12(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            formTick();
            if (GO && ir.p != goP) {
                goP = ir.p;
                System.out.println("[go] p doi -> " + (goP == null ? "null" : goP.getClass().getName() + " az " + goP.az + " @" + goP.aw + "," + goP.ax
                        + (goP instanceof hn ? " " + ((hn) goP).x + " w " + ((hn) goP).w : "")) + " l " + main.a.l);
            }
            if (GO && System.currentTimeMillis() - goLuc > 2000) {
                goLuc = System.currentTimeMillis();
                goIn("khung");
            }
        }
        if (hd() && img != null) {
            docLe();
            x = gocT - 6;
            y = caoMan() - img.getHeight() - (traiDuoi ? CAO + 7 : 3) - nang();
        }
        g.drawImage(img, x, y, neo);
    }

    // ---- le man hinh (tai tho, goc bo tron): thuoc tinh "j2n.le" = "trai,phai,duoi,goc trai,goc phai" (diem logic), app dat
    // luc khoi dong. leT / leP: le day du (vat nam giua chieu cao man hinh - bang menu, o chat, nut MENU - phai tranh tai tho);
    // gocT / gocP: le cho vat o goc man hinh (phim mem day, thu, nhan goc: chi can tranh goc bo tron); leD: le duoi (thanh Home)
    static int leT = -1, leP, leD, gocT, gocP;

    static void docLe() {
        if (leT >= 0) {
            return;
        }
        int[] v = new int[5];
        v[3] = v[4] = -1;
        try {
            String s = System.getProperty("j2n.le");
            if (s != null) {
                int i = 0, n = 0;
                while (n < 5 && i <= s.length()) {
                    int j = s.indexOf(',', i);
                    if (j < 0) {
                        j = s.length();
                    }
                    if (j > i) {
                        v[n] = Integer.parseInt(s.substring(i, j).trim());
                    }
                    n++;
                    i = j + 1;
                }
            }
        } catch (Throwable e) {
        }
        if (v[3] < 0) {
            v[3] = v[0];
        }
        if (v[4] < 0) {
            v[4] = v[1];
        }
        leP = v[1] + 6 < 12 ? 12 : v[1] + 6;
        leD = v[2] < 0 ? 0 : v[2];
        gocT = (v[3] < 10 ? 10 : v[3]) + 4;
        gocP = (v[4] < 10 ? 10 : v[4]) + 4;
        leT = v[0] + 6 < 12 ? 12 : v[0] + 6;
    }

    /** im.b() vua dat o chat: ban HD lui o vao trong le man hinh (tai tho) va nang len theo phim mem */
    public static void oChat(im o) {
        if (!hd()) {
            return;
        }
        try {
            docLe();
            dx f = o.b;
            if (f == null) {
                return;
            }
            f.a = leT;
            f.c = main.a.m - leT - leP;
            f.b -= nang();
        } catch (Throwable e) {
        }
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
            return nutTai(main.a.h, main.a.i) == N_CHAT && trong(N_CHAT, main.a.j, main.a.k, 9);
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

    /** so cot cua bang do / shop / ruong (dp): ban HD man ngang rong -> 8 cot; con lai 5 nhu goc */
    public static int soCot() {
        try {
            return hd() && main.a.m >= 420 ? 8 : 5;
        } catch (Throwable e) {
            return 5;
        }
    }

    /** o luoi cua bang (dp.e): ban HD 30 -> 32 (bang cao 203 / 250, rong 8 x 32 + 14 = 270) */
    public static int oLuoi(int e) {
        try {
            return hd() && main.a.m >= 420 && e == 30 ? 32 : e;
        } catch (Throwable t) {
            return e;
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

    // ---- THEME BANG MOI (ban HD; admin gui anh mau: "thame mua ban chinh nhu nay cho no dep", "sua may thame nay cho giong"):
    // moi bang cua game (shop, Ban than, ruong, Qua, quay so, dat cuoc...) nen XANH NHAT vien trang bo goc + bong do; long bang
    // trang xanh; the dang chon mau CAM chu trang, the khac trang mo. Tu ve bang may ve (kieu 9 / 10 / 4), khong dung anh goc.
    static final int MAU_BANG = 0x74BDF0, MAU_LONG = 0xF4FBFF, MAU_THE = 0xFF9326, MAU_THE_PHU = 0xE4F3FF, MAU_THE_BAO = 0xFFE08A;

    /** je.a(Graphics, x, y, w, h): 1 o cua luoi do. Ban HD: o xanh nhat vien trang (theme moi); tra ve false -> ve nhu goc */
    public static boolean oLuoiVe(Graphics g, int x, int y, int w, int h) {
        if (!hd()) {
            return false;
        }
        g.setColor(0xA9DBF8);
        g.fillRect(x, y, w, h);
        g.setColor(0xFFFFFF);
        g.drawRect(x, y, w, h);
        g.setColor(0x8CC9F0);
        g.drawRect(x + 1, y + 1, w - 2, h - 2);
        return true;
    }

    /** o dang chon cua luoi do (dp): ban goc to xanh la; ban HD nen trang vien cam */
    /**
     * Bau cua - luot TA cua minh (quan tri: "ta thiet ke lai cho de ti"): dong chi dan tung buoc phia tren ban + khung noi quanh
     * o da chon (vang = o lay chip, xanh = o don toi). Goi cuoi ja.b(Graphics) o moi ban (JAR / doc / HD).
     * luot = dang la luot ta cua minh (ja.aE), xong = da gui lenh ta (ja.f), T / U = o nguon / o dich (-1 chua chon).
     */
    public static void taVe(Graphics g, boolean luot, boolean xong, int T, int U, int R, int S) {
        try {
            if (!luot || xong || !(kx.j || kx.k) || kx.l) {
                return;
            }
            main.a.c(g);
            int ow = ja.g, oh = ja.h, W = main.a.m;
            boolean nhay = (System.currentTimeMillis() / 300) % 2 == 0;
            for (int lan = 0; lan < 2; lan++) {
                int o = lan == 0 ? T : U;
                if (o < 0 || o > 5) {
                    continue;
                }
                int x = R + o % 3 * (ow + 10), y = S + o / 3 * (oh + 8);
                int mau = lan == 0 ? 0xFFD23A : 0x35C8FF;
                for (int k = 0; k < 3; k++) {
                    g.setColor(k == 1 && nhay ? 0xFFFFFF : mau);
                    g.drawRoundRect(x - 2 - k, y - 2 - k, ow + 3 + 2 * k, oh + 3 + 2 * k, 8, 8);
                }
                String nh = lan == 0 ? "LẤY" : "DỒN";
                int nw = main.a.L.a(nh) + 8;
                g.setColor(lan == 0 ? 0xB36B00 : 0x0B5FA8);
                g.fillRoundRect(x + ow / 2 - nw / 2, y - 8, nw, 13, 6, 6);
                main.a.L.a(g, nh, x + ow / 2, y - 8, 2);
            }
            String s = T < 0 ? "Lượt TẢ: chạm ô có chip muốn gom" : U < 0 ? "Chạm Ô KHÁC để dồn chip tới" : null;
            if (s == null) {
                return;
            }
            int w = main.a.L.a(s) + 18, h = 15, x = (W - w) / 2, y = S - 21;
            if (y < 14) {
                y = 14;
            }
            g.setColor(0x10222C);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(nhay ? 0xFFD23A : 0xFFFFFF);
            g.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
            main.a.L.a(g, s, W / 2, y + 1, 2);
        } catch (Throwable e) {
        }
    }

    /** mo o nhap chat trong phong OnGame (bau cua, tien len, phom...): y nhu game goc mo bong chat o the gioi, dich gui = phong */
    static void moChatPhong() {
        try {
            if (!(main.a.r instanceof kx) || main.a.A != null) {
                return;
            }
            if (!dm.d) {
                dm.d = true;
                dm.b().b[4] = 1;
                main.a.a.b();
                main.a.z.a = true;
            }
            im.d().d = (cq) main.a.r;
            im.c = true;
        } catch (Throwable e) {
        }
    }

    /** dp vua dat khung bang (je): ban HD can bang giua ca chieu cao man hinh (tab khong bi cat, chua cho nut X goc tren) */
    public static void bangY() {
        if (!hd()) {
            return;
        }
        try {
            je k = je.a();
            int y = (main.a.n - k.e) / 2;
            if (y < 3) {
                y = 3;
            }
            k.h = y;
            dp.b = y;
        } catch (Throwable e) {
        }
    }

    public static void oChon(int x, int y, int w, int h, int mau, Graphics g) {
        if (!hd()) {
            je.a(x, y, w, h, mau, g);
            return;
        }
        g.setColor(MAU_THE);
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        g.setColor(0xFFFFFF);
        g.fillRect(x, y, w, h);
    }

    /**
     * gw.a(Graphics, x, y, w, h, mau, mau, kieu) (ham ve khung goc cua game; PatchFarmFx chen loi goi nay vao DAU ham nen moi
     * cho ve khung - ca goi noi bo trong gw: bang menu noi, bang 2 lop... - deu doi theme). kieu 0 = khung ngoai, 1 = long bang.
     * Tra ve true = da ve (ban HD); false = ve nhu goc.
     */
    public static boolean khungHd(Graphics g, int x, int y, int w, int h, int a, int b, int c) {
        if (!hd()) {
            return false;
        }
        if (c == 0 && w >= 24 && h >= 24) {
            kieu(g, 9, x, y, w, h, 7, MAU_BANG);
            return true;
        }
        if (c == 1 && w >= 16 && h >= 16) {
            kieu(g, 10, x, y, w, h, 5, MAU_LONG);
            kieu(g, 10, x, y, w, h, 5, MAU_LONG);
            return true;
        }
        return false;
    }

    /**
     * PHONG CHO tro choi (kx.e: nen hoa van + "P: x - B: y" + muc cuoc) - ban HD (admin: "phong cho nhin trong qua"): lam sau
     * nen (toi dan ve goc), dat 1 BAN NI giua phong, ghe TRONG hien vong ghe mo o dung cho nguoi se ngoi, dong chu so phong
     * nam tren dai sang cho de doc. Dang choi (kx.j) thi khong ve (ban choi co giao dien rieng).
     */
    public static void nenPhong(ew o, Graphics g) {
        o.b(g);
        if (!hd()) {
            return;
        }
        try {
            int W = main.a.m, H = main.a.q > main.a.n ? main.a.q : main.a.n;
            if (kx.j) {
                // dang choi Bau cua: ban ni lon duoi 6 o linh vat (cac tro bai co ban rieng)
                if (main.a.r instanceof ja) {
                    kieu(g, 7, 0, 0, W, H, 0, 0);
                    int bw = 250, bh = 150;
                    kieu(g, 6, W / 2 - bw / 2, main.a.n / 2 - 18 - bh / 2 + (H - main.a.n) / 4, bw, bh, 46, 0x1F9460);
                }
                return;
            }
            kieu(g, 7, 0, 0, W, H, 0, 0);
            int tw = W * 44 / 100, th = H * 44 / 100;
            if (tw > 280) {
                tw = 280;
            }
            int tx = (W - tw) / 2, ty = main.a.n / 2 - th / 2 + 2;
            kieu(g, 6, tx, ty, tw, th, th / 2, 0x1F9460);
            if (kx.m != null && kx.x != null && kx.F != null) {
                for (int i = 0; i < kx.H && i < kx.m.size() && i < kx.x.length; i++) {
                    hn h = (hn) kx.m.elementAt(i);
                    int k = kx.x[i];
                    if (h == null || h.w != -1 || k < 0 || k >= kx.F.length || kx.F[k] == null) {
                        continue;
                    }
                    kieu(g, 8, kx.F[k].a - 15, kx.F[k].b - 34, 30, 38, 13, 0xFFFFFF);     // ghe trong
                }
            }
            kieu(g, 10, main.a.o - 58, main.a.n / 2 - 10 * gy.Y - 2, 116, 14, 7, 0xF4FBFF);
        } catch (Throwable e) {
        }
    }

    /**
     * bang vi tri ghe cua phong tro choi (kx.F, ja.aK) vua tao: ghe trai / phai cua game goc nam sat mep (x = 13..20). Ban HD
     * lui vao trong le man hinh (tai tho, goc bo tron) - truoc day ten + tien cua nguoi ngoi ghe trai bi cat mat chu dau.
     */
    public static dr[] ghe(dr[] f) {
        if (f == null || !hd()) {
            return f;
        }
        docLe();
        int xl = leT + 24, xr = main.a.m - leP - 24;
        for (int k = 0; k < f.length; k++) {
            if (f[k] != null) {
                f[k].a = f[k].a < xl ? xl : f[k].a > xr ? xr : f[k].a;
            }
        }
        return f;
    }

    /** mau dong dang chon cua bang menu noi (goc 0xECFBE9 xanh rat nhat): ban HD nen trang nen dung cam nhat cho noi */
    public static int mauChon(int mau) {
        return hd() ? 0xFFD596 : mau;
    }

    /** khung cua so: ew.a(Graphics, x, y, w, h, mau, mau, kieu) - ban HD: gw.a da tu ve theme moi (khungHd) */
    public static void khung7(ew o, Graphics g, int x, int y, int w, int h, int a, int b, int c) {
        o.a(g, x, y, w, h, a, b, c);
    }

    /**
     * bang co the (tab): ew.a(Graphics, x, y, CAO, RONG, the dang chon, the dau tien dang hien, buoc the, rong the, cao the,
     * tong so the, so the hien toi da, dem nhay[], kieu nhay[], ten the dang chon) - chu y thu tu cao truoc rong
     */
    public static void menu(ew o, Graphics g, int x, int y, int h, int w, int chon, int dau, int buoc, int rt, int ct, int tong, int toiDa,
            int[] m, int[] n, String s) {
        if (!hd()) {
            o.a(g, x, y, h, w, chon, dau, buoc, rt, ct, tong, toiDa, m, n, s);
            return;
        }
        main.a.c(g);
        kieu(g, 9, x, y, w, h, 7, MAU_BANG);
        int cuoi = tong >= toiDa ? toiDa + dau : tong;
        for (int i = dau; i < cuoi; i++) {
            if (i == chon || i < 0 || i >= m.length || i >= n.length) {
                continue;
            }
            if (++m[i] > 20) {
                m[i] = 0;
            }
            boolean bao = n[i] != 0 && m[i] <= 5;       // the co tin moi: nhay vang
            kieu(g, 10, x + 3 + (i - dau) * buoc, y + 4, rt - 2, ct + 4, 5, bao ? MAU_THE_BAO : MAU_THE_PHU);
        }
        kieu(g, 10, x + 3, y + ct, w - 6, h - ct - 3, 5, MAU_LONG);
        kieu(g, 10, x + 3, y + ct, w - 6, h - ct - 3, 5, MAU_LONG);
        int tx = x + 3 + (chon - dau) * buoc;
        kieu(g, 4, tx, y + 3, rt - 1, ct - 2, 6, MAU_THE);
        main.a.L.a(g, s, tx + rt / 2, y + 2 + (ct - 2) / 2 - gy.ag / 2, 2);
    }

    /*
     * HOP THOAI KIEU MOI (ban HD, dot 4 - admin: "khung chu to qua", "be lai khung chu xiu", "mau xanh kia cho nhe"): hop thong
     * bao / hop hoi chuan cua game (lop jj) truoc day la 1 dai xanh dam rong gan het man hinh nam sat day, lua chon kieu
     * "< Co >" phai bam mui ten de doi. Ban HD ve lai: bang XANH NHAT bo goc NHO GON vua voi chu, nam GIUA man hinh, moi lua
     * chon la 1 NUT rieng bam thang (nhieu hon 3 lua chon / qua rong: 1 nut lua chon + 2 nut mui ten). PatchFarmFx doi cho
     * goi main.a.v.a(g) / main.a.v.l() trong main.a sang hopVe / hopL; lop khac jj (o nhap, bang...) giu nhu goc.
     */
    private static final int HOP_NUT = 6;
    private static final int[][] hn2 = new int[HOP_NUT][4];     // khung nut cua hop: x, y, rong, cao
    private static int hopSo;                                   // so nut dang ve
    private static boolean hopChon;                             // true: kieu 1 nut lua chon + mui ten (nut 0 = "<", 1 = giua, 2 = ">")
    private static String hopChu;                               // noi dung da ngat dong
    private static int hopRong;
    private static java.util.Vector hopDong;
    static final int MAU_HOP = 0xBFE4F8, MAU_NUT_PHU = 0x6E8FB3;

    /** chieu cao that cua man hinh (cac man kieu OnGame de main.a.n = cao - dai phim mem) */
    static int caoMan() {
        return main.a.q > main.a.n ? main.a.q : main.a.n;
    }

    public static void hopVe(gy o, Graphics g) {
        if (!hd() || !(o instanceof jj)) {
            o.a(g);
            return;
        }
        try {
            veHop((jj) o, g);
        } catch (Throwable e) {
        }
    }

    private static void veHop(jj d, Graphics g) {
        main.a.c(g);
        hopSo = 0;
        if (System.currentTimeMillis() / 100 - d.t < 5) {
            return;                 // nhu goc: hop "xin cho" chi hien sau 0,5 giay
        }
        docLe();
        int W = main.a.m, H = caoMan();
        String s = d.d == null ? "" : d.d;
        int toiDa = W - 2 * leT - 50;
        if (toiDa > 330) {
            toiDa = 330;
        }
        if (toiDa < 150) {
            toiDa = 150;
        }
        if (s != hopChu || hopRong != toiDa || hopDong == null) {
            hopChu = s;
            hopRong = toiDa;
            hopDong = main.a.K.b(s, toiDa - 30);
        }
        int caoDong = main.a.K.a() + 3, soDong = hopDong.size();
        int rongChu = 0;
        for (int i = 0; i < soDong; i++) {
            int w = main.a.K.a((String) hopDong.elementAt(i));
            if (w > rongChu) {
                rongChu = w;
            }
        }
        // ---- cac nut
        int n = d.e != null ? d.e.size() : 0;
        String[] nhanNut = null;
        int[] rongNut = null;
        int rongHang = 0;
        hopChon = false;
        if (n > 0) {
            nhanNut = new String[n];
            rongNut = new int[n];
            for (int i = 0; i < n; i++) {
                by b = (by) d.e.elementAt(i);
                nhanNut[i] = b == null || b.a == null ? "" : b.a;
                rongNut[i] = rongNutChu(nhanNut[i]);
                if (rongNut[i] < 58) {
                    rongNut[i] = 58;
                }
                rongHang += rongNut[i] + (i > 0 ? 8 : 0);
            }
            if (n > 3 || n > HOP_NUT || rongHang > toiDa - 28) {
                hopChon = true;
                rongHang = 0;
                for (int i = 0; i < n; i++) {
                    if (rongNut[i] > rongHang) {
                        rongHang = rongNut[i];
                    }
                }
                rongHang += 2 * (26 + 6);
            }
        } else if (d.ad != null && nhan(d.ad) != null) {
            n = 1;
            nhanNut = new String[]{d.ad.a};
            rongNut = new int[]{rongNutChu(d.ad.a) < 70 ? 70 : rongNutChu(d.ad.a)};
            rongHang = rongNut[0];
        }
        int rong = (rongChu > rongHang ? rongChu : rongHang) + 32;
        if (rong < 150) {
            rong = 150;
        }
        if (rong > toiDa) {
            rong = toiDa;
        }
        int caoChu = soDong * caoDong;
        if (d.a && caoChu < 26) {
            caoChu = 26;            // hop "xin cho": cho vong xoay
        }
        int cao = 13 + caoChu + (n > 0 ? 9 + CAO + 11 : 11);
        int x = (W - rong) / 2, y = (H - cao) / 2 - 4;
        if (y < 4) {
            y = 4;
        }
        // ---- nen mo + bang
        kieu(g, 11, 0, 0, W, H, 0, 0x061A2E);
        kieu(g, 9, x, y, rong, cao, 9, MAU_HOP);
        // ---- chu
        int yc = y + 13;
        if (d.a && jj.b != null) {
            // vong xoay "dang tai" ben trai dong chu
            jj.b.a(d.k, x + rong / 2, yc + caoChu + 2, 0, 3, g);
            yc -= 6;
        }
        for (int i = 0; i < soDong; i++) {
            main.a.K.a(g, (String) hopDong.elementAt(i), W / 2, yc + i * caoDong, 2);
        }
        // ---- nut
        if (n <= 0) {
            return;
        }
        int yn = y + 13 + caoChu + 9;
        if (hopChon) {
            int gi = d.f >= 0 && d.f < n ? d.f : 0;
            int wg = rongHang - 2 * (26 + 6);
            int x0 = (W - rongHang) / 2;
            veNutHop(g, 0, x0, yn, 26, "<", MAU_NUT_PHU);
            veNutHop(g, 1, x0 + 26 + 6, yn, wg, nhanNut[gi], MAU_NUT);
            veNutHop(g, 2, x0 + 26 + 6 + wg + 6, yn, 26, ">", MAU_NUT_PHU);
            hopSo = 3;
            return;
        }
        int xn = (W - rongHang) / 2;
        for (int i = 0; i < n; i++) {
            // lua chon dau = nut chinh (xanh), "Khong" / "Dong" / "Huy" = nut phu (xam xanh)
            String t = nhanNut[i];
            boolean phu = i > 0 && (t.equals(kf.p) || t.equals(kf.x) || t.equals("Đóng") || t.equals("Hủy") || t.equals("Thôi"));
            veNutHop(g, i, xn, yn, rongNut[i], t, phu ? MAU_NUT_PHU : MAU_NUT);
            xn += rongNut[i] + 8;
        }
        hopSo = n;
    }

    private static void veNutHop(Graphics g, int i, int x, int y, int w, String s, int mau) {
        kieu(g, 4, x, y, w, CAO, 7, mau);
        main.a.L.a(g, s, x + w / 2, y + CAO / 2 - gy.ag / 2 - 1, 2);
        hn2[i][0] = x;
        hn2[i][1] = y;
        hn2[i][2] = w;
        hn2[i][3] = CAO;
    }

    private static int nutHop(int x, int y) {
        for (int i = 0; i < hopSo; i++) {
            int[] v = hn2[i];
            if (x >= v[0] - 5 && x < v[0] + v[2] + 5 && y >= v[1] - 6 && y < v[1] + v[3] + 6) {
                return i;
            }
        }
        return -1;
    }

    /** main.a (vong lap): hop thoai dang mo xu ly phim / cham. Ban HD + hop jj: cham thang vao nut cua hop kieu moi */
    public static void hopL(gy o) {
        if (!hd() || !(o instanceof jj)) {
            o.l();
            return;
        }
        jj d = (jj) o;
        boolean nha = main.a.f;
        int n = -1;
        if (nha) {
            n = nutHop(main.a.h, main.a.i);
            if (n >= 0 && n != nutHop(main.a.j, main.a.k)) {
                n = -1;             // phai nhan va nha trong cung 1 nut
            }
        }
        // phan con lai cua ham goc (dem gio hop "xin cho", phim cung) chay voi co "vua nha tay" da tat: no khong do theo
        // vung cham cua hop kieu cu nua
        main.a.f = false;
        main.a.g = false;
        try {
            o.l();
        } catch (Throwable e) {
        }
        if (n < 0 || main.a.v != o) {
            return;
        }
        try {
            if (hopChon) {
                int so = d.e != null ? d.e.size() : 0;
                if (so <= 0) {
                    return;
                }
                if (n == 1) {
                    main.a.h();
                    d.a(d.ad);
                } else {
                    int f = d.f + (n == 0 ? -1 : 1);
                    d.f = f < 0 ? so - 1 : f >= so ? 0 : f;
                    d.ad = (by) d.e.elementAt(d.f);
                }
                return;
            }
            if (d.e != null && n < d.e.size()) {
                d.f = n;
                d.ad = (by) d.e.elementAt(n);
            }
            by b = d.ad;
            main.a.h();
            d.a(b);
        } catch (Throwable e) {
        }
    }

    /*
     * GIAO DIEN NUT KIEU MOI (ban HD, dot 3 - theo anh chu server dua):
     *  - Goc tren trai: nut MENU hinh the mau xanh vien trang (phim mem trai ten "Menu"), nut thu / su kien (co cham do so tin
     *    moi), nut chat.
     *  - Goc tren phai (man choi): nut chuc nang (4 o mau = phim mem giua "Chon" cu), nut cam xuc, nut hanh dong.
     *  - KHONG con nut nao o giua day man hinh: phim mem giua (khi khong phai "Chon") don sang goc phai duoi canh phim mem
     *    phai; phim mem trai (khong phai Menu) o goc trai duoi. Nut chu = nut mau xanh vien trang (may ve kieu 4).
     *  - Dai chu chay: khong con nen toi, chu chay trong khoang trong giua 2 cum nut tren.
     * Anh nut tu ve (hd/mkprops.py -> hdx/p_nut_*.png, p_bao_*.png). vn[] giu khung cua tung nut trong khung hinh vua ve de
     * do cham (vungPhimMem / phimNao / vungChat).
     */
    static int khung;               // dem lan ve phim mem
    static boolean traiDuoi;        // lan ve vua roi co phim mem trai o goc trai duoi (nhan 12+ ne len tren)
    static boolean menuTren;        // lan ve vua roi cua MAN HINH (khong phai bang noi) co nut MENU o goc tren
    static final int CAO = 22;      // cao nut chu
    static final int NUT = 24;      // canh nut bieu tuong
    static final int MAU_NUT = 0x2C88E2;
    static final int N_TRAI = 0, N_GIUA = 1, N_PHAI = 2, N_CHAT = 3, N_THU = 4, N_CAMXUC = 5, N_HDONG = 6, N_SO = 7;
    private static final int[][] vn = new int[N_SO][4];     // x, y, rong, cao (rong = 0: khong co nut)
    static long vnLuc;                                      // luc ve cac nut tren (ms)
    private static int chamPhim = -1;                       // phim mem vua cham (vungPhimMem -> phimNao)
    static int daiX0, daiX1;                                // khoang trong giua 2 cum nut tren (cho dai chu chay)
    private static java.util.Hashtable anhNut = new java.util.Hashtable();

    /** anh nut tu ve trong jar: /hdx/p_<ten>.png (null neu thieu) */
    static Image nut(String ten) {
        Object o = anhNut.get(ten);
        if (o == null) {
            try {
                o = Image.createImage("/hdx/p_" + ten + ".png");
            } catch (Throwable e) {
                o = "";
            }
            anhNut.put(ten, o);
        }
        return o instanceof Image ? (Image) o : null;
    }

    private static void dat(int i, int x, int y, int w, int h) {
        vn[i][0] = x;
        vn[i][1] = y;
        vn[i][2] = w;
        vn[i][3] = h;
    }

    static boolean trong(int i, int x, int y, int no) {
        int[] v = vn[i];
        return v[2] > 0 && x >= v[0] - no && x < v[0] + v[2] + no && y >= v[1] - no && y < v[1] + v[3] + no;
    }

    /** nut (cua lan ve gan nhat) nam duoi diem (x, y), -1 neu khong co */
    static int nutTai(int x, int y) {
        if (System.currentTimeMillis() - vnLuc > 400) {
            return -1;                  // lau roi khong ve phim mem (man hinh khong co nut): khung cu khong con dung
        }
        for (int i = 0; i < N_SO; i++) {
            if (trong(i, x, y, 5)) {
                return i;
            }
        }
        return -1;
    }

    private static String nhan(by p) {
        return p == null || p.a == null || p.a.length() == 0 ? null : p.a;
    }

    /** 1 nut bieu tuong (anh 26 x 28, tam nut 24 x 24 tai (1, 1)) */
    private static void veNutAnh(Graphics g, String ten, int i, int x, int y) {
        Image im = nut(ten);
        if (im != null) {
            g.drawImage(im, x - 1, y - 1, 0);
        } else {
            kieu(g, 4, x, y, NUT, NUT, 6, MAU_NUT);
        }
        dat(i, x, y, NUT, NUT);
    }

    /** 1 nut chu (nut mau xanh vien trang), tra ve be rong */
    private static int rongNutChu(String s) {
        int w = main.a.L.a(s) + 20;
        return w < 44 ? 44 : w;
    }

    private static void veNutChu(Graphics g, int i, int x, int y, int w, String s) {
        kieu(g, 4, x, y, w, CAO, 7, MAU_NUT);
        main.a.L.a(g, s, x + w / 2, y + CAO / 2 - gy.ag / 2 - 1, 2);
        dat(i, x, y, w, CAO);
    }

    /**
     * 3 phim mem (trai / giua / phai) - gy.a(Graphics) cua man hinh hoac cua bang noi dang mo. Ban HD ve giao dien nut kieu
     * moi (xem chu thich o tren); ban thuong / J2ME: ve nhu goc.
     */
    public static void phimMem(ew o, Graphics g, by l, by c, by r) {
        if (!hd()) {
            o.a(g, l, c, r);
            return;
        }
        try {
            vePhimHd(g, l, c, r);
        } catch (Throwable e) {
        }
    }

    /**
     * Cac man kieu OnGame (sanh, danh sach phong / ban, phong cho, ban choi, o nhap) ve phim mem qua jy.a(g, l, c, r) /
     * hb.a(g) = thanh day (ew.c) + chu tren thanh (ew.b). Ban HD: khong ve thanh day, ve nut kieu moi (va ghi khung nut de
     * gy.l() do cham); ban thuong / J2ME: nhu goc.
     */
    public static void thanhDay(ew o, Graphics g) {
        if (!hd()) {
            o.c(g);
            return;
        }
        // Sanh OnGame: may chu da bo xeng (cuoc thang bang xu, app nhan co ae.u = false) thi bo o "Chuyen tien" (doi xu <-> xeng)
        // cuoi danh sach tro choi - quan tri: "may game giai tri gio chi co xu, bo han xeng"
        try {
            if (!ae.u && kf.eA != null && kf.eA.length == 5) {
                String[] n = new String[4];
                System.arraycopy(kf.eA, 0, n, 0, 4);
                kf.eA = n;
            }
        } catch (Throwable e) {
        }
    }

    public static void phimMemB(ew o, Graphics g, by l, by c, by r) {
        if (!hd()) {
            o.b(g, l, c, r);
            return;
        }
        try {
            vePhimHd(g, l, c, r);
        } catch (Throwable e) {
        }
    }

    /**
     * ho.a() (bo cuon chung) bao cho man hinh: o thu i dang nam duoi ngon tay / vua duoc chon; co = false nghia la "dang giu
     * tay" hoac "bam phim OK". Bang cua hang (dp) cua game goc: giu tay tren o DANG CHON la chay luon phim "Mua" -> ban HD cham
     * vao mon nao cung bat hop hoi mua, keo de cuon cung vay. Ban HD: dang giu tay tren luoi cua hang thi chi CHON mon; dang
     * keo (da roi xa diem nhan) thi khong doi mon dang chon. Mua = bam nut "Mua".
     */
    public static void chonO(hj o, int i, boolean co) {
        if (!co && main.a.e && o instanceof dp && hd()) {
            int dx = main.a.h - main.a.j, dy = main.a.i - main.a.k;
            if (dx < 0) {
                dx = -dx;
            }
            if (dy < 0) {
                dy = -dy;
            }
            if (dx <= 6 && dy <= 6) {
                o.a(i, true);
            }
            return;
        }
        o.a(i, co);
    }

    /** mau nut lon giua man o phong cho OnGame */
    static final int MAU_BAT_DAU = 0xF2981C, MAU_SAN_SANG = 0x2FB14A, MAU_HUY = 0xDB5A52;

    /** nut lon (phong cho: Bat dau / San sang): vong sang nhe phia sau + nut mau vien trang + chu giua */
    private static void veNutLon(Graphics g, int i, int x, int y, int w, int h, String s, int mau) {
        // quang sang "tho" cham quanh nut de moi goi bam: vung quang no ra / co lai 0..2 diem
        int pha = (int) ((System.currentTimeMillis() / 40) % 50);
        int no = (pha < 25 ? pha : 50 - pha) / 9;
        kieu(g, 5, x - no, y - no, w + 2 * no, h + 2 * no, (h + 2 * no) / 2, mau);
        kieu(g, 4, x, y, w, h, h / 2, mau);
        main.a.L.a(g, s, x + w / 2, y + h / 2 - gy.ag / 2 - 1, 2);
        dat(i, x, y, w, h);
    }

    private static void vePhimHd(Graphics g, by l, by c, by r) {
        docLe();
        khung++;
        for (int i = 0; i < N_SO; i++) {
            vn[i][2] = 0;
        }
        vnLuc = System.currentTimeMillis();
        traiDuoi = false;
        if (DUMP && main.a.r != manPhim) {
            manPhim = main.a.r;
            System.out.println("[hd] man (phim mem) " + (manPhim == null ? "null" : manPhim.getClass().getName()));
        }
        int W = main.a.m, H = caoMan();
        String sl = nhan(l), sc = nhan(c), sr = nhan(r);
        // man hinh "tran": khong co bang / hop noi / o chat dang mo (luc do phim mem la cua bang noi)
        boolean tran = main.a.t == null && main.a.v == null && main.a.A == null && !im.c;
        Object man = main.a.r;
        // man hinh ve the gioi tran man (khu, nong trai, ban do thanh pho, man chao, cau ca, nha...): nut MENU len goc tren.
        // Man hinh dang bang (su kien, cua hang, danh sach...) co phim trai "Menu": de o goc trai duoi nhu nut chu thuong
        boolean theGioi = man instanceof ae || man instanceof go || man instanceof ea || man instanceof es || man instanceof kv
                || man instanceof cz || man instanceof ei || man instanceof ic;
        boolean phong = man instanceof kx;                      // phong cho / ban choi OnGame (Bau cua, Tien len, Phom...)
        boolean menu = tran && (theGioi || phong) && sl != null && (sl.equals(kf.c) || sl.equals("Menu") || sl.equals("MENU"));
        boolean khu = tran && man instanceof ae && main.a.Z == 0 && !ae.t;      // dang choi trong 1 khu
        int yT = 3;
        if (tran) {
            menuTren = menu;
        }
        // ---- cum tren trai: MENU, thu, chat
        int xt = gocT;
        if (menu) {
            Image im = nut("nut_menu");
            if (im != null) {
                g.drawImage(im, xt - 1, yT, 0);
            } else {
                kieu(g, 4, xt, yT + 1, 50, CAO, 7, MAU_NUT);
                main.a.L.a(g, sl, xt + 25, yT + 1 + CAO / 2 - gy.ag / 2 - 1, 2);
            }
            dat(N_TRAI, xt, yT + 1, 50, CAO);
            xt += 50 + 6;
        }
        if (menu && (khu || (man instanceof ea && hj.as > 0))) {
            veNutAnh(g, "nut_thu", N_THU, xt, yT);
            if (hj.as > 0) {
                Image b = nut(hj.as > 9 ? "bao_n" : "bao_" + hj.as);
                if (b != null) {
                    g.drawImage(b, xt + 13, yT + 13, 0);
                }
            }
            xt += NUT + 6;
        }
        // phong cho / ban choi OnGame (kx) cung co nut chat: ban HD khong co ban phim ao nen truoc day KHONG chat duoc trong phong
        if (tran && ((hj.ap != null && main.GameMidlet.e == 9 && main.a.m()) || phong)) {
            veNutAnh(g, "nut_chat", N_CHAT, xt, yT);
            xt += NUT + 6;
        }
        // ---- cum tren phai: hanh dong, cam xuc, chuc nang (= phim mem giua "Chon")
        int xp = W - gocP;
        boolean chon = sc != null && sc.equals(kf.O);
        if (khu && main.a.D == null) {
            xp -= NUT;
            veNutAnh(g, "nut_hanh_dong", N_HDONG, xp, yT);
            xp -= 6 + NUT;
            veNutAnh(g, "nut_cam_xuc", N_CAMXUC, xp, yT);
            xp -= 6;
        }
        if (menu && chon) {
            // man chao / ban do thanh pho: cham thang vao muc / bieu tuong khu la chon -> khong can nut
            if (!(man instanceof es) && !(man instanceof ea)) {
                xp -= NUT;
                veNutAnh(g, "nut_them", N_GIUA, xp, yT);
                xp -= 6;
            }
            sc = null;
        }
        daiX0 = xt + 4;
        // man chao / chon thanh pho / ban do thanh pho co nhan phien ban hoac luu luong o goc phai tren: chua cho
        daiX1 = xp - 4 - (man instanceof ea || man instanceof es || man instanceof ez ? 44 : 0);
        // ---- phong cho OnGame: phim giua "Bat dau" / "San sang" / "Khong s.sang" = nut lon o GIUA man hinh (duoi ghe tren,
        // tren dong "P: .. - B: .." giua man)
        if (phong && tran && sc != null && (sc.equals(kf.n) || sc.equals(kf.j) || sc.equals(kf.k))) {
            int w = main.a.L.a(sc) + 52;
            if (w < 104) {
                w = 104;
            }
            int h = 30;
            int yG = (53 + (H / 2 - 10)) / 2 + 2 - h / 2;       // giua ghe tren (y = 53) va dong thong tin phong (H / 2 - 10)
            veNutLon(g, N_GIUA, (W - w) / 2, yG, w, h, sc, sc.equals(kf.n) ? MAU_BAT_DAU : sc.equals(kf.j) ? MAU_SAN_SANG : MAU_HUY);
            sc = null;
        }
        // ---- bang do / shop / ruong (dp) - theo mau quan tri gui: "Dong" = nut X do tron o goc tren phai cua bang; nut hanh
        // dong (Mua / Chon / Ban...) = nut lon nam trong bang, goc duoi phai
        if (tran && man instanceof dp) {
            try {
                je k = je.a();
                int bx = k.g, bw = k.f, bh = k.e, by0 = k.h;
                if (bw >= 150 && bh >= 120 && bx >= 0 && bx + bw <= W && by0 >= 0 && by0 + bh <= H + 4) {
                    if (sr != null && sr.equals(kf.d)) {
                        int x = bx + bw - NUT / 2 - 5, y = by0 + je.o - NUT / 2 + 3;
                        if (x > W - NUT - 2) {
                            x = W - NUT - 2;
                        }
                        if (y < 1) {
                            y = 1;
                        }
                        veNutAnh(g, "nut_dong", N_PHAI, x, y);
                        sr = null;
                    }
                    if (sc != null && dp.f == 2 && !dp.k) {     // chi khi bang co vung thong tin phia duoi luoi (2 hang o)
                        int w = main.a.L.a(sc) + 40;
                        if (w < 82) {
                            w = 82;
                        }
                        int h = 26, x = bx + bw - w - 13, y = by0 + bh - h - 10;
                        kieu(g, 4, x, y, w, h, 9, MAU_NUT);
                        main.a.L.a(g, sc, x + w / 2, y + h / 2 - gy.ag / 2 - 1, 2);
                        dat(N_GIUA, x, y, w, h);
                        sc = null;
                    }
                }
            } catch (Throwable e) {
            }
        }
        // dang mo o chat trong phong OnGame: phim giua cua phong (Bat dau / San sang) khong ve o hang day (bi nut Chat de len)
        if (phong && im.c && sc != null && (sc.equals(kf.n) || sc.equals(kf.j) || sc.equals(kf.k))) {
            sc = null;
        }
        // ---- hang day: phim trai (khong phai Menu) o goc trai; phim giua + phai don ve goc phai
        int yD = H - CAO - 3 - nang();
        if (!menu && sl != null) {
            veNutChu(g, N_TRAI, gocT, yD, rongNutChu(sl), sl);
            traiDuoi = true;
        }
        int x1 = W - gocP;
        if (sr != null) {
            int w = rongNutChu(sr);
            x1 -= w;
            veNutChu(g, N_PHAI, x1, yD, w, sr);
            x1 -= 6;
        }
        if (sc != null) {
            int w = rongNutChu(sc);
            x1 -= w;
            veNutChu(g, N_GIUA, x1, yD, w, sc);
        }
    }

    /** phim mem o day duoc nang len bao nhieu diem (may co thanh Home o mep duoi: le duoi > 0) */
    static int nang() {
        docLe();
        int n = leD - 4;
        return n < 0 ? 0 : n > 12 ? 12 : n;
    }

    /**
     * gy.l(): phim mem nao vua duoc cham (0 trai, 1 giua, 2 phai; -1 khong). Ban HD: nut vua cham (vungPhimMem da tim) -
     * nut MENU = phim trai, nut chuc nang 4 o mau = phim giua.
     */
    public static int phimNao(ew o) {
        if (!hd()) {
            return o.d();
        }
        int n = chamPhim;
        chamPhim = -1;
        return n;
    }

    // ---- vong quay so (hw): game goc phai GIU phim giua de lay da roi nha moi quay, va do vung cham theo 3 o phim mem cu
    // (main.a.ae). Ban HD ve nut "Quay" o goc phai nen giu nut khong an -> nguoi choi thay "quay so bi lag / bam khong an".
    /** hw.l(): phim mem dang duoc GIU / vua nha (0 trai, 1 giua, 2 phai; -1 khong). Ban HD: theo khung nut vua ve */
    public static int phimGiu(ew o) {
        if (!hd()) {
            return o.d();
        }
        if (!main.a.e && !main.a.f) {
            return -1;
        }
        int n = nutTai(main.a.h, main.a.i);
        return n >= 0 && n <= N_PHAI ? n : -1;
    }

    /** hw.q ("da bam Quay 1 lan de san sang"): ban HD luon san sang - giu nut Quay la lay da ngay, khoi bam mo truoc */
    public static boolean quaySan(boolean q) {
        return q || hd();
    }

    /** hw.k (goc kim luc, 90 = chua co luc): ban HD cham nhanh nut Quay (chua kip lay da) van quay voi luc nho nhat */
    public static int quayLuc(int k) {
        return hd() && k <= 90 && main.a.c[5] ? 96 : k;
    }

    /**
     * le.a(Vector, int kieu): bang menu noi cua phim mem (kieu 0 = ben trai, 1 = ben phai). Ban HD: bang ben trai xo xuong
     * ngay duoi nut MENU o goc tren; bang ben phai lui vao trong le man hinh.
     */
    public static int menuX(int x, int kieu, int rong) {
        if (!hd()) {
            return x;
        }
        docLe();
        return kieu == 0 ? leT : kieu == 1 ? x - leP + 4 : x;
    }

    public static int menuY(int y, int kieu) {
        if (!hd()) {
            return y;
        }
        if (kieu != 0 || !menuTren) {
            return y - nang() - 4;      // bang ben phai / giua, hoac phim trai dang o goc trai duoi: nam tren hang nut day
        }
        return y < 30 ? y : 30;         // nut MENU o goc tren: bang xo xuong ngay duoi nut
    }

    /**
     * gy.l() (luc vua nha tay): co cham vao phim mem khong. Ban HD: do theo khung cac nut vua ve. Cham nut MENU / nut chuc
     * nang / nut chu -> true (phimNao tra ve phim mem tuong ung). Cham nut thu / cam xuc / hanh dong -> lam ngay tai day (dang
     * o luong game) roi nuot lan cham (ban do khong coi la cham de di). Phai la CHAM (nhan va nha cung trong 1 nut).
     */
    public static boolean vungPhimMem(int x, int y, int w, int h) {
        if (!hd()) {
            return main.a.b(x, y, w, h);
        }
        chamPhim = -1;
        goIn("nha");
        int n = nutTai(main.a.h, main.a.i);
        if (n == N_CHAT && main.a.r instanceof kx && trong(n, main.a.j, main.a.k, 9)) {
            moChatPhong();              // phong OnGame: mo o chat (game goc chi mo bong chat o the gioi, GameMidlet.e == 9)
            main.a.g = false;
            main.a.f = false;
            return false;
        }
        if (n < 0 || n == N_CHAT || !trong(n, main.a.j, main.a.k, 9)) {
            return false;
        }
        if (n <= N_PHAI) {
            chamPhim = n;
            return true;
        }
        main.a.g = false;
        main.a.f = false;
        try {
            if (n == N_THU) {
                hc.b().a(main.a.r);         // man su kien / thu (nhu muc "Su kien" trong bang bieu tuong)
            } else if (main.a.r instanceof ae) {
                hn me = main.GameMidlet.i;
                // cung dieu kien voi bang bieu tuong cua game (cd.f())
                if (me != null && (me.Q == 0 || me.Q == -5) && !ca.b && !ae.t) {
                    cd.b().d(n == N_CAMXUC ? 2 : 1, 0);     // 2 = "Cam xuc", 1 = "Hanh dong"
                }
            }
        } catch (Throwable e) {
        }
        return false;
    }

    // ---- dai chu chay thong bao (main.a.paint): ban HD khong ve nen toi, chu chay trong khoang trong giua 2 cum nut tren
    /** o nen cua dai (anh main.a.aa lat ngang) */
    public static void veDai(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            return;
        }
        g.drawImage(img, x, y, neo);
    }

    /** vach ke duoi dai */
    public static void vachDai(Graphics g, int x, int y, int w, int h) {
        if (hd()) {
            return;
        }
        g.fillRect(x, y, w, h);
    }

    /** vung cat cua dong chu chay */
    public static void clipDai(Graphics g, int x, int y, int w, int h) {
        if (hd()) {
            docLe();
            int a = leT, b = main.a.m - leP;
            if (System.currentTimeMillis() - vnLuc < 400 && daiX1 - daiX0 >= 60) {
                a = daiX0;
                b = daiX1;
            }
            g.setClip(a, y, b - a, h + 2);
            return;
        }
        g.setClip(x, y, w, h);
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
    /**
     * Chu de quan tri chon (may chu gui lenh 119 / 9 - xem DauTruong.tin): 0 khong (canh nhu game goc, KHONG vat trang
     * tri), 1 Giang sinh, 2 Tinh nhan, 3 Vui nhon, 4 Halloween, 5 cong vien hoa (chi vat trang tri chung, khong hieu ung
     * le hoi). Ban HD ve hieu ung + vat trang tri theo chu de; ban thuong bo qua.
     */
    public static int chuDe;

    public static void nenTg(ir o, Graphics g) {
        boolean hd = hd();
        if (hd) {
            try {
                // co cua canh: 1 ngoai troi (ban do co nen troi / rung), 2 co su song mat dat, 4 ban dem
                int co = ir.b != null ? 3 : 0;
                if (ir.j == 1) {
                    co |= 4;
                }
                g.setStrokeStyle(0x48A0 + co);
            } catch (Throwable e) {
            }
            if (DUMP && main.a.r != manCu) {
                manCu = main.a.r;
                System.out.println("[hd] man " + (manCu == null ? "null" : manCu.getClass().getName()) + " ban do " + ir.a);
                dumpMap = -1;
            }
            if (DUMP && dumpMap != ir.a && ir.l != null && ir.l.size() > 0) {
                dumpMap = ir.a;
                StringBuffer sb = new StringBuffer("[hd] vat ban do " + ir.a + " (rong " + ir.e * ir.i + ", cam " + Cam.x() + "):");
                for (int i = 0; i < ir.l.size(); i++) {
                    Object v = ir.l.elementAt(i);
                    if (v instanceof cc) {
                        cc c = (cc) v;
                        sb.append(' ').append(c.f).append('@').append(c.aw).append(',').append(c.ax);
                    }
                }
                System.out.println(sb.toString());
            }
            try {
                g.setStrokeStyle(0x4870 + (chuDe >= 1 && chuDe <= 4 ? chuDe : 0));     // bao chu de cho may ve
                g.setStrokeStyle(0x4852);                                              // sap ve nen
            } catch (Throwable e) {
            }
        }
        if (hd && ir.b == null && !bongTat) {
            try {
                HdPhong.camera();       // phong trong nha: camera khong lo khoang den phia tren tran
            } catch (Throwable e) {
            }
        }
        try {
            o.b(g);     // nen (o gach mat dat): giu phang, khong do bong (chi nhuom mau theo chu de)
        } catch (Throwable e) {
            loiVe("nen", o, null, e);
        }
        if (hd) {
            try {
                // ban do ngoai troi hep hon man hinh (vd khu sinh thai rong 504 tren man 540): bao le trong 2 ben de may ve
                // lat guong mep NEN ra le (truoc khi ve vat the) - canh kin man hinh, khong con 2 dai den
                int cx = Cam.x();
                if (ir.b == null) {
                    if (!bongTat) {
                        HdPhong.ve(g);  // phong trong nha nho hon man hinh: noi rong san + tuong ra kin man hinh (khong con dai den)
                        if (DUMP && phongIn != ir.a) {
                            phongIn = ir.a;
                            System.out.println(HdPhong.ta());
                        }
                    }
                } else if (cx < 0 && !bongTat) {
                    int trai = -cx;
                    int phai = main.a.m - ir.e * ir.i * (js.ay <= 0 ? 1 : js.ay) - trai;
                    if (phai < 0) {
                        phai = 0;
                    }
                    if (trai <= 160 && phai <= 160) {
                        g.setStrokeStyle(0x4D000000 | (trai << 12) | phai);
                    }
                }
            } catch (Throwable e) {
            }
            try {
                g.setStrokeStyle(0x4850);
            } catch (Throwable e) {
            }
            HdTrangTri.capNhat();   // vat trang tri / cho chup anh cua ban HD (chen vao danh sach vat the cua ban do)
            if (!bongTat && (main.a.r instanceof ae || main.a.r instanceof go)) {
                veChan(g);
            }
        }
    }

    // ---- vong sang duoi chan nhan vat cua minh + bui chan khi di (ve ngay sau nen, duoi moi vat the)
    private static Image vong;
    private static Image[] bui = new Image[4];
    private static boolean chanThu;
    private static final int BUI_N = 6;
    private static int[] buiX = new int[BUI_N], buiY = new int[BUI_N];
    private static long[] buiTu = new long[BUI_N];
    private static int buiI, chanX = -9999, chanY, chanMap = -1;
    private static long buiCuoi;

    private static void veChan(Graphics g) {
        try {
            if (!chanThu) {
                chanThu = true;
                try {
                    vong = Image.createImage("/hdx/p_vong.png");
                    for (int i = 0; i < 4; i++) {
                        bui[i] = Image.createImage("/hdx/p_bui_" + i + ".png");
                    }
                } catch (Throwable e) {
                }
            }
            hn p = main.GameMidlet.i;
            if (p == null) {
                return;
            }
            int x = p.aw * js.ay, y = p.ax * js.ay;
            long now = System.currentTimeMillis();
            if (ir.a != chanMap) {
                chanMap = ir.a;         // sang ban do khac: bo bui cu
                for (int i = 0; i < BUI_N; i++) {
                    buiTu[i] = 0;
                }
                chanX = x;
                chanY = y;
            }
            int dx = x - chanX, dy = y - chanY;
            if ((dx != 0 || dy != 0) && dx > -40 && dx < 40 && dy > -40 && dy < 40 && now - buiCuoi > 170) {
                buiCuoi = now;
                buiX[buiI] = x - (dx > 0 ? 5 : dx < 0 ? -5 : 0);
                buiY[buiI] = y - 1;
                buiTu[buiI] = now;
                buiI = (buiI + 1) % BUI_N;
            }
            chanX = x;
            chanY = y;
            if (vong != null) {
                g.setStrokeStyle(0x4883);           // nhip nho (to / nho nhe)
                g.drawImage(vong, x, y - 1, 3);
                g.setStrokeStyle(0x4880);
            }
            for (int i = 0; i < BUI_N; i++) {
                long t = now - buiTu[i];
                if (buiTu[i] == 0 || t < 0 || t >= 440) {
                    continue;
                }
                Image im = bui[(int) (t / 110)];
                if (im != null) {
                    g.drawImage(im, buiX[i], buiY[i] - (int) (t / 110), 3);
                }
            }
        } catch (Throwable e) {
        }
    }

    /**
     * ea.a(Graphics): ve BAN DO THANH PHO (ea.b: o gach, bieu tuong khu, may, ten khu - khong qua ir). Ban HD: bao may ve
     * day la canh ngoai troi (o gach co van noi, nhuom theo chu de), ve xong thi hau ky canh: bong may troi, nuoc lap lanh,
     * nang / trang, toi goc. Do doi cua Graphics luc ve xong (vi tri cuon ban do) giu nguyen de hieu ung bam theo ban do.
     */
    public static void veTp(ea o, Graphics g) {
        if (!hd()) {
            o.b(g);
            return;
        }
        try {
            g.setStrokeStyle(0x4870 + (chuDe >= 1 && chuDe <= 4 ? chuDe : 0));
            g.setStrokeStyle(0x48A0 + 1 + (ir.j == 1 ? 4 : 0));     // ngoai troi, khong co su song mat dat
            g.setStrokeStyle(0x4852);
        } catch (Throwable e) {
        }
        o.b(g);
        try {
            int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
            g.setClip(-g.getTranslateX(), -g.getTranslateY(), main.a.m, main.a.n);
            if (o.c > 0 && o.c <= 80) {
                // man hinh rong hon ban do (may man rat dai): ban do nam giua, 2 le hep -> lat guong mep ban do ra le
                g.setStrokeStyle(0x4D000000 | (o.c << 12) | o.c);
            }
            g.setStrokeStyle(0x4850);
            g.setStrokeStyle(0x4851);
            g.setClip(cx, cy, cw, ch);
        } catch (Throwable e) {
        }
    }

    /**
     * ir.d(g) ve 1 vat the TINH cua ban do (cay, ghe, nha... - danh sach ir.l): bao may ve HD de xu ly theo chu de (tuyet
     * dong tren dinh, tan cay doi mau la thu) roi tra ve che do vat the dong. PatchFarmFx doi cho goi W.a(g) sang day.
     */
    /**
     * go.l() (cham trong nong trai): moi cho goi Vector.elementAt doi sang ham nay. Game goc: cham vao O DAT CHUA MUA (o co bien
     * "Mua", loai o 51, so thu tu = so o dang co) -> go.c.elementAt(so o) nem loi ngay luc NHAN, co "vua nhan" khong duoc xoa
     * -> vong lap game nem loi lien tuc (may dung hinh, nong may) cho toi luc nha tay; nha tay lai nem tiep -> khong bao gio
     * mua duoc dat bang cham ("K mua dc dat"). Chi so ngoai danh sach o dat -> tra 1 o gia nam dung o vua cham: game di toi
     * canh o do roi tu mo hop mua dat (go.t(): so thu tu = so o dang co -> hoi may chu gia mua).
     */
    public static Object phanTuDat(java.util.Vector v, int i) {
        if (v != null && v == go.c && (i < 0 || i >= v.size())) {
            cf o = new cf();
            try {
                int n = ir.i * gy.Y;
                o.aw = (main.a.h + Cam.x()) / n * ir.i + ir.i / 2;
                o.ax = (main.a.i + Cam.y()) / n * ir.i + ir.i / 2;
                o.a = o.aw / ir.i;
                o.b = o.ax / ir.i;
                o.c = -1;
            } catch (Throwable e) {
            }
            return o;
        }
        return v.elementAt(i);
    }

    // ---- LUOI AN TOAN khi ve (yeu cau 16 "thi thoang di chuyen bi loi mat hinh"): 1 vat / 1 mon do nem loi luc ve thi game
    // goc bo do ca khung (cac vat phia sau, nhan vat, toan bo nut deu mat). Moi vat ve trong try rieng: vat loi bi bo qua,
    // phan con lai cua khung van ve du; loi duoc ghi vao nhat ky cua app (log.txt) kem lop / ten / ma mon de tim du lieu hong.
    private static int loiVeDem;
    private static long loiVeLuc;

    static void loiVe(String noi, Object o, String them, Throwable e) {
        try {
            loiVeDem++;
            long nay = System.currentTimeMillis();
            if (loiVeDem > 30 && nay - loiVeLuc < 5000) {
                return;                         // khong lam day nhat ky: 30 dong dau, sau do toi da 1 dong / 5 giay
            }
            loiVeLuc = nay;
            StringBuffer sb = new StringBuffer("[hd] LOI VE #").append(loiVeDem).append(' ').append(noi).append(' ')
                    .append(o == null ? "null" : o.getClass().getName());
            if (o instanceof js) {
                js v = (js) o;
                sb.append(" az ").append(v.az).append(" @").append(v.aw).append(',').append(v.ax);
            }
            if (o instanceof gx) {
                gx v = (gx) o;
                sb.append(" id ").append(v.w).append(" ten ").append(v.x).append(" H ").append(v.H).append(" khung ").append(v.y)
                        .append(" K ").append(v.K);
            }
            if (o instanceof hn) {
                hn h = (hn) o;
                sb.append(" S ").append(h.S).append(" thu ").append(h.V).append(" mac");
                for (int i = 0; h.e != null && i < h.e.size() && i < 24; i++) {
                    sb.append(' ').append(((hx) h.e.elementAt(i)).a);
                }
            }
            if (o instanceof ld) {
                ld l = (ld) o;
                sb.append(" ma ").append(l.g).append(" goc ").append(l.f).append(" lop ").append(l.j);
            }
            if (them != null) {
                sb.append(' ').append(them);
            }
            sb.append(" ban do ").append(ir.a).append(": ").append(e);
            System.out.println(sb.toString());
            if (loiVeDem <= 3) {
                e.printStackTrace();
            }
        } catch (Throwable e2) {
        }
    }

    /** ir.d(g): vat thuoc danh sach thu 3 (ir.n) - chi boc an toan */
    public static void veKhac(js o, Graphics g) {
        try {
            o.a(g);
        } catch (Throwable e) {
            loiVe("vat3", o, null, e);
        }
    }

    /** hn.a(Graphics, x, y, boolean) (hinh nhan vat trong bang / phong game): ve 1 mon do - chi boc an toan */
    public static void vePhanAn(ld p, Graphics g, int a, int x, int y, int d) {
        try {
            p.a(g, a, x, y, d);
        } catch (Throwable e) {
            loiVe("mon(xem)", p, "khung " + a, e);
        }
    }

    public static void veVat(js o, Graphics g) {
        if (!hd()) {
            try {
                o.a(g);
            } catch (Throwable e) {
                loiVe("vat", o, null, e);
            }
            return;
        }
        try {
            g.setStrokeStyle(0x4853);
            if (!bongTat) {
                g.setStrokeStyle(0x4A000000 | ((o.ax * js.ay) & 0xFFFF));  // duong chan: may ve do bong xuong mat dat
            }
        } catch (Throwable e) {
        }
        try {
            o.a(g);
        } catch (Throwable e) {
            loiVe("vat", o, null, e);
        }
        try {
            if (ir.j == 1 && o instanceof cc && laDen(((cc) o).f)) {
                den(g, 1, o.aw * js.ay, o.ax * js.ay);
            }
            g.setStrokeStyle(0x4850);
        } catch (Throwable e) {
        }
    }

    /** vat the cua ban do (cc.f) la cot den duong: ban dem toa sang */
    static boolean laDen(int f) {
        return f == 845;
    }

    /** bao may ve 1 nguon sang ban dem tai chan vat (x, y theo toa do dang ve): kieu 1 cot den cao, 2 den nho, 3 den vua */
    static void den(Graphics g, int kieu, int x, int y) {
        try {
            if (x < -1000 || x > 3000 || y < -250 || y > 760) {
                return;
            }
            g.setStrokeStyle(0x4C000000 | ((kieu & 3) << 22) | (((x + 1024) & 0xFFF) << 10) | ((y + 256) & 0x3FF));
        } catch (Throwable e) {
        }
    }

    /**
     * ir.d(g) ve 1 vat the DONG (danh sach ir.m: nhan vat, thu nuoi, boss...). Nhan vat (hn) tu bao duong chan cho tung mon
     * do (vePhan) - ten / bong thoai khong do bong; cac vat dong khac bao duong chan cho ca luot ve.
     */
    public static void veDong(js o, Graphics g) {
        if (!hd() || bongTat || o instanceof hn || o.az == 7) {
            try {
                o.a(g);
            } catch (Throwable e) {
                loiVe("dong", o, null, e);
                try {
                    g.setStrokeStyle(0x4880);       // nhan vat loi giua chung: tra may ve ve trang thai thuong
                    g.setStrokeStyle(0x4854);
                } catch (Throwable e2) {
                }
            }
            return;
        }
        try {
            g.setStrokeStyle(0x4A000000 | ((o.ax * js.ay) & 0xFFFF));
        } catch (Throwable e) {
        }
        try {
            o.a(g);
        } catch (Throwable e) {
            loiVe("dong", o, null, e);
        }
        try {
            g.setStrokeStyle(0x4854);
        } catch (Throwable e) {
        }
    }

    /*
     * "Chuyen dong song" cho do KHONG co khung hoat hinh (lop ld thuong, khong phai fa): canh / duoi / do deo lung (lop 5)
     * vay nhe, hao quang (lop 3) phap phong, thu cung (db ve, lop -1) nhun. May ve HD lam bien dang luc ve; ban thuong
     * goi thang ham goc. PatchFarmFx doi cho goi ld.a o hn.a(Graphics) va db.a(Graphics) sang 2 ham nay.
     */
    public static void vePhan(ld p, Graphics g, int a, int x, int y, int d) {
        if (p == null) {
            return;                             // game goc: mat bieu cam tro vao mon khong co -> null -> ca khung hong
        }
        if (!hd()) {
            try {
                p.a(g, a, x, y, d);
            } catch (Throwable e) {
                loiVe("mon", p, "khung " + a, e);
            }
            return;
        }
        int kieu = 0;
        if (!(p instanceof fa)) {
            kieu = p.j == 5 ? 1 : p.j == 3 ? 3 : p.j == -1 ? 2 : 0;        // canh / hao quang / thu cung
        }
        try {
            if (p.j != 3 && !bongTat) {
                // duong chan: mon do nay do bong xuong dat; pha nhip tho rieng cua tung nguoi (theo x: di thi nhun nhe)
                g.setStrokeStyle(0x4B000000 | (((x * 32 / 3) & 0xFF) << 16) | (y & 0xFFFF));
            }
            if (kieu != 0) {
                g.setStrokeStyle(0x4880 + kieu);
            }
        } catch (Throwable e) {
        }
        try {
            if (THU_LOI && p.j == 20 && (main.a.l & 7) == 0) {
                throw new RuntimeException("thu loi ve (J2N_THULOI)");      // CHI ban thu Linux: kiem luoi an toan
            }
            p.a(g, a, x, y, d);
        } catch (Throwable e) {
            loiVe("mon", p, "khung " + a, e);
        }
        try {
            g.setStrokeStyle(0x4880);
            g.setStrokeStyle(0x4854);
        } catch (Throwable e) {
        }
    }

    public static void veThu(ld p, Graphics g, int a, int b, int c) {
        if (p == null) {
            return;
        }
        if (!hd() || p instanceof fa) {
            try {
                p.a(g, a, b, c);
            } catch (Throwable e) {
                loiVe("thu", p, null, e);
            }
            return;
        }
        try {
            g.setStrokeStyle(0x4882);
        } catch (Throwable e) {
        }
        try {
            p.a(g, a, b, c);
        } catch (Throwable e) {
            loiVe("thu", p, null, e);
        }
        try {
            g.setStrokeStyle(0x4880);
        } catch (Throwable e) {
        }
    }

    private static Object manCu, manPhim;
    private static final boolean DUMP = false;      // true: in danh sach vat the cua ban do moi lan vao (khao sat ma vat)
    private static int dumpMap = -1;

    /** man hinh hien tai khong do bong (ban do thanh pho: bong bong ten khu, may la vat "bay") - nenTg dat moi khung */
    static boolean bongTat;
    static int phongIn = -1;

    // ---- gon song tai cho vua cham vao ban do (phan hoi cham cho ban khong co nut ao)
    private static int gonX, gonY;
    private static long gonTu;

    /** KeyTap.g(): vua cham man hinh tai (x, y) */
    // ---- go loi tren ban thu Linux (J2N_GOLOI=1): in trang thai cham / man hinh ra log; may that khong co bien nay
    static final boolean GO = goLoi();
    /** CHI ban thu Linux (bien moi truong J2N_THULOI=1; may that khong doc duoc bien nay): co y nem loi khi ve ao */
    static final boolean THU_LOI = thuLoi();

    private static boolean thuLoi() {
        try {
            return "1".equals(System.getProperty("j2n.env:J2N_THULOI"));
        } catch (Throwable e) {
            return false;
        }
    }
    private static long goLuc;
    private static js goP;

    private static boolean goLoi() {
        try {
            return "1".equals(System.getProperty("j2n.env:J2N_GOLOI"));
        } catch (Throwable e) {
            return false;
        }
    }

    private static js goP2;
    private static int goX = -9999, goY, goCx, goCy, goTx, goTy;
    private static long goDiLuc;

    /** CHI ban thu Linux (GOLOI=1): sau moi vong logic (TocDo.ve) - theo doi vat dang duoc chi va BUOC DI cua nhan vat / may quay */
    static void goSauLogic() {
        try {
            if (ir.p != goP2) {
                goP2 = ir.p;
                System.out.println("[go] (logic) p -> " + (goP2 == null ? "null" : goP2.getClass().getName() + " az " + goP2.az + " @" + goP2.aw + ","
                        + goP2.ax) + " l " + main.a.l);
            }
            hn me = main.GameMidlet.i;
            if (me != null && (main.a.r instanceof ae || main.a.r instanceof go)) {
                int cx = Cam.x(), cy = Cam.y();
                long nay = System.currentTimeMillis();
                if (me.aw != goX || me.ax != goY || cx != goCx || cy != goCy) {
                    System.out.println("[di] l " + main.a.l + " +" + (nay - goDiLuc) + "ms me " + me.aw + "," + me.ax + " d " + (me.aw - goX) + ","
                            + (me.ax - goY) + " cam " + cx + "," + cy + " d " + (cx - goCx) + "," + (cy - goCy) + " man " + (me.aw - cx) + ","
                            + (me.ax - cy) + " Q " + me.Q + " K " + me.K + " H " + me.H + " au " + me.au);
                }
                goX = me.aw;
                goY = me.ax;
                goCx = cx;
                goCy = cy;
                goDiLuc = nay;
                for (int i = 0; ir.m != null && i < ir.m.size(); i++) {
                    Object v = ir.m.elementAt(i);
                    if (v instanceof gr && ((gr) v).r == me) {
                        gr t = (gr) v;
                        if (t.aw != goTx || t.ax != goTy) {
                            System.out.println("[thu] l " + main.a.l + " thu " + t.aw + "," + t.ax + " d " + (t.aw - goTx) + "," + (t.ax - goTy)
                                    + " cach chu " + (t.aw - me.aw) + "," + (t.ax - me.ax) + " H " + t.H + " G " + t.G + " e " + t.e + " cho "
                                    + (t.t == null ? -1 : t.t.size()));
                            goTx = t.aw;
                            goTy = t.ax;
                        }
                    }
                }
            }
        } catch (Throwable e) {
        }
    }

    private static int goDiemDem;
    static boolean goTheo;
    static int goCuoi;

    /** CHI ban thu: ma cua lenh goi / doc mang vua XONG trong ham dang theo doi */
    public static void goDiem2(int ma) {
        if (goTheo) {
            goCuoi = ma;
        }
    }

    /** CHI ban thu (PatchFarmFx -Dgoloi=1): diem theo doi chen trong ir.b() / ir.i(int) */
    public static void goDiem(int ma) {
        if (GO && goDiemDem < 300 && main.a.r instanceof ae && ir.a == 103 && main.a.l % 4 == 2) {
            goDiemDem++;
            System.out.println("[go] diem " + ma + " l " + main.a.l + " map " + ir.a + " man " + (main.a.r == null ? "null" : main.a.r.getClass().getName()));
        }
    }

    static void goIn(String dau) {
        if (!GO) {
            return;
        }
        try {
            hn me = main.GameMidlet.i;
            System.out.println("[go] " + dau + " man " + (main.a.r == null ? "null" : main.a.r.getClass().getName()) + " H " + main.a.H
                    + " g " + main.a.g + " e " + main.a.e + " f " + main.a.f + " hi " + main.a.h + "," + main.a.i + " jk " + main.a.j + ","
                    + main.a.k + " t " + (main.a.t != null) + " v " + (main.a.v != null) + " A " + (main.a.A != null) + " D "
                    + (main.a.D != null) + " ae.t " + ae.t + " Z " + main.a.Z + " mn " + main.a.m + "x" + main.a.n + " q " + main.a.q
                    + " T " + main.a.T + " cam " + Cam.x() + "," + Cam.y() + " map " + ir.a + " " + ir.e + "x" + ir.f + " o " + ir.i
                    + (me == null ? "" : " me " + me.aw + "," + me.ax + " Q " + me.Q + " av " + (me.av == null ? "null" : me.av.a + "," + me.av.b)));
            if (dau.startsWith("khung") && main.a.r != null) {
                StringBuffer sb = new StringBuffer("[go]   phim [" + nhan(main.a.r.ac) + "|" + nhan(main.a.r.ad) + "|" + nhan(main.a.r.ae) + "] p "
                        + (ir.p == null ? "null" : ir.p.getClass().getName() + " az " + ir.p.az + " @" + ir.p.aw + "," + ir.p.ax) + " ae.h "
                        + (ae.h == null ? "null" : ae.h.x + " w " + ae.h.w) + " goN " + go.N + " goE " + go.E + " goy " + go.y + " l " + main.a.l
                        + " cz " + (cz.a == main.a.r) + " cd " + (cd.a == main.a.r) + " m:");
                if (ir.m != null && me != null) {
                    for (int i = 0; i < ir.m.size() && i < 24; i++) {
                        Object o = ir.m.elementAt(i);
                        if (o instanceof js) {
                            js j = (js) o;
                            sb.append(' ').append(o.getClass().getName()).append('/').append(j.az).append('@').append(j.aw).append(',').append(j.ax);
                            if (o instanceof hn) {
                                hn h = (hn) o;
                                sb.append(h.L ? "L" : "").append(':').append(h.x).append("[Q").append(h.Q).append(" R").append(h.R).append(" au")
                                        .append(h.au).append(" CD").append(h.C).append(',').append(h.D).append(" av")
                                        .append(h.av == null ? "-" : h.av.a + "," + h.av.b).append(" H").append(h.H).append(h.at == null ? "" : " at")
                                        .append(h.p ? " p" : "").append(']');
                            }
                        }
                    }
                }
                System.out.println(sb.toString());
            }
        } catch (Throwable e) {
            System.out.println("[go] loi " + e);
        }
    }

    public static void cham(int x, int y) {
        goIn("nhan " + x + "," + y);
        try {
            if (!hd()) {
                return;
            }
            chamLuc = System.currentTimeMillis();       // cho formTick: cham vao o nhap chu -> xin ban phim
            chamFx = x;
            chamFy = y;
            if (nutTai(x, y) >= 0) {
                return;                                 // cham vao nut giao dien: khong ve gon song
            }
            gonX = x + Cam.x();
            gonY = y + Cam.y();
            gonTu = System.currentTimeMillis();
        } catch (Throwable e) {
        }
    }

    private static void veGon(Graphics g) {
        long t = System.currentTimeMillis() - gonTu;
        if (t < 0 || t > 420) {
            return;
        }
        try {
            int r = 3 + (int) (t * 13 / 420);
            g.setColor(t < 220 ? 0xFFFFFF : 0xE8F6FF);
            g.drawArc(gonX - r, gonY - r / 2, 2 * r, r, 0, 360);
            if (t < 260) {
                g.setColor(0xFFE680);
                g.drawArc(gonX - r + 2, gonY - r / 2 + 1, 2 * r - 4, r - 2, 0, 360);
            }
        } catch (Throwable e) {
        }
    }

    public static void trenTg(ir o, Graphics g) {
        try {
            o.d(g);
        } catch (Throwable e) {
            loiVe("lop vat", o, null, e);       // du co loi van ket thuc doan the gioi + ve HUD
        }
        if (hd()) {
            veGon(g);
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
