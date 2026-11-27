package j2n;

public final class Sys {
    private Sys() {
    }

    public static String property(String key) {
        if (key.equals("microedition.platform")) {
            return "j2me";
        }
        if (key.equals("microedition.profiles")) {
            return "MIDP-2.1";
        }
        if (key.equals("microedition.configuration")) {
            return "CLDC-1.1";
        }
        if (key.equals("microedition.locale")) {
            return "en-US";
        }
        if (key.equals("microedition.encoding")) {
            return "ISO8859_1";
        }
        if (key.equals("microedition.io.file.FileConnection.version")) {
            return null;
        }
        if (key.equals("j2n.hd")) {
            return Disp.hdAnh() ? "1" : null;       // Avatar HD: client (HdAnh) xin bo anh HD cua may chu
        }
        if (key.equals("j2n.hdk")) {
            int k = Disp.hdK();                     // Avatar 250 HD: may ve net cao dang bat (client: HdUi)
            return k > 1 ? String.valueOf(k) : null;
        }
        if (key.startsWith("j2n.phim:")) {
            // Avatar 250 HD (HdUi.formTick): cham vao o nhap chu cua form -> bat / tat ban phim dien thoai.
            // "j2n.phim:<kieu 0 tat / 1 chu / 2 mat khau>:<day vung game phai nam tren ban phim>"
            try {
                int i = key.indexOf(':', 9);
                Disp.wantKeyboard(Integer.parseInt(key.substring(9, i)), Integer.parseInt(key.substring(i + 1)));
            } catch (Throwable e) {
            }
            return "";
        }
        if (key.equals("j2n.le")) {
            // Avatar 250 HD: le an toan cua man hinh "trai,phai,duoi,goc trai,goc phai" (diem logic) - client (HdUi) dat nut tranh tai tho
            return Disp.hdTin(8) + "," + Disp.hdTin(9) + "," + Disp.hdTin(10) + "," + Disp.hdTin(11) + "," + Disp.hdTin(12);
        }
        return property0(key);
    }

    private static native String property0(String key);

    public static native int tzOffset();

    public static native String tzId();

    public static native void log(String s);
}
