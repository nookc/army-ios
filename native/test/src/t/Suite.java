package t;

import java.io.*;
import java.util.*;

/** bo thu so sanh: chay tren JVM that va tren runtime dich san, in ra giong nhau la dung */
public class Suite {
    static PrintStream o;
    static int clinitOrder;

    static void p(String s) {
        o.println(s);
    }

    interface Shape {
        int area();
        String name();
    }

    static abstract class Base implements Shape {
        static String tag;
        static {
            tag = "base" + (++clinitOrder);
        }
        int w;

        Base(int w) {
            this.w = w;
        }

        public String name() {
            return "Base" + w;
        }

        abstract int k();

        int twice() {
            return 2 * k();
        }
    }

    static class Sq extends Base {
        static String tag2;
        static {
            tag2 = "sq" + (++clinitOrder);
        }

        Sq(int w) {
            super(w);
        }

        public int area() {
            return w * w;
        }

        int k() {
            return w + 1;
        }

        public String name() {
            return "Sq(" + super.name() + ")";
        }
    }

    static class Rect extends Sq {
        int h;

        Rect(int w, int h) {
            super(w);
            this.h = h;
        }

        public int area() {
            return w * h;
        }

        int k() {
            return h;
        }
    }

    static int fin(int x) {
        try {
            if (x > 0) {
                return x * 10;
            }
            throw new IllegalStateException("neg");
        } catch (IllegalStateException e) {
            return -1;
        } finally {
            clinitOrder += 100;
        }
    }

    static String exc(int kind) {
        try {
            switch (kind) {
                case 0:
                    int[] a = new int[2];
                    return "" + a[kind + 5];
                case 1:
                    Object s = "x";
                    Integer i = (Integer) s;
                    return "no";
                case 2:
                    String n = null;
                    return "" + n.length();
                case 3:
                    return "" + (5 / (kind - 3));
                case 4:
                    return "" + new int[-1].length;
                case 5:
                    throw new RuntimeException("custom");
                default:
                    return "ok";
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return "AIOOBE";
        } catch (ClassCastException e) {
            return "CCE";
        } catch (NullPointerException e) {
            return "NPE";
        } catch (ArithmeticException e) {
            return "AE " + e.getMessage();
        } catch (NegativeArraySizeException e) {
            return "NASE";
        } catch (RuntimeException e) {
            return "RE " + e.getMessage();
        }
    }

    static int sw(int x) {
        switch (x) {
            case -1: return 10;
            case 0: return 11;
            case 1: return 12;
            case 2: return 13;
            case 5: return 15;
            default: return 99;
        }
    }

    static int sw2(int x) {
        switch (x) {
            case -100000: return 1;
            case 7: return 2;
            case 1000: return 3;
            case 123456789: return 4;
            default: return 5;
        }
    }

    static int counter;

    static synchronized void inc() {
        counter++;
    }

    public static void run(PrintStream out) throws Exception {
        o = out;
        // ---- so nguyen
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
        p("int " + (a + 1) + " " + (b - 1) + " " + (a * 3) + " " + (b / -1) + " " + (b % -1) + " " + (-7 / 2) + " " + (-7 % 2)
                + " " + (7 % -3));
        p("shift " + (1 << 33) + " " + (-16 >> 2) + " " + (-16 >>> 28) + " " + (1L << 65) + " " + (-1L >>> 60) + " " + (5 >> -1));
        p("cast " + (byte) 200 + " " + (char) 70000 + " " + (int) (char) -1 + " " + (short) 40000 + " " + (int) 3.99 + " "
                + (int) -3.99 + " " + (long) 1e30 + " " + (int) Float.NaN + " " + (int) Double.POSITIVE_INFINITY);
        long L = Long.MAX_VALUE;
        p("long " + (L + 1) + " " + (L * 7) + " " + (Long.MIN_VALUE / -1) + " " + (-123456789012L % 1000) + " "
                + Long.toString(-255, 16) + " " + Long.toHexString(-1L) + " " + Long.parseLong("-9223372036854775808"));
        p("cmp " + Long.MAX_VALUE + " " + (0.0f / 0 > 1) + " " + (0.0 / 0 < 1) + " " + (1f < 2f));
        // ---- so thuc
        p("float " + 1.0f + " " + 0.1f + " " + 1e10f + " " + 1.5e-5f + " " + 123456.78f + " " + (1f / 3) + " " + -0.0f + " "
                + 100f + " " + 1e7f + " " + 9999999f);
        p("double " + 1.0 + " " + 0.1 + " " + 1e10 + " " + 1.5e-5 + " " + (1.0 / 3) + " " + 123456789.125 + " " + 1e-3 + " "
                + 1e21 + " " + Math.sqrt(2) + " " + Math.floor(-1.5) + " " + Math.ceil(-1.5));
        p("parse " + Float.parseFloat("3.25") + " " + Double.parseDouble("-1e3") + " " + Float.floatToIntBits(1.5f) + " "
                + Double.doubleToLongBits(-2.0) + " " + Float.intBitsToFloat(0x40490fdb));
        // ---- chuoi
        String s = "Xin chào Việt Nam";
        p("str " + s.length() + " " + s.charAt(5) + " " + s.indexOf("Việt") + " " + s.indexOf('a', 8) + " " + s.lastIndexOf('a')
                + " " + s.substring(4, 8) + "|" + s.toUpperCase() + "|" + s.toLowerCase() + "|" + "  tr im ".trim() + "|");
        p("str2 " + "abc".compareTo("abd") + " " + "b".compareTo("abc") + " " + "abc".equals(new String("abc")) + " "
                + ("ab" + "c").hashCode() + " " + "".hashCode() + " " + "x".equalsIgnoreCase("X") + " " + "abcabc".lastIndexOf("bc")
                + " " + "a,b,,c".indexOf(",,") + " " + "hello".replace('l', 'L') + " " + "abc".startsWith("ab") + " "
                + "abc".endsWith("bc") + " " + "abc".concat("def"));
        String lit = "intern-me";
        String built = new StringBuffer("intern").append("-me").toString();
        p("intern " + (lit == built) + " " + (lit == built.intern()) + " " + (lit == "intern-me"));
        byte[] u8 = "Đồng ý ₫".getBytes("UTF-8");
        p("utf8 " + u8.length + " " + new String(u8, "UTF-8") + " " + "Đồng".getBytes("ISO-8859-1").length + " " + (int) "Đ".getBytes("ISO-8859-1")[0]);
        StringBuffer sb = new StringBuffer("0123456789");
        sb.insert(3, "abc").delete(0, 2).reverse().setCharAt(0, 'Z');
        sb.append(true).append('c').append(42).append(-7L).append((Object) null).append(2.5f);
        p("sb " + sb + " " + sb.length() + " " + sb.charAt(3));
        sb.setLength(5);
        sb.deleteCharAt(1);
        p("sb2 " + sb + " " + new StringBuffer("ab").insert(0, 12).insert(4, 'x'));
        p("int2 " + Integer.parseInt("-2147483648") + " " + Integer.parseInt("+15") + " " + Integer.parseInt("ff", 16) + " "
                + Integer.toString(-255, 2) + " " + Integer.toHexString(-1) + " " + Integer.toOctalString(8) + " " + Short.parseShort("-123")
                + " " + Byte.parseByte("12") + " " + Integer.valueOf("77").intValue());
        String[] bad = {"", "-", "2147483648", "12a", " 1", null};
        StringBuffer r = new StringBuffer();
        for (int i = 0; i < bad.length; i++) {
            try {
                r.append(Integer.parseInt(bad[i])).append(';');
            } catch (NumberFormatException e) {
                r.append("NFE;");
            }
        }
        p("nfe " + r);
        p("char " + Character.toUpperCase('ằ') + Character.toUpperCase('ữ') + Character.toUpperCase('đ') + Character.toUpperCase('ơ')
                + Character.toLowerCase('Ặ') + Character.toLowerCase('Ư') + " " + Character.isDigit('7') + Character.digit('z', 36)
                + Character.forDigit(11, 16));
        // ---- lop / ngoai le
        Shape[] sh = {new Sq(3), new Rect(2, 5), new Sq(1)};
        StringBuffer z = new StringBuffer();
        for (int i = 0; i < sh.length; i++) {
            Base bb = (Base) sh[i];
            z.append(sh[i].area()).append(':').append(sh[i].name()).append(':').append(bb.twice()).append(':')
                    .append(sh[i] instanceof Rect).append(sh[i] instanceof Shape).append(' ');
        }
        p("obj " + z + Base.tag + " " + Sq.tag2);
        p("fin " + fin(3) + " " + fin(-1) + " " + clinitOrder);
        StringBuffer ex = new StringBuffer();
        for (int i = 0; i < 7; i++) {
            ex.append(exc(i)).append('|');
        }
        p("exc " + ex);
        p("sw " + sw(-1) + sw(0) + sw(1) + sw(2) + sw(3) + sw(5) + sw(-2) + " " + sw2(-100000) + sw2(7) + sw2(1000) + sw2(123456789)
                + sw2(8));
        // ---- mang
        int[][] m = new int[3][4];
        m[2][3] = 7;
        Object[] oa = new String[2];
        boolean ase = false;
        try {
            oa[0] = new Integer(1);
        } catch (ArrayStoreException e) {
            ase = true;
        }
        int[] arr = {1, 2, 3, 4, 5, 6};
        System.arraycopy(arr, 0, arr, 2, 4);
        p("arr " + m.length + " " + m[0].length + " " + m[2][3] + " " + (oa instanceof Object[]) + " " + (oa instanceof String[]) + " "
                + ase + " " + arr[0] + arr[1] + arr[2] + arr[3] + arr[4] + arr[5] + " " + new byte[3][][].length);
        long[] la = new long[2];
        la[1] = 1L << 40;
        double[] da = {0.5};
        char[] ca = "hi".toCharArray();
        boolean[] ba = new boolean[1];
        ba[0] = true;
        p("arr2 " + la[1] + " " + da[0] + " " + ca[1] + " " + ba[0] + " " + new String(ca, 0, 1));
        // ---- Vector / Hashtable
        Vector v = new Vector();
        for (int i = 0; i < 20; i++) {
            v.addElement(new Integer(i * i));
        }
        v.removeElementAt(3);
        v.insertElementAt("x", 0);
        v.removeElement(new Integer(16));
        v.setElementAt("y", 5);
        p("vec " + v.size() + " " + v.elementAt(0) + " " + v.elementAt(5) + " " + v.indexOf(new Integer(25)) + " " + v.contains("x") + " "
                + v.lastElement());
        Hashtable h = new Hashtable();
        for (int i = 0; i < 50; i++) {
            h.put("k" + i, new Integer(i));
        }
        h.remove("k7");
        int sum = 0;
        for (Enumeration e = h.elements(); e.hasMoreElements();) {
            sum += ((Integer) e.nextElement()).intValue();
        }
        p("ht " + h.size() + " " + h.get("k10") + " " + h.containsKey("k7") + " " + sum);
        Stack st = new Stack();
        st.push("a");
        st.push("b");
        p("stack " + st.pop() + st.peek() + st.empty() + st.search("a"));
        // ---- Random
        Random rnd = new Random(12345);
        StringBuffer rs = new StringBuffer();
        for (int i = 0; i < 5; i++) {
            rs.append(rnd.nextInt()).append(',').append(rnd.nextInt(100)).append(',');
        }
        rs.append(rnd.nextLong());
        p("rnd " + rs);
        // ---- luong
        Thread[] ts = new Thread[4];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(new Runnable() {
                public void run() {
                    for (int j = 0; j < 10000; j++) {
                        inc();
                    }
                }
            });
            ts[i].start();
        }
        for (int i = 0; i < ts.length; i++) {
            ts[i].join();
        }
        final Object lock = new Object();
        final int[] box = new int[1];
        Thread w = new Thread() {
            public void run() {
                synchronized (lock) {
                    box[0] = 42;
                    lock.notifyAll();
                }
            }
        };
        synchronized (lock) {
            w.start();
            while (box[0] == 0) {
                lock.wait(1000);
            }
        }
        p("thread " + counter + " " + box[0]);
        // ---- Data streams
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bo);
        dout.writeInt(-2);
        dout.writeShort(65535);
        dout.writeByte(200);
        dout.writeUTF("Mã quà \u0000 tặng");
        dout.writeLong(Long.MIN_VALUE + 5);
        dout.writeBoolean(true);
        dout.writeChar('Đ');
        byte[] data = bo.toByteArray();
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(data));
        p("data " + data.length + " " + din.readInt() + " " + din.readShort() + " " + din.readUnsignedByte() + " " + din.readUTF()
                .length() + " " + din.readLong() + " " + din.readBoolean() + " " + din.readChar() + " " + din.available());
        boolean eof = false;
        try {
            din.readByte();
        } catch (EOFException e) {
            eof = true;
        }
        p("eof " + eof);
        // ---- Calendar (TZ co dinh)
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(1700000000000L));
        p("cal " + c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DAY_OF_MONTH) + " " + c.get(Calendar.HOUR_OF_DAY)
                + ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND) + " dow" + c.get(Calendar.DAY_OF_WEEK) + " " + c.get(Calendar.AM_PM));
        c.setTime(new Date(-86400000L * 400));
        p("cal2 " + c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DATE) + " dow" + c.get(Calendar.DAY_OF_WEEK));
        p("misc " + Math.abs(-5) + Math.max(3, 9) + Math.min(-2, 4) + " " + Math.abs(Integer.MIN_VALUE) + " " + (Object) "s" + " "
                + String.valueOf((Object) null) + " " + String.valueOf(new char[]{'o', 'k'}) + " " + new Integer(5).equals(new Integer(5)));
        p("done");
    }
}
