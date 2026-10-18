package javax.microedition.lcdui;

public final class Font {
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;
    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;
    public static final int FONT_STATIC_TEXT = 0;
    public static final int FONT_INPUT_TEXT = 1;
    private static Font def;

    /* doc tu C */
    int face, style, size;
    int px;           /* chieu cao co chu (pixel) */

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        this.px = size == SIZE_SMALL ? 0 : size == SIZE_LARGE ? 2 : 1;
    }

    public static synchronized Font getDefaultFont() {
        if (def == null) {
            def = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);
        }
        return def;
    }

    public static Font getFont(int fontSpecifier) {
        return getDefaultFont();
    }

    public static Font getFont(int face, int style, int size) {
        if (face == FACE_SYSTEM && style == STYLE_PLAIN && size == SIZE_MEDIUM) {
            return getDefaultFont();
        }
        return new Font(face, style, size);
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public int getFace() {
        return face;
    }

    public boolean isPlain() {
        return style == 0;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public native int getHeight();

    public native int getBaselinePosition();

    public native int charWidth(char c);

    public int charsWidth(char[] c, int off, int len) {
        return stringWidth(new String(c, off, len));
    }

    public native int stringWidth(String s);

    public int substringWidth(String s, int off, int len) {
        return stringWidth(s.substring(off, off + len));
    }
}
