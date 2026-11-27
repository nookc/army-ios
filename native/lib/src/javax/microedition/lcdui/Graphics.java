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
    /* Avatar: so diem anh that cho 1 diem logic (man ve net cao x3 o dau truong; con lai 1). Game van ve theo toa do logic */
    int k = 1;
    /* Avatar: ve hinh (cung, chu nhat bo goc, duong thang) o diem anh that tren man net cao -> min, khong rang cua.
     * Lop phu dau truong bat bang setStrokeStyle(0x4844) / tat 0x4845 (may J2ME that nem loi -> lop phu bo qua). */
    boolean hdp;
    /** May HD: kieu ve giao dien 3D cho lan fillRoundRect ke tiep (C doc roi xoa) */
    int hdKieu;
    /** May HD: kieu chuyen dong song cho cac lan ve anh ke tiep (C doc) */
    int hdLac;
    /** May HD: duong chan cua vat dang ve (Integer.MIN_VALUE = khong co) - C doc de do bong */
    int hdChan = Integer.MIN_VALUE;
    /** May HD: 0 = vat the thuong; 0x100 | pha = manh cua nhan vat (khong ap nguong kich thuoc bong; pha nhip tho) */
    int hdChanK;

    Graphics(Image target) {
        img = target;
        if (target != null) {
            if (target.hd > 1) {
                // May HD: anh mutable net cao - game ve theo toa do logic, moi diem logic = hd diem anh that
                reset(target.getWidth(), target.getHeight());
                k = target.hd;
            } else {
                reset(target.w, target.h);
            }
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
        hdp = j2n.Disp.hdK() > 1;       // May HD: hinh ve luon min o diem anh that
        hdLac = 0;
        hdKieu = 0;
        hdChan = Integer.MIN_VALUE;
        hdChanK = 0;
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
        if (s == 0x4844 || s == 0x4845) {
            hdp = s == 0x4844 || j2n.Disp.hdK() > 1;
            return;
        }
        if (s >= 0x4880 && s <= 0x4883) {
            // May HD: cac lan ve anh ke tiep co "chuyen dong song" (1 vay canh, 2 thu cung nhun, 3 hao quang), 0x4880 = thoi
            hdLac = s - 0x4880;
            return;
        }
        if (s >= 0x4861 && s <= 0x4868) {
            // May HD: lan fillRoundRect ke tiep ve theo kieu giao dien 3D (1 bong do khung, 2 lop bong sang, 3 nut kinh,
            // 4 nut mau vien trang)
            hdKieu = s - 0x4860;
            return;
        }
        if ((s >>> 24) == 0x4A || (s >>> 24) == 0x4B) {
            // May HD: "duong chan" (toa do y nhu khi ve) cua vat sap ve -> may ve do bong xuong mat dat duoi duong nay.
            // 0x4A00yyyy = vat the; 0x4Bppyyyy = 1 manh cua nhan vat (pp = pha rieng cua nhan vat do: nhip tho)
            hdChan = (short) s;
            hdChanK = (s >>> 24) == 0x4B ? (0x100 | ((s >> 16) & 0xFF)) : 0;
            return;
        }
        if (s == 0x4854) {
            hdChan = Integer.MIN_VALUE;         // het vat: thoi do bong
            hdChanK = 0;
            return;
        }
        if (s >= 0x4850 && s <= 0x4853) {
            hdChan = Integer.MIN_VALUE;
            hdChanK = 0;
            // May HD: client bao ve nen (0x4852) / vat the dong cua the gioi (0x4850) / vat the tinh cua ban do (0x4853) /
            // ket thuc the gioi (0x4851) - HdUi
            hdCanh(s == 0x4850 ? 1 : s == 0x4851 ? 2 : s == 0x4852 ? 3 : 4);
            return;
        }
        if ((s >>> 24) == 0x4D) {
            // May HD: le trong 2 ben cua ban do hep hon man hinh (trai << 12 | phai) - may ve lat guong mep ban do ra le
            hdCanh(s);
            return;
        }
        if ((s >>> 24) == 0x4C) {
            // May HD: nguon sang ban dem tai chan vat vua ve (cot den, den long, nen...) - hd3d.c fx_den
            hdCanh(s);
            return;
        }
        if (s >= 0x48A0 && s <= 0x48A7) {
            // May HD: co cua canh sap ve (1 ngoai troi, 2 co su song mat dat, 4 ban dem) - xem hd3d.c gfx_canh_co
            hdCanh(32 + (s - 0x48A0));
            return;
        }
        if (s >= 0x4870 && s <= 0x4877) {
            // May HD: chu de may chu bao (0 khong, 1 Giang sinh, 2 Tinh nhan, 3 Vui nhon, 4 Halloween)
            hdCanh(16 + (s - 0x4870));
            return;
        }
        if (s == 0x4846) {
            // Avatar: anh createRGBImage ke tiep la anh net cao (w, h = diem anh that) - boss dau truong phong to luc chay
            Image.hdLan = j2n.Ui.HD_MAN;
            return;
        }
        if (s != SOLID && s != DOTTED) {
            throw new IllegalArgumentException();
        }
        stroke = s;
    }

    private native void hdCanh(int pha);

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
        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("drawRegion");
        }
        if (transform < 0 || transform > 7) {
            throw new IllegalArgumentException("transform");
        }
        int sw = src.getWidth(), sh = src.getHeight();
        if (xs < 0 || ys < 0 || xs + w > sw || ys + h > sh) {
            // May that (Nokia...) cat phan vung nam ngoai anh thay vi nem loi; client Avatar co man (tao nhan vat...) dua
            // vung lo ra -> truoc day ca khung ve bi huy (hinh thieu, nhap nhay). Cat vung, giu dung vi tri phan con lai.
            if (w == 0 || h == 0) {
                return;
            }
            boolean swap = transform >= 4; // ROT90 / ROT270 / MIRROR_ROT90 / MIRROR_ROT270: rong <-> cao
            int dw = swap ? h : w, dh = swap ? w : h;
            int x = xd, y = yd;
            if ((anchor & HCENTER) != 0) {
                x -= dw / 2;
            } else if ((anchor & RIGHT) != 0) {
                x -= dw;
            }
            if ((anchor & VCENTER) != 0) {
                y -= dh / 2;
            } else if ((anchor & BOTTOM) != 0) {
                y -= dh;
            }
            int x0 = xs < 0 ? 0 : xs, y0 = ys < 0 ? 0 : ys;
            int x1 = xs + w > sw ? sw : xs + w, y1 = ys + h > sh ? sh : ys + h;
            if (x1 <= x0 || y1 <= y0) {
                return;
            }
            if (transform == 0) {
                x += x0 - xs;
                y += y0 - ys;
            }
            drawRegion0(src, x0, y0, x1 - x0, y1 - y0, transform, x, y, TOP | LEFT);
            return;
        }
        drawRegion0(src, xs, ys, w, h, transform, xd, yd, anchor);
    }

    public native void drawRGB(int[] rgb, int off, int scan, int x, int y, int w, int h, boolean alpha);

    public native void copyArea(int xs, int ys, int w, int h, int xd, int yd, int anchor);

    public int getDisplayColor(int c) {
        return c & 0xFFFFFF;
    }
}
