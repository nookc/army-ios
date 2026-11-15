package vn.avatar.diemky;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Vibrator;

/** Cau noi JNI: Java -> runtime C (libavatar.so: client Avatar dich san sang ma may) va C -> Java (goi tu luong game) */
final class Native {
    static {
        System.loadLibrary("avatar");
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

    /** v8h-31: 1 nhip quet man hinh (Choreographer frameTimeNanos) */
    static native void vsync(long frameTimeNanos);

    static native String gcStats();

    /** kich thuoc khung moi nhat: (rong << 16) | cao, 0 = chua co (ban ban ca ve gap doi -> khung lon hon) */
    static native int frameSize();

    /** may chu thay cho 127.0.0.1 cua client: "host" hoac "host:cong" */
    static native void setServer(String spec);

    /** Canvas ve phong to (ten lop cach nhau dau phay, so lan) - goi truoc start */
    static native void setHd(String names, int scale);

    /** activity dang hien (cap nhat o onCreate) */
    static volatile MainActivity act;

    // ------------------------------------------------------------------ goi tu C (luong game)
    static void showTextBox(String title, String text, int max, int cons) {
        MainActivity.requestTextBox(title, text, max, cons);
    }

    /** game vao / ra o go chu (KbHook trong client) -> bat / tat ban phim dien thoai */
    static void wantKeyboard(int show, int areaBottom) {
        MainActivity.wantKeyboard(show != 0, areaBottom);
    }

    static int soundCreate(byte[] data, String type) {
        return Sound.create(data, type);
    }

    /**
     * v8h-31: phat / dung / am luong chay tren luong am thanh rieng (theo dung thu tu goi): truoc day luong game goi thang
     * AudioTrack (stop + reloadStaticData + play: 1-10+ ms moi tieng ban / no) -> khung hinh bi tre
     */
    private static final java.util.concurrent.ExecutorService AM = java.util.concurrent.Executors.newSingleThreadExecutor(
            new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Avatar-AmThanh");
                    t.setDaemon(true);
                    return t;
                }
            });

    private static void am(Runnable r) {
        try {
            AM.execute(r);
        } catch (Throwable e) {
            r.run();
        }
    }

    static void soundStart(final int h, final int loops) {
        am(new Runnable() {
            public void run() {
                Sound.start(h, loops);
            }
        });
    }

    static void soundStop(final int h) {
        am(new Runnable() {
            public void run() {
                Sound.stop(h);
            }
        });
    }

    static void soundClose(final int h) {
        am(new Runnable() {
            public void run() {
                Sound.close(h);
            }
        });
    }

    /** app ra nen / vao lai: dung / phat tiep (cung luong am thanh - dung thu tu voi lenh phat dang cho) */
    static void soundPauseAll(final boolean pause) {
        am(new Runnable() {
            public void run() {
                Sound.pauseAll(pause);
            }
        });
    }

    static void soundVolume(final int h, final int level) {
        am(new Runnable() {
            public void run() {
                Sound.volume(h, level);
            }
        });
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
