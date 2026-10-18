package java.lang;

public final class Integer {
    public static final int MIN_VALUE = 0x80000000;
    public static final int MAX_VALUE = 0x7fffffff;
    private final int value;

    public Integer(int v) {
        value = v;
    }

    public static int parseInt(String s) throws NumberFormatException {
        return parseInt(s, 10);
    }

    public static int parseInt(String s, int radix) throws NumberFormatException {
        if (s == null) {
            throw new NumberFormatException("null");
        }
        if (radix < 2 || radix > 36) {
            throw new NumberFormatException("radix");
        }
        int n = s.length();
        if (n == 0) {
            throw new NumberFormatException(s);
        }
        int i = 0;
        boolean neg = false;
        int limit = -MAX_VALUE;
        char c0 = s.charAt(0);
        if (c0 == '-') {
            neg = true;
            limit = MIN_VALUE;
            i++;
        } else if (c0 == '+') {
            i++;
        }
        if (i >= n) {
            throw new NumberFormatException(s);
        }
        int mulmin = limit / radix;
        int result = 0;
        while (i < n) {
            int d = Character.digit(s.charAt(i++), radix);
            if (d < 0 || result < mulmin) {
                throw new NumberFormatException(s);
            }
            result *= radix;
            if (result < limit + d) {
                throw new NumberFormatException(s);
            }
            result -= d;
        }
        return neg ? result : -result;
    }

    public static Integer valueOf(String s) throws NumberFormatException {
        return new Integer(parseInt(s, 10));
    }

    public static Integer valueOf(String s, int radix) throws NumberFormatException {
        return new Integer(parseInt(s, radix));
    }

    public static Integer valueOf(int i) {
        return new Integer(i);
    }

    public static String toString(int i) {
        if (i == MIN_VALUE) {
            return "-2147483648";
        }
        char[] buf = new char[11];
        int p = 11;
        boolean neg = i < 0;
        if (neg) {
            i = -i;
        }
        do {
            buf[--p] = (char) ('0' + i % 10);
            i /= 10;
        } while (i != 0);
        if (neg) {
            buf[--p] = '-';
        }
        return new String(buf, p, 11 - p);
    }

    public static String toString(int i, int radix) {
        if (radix < 2 || radix > 36) {
            radix = 10;
        }
        if (radix == 10) {
            return toString(i);
        }
        char[] buf = new char[33];
        int p = 33;
        boolean neg = i < 0;
        if (!neg) {
            i = -i;
        }
        while (i <= -radix) {
            buf[--p] = Character.forDigit(-(i % radix), radix);
            i /= radix;
        }
        buf[--p] = Character.forDigit(-i, radix);
        if (neg) {
            buf[--p] = '-';
        }
        return new String(buf, p, 33 - p);
    }

    private static String unsigned(int i, int shift) {
        char[] buf = new char[32];
        int p = 32;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            buf[--p] = Character.forDigit(i & mask, radix);
            i >>>= shift;
        } while (i != 0);
        return new String(buf, p, 32 - p);
    }

    public static String toHexString(int i) {
        return unsigned(i, 4);
    }

    public static String toOctalString(int i) {
        return unsigned(i, 3);
    }

    public static String toBinaryString(int i) {
        return unsigned(i, 1);
    }

    public byte byteValue() {
        return (byte) value;
    }

    public short shortValue() {
        return (short) value;
    }

    public int intValue() {
        return value;
    }

    public long longValue() {
        return value;
    }

    public float floatValue() {
        return value;
    }

    public double doubleValue() {
        return value;
    }

    public String toString() {
        return toString(value);
    }

    public int hashCode() {
        return value;
    }

    public boolean equals(Object o) {
        return o instanceof Integer && ((Integer) o).value == value;
    }
}
