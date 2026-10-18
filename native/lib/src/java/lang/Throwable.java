package java.lang;

public class Throwable {
    String detailMessage;

    public Throwable() {
    }

    public Throwable(String message) {
        detailMessage = message;
    }

    public String getMessage() {
        return detailMessage;
    }

    public String toString() {
        String n = getClass().getName();
        return detailMessage != null ? n + ": " + detailMessage : n;
    }

    public void printStackTrace() {
        System.err.println(toString());
    }
}
