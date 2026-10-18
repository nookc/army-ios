package java.lang;

import java.io.UnsupportedEncodingException;

public final class String implements CharSequence, Comparable {
    /* thu tu / ten truong phai khop rt.c (f_value__5b, f_offset_I, f_count_I, f_hash_I) */
    final char[] value;
    final int offset;
    final int count;
    private int hash;

    public String() {
        value = new char[0];
        offset = 0;
        count = 0;
    }

    public String(String s) {
        value = s.value;
        offset = s.offset;
        count = s.count;
    }

    public String(char[] v) {
        this(v, 0, v.length);
    }

    public String(char[] v, int off, int len) {
        if (off < 0 || len < 0 || off > v.length - len) {
            throw new StringIndexOutOfBoundsException();
        }
        char[] c = new char[len];
        System.arraycopy(v, off, c, 0, len);
        value = c;
        offset = 0;
        count = len;
    }

    /* dung noi bo: khong copy */
    String(int off, int len, char[] v) {
        value = v;
        offset = off;
        count = len;
    }

    public String(byte[] b) {
        this(b, 0, b.length);
    }

    public String(byte[] b, int off, int len) {
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new StringIndexOutOfBoundsException();
        }
        char[] c = new char[len];
        for (int i = 0; i < len; i++) {
            c[i] = (char) (b[off + i] & 0xFF);
        }
        value = c;
        offset = 0;
        count = len;
    }

    public String(byte[] b, String enc) throws UnsupportedEncodingException {
        this(b, 0, b.length, enc);
    }

    public String(byte[] b, int off, int len, String enc) throws UnsupportedEncodingException {
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new StringIndexOutOfBoundsException();
        }
        int cs = charset(enc);
        char[] c;
        int n;
        if (cs == 1) {
            c = new char[len];
            n = 0;
            int i = off, end = off + len;
            while (i < end) {
                int x = b[i] & 0xFF;
                if (x < 0x80) {
                    c[n++] = (char) x;
                    i++;
                } else if ((x & 0xE0) == 0xC0 && i + 1 < end && (b[i + 1] & 0xC0) == 0x80) {
                    c[n++] = (char) (((x & 0x1F) << 6) | (b[i + 1] & 0x3F));
                    i += 2;
                } else if ((x & 0xF0) == 0xE0 && i + 2 < end && (b[i + 1] & 0xC0) == 0x80 && (b[i + 2] & 0xC0) == 0x80) {
                    c[n++] = (char) (((x & 0x0F) << 12) | ((b[i + 1] & 0x3F) << 6) | (b[i + 2] & 0x3F));
                    i += 3;
                } else if ((x & 0xF8) == 0xF0 && i + 3 < end) {
                    int cp = ((x & 0x07) << 18) | ((b[i + 1] & 0x3F) << 12) | ((b[i + 2] & 0x3F) << 6) | (b[i + 3] & 0x3F);
                    cp -= 0x10000;
                    if (n + 2 > c.length) {
                        char[] nc = new char[c.length + 16];
                        System.arraycopy(c, 0, nc, 0, n);
                        c = nc;
                    }
                    c[n++] = (char) (0xD800 + (cp >> 10));
                    c[n++] = (char) (0xDC00 + (cp & 0x3FF));
                    i += 4;
                } else {
                    c[n++] = '�';
                    i++;
                }
            }
        } else {
            c = new char[len];
            n = len;
            for (int i = 0; i < len; i++) {
                int x = b[off + i] & 0xFF;
                c[i] = (cs == 2 && x > 0x7F) ? '�' : (char) x;
            }
        }
        value = c;
        offset = 0;
        count = n;
    }

    public String(StringBuffer sb) {
        String s = sb.toString();
        value = s.value;
        offset = s.offset;
        count = s.count;
    }

    /** 0 latin1, 1 utf8, 2 ascii */
    static int charset(String enc) throws UnsupportedEncodingException {
        if (enc == null) {
            throw new NullPointerException();
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < enc.count; i++) {
            char ch = enc.value[enc.offset + i];
            if (ch == '-' || ch == '_' || ch == ' ') {
                continue;
            }
            sb.append(Character.toUpperCase(ch));
        }
        String n = sb.toString();
        if (n.equals("UTF8")) {
            return 1;
        }
        if (n.equals("ISO88591") || n.equals("LATIN1") || n.equals("ISO8859") || n.equals("8859_1")) {
            return 0;
        }
        if (n.equals("ASCII") || n.equals("USASCII")) {
            return 2;
        }
        throw new UnsupportedEncodingException(enc);
    }

    public int length() {
        return count;
    }

    public char charAt(int i) {
        if (i < 0 || i >= count) {
            throw new StringIndexOutOfBoundsException(i);
        }
        return value[offset + i];
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcBegin < 0 || srcEnd > count || srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException();
        }
        System.arraycopy(value, offset + srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    public byte[] getBytes() {
        byte[] b = new byte[count];
        for (int i = 0; i < count; i++) {
            char ch = value[offset + i];
            b[i] = ch <= 0xFF ? (byte) ch : (byte) '?';
        }
        return b;
    }

    public byte[] getBytes(String enc) throws UnsupportedEncodingException {
        int cs = charset(enc);
        if (cs != 1) {
            byte[] b = new byte[count];
            int max = cs == 2 ? 0x7F : 0xFF;
            for (int i = 0; i < count; i++) {
                char ch = value[offset + i];
                b[i] = ch <= max ? (byte) ch : (byte) '?';
            }
            return b;
        }
        int n = 0;
        for (int i = 0; i < count; i++) {
            char ch = value[offset + i];
            n += ch < 0x80 ? 1 : ch < 0x800 ? 2 : 3;
        }
        byte[] b = new byte[n];
        int k = 0;
        for (int i = 0; i < count; i++) {
            int ch = value[offset + i];
            if (ch >= 0xD800 && ch < 0xDC00 && i + 1 < count) {
                int lo = value[offset + i + 1];
                if (lo >= 0xDC00 && lo < 0xE000) {
                    int cp = 0x10000 + ((ch - 0xD800) << 10) + (lo - 0xDC00);
                    // 3+3 byte -> 4 byte: mang da du (6 >= 4), cat bot o cuoi
                    b[k++] = (byte) (0xF0 | (cp >> 18));
                    b[k++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                    b[k++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                    b[k++] = (byte) (0x80 | (cp & 0x3F));
                    i++;
                    continue;
                }
            }
            if (ch < 0x80) {
                b[k++] = (byte) ch;
            } else if (ch < 0x800) {
                b[k++] = (byte) (0xC0 | (ch >> 6));
                b[k++] = (byte) (0x80 | (ch & 0x3F));
            } else {
                b[k++] = (byte) (0xE0 | (ch >> 12));
                b[k++] = (byte) (0x80 | ((ch >> 6) & 0x3F));
                b[k++] = (byte) (0x80 | (ch & 0x3F));
            }
        }
        if (k != n) {
            byte[] r = new byte[k];
            System.arraycopy(b, 0, r, 0, k);
            return r;
        }
        return b;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof String)) {
            return false;
        }
        String s = (String) o;
        int n = count;
        if (n != s.count) {
            return false;
        }
        char[] a = value, b = s.value;
        int i = offset, j = s.offset;
        while (n-- > 0) {
            if (a[i++] != b[j++]) {
                return false;
            }
        }
        return true;
    }

    public boolean equalsIgnoreCase(String s) {
        if (s == null || s.count != count) {
            return false;
        }
        return regionMatches(true, 0, s, 0, count);
    }

    public int compareTo(String s) {
        int n = Math.min(count, s.count);
        char[] a = value, b = s.value;
        int i = offset, j = s.offset;
        for (int k = 0; k < n; k++) {
            char x = a[i + k], y = b[j + k];
            if (x != y) {
                return x - y;
            }
        }
        return count - s.count;
    }

    public int compareTo(Object o) {
        return compareTo((String) o);
    }

    public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
        if (ooffset < 0 || toffset < 0 || toffset > (long) count - len || ooffset > (long) other.count - len) {
            return false;
        }
        char[] a = value, b = other.value;
        int i = offset + toffset, j = other.offset + ooffset;
        while (len-- > 0) {
            char c1 = a[i++], c2 = b[j++];
            if (c1 == c2) {
                continue;
            }
            if (ignoreCase) {
                char u1 = Character.toUpperCase(c1), u2 = Character.toUpperCase(c2);
                if (u1 == u2 || Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        return regionMatches(false, toffset, other, ooffset, len);
    }

    public boolean startsWith(String prefix, int toffset) {
        if (toffset < 0 || toffset > count - prefix.count) {
            return false;
        }
        char[] a = value, b = prefix.value;
        int i = offset + toffset, j = prefix.offset;
        for (int k = 0; k < prefix.count; k++) {
            if (a[i + k] != b[j + k]) {
                return false;
            }
        }
        return true;
    }

    public boolean startsWith(String prefix) {
        return startsWith(prefix, 0);
    }

    public boolean endsWith(String suffix) {
        return startsWith(suffix, count - suffix.count);
    }

    public int hashCode() {
        int h = hash;
        if (h == 0 && count > 0) {
            char[] v = value;
            int o = offset;
            for (int i = 0; i < count; i++) {
                h = 31 * h + v[o + i];
            }
            hash = h;
        }
        return h;
    }

    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    public int indexOf(int ch, int from) {
        if (from < 0) {
            from = 0;
        }
        char[] v = value;
        for (int i = from; i < count; i++) {
            if (v[offset + i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, count - 1);
    }

    public int lastIndexOf(int ch, int from) {
        if (from >= count) {
            from = count - 1;
        }
        char[] v = value;
        for (int i = from; i >= 0; i--) {
            if (v[offset + i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(String s) {
        return indexOf(s, 0);
    }

    public int indexOf(String s, int from) {
        if (from < 0) {
            from = 0;
        }
        int n = s.count;
        if (n == 0) {
            return from <= count ? from : count;
        }
        char[] a = value, b = s.value;
        char first = b[s.offset];
        int max = count - n;
        for (int i = from; i <= max; i++) {
            if (a[offset + i] != first) {
                continue;
            }
            int k = 1;
            while (k < n && a[offset + i + k] == b[s.offset + k]) {
                k++;
            }
            if (k == n) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(String s) {
        int n = s.count;
        for (int i = count - n; i >= 0; i--) {
            if (startsWith(s, i)) {
                return i;
            }
        }
        return -1;
    }

    public String substring(int begin) {
        return substring(begin, count);
    }

    public String substring(int begin, int end) {
        if (begin < 0) {
            throw new StringIndexOutOfBoundsException(begin);
        }
        if (end > count) {
            throw new StringIndexOutOfBoundsException(end);
        }
        if (begin > end) {
            throw new StringIndexOutOfBoundsException(end - begin);
        }
        if (begin == 0 && end == count) {
            return this;
        }
        return new String(offset + begin, end - begin, value);
    }

    public String concat(String s) {
        if (s.count == 0) {
            return this;
        }
        char[] c = new char[count + s.count];
        System.arraycopy(value, offset, c, 0, count);
        System.arraycopy(s.value, s.offset, c, count, s.count);
        return new String(0, c.length, c);
    }

    public String replace(char a, char b) {
        if (a == b || indexOf(a) < 0) {
            return this;
        }
        char[] c = new char[count];
        for (int i = 0; i < count; i++) {
            char ch = value[offset + i];
            c[i] = ch == a ? b : ch;
        }
        return new String(0, count, c);
    }

    public String toLowerCase() {
        char[] c = null;
        for (int i = 0; i < count; i++) {
            char ch = value[offset + i];
            char l = Character.toLowerCase(ch);
            if (l != ch && c == null) {
                c = new char[count];
                System.arraycopy(value, offset, c, 0, count);
            }
            if (c != null) {
                c[i] = l;
            }
        }
        return c == null ? this : new String(0, count, c);
    }

    public String toUpperCase() {
        char[] c = null;
        for (int i = 0; i < count; i++) {
            char ch = value[offset + i];
            char u = Character.toUpperCase(ch);
            if (u != ch && c == null) {
                c = new char[count];
                System.arraycopy(value, offset, c, 0, count);
            }
            if (c != null) {
                c[i] = u;
            }
        }
        return c == null ? this : new String(0, count, c);
    }

    public String trim() {
        int b = 0, e = count;
        while (b < e && value[offset + b] <= ' ') {
            b++;
        }
        while (e > b && value[offset + e - 1] <= ' ') {
            e--;
        }
        return (b > 0 || e < count) ? substring(b, e) : this;
    }

    public String toString() {
        return this;
    }

    public char[] toCharArray() {
        char[] c = new char[count];
        System.arraycopy(value, offset, c, 0, count);
        return c;
    }

    public native String intern();

    public static String valueOf(Object o) {
        return o == null ? "null" : o.toString();
    }

    public static String valueOf(char[] c) {
        return new String(c);
    }

    public static String valueOf(char[] c, int off, int n) {
        return new String(c, off, n);
    }

    public static String valueOf(boolean b) {
        return b ? "true" : "false";
    }

    public static String valueOf(char c) {
        char[] a = new char[]{c};
        return new String(0, 1, a);
    }

    public static String valueOf(int i) {
        return Integer.toString(i);
    }

    public static String valueOf(long l) {
        return Long.toString(l);
    }

    public static String valueOf(float f) {
        return Float.toString(f);
    }

    public static String valueOf(double d) {
        return Double.toString(d);
    }
}
