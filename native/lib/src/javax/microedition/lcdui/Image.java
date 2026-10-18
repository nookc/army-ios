package javax.microedition.lcdui;

import java.io.IOException;
import java.io.InputStream;

public class Image {
    /* truong doc tu C - khong doi */
    int w, h;
    int[] argb;
    boolean mutable;
    int akind;       /* 0 dac, 1 chi trong suot hoan toan, 2 ban trong suot (C chon cach ve nhanh) */

    Image(int w, int h, int[] argb, boolean mutable) {
        this.w = w;
        this.h = h;
        this.argb = argb;
        this.mutable = mutable;
    }

    public static Image createImage(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException();
        }
        int[] p = new int[w * h];
        for (int i = 0; i < p.length; i++) {
            p[i] = 0xFFFFFFFF;
        }
        return new Image(w, h, p, true);
    }

    public static Image createImage(Image src) {
        if (!src.mutable) {
            return src;
        }
        int[] p = new int[src.argb.length];
        System.arraycopy(src.argb, 0, p, 0, p.length);
        Image i = new Image(src.w, src.h, p, false);
        i.akind = src.akind;
        return i;
    }

    public static Image createImage(String name) throws IOException {
        if (name == null) {
            throw new NullPointerException();
        }
        String r = name.startsWith("/") ? name.substring(1) : name;
        byte[] b = j2n.Res.read(r);
        if (b == null) {
            throw new IOException("khong co anh: " + name);
        }
        return decode(b, 0, b.length);
    }

    public static Image createImage(byte[] data, int off, int len) {
        if (off < 0 || len < 0 || off + len > data.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return decode(data, off, len);
    }

    public static Image createImage(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf, 0, buf.length)) > 0) {
            bo.write(buf, 0, n);
        }
        byte[] b = bo.toByteArray();
        Image i = decode(b, 0, b.length);
        if (i == null) {
            throw new IOException("anh hong");
        }
        return i;
    }

    private static native Image decode0(byte[] data, int off, int len);

    private static Image decode(byte[] data, int off, int len) {
        Image i = decode0(data, off, len);
        if (i == null) {
            throw new IllegalArgumentException("anh hong");
        }
        return i;
    }

    /* C tao doi tuong qua ham nay */
    static Image make(int w, int h, int[] argb, int akind) {
        Image i = new Image(w, h, argb, false);
        i.akind = akind;
        return i;
    }

    public static Image createRGBImage(int[] rgb, int w, int h, boolean processAlpha) {
        if (rgb == null) {
            throw new NullPointerException();
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException();
        }
        if (rgb.length < w * h) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int[] p = new int[w * h];
        int k = 0;
        if (processAlpha) {
            for (int i = 0; i < p.length; i++) {
                int v = rgb[i];
                p[i] = v;
                int a = v >>> 24;
                if (a != 0xFF) {
                    if (a != 0) {
                        k = 2;
                    } else if (k == 0) {
                        k = 1;
                    }
                }
            }
        } else {
            for (int i = 0; i < p.length; i++) {
                p[i] = rgb[i] | 0xFF000000;
            }
        }
        Image im = new Image(w, h, p, false);
        im.akind = k;
        return im;
    }

    public static native Image createImage(Image src, int x, int y, int w, int h, int transform);

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException();
        }
        return new Graphics(this);
    }

    public int getWidth() {
        return w;
    }

    public int getHeight() {
        return h;
    }

    public boolean isMutable() {
        return mutable;
    }

    public void getRGB(int[] out, int off, int scan, int x, int y, int ww, int hh) {
        if (x < 0 || y < 0 || ww < 0 || hh < 0 || x + ww > w || y + hh > h) {
            throw new IllegalArgumentException();
        }
        if (Math.abs(scan) < ww) {
            throw new IllegalArgumentException();
        }
        for (int j = 0; j < hh; j++) {
            int s = (y + j) * w + x;
            int d = off + j * scan;
            System.arraycopy(argb, s, out, d, ww);
        }
    }
}
