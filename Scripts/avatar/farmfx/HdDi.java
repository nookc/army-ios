import main.GameMidlet;

/**
 * Ban HD: DI CHUYEN muot (ban HD khong co nut di chuyen ao - moi buoc di la "cham de di").
 *
 * Game goc: cham vao ban do -> hn.l() -> hn.m() tim duong BFS 4 huong qua TAM cac o (24 px) -> nhan vat di tung o, duong
 * di hinh chu L / bac thang (vd sang trai 1 o, len 3 o, roi sang phai 4 o du diem cham nam cheo ngay truoc mat). Moi lan
 * doi huong trai <-> phai may quay lai vung "nhin truoc" (main.a.m / 10 = 54 px o man ngang) sang phia kia: canh giat qua
 * giat lai ("di chuyen ca giat ca tung"). Thu cung (gr) di theo vet chan chu voi toc do 2 + Q, lai bi nhan voi suc khoe
 * hn.W / 100 (may chu gui 101 -> W = -1 -> 1 px / vong): di mot quang la thu cung tut lai rat xa.
 *
 * Ban HD (chi khi HdUi.hd()):
 *  - muot(hn): sau BFS, bo cac nut giua khi di THANG toi nut xa hon duoc (hanh lang rong 5 px toan o trong) -> duong di la
 *    vai doan thang; cac nut canh o dac biet (cua, loi ra, ghe...) giu nguyen de su kien cua o van xay ra dung cho.
 *  - sauBuoc(hn): di dung tren doan thang voi toc do deu (game goc cat phan le -> lech huong, nhanh cham theo huong).
 *  - camTruoc: may quay giua nhan vat (khong nhin truoc) -> doi huong khong lam canh giat.
 *  - buocThu(gr): thu cung chay thang toi dich, cang xa chu cang nhanh (bat kip chu), khong phu thuoc hn.W.
 * Ban thuong (doc) / J2ME: moi ham tra ve ngay - y nhu game goc.
 */
public final class HdDi {

    private HdDi() {
    }

    /** do.b(int): vung nhin truoc cua may quay (main.a.m / 10) -> ban HD = 0 (nhan vat o giua man) */
    /** cap mon do cua thu -> so toc do nhu ban goc tinh (1 thuong, 2 nhanh): moi ban, ca ban doc */
    public static int capThu(int cap) {
        return cap >= 10 ? 2 : 1;
    }

    /** aw.e(n) khong nem loi khi n <= 0 */
    public static int ngauNhien(int n) {
        return n <= 0 ? 0 : aw.e(n);
    }

    public static int camTruoc(int p) {
        return HdUi.hd() ? 0 : p;
    }

    private static int can(int v) {
        if (v <= 0) {
            return 0;
        }
        int r = v, t = (v + 1) >> 1;
        while (t < r) {
            r = t;
            t = (t + v / t) >> 1;
        }
        return r;
    }

    private static int kc(int dx, int dy) {
        return can(dx * dx + dy * dy);
    }

    /** loai o tai diem ban do (x, y); -9999 = ngoai ban do */
    private static int loai(int x, int y) {
        int T = ir.i;
        if (x < 0 || y < 0) {
            return -9999;
        }
        int cx = x / T, cy = y / T;
        if (cx >= ir.e || cy >= ir.f) {
            return -9999;
        }
        int k = cy * ir.e + cx;
        short[] g = ir.g;
        return g == null || k >= g.length ? -9999 : g[k];
    }

    private static final int LE = 5;

    /** di thang tu (x0, y0) toi (x1, y1) duoc khong: moi diem cach nhau 3 px tren doan + 4 diem le quanh no phai la o trong (80) */
    private static boolean thang(int x0, int y0, int x1, int y1) {
        int T = ir.i;
        int dx = x1 - x0, dy = y1 - y0;
        int L = kc(dx, dy);
        if (L <= 2) {
            return true;
        }
        int o0 = (y0 / T) * 1000 + x0 / T, o1 = (y1 / T) * 1000 + x1 / T;
        int n = L / 3 + 1;
        for (int s = 0; s <= n; s++) {
            int x = x0 + dx * s / n, y = y0 + dy * s / n;
            for (int k = 0; k < 5; k++) {
                int px = x + (k == 1 ? -LE : k == 2 ? LE : 0), py = y + (k == 3 ? -LE : k == 4 ? LE : 0);
                if (px < 0 || py < 0) {
                    return false;
                }
                int o = (py / T) * 1000 + px / T;
                if (o == o0 || o == o1) {
                    continue;                   // o cua 2 dau doan: nam tren duong BFS, loai gi cung duoc
                }
                if (loai(px, py) != 80) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Cuoi hn.m() (vua tim xong duong): rut gon mang nut hn.aR[0..au] (aR[0] = o dich, aR[au] = o xuat phat; game di qua tam
     * cac o aR[au - 1] .. aR[2] roi toi dung diem cham). Giu nguyen aR[0], aR[1] va o xuat phat.
     */
    public static void muot(hn o) {
        try {
            if (o != GameMidlet.i || !HdUi.hd() || o.aR == null || o.av == null || ir.g == null) {
                return;
            }
            int n = o.au;
            if (n < 3 || n >= o.aR.length) {
                return;                         // 0..2 nut: game goc da di thang toi diem cham
            }
            int T = ir.i, Y = gy.Y;
            short[] R = o.aR;
            int m = n - 2;                      // so nut giua: diem 1..m = tam o aR[n - 1] .. aR[2]; diem m + 1 = diem cham
            int[] X = new int[m + 2], Yy = new int[m + 2];
            boolean[] buoc = new boolean[m + 2];
            X[0] = o.aw;
            Yy[0] = o.ax;
            for (int k = 1; k <= m; k++) {
                int v = R[n - k];
                X[k] = (v & 0xFF) * T + T / 2;
                Yy[k] = (v >> 8) * T + T / 2;
            }
            X[m + 1] = o.av.a / Y;
            Yy[m + 1] = o.av.b / Y;
            // nut BAT BUOC (khong duoc bo): o cua no hoac o ke tiep tren duong khong phai o trong (80) - cua, loi ra, ghe...
            // (game xet su kien cua o KE TIEP moi khi toi 1 nut: phai dung canh o do nhu game goc)
            for (int k = 1; k <= m; k++) {
                int v = R[n - k], v2 = R[n - k - 1];
                boolean dacBiet = loai((v & 0xFF) * T + 1, (v >> 8) * T + 1) != 80 || loai((v2 & 0xFF) * T + 1, (v2 >> 8) * T + 1) != 80;
                if (k == m) {
                    int v0 = R[0];
                    dacBiet |= loai((v0 & 0xFF) * T + 1, (v0 >> 8) * T + 1) != 80 || loai(X[m + 1], Yy[m + 1]) != 80;
                }
                buoc[k] = dacBiet;
            }
            int[] giu = new int[m + 1];
            int ng = 0, cur = 0;
            while (cur < m + 1) {
                int tot = cur + 1, hong = 0;
                for (int k = cur + 2; k <= m + 1 && k - cur <= 60 && hong < 10; k++) {
                    if (buoc[k - 1]) {
                        break;
                    }
                    if (thang(X[cur], Yy[cur], X[k], Yy[k])) {
                        tot = k;
                        hong = 0;
                    } else {
                        hong++;
                    }
                }
                if (tot <= m) {
                    giu[ng++] = tot;
                }
                cur = tot;
            }
            if (ng >= m) {
                return;                         // khong bo duoc nut nao
            }
            short dau = R[n];
            // xep lai: [o dich, aR[1], nut giu gan dich nhat, ..., nut giu dau tien, o xuat phat]
            short[] moi = new short[ng];
            for (int k = 0; k < ng; k++) {
                moi[k] = R[n - giu[ng - 1 - k]];
            }
            for (int k = 0; k < ng; k++) {
                R[2 + k] = moi[k];
            }
            R[2 + ng] = dau;
            o.au = 2 + ng;
            if (HdUi.GO) {
                StringBuffer sb = new StringBuffer("[di] muot " + n + " -> " + (2 + ng) + " nut: (" + X[0] + "," + Yy[0] + ")");
                for (int k = 0; k < ng; k++) {
                    sb.append(" (").append(X[giu[k]]).append(',').append(Yy[giu[k]]).append(buoc[giu[k]] ? ")!" : ")");
                }
                System.out.println(sb.append(" -> (").append(X[m + 1]).append(',').append(Yy[m + 1]).append(')').toString());
            }
        } catch (Throwable e) {
        }
    }

    // ---- doan thang dang di cua nhan vat minh
    private static boolean dang, coTruoc;
    private static int x0, y0, x1 = -99999, y1, dai, daDi;
    private static int truocX, truocY, baoX, baoY;

    /** cuoi hn.b() (moi vong logic cua 1 nhan vat): nhan vat minh dang "cham de di" -> dat vi tri dung tren doan thang */
    public static void sauBuoc(hn o) {
        if (o != GameMidlet.i) {
            return;
        }
        try {
            if (HdUi.hd() && o.Q == -5 && !o.M && o.N == 0) {
                if (o.H == 0 && o.au > 0) {
                    o.H = 1;                    // vua toi 1 nut giua duong: van dang di (khong nhay ve dang dung 1 vong)
                }
                int cx = o.C, cy = o.D;
                if (!dang || cx != x1 || cy != y1) {
                    dang = true;
                    x0 = truocX;
                    y0 = truocY;
                    x1 = cx;
                    y1 = cy;
                    dai = coTruoc ? kc(x1 - x0, y1 - y0) : 0;
                    daDi = 0;
                    baoX = x0;
                    baoY = y0;
                }
                if (o.aw == cx && o.ax == cy) {
                    baoX = cx;                  // vua toi nut: game goc da bao vi tri nay cho may chu
                    baoY = cy;
                } else if (dai > 0) {
                    daDi += o.G;
                    if (dai - daDi > o.G) {
                        int dx = (x1 - x0) * daDi, dy = (y1 - y0) * daDi;
                        o.aw = x0 + (dx >= 0 ? dx + dai / 2 : dx - dai / 2) / dai;
                        o.ax = y0 + (dy >= 0 ? dy + dai / 2 : dy - dai / 2) / dai;
                    }
                    // game goc bao vi tri moi o (24 px); doan thang dai: bao deu moi 24 px de nguoi khac thay minh di lien tuc
                    if (kc(o.aw - baoX, o.ax - baoY) >= ir.i && dai - daDi >= ir.i && main.a.v == null
                            && (GameMidlet.e == 9 || GameMidlet.e == 11) && !ae.t) {
                        baoX = o.aw;
                        baoY = o.ax;
                        bf.a().a(o.aw, o.ax, o.K, o.N);
                        if (HdUi.GO) {
                            System.out.println("[di] bao " + o.aw + "," + o.ax);
                        }
                    }
                }
            } else {
                dang = false;
            }
            truocX = o.aw;
            truocY = o.ax;
            coTruoc = true;
        } catch (Throwable e) {
        }
    }

    /**
     * Dau gr.g() (thu cung chon dich ke tiep). Game goc: het moc theo chu thi lang thang +-10 px quanh CHO NO DANG DUNG, ke ca
     * khi con cach chu ca doan (chu dung lai giua 2 moc) -> cu "giat giat" tai cho mai. Ban HD: het moc ma con xa chu (> 35 px)
     * thi chay toi dung sau lung chu; hang moc dai qua (chu di xa) thi bo cac moc cu, chi giu 2 moc cuoi (cat duong ve phia chu).
     * Tra ve true = da dat dich (bo qua ham goc).
     */
    public static boolean dichThu(gr p) {
        try {
            if (!HdUi.hd() || p.n == null || p.r == null) {
                return false;
            }
            if (p.t != null && p.t.size() > 0) {
                while (p.t.size() > 2) {
                    p.t.removeElementAt(0);
                }
                return false;
            }
            if (kc(p.r.aw - p.aw, p.r.ax - p.ax) <= 35) {
                return false;
            }
            int n = 14 + aw.e(12);
            if (p.r.K == 0) {
                n = -n;
            }
            int x = p.r.aw + n;
            if (loai(x, p.r.ax) != 80) {
                x = p.r.aw;
            }
            p.n.a = x;
            p.n.b = p.r.ax;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Dau gr.e() (1 buoc di cua thu cung): ban HD tu buoc thang toi dich gr.n - cang xa chu cang nhanh (4 .. 9 px / vong; chu
     * di 4 px / vong), dang lang thang quanh chu thi di cham nhu game goc. Tra ve true = da xu ly (bo qua ham goc).
     */
    public static boolean buocThu(gr p) {
        try {
            if (!HdUi.hd() || p.n == null || p.r == null) {
                return false;
            }
            int xa = kc(p.r.aw - p.aw, p.r.ax - p.ax);
            int cho = p.t == null ? 0 : p.t.size();
            int g;
            if (cho == 0 && xa <= 45) {
                g = p.G < 1 ? 1 : p.G > 3 ? 3 : p.G;        // quanh quan canh chu
            } else {
                g = xa > 260 ? 9 : xa > 170 ? 7 : xa > 100 ? 6 : xa > 60 ? 5 : 4;
                if (cho > 2 && g < 6) {
                    g = 6;                                  // con nhieu moc phai di qua: nhanh len
                }
            }
            int dx = p.n.a - p.aw, dy = p.n.b - p.ax;
            int d = kc(dx, dy);
            if (d <= g) {
                p.aw = p.n.a;
                p.ax = p.n.b;
                try {
                    p.h();
                } catch (Throwable e) {
                    // gr.h() ket thuc bang G = 1 + aw.e(Q): thu cung cap 0 (Q = 0) -> Random.nextInt(0) nem loi (game goc: ca vong
                    // logic bi bo do - moi nhan vat khung 1 nhip). Den day trang thai "da toi noi" da dat xong; chi con thieu toc do.
                    p.G = 1;
                }
                return true;
            }
            int sx = dx * g, sy = dy * g;
            p.aw += (sx >= 0 ? sx + d / 2 : sx - d / 2) / d;
            p.ax += (sy >= 0 ? sy + d / 2 : sy - d / 2) / d;
            if (dx != 0) {
                p.K = dx > 0 ? (byte) 0 : gx.J;
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** vong quay so: toc do dau (ban goc 100 + luc, luc 0..180). Cao hon de vong quay nhanh ro, toi da 280 (14 do / nhip) */
    public static int quayDau(int j) {
        int v = j + 110;
        return v > 280 ? 280 : v < 120 ? 120 : v;
    }

    /** vong quay so: moi nhip giam 5 (ban goc giam 1) - quay 2 giay roi cham dan thay vi 5-14 giay */
    public static int quayGiam(int j) {
        return j - 5;
    }

    // ---------------------------------------------------------------- vong quay so: quay tiep / tu quay ngay tren man vong quay
    static boolean quayRoi, quayTrong, tuQuay;
    static long quayXong, quayGui;
    static by cmTu, cmDong;

    /** vao man vong quay */
    public static void quayMo() {
        tuQuay = false;
        quayXong = 0;
        quayGui = 0;
    }

    /** this.r.a() trong lenh 2 cua vong quay: chi roi man hinh khi nguoi choi bam "Dong" */
    public static void quayDong(hj r) {
        if (quayRoi && r != null) {
            r.a();
        }
    }

    /** gui 1 luot quay moi voi luc cua lan truoc (datLai: vong dang hien qua -> dat lai vong truoc) */
    static void quayNua(hw o, int s, int m, boolean datLai) {
        if (datLai) {
            quayTrong = true;
            try {
                o.a(2, 0);              // lenh goc: dat lai vong + hien qua bay len nguoi (khong roi vi quayRoi = false)
            } catch (Throwable e) {
                // buoc cuoi cua lenh goc (hien qua bay len) nem loi khi mon trung chua tai xong hinh: vong da dat lai roi -> quay tiep
            } finally {
                quayTrong = false;
            }
        }
        int luc = m < 96 ? 96 : m > 270 ? 270 : m;
        cx.a().a((short) s, luc - 90);
        main.a.i();
        quayGui = System.currentTimeMillis();
    }

    /** dau hw.a(lenh, _): true = da xu ly */
    public static boolean quayLenh(hw o, int lenh, int s, int m) {
        if (quayTrong) {
            return false;
        }
        try {
            if (lenh == 2) {                    // phim giua sau khi ra qua: "Quay tiep"
                quayNua(o, s, m, true);
                return true;
            }
            if (lenh == 33) {                   // "Dong"
                tuQuay = false;
                quayRoi = true;
                quayTrong = true;
                try {
                    o.a(2, 0);
                } catch (Throwable e) {
                } finally {
                    quayTrong = false;
                    quayRoi = false;
                }
                return true;
            }
            if (lenh == 32) {                   // "Tu quay" / "Dung tu quay"
                tuQuay = !tuQuay;
                quayXong = 0;
                quayGui = 0;
                return true;
            }
        } catch (Throwable e) {
        }
        return false;
    }

    /** dau hw.k() (moi nhip): dat nhan 3 phim + tu quay. t = phim "Quay" (chua quay), v = phim hien sau khi ra du 3 qua */
    public static void quayTick(hw o, int s, int m, by t, by v) {
        try {
            if (cmTu == null) {
                cmTu = new by("Tự quay", 32);
                cmDong = new by(kf.d, 33);
            }
            v.a = "Quay tiếp";
            cmTu.a = tuQuay ? "Dừng tự quay" : "Tự quay";
            o.ac = cmTu;
            o.ae = cmDong;
            if (!tuQuay) {
                return;
            }
            long now = System.currentTimeMillis();
            if (o.ad == v) {                    // da hien du 3 qua: nghi 1 giay cho nguoi choi nhin roi quay tiep
                if (quayXong == 0) {
                    quayXong = now;
                } else if (now - quayXong >= 1000) {
                    quayXong = 0;
                    quayNua(o, s, m, true);
                }
            } else if (o.ad == t) {             // vong dang dung cho
                quayXong = 0;
                if (quayGui == 0) {
                    quayNua(o, s, m, false);    // vua bat tu quay: quay luon
                } else if (now - quayGui > 5000) {
                    tuQuay = false;             // may chu khong cho quay (het xu / luong / ve, ruong day): dung tu quay
                    quayGui = 0;
                }
            } else {
                quayXong = 0;
            }
        } catch (Throwable e) {
        }
    }
}
