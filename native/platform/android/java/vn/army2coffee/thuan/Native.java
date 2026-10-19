package vn.army2coffee.thuan;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Vibrator;

/** Cau noi JNI: Java -> runtime C (libarmy2.so) va C -> Java (goi tu luong game) */
final class Native {
    static {
        System.loadLibrary("army2");
    }

    private Native() {
    }

    // ma su kien (platform.h)
    static final int KEY_PRESS = 1;
    static final int KEY_RELEASE = 2;
    static final int POINTER_PRESS = 3;
    static final int POINTER_DRAG = 4;
    static final int POINTER_RELEASE = 5;
    static final int GC = 13;

    static native boolean start(Object assets, String dataDir, int w, int h);

    static native void post(int type, int a, int b);

    static native void textDone(boolean ok, String text);

    static native int copyFrame(Bitmap bmp, int last);

    static native int frames();

    static native String gcStats();

    /** activity dang hien (cap nhat o onCreate) */
    static volatile MainActivity act;

    // ------------------------------------------------------------------ goi tu C (luong game)
    static void showTextBox(final String title, final String text, final int max, final int cons) {
        final MainActivity a = act;
        if (a == null) {
            textDone(false, null);
            return;
        }
        a.runOnUiThread(new Runnable() {
            public void run() {
                a.showTextBox(title, text, max, cons);
            }
        });
    }

    static int soundCreate(byte[] data, String type) {
        return Sound.create(data, type);
    }

    static void soundStart(int h, int loops) {
        Sound.start(h, loops);
    }

    static void soundStop(int h) {
        Sound.stop(h);
    }

    static void soundClose(int h) {
        Sound.close(h);
    }

    static void soundVolume(int h, int level) {
        Sound.volume(h, level);
    }

    static boolean openUrl(String url) {
        final MainActivity a = act;
        if (a == null || url == null) {
            return false;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    static boolean sms(String number, String text) {
        final MainActivity a = act;
        if (a == null) {
            return false;
        }
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (number == null ? "" : number)));
            i.putExtra("sms_body", text == null ? "" : text);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    static void vibrate(int ms) {
        final MainActivity a = act;
        if (a == null) {
            return;
        }
        try {
            Vibrator v = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(Math.max(10, Math.min(ms, 2000)));
            }
        } catch (Throwable e) {
        }
    }

    static void exitApp() {
        final MainActivity a = act;
        if (a != null) {
            a.finish();
        }
        System.exit(0);
    }
}
