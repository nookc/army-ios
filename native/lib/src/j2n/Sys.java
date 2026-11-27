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
        return property0(key);
    }

    private static native String property0(String key);

    public static native int tzOffset();

    public static native String tzId();

    public static native void log(String s);
}
