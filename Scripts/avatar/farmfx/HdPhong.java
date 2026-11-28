import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Ban HD: PHONG TRONG NHA (ban do khong co nen troi) nho hon man hinh -> noi rong san + tuong cua phong ra kin man hinh,
 * khong con dai den 2 ben / phia tren / phia duoi.
 *
 * Cach lam (tren luoi o gach, ve bang chinh bo o gach cua phong nen net nhu that):
 *  1) "loi" cua phong = hinh chu nhat cac hang / cot co tu 1/2 so o la o DAC (anh o khong trong suot, it diem den). Vien
 *     phong (nua o la den) va hang cua ra vao (gan nhu toan o den) nam ngoai loi.
 *  2) moi hang cua loi co "mau nen" lap theo chieu ngang cho phia TRAI va phia PHAI:
 *       - hang san (co o di lai duoc): chi xet cac O SAN o nua phong gan phia do (san ca ro: chu ky 2, san tron: 1) -> do
 *         vat ke sat tuong (tu, ke hang, chuong thu) khong bi lap ra ngoai;
 *       - hang tuong: o nhieu nhat cua tung lop du, chu ky 1..6 (den tran cach deu 3 o...), cham diem do phu x do thuan.
 *  3) moi o tren man hinh nam ngoai loi duoc ve bang o cua mau nen: san va tuong chay tiep ra toi mep man hinh, de len ca
 *     vien cu. Hang vien tran giu nguyen kieu, chi noi dai sang 2 ben. O dac san co phia duoi loi (cua ra vao) giu nguyen.
 *  4) camera doc khong len qua tran phong; phong thap hon man hinh thi can sat tran, phan du phia duoi la san.
 * Vung di lai cua nhan vat KHONG doi (ban do tren may chu va cac ban khac van nhu cu).
 */
public final class HdPhong {
    private static final int[] CHU_KY = {1, 2, 3, 4, 6};
    private static short[] mang;
    private static Object bo;
    private static int E, F, banDo = -1;
    private static boolean dung;
    private static int c0, c1, r0, r1, giua;
    private static short[][] mauT, mauP;            // mau nen cua tung hang cho phia trai / phia phai
    private static boolean[] ngoai;                 // o khong dac thong ra mep ban do (vien, goc den, khoang trong): ve de len
    private static boolean[] chungT, chungP;        // hang do, phia do dung mau san CHUNG cua ca phong (sA / sB / sPha)
    private static int sKieu, sA, sB, sPha;         // mau san chung: sKieu 0 khong co, 1 mot loai o (sA), 2 ca ro sA / sB
    private static byte[] dac = new byte[0];        // theo ma o: 0 chua xet, 1 dac, 2 co diem trong suot / nhieu diem den
    private static int[] diem;
    private static int trenDen;

    private HdPhong() {
    }

    private static boolean toi(int v) {
        return (v & 0xFF0000) <= 0x120000 && (v & 0xFF00) <= 0x1200 && (v & 0xFF) <= 0x12;
    }

    private static boolean docO(int ma) {
        ht t = ir.c;
        Image a = t.d;
        int w = t.a, h = t.b, x = (ma / t.c) * w, y = (ma % t.c) * h;
        if (ma < 0 || w <= 0 || h <= 0 || x + w > a.getWidth() || y + h > a.getHeight()) {
            return false;
        }
        if (diem == null || diem.length < w * h) {
            diem = new int[w * h];
        }
        a.getRGB(diem, 0, w, x, y, w, h);
        return true;
    }

    private static boolean oDac(int ma) {
        if (ma < 0) {
            return false;
        }
        if (ma >= dac.length) {
            byte[] m = new byte[ma + 64];
            System.arraycopy(dac, 0, m, 0, dac.length);
            dac = m;
        }
        if (dac[ma] == 0) {
            byte kq = 2;
            try {
                if (docO(ma)) {
                    int n = ir.c.a * ir.c.b, trong = 0, den = 0;
                    for (int i = n - 1; i >= 0; i--) {
                        int v = diem[i];
                        if ((v >>> 24) < 128) {
                            trong++;
                        } else if (toi(v)) {
                            den++;
                        }
                    }
                    if (trong * 50 <= n && den * 5 < n) {
                        kq = 1;
                    }
                }
            } catch (Throwable e) {
            }
            dac[ma] = kq;
        }
        return dac[ma] == 1;
    }

    /** o san (nhan vat di lai duoc): theo bang loai o cua game (80), bo cac ma trang tri 114..123; khong co bang thi theo ma */
    private static boolean oSan(int i) {
        int ma = ir.d[i];
        if (ma < 0 || (ma >= 114 && ma <= 123)) {
            return false;
        }
        short[] loai = ir.g;
        if (loai != null && loai.length == ir.d.length) {
            return loai[i] == 80;
        }
        return ma < 7 || (ma >= 44 && ma <= 55);
    }

    private static void tinh() {
        dung = false;
        short[] d = ir.d;
        mang = d;
        bo = ir.c;
        E = ir.e;
        F = ir.f;
        banDo = ir.a;
        dac = new byte[0];
        trenDen = 0;
        if (d == null || ir.c == null || E < 6 || F < 5 || d.length < E * F) {
            return;
        }
        boolean[] o = new boolean[E * F], co = new boolean[E * F], san = new boolean[E * F];
        for (int i = E * F - 1; i >= 0; i--) {
            o[i] = oDac(d[i]);
            co[i] = d[i] >= 0;
            san[i] = o[i] && oSan(i);
        }
        r0 = 0;
        r1 = F - 1;
        while (r0 < F && dem(o, r0, 0, E - 1, true) * 2 < E) {
            r0++;
        }
        while (r1 > r0 && dem(o, r1, 0, E - 1, true) * 2 < E) {
            r1--;
        }
        if (r1 - r0 < 3) {
            return;
        }
        int cao = r1 - r0 + 1;
        c0 = 0;
        c1 = E - 1;
        while (c0 < E && dem(o, c0, r0, r1, false) * 2 < cao) {
            c0++;
        }
        while (c1 > c0 && dem(o, c1, r0, r1, false) * 2 < cao) {
            c1--;
        }
        if (c1 - c0 < 4) {
            return;
        }
        giua = (c0 + c1) / 2;
        // o khong dac noi lien ra mep ban do (qua cac o khong dac khac) = vien phong / goc den / khoang trong -> se ve de len
        // bang mau nen; o toi mau nam giua phong (do vat) khong thong ra mep nen duoc giu nguyen
        ngoai = new boolean[E * F];
        int[] ngan = new int[E * F];
        int nNgan = 0;
        for (int i = 0; i < E * F; i++) {
            int c = i % E, r = i / E;
            if ((c == 0 || r == 0 || c == E - 1 || r == F - 1) && !o[i]) {
                ngoai[i] = true;
                ngan[nNgan++] = i;
            }
        }
        while (nNgan > 0) {
            int i = ngan[--nNgan], c = i % E;
            for (int k = 0; k < 4; k++) {
                int j = k == 0 ? i - E : k == 1 ? i + E : k == 2 ? i - 1 : i + 1;
                if (j < 0 || j >= E * F || (k == 2 && c == 0) || (k == 3 && c == E - 1) || ngoai[j] || o[j]) {
                    continue;
                }
                ngoai[j] = true;
                ngan[nNgan++] = j;
            }
        }
        mauT = new short[F][];
        mauP = new short[F][];
        chungT = new boolean[F];
        chungP = new boolean[F];
        sanChung(d, san);
        int sanDau = -1;
        for (int r = 0; r <= r1; r++) {
            if (r < r0) {
                // hang vien phia tren loi (tran): giu nguyen kieu cua no, chi noi dai sang 2 ben
                short[] m = mauHang(d, co, r, c0, c1, CHU_KY.length, 700);
                mauT[r] = mauP[r] = m != null ? m : new short[] {-1};
                continue;
            }
            int nSan = dem(san, r, c0, c1, true);
            if (nSan >= 2) {
                if (sanDau < 0) {
                    sanDau = r;
                }
                // hang san: mau cua cac o san o nua phong gan phia can noi (nua do khong du o san thi xet ca hang)
                mauT[r] = mauPhia(d, san, r, c0, giua);
                mauP[r] = mauPhia(d, san, r, giua + 1, c1);
                chungT[r] = thuocChung(mauT[r]);
                chungP[r] = thuocChung(mauP[r]);
            } else if (sanDau < 0) {
                // hang tuong
                mauT[r] = mauP[r] = mauHang(d, o, r, c0, c1, CHU_KY.length, 700);
                if (mauT[r] == null) {
                    return;
                }
            }
        }
        // hang nam trong vung san nhung bi do vat che het: muon mau cua hang san gan nhat (so le theo khoang cach: san ca ro)
        for (int r = r0; r <= r1; r++) {
            if (mauT[r] != null && mauP[r] != null) {
                continue;
            }
            int rn = -1;
            for (int xa = 1; xa <= r1 - r0 && rn < 0; xa++) {
                if (r + xa <= r1 && r + xa >= sanDau && sanDau >= 0 && mauT[r + xa] != null && dem(san, r + xa, c0, c1, true) >= 2) {
                    rn = r + xa;
                } else if (r - xa >= sanDau && sanDau >= 0 && mauT[r - xa] != null && dem(san, r - xa, c0, c1, true) >= 2) {
                    rn = r - xa;
                }
            }
            if (sKieu != 0 && sanDau >= 0 && r > sanDau) {
                mauT[r] = mauP[r] = new short[] {(short) sA};
                chungT[r] = chungP[r] = true;
                continue;
            }
            if (rn < 0) {
                short[] m = mauHang(d, o, r, c0, c1, CHU_KY.length, 700);
                if (m == null) {
                    return;
                }
                mauT[r] = mauP[r] = m;
                continue;
            }
            mauT[r] = dich(mauT[rn], rn - r);
            mauP[r] = dich(mauP[rn], rn - r);
        }
        if (r0 > 0) {
            trenDen = hangDen(mauT[0]);
        }
        dung = true;
    }

    /**
     * mau san CHUNG cua ca phong: 2 loai o san nhieu nhat xep ca ro (so le theo cot + hang) -> sKieu 2; 1 loai o chiem tu
     * nua so o san -> sKieu 1. Hang san nao co mau rieng chi gom cac o nay thi noi bang mau chung, de phan noi rong dong
     * deu ca phong (ban do goc hay co vai o san lech nhip canh do vat).
     */
    private static void sanChung(short[] d, boolean[] san) {
        sKieu = 0;
        sA = sB = -1;
        int nA = 0, nB = 0, tong = 0;
        for (int lan = 0; lan < 2; lan++) {
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    if (!san[r * E + c]) {
                        continue;
                    }
                    int t = d[r * E + c];
                    if (lan == 0) {
                        tong++;
                    }
                    if (lan == 1 && t == sA) {
                        continue;
                    }
                    int n = 0;
                    for (int i = r * E + c; i <= r1 * E + c1; i++) {
                        int ci = i % E;
                        if (ci >= c0 && ci <= c1 && san[i] && d[i] == t) {
                            n++;
                        }
                    }
                    if (lan == 0 && n > nA) {
                        nA = n;
                        sA = t;
                    } else if (lan == 1 && n > nB) {
                        nB = n;
                        sB = t;
                    }
                }
            }
        }
        if (sA < 0) {
            return;
        }
        if (sB >= 0 && nB * 4 >= nA) {
            int[] khop = new int[2];
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    if (!san[r * E + c]) {
                        continue;
                    }
                    int t = d[r * E + c];
                    if (t == sA) {
                        khop[(c + r) & 1]++;
                    } else if (t == sB) {
                        khop[(c + r + 1) & 1]++;
                    }
                }
            }
            sPha = khop[0] >= khop[1] ? 0 : 1;
            if (khop[sPha] * 100 >= (nA + nB) * 65) {
                sKieu = 2;
                return;
            }
        }
        if (nA * 2 >= tong) {
            sKieu = 1;
            sB = -1;
        }
    }

    private static boolean thuocChung(short[] m) {
        if (sKieu == 0 || m == null) {
            return false;
        }
        for (int q = 0; q < m.length; q++) {
            if (m[q] != sA && !(sKieu == 2 && m[q] == sB)) {
                return false;
            }
        }
        return true;
    }

    /** mau san cua hang r cho 1 phia (nua phong ca..cb): du 2 o san -> mau cua nua do; 1 o -> mau ca hang neu co o do, khong thi chinh o do */
    private static short[] mauPhia(short[] d, boolean[] san, int r, int ca, int cb) {
        int n = dem(san, r, ca, cb, true);
        if (n >= 2) {
            return mauHang(d, san, r, ca, cb, 2, 0);
        }
        short[] ca2 = mauHang(d, san, r, c0, c1, 2, 0);
        if (n == 1) {
            short t = nhieuNhat(d, san, r, 1, 0, ca, cb);
            for (int q = 0; q < ca2.length; q++) {
                if (ca2[q] == t) {
                    return ca2;
                }
            }
            return new short[] {t};
        }
        return ca2;
    }

    private static short[] dich(short[] m, int buoc) {
        int p = m.length;
        short[] kq = new short[p];
        for (int q = 0; q < p; q++) {
            kq[q] = m[((q + buoc) % p + p) % p];
        }
        return kq;
    }

    private static int dem(boolean[] o, int i, int a, int b, boolean hang) {
        int n = 0;
        for (int j = a; j <= b; j++) {
            if (o[hang ? i * E + j : j * E + i]) {
                n++;
            }
        }
        return n;
    }

    /**
     * mau nen lap theo chieu ngang cua hang r, chi xet cac o co co[] trong cot ca..cb: mang dai = chu ky, phan tu theo
     * (cot mod chu ky). Voi moi chu ky lay o nhieu nhat cua tung lop du, cham diem (diemMau), chon diem cao nhat (xap xi
     * nhau: chu ky nho hon; chu ky > 1 phai dat diem nguong - tuong chi lap do trang tri khi no cach deu ro rang, vd den tran,
     * con cua / dong ho / cua so le loi thi noi bang o tuong tron). null neu khong co o nao.
     */
    private static short[] mauHang(short[] d, boolean[] co, int r, int ca, int cb, int soChuKy, int nguong) {
        short chung = nhieuNhat(d, co, r, 1, 0, ca, cb);
        if (chung < 0) {
            return null;
        }
        short[] tot = null;
        int cao = -1;
        for (int k = 0; k < soChuKy; k++) {
            int p = CHU_KY[k];
            if (k > 0 && p * 2 > cb - ca + 1) {
                break;
            }
            short[] m = new short[p];
            for (int q = 0; q < p; q++) {
                short t = nhieuNhat(d, co, r, p, q, ca, cb);
                m[q] = t < 0 ? chung : t;
            }
            int diemM = diemMau(d, co, r, m, ca, cb);
            if (diemM > cao + 20 && (k == 0 || diemM >= nguong)) {
                cao = diemM;
                tot = m;
            }
        }
        return tot;
    }

    /**
     * diem (0..1000) cua mau m voi hang r = do phu (so o dung voi mau / so o) x do thuan^2 (trong cac o mang ma o co trong
     * mau, bao nhieu phan nam dung cho). Do vat xep lien nhau co do phu thap; mau ghep nham nen + do vat co do thuan thap.
     */
    private static int diemMau(short[] d, boolean[] co, int r, short[] m, int ca, int cb) {
        int p = m.length, n0 = 0, khop = 0, trongMau = 0;
        for (int c = ca; c <= cb; c++) {
            if (!co[r * E + c]) {
                continue;
            }
            n0++;
            short t = d[r * E + c];
            if (t == m[c % p]) {
                khop++;
                trongMau++;
                continue;
            }
            for (int q = 0; q < p; q++) {
                if (m[q] == t) {
                    trongMau++;
                    break;
                }
            }
        }
        if (n0 == 0 || trongMau == 0) {
            return 0;
        }
        return (int) ((long) khop * 1000 * khop * khop / ((long) n0 * trongMau * trongMau));
    }

    /** o xuat hien nhieu nhat trong hang r o cac cot c (c mod p == q) thuoc ca..cb; hoa: o nhieu hon trong ca doan; -1: khong co */
    private static short nhieuNhat(short[] d, boolean[] co, int r, int p, int q, int ca, int cb) {
        short tot = -1;
        int nTot = 0;
        for (int c = ca; c <= cb; c++) {
            if (c % p != q || !co[r * E + c]) {
                continue;
            }
            short t = d[r * E + c];
            int n = 0;
            for (int c2 = c; c2 <= cb; c2++) {
                if (c2 % p == q && co[r * E + c2] && d[r * E + c2] == t) {
                    n++;
                }
            }
            if (n > nTot || (n == nTot && tot >= 0 && t != tot && demO(d, co, r, t, ca, cb) > demO(d, co, r, tot, ca, cb))) {
                nTot = n;
                tot = t;
            }
        }
        return tot;
    }

    private static int demO(short[] d, boolean[] co, int r, short t, int ca, int cb) {
        int n = 0;
        for (int c = ca; c <= cb; c++) {
            if (co[r * E + c] && d[r * E + c] == t) {
                n++;
            }
        }
        return n;
    }

    /** so hang diem den lien tiep o mep TREN cua cac o trong mau (phan toi phia tren duong vien tran): camera giau di */
    private static int hangDen(short[] m) {
        int it = 99;
        try {
            int w = ir.c.a, h = ir.c.b;
            for (int k = 0; k < m.length; k++) {
                if (!docO(m[k])) {
                    return 0;
                }
                int y = 0;
                for (; y < h; y++) {
                    int den = 0;
                    for (int x = 0; x < w; x++) {
                        int v = diem[y * w + x];
                        if ((v >>> 24) < 128 || toi(v)) {
                            den++;
                        }
                    }
                    if (den * 10 < w * 9) {
                        break;
                    }
                }
                if (y < it) {
                    it = y;
                }
            }
        } catch (Throwable e) {
            return 0;
        }
        return it >= ir.c.b ? 0 : it * (gy.Y <= 0 ? 1 : gy.Y);
    }

    private static boolean san() {
        if (ir.b != null || ir.d == null || ir.c == null) {
            return false;
        }
        if (mang != ir.d || bo != ir.c || E != ir.e || F != ir.f || banDo != ir.a) {
            try {
                tinh();
            } catch (Throwable e) {
                dung = false;
            }
        }
        return dung;
    }

    /**
     * goi TRUOC khi ve nen: camera doc khong len qua tran phong (game goc de camera bam theo nhan vat, di len gan tuong la lo
     * ra khoang den phia tren); phong thap hon man hinh thi can sat tran, phan du phia duoi noi bang san.
     */
    public static void camera() {
        if (!san()) {
            return;
        }
        int n = ir.i * (gy.Y <= 0 ? 1 : gy.Y);
        if (Cam.y() < trenDen || (F * n - trenDen < main.a.n && Cam.y() != trenDen)) {
            Cam.datDoc(trenDen);
        }
    }

    /** goi ngay sau khi game ve xong cac o gach cua phong (Graphics dang dich theo camera) */
    public static void ve(Graphics g) {
        if (!san()) {
            return;
        }
        int n = ir.i * (gy.Y <= 0 ? 1 : gy.Y);
        if (n <= 0) {
            return;
        }
        int cx = Cam.x(), cy = Cam.y();
        int ca = chiaSan(cx, n), cb = chiaSan(cx + main.a.m - 1, n), ra = chiaSan(cy, n), rb = chiaSan(cy + main.a.n - 1, n);
        short[] d = ir.d;
        ht t = ir.c;
        for (int r = ra; r <= rb; r++) {
            boolean trongHang = r >= 0 && r < F;
            int rr = r < 0 ? 0 : r > r1 ? r1 - (r1 > r0 ? (r - r1) & 1 : 0) : r;
            for (int c = ca; c <= cb; c++) {
                boolean trongBanDo = trongHang && c >= 0 && c < E;
                if (trongBanDo && !ngoai[r * E + c]) {
                    continue;       // o cua phong (san, tuong, do vat, cua ra vao): giu nguyen
                }
                int ma;
                if (c <= giua ? chungT[rr] : chungP[rr]) {
                    ma = sKieu == 2 && ((c + r + sPha) & 1) != 0 ? sB : sA;
                } else {
                    short[] m = c <= giua ? mauT[rr] : mauP[rr];
                    int p = m.length, q = c % p;
                    if (q < 0) {
                        q += p;
                    }
                    ma = m[q];
                }
                if (ma >= 0 && !(trongBanDo && d[r * E + c] == ma)) {
                    t.b(ma / t.c, ma % t.c, c * n, r * n, g);
                }
            }
        }
    }

    private static int chiaSan(int a, int n) {
        return a >= 0 ? a / n : -((-a + n - 1) / n);
    }

    /** go loi: in luoi o cua ban do hien tai (* = o khong dac, ' = o san) + mau nen trai | phai cua tung hang */
    static String ta() {
        StringBuffer sb = new StringBuffer("[hd] phong " + ir.a + " E" + E + " F" + F + " loi c" + c0 + ".." + c1 + " r" + r0 + ".." + r1 + " dung " + dung + " trenDen " + trenDen + " san chung " + sKieu + ":" + sA + "," + sB + " pha " + sPha + "\n");
        short[] d = ir.d;
        for (int r = 0; r < F && d != null && d.length >= E * F; r++) {
            for (int c = 0; c < E; c++) {
                int v = d[r * E + c];
                sb.append(v < 0 ? "  ." : (v < 10 ? "  " : v < 100 ? " " : "") + v).append(v < 0 ? ' ' : !oDac(v) ? (ngoai != null && ngoai[r * E + c] ? '*' : '#') : oSan(r * E + c) ? '\'' : ' ');
            }
            if (dung && r <= r1) {
                sb.append(chungT[r] ? "  C" : "   ").append(chungP[r] ? "C |" : "  |");
                for (int q = 0; q < mauT[r].length; q++) {
                    sb.append(' ').append(mauT[r][q]);
                }
                sb.append(" |");
                for (int q = 0; q < mauP[r].length; q++) {
                    sb.append(' ').append(mauP[r][q]);
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
