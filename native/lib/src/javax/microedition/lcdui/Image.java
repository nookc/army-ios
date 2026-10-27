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
        Image c = cacheGet(r);
        if (c != null) {
            return c;
        }
        byte[] b = j2n.Res.read(r);
        if (b == null) {
            throw new IOException("khong co anh: " + name);
        }
        Image i = decode(b, 0, b.length);
        cachePut(r, i);
        return i;
    }

    // ------------------------------------------------------------------ v8h-31: giu anh lon da giai nen
    /*
     * Anh tai nguyen lon (boss Hon Don Thu 1006x1626, Bao Chua, T-Rex, sanh cho...) giai nen mat 15-150 ms tren dien thoai; game
     * bo anh khi doi phong / ra sanh roi nap lai -> khung hinh khung moi lan. Anh tai nguyen la anh bat bien (khong ve vao duoc)
     * nen dung chung 1 doi tuong an toan: giu anh tu CACHE_MIN diem tro len (toi da CACHE_MAX diem, bo anh lau khong dung nhat).
     * Luong nen giai nen san cac anh lon nhat ngay khi vao game (Boot) -> lan dau hien cung khong khung.
     */
    static final int CACHE_MIN = 120000;
    static final int CACHE_MAX = 9000000;
    private static final java.util.Hashtable cache = new java.util.Hashtable();
    private static final java.util.Vector cacheThuTu = new java.util.Vector();
    private static int cachePx;

    private static Image cacheGet(String r) {
        synchronized (cache) {
            Image i = (Image) cache.get(r);
            if (i != null) {
                cacheThuTu.removeElement(r);
                cacheThuTu.addElement(r);
            }
            return i;
        }
    }

    private static void cachePut(String r, Image i) {
        if (i == null || i.mutable) {
            return;
        }
        int px = i.w * i.h;
        if (px < CACHE_MIN || px > CACHE_MAX) {
            return;
        }
        synchronized (cache) {
            if (cache.containsKey(r)) {
                return;
            }
            while (cachePx + px > CACHE_MAX && cacheThuTu.size() > 0) {
                String cu = (String) cacheThuTu.elementAt(0);
                cacheThuTu.removeElementAt(0);
                Image x = (Image) cache.remove(cu);
                if (x != null) {
                    cachePx -= x.w * x.h;
                }
            }
            cache.put(r, i);
            cacheThuTu.addElement(r);
            cachePx += px;
        }
    }

    /** giai nen san (luong nen, Boot): anh nao khong co trong jar thi bo qua */
    public static void napSan(String[] ds) {
        for (int k = 0; k < ds.length; k++) {
            String r = ds[k];
            synchronized (cache) {
                if (cache.containsKey(r) || cachePx >= CACHE_MAX) {
                    continue;
                }
            }
            try {
                byte[] b = j2n.Res.read(r);
                if (b != null) {
                    cachePut(r, decode0(b, 0, b.length));
                }
            } catch (Throwable e) {
            }
        }
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
