package java.lang;

public final class Long {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
    private final long value;

    public Long(long v) {
        value = v;
    }

    public static long parseLong(String s) throws NumberFormatException {
        return parseLong(s, 10);
    }

    public static long parseLong(String s, int radix) throws NumberFormatException {
        if (s == null) {
            throw new NumberFormatException("null");
        }
        int n = s.length();
        if (n == 0 || radix < 2 || radix > 36) {
            throw new NumberFormatException(s);
        }
        int i = 0;
        boolean neg = false;
        long limit = -MAX_VALUE;
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
        long mulmin = limit / radix;
        long result = 0;
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

    public static Long valueOf(long l) {
        return new Long(l);
    }

    public static String toString(long i) {
        if (i == MIN_VALUE) {
            return "-9223372036854775808";
        }
        char[] buf = new char[20];
        int p = 20;
        boolean neg = i < 0;
        if (neg) {
            i = -i;
        }
        do {
            buf[--p] = (char) ('0' + (int) (i % 10));
            i /= 10;
        } while (i != 0);
        if (neg) {
            buf[--p] = '-';
        }
        return new String(buf, p, 20 - p);
    }

    public static String toString(long i, int radix) {
        if (radix < 2 || radix > 36 || radix == 10) {
            return toString(i);
        }
        char[] buf = new char[65];
        int p = 65;
        boolean neg = i < 0;
        if (!neg) {
            i = -i;
        }
        while (i <= -radix) {
            buf[--p] = Character.forDigit((int) (-(i % radix)), radix);
            i /= radix;
        }
        buf[--p] = Character.forDigit((int) (-i), radix);
        if (neg) {
            buf[--p] = '-';
        }
        return new String(buf, p, 65 - p);
    }

    public static String toHexString(long i) {
        char[] buf = new char[16];
        int p = 16;
        do {
            buf[--p] = Character.forDigit((int) (i & 15), 16);
            i >>>= 4;
        } while (i != 0);
        return new String(buf, p, 16 - p);
    }

    public long longValue() {
        return value;
    }

    public int intValue() {
        return (int) value;
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
        return (int) (value ^ (value >>> 32));
    }

    public boolean equals(Object o) {
        return o instanceof Long && ((Long) o).value == value;
    }
}
