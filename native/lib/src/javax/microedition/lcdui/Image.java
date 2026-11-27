package javax.microedition.lcdui;

import java.io.IOException;
import java.io.InputStream;

public class Image {
    /* truong doc tu C - khong doi */
    int w, h;
    int[] argb;
    boolean mutable;
    int akind;       /* 0 dac, 1 chi trong suot hoan toan, 2 ban trong suot (C chon cach ve nhanh) */
    /* Avatar: anh net cao - w, h, argb la diem anh that, kich thuoc logic = w / hd (anh dt3/ ve tren man net cao) */
    int hd = 1;
    /* May HD (j2n.Disp.hdK() > 1): ban da phong to len he so cua dich ve - C tao lan dau anh duoc ve (gfx.c hd_build).
     * bq = he so da yeu cau, bk = he so that cua big (0 = chua co / khong lam duoc), bkind = kieu alpha cua big */
    int[] big;
    int bk, bq, bkind;
    /* May HD: 1 = anh "min" (ve 3D, chuyen mau lien tuc) -> phong to bicubic; 0 = pixel art -> xBRZ; -1 = tu nhan dang */
    int smooth = -1;
    /** Graphics.setStrokeStyle(0x4846) dat: anh createRGBImage ke tiep co he so nay (dung 1 lan) */
    static int hdLan;

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
        int k = j2n.Disp.hdK();
        if (k > 1 && (long) w * k * h * k <= 40000000L) {
            // May HD: bo dem ngoai man hinh cua game cung net cao - kich thuoc logic w x h, diem anh that gap k lan
            int[] q = new int[w * k * h * k];
            for (int i = 0; i < q.length; i++) {
                q[i] = 0xFFFFFFFF;
            }
            Image im = new Image(w * k, h * k, q, true);
            im.hd = k;
            return im;
        }
        return createRaw(w, h);
    }

    /** anh mutable dung so diem anh that w x h (he so 1) - bo dem trinh bay cua j2n.Ui */
    static Image createRaw(int w, int h) {
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
        i.hd = src.hd;
        i.smooth = src.smooth;
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
        if (r.startsWith("dt/") && r.endsWith(".png")) {
            // dau truong: co ban net cao x3 trong dt3/ thi dung (kich thuoc logic giu nguyen nhu anh dt/)
            String r3 = "dt3/" + r.substring(3);
            Image c3 = cacheGet(r3);
            if (c3 != null) {
                return c3;
            }
            byte[] b3 = j2n.Res.read(r3);
            if (b3 != null) {
                Image i3 = decode(b3, 0, b3.length);
                i3.hd = j2n.Ui.HD_MAN;
                i3.smooth = 1;
                cachePut(r3, i3);
                return i3;
            }
        }
        byte[] b = j2n.Res.read(r);
        if (b == null) {
            throw new IOException("khong co anh: " + name);
        }
        Image i = decode(b, 0, b.length);
        if (r.startsWith("fish/") || r.startsWith("fish1/") || r.startsWith("dt/")) {
            i.smooth = 1;       // ban ca / dau truong: anh ve 3D, khong phai pixel art
        }
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
        int d = heSoHd(data, off, len);
        if (d > 1 && i.w % d == 0 && i.h % d == 0) {
            i.hd = d;           // anh HD cua may chu (x2): kich thuoc logic = diem anh / d, ve net o man net cao
        }
        return i;
    }

    /**
     * Avatar HD: may chu danh dau anh HD (ban x2 dung gap doi ban thuong) bang chunk rieng "apHd" (1 byte he so) ngay sau
     * IHDR. Tra ve he so (2..4), 0 neu khong co. Chi doc cac chunk truoc IDAT.
     */
    static int heSoHd(byte[] b, int off, int len) {
        if (len < 33 || (b[off] & 0xFF) != 0x89 || b[off + 1] != 'P' || b[off + 2] != 'N' || b[off + 3] != 'G') {
            return 0;
        }
        int p = off + 8, het = off + len;
        for (int k = 0; k < 8 && p + 8 <= het; k++) {
            int n = ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
            if (n < 0 || p + 12 + n > het) {
                return 0;
            }
            if (b[p + 4] == 'a' && b[p + 5] == 'p' && b[p + 6] == 'H' && b[p + 7] == 'd' && n >= 1) {
                int d = b[p + 8] & 0xFF;
                return d >= 2 && d <= 4 ? d : 0;
            }
            if (b[p + 4] == 'I' && b[p + 5] == 'D' && b[p + 6] == 'A' && b[p + 7] == 'T') {
                return 0;
            }
            p += 12 + n;
        }
        return 0;
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
        if (rgbMang != null) {
            // May HD: game cat anh con bang getRGB -> createRGBImage. Dung mang cua lan getRGB cuoi, noi dung chua doi ->
            // cat thang tu anh nguon de giu he so net cao (khong tut ve diem anh logic)
            Image tu = null;
            int cx = 0, cy = 0;
            synchronized (rgbKhoa) {
                if (rgb == rgbMang && processAlpha && w == rgbW && h == rgbH && rgbAnh != null && hdLan <= 1
                        && tong(rgb, w * h) == rgbTong) {
                    tu = rgbAnh;
                    cx = rgbX;
                    cy = rgbY;
                }
                rgbMang = null;
                rgbAnh = null;
            }
            if (tu != null) {
                return createImage(tu, cx, cy, w, h, 0);
            }
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
        if (hdLan > 1) {
            if (w % hdLan == 0 && h % hdLan == 0) {
                im.hd = hdLan;
            }
            hdLan = 0;
        }
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
        return hd > 1 ? w / hd : w;
    }

    public int getHeight() {
        return hd > 1 ? h / hd : h;
    }

    public boolean isMutable() {
        return mutable;
    }

    public void getRGB(int[] out, int off, int scan, int x, int y, int ww, int hh) {
        if (x < 0 || y < 0 || ww < 0 || hh < 0 || x + ww > getWidth() || y + hh > getHeight()) {
            throw new IllegalArgumentException();
        }
        if (Math.abs(scan) < ww) {
            throw new IllegalArgumentException();
        }
        if (hd > 1) {
            // anh net cao: lay mau theo luoi logic
            for (int j = 0; j < hh; j++) {
                int s = (y + j) * hd * w + x * hd;
                int d = off + j * scan;
                for (int i = 0; i < ww; i++) {
                    out[d + i] = argb[s + i * hd];
                }
            }
        } else {
            for (int j = 0; j < hh; j++) {
                int s = (y + j) * w + x;
                int d = off + j * scan;
                System.arraycopy(argb, s, out, d, ww);
            }
        }
        if (off == 0 && scan == ww && ww > 0 && hh > 0 && j2n.Disp.hdK() > 1) {
            synchronized (rgbKhoa) {
                rgbMang = out;
                rgbAnh = this;
                rgbX = x;
                rgbY = y;
                rgbW = ww;
                rgbH = hh;
                rgbTong = tong(out, ww * hh);
            }
        }
    }

    /* May HD: lan getRGB cuoi (xem createRGBImage) */
    private static final Object rgbKhoa = new Object();
    private static int[] rgbMang;
    private static Image rgbAnh;
    private static int rgbX, rgbY, rgbW, rgbH, rgbTong;

    private static int tong(int[] a, int n) {
        int t = 17;
        for (int i = 0; i < n; i++) {
            t = t * 31 + a[i];
        }
        return t;
    }
}
