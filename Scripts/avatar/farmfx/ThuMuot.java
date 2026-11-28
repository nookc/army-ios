import java.util.Hashtable;

/**
 * Thu cung 3D co CHU KY MUOT (moi ban, ca ban doc): 3 con ngua canh (anh goc 30700..30759, 4 anh / cap, 15 cap).
 *
 * Game goc chi dung 4 anh / thu cung: dang di doi qua lai 2 anh (khung 0 / 1, 3 nhip doi 1 lan), dung yen 1 anh co dinh
 * -> canh "giat cuc", chan dung im ("bi cung khi vay canh hoac di chuyen").
 * Ban moi: may chu co them 12 anh / cap (31000 + cap * 12 + khung): du 1 nhip dap canh + chan phi + bom duoi bay, lap lien
 * mach. gr.a(...) / fa.a(...) goi fx.b(ma anh) -> truoc do ThuMuot.anh(ma, thu) doi sang khung theo THOI GIAN (khong theo
 * nhip game): dung yen 1 nhip canh / giay, dang chay theo chu nhanh hon; moi con 1 pha rieng (khong vo deu tam tap).
 * Chi dung khi da tai du ca 12 khung cua cap do (dang tai / may chu cu khong co anh -> ve 4 anh cu nhu truoc).
 */
public final class ThuMuot {

    static final int ANH0 = 30700, ANH1 = 30760, MUOT0 = 31000, N = 12;
    static final int DUNG = 1000, CHAY = 600;                 // ms / nhip canh

    private static final Hashtable pha = new Hashtable();      // gr -> int[] {pha (phan nghin khung), moc ms, ms / nhip}
    private static final boolean[] du = new boolean[(ANH1 - ANH0) / 4];
    private static final long[] xem = new long[(ANH1 - ANH0) / 4];

    private ThuMuot() {
    }

    /** ma anh game goc dinh ve -> ma anh khung muot (hoac giu nguyen) */
    public static short anh(short id, gr p) {
        try {
            if (id < ANH0 || id >= ANH1) {
                return id;
            }
            int cap = (id - ANH0) >> 2;
            long nay = System.currentTimeMillis();
            int k = khung(p, nay);
            if (!du[cap]) {
                if (nay - xem[cap] < 500 && nay >= xem[cap]) {
                    return id;
                }
                xem[cap] = nay;
                boolean ok = true;
                for (int i = 0; i < N; i++) {                   // fx.b tu xin may chu anh chua co
                    f a = fx.b((short) (MUOT0 + cap * N + i));
                    if (a == null || a.d == -1 || a.a == null) {
                        ok = false;
                    }
                }
                if (!ok) {
                    return id;
                }
                du[cap] = true;
            }
            short moi = (short) (MUOT0 + cap * N + k);
            f a = fx.b(moi);
            if (a == null || a.d == -1 || a.a == null) {        // bo nho dem vua xoa (thu ra khoi man hinh lau): tai lai
                du[cap] = false;
                xem[cap] = 0;
                return id;
            }
            return moi;
        } catch (Throwable e) {
            return id;
        }
    }

    private static int khung(gr p, long nay) {
        if (p == null) {
            return (int) ((nay % DUNG) * N / DUNG);
        }
        int t = (int) (nay & 0x3fffffffL);
        int muon = p.H == 1 ? CHAY : DUNG;
        int[] s = (int[]) pha.get(p);
        if (s == null) {
            if (pha.size() > 80) {
                pha.clear();
            }
            s = new int[] { (p.hashCode() & 0x7fff) % (N * 1000), t, muon };
            pha.put(p, s);
        }
        int dt = t - s[1];
        if (dt < 0 || dt > 400) {
            dt = 60;
        }
        s[1] = t;
        if (dt > 0) {
            int d = muon - s[2];                                // doi toc do tu tu: khong giat luc dung <-> chay
            s[2] += d > 40 ? 40 : d < -40 ? -40 : d;
            s[0] = (s[0] + dt * N * 1000 / s[2]) % (N * 1000);
        }
        return s[0] / 1000;
    }
}
