package java.util;

public abstract class Calendar {
    public static final int YEAR = 1;
    public static final int MONTH = 2;
    public static final int DATE = 5;
    public static final int DAY_OF_MONTH = 5;
    public static final int DAY_OF_WEEK = 7;
    public static final int AM_PM = 9;
    public static final int HOUR = 10;
    public static final int HOUR_OF_DAY = 11;
    public static final int MINUTE = 12;
    public static final int SECOND = 13;
    public static final int MILLISECOND = 14;
    public static final int SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7;
    public static final int JANUARY = 0, FEBRUARY = 1, MARCH = 2, APRIL = 3, MAY = 4, JUNE = 5, JULY = 6, AUGUST = 7,
            SEPTEMBER = 8, OCTOBER = 9, NOVEMBER = 10, DECEMBER = 11;
    public static final int AM = 0, PM = 1;

    protected int[] fields = new int[15];
    protected boolean[] isSet = new boolean[15];
    protected long time;
    private TimeZone zone;

    protected Calendar() {
        zone = TimeZone.getDefault();
        time = System.currentTimeMillis();
        computeFields();
    }

    public static synchronized Calendar getInstance() {
        return new GregCal();
    }

    public static synchronized Calendar getInstance(TimeZone tz) {
        Calendar c = new GregCal();
        c.setTimeZone(tz);
        return c;
    }

    public final Date getTime() {
        return new Date(getTimeInMillis());
    }

    public final void setTime(Date d) {
        setTimeInMillis(d.getTime());
    }

    protected long getTimeInMillis() {
        return time;
    }

    protected void setTimeInMillis(long m) {
        time = m;
        computeFields();
    }

    public final int get(int f) {
        return fields[f];
    }

    public final void set(int f, int v) {
        fields[f] = v;
        isSet[f] = true;
        computeTime();
        computeFields();
    }

    public void setTimeZone(TimeZone tz) {
        zone = tz;
        computeFields();
    }

    public TimeZone getTimeZone() {
        return zone;
    }

    protected abstract void computeFields();

    protected abstract void computeTime();

    public boolean equals(Object o) {
        return o instanceof Calendar && ((Calendar) o).time == time;
    }

    public boolean before(Object o) {
        return o instanceof Calendar && time < ((Calendar) o).time;
    }

    public boolean after(Object o) {
        return o instanceof Calendar && time > ((Calendar) o).time;
    }

    public String toString() {
        return fields[YEAR] + "-" + (fields[MONTH] + 1) + "-" + fields[DATE] + " " + fields[HOUR_OF_DAY] + ":"
                + fields[MINUTE] + ":" + fields[SECOND];
    }
}

final class GregCal extends Calendar {
    protected void computeFields() {
        long t = time + getTimeZone().getRawOffset();
        long days = t >= 0 ? t / 86400000L : -((-t + 86399999L) / 86400000L);
        long ms = t - days * 86400000L;
        // civil from days (Howard Hinnant)
        long z = days + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp < 10 ? mp + 3 : mp - 9;
        if (m <= 2) {
            y++;
        }
        fields[YEAR] = (int) y;
        fields[MONTH] = (int) m - 1;
        fields[DATE] = (int) d;
        int dow = (int) ((days + 4) % 7);   // 1970-01-01 la thu 5
        if (dow < 0) {
            dow += 7;
        }
        fields[DAY_OF_WEEK] = dow + 1;
        int sec = (int) (ms / 1000);
        fields[MILLISECOND] = (int) (ms % 1000);
        fields[HOUR_OF_DAY] = sec / 3600;
        fields[MINUTE] = (sec / 60) % 60;
        fields[SECOND] = sec % 60;
        fields[AM_PM] = fields[HOUR_OF_DAY] < 12 ? AM : PM;
        fields[HOUR] = fields[HOUR_OF_DAY] % 12;
    }

    protected void computeTime() {
        long y = fields[YEAR];
        long m = fields[MONTH] + 1;
        long d = fields[DATE];
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long mp = m > 2 ? m - 3 : m + 9;
        long doy = (153 * mp + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        long days = era * 146097 + doe - 719468;
        int hod = isSet[HOUR] && !isSet[HOUR_OF_DAY] ? fields[HOUR] + (fields[AM_PM] == PM ? 12 : 0) : fields[HOUR_OF_DAY];
        long ms = days * 86400000L + hod * 3600000L + fields[MINUTE] * 60000L + fields[SECOND] * 1000L
                + fields[MILLISECOND];
        time = ms - getTimeZone().getRawOffset();
        for (int i = 0; i < isSet.length; i++) {
            isSet[i] = false;
        }
    }
}
