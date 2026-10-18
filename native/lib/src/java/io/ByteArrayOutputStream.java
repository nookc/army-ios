package java.io;

public class ByteArrayOutputStream extends OutputStream {
    protected byte[] buf;
    protected int count;

    public ByteArrayOutputStream() {
        this(32);
    }

    public ByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        buf = new byte[size];
    }

    private void ensure(int n) {
        if (n > buf.length) {
            int c = Math.max(buf.length << 1, n);
            byte[] b = new byte[c];
            System.arraycopy(buf, 0, b, 0, count);
            buf = b;
        }
    }

    public void write(int b) {
        ensure(count + 1);
        buf[count++] = (byte) b;
    }

    public void write(byte[] b, int off, int len) {
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        ensure(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void reset() {
        count = 0;
    }

    public byte[] toByteArray() {
        byte[] b = new byte[count];
        System.arraycopy(buf, 0, b, 0, count);
        return b;
    }

    public int size() {
        return count;
    }

    public String toString() {
        return new String(buf, 0, count);
    }

    public void close() {
    }
}
