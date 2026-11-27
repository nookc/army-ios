package j2n;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

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
    /* Avatar: Form co TextField (vd nhap so dien thoai) -> cung hien bang hop nhap chu */
    private static Form textForm;
    private static TextField textField;
    /* man ve phong to (ban ban ca): ve vao anh lon roi trinh bay anh do */
    private static Image hdImg;
    private static Graphics hdG;
    /* Avatar: man thuong ve net cao (x HD_MAN) khi khung truoc co ve anh dt3/ (dau truong): game van ve toa do logic,
     * moi lenh ve phong to sac net theo diem anh, rieng anh net cao ve dung diem anh that -> bang sung / nut / chu ro net */
    public static final int HD_MAN = 3;
    private static int hdMan = 1;
    private static int hdKhong;
    private static Image manImg;
    private static Graphics manG;
    /* nha phim / nha cham tra ve dung man da nhan luc bam (vd bam X thoat ban ca: lan nha khong thanh cu cham o man moi) */
    private static final Hashtable keyTarget = new Hashtable();
    private static Displayable ptrTarget;

    /** so lan phong to cho Canvas d (theo ten lop nen tang dat) */
    public static int scaleFor(Displayable d) {
        if (!Display.isCanvas(d)) {
            return 1;
        }
        int s = Disp.hdScale();
        if (s <= 1) {
            return 1;
        }
        String names = Disp.hdNames();
        if (names == null || names.length() == 0) {
            return 1;
        }
        String n = d.getClass().getName();
        int p = 0;
        while (p <= names.length()) {
            int c = names.indexOf(',', p);
            if (c < 0) {
                c = names.length();
            }
            String one = names.substring(p, c).trim();
            if (one.length() > 0 && (n.equals(one) || n.endsWith("." + one))) {
                return s;
            }
            p = c + 1;
        }
        return 1;
    }

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
            textForm = null;
            textField = null;
            Disp.showTextBox(t.getTitle(), t.getString(), Display.maxSizeOf(t), Display.constraintsOf(t));
        } else if (d instanceof Form && ((Form) d).firstTextField() != null) {
            Form f = (Form) d;
            TextField tf = f.firstTextField();
            textBox = null;
            textForm = f;
            textField = tf;
            String title = f.getTitle() != null ? f.getTitle() : "";
            if (tf.getLabel() != null && tf.getLabel().length() > 0) {
                title = title.length() > 0 ? title + " - " + tf.getLabel() : tf.getLabel();
            }
            String note = f.plainText();
            if (note.length() > 0) {
                title = title + "\n" + note;
            }
            Disp.showTextBox(title, tf.getString(), tf.getMaxSize(), tf.getConstraints());
        } else {
            Disp.requestRepaint();
        }
        Disp.postEvent(Disp.EV_SERIAL, 0, 0);
    }

    /* luong game vua xin ve (Canvas.repaint) tu lan serviceRepaints truoc */
    private static volatile boolean daXinVe;

    public static void repaint(Displayable c) {
        if (c == cur) {
            daXinVe = true;
            Disp.requestRepaint();
        }
    }

    public static void serviceRepaints(Displayable c) {
        if (c != cur) {
            // Canvas khong hien (vd vong lap thanh pho chay ngam khi dang o ban ban ca): khong gianh nhip quet cua man dang hien
            return;
        }
        if (Thread.currentThread() != evThread && daXinVe) {
            // v8h-31: vong lap game ve xong 1 khung -> ngu giua 2 khung theo nhip quet man hinh (neu nen tang co).
            // Chi danh dau khi vong nay CO xin ve: Avatar toc do x2..x10 chay nhieu vong logic khong ve - truoc day moi vong
            // deu doi 1 nhip quet (16,7 ms) -> toc do that ket o ~x3 va x7 chi con ~8 khung / giay (giat)
            daXinVe = false;
            Disp.frameMark();
        }
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
        int hdK = Disp.hdK();
        if (d != null && Display.isCanvas(d) && hdK > 1) {
            paintHd(d, hdK);
        } else if (d != null && Display.isCanvas(d)) {
            int s = Display.scaleOf(d);
            if (s > 1) {
                int w = Disp.width() * s, h = Disp.height() * s;
                if (hdImg == null || hdImg.getWidth() != w || hdImg.getHeight() != h) {
                    hdImg = Image.createImage(w, h);
                    hdG = hdImg.getGraphics();
                }
                Display.resetGraphics(hdG, w, h);
                try {
                    Display.paintCanvas(d, hdG);
                } catch (Throwable e) {
                    System.err.println("[Ui] loi paint: " + e);
                }
                Display.presentImage(hdImg);
            } else {
                hdImg = null;
                hdG = null;
                int muc = Disp.hdMuc();
                if (muc > 1) {
                    hdKhong = 0;
                    // anh dt3/ (x3, dau truong) -> man x3; chi anh HD x2 cua may chu -> man x2 (ve dung tung diem anh)
                    int m = muc >= HD_MAN ? HD_MAN : 2;
                    if (m != hdMan) {
                        hdMan = m;
                        manImg = null;
                        manG = null;
                    }
                } else if (hdMan > 1 && ++hdKhong > 6) {
                    hdMan = 1;      // roi dau truong: ve lai nhu cu (bo anh lon)
                    manImg = null;
                    manG = null;
                }
                if (hdMan > 1) {
                    int w = Disp.width(), h = Disp.height();
                    if (manImg == null || manImg.getWidth() != w * hdMan || manImg.getHeight() != h * hdMan) {
                        manImg = Image.createImage(w * hdMan, h * hdMan);
                        manG = manImg.getGraphics();
                    }
                    Display.resetGraphicsK(manG, w, h, hdMan);
                    try {
                        Display.paintCanvas(d, manG);
                    } catch (Throwable e) {
                        System.err.println("[Ui] loi paint: " + e);
                    }
                    Display.presentImage(manImg);
                } else {
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
            }
        }
        synchronized (paintLock) {
            paintedSeq++;
            paintLock.notifyAll();
        }
    }

    /* May HD: moi Canvas ve vao 1 bo dem net cao (K diem anh that / diem logic cua man). Canvas phong to s lan (ban ban
     * ca: kich thuoc logic gap s) ve voi he so K / s -> cung ra bo dem do net do. */
    /* May HD - tu ha do net: C do thoi gian ve trung binh (cua so 90 khung, bo khung dang ban phong to anh). 2 cua so lien
     * tiep cham hon nguong -> giam K 1 bac (4 -> 3 -> 2) va nho lai (RMS "avhdk") de lan sau vao la muot ngay. Cu 8 lan
     * mo app thi thu lai muc cua may 1 lan (may nong / ban tam thoi khong bi phat mai). */
    private static final int HD_NGUONG_US = 30000;
    private static boolean hdDoc;
    private static int hdChamLt;
    /** muc da nho: he so K (0 = theo may), hieu ung canh da tat (1) hay chua (0) */
    private static int hdNhoK, hdNhoFx;

    private static void hdGhi(int lan) {
        try {
            javax.microedition.rms.RecordStore rs = javax.microedition.rms.RecordStore.openRecordStore("avhdk", true);
            byte[] n = new byte[] {(byte) hdNhoK, (byte) lan, (byte) hdNhoFx};
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, n, 0, 3);
            } else {
                rs.addRecord(n, 0, 3);
            }
            rs.closeRecordStore();
        } catch (Throwable e) {
        }
    }

    private static int hdLan;

    private static void hdKhoiDau(int K) {
        hdDoc = true;
        int kNho = 0, lan = 0;
        try {
            javax.microedition.rms.RecordStore rs = javax.microedition.rms.RecordStore.openRecordStore("avhdk", true);
            byte[] b = rs.getNumRecords() > 0 ? rs.getRecord(1) : null;
            rs.closeRecordStore();
            if (b != null && b.length >= 2) {
                kNho = b[0];
                lan = b[1] & 0xFF;
                hdNhoFx = b.length >= 3 ? b[2] : 0;
            }
        } catch (Throwable e) {
        }
        lan = (lan + 1) & 0x7F;
        if (lan % 8 == 0) {
            kNho = 0;           // thu lai muc cao nhat cua may
            hdNhoFx = 0;
        }
        if (hdNhoFx == 1 && Disp.hdTin(7) > 1) {
            Disp.hdDat(100 + 1);
        }
        if (kNho >= 2 && kNho < K) {
            Disp.hdDat(kNho);
            System.err.println("[hd] dung do net da nho x" + kNho + " (may: x" + K + ")");
        } else {
            kNho = 0;
        }
        hdNhoK = kNho;
        hdLan = lan;
        hdGhi(lan);
    }

    private static void paintHd(Displayable d, int K) {
        if (!hdDoc) {
            hdKhoiDau(K);
            K = Disp.hdK();
        }
        Disp.hdMoc(0);
        int s = Display.scaleOf(d);
        if (s < 1) {
            s = 1;
        }
        int k = (K + s - 1) / s;
        if (k < 1) {
            k = 1;
        }
        int lw = Disp.width() * s, lh = Disp.height() * s;
        int pw = lw * k, ph = lh * k;
        if (manImg == null || Display.rawWidth(manImg) != pw || Display.rawHeight(manImg) != ph) {
            manImg = Display.rawImage(pw, ph);
            manG = manImg.getGraphics();
        }
        Display.resetGraphicsK(manG, lw, lh, k);
        try {
            Display.paintCanvas(d, manG);
        } catch (Throwable e) {
            System.err.println("[Ui] loi paint: " + e);
        }
        Disp.hdMoc(2);
        Display.presentImage(manImg);
        Disp.hdMoc(1);
        int tb = Disp.hdTin(4);
        if (tb > 0) {
            int ng = Disp.hdTin(6);
            if (ng <= 0) {
                ng = HD_NGUONG_US;
            }
            if (tb <= ng) {
                hdChamLt = 0;
            } else if (++hdChamLt >= 2) {
                hdChamLt = 0;
                if (Disp.hdTin(7) > 1) {
                    // buoc 1: bo hau ky canh (loa sang, tia nang...) - giu do net
                    Disp.hdDat(100 + 1);
                    hdNhoFx = 1;
                    hdGhi(hdLan);
                    System.err.println("[hd] may ve cham (" + tb + " us/khung) -> tat hau ky canh");
                } else if (K > 2) {
                    Disp.hdDat(K - 1);
                    hdNhoK = K - 1;
                    hdGhi(hdLan);
                    System.err.println("[hd] may ve cham (" + tb + " us/khung) -> giam do net xuong x" + (K - 1));
                }
            }
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
        Displayable t = textBox;
        if (t == null) {
            t = textForm;
        }
        if (t == null) {
            return;
        }
        if (ok) {
            String s = Disp.textResult();
            if (textBox != null) {
                Display.setText(textBox, s != null ? s : "");
            } else if (textField != null) {
                textField.setString(s != null ? s : "");
            }
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
                    case Disp.EV_KEY_REPEAT:
                        if (d != null && Display.isCanvas(d)) {
                            if (type == Disp.EV_KEY_PRESS) {
                                keyTarget.put(new Integer(ev[1]), d);
                            }
                            Display.keyCanvas(d, type == Disp.EV_KEY_PRESS ? 1 : 3, ev[1]);
                        }
                        break;
                    case Disp.EV_KEY_RELEASE: {
                        Displayable kt = (Displayable) keyTarget.remove(new Integer(ev[1]));
                        if (kt == null) {
                            kt = d;
                        }
                        if (kt != null && Display.isCanvas(kt)) {
                            Display.keyCanvas(kt, 2, ev[1]);
                        }
                        break;
                    }
                    case Disp.EV_POINTER_PRESS:
                    case Disp.EV_POINTER_DRAG:
                    case Disp.EV_POINTER_RELEASE:
                        if (d != null && Display.isCanvas(d)) {
                            int s = Display.scaleOf(d);
                            int x = ev[1], y = ev[2];
                            if (s > 1) {
                                x = x * s + (s >> 1);
                                y = y * s + (s >> 1);
                            }
                            if (type == Disp.EV_POINTER_PRESS) {
                                ptrTarget = d;
                                Display.pointerCanvas(d, type, x, y);
                            } else if (d == ptrTarget) {
                                Display.pointerCanvas(d, type, x, y);
                            }
                        }
                        if (type == Disp.EV_POINTER_RELEASE) {
                            ptrTarget = null;
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
                            int s = Display.scaleOf(d);
                            Display.sizeCanvas(d, ev[1] * s, ev[2] * s);
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
