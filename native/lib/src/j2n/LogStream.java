package j2n;

import java.io.OutputStream;

public final class LogStream extends OutputStream {
    private final byte[] buf = new byte[512];
    private int n;
    private final int fd;

    public LogStream(int fd) {
        this.fd = fd;
    }

    public synchronized void write(int b) {
        if (b == '\n' || n == buf.length) {
            flushLine();
            if (b == '\n') {
                return;
            }
        }
        buf[n++] = (byte) b;
    }

    public synchronized void flush() {
        if (n > 0) {
            flushLine();
        }
    }

    private void flushLine() {
        try {
            Sys.log(new String(buf, 0, n, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
        }
        n = 0;
    }
}
