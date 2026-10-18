package javax.microedition.lcdui;

public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;
    public static final int SOLID = 0;
    public static final int DOTTED = 1;

    /* truong doc tu C (natives_lcdui.c) - khong doi ten / kieu */
    int tx, ty;
    int cx, cy, cw, ch;      /* vung cat, toa do tuyet doi */
    int color;               /* 0xRRGGBB */
    int stroke;
    int tw, th;              /* kich thuoc dich */
    Image img;               /* null = man hinh */
    Font font;

    Graphics(Image target) {
        img = target;
        if (target != null) {
            reset(target.w, target.h);
        } else {
            reset(j2n.Disp.width(), j2n.Disp.height());
        }
    }

    void reset(int w, int h) {
        tw = w;
        th = h;
        tx = 0;
        ty = 0;
        cx = 0;
        cy = 0;
        cw = w;
        ch = h;
        color = 0;
        stroke = SOLID;
        font = Font.getDefaultFont();
    }

    public void translate(int x, int y) {
        tx += x;
        ty += y;
    }

    public int getTranslateX() {
        return tx;
    }

    public int getTranslateY() {
        return ty;
    }

    public int getColor() {
        return color;
    }

    public int getRedComponent() {
        return (color >> 16) & 0xff;
    }

    public int getGreenComponent() {
        return (color >> 8) & 0xff;
    }

    public int getBlueComponent() {
        return color & 0xff;
    }

    public int getGrayScale() {
        return (getRedComponent() * 76 + getGreenComponent() * 150 + getBlueComponent() * 29) >> 8;
    }

    public void setColor(int r, int g, int b) {
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException();
        }
        color = (r << 16) | (g << 8) | b;
    }

    public void setColor(int rgb) {
        color = rgb & 0xFFFFFF;
    }

    public void setGrayScale(int v) {
        if (v < 0 || v > 255) {
            throw new IllegalArgumentException();
        }
        color = (v << 16) | (v << 8) | v;
    }

    public Font getFont() {
        return font;
    }

    public void setStrokeStyle(int s) {
        if (s != SOLID && s != DOTTED) {
            throw new IllegalArgumentException();
        }
        stroke = s;
    }

    public int getStrokeStyle() {
        return stroke;
    }

    public void setFont(Font f) {
        font = f == null ? Font.getDefaultFont() : f;
    }

    public int getClipX() {
        return cx - tx;
    }

    public int getClipY() {
        return cy - ty;
    }

    public int getClipWidth() {
        return cw;
    }

    public int getClipHeight() {
        return ch;
    }

    public void clipRect(int x, int y, int w, int h) {
        int x1 = x + tx, y1 = y + ty, x2 = x1 + w, y2 = y1 + h;
        int ox2 = cx + cw, oy2 = cy + ch;
        if (x1 < cx) {
            x1 = cx;
        }
        if (y1 < cy) {
            y1 = cy;
        }
        if (x2 > ox2) {
            x2 = ox2;
        }
        if (y2 > oy2) {
            y2 = oy2;
        }
        cx = x1;
        cy = y1;
        cw = x2 > x1 ? x2 - x1 : 0;
        ch = y2 > y1 ? y2 - y1 : 0;
    }

    public void setClip(int x, int y, int w, int h) {
        int x1 = x + tx, y1 = y + ty, x2 = x1 + w, y2 = y1 + h;
        if (x1 < 0) {
            x1 = 0;
        }
        if (y1 < 0) {
            y1 = 0;
        }
        if (x2 > tw) {
            x2 = tw;
        }
        if (y2 > th) {
            y2 = th;
        }
        cx = x1;
        cy = y1;
        cw = x2 > x1 ? x2 - x1 : 0;
        ch = y2 > y1 ? y2 - y1 : 0;
    }

    public native void drawLine(int x1, int y1, int x2, int y2);

    public native void fillRect(int x, int y, int w, int h);

    public native void drawRect(int x, int y, int w, int h);

    public native void drawRoundRect(int x, int y, int w, int h, int aw, int ah);

    public native void fillRoundRect(int x, int y, int w, int h, int aw, int ah);

    public native void fillArc(int x, int y, int w, int h, int start, int arc);

    public native void drawArc(int x, int y, int w, int h, int start, int arc);

    public native void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3);

    private native void drawText(String s, int x, int y, int anchor);

    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) {
            throw new NullPointerException();
        }
        drawText(s, x, y, anchor);
    }

    public void drawSubstring(String s, int off, int len, int x, int y, int anchor) {
        drawString(s.substring(off, off + len), x, y, anchor);
    }

    public void drawChar(char c, int x, int y, int anchor) {
        drawString(String.valueOf(c), x, y, anchor);
    }

    public void drawChars(char[] c, int off, int len, int x, int y, int anchor) {
        drawString(new String(c, off, len), x, y, anchor);
    }

    private native void drawImage0(Image img, int x, int y, int anchor);

    public void drawImage(Image i, int x, int y, int anchor) {
        if (i == null) {
            throw new NullPointerException();
        }
        drawImage0(i, x, y, anchor);
    }

    private native void drawRegion0(Image src, int xs, int ys, int w, int h, int transform, int xd, int yd, int anchor);

    public void drawRegion(Image src, int xs, int ys, int w, int h, int transform, int xd, int yd, int anchor) {
        if (src == null) {
            throw new NullPointerException();
        }
        if (xs < 0 || ys < 0 || w < 0 || h < 0 || xs + w > src.w || ys + h > src.h) {
            throw new IllegalArgumentException("drawRegion");
        }
        if (transform < 0 || transform > 7) {
            throw new IllegalArgumentException("transform");
        }
        drawRegion0(src, xs, ys, w, h, transform, xd, yd, anchor);
    }

    public native void drawRGB(int[] rgb, int off, int scan, int x, int y, int w, int h, boolean alpha);

    public native void copyArea(int xs, int ys, int w, int h, int xd, int yd, int anchor);

    public int getDisplayColor(int c) {
        return c & 0xFFFFFF;
    }
}
