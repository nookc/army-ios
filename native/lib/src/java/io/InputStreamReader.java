package java.io;

public class InputStreamReader extends Reader {
    private final InputStream in;
    private final boolean utf8;

    public InputStreamReader(InputStream in) {
        this.in = in;
        this.utf8 = false;
    }

    public InputStreamReader(InputStream in, String enc) throws UnsupportedEncodingException {
        this.in = in;
        String e = enc.toUpperCase();
        this.utf8 = e.equals("UTF-8") || e.equals("UTF8");
    }

    public int read() throws IOException {
        int b = in.read();
        if (b < 0) {
            return -1;
        }
        if (!utf8 || b < 0x80) {
            return b;
        }
        if ((b & 0xE0) == 0xC0) {
            int b2 = in.read();
            return ((b & 0x1F) << 6) | (b2 & 0x3F);
        }
        if ((b & 0xF0) == 0xE0) {
            int b2 = in.read(), b3 = in.read();
            return ((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
        }
        return 0xFFFD;
    }

    public int read(char[] c, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int ch = read();
            if (ch < 0) {
                return n == 0 ? -1 : n;
            }
            c[off + n++] = (char) ch;
            if (in.available() <= 0) {
                break;
            }
        }
        return n;
    }

    public boolean ready() throws IOException {
        return in.available() > 0;
    }

    public void close() throws IOException {
        in.close();
    }
}
