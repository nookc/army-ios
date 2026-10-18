package java.lang;

public final class StringBuffer implements CharSequence {
    private char[] value;
    private int count;

    public StringBuffer() {
        value = new char[16];
    }

    public StringBuffer(int cap) {
        if (cap < 0) {
            throw new NegativeArraySizeException();
        }
        value = new char[cap];
    }

    public StringBuffer(String s) {
        value = new char[s.length() + 16];
        append(s);
    }

    public int length() {
        return count;
    }

    public int capacity() {
        return value.length;
    }

    private void grow(int min) {
        int n = (value.length + 1) * 2;
        if (n < min) {
            n = min;
        }
        char[] v = new char[n];
        System.arraycopy(value, 0, v, 0, count);
        value = v;
    }

    public void ensureCapacity(int min) {
        if (min > value.length) {
            grow(min);
        }
    }

    public void setLength(int n) {
        if (n < 0) {
            throw new StringIndexOutOfBoundsException(n);
        }
        if (n > value.length) {
            grow(n);
        }
        for (int i = count; i < n; i++) {
            value[i] = 0;
        }
        count = n;
    }

    public char charAt(int i) {
        if (i < 0 || i >= count) {
            throw new StringIndexOutOfBoundsException(i);
        }
        return value[i];
    }

    public void getChars(int b, int e, char[] dst, int db) {
        if (b < 0 || e > count || b > e) {
            throw new StringIndexOutOfBoundsException();
        }
        System.arraycopy(value, b, dst, db, e - b);
    }

    public void setCharAt(int i, char c) {
        if (i < 0 || i >= count) {
            throw new StringIndexOutOfBoundsException(i);
        }
        value[i] = c;
    }

    public StringBuffer append(Object o) {
        return append(String.valueOf(o));
    }

    public StringBuffer append(String s) {
        if (s == null) {
            s = "null";
        }
        int n = s.length();
        if (count + n > value.length) {
            grow(count + n);
        }
        s.getChars(0, n, value, count);
        count += n;
        return this;
    }

    public StringBuffer append(StringBuffer sb) {
        return append(String.valueOf(sb));
    }

    public StringBuffer append(char[] c) {
        return append(c, 0, c.length);
    }

    public StringBuffer append(char[] c, int off, int n) {
        if (count + n > value.length) {
            grow(count + n);
        }
        System.arraycopy(c, off, value, count, n);
        count += n;
        return this;
    }

    public StringBuffer append(boolean b) {
        return append(b ? "true" : "false");
    }

    public StringBuffer append(char c) {
        if (count + 1 > value.length) {
            grow(count + 1);
        }
        value[count++] = c;
        return this;
    }

    public StringBuffer append(int i) {
        return append(Integer.toString(i));
    }

    public StringBuffer append(long l) {
        return append(Long.toString(l));
    }

    public StringBuffer append(float f) {
        return append(Float.toString(f));
    }

    public StringBuffer append(double d) {
        return append(Double.toString(d));
    }

    public StringBuffer delete(int start, int end) {
        if (end > count) {
            end = count;
        }
        if (start < 0 || start > end) {
            throw new StringIndexOutOfBoundsException();
        }
        int n = end - start;
        if (n > 0) {
            System.arraycopy(value, end, value, start, count - end);
            count -= n;
        }
        return this;
    }

    public StringBuffer deleteCharAt(int i) {
        if (i < 0 || i >= count) {
            throw new StringIndexOutOfBoundsException(i);
        }
        System.arraycopy(value, i + 1, value, i, count - i - 1);
        count--;
        return this;
    }

    public StringBuffer insert(int off, String s) {
        if (off < 0 || off > count) {
            throw new StringIndexOutOfBoundsException();
        }
        if (s == null) {
            s = "null";
        }
        int n = s.length();
        if (count + n > value.length) {
            grow(count + n);
        }
        System.arraycopy(value, off, value, off + n, count - off);
        s.getChars(0, n, value, off);
        count += n;
        return this;
    }

    public StringBuffer insert(int off, char c) {
        if (off < 0 || off > count) {
            throw new StringIndexOutOfBoundsException();
        }
        if (count + 1 > value.length) {
            grow(count + 1);
        }
        System.arraycopy(value, off, value, off + 1, count - off);
        value[off] = c;
        count++;
        return this;
    }

    public StringBuffer insert(int off, Object o) {
        return insert(off, String.valueOf(o));
    }

    public StringBuffer insert(int off, char[] c) {
        return insert(off, new String(c));
    }

    public StringBuffer insert(int off, boolean b) {
        return insert(off, b ? "true" : "false");
    }

    public StringBuffer insert(int off, int i) {
        return insert(off, Integer.toString(i));
    }

    public StringBuffer insert(int off, long l) {
        return insert(off, Long.toString(l));
    }

    public StringBuffer reverse() {
        for (int i = 0, j = count - 1; i < j; i++, j--) {
            char t = value[i];
            value[i] = value[j];
            value[j] = t;
        }
        return this;
    }

    public String toString() {
        return new String(value, 0, count);
    }
}
