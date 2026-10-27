package j2n;

/** cau noi voi nen tang (iOS / Linux): man hinh, su kien, hop nhap chu */
public final class Disp {
    public static final int EV_KEY_PRESS = 1;
    public static final int EV_KEY_RELEASE = 2;
    public static final int EV_POINTER_PRESS = 3;
    public static final int EV_POINTER_DRAG = 4;
    public static final int EV_POINTER_RELEASE = 5;
    public static final int EV_REPAINT = 6;
    public static final int EV_TEXT_DONE = 7;
    public static final int EV_SERIAL = 8;
    public static final int EV_SIZE = 9;
    public static final int EV_KEY_REPEAT = 10;
    public static final int EV_PAUSE = 11;
    public static final int EV_RESUME = 12;
    public static final int EV_GC = 13; /* tu kiem thu: goi GC tren luong su kien */

    private Disp() {
    }

    public static native int width();

    public static native int height();

    /** cho su kien: ev[0] loai, ev[1..3] tham so; tra ve loai */
    public static native int waitEvent(int[] ev);

    public static native void requestRepaint();

    public static native boolean repaintPending();

    public static native void clearRepaint();

    public static native void postEvent(int type, int a, int b);

    public static native void present();

    public static native void showTextBox(String title, String text, int maxSize, int constraints);

    public static native String textResult();

    public static native boolean platformRequest(String url);

    public static native boolean vibrate(int ms);

    public static native void exit();

    /** v8h-31: luong game vua xong 1 khung (serviceRepaints) - lan ngu tiep theo doi nhip quet man hinh (disp.c) */
    public static native void frameMark();
}
