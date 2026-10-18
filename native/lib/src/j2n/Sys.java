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
        return property0(key);
    }

    private static native String property0(String key);

    public static native int tzOffset();

    public static native String tzId();

    public static native void log(String s);
}
