package j2n;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import javax.microedition.io.HttpConnection;

/** HTTP/1.0 toi gian qua socket (game chi GET vai URL http) */
public final class HttpConn implements HttpConnection {
    private final String url;
    private String host;
    private int port = 80;
    private String file = "/";
    private String method = GET;
    private final Vector reqKeys = new Vector(), reqVals = new Vector();
    private final Vector resKeys = new Vector(), resVals = new Vector();
    private int code = -1;
    private String message;
    private byte[] body;
    private ByteArrayOutputStream post;
    private boolean closed;

    public HttpConn(String url) throws IOException {
        this.url = url;
        String u = url;
        if (u.startsWith("https://")) {
            throw new IOException("https chua ho tro");
        }
        u = u.substring(7);
        int s = u.indexOf('/');
        String hp = s < 0 ? u : u.substring(0, s);
        file = s < 0 ? "/" : u.substring(s);
        int c = hp.lastIndexOf(':');
        if (c > 0) {
            host = hp.substring(0, c);
            port = Integer.parseInt(hp.substring(c + 1));
        } else {
            host = hp;
        }
    }

    public String getURL() {
        return url;
    }

    public String getProtocol() {
        return "http";
    }

    public String getHost() {
        return host;
    }

    public String getFile() {
        int q = file.indexOf('?');
        return q < 0 ? file : file.substring(0, q);
    }

    public String getRef() {
        return null;
    }

    public String getQuery() {
        int q = file.indexOf('?');
        return q < 0 ? null : file.substring(q + 1);
    }

    public int getPort() {
        return port;
    }

    public String getRequestMethod() {
        return method;
    }

    public void setRequestMethod(String m) throws IOException {
        method = m;
    }

    public String getRequestProperty(String k) {
        int i = reqKeys.indexOf(k);
        return i < 0 ? null : (String) reqVals.elementAt(i);
    }

    public void setRequestProperty(String k, String v) throws IOException {
        int i = reqKeys.indexOf(k);
        if (i >= 0) {
            reqVals.setElementAt(v, i);
        } else {
            reqKeys.addElement(k);
            reqVals.addElement(v);
        }
    }

    private void run() throws IOException {
        if (code != -1) {
            return;
        }
        if (closed) {
            throw new IOException("da dong");
        }
        int fd = Net.connect(host, port);
        if (fd < 0) {
            throw new IOException(Net.error());
        }
        try {
            Net.timeout(fd, READ_MS);
            StringBuffer sb = new StringBuffer();
            sb.append(method).append(' ').append(file).append(" HTTP/1.0\r\n");
            sb.append("Host: ").append(host).append("\r\n");
            byte[] pb = post != null ? post.toByteArray() : null;
            for (int i = 0; i < reqKeys.size(); i++) {
                sb.append((String) reqKeys.elementAt(i)).append(": ").append((String) reqVals.elementAt(i)).append("\r\n");
            }
            if (pb != null && getRequestProperty("Content-Length") == null) {
                sb.append("Content-Length: ").append(pb.length).append("\r\n");
            }
            sb.append("Connection: close\r\n\r\n");
            byte[] h = sb.toString().getBytes("UTF-8");
            Net.check(Net.write(fd, h, 0, h.length), "ghi http");
            if (pb != null && pb.length > 0) {
                Net.check(Net.write(fd, pb, 0, pb.length), "ghi http");
            }
            /* doc toi khi: du Content-Length / het khoi chunked / may chu dong / qua thoi gian
               (khong cho may chu dong ket noi: proxy hoac may chu giu ket noi se lam treo) */
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            long han = System.currentTimeMillis() + TOTAL_MS;
            int hdrEnd = -1;
            int need = -1;
            boolean chunked = false;
            while (true) {
                if (need >= 0 && all.size() >= need) {
                    break;
                }
                if (chunked && dechunk(all.toByteArray(), hdrEnd) != null) {
                    break;
                }
                if (System.currentTimeMillis() > han) {
                    break;
                }
                int n = Net.read(fd, buf, 0, buf.length);
                if (n <= 0) {
                    break;
                }
                all.write(buf, 0, n);
                if (hdrEnd < 0) {
                    byte[] cur = all.toByteArray();
                    hdrEnd = headerEnd(cur);
                    if (hdrEnd >= 0) {
                        String hs = new String(cur, 0, hdrEnd).toLowerCase();
                        chunked = headerValue(hs, "transfer-encoding").indexOf("chunked") >= 0;
                        int st = statusOf(hs);
                        if (method.equals(HEAD) || st == 204 || st == 304 || (st >= 100 && st < 200)) {
                            need = hdrEnd;
                        } else if (!chunked) {
                            try {
                                String cl = headerValue(hs, "content-length");
                                if (cl.length() > 0) {
                                    need = hdrEnd + Integer.parseInt(cl);
                                }
                            } catch (NumberFormatException e) {
                                need = -1;
                            }
                        }
                    }
                }
            }
            byte[] r = all.toByteArray();
            if (r.length == 0) {
                throw new IOException("may chu khong tra loi " + host + ":" + port + " (" + Net.error() + ")");
            }
            int p = 0;
            int lineNo = 0;
            int bodyStart = r.length;
            while (p < r.length) {
                int e = p;
                while (e < r.length && r[e] != '\n') {
                    e++;
                }
                int end = e > p && r[e - 1] == '\r' ? e - 1 : e;
                String line = new String(r, p, end - p);
                p = e + 1;
                if (line.length() == 0) {
                    bodyStart = p;
                    break;
                }
                if (lineNo++ == 0) {
                    int s1 = line.indexOf(' ');
                    int s2 = s1 < 0 ? -1 : line.indexOf(' ', s1 + 1);
                    try {
                        code = Integer.parseInt(s2 < 0 ? line.substring(s1 + 1).trim() : line.substring(s1 + 1, s2));
                    } catch (RuntimeException ex) {
                        code = 0;
                    }
                    message = s2 < 0 ? "" : line.substring(s2 + 1);
                } else {
                    int c = line.indexOf(':');
                    if (c > 0) {
                        resKeys.addElement(line.substring(0, c).trim().toLowerCase());
                        resVals.addElement(line.substring(c + 1).trim());
                    }
                }
            }
            if (code == -1) {
                code = 0;
            }
            byte[] dc = chunked ? dechunk(r, bodyStart) : null;
            if (chunked && dc == null) {
                dc = dechunkPartial(r, bodyStart);
            }
            if (dc != null) {
                body = dc;
                int i = resKeys.indexOf("transfer-encoding");
                if (i >= 0) {
                    resKeys.removeElementAt(i);
                    resVals.removeElementAt(i);
                }
            } else {
                int n = Math.max(0, r.length - bodyStart);
                if (need >= 0 && need - bodyStart >= 0 && need - bodyStart < n) {
                    n = need - bodyStart;
                }
                body = new byte[n];
                System.arraycopy(r, bodyStart, body, 0, n);
            }
        } finally {
            Net.close(fd);
        }
    }

    private static final int READ_MS = 15000;
    private static final int TOTAL_MS = 30000;

    /** vi tri bat dau than (sau dong trong \r\n\r\n hoac \n\n), -1 neu chua du header */
    private static int headerEnd(byte[] b) {
        for (int i = 0; i + 1 < b.length; i++) {
            if (b[i] == '\n') {
                if (b[i + 1] == '\n') {
                    return i + 2;
                }
                if (b[i + 1] == '\r' && i + 2 < b.length && b[i + 2] == '\n') {
                    return i + 3;
                }
            }
        }
        return -1;
    }

    /** gia tri header (hs da chu thuong), "" neu khong co */
    private static String headerValue(String hs, String key) {
        int p = 0;
        while (true) {
            int nl = hs.indexOf('\n', p);
            if (nl < 0) {
                return "";
            }
            p = nl + 1;
            if (hs.startsWith(key, p)) {
                int c = p + key.length();
                while (c < hs.length() && hs.charAt(c) == ' ') {
                    c++;
                }
                if (c < hs.length() && hs.charAt(c) == ':') {
                    int e = hs.indexOf('\n', c);
                    return (e < 0 ? hs.substring(c + 1) : hs.substring(c + 1, e)).trim();
                }
            }
        }
    }

    private static int statusOf(String hs) {
        int s1 = hs.indexOf(' ');
        if (s1 < 0) {
            return 0;
        }
        int e = s1 + 1;
        while (e < hs.length() && hs.charAt(e) >= '0' && hs.charAt(e) <= '9') {
            e++;
        }
        try {
            return Integer.parseInt(hs.substring(s1 + 1, e));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** giai ma Transfer-Encoding: chunked tu vi tri off; null neu chua nhan het (chua co khoi 0) */
    private static byte[] dechunk(byte[] b, int off) {
        return dechunk0(b, off, false);
    }

    /** nhu dechunk nhung tra ve phan da nhan duoc (ket noi dong giua chung) */
    private static byte[] dechunkPartial(byte[] b, int off) {
        return dechunk0(b, off, true);
    }

    private static byte[] dechunk0(byte[] b, int off, boolean partial) {
        if (off < 0) {
            return null;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int p = off;
        while (true) {
            int e = p;
            while (e < b.length && b[e] != '\n') {
                e++;
            }
            if (e >= b.length) {
                return partial ? o.toByteArray() : null;
            }
            String ln = new String(b, p, e - p).trim();
            int sc = ln.indexOf(';');
            if (sc >= 0) {
                ln = ln.substring(0, sc).trim();
            }
            int n;
            try {
                n = Integer.parseInt(ln, 16);
            } catch (NumberFormatException ex) {
                return partial ? o.toByteArray() : null;
            }
            p = e + 1;
            if (n == 0) {
                return o.toByteArray();
            }
            if (p + n > b.length) {
                if (partial) {
                    o.write(b, p, b.length - p);
                    return o.toByteArray();
                }
                return null;
            }
            o.write(b, p, n);
            p += n;
            if (p < b.length && b[p] == '\r') {
                p++;
            }
            if (p < b.length && b[p] == '\n') {
                p++;
            }
        }
    }

    public int getResponseCode() throws IOException {
        run();
        return code;
    }

    public String getResponseMessage() throws IOException {
        run();
        return message;
    }

    public long getExpiration() throws IOException {
        return 0;
    }

    public long getDate() throws IOException {
        return 0;
    }

    public long getLastModified() throws IOException {
        return 0;
    }

    public String getHeaderField(String name) throws IOException {
        run();
        int i = resKeys.indexOf(name.toLowerCase());
        return i < 0 ? null : (String) resVals.elementAt(i);
    }

    public int getHeaderFieldInt(String name, int def) throws IOException {
        String v = getHeaderField(name);
        try {
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public long getHeaderFieldDate(String name, long def) throws IOException {
        return def;
    }

    public String getHeaderField(int n) throws IOException {
        run();
        return n < resVals.size() ? (String) resVals.elementAt(n) : null;
    }

    public String getHeaderFieldKey(int n) throws IOException {
        run();
        return n < resKeys.size() ? (String) resKeys.elementAt(n) : null;
    }

    public String getType() {
        try {
            return getHeaderField("content-type");
        } catch (IOException e) {
            return null;
        }
    }

    public String getEncoding() {
        try {
            return getHeaderField("content-encoding");
        } catch (IOException e) {
            return null;
        }
    }

    public long getLength() {
        try {
            run();
            String v = getHeaderField("content-length");
            if (v != null) {
                return Long.parseLong(v.trim());
            }
            return body != null ? body.length : -1;
        } catch (IOException e) {
            return -1;
        } catch (NumberFormatException e) {
            return body != null ? body.length : -1;
        }
    }

    public InputStream openInputStream() throws IOException {
        run();
        return new ByteArrayInputStream(body);
    }

    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    public OutputStream openOutputStream() throws IOException {
        if (post == null) {
            post = new ByteArrayOutputStream();
        }
        return post;
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    public void close() throws IOException {
        closed = true;
    }
}
