package j2n;

import java.io.IOException;

public final class Net {
    private Net() {
    }

    /** tra ve fd >= 0, hoac -1 (khong ket noi duoc) */
    public static native int connect(String host, int port);

    /** so byte doc duoc, -1 het luong, -2 loi */
    public static native int read(int fd, byte[] b, int off, int len);

    /** so byte da ghi, < 0 loi */
    public static native int write(int fd, byte[] b, int off, int len);

    public static native int available(int fd);

    public static native void shutdown(int fd);

    public static native void close(int fd);

    public static native String localAddress(int fd);

    public static native int localPort(int fd);

    public static void check(int r, String what) throws IOException {
        if (r < 0) {
            throw new IOException(what);
        }
    }
}
