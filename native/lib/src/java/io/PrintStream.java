package java.io;

public class PrintStream extends OutputStream {
    private OutputStream out;
    private boolean error;

    public PrintStream(OutputStream out) {
        this.out = out;
    }

    public void write(int b) {
        try {
            out.write(b);
        } catch (IOException e) {
            error = true;
        }
    }

    public void write(byte[] b, int off, int len) {
        try {
            out.write(b, off, len);
        } catch (IOException e) {
            error = true;
        }
    }

    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            error = true;
        }
    }

    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            error = true;
        }
    }

    public boolean checkError() {
        return error;
    }

    private void str(String s) {
        if (s == null) {
            s = "null";
        }
        try {
            byte[] b = s.getBytes("UTF-8");
            write(b, 0, b.length);
        } catch (UnsupportedEncodingException e) {
        }
    }

    public void print(String s) {
        str(s);
    }

    public void print(Object o) {
        str(String.valueOf(o));
    }

    public void print(char c) {
        str(String.valueOf(c));
    }

    public void print(int i) {
        str(String.valueOf(i));
    }

    public void print(long l) {
        str(String.valueOf(l));
    }

    public void print(boolean b) {
        str(String.valueOf(b));
    }

    public void print(char[] c) {
        str(new String(c));
    }

    public void print(float f) {
        str(String.valueOf(f));
    }

    public void print(double d) {
        str(String.valueOf(d));
    }

    public void println() {
        write('\n');
        flush();
    }

    public void println(String s) {
        str(s);
        println();
    }

    public void println(Object o) {
        println(String.valueOf(o));
    }

    public void println(char c) {
        println(String.valueOf(c));
    }

    public void println(int i) {
        println(String.valueOf(i));
    }

    public void println(long l) {
        println(String.valueOf(l));
    }

    public void println(boolean b) {
        println(String.valueOf(b));
    }

    public void println(char[] c) {
        println(new String(c));
    }

    public void println(float f) {
        println(String.valueOf(f));
    }

    public void println(double d) {
        println(String.valueOf(d));
    }
}
