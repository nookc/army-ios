package javax.microedition.lcdui;

import java.util.Vector;

public abstract class Displayable {
    Vector commands = new Vector();
    CommandListener listener;
    String title;
    boolean shown;

    Displayable() {
    }

    public void addCommand(Command c) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (!commands.contains(c)) {
            commands.addElement(c);
        }
    }

    public void removeCommand(Command c) {
        commands.removeElement(c);
    }

    public void setCommandListener(CommandListener l) {
        listener = l;
    }

    public boolean isShown() {
        return shown;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String s) {
        title = s;
    }

    public int getWidth() {
        return j2n.Disp.width();
    }

    public int getHeight() {
        return j2n.Disp.height();
    }

    protected void sizeChanged(int w, int h) {
    }

    /* goi tren luong su kien */
    void showNotify0() {
    }

    void hideNotify0() {
    }
}
