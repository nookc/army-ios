package javax.microedition.lcdui;

public class TextField extends Item {
    public static final int ANY = 0;
    public static final int EMAILADDR = 1;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;
    public static final int CONSTRAINT_MASK = 0xFFFF;
    public static final int PASSWORD = 0x10000;
    public static final int UNEDITABLE = 0x20000;
    public static final int SENSITIVE = 0x40000;
    public static final int NON_PREDICTIVE = 0x80000;
    public static final int INITIAL_CAPS_WORD = 0x100000;
    public static final int INITIAL_CAPS_SENTENCE = 0x200000;

    String text = "";
    int maxSize;
    int constraints;

    /* Avatar: o nhap chu trong Form (hien bang hop nhap chu cua he dieu hanh, xem j2n.Ui) */
    public TextField(String label, String text, int maxSize, int constraints) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException();
        }
        this.label = label;
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
}
