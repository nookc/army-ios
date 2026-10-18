package java.lang;

public final class Character {
    public static final int MIN_RADIX = 2;
    public static final int MAX_RADIX = 36;
    public static final char MIN_VALUE = '\u0000';
    public static final char MAX_VALUE = '￿';
    private final char value;

    public Character(char v) {
        value = v;
    }

    public char charValue() {
        return value;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public int hashCode() {
        return value;
    }

    public boolean equals(Object o) {
        return o instanceof Character && ((Character) o).value == value;
    }

    public static boolean isLowerCase(char c) {
        return toUpperCase(c) != c || (c >= 'a' && c <= 'z') || c == 'ß';
    }

    public static boolean isUpperCase(char c) {
        return toLowerCase(c) != c || (c >= 'A' && c <= 'Z');
    }

    public static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= 0xC0 && c != 0xD7 && c != 0xF7);
    }

    public static boolean isLetterOrDigit(char c) {
        return isLetter(c) || isDigit(c);
    }

    public static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    public static boolean isWhitespace(char c) {
        return isSpace(c) || c == 0x0B || (c >= 0x1C && c <= 0x1F);
    }

    /** Latin-1 + Latin Extended-A/B + tieng Viet (Latin Extended Additional 1E00-1EFF) + Hy Lap / Kirin co ban */
    public static char toLowerCase(char c) {
        if (c < 0x80) {
            return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
        }
        if (c >= 0xC0 && c <= 0xDE && c != 0xD7) {
            return (char) (c + 32);
        }
        if ((c >= 0x100 && c <= 0x137) || (c >= 0x14A && c <= 0x177) || (c >= 0x1E00 && c <= 0x1E95)
                || (c >= 0x1EA0 && c <= 0x1EFF)) {
            return (c & 1) == 0 ? (char) (c + 1) : c;
        }
        if ((c >= 0x139 && c <= 0x148) || (c >= 0x179 && c <= 0x17E)) {
            return (c & 1) == 1 ? (char) (c + 1) : c;
        }
        if (c == 0x178) {
            return 0xFF;
        }
        if (c == 0x1A0 || c == 0x1AF) {
            return (char) (c + 1);   /* O' U' tieng Viet */
        }
        if (c == 0x110) {
            return 0x111;            /* D- */
        }
        if (c >= 0x391 && c <= 0x3AB && c != 0x3A2) {
            return (char) (c + 32);
        }
        if (c >= 0x410 && c <= 0x42F) {
            return (char) (c + 32);
        }
        if (c >= 0x400 && c <= 0x40F) {
            return (char) (c + 80);
        }
        return c;
    }

    public static char toUpperCase(char c) {
        if (c < 0x80) {
            return (c >= 'a' && c <= 'z') ? (char) (c - 32) : c;
        }
        if (c >= 0xE0 && c <= 0xFE && c != 0xF7) {
            return (char) (c - 32);
        }
        if (c == 0xFF) {
            return 0x178;
        }
        if ((c >= 0x100 && c <= 0x137) || (c >= 0x14A && c <= 0x177) || (c >= 0x1E00 && c <= 0x1E95)
                || (c >= 0x1EA0 && c <= 0x1EFF)) {
            return (c & 1) == 1 ? (char) (c - 1) : c;
        }
        if ((c >= 0x139 && c <= 0x148) || (c >= 0x179 && c <= 0x17E)) {
            return (c & 1) == 0 ? (char) (c - 1) : c;
        }
        if (c == 0x1A1 || c == 0x1B0) {
            return (char) (c - 1);
        }
        if (c == 0x111) {
            return 0x110;
        }
        if (c >= 0x3B1 && c <= 0x3CB && c != 0x3C2) {
            return (char) (c - 32);
        }
        if (c >= 0x430 && c <= 0x44F) {
            return (char) (c - 32);
        }
        if (c >= 0x450 && c <= 0x45F) {
            return (char) (c - 80);
        }
        return c;
    }

    public static int digit(char c, int radix) {
        int d;
        if (c >= '0' && c <= '9') {
            d = c - '0';
        } else if (c >= 'a' && c <= 'z') {
            d = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'Z') {
            d = c - 'A' + 10;
        } else {
            return -1;
        }
        return d < radix ? d : -1;
    }

    public static char forDigit(int d, int radix) {
        if (d < 0 || d >= radix || radix < 2 || radix > 36) {
            return 0;
        }
        return (char) (d < 10 ? '0' + d : 'a' + d - 10);
    }
}
