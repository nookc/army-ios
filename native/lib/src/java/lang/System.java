package java.lang;

import java.io.PrintStream;

public final class System {
    public static final PrintStream out = new PrintStream(new j2n.LogStream(1));
    public static final PrintStream err = new PrintStream(new j2n.LogStream(2));

    private System() {
    }

    public static native void arraycopy(Object src, int srcPos, Object dst, int dstPos, int length);

    public static native long currentTimeMillis();

    public static native int identityHashCode(Object o);

    public static String getProperty(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return j2n.Sys.property(key);
    }

    public static void gc() {
        Runtime.getRuntime().gc();
    }

    public static void exit(int code) {
        Runtime.getRuntime().exit(code);
    }
}
