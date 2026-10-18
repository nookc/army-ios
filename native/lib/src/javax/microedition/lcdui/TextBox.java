package javax.microedition.lcdui;

public class TextBox extends Screen {
    String text;
    int maxSize;
    int constraints;

    public TextBox(String title, String text, int maxSize, int constraints) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException();
        }
        this.title = title;
        this.maxSize = maxSize;
        this.constraints = constraints;
        setString(text);
    }

    public String getString() {
        return text;
    }

    public void setString(String s) {
        if (s == null) {
            s = "";
        }
        if (s.length() > maxSize) {
            s = s.substring(0, maxSize);
        }
        text = s;
    }

    public int getChars(char[] data) {
        text.getChars(0, text.length(), data, 0);
        return text.length();
    }

    public int size() {
        return text.length();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int setMaxSize(int m) {
        if (m <= 0) {
            throw new IllegalArgumentException();
        }
        maxSize = m;
        if (text.length() > m) {
            text = text.substring(0, m);
        }
        return m;
    }

    public int getConstraints() {
        return constraints;
    }

    public void setConstraints(int c) {
        constraints = c;
    }

    public void insert(String s, int pos) {
        if (pos < 0) {
            pos = 0;
        }
        if (pos > text.length()) {
            pos = text.length();
        }
        setString(text.substring(0, pos) + s + text.substring(pos));
    }

    public void delete(int off, int len) {
        setString(text.substring(0, off) + text.substring(off + len));
    }

    public int getCaretPosition() {
        return text.length();
    }

    public void setInitialInputMode(String m) {
    }
}
