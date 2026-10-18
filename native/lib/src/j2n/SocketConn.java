package j2n;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.SocketConnection;

public final class SocketConn implements SocketConnection {
    private int fd;
    private final String host;
    private final int port;
    private int refs = 1;     /* ket noi + cac luong mo */
    private boolean connClosed;
    private boolean inOpen, outOpen;

    private SocketConn(int fd, String host, int port) {
        this.fd = fd;
        this.host = host;
        this.port = port;
    }

    public static SocketConn open(String hp) throws IOException {
        int c = hp.lastIndexOf(':');
        if (c <= 0) {
            throw new IllegalArgumentException("socket://" + hp);
        }
        String h = hp.substring(0, c);
        int p;
        try {
            p = Integer.parseInt(hp.substring(c + 1).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port");
        }
        int fd = Net.connect(h, p);
        if (fd < 0) {
            throw new javax.microedition.io.ConnectionNotFoundException("khong ket noi duoc " + hp);
        }
        return new SocketConn(fd, h, p);
    }

    int fd() {
        return fd;
    }

    synchronized void release() {
        if (--refs <= 0 && fd >= 0) {
            Net.close(fd);
            fd = -1;
        }
    }

    public synchronized void close() throws IOException {
        if (connClosed) {
            return;
        }
        connClosed = true;
        if (fd >= 0) {
            Net.shutdown(fd);   /* danh thuc luong dang doc */
        }
        release();
    }

    public synchronized InputStream openInputStream() throws IOException {
        if (connClosed || fd < 0) {
            throw new IOException("da dong");
        }
        if (inOpen) {
            throw new IOException("da mo luong vao");
        }
        inOpen = true;
        refs++;
        return new SockIn(this);
    }

    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    public synchronized OutputStream openOutputStream() throws IOException {
        if (connClosed || fd < 0) {
            throw new IOException("da dong");
        }
        if (outOpen) {
            throw new IOException("da mo luong ra");
        }
        outOpen = true;
        refs++;
        return new SockOut(this);
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    public void setSocketOption(byte option, int value) throws IOException {
    }

    public int getSocketOption(byte option) throws IOException {
        return option == DELAY ? 0 : option == KEEPALIVE ? 1 : 0;
    }

    public String getLocalAddress() throws IOException {
        return Net.localAddress(fd);
    }

    public int getLocalPort() throws IOException {
        return Net.localPort(fd);
    }

    public String getAddress() throws IOException {
        return host;
    }

    public int getPort() throws IOException {
        return port;
    }
}

final class SockIn extends InputStream {
    private final SocketConn c;
    private boolean closed;
    private final byte[] one = new byte[1];

    SockIn(SocketConn c) {
        this.c = c;
    }

    public int read() throws IOException {
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : (one[0] & 0xff);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("da dong");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        int fd = c.fd();
        if (fd < 0) {
            throw new IOException("da dong");
        }
        int n = Net.read(fd, b, off, len);
        if (n == -2) {
            throw new IOException("loi doc socket");
        }
        return n;
    }

    public int available() throws IOException {
        int fd = c.fd();
        return fd < 0 ? 0 : Math.max(0, Net.available(fd));
    }

    public synchronized void close() {
        if (!closed) {
            closed = true;
            c.release();
        }
    }
}

final class SockOut extends OutputStream {
    private final SocketConn c;
    private boolean closed;
    /* gom byte cho toi flush (game ghi tung byte dau tin roi flush) -> 1 goi TCP / tin */
    private final byte[] buf = new byte[8192];
    private int n;

    SockOut(SocketConn c) {
        this.c = c;
    }

    private void send(byte[] b, int off, int len) throws IOException {
        int fd = c.fd();
        if (fd < 0) {
            throw new IOException("da dong");
        }
        int done = 0;
        while (done < len) {
            int r = Net.write(fd, b, off + done, len - done);
            if (r < 0) {
                throw new IOException("loi ghi socket");
            }
            done += r;
        }
    }

    public synchronized void write(int b) throws IOException {
        if (closed) {
            throw new IOException("da dong");
        }
        if (n == buf.length) {
            flush();
        }
        buf[n++] = (byte) b;
    }

    public synchronized void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("da dong");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (n + len > buf.length) {
            flush();
            if (len > buf.length) {
                send(b, off, len);
                return;
            }
        }
        System.arraycopy(b, off, buf, n, len);
        n += len;
    }

    public synchronized void flush() throws IOException {
        if (n > 0) {
            int k = n;
            n = 0;
            send(buf, 0, k);
        }
    }

    public synchronized void close() {
        if (!closed) {
            try {
                flush();
            } catch (IOException e) {
            }
            closed = true;
            c.release();
        }
    }
}
