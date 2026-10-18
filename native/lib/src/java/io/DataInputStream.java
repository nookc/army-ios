package java.io;

public class DataInputStream extends InputStream implements DataInput {
    protected InputStream in;

    public DataInputStream(InputStream in) {
        this.in = in;
    }

    public int read() throws IOException {
        return in.read();
    }

    public final int read(byte[] b) throws IOException {
        return in.read(b, 0, b.length);
    }

    public final int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    public final void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    public final void readFully(byte[] b, int off, int len) throws IOException {
        if (len < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < len) {
            int c = in.read(b, off + n, len - n);
            if (c < 0) {
                throw new EOFException();
            }
            n += c;
        }
    }

    public final int skipBytes(int n) throws IOException {
        int total = 0;
        int cur;
        while (total < n && (cur = (int) in.skip(n - total)) > 0) {
            total += cur;
        }
        return total;
    }

    private int r1() throws IOException {
        int c = in.read();
        if (c < 0) {
            throw new EOFException();
        }
        return c;
    }

    public final boolean readBoolean() throws IOException {
        return r1() != 0;
    }

    public final byte readByte() throws IOException {
        return (byte) r1();
    }

    public final int readUnsignedByte() throws IOException {
        return r1();
    }

    public final short readShort() throws IOException {
        int a = r1(), b = r1();
        return (short) ((a << 8) | b);
    }

    public final int readUnsignedShort() throws IOException {
        int a = r1(), b = r1();
        return (a << 8) | b;
    }

    public final char readChar() throws IOException {
        int a = r1(), b = r1();
        return (char) ((a << 8) | b);
    }

    public final int readInt() throws IOException {
        int a = r1(), b = r1(), c = r1(), d = r1();
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

    public final long readLong() throws IOException {
        return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
    }

    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public final String readUTF() throws IOException {
        return readUTF(this);
    }

    public static final String readUTF(DataInput in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b, 0, len);
        char[] c = new char[len];
        int n = 0, i = 0;
        while (i < len) {
            int x = b[i] & 0xff;
            if (x < 0x80) {
                c[n++] = (char) x;
                i++;
            } else if ((x & 0xE0) == 0xC0) {
                if (i + 1 >= len) {
                    throw new UTFDataFormatException();
                }
                int y = b[i + 1];
                if ((y & 0xC0) != 0x80) {
                    throw new UTFDataFormatException();
                }
                c[n++] = (char) (((x & 0x1F) << 6) | (y & 0x3F));
                i += 2;
            } else if ((x & 0xF0) == 0xE0) {
                if (i + 2 >= len) {
                    throw new UTFDataFormatException();
                }
                int y = b[i + 1], z = b[i + 2];
                if ((y & 0xC0) != 0x80 || (z & 0xC0) != 0x80) {
                    throw new UTFDataFormatException();
                }
                c[n++] = (char) (((x & 0x0F) << 12) | ((y & 0x3F) << 6) | (z & 0x3F));
                i += 3;
            } else {
                throw new UTFDataFormatException();
            }
        }
        return new String(c, 0, n);
    }

    public int available() throws IOException {
        return in.available();
    }

    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    public void close() throws IOException {
        in.close();
    }

    public synchronized void mark(int limit) {
        in.mark(limit);
    }

    public synchronized void reset() throws IOException {
        in.reset();
    }

    public boolean markSupported() {
        return in.markSupported();
    }
}
