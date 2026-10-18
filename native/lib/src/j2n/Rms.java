package j2n;

public final class Rms {
    private Rms() {
    }

    public static native byte[] load(String name);

    public static native boolean save(String name, byte[] data);

    public static native boolean delete(String name);

    public static native String[] list();
}
