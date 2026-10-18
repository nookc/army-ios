package java.util;

public class Date {
    private long millis;

    public Date() {
        this(System.currentTimeMillis());
    }

    public Date(long d) {
        millis = d;
    }

    public long getTime() {
        return millis;
    }

    public void setTime(long t) {
        millis = t;
    }

    public boolean equals(Object o) {
        return o instanceof Date && ((Date) o).millis == millis;
    }

    public int hashCode() {
        return (int) millis ^ (int) (millis >> 32);
    }

    public String toString() {
        Calendar c = Calendar.getInstance();
        c.setTime(this);
        return c.toString();
    }
}
