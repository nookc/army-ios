package java.io;

public class ByteArrayInputStream extends InputStream {
    protected byte[] buf;
    protected int pos;
    protected int mark;
    protected int count;

    public ByteArrayInputStream(byte[] b) {
        buf = b;
        pos = 0;
        count = b.length;
    }

    public ByteArrayInputStream(byte[] b, int off, int len) {
        buf = b;
        pos = off;
        count = Math.min(off + len, b.length);
        mark = off;
    }

    public int read() {
        return pos < count ? (buf[pos++] & 0xff) : -1;
    }

    public int read(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (pos >= count) {
            return -1;
        }
        if (pos + len > count) {
            len = count - pos;
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(buf, pos, b, off, len);
        pos += len;
        return len;
    }

    public long skip(long n) {
        if (pos + n > count) {
            n = count - pos;
        }
        if (n < 0) {
            return 0;
        }
        pos += (int) n;
        return n;
    }

    public int available() {
        return count - pos;
    }

    public boolean markSupported() {
        return true;
    }

    public void mark(int limit) {
        mark = pos;
    }

    public void reset() {
        pos = mark;
    }

    public void close() {
    }
}
