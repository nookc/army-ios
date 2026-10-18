package java.lang;

public class Runtime {
    private static final Runtime current = new Runtime();

    private Runtime() {
    }

    public static Runtime getRuntime() {
        return current;
    }

    public native long freeMemory();

    public native long totalMemory();

    public native void gc();

    public native void exit(int code);
}
