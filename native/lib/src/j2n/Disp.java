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

    /** Avatar: Canvas co ten lop trong hdNames() (cach nhau dau phay) duoc ve phong to hdScale() lan (1 = tat) */
    public static native int hdScale();

    public static native String hdNames();

    /** trinh bay khung ve san trong mang diem anh (man phong to) */
    public static native void presentImage(int[] argb, int w, int h);

    /** Avatar: khung vua ve co anh net cao (dt3/) hay khong (doc xong xoa co) */
    public static native boolean hdSeen();

    /** Avatar HD: he so net cao lon nhat cua anh da ve tu lan hoi truoc (0 / 1 = khong co anh net cao), roi xoa */
    public static native int hdMuc();

    /** Avatar HD: nen tang bat "anh nhan vat / do / ban do net cao" (app xin bo anh HD x2 cua may chu) */
    public static native boolean hdAnh();

    /** May HD: so diem anh that cho 1 diem logic o moi dich ve (2..4); 0 = tat (ban thuong) */
    public static native int hdK();

    /** May HD: moc ve khung (0 = bat dau, 1 = xong) - do thoi gian ve + nhip kho anh net cao */
    public static native void hdMoc(int pha);

    /** May HD: thong tin (0 viec cho, 1 MB kho, 2 so anh, 3 han muc MB, 4 thoi gian ve trung binh micro giay, 5 so khung do) */
    public static native int hdTin(int what);

    /** May HD: doi he so K luc dang chay (2..4) */
    public static native void hdDat(int k);

    /** Avatar: game vao / ra che do go chu -> nen tang bat / tat ban phim he thong.
     *  show 1/0; areaBottom = day vung game con dung duoc (diem anh Canvas, 0 = ca man) de dat ngay tren ban phim */
    public static native void wantKeyboard(int show, int areaBottom);
}
