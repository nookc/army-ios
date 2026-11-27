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

    /** gw.a(Graphics): bong chat - ban HD nam canh nut MENU o goc tren trai */
    public static void veChat(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            x = chatX();
            y = 14 + tren();
        }
        g.drawImage(img, x, y, neo);
    }

    /** tam bong chat o ban HD: ngay sau nut MENU (neu nut dang o tren) */
    static int chatX() {
        docLe();
        return menuKhung >= khung - 2 ? mnX + mnW + 20 : leT + 16;
    }

    /** do cao dai chu chay thong bao o mep tren (main.a.ab; 0 = khong co): nut MENU / bong chat tut xuong duoi dai */
    static int tren() {
        int t = main.a.ab;
        return t > 0 ? (t > 30 ? 30 : t) : 0;
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

    /** gw.a(Graphics): hinh thu + so thu o goc phai tren - ban HD lui vao trong le man hinh */
    public static void veMail(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            docLe();
            x -= gocP;
            y += 2;
        }
        g.drawImage(img, x, y, neo);
    }

    public static void chuMail(cs f, Graphics g, String s, int x, int y, int can) {
        if (hd()) {
            docLe();
            x -= gocP;
            y += 2;
        }
        f.a(g, s, x, y, can);
    }

    /** main.a.a(Graphics): nhan "12+" - ban HD xuong goc trai duoi (goc tren trai la nut MENU) */
    public static void ve12(Graphics g, Image img, int x, int y, int neo) {
        if (hd()) {
            formTick();
        }
        if (hd() && img != null) {
            docLe();
            x = gocT - 6;
            y = main.a.n - img.getHeight() - (traiDuoi && menuKhung < khung - 2 ? 26 : 3) - nang();
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
            return main.a.a(chatX() - 20, tren(), 40, 34);
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

    static int khung;               // dem khung co ve phim mem
    static int menuKhung = -9;      // khung gan nhat ve nut MENU o goc tren trai
    static int mnX, mnY, mnW, mnH;  // khung nut MENU
    static boolean chamMenu;        // vua cham nut MENU (gy.l() -> phimNao tra ve phim trai)
    static boolean traiDuoi;        // khung nay co phim mem trai o day man hinh

    /**
     * 3 phim mem (trai / giua / phai). Ban HD: ve nut kinh toi bo tron sau chu, lui vao trong le man hinh; phim trai ten
     * "Menu" thanh nut MENU o GOC TREN TRAI (bam vao thi bang menu xo xuong ngay duoi - xem menuY).
     */
    public static void phimMem(ew o, Graphics g, by l, by c, by r) {
        if (!hd()) {
            o.a(g, l, c, r);
            return;
        }
        docLe();
        khung++;
        if (DUMP && main.a.r != manPhim) {
            manPhim = main.a.r;
            System.out.println("[hd] man (phim mem) " + (manPhim == null ? "null" : manPhim.getClass().getName()));
        }
        boolean menu = l != null && l.a != null && (l.a.equals("Menu") || l.a.equals("MENU") || l.a.equals("menu"));
        traiDuoi = !menu && l != null && l.a != null && l.a.length() > 0;
        int x0 = 0, x2 = 0, nang = nang();
        boolean doi = false;
        try {
            x0 = main.a.ae[0].a;
            x2 = main.a.ae[2].a;
            main.a.ae[0].a = x0 + gocT - 2;
            main.a.ae[2].a = x2 - gocP + 2;
            for (int i = 0; i < 3; i++) {
                main.a.ae[i].b -= nang;
            }
            doi = true;
            int cao = 19, T = main.a.T, dem = 7;
            for (int i = menu ? 1 : 0; i < 3; i++) {
                by p = i == 0 ? l : i == 1 ? c : r;
                if (p == null || p.a == null || p.a.length() == 0 || main.a.ae[i] == null) {
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
                kieu(g, 3, x, y, tw + dem * 2, cao, cao / 2, 0x16324A);
            }
            if (menu) {
                int tw = main.a.L.a(l.a);
                mnW = tw + 18;
                mnH = 20;
                mnX = leT;
                mnY = 4 + tren();
                menuKhung = khung;
                kieu(g, 3, mnX, mnY, mnW, mnH, mnH / 2, 0x1B4A78);
                main.a.L.a(g, l.a, mnX + mnW / 2, mnY + mnH / 2 - gy.ag / 2, 2);
            }
        } catch (Throwable e) {
        }
        try {
            o.a(g, menu ? null : l, c, r);
        } finally {
            if (doi) {
                main.a.ae[0].a = x0;
                main.a.ae[2].a = x2;
                for (int i = 0; i < 3; i++) {
                    main.a.ae[i].b += nang;
                }
            }
        }
    }

    /** phim mem o day duoc nang len bao nhieu diem (may co thanh Home o mep duoi: le duoi > 0) */
    static int nang() {
        docLe();
        int n = leD - 4;
        return n < 0 ? 0 : n > 12 ? 12 : n;
    }

    /**
     * gy.l(): phim mem nao vua duoc cham (0 trai, 1 giua, 2 phai; -1 khong). Ban HD tu tinh theo vi tri cham (phim da lui
     * vao trong le / nang len nen o goc cua game khong con dung): cham nut MENU o goc tren = phim trai.
     */
    public static int phimNao(ew o) {
        if (!hd()) {
            return o.d();
        }
        if (chamMenu) {
            chamMenu = false;
            return 0;
        }
        int px = main.a.h, W = main.a.m;
        if (px < gocT + 86) {
            return 0;
        }
        if (px > W - gocP - 86) {
            return 2;
        }
        if (px > W / 2 - 30 && px < W / 2 + 30) {
            return 1;
        }
        return -1;
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
        if (kieu != 0) {
            return y - nang();          // bang ben phai / giua: nang theo phim mem
        }
        int t = 27 + tren();
        return y < t ? y : t;
    }

    /**
     * gy.l(): dai phim mem duoi day man hinh (ca be ngang, chia 3). Ban HD (man ngang, ban do cao gan bang man): chi nhan
     * cham quanh 3 nhan (goc trai, giua, goc phai) - phan con lai cua dai de cham vao ban do (o cua ra, vat the o hang
     * duoi cung khong bi phim mem che).
     */
    public static boolean vungPhimMem(int x, int y, int w, int h) {
        if (!hd()) {
            return main.a.b(x, y, w, h);
        }
        docLe();
        chamMenu = false;
        boolean menuTren = menuKhung >= khung - 2;
        if (menuTren && main.a.b(mnX - 6, 0, mnW + 12, mnY + mnH + 8)) {
            chamMenu = true;            // nut MENU o goc tren trai
            return true;
        }
        int nang = nang();
        if (!main.a.b(x, y - nang, w, h + nang)) {
            return false;
        }
        int px = main.a.h, W = main.a.m;
        if (px < gocT + 86) {
            return !menuTren;           // goc trai duoi chi la phim mem khi phim trai dang nam o duoi
        }
        return px > W - gocP - 86 || (px > W / 2 - 30 && px < W / 2 + 30);
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
     * Chu de quan tri chon (may chu gui lenh 119 / 9 - xem DauTruong.tin): 0 khong, 1 Giang sinh, 2 Tinh nhan, 3 Vui nhon,
     * 4 Halloween. Ban HD ve hieu ung + vat trang tri theo chu de; ban thuong bo qua.
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
                g.setStrokeStyle(0x4870 + (chuDe >= 0 && chuDe <= 7 ? chuDe : 0));     // bao chu de cho may ve
                g.setStrokeStyle(0x4852);                                              // sap ve nen
            } catch (Throwable e) {
            }
        }
        o.b(g);         // nen (o gach mat dat): giu phang, khong do bong (chi nhuom mau theo chu de)
        if (hd) {
            try {
                // ban do ngoai troi hep hon man hinh (vd khu sinh thai rong 504 tren man 540): bao le trong 2 ben de may ve
                // lat guong mep NEN ra le (truoc khi ve vat the) - canh kin man hinh, khong con 2 dai den
                int cx = Cam.x();
                if (cx < 0 && ir.b != null && !bongTat) {
                    int trai = -cx;
                    int phai = main.a.m - ir.e * ir.i * (js.ay <= 0 ? 1 : js.ay) - trai;
                    if (phai < 0) {
                        phai = 0;
                    }
                    if (trai <= 80 && phai <= 80) {
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
            g.setStrokeStyle(0x4870 + (chuDe >= 0 && chuDe <= 7 ? chuDe : 0));
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
    public static void veVat(js o, Graphics g) {
        if (!hd()) {
            o.a(g);
            return;
        }
        try {
            g.setStrokeStyle(0x4853);
            if (!bongTat) {
                g.setStrokeStyle(0x4A000000 | ((o.ax * js.ay) & 0xFFFF));  // duong chan: may ve do bong xuong mat dat
            }
        } catch (Throwable e) {
        }
        o.a(g);
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
            o.a(g);
            return;
        }
        try {
            g.setStrokeStyle(0x4A000000 | ((o.ax * js.ay) & 0xFFFF));
        } catch (Throwable e) {
        }
        o.a(g);
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
        if (!hd()) {
            p.a(g, a, x, y, d);
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
        p.a(g, a, x, y, d);
        try {
            g.setStrokeStyle(0x4880);
            g.setStrokeStyle(0x4854);
        } catch (Throwable e) {
        }
    }

    public static void veThu(ld p, Graphics g, int a, int b, int c) {
        if (!hd() || p instanceof fa) {
            p.a(g, a, b, c);
            return;
        }
        try {
            g.setStrokeStyle(0x4882);
        } catch (Throwable e) {
        }
        p.a(g, a, b, c);
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

    // ---- gon song tai cho vua cham vao ban do (phan hoi cham cho ban khong co nut ao)
    private static int gonX, gonY;
    private static long gonTu;

    /** KeyTap.g(): vua cham man hinh tai (x, y) */
    public static void cham(int x, int y) {
        try {
            if (!hd()) {
                return;
            }
            chamLuc = System.currentTimeMillis();       // cho formTick: cham vao o nhap chu -> xin ban phim
            chamFx = x;
            chamFy = y;
            if (y > main.a.n - 24 || (y < 30 && x < 140)) {
                return;
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
        o.d(g);
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
