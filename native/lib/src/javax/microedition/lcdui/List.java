package javax.microedition.lcdui;

/** Avatar: client chi tham chieu List.SELECT_COMMAND va getSelectedIndex() (khong tao List). */
public class List extends Screen {
    public static final int EXCLUSIVE = 1;
    public static final int MULTIPLE = 2;
    public static final int IMPLICIT = 3;
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);

    public List(String title, int type) {
        this.title = title;
    }

    public int getSelectedIndex() {
        return -1;
    }

    public int size() {
        return 0;
    }
}
