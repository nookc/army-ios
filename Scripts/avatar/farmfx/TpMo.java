import java.io.DataInputStream;
import javax.microedition.rms.RecordStore;
import main.GameMidlet;

/**
 * Thanh pho dang mo (danh sach "Chon T. Pho" luc dang nhap). May chu quan ly thanh pho nao dang mo (trang quan tri),
 * gui cho app o lenh 119 muc con 10; app luu lai (RMS "avatarTpMo") va chi hien thanh pho dang mo. App bao thanh pho da
 * chon cho may chu o chuoi thu 3 cua lenh -17: "tp=5;ban=21;hd=1" (ma = vi tri trong danh sach goc cua app).
 *
 * PatchFarmFx: cx.b() "getstatic ae.j" -> tin(); dau ez.a(), ez.e(), main.a.c() -> nap(); DauTruong.tin muc con 10 -> nhan().
 * Chi ap dung cho danh sach tieng Viet (GameMidlet.a[0]).
 */
public final class TpMo {
    /** so ban giao (may chu: Session.banApp) */
    public static final int BAN = 21;
    /** mo san khi chua tung noi may chu: Dieu Ky (5), Mong Mo (6) */
    static final int[] MAC_DINH = {5, 6};

    static String[][] gA, gB;
    static int[][] gC;
    static int soTp;
    static byte[] mo;
    static int[][] maTheo;
    static boolean xong;
    static int chon = -1;

    private TpMo() {
    }

    public static synchronized void nap() {
        if (xong) {
            return;
        }
        xong = true;
        try {
            gA = GameMidlet.a[0];
            gB = GameMidlet.b[0];
            gC = GameMidlet.c[0];
            soTp = 0;
            for (int r = 0; r < gA.length; r++) {
                soTp += gA[r].length - 1;
            }
            mo = new byte[soTp];
            byte[] luu = doc();
            if (luu != null && luu.length == soTp) {
                System.arraycopy(luu, 0, mo, 0, soTp);
            } else {
                for (int i = 0; i < MAC_DINH.length; i++) {
                    if (MAC_DINH[i] < soTp) {
                        mo[MAC_DINH[i]] = 1;
                    }
                }
            }
            apDung();
        } catch (Throwable t) {
            gA = null;
        }
    }

    /** dung lai GameMidlet.a/b/c[0] chi gom thanh pho dang mo (xu so khong con thanh pho nao thi bo luon) */
    static void apDung() {
        if (gA == null) {
            return;
        }
        boolean co = false;
        for (int i = 0; i < soTp; i++) {
            co |= mo[i] != 0;
        }
        int nr = 0, id = 0;
        int[] dem = new int[gA.length];
        for (int r = 0; r < gA.length; r++) {
            for (int j = 1; j < gA[r].length; j++, id++) {
                if (!co || mo[id] != 0) {
                    dem[r]++;
                }
            }
            if (dem[r] > 0) {
                nr++;
            }
        }
        String[][] a = new String[nr][];
        String[][] b = new String[nr][];
        int[][] c = new int[nr][];
        int[][] ma = new int[nr][];
        int k = 0;
        id = 0;
        for (int r = 0; r < gA.length; r++) {
            if (dem[r] == 0) {
                id += gA[r].length - 1;
                continue;
            }
            a[k] = new String[dem[r] + 1];
            b[k] = new String[dem[r]];
            c[k] = new int[dem[r]];
            ma[k] = new int[dem[r]];
            a[k][0] = gA[r][0];
            int n = 0;
            for (int j = 1; j < gA[r].length; j++, id++) {
                if (!co || mo[id] != 0) {
                    a[k][n + 1] = gA[r][j];
                    b[k][n] = gB[r][Math.min(j - 1, gB[r].length - 1)];
                    c[k][n] = gC[r][Math.min(j - 1, gC[r].length - 1)];
                    ma[k][n] = id;
                    n++;
                }
            }
            k++;
        }
        GameMidlet.a[0] = a;
        GameMidlet.b[0] = b;
        GameMidlet.c[0] = c;
        maTheo = ma;
        // giu cho dang chon (noi lai sau khi rot mang dung lai thanh pho cu neu con mo); khong con thi ve thanh pho dau
        try {
            if (ez.a != null) {
                int r0 = 0, n0 = -1;
                for (int r = 0; r < ma.length; r++) {
                    for (int n = 0; n < ma[r].length; n++) {
                        if (ma[r][n] == chon) {
                            r0 = r;
                            n0 = n;
                        }
                    }
                }
                ez e = ez.a;
                hj h = e;
                if (n0 >= 0) {
                    e.b = r0;
                    h.ar = n0 + 1;
                } else {
                    if (e.b >= ma.length) {
                        e.b = 0;
                    }
                    if (h.ar - 1 >= ma[e.b].length) {
                        h.ar = h.ar > 0 ? 1 : 0;
                    }
                }
                if (main.a.r == e) {
                    e.e();
                }
            }
        } catch (Throwable t) {
        }
    }

    /**
     * Muc trong MENU do may chu dat (Chuc nang, Hoi nhom...): app goc gui vi tri muc (0, 1, 2...) bang lenh -61, trung voi so
     * 1..4 cua tam bien Bang xep hang. Muc tu thu 2 tro di gui 100 + vi tri de may chu phan biet duoc.
     */
    public static int maLenh(int viTri) {
        return viTri >= 1 ? 100 + viTri : viTri;
    }

    /** Truong dua pet: 9 muc cuoc (ban goc 100..50.000); o cuoi -1 = "So khac" (may chu mo o nhap so xu) */
    public static int[] chipDua(int[] goc) {
        return new int[] { 1000, 5000, 10000, 50000, 100000, 500000, 1000000, 5000000, -1 };
    }

    /** chu tren nut muc cuoc */
    public static String nhanChip(int v) {
        if (v == -1) {
            return "Khac";
        }
        if (v >= 1000000 && v % 1000000 == 0) {
            return (v / 1000000) + "tr";
        }
        if (v >= 1000 && v % 1000 == 0) {
            return (v / 1000) + "k";
        }
        return String.valueOf(v);
    }

    /** chuoi thu 3 cua lenh -17 */
    public static String tin() {
        return "tp=" + ma() + ";ban=" + BAN + ";hd=" + (HdUi.hd() ? 1 : 0);
    }

    /** chuoi phien ban trong lenh dang nhap: dang nhap lai tren ket noi cu (khong gui lai -17) van bao dung thanh pho moi chon */
    public static String dangNhap() {
        return "2.5.8;tp=" + ma();
    }

    /** ma thanh pho dang chon tren man "Chon T. Pho" (-1 = khong ro) */
    static int ma() {
        int id = -1;
        try {
            nap();
            if (dm.b().b[4] == 0 && maTheo != null) {
                ez e = ez.b();
                hj h = e;
                int r = e.b, n = h.ar - 1;
                if (n < 0) {
                    n = 0;
                }
                if (r >= 0 && r < maTheo.length && n < maTheo[r].length) {
                    id = maTheo[r][n];
                }
            }
        } catch (Throwable t) {
        }
        chon = id;
        return id;
    }

    /** lenh 119 muc con 10: [so thanh pho][co mo x so] */
    public static void nhan(DataInputStream d) {
        try {
            nap();
            int n = d.readByte();
            byte[] m = new byte[n];
            for (int i = 0; i < n; i++) {
                m[i] = d.readByte();
            }
            if (gA == null || n != soTp) {
                return;
            }
            boolean khac = false, co = false;
            for (int i = 0; i < n; i++) {
                khac |= (m[i] != 0) != (mo[i] != 0);
                co |= m[i] != 0;
            }
            if (!khac || !co) {
                return;
            }
            mo = m;
            ghi(m);
            apDung();
        } catch (Throwable t) {
        }
    }

    static byte[] doc() {
        try {
            RecordStore rs = RecordStore.openRecordStore("avatarTpMo", true);
            byte[] b = rs.getNumRecords() > 0 ? rs.getRecord(1) : null;
            rs.closeRecordStore();
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    static void ghi(byte[] b) {
        try {
            RecordStore rs = RecordStore.openRecordStore("avatarTpMo", true);
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, b, 0, b.length);
            } else {
                rs.addRecord(b, 0, b.length);
            }
            rs.closeRecordStore();
        } catch (Throwable t) {
        }
    }
}
