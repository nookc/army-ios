package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    public static final int LIST_ELEMENT = 1;
    public static final int CHOICE_GROUP_ELEMENT = 2;
    public static final int ALERT = 3;
    private static Display display;

    private Display() {
    }

    public static synchronized Display getDisplay(MIDlet m) {
        if (display == null) {
            display = new Display();
        }
        return display;
    }

    public Displayable getCurrent() {
        return j2n.Ui.current();
    }

    public void setCurrent(Displayable d) {
        if (d != null) {
            j2n.Ui.setCurrent(d);
        }
    }

    public void setCurrent(Alert a, Displayable next) {
        setCurrent(next);
    }

    public void callSerially(Runnable r) {
        j2n.Ui.callSerially(r);
    }

    public boolean vibrate(int ms) {
        return j2n.Disp.vibrate(ms);
    }

    public boolean flashBacklight(int ms) {
        return false;
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 1 << 24;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public int getBestImageWidth(int t) {
        return 0;
    }

    public int getBestImageHeight(int t) {
        return 0;
    }

    /* cau noi cho j2n.Ui (cung goi javax.microedition.lcdui) */
    public static void paintCanvas(Displayable d, Graphics g) {
        ((Canvas) d).doPaint(g);
    }

    public static void keyCanvas(Displayable d, int type, int code) {
        ((Canvas) d).doKey(type, code);
    }

    public static void pointerCanvas(Displayable d, int type, int x, int y) {
        ((Canvas) d).doPointer(type, x, y);
    }

    public static void sizeCanvas(Displayable d, int w, int h) {
        ((Canvas) d).doSize(w, h);
    }

    public static void notifyShown(Displayable d, boolean shown) {
        d.shown = shown;
        if (shown) {
            d.showNotify0();
        } else {
            d.hideNotify0();
        }
    }

    public static boolean isCanvas(Displayable d) {
        return d instanceof Canvas;
    }

    public static CommandListener listenerOf(Displayable d) {
        return d.listener;
    }

    public static java.util.Vector commandsOf(Displayable d) {
        return d.commands;
    }

    public static void setText(TextBox t, String s) {
        t.setString(s);
    }

    public static int maxSizeOf(TextBox t) {
        return t.maxSize;
    }

    public static int constraintsOf(TextBox t) {
        return t.constraints;
    }

    public static Graphics screenGraphics() {
        return new Graphics(null);
    }

    public static void resetGraphics(Graphics g, int w, int h) {
        g.reset(w, h);
    }
}
