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
            throw new IOException("khong ket noi duoc " + host);
        }
        try {
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
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (true) {
                int n = Net.read(fd, buf, 0, buf.length);
                if (n <= 0) {
                    break;
                }
                all.write(buf, 0, n);
            }
            byte[] r = all.toByteArray();
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
            body = new byte[Math.max(0, r.length - bodyStart)];
            System.arraycopy(r, bodyStart, body, 0, body.length);
        } finally {
            Net.close(fd);
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
