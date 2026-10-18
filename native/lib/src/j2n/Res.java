package j2n;

public final class Res {
    private Res() {
    }

    /** doc tep trong jar (ten khong co / dau), null neu khong co */
    public static native byte[] read(String name);

    public static native String appProperty(String key);
}
