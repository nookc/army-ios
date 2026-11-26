/**
 * Ban phim ao cua app (lop b, bat / tat bang nut tay cam nho o goc tren).
 * Cham vao vung ban phim thi KHONG bat co "vua cham man choi" (main.a.g).
 * Truoc day: ban phim chi xoa co main.a.e, con main.a.g van bat -> menu icon (cd.l) thay g ma khong trung icon nao
 * nen tu dong ngay khi bam mui ten tren ban phim ao (khong chon duoc Ban than / Giao dich...).
 * PatchFarmFx thay "main.a.g = true" trong main.a.pointerPressed bang KeyTap.g(x, y).
 */
public final class KeyTap {

    private KeyTap() {
    }

    /**
     * Cuoi b.a(Graphics) (ve ban phim ao): ghi chu cho cac o truoc day de trong - phim mem trai "F1", phim mem phai
     * "F2", phim giua "OK" (cung kieu chu voi o "abc"). Dang go chu (ban phim T9) thi khong ghi.
     */
    public static void nhan(b p, javax.microedition.lcdui.Graphics g) {
        if (p == null || p.a || p.u == null || p.l <= 0 || p.f <= 0 || p.g <= 0 || main.a.K == null) {
            return;
        }
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.setClip(p.b, p.c, p.d, p.e);
        for (int i = 0; i < p.u.length; i++) {
            int k = p.u[i];
            String s = k == -6 ? "F1" : k == -7 ? "F2" : k == -5 ? "OK" : null;
            if (s == null) {
                continue;
            }
            main.a.K.a(g, s, p.b + i % p.l * p.f + p.f / 2, p.c + i / p.l * p.g + p.g / 2 - 5, 2);
        }
        g.setClip(cx, cy, cw, ch);
    }

    // ------------------------------------------------------------------ chu hoa co dau

    /**
     * Phong chu cua game (dh) khong co chu hoa co dau (Ư Ơ Ă Â Ê Ô Á À Ạ Ế...): gap la bo qua -> chu bien mat
     * ("Ơi" thanh " i"). PatchFarmFx thay dh: String.indexOf(char) bang ham nay: khong co thi lay chu thuong cung dau.
     */
    public static int chu(String bang, int c) {
        int i = bang.indexOf(c);
        if (i >= 0 || c < 0xC0) {
            return i;
        }
        int t = thuong(c);
        return t != c ? bang.indexOf(t) : -1;
    }

    /** chu hoa tieng Viet -> chu thuong (CLDC Character.toLowerCase chi lo chu ASCII) */
    public static int thuong(int c) {
        if (c >= 0xC0 && c <= 0xDE && c != 0xD7) {
            return c + 32;                          // À Á Â Ã È É Ê Ì Í Ò Ó Ô Õ Ù Ú Ý
        }
        if (c == 0x102 || c == 0x110 || c == 0x128 || c == 0x168 || c == 0x1A0 || c == 0x1AF) {
            return c + 1;                           // Ă Đ Ĩ Ũ Ơ Ư
        }
        if (c >= 0x1EA0 && c <= 0x1EF9 && (c & 1) == 0) {
            return c + 1;                           // Ạ Ả Ấ ... Ỹ
        }
        return c;
    }

    // ------------------------------------------------------------------ o dang go (cho KbHook ban app)

    /** o chu dang duoc chon (dx.v) gan nhat - KbHook (ban app) biet la o mat khau (dx.H == 2) de bao ban phim he thong */
    public static dx dangGo;
    private static long dangGoLuc;

    /** PatchFarmFx: dau dx.e() (moi khung, moi o chu dang hien) */
    public static void oChu(dx d) {
        if (d != null && d.d()) {
            dangGo = d;
            dangGoLuc = System.currentTimeMillis();
        }
    }

    /** o chu dang chon va con hien tren man (e() vua chay trong 0,5 giay); khong co -> null */
    public static dx oDangGo() {
        dx d = dangGo;
        return d != null && System.currentTimeMillis() - dangGoLuc < 500 ? d : null;
    }

    // ------------------------------------------------------------------ nhan ban do thanh pho

    private static final String[] NHAN = new String[16];
    private static final int[] NX = new int[16], NY = new int[16], NW = new int[16];
    private static int soNhan;

    /**
     * ea.b(Graphics) (ban do thanh pho): nhan cua khu nam ngoai man bi kep vao mep (vd "Khu ngoại ô" va "Nông trại"
     * cung hang, cung kep mep phai o man hep -> chong thanh "Khu nNông trại"). Nhan sau trung cho nhan truoc thi
     * day xuong tung dong cho toi khi het chong. Nhan dau tien ve lai = khung moi.
     */
    public static void nhanBanDo(cs f, javax.microedition.lcdui.Graphics g, String s, int x, int y, int neo) {
        if (f == null || s == null) {
            return;
        }
        if (soNhan > 0 && s.equals(NHAN[0])) {
            soNhan = 0;
        }
        int w = f.a(s);
        int h = Math.max(8, f.a() + 2);
        int l = neo == 2 ? x - w / 2 : neo == 1 ? x - w : x;
        // nhan kep mep van loi ra ngoai man vai diem ("Khu mua sắm" sat mep phai): dua han vao trong man
        int tx = g.getTranslateX();
        int phai = main.a.m - 2;
        if (l + tx + w > phai) {
            l = phai - w - tx;
        }
        if (l + tx < 2) {
            l = 2 - tx;
        }
        x = neo == 2 ? l + w / 2 : neo == 1 ? l + w : l;
        for (int lan = 0; lan < 8; lan++) {
            boolean chong = false;
            for (int i = 0; i < soNhan; i++) {
                if (l < NX[i] + NW[i] + 2 && NX[i] < l + w + 2 && y < NY[i] + h && NY[i] < y + h) {
                    chong = true;
                    break;
                }
            }
            if (!chong) {
                break;
            }
            y += h;
        }
        if (soNhan < NHAN.length) {
            NHAN[soNhan] = s;
            NX[soNhan] = l;
            NY[soNhan] = y;
            NW[soNhan] = w;
            soNhan++;
        }
        f.a(g, s, x, y, neo);
    }

    // ------------------------------------------------------------------ chu bay (+xu / -xu...) tren nhan vat

    /**
     * main.a.paint: chu bay ("-25000xu", "+1luong"...) ve sau cung, de len hop thoai / menu / bang (Auto quay so moi 1,5
     * giay mot lan -> chu bay chong len menu NPC, hop thong bao). Dang mo hop thoai / menu / danh sach / luoi thi khong
     * ve (chu van bay va tu het nhu cu), ve lai khi ve man choi.
     */
    public static void veNoi(cp c, javax.microedition.lcdui.Graphics g) {
        if (c == null) {
            return;
        }
        if (main.a.v != null || main.a.t != null || main.a.A != null
                || main.a.r instanceof cd || main.a.r instanceof cy || main.a.r instanceof dp) {
            return;
        }
        c.a(g);
    }

    // ------------------------------------------------------------------ danh sach co nhan vat (bang xep hang, ban be)

    /** chieu cao 1 dong danh sach (cy.l = caoHang() * gy.Y, goc 40): them cho trong giua cac nhan vat */
    public static int caoHang() {
        return 46;
    }

    /**
     * cy.a(Graphics): ve nhan vat cua 1 dong - cat theo o cua dong (cot nhan vat, chieu cao dong) de mu / toc to,
     * canh, do cam tay... khong de len dong tren / duoi va len chu ben phai. y = dau dong + cy.l - 5 * gy.Y.
     */
    public static void veHangAvatar(hn h, javax.microedition.lcdui.Graphics g, int x, int y, boolean b) {
        if (h == null) {
            return;
        }
        int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
        int s = gy.Y;
        int cao = caoHang() * s;
        int tren = y + 5 * s - cao;
        int trai = 10 * s + 4;
        int phai = 60 + (s - 1) * 30 - 2;
        g.clipRect(trai, tren + 1, phai - trai, cao - 2);
        h.a(g, x, y, b);
        g.setClip(ox, oy, ow, oh);
    }

    // ------------------------------------------------------------------ luoi do chi de xem (rương / đồ đang mặc nguoi khac)

    /** ma cua hang "chi xem" may chu gui (XemNguoi): 27 ruong do, 28 do dang mac */
    public static boolean chiXem(int shop) {
        return shop == 27 || shop == 28;
    }

    /** ae.a(...) mo luoi cua hang: chu tren nut cua tung mon (goc luon "Mua") */
    public static String nutShop(int shop) {
        return chiXem(shop) ? "Chi tiết" : kf.Z;
    }

    /**
     * Dau jx.a() (chon 1 mon trong luoi cua hang): luoi chi xem -> nhan vat xem truoc mac thu mon nay (giong goc) va
     * khung thong tin ghi tung dong may chu gui (ten, loai, han dung, so luong) thay cho "Cấp độ chính / Tiền / Tk game"
     * (tien cua chinh minh, khong lien quan). true = da xu ly, bo qua phan goc.
     */
    public static boolean thongTinXem(jx x) {
        if (x == null || !(x.b instanceof gt)) {
            return false;
        }
        gt t = (gt) x.b;
        if (dp.n && x.h == dp.j) {
            dangXem = chiXem(t.e);
        }
        if (!chiXem(t.e) || !dp.n || x.h != dp.j) {
            return false;
        }
        if (x.h == 0) {
            hinhXem = docHinh(t.d);      // mon dau luoi mang trang phuc nguoi duoc xem (dong an)
        }
        ld l = x.f;
        if (l != null && l.g == -1) {
            l = fx.a(x.g);
        }
        if (l == null || l.g == -1) {
            return true;                 // mon chua nap xong: khung sau ve lai (giong goc)
        }
        thu(l);
        dp.n();
        String s = t.d != null && t.d.length() > 0 ? t.d : l.l;
        int i = 0;
        while (s != null && i < s.length()) {
            int k = s.indexOf('\n', i);
            if (k < 0) {
                k = s.length();
            }
            String dong = s.substring(i, k);
            if (dong.indexOf('\u0001') < 0) {       // dong an (trim() cung xoa ky tu nay nen kiem truoc)
                dong = dong.trim();
                if (dong.length() > 0) {
                    dp.a(dong);
                }
            }
            i = k + 1;
        }
        return true;
    }

    /** trang phuc cua nguoi dang duoc xem (id mon) - null: thu tren nhan vat cua minh (nhu cua hang goc) */
    private static short[] hinhXem;

    /** dong an "\u0001id,id,..." trong chu cua mon (may chu chi gui cho app tu ban 19) */
    static short[] docHinh(String s) {
        if (s == null) {
            return null;
        }
        int k = s.indexOf('\u0001');
        if (k < 0) {
            return null;
        }
        int het = s.indexOf('\n', k);
        String ds = s.substring(k + 1, het < 0 ? s.length() : het);
        short[] tam = new short[32];
        int n = 0, so = 0;
        boolean coSo = false;
        for (int i = 0; i <= ds.length() && n < tam.length; i++) {
            char c = i < ds.length() ? ds.charAt(i) : ',';
            if (c >= '0' && c <= '9') {
                so = so * 10 + (c - '0');
                coSo = true;
            } else {
                if (coSo && so < 32768) {
                    tam[n++] = (short) so;
                }
                so = 0;
                coSo = false;
            }
        }
        if (n == 0) {
            return null;
        }
        short[] kq = new short[n];
        System.arraycopy(tam, 0, kq, 0, n);
        return kq;
    }

    /** mon thu cung dang chon trong luoi chi xem: khung xem truoc ve hinh con thu cung (khong ghep vao nguoi) */
    private static ld thuCung;

    /** nhan vat xem truoc (ae.C) mac thu mon l: tren trang phuc nguoi duoc xem, khong co thi tren nhan vat minh */
    static void thu(ld l) {
        thuCung = l.j == -1 ? l : null;
        short[] h = hinhXem;
        if (h == null) {
            ae.a(l);
            return;
        }
        hn c = new hn();
        c.K = 0;
        c.e = new java.util.Vector();
        boolean co = false;
        for (int i = 0; i < h.length; i++) {
            hx p = new hx(h[i]);
            ld lp = fx.a(p.a);
            if (lp != null && lp.j == l.j) {
                p.a = l.g;
                co = true;
            }
            c.a(p);
        }
        if (!co && l.j != -1) {
            c.a(new hx(l.g));
        }
        c.h();
        ae.C = c;
    }

    /**
     * dp.a(Graphics): ve nhan vat xem truoc (duoi luoi). Luoi chi xem: cat trong khung xem truoc (canh / toc / mu to
     * khong de len o luoi va chu), mon thu cung thi ve hinh con thu (thu nho vua khung) thay cho nguoi.
     */
    public static void vePreview(hn c, javax.microedition.lcdui.Graphics g, int x, int y, boolean b) {
        if (!dangXem) {
            if (c != null) {
                c.a(g, x, y, b);
            }
            return;
        }
        int ox = g.getClipX(), oy = g.getClipY(), ow = g.getClipWidth(), oh = g.getClipHeight();
        int s = gy.Y;
        g.clipRect(x - 30 * s, y - 45 * s + 1, 60 * s, 52 * s);
        ld t = thuCung;
        if (t != null && t.g != -1) {
            int o = 40 * s;
            boolean xong = false;
            if (t.g >= 2000) {
                f im = fx.b((short) t.h);
                if (im != null && im.d != -1 && im.a != null) {
                    javax.microedition.lcdui.Image n = im.b > o || im.c > o ? nho(t.h, im, o) : im.a;
                    if (n != null) {
                        g.drawImage(n, x, y - 20 * s, 3);
                        xong = true;
                    }
                }
            }
            if (!xong) {
                t.a(g, x, y - 20 * s, 3);
            }
        } else if (c != null) {
            c.a(g, x, y, b);
        }
        g.setClip(ox, oy, ow, oh);
    }

    /** bo dong an khoi chu hien cho nguoi choi */
    static String boAn(String s) {
        if (s == null) {
            return s;
        }
        int k = s.indexOf('\u0001');
        if (k < 0) {
            return s;
        }
        int het = s.indexOf('\n', k);
        String truoc = s.substring(0, k), sau = het < 0 ? "" : s.substring(het + 1);
        while (truoc.endsWith("\n")) {
            truoc = truoc.substring(0, truoc.length() - 1);
        }
        return sau.length() == 0 ? truoc : truoc + "\n" + sau;
    }

    // ------------------------------------------------------------------ hinh mon trong o luoi (cua hang, ruong)

    private static final java.util.Hashtable NHO = new java.util.Hashtable();

    /**
     * jx / fz .a(Graphics, x, y) (o luoi cua hang / ruong): ve hinh mon o giua o. Hinh mon moi (canh, hao quang, toc
     * to...) co khi to gap 2-3 lan o luoi -> de len cac o ben canh. To hon o thi ve ban thu nho vua o (giu ti le).
     */
    public static void veO(ld l, javax.microedition.lcdui.Graphics g, int x, int y, int neo) {
        if (l == null) {
            return;
        }
        if (l.g >= 2000) {
            f im = fx.b((short) l.h);
            int o = dp.e - 4 * gy.Y;
            if (im != null && im.d != -1 && im.a != null && o > 4 && (im.b > o || im.c > o)) {
                javax.microedition.lcdui.Image n = nho(l.h, im, o);
                if (n != null) {
                    g.drawImage(n, x, y, 3);
                    return;
                }
            }
        }
        l.a(g, x, y, neo);
    }

    static javax.microedition.lcdui.Image nho(int id, f im, int o) {
        Integer k = new Integer(id * 64 + o);
        Object c = NHO.get(k);
        if (c != null) {
            return (javax.microedition.lcdui.Image) c;
        }
        try {
            int w = im.b, h = im.c;
            int nw, nh;
            if (w >= h) {
                nw = o;
                nh = Math.max(1, h * o / w);
            } else {
                nh = o;
                nw = Math.max(1, w * o / h);
            }
            int[] src = new int[w * h];
            im.a.getRGB(src, 0, w, 0, 0, w, h);
            int[] dst = new int[nw * nh];
            for (int y = 0; y < nh; y++) {
                int y0 = y * h / nh, y1 = Math.max(y0 + 1, (y + 1) * h / nh);
                for (int x = 0; x < nw; x++) {
                    int x0 = x * w / nw, x1 = Math.max(x0 + 1, (x + 1) * w / nw);
                    int sa = 0, sr = 0, sg = 0, sb = 0, n = 0;
                    for (int yy = y0; yy < y1; yy++) {
                        for (int xx = x0; xx < x1; xx++) {
                            int p = src[yy * w + xx];
                            int a = p >>> 24;
                            sa += a;
                            sr += ((p >> 16) & 255) * a;
                            sg += ((p >> 8) & 255) * a;
                            sb += (p & 255) * a;
                            n++;
                        }
                    }
                    // net ro nhu hinh pixel goc: trong suot hoac dac (may J2ME cu khong ve duoc nua trong)
                    if (sa == 0 || sa / n < 96) {
                        dst[y * nw + x] = 0;
                    } else {
                        dst[y * nw + x] = 0xFF000000 | ((sr / sa) << 16) | ((sg / sa) << 8) | (sb / sa);
                    }
                }
            }
            javax.microedition.lcdui.Image kq = javax.microedition.lcdui.Image.createRGBImage(dst, nw, nh, true);
            if (NHO.size() > 80) {
                NHO.clear();
            }
            NHO.put(k, kq);
            return kq;
        } catch (Throwable t) {
            return null;
        }
    }

    /** luoi dang mo la luoi chi xem (ghi o thongTinXem khi chon mon) */
    public static boolean dangXem;

    /** dp.a(Graphics): dong dau luoi cua hang (goc: tien cua minh). Luoi chi xem: ghi huong dan */
    public static String dauShop(String s) {
        return dangXem ? "B\u1ea5m v\u00e0o t\u1eebng m\u00f3n \u0111\u1ec3 xem th\u1eed" : s;
    }

    /** gt.a(): bam nut cua 1 mon. Luoi chi xem: hien hop thong tin (OK), khong hoi Co / Khong. */
    public static void hoiShop(String s, ii h, int shop) {
        if (chiXem(shop)) {
            main.a.b(boAn(s));
            return;
        }
        main.a.a(s, h);
    }

    /**
     * Bau Cua (ja.l / ja.j, lan 34 "cuoc khong gioi han"): game goc chi cho bam dat 6 chip / van. Nay moi o toi da 127 chip
     * (so chip 1 o gui may chu bang 1 byte). o = ja.O (cac o kl, kl.b = so chip dang bam), v = o dang chon.
     */
    public static boolean chipO(java.util.Vector o, int v) {
        try {
            return ((kl) o.elementAt(v)).b < 127;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Bau Cua: tien bay giua 2 ghe (lenh 60). Game goc luu so tien vao short (dn.o) -> tren 32.767 hien so sai: giu so
     *  int o dn.c (truong khong dung toi o hieu ung nay) */
    public static void datTienBay(dn d, int n) {
        d.o = (short) n;
        d.c = n;
    }

    /** chu "+..." tren dong tien bay: so day du (phong so nay khong co dau cham - ve '.' thanh '0') */
    public static String tienBay(dn d) {
        return String.valueOf(d.c != 0 ? d.c : d.o);
    }

    /** lan cham nay da keo ngang la bai (khoa huong toi khi nhac tay) - xem keoDoc() */
    static boolean keoNgang;

    /**
     * Tien len (kd.l) / Phom (kg.l): thay main.a.l() (do lech doc tu luc cham). Game goc: lech doc qua 10 diem anh la
     * "chon / bo chon" la bai NGAY CA khi dang keo ngang de doi cho -> tren dien thoai ngon tay hoi xien la la bai khong
     * dich duoc ("danh bai khong sap xep duoc la bai"). Nay: keo ngang la chinh (ngang >= doc) hoac lan cham nay da keo
     * ngang qua 10 diem anh thi coi nhu khong lech doc, cho toi khi nhac tay.
     */
    public static int keoDoc() {
        int dx = main.a.k(), dy = main.a.l();
        int ax = Math.abs(dx), ay = Math.abs(dy);
        if (!keoNgang && ax > 10 && ax >= ay) {
            keoNgang = true;
        }
        return keoNgang || ax >= ay ? 0 : dy;
    }

    public static void g(int x, int y) {
        keoNgang = false;                // lan cham moi: chua keo ngang
        if (DauTruong.cham(x, y)) {
            return;                      // cham vao bang sung / nut / boss o dau truong
        }
        if (main.a.z != null) {
            // man doc: ban phim nam duoi (y >= chieu cao man choi); man ngang: ban phim nam ben phai
            if (main.a.G ? x >= main.a.m : y >= main.a.n) {
                return;
            }
        }
        main.a.g = true;
    }
}
