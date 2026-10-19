package j2n;

import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.TextBox;

/** luong su kien MIDP: phim, cham, ve, hop nhap chu (moi callback cua game chay tuan tu tren luong nay) */
public final class Ui {
    private static Displayable cur;
    private static Displayable shownNotified;
    private static Thread evThread;
    private static Graphics screen;
    private static final Object paintLock = new Object();
    private static int paintedSeq;
    private static final Vector serial = new Vector();
    private static TextBox textBox;

    private Ui() {
    }

    public static Displayable current() {
        return cur;
    }

    public static void setCurrent(Displayable d) {
        cur = d;
        if (d instanceof TextBox) {
            TextBox t = (TextBox) d;
            textBox = t;
            Disp.showTextBox(t.getTitle(), t.getString(), Display.maxSizeOf(t), Display.constraintsOf(t));
        } else {
            Disp.requestRepaint();
        }
        Disp.postEvent(Disp.EV_SERIAL, 0, 0);
    }

    public static void repaint(Displayable c) {
        if (c == cur) {
            Disp.requestRepaint();
        }
    }

    public static void serviceRepaints(Displayable c) {
        if (c != cur) {
            return;
        }
        if (Thread.currentThread() == evThread) {
            if (Disp.repaintPending()) {
                paintNow();
            }
            return;
        }
        synchronized (paintLock) {
            if (!Disp.repaintPending()) {
                return;
            }
            int target = paintedSeq + 1;
            long end = System.currentTimeMillis() + 250;
            while (paintedSeq < target && Disp.repaintPending()) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    break;
                }
                try {
                    paintLock.wait(left < 50 ? left : 50);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public static void callSerially(Runnable r) {
        synchronized (serial) {
            serial.addElement(r);
        }
        Disp.postEvent(Disp.EV_SERIAL, 0, 0);
    }

    private static void paintNow() {
        Disp.clearRepaint();
        Displayable d = cur;
        if (d != null && Display.isCanvas(d)) {
            if (screen == null) {
                screen = Display.screenGraphics();
            }
            Display.resetGraphics(screen, Disp.width(), Disp.height());
            try {
                Display.paintCanvas(d, screen);
            } catch (Throwable e) {
                System.err.println("[Ui] loi paint: " + e);
            }
            Disp.present();
        }
        synchronized (paintLock) {
            paintedSeq++;
            paintLock.notifyAll();
        }
    }

    private static void notifyShown() {
        Displayable d = cur;
        if (d != shownNotified) {
            Displayable old = shownNotified;
            shownNotified = d;
            if (old != null) {
                Display.notifyShown(old, false);
            }
            if (d != null) {
                Display.notifyShown(d, true);
            }
        }
    }

    private static void runSerial() {
        while (true) {
            Runnable r;
            synchronized (serial) {
                if (serial.size() == 0) {
                    return;
                }
                r = (Runnable) serial.elementAt(0);
                serial.removeElementAt(0);
            }
            try {
                r.run();
            } catch (Throwable e) {
                System.err.println("[Ui] loi callSerially: " + e);
            }
        }
    }

    private static void textDone(boolean ok) {
        TextBox t = textBox;
        if (t == null) {
            return;
        }
        if (ok) {
            String s = Disp.textResult();
            Display.setText(t, s != null ? s : "");
        }
        CommandListener l = Display.listenerOf(t);
        Vector cmds = Display.commandsOf(t);
        Command pick = null;
        for (int i = 0; i < cmds.size(); i++) {
            Command c = (Command) cmds.elementAt(i);
            int ty = c.getCommandType();
            if (ok && (ty == Command.OK || ty == Command.SCREEN || ty == Command.ITEM)) {
                pick = c;
                break;
            }
            if (!ok && (ty == Command.CANCEL || ty == Command.BACK || ty == Command.EXIT || ty == Command.STOP)) {
                pick = c;
                break;
            }
        }
        if (pick == null && cmds.size() > 0) {
            pick = (Command) cmds.elementAt(ok ? 0 : cmds.size() - 1);
        }
        if (l != null && pick != null) {
            l.commandAction(pick, t);
        }
    }

    static void loop() {
        evThread = Thread.currentThread();
        int[] ev = new int[4];
        while (true) {
            int type = Disp.waitEvent(ev);
            try {
                notifyShown();
                Displayable d = cur;
                switch (type) {
                    case Disp.EV_KEY_PRESS:
                    case Disp.EV_KEY_RELEASE:
                    case Disp.EV_KEY_REPEAT:
                        if (d != null && Display.isCanvas(d)) {
                            Display.keyCanvas(d, type == Disp.EV_KEY_PRESS ? 1 : type == Disp.EV_KEY_RELEASE ? 2 : 3, ev[1]);
                        }
                        break;
                    case Disp.EV_POINTER_PRESS:
                    case Disp.EV_POINTER_DRAG:
                    case Disp.EV_POINTER_RELEASE:
                        if (d != null && Display.isCanvas(d)) {
                            Display.pointerCanvas(d, type, ev[1], ev[2]);
                        }
                        break;
                    case Disp.EV_REPAINT:
                        paintNow();
                        break;
                    case Disp.EV_TEXT_DONE:
                        textDone(ev[1] != 0);
                        break;
                    case Disp.EV_SERIAL:
                        runSerial();
                        break;
                    case Disp.EV_GC:
                        System.gc();
                        break;
                    case Disp.EV_SIZE:
                        if (d != null && Display.isCanvas(d)) {
                            Display.sizeCanvas(d, ev[1], ev[2]);
                        }
                        Disp.requestRepaint();
                        break;
                    default:
                        break;
                }
            } catch (Throwable e) {
                System.err.println("[Ui] loi su kien " + type + ": " + e);
            }
        }
    }
}
