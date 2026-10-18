package java.util;

public abstract class TimeZone {
    private static TimeZone def;

    public TimeZone() {
    }

    public abstract int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis);

    public abstract int getRawOffset();

    public abstract boolean useDaylightTime();

    public String getID() {
        return "GMT";
    }

    public static synchronized TimeZone getDefault() {
        if (def == null) {
            def = new SimpleTz(j2n.Sys.tzOffset(), j2n.Sys.tzId());
        }
        return def;
    }

    public static TimeZone getTimeZone(String id) {
        if (id == null) {
            return getDefault();
        }
        if (id.equals("GMT") || id.equals("UTC")) {
            return new SimpleTz(0, id);
        }
        if (id.startsWith("GMT") && id.length() > 4) {
            int sign = id.charAt(3) == '-' ? -1 : 1;
            String r = id.substring(4);
            int h, m = 0;
            int c = r.indexOf(':');
            try {
                if (c >= 0) {
                    h = Integer.parseInt(r.substring(0, c));
                    m = Integer.parseInt(r.substring(c + 1));
                } else {
                    h = Integer.parseInt(r);
                }
                return new SimpleTz(sign * (h * 3600000 + m * 60000), id);
            } catch (NumberFormatException e) {
            }
        }
        return getDefault();
    }

    public static String[] getAvailableIDs() {
        return new String[]{"GMT", getDefault().getID()};
    }
}

final class SimpleTz extends TimeZone {
    private final int off;
    private final String id;

    SimpleTz(int off, String id) {
        this.off = off;
        this.id = id;
    }

    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis) {
        return off;
    }

    public int getRawOffset() {
        return off;
    }

    public boolean useDaylightTime() {
        return false;
    }

    public String getID() {
        return id;
    }
}
