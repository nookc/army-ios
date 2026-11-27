package j2n;

import java.util.Hashtable;

/**
 * May HD (ban "Avatar 250 HD"): bo anh thay the nam trong jar game, thu muc hdx/.
 *   hdx/ds            danh sach ma CRC32 cua cac anh GOC co ban thay (4 byte / muc, big-endian)
 *   hdx/<8 hex>.png   anh thay cho anh goc co ma do; chunk rieng "apHd" = he so (so diem anh cho 1 diem logic)
 * Image.decode() hoi o day truoc khi giai ma: anh goc (font, khung, nut...) duoc thay bang ban ve lai net cao, kich thuoc
 * logic giu nguyen nen game khong can biet. Ban thuong (may HD tat) khong goi toi lop nay.
 */
public final class HdGoi {
    private static Hashtable ds;
    private static int[] bang;

    private HdGoi() {
    }

    private static synchronized void nap() {
        if (ds != null) {
            return;
        }
        Hashtable h = new Hashtable();
        try {
            byte[] b = Res.read("hdx/ds");
            if (b != null) {
                for (int i = 0; i + 4 <= b.length; i += 4) {
                    int c = ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                    h.put(new Integer(c), Boolean.TRUE);
                }
            }
        } catch (Throwable e) {
        }
        int[] t = new int[256];
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
            }
            t[n] = c;
        }
        bang = t;
        ds = h;
    }

    static int crc(byte[] b, int off, int len) {
        int c = 0xFFFFFFFF;
        int[] t = bang;
        for (int i = off, het = off + len; i < het; i++) {
            c = t[(c ^ b[i]) & 0xFF] ^ (c >>> 8);
        }
        return ~c;
    }

    /** byte PNG thay the cho anh goc data[off .. off + len), null neu khong co */
    public static byte[] tim(byte[] data, int off, int len) {
        if (ds == null) {
            nap();
        }
        if (ds.size() == 0 || len < 24) {
            return null;
        }
        int c = crc(data, off, len);
        if (!ds.containsKey(new Integer(c))) {
            return null;
        }
        String hex = Integer.toHexString(c);
        while (hex.length() < 8) {
            hex = "0" + hex;
        }
        try {
            return Res.read("hdx/" + hex + ".png");
        } catch (Throwable e) {
            return null;
        }
    }
}
