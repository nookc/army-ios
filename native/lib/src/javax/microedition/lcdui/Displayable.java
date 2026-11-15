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

    /* Avatar: so lan phong to man ve cua Canvas nay (ban ban ca ve gap doi cho net); 0 = chua xet */
    int j2nScale;

    final int scale() {
        int s = j2nScale;
        if (s == 0) {
            s = j2n.Ui.scaleFor(this);
            j2nScale = s;
        }
        return s;
    }

    public int getWidth() {
        return j2n.Disp.width() * scale();
    }

    public int getHeight() {
        return j2n.Disp.height() * scale();
    }

    protected void sizeChanged(int w, int h) {
    }

    /* goi tren luong su kien */
    void showNotify0() {
    }

    void hideNotify0() {
    }
}
