package javax.microedition.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Connector {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int READ_WRITE = 3;

    private Connector() {
    }

    public static Connection open(String name) throws IOException {
        return open(name, READ_WRITE, false);
    }

    public static Connection open(String name, int mode) throws IOException {
        return open(name, mode, false);
    }

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name == null) {
            throw new IllegalArgumentException("null");
        }
        String n = name.trim();
        if (n.startsWith("socket://")) {
            return j2n.SocketConn.open(n.substring(9));
        }
        if (n.startsWith("http://")) {
            return new j2n.HttpConn(n);
        }
        if (n.startsWith("https://")) {
            return new j2n.HttpConn(n);
        }
        if (n.startsWith("sms://")) {
            return new j2n.SmsConn(n.substring(6));
        }
        throw new ConnectionNotFoundException(name);
    }

    public static DataInputStream openDataInputStream(String name) throws IOException {
        return ((InputConnection) open(name, READ)).openDataInputStream();
    }

    public static DataOutputStream openDataOutputStream(String name) throws IOException {
        return ((OutputConnection) open(name, WRITE)).openDataOutputStream();
    }

    public static InputStream openInputStream(String name) throws IOException {
        return ((InputConnection) open(name, READ)).openInputStream();
    }

    public static OutputStream openOutputStream(String name) throws IOException {
        return ((OutputConnection) open(name, WRITE)).openOutputStream();
    }
}
