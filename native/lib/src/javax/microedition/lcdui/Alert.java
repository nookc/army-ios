package javax.microedition.lcdui;

public class Alert extends Screen {
    public static final Command DISMISS_COMMAND = new Command("Done", Command.OK, 0);
    public static final int FOREVER = -2;
    String text;

    public Alert(String title) {
        this.title = title;
    }

    public Alert(String title, String text, Image img, AlertType type) {
        this.title = title;
        this.text = text;
    }

    public String getString() {
        return text;
    }

    public void setString(String s) {
        text = s;
    }

    public void setTimeout(int t) {
    }

    public int getTimeout() {
        return FOREVER;
    }
}
