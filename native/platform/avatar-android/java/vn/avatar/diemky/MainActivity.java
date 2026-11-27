package vn.avatar.diemky;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Avatar 250 (client 2.5.8) - ban Android "thuan": client J2ME da dich san sang ma may (libavatar.so),
 * KHONG chay qua gia lap / J2ME Loader.
 * Man hinh: khung game toan man (rong 240 / 320 / 360 tuy chon, cao theo ti le man), ban ban ca ve gap doi cho net;
 * nut "Aa" mep phai: cham = ban phim Android go thang vao o chu cua game, giu 2 giay = cai dat (chi co giao dien).
 * May chu: chi lay tu ban app (assets/server.dat ma hoa, do DoiMayChu / trang cai dat VPS ghi san). Khong co o nhap va khong hien
 * dia chi may chu o dau trong app (chu server khong muon lo IP).
 */
public final class MainActivity extends Activity {
    static final String TAG = "Avatar";
    static final int[] SIZES = {240, 320, 360};
    static final String[] SIZE_NAMES = {"To (240)", "Vừa (320)", "Nhỏ (360)"};
    /** game (runtime C) da chay: song qua viec Android tao lai activity */
    static boolean started;
    static int gW = 240, gH = 426;
    /**
     * Ban "Avatar 250 HD" (goi co assets/hd.cfg): man NGANG, may ve net cao (moi diem game = K diem anh that, anh phong to
     * thong minh, chu / khung net), dieu khien cham thang tren man hinh. Ban thuong (khong co tep) giu nguyen nhu cu.
     */
    static boolean banHd;
    /** ban HD: so diem anh that / diem game cua khung trinh bay (2..4) */
    static int hdK;

    SharedPreferences prefs;
    GameView view;
    int pxScale; // > 0: canvas hien dung n x n diem anh moi diem game
    AlertDialog dialog;
    EditText dialogEdit;
    TextReq dialogReq;
    AlertDialog settingsDlg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Native.act = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = getSharedPreferences("avatar", MODE_PRIVATE);
        if (!started) {
            banHd = coTep("hd.cfg");
        }
        if (banHd) {
            // ban HD: luon ngang (xoay 2 chieu theo may). Manifest cua ban HD cung dat sensorLandscape nen mo len la ngang ngay
            try {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } catch (Throwable t) {
            }
        }
        Intent it = getIntent();
        String extra = it != null ? it.getStringExtra("server") : null;   // CI / loi tat: am start --es server host:cong
        if (extra != null && normalizeServer(extra).length() > 0) {
            prefs.edit().putString("server", normalizeServer(extra)).apply();
        }
        Log.i(TAG, "[app] activity tao (game " + (started ? "dang chay" : "chua chay") + ")");
        immersive();
        // may chu dien san trong APK (ma hoa) -> vao thang game; ban chua gan may chu -> bao tai app tu trang web
        if (!started) {
            byte[] enc = bundledEnc();
            try {
                encOk = enc != null && Native.setServerEnc(enc);
            } catch (Throwable t) {
                encOk = false;
            }
        }
        if (started || encOk || server().length() > 0) {
            startGame();
        } else {
            noServer();
        }
    }

    /** toan man hinh: an thanh trang thai + thanh dieu huong (vuot tu mep de hien tam) */
    @SuppressWarnings("deprecation")
    void immersive() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } catch (Throwable t) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            immersive();
        }
    }

    /** goi tu luong game (KbHook): game vao / ra o go chu -> bat / tat ban phim dien thoai.
     *  show: 0 tat, 1 o chu thuong, 2 o mat khau (KbHook goi lai khi doi o chu -> bat dau lai bo dem go) */
    static void wantKeyboard(final int show, final int areaBottom) {
        final MainActivity a = Native.act;
        if (a == null) {
            return;
        }
        a.runOnUiThread(new Runnable() {
            public void run() {
                if (a.view != null) {
                    a.view.gameKeyboard(show, areaBottom);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) {
            view.resumeFrames();
        }
        Native.soundPauseAll(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view != null) {
            view.pauseFrames();
            view.releaseAll();
        }
        Native.soundPauseAll(true);
    }

    @Override
    protected void onDestroy() {
        closeDialogSilently();
        if (Native.act == this) {
            Native.act = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ may chu + cai dat
    static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        in.close();
        return new String(bo.toByteArray(), "UTF-8").trim();
    }

    /** dia chi may chu dien san trong APK: assets/server.dat (ma hoa + xac thuc, DoiMayChu / trang cai dat VPS ghi).
     *  Giai ma o ma may (Native.setServerEnc): dia chi khong nam o dang chu trong APK, khong qua Java, khong ghi log. */
    byte[] bundledEnc() {
        try {
            java.io.InputStream in = getAssets().open("server.dat");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            in.close();
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** dia chi da luu (chi cho ban chua gan may chu: CI chay thu am start --es server ...) */
    String server() {
        String s = prefs.getString("server", null);
        return s != null ? s : "";
    }

    static boolean encOk;

    void noServer() {
        AlertDialog.Builder bd = new AlertDialog.Builder(this);
        bd.setTitle("Avatar 250");
        bd.setMessage("Bản app này chưa được gắn máy chủ.\nHãy tải app ở trang web của máy chủ rồi cài lại.");
        bd.setCancelable(false);
        bd.setPositiveButton("Thoát", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                exitApp();
            }
        });
        bd.show();
        Log.i(TAG, "[app] chua gan may chu");
    }

    int savedWidth() {
        int v = prefs.getInt("vw", SIZES[0]);
        for (int s : SIZES) {
            if (s == v) {
                return v;
            }
        }
        return SIZES[0];
    }

    /** bo http://, dau / va khoang trang neu nguoi choi dan ca duong link */
    static String normalizeServer(String s) {
        s = s.trim();
        int p = s.indexOf("://");
        if (p >= 0) {
            s = s.substring(p + 3);
        }
        int sl = s.indexOf('/');
        if (sl >= 0) {
            s = s.substring(0, sl);
        }
        return s.replace(" ", "");
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** giu nut Aa 2 giay: cai dat co giao dien + thoat game (khong co / khong hien dia chi may chu) */
    void settingsDialog() {
        if (settingsDlg != null && settingsDlg.isShowing()) {
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));
        TextView tv2 = new TextView(this);
        tv2.setText("Cỡ giao diện");
        box.addView(tv2);
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(LinearLayout.HORIZONTAL);
        final int curVw = savedWidth();
        for (int i = 0; i < SIZES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(SIZE_NAMES[i]);
            rb.setId(1000 + i);
            rg.addView(rb);
            if (SIZES[i] == curVw) {
                rb.setChecked(true);
            }
        }
        if (rg.getCheckedRadioButtonId() == -1) {
            rg.check(1000);
        }
        box.addView(rg);
        AlertDialog.Builder bd = new AlertDialog.Builder(this);
        bd.setTitle("Avatar 250");
        bd.setView(box);
        bd.setCancelable(true);
        bd.setPositiveButton("Lưu", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                int sel = rg.getCheckedRadioButtonId() - 1000;
                int nvw = SIZES[sel >= 0 && sel < SIZES.length ? sel : 0];
                if (nvw != curVw || prefs.getBoolean("vwSet", false)) {
                    prefs.edit().putInt("vw", nvw).putBoolean("vwSet", true).apply(); // nguoi choi tu chon co giao dien
                }
                settingsDlg = null;
                if (nvw != curVw) {
                    Toast.makeText(MainActivity.this, "Mở lại app để đổi cỡ giao diện", Toast.LENGTH_LONG).show();
                }
            }
        });
        bd.setNegativeButton("Đóng", null);
        bd.setNeutralButton("Thoát game", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                exitApp();
            }
        });
        settingsDlg = bd.show();
        Log.i(TAG, "[app] hop cai dat");
    }

    void startGame() {
        if (view != null) {
            return;
        }
        view = new GameView(this);
        setContentView(view);
        view.requestFocus();
        view.resumeFrames();
        if (pending != null) {
            // activity bi tao lai giua luc dang nhap chu: hien lai hop (giu chu da go)
            view.post(new Runnable() {
                public void run() {
                    showPending();
                }
            });
        }
    }

    boolean coTep(String ten) {
        try {
            InputStream in = getAssets().open(ten);
            in.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    long hdChoTu;

    /** Ban HD: Canvas ngang - cao ~220..300 diem game (hoac theo "Co giao dien"), rong theo ti le man; khung ve net K lan */
    void onViewSizedHd(final int w, final int h) {
        if (w < h) {
            // man chua xoay ngang xong: doi lan bo tri sau. May khong xoay duoc (cua so chia doi...) thi sau 1,5 giay chay luon
            if (hdChoTu == 0) {
                hdChoTu = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - hdChoTu < 1500) {
                if (view != null) {
                    view.postDelayed(new Runnable() {
                        public void run() {
                            if (!started && view != null && view.getWidth() > 0) {
                                onViewSized(view.getWidth(), view.getHeight());
                            }
                        }
                    }, 300);
                }
                return;
            }
        }
        int vw, vh, n;
        if (prefs.getBoolean("vwSet", false)) {
            int c = savedWidth();
            vh = c <= 240 ? 240 : c <= 320 ? 300 : 360;      // To / Vua / Nho
            vw = Math.round(vh * (float) w / Math.max(1, h));
            n = Math.round(h / (float) vh);
            pxScale = 0;
        } else {
            n = Math.max(2, Math.min(w, h) / 220);
            vw = w / n;
            vh = h / n;
            pxScale = n;
        }
        if (w >= h) {
            vw = Math.max(vh * 4 / 3, Math.min(vw, vh * 12 / 5));
            vw -= vw % 2;
        } else {
            vh = Math.max(vw * 4 / 3, Math.min(vh, vw * 12 / 5));
            vh -= vh % 2;
        }
        gW = vw;
        gH = vh;
        hdK = Math.max(2, Math.min(4, n));
        File data = new File(getFilesDir(), "rms");
        data.mkdirs();
        if (!encOk) {
            Native.setServer(server());
        }
        Native.setHd("FishGame", 2);
        Native.setHdK(hdK);
        Native.setHdAnh(true);
        if (!Native.start(getAssets(), data.getAbsolutePath(), gW, gH)) {
            Log.e(TAG, "khong khoi dong duoc runtime");
            new AlertDialog.Builder(this).setTitle("Lỗi").setMessage("Không khởi động được game (thiếu dữ liệu trong APK).")
                    .setPositiveButton("Thoát", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            exitApp();
                        }
                    }).show();
            return;
        }
        started = true;
        Log.i(TAG, "[app] vao game HD: man " + gW + "x" + gH + " net x" + hdK + " (view " + w + "x" + h + ")");
        if (view != null) {
            view.relayout();
        }
    }

    /** View co kich thuoc: dung Canvas (rong theo cai dat, cao theo ti le man) roi chay game */
    void onViewSized(int w, int h) {
        if (started) {
            return;
        }
        if (banHd) {
            onViewSizedHd(w, h);
            return;
        }
        int vw, vh;
        if (prefs.getBoolean("vwSet", false)) {
            vw = savedWidth();
            vh = Math.max(vw * 4 / 3, Math.round(vw * (float) h / Math.max(1, w)));
            pxScale = 0;
        } else {
            // moi diem anh game = n x n diem anh man hinh (n nguyen): net deu, khong nhoe / rung khi cuon ban do
            int n = Math.max(2, w / 220);
            vw = w / n;
            vh = h / n;
            pxScale = n;
        }
        vh = Math.max(vw * 4 / 3, Math.min(vh, vw * 12 / 5));
        vh -= vh % 2;
        gW = vw;
        gH = vh;
        File data = new File(getFilesDir(), "rms");
        data.mkdirs();
        if (!encOk) {
            Native.setServer(server());
        }
        Native.setHd("FishGame", 2);
        if (!Native.start(getAssets(), data.getAbsolutePath(), gW, gH)) {
            Log.e(TAG, "khong khoi dong duoc runtime");
            new AlertDialog.Builder(this).setTitle("Lỗi").setMessage("Không khởi động được game (thiếu dữ liệu trong APK).")
                    .setPositiveButton("Thoát", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            exitApp();
                        }
                    }).show();
            return;
        }
        started = true;
        Log.i(TAG, "[app] vao game: man " + gW + "x" + gH);
    }

    void exitApp() {
        finish();
        System.exit(0);
    }

    // ------------------------------------------------------------------ phim cung (nut Back, ban phim ngoai, adb input)
    static int mapKey(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return -1;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return -2;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return -3;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return -4;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return -5;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SOFT_LEFT:
                return -6;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_SOFT_RIGHT:
                return -7;
            case KeyEvent.KEYCODE_DEL:
                return -8;
            default:
                int u = e == null ? 0 : e.getUnicodeChar();
                return u > 0 ? u : 0;
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || !started) {
            return super.onKeyDown(code, e);
        }
        int k = mapKey(code, e);
        if (k == 0) {
            return super.onKeyDown(code, e);
        }
        if (e.getRepeatCount() == 0) {
            if (k >= 32) {
                keyTap(-1000);   // o chu cua game: go thang ky tu (khong T9)
            }
            Native.post(Native.KEY_PRESS, k, 0);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || !started) {
            return super.onKeyUp(code, e);
        }
        int k = mapKey(code, e);
        if (k == 0) {
            return super.onKeyUp(code, e);
        }
        Native.post(Native.KEY_RELEASE, k, 0);
        return true;
    }

    static void keyTap(int k) {
        Native.post(Native.KEY_PRESS, k, 0);
        Native.post(Native.KEY_RELEASE, k, 0);
    }

    /** chu tu ban phim Android -> phim cua game (-1000: o chu chuyen sang go thang; '\n' = chon) */
    static void typeText(CharSequence t) {
        if (!started || t == null) {
            return;
        }
        boolean mode = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\n') {
                keyTap(-5);
                continue;
            }
            if (ch < 32 || (ch >= 0xD800 && ch <= 0xDFFF)) {   // dieu khien / emoji: font game khong ve duoc
                continue;
            }
            if (!mode) {
                keyTap(-1000);
                mode = true;
            }
            keyTap(ch);
        }
    }

    // ------------------------------------------------------------------ hop nhap chu cua game (Form / TextBox)
    static final class TextReq {
        final String title;
        String text;
        final int max, cons;

        TextReq(String title, String text, int max, int cons) {
            this.title = title;
            this.text = text;
            this.max = max;
            this.cons = cons;
        }
    }

    /** hop dang cho tra loi (chi doc/ghi tren luong UI) */
    static TextReq pending;

    /** goi tu luong game */
    static void requestTextBox(final String title, final String text, final int max, final int cons) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                pending = new TextReq(title, text, max, cons);
                MainActivity a = Native.act;
                if (a != null && !a.isFinishing() && a.view != null) {
                    a.showPending();
                }
            }
        });
    }

    void closeDialogSilently() {
        AlertDialog d = dialog;
        if (d == null) {
            return;
        }
        if (dialogEdit != null && dialogReq != null && dialogReq == pending) {
            dialogReq.text = dialogEdit.getText().toString();
        }
        dialog = null;
        dialogEdit = null;
        dialogReq = null;
        d.setOnCancelListener(null);
        try {
            d.dismiss();
        } catch (Throwable e) {
        }
    }

    void hideIme(View v) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && v != null && v.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        } catch (Throwable e) {
        }
    }

    static void finishText(TextReq r, boolean ok, String text) {
        if (pending != r) {
            return;
        }
        pending = null;
        Native.textDone(ok, ok ? text : null);
    }

    void showPending() {
        final TextReq r = pending;
        if (r == null || isFinishing()) {
            return;
        }
        if (view != null) {
            view.releaseAll();
        }
        closeDialogSilently();
        final EditText ed = new EditText(this);
        ed.setText(r.text == null ? "" : r.text);
        ed.setSingleLine(true);
        ed.setSelectAllOnFocus(true);
        int c = r.cons & 0xFFFF;
        boolean pw = (r.cons & 0x10000) != 0;
        int it;
        if (c == 2) {
            it = InputType.TYPE_CLASS_NUMBER | (pw ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : 0);
        } else if (c == 5) {
            it = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        } else if (c == 3) {
            it = InputType.TYPE_CLASS_PHONE;
        } else if (c == 1) {
            it = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        } else if (c == 4) {
            it = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        } else {
            it = InputType.TYPE_CLASS_TEXT | (pw ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        ed.setInputType(it);
        if (r.max > 0) {
            ed.setFilters(new InputFilter[]{new InputFilter.LengthFilter(r.max)});
        }
        AlertDialog.Builder bd = new AlertDialog.Builder(this);
        bd.setTitle(r.title != null && r.title.length() > 0 ? r.title : "Nhập");
        bd.setView(ed);
        bd.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                hideIme(ed);
                finishText(r, true, ed.getText().toString());
            }
        });
        bd.setNegativeButton("Hủy", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                hideIme(ed);
                finishText(r, false, null);
            }
        });
        bd.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface d) {
                finishText(r, false, null);
            }
        });
        final AlertDialog d = bd.create();
        d.setCanceledOnTouchOutside(false);
        ed.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                        || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            }
        });
        ed.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Window dw = d.getWindow();
        if (dw != null) {
            dw.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        d.show();
        ed.requestFocus();
        dialog = d;
        dialogEdit = ed;
        dialogReq = r;
        Log.i(TAG, "[app] hop nhap chu kieu " + r.cons);
    }

    // ================================================================== man hinh game
    static final class GameView extends View implements Choreographer.FrameCallback {
        final MainActivity act;
        /** man thuong (anh diem anh) phong to sac net; man phong to (ban ban ca) hoac phong it phong min */
        final Paint sharp = new Paint(), smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
        /** ban HD: khung chep nguyen byte (0xAARRGGBB) -> ve voi bo loc doi kenh R <-> B (GPU lam, khong ton CPU) */
        final Paint hdPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint tab = new Paint(Paint.ANTI_ALIAS_FLAG), tabLine = new Paint(Paint.ANTI_ALIAS_FLAG),
                tabText = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect src = new Rect(), dst = new Rect(), vis = new Rect();
        final RectF tabRect = new RectF();
        final int[] loc = new int[2];
        Bitmap frame;
        int lastFrame = -1;
        boolean framesOn, onTab, kbShown;
        long tabDown;
        float k = 1f;
        int gameKbArea; // > 0: game dang o go chu (ban phim dien thoai do game xin)
        int ptr = -1;
        String lastLayout;

        GameView(MainActivity a) {
            super(a);
            act = a;
            setFocusable(true);
            setFocusableInTouchMode(true);
            setBackgroundColor(Color.BLACK);
            sharp.setFilterBitmap(false);
            sharp.setAntiAlias(false);
            sharp.setDither(false);
            hdPaint.setDither(false);
            hdPaint.setColorFilter(new ColorMatrixColorFilter(new float[]{
                    0, 0, 1, 0, 0,
                    0, 1, 0, 0, 0,
                    1, 0, 0, 0, 0,
                    0, 0, 0, 1, 0}));
            tab.setColor(0x6B000000);
            tabLine.setColor(0x59FFFFFF);
            tabLine.setStyle(Paint.Style.STROKE);
            tabText.setColor(0xE6FFFFFF);
            tabText.setTextAlign(Paint.Align.CENTER);
            tabText.setFakeBoldText(true);
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                public void onGlobalLayout() {
                    relayout();
                }
            });
        }

        void resumeFrames() {
            if (!framesOn) {
                framesOn = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        void pauseFrames() {
            framesOn = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long t) {
            if (!framesOn) {
                return;
            }
            if (started) {
                int fs = Native.frameSize();
                int fw = fs >>> 16, fh = fs & 0xFFFF;
                if (fw > 0 && fh > 0 && (frame == null || frame.getWidth() != fw || frame.getHeight() != fh)) {
                    frame = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
                    frame.eraseColor(Color.BLACK);
                    lastFrame = -1;
                }
                if (frame != null) {
                    int f = banHd ? Native.copyFrameRaw(frame, lastFrame) : Native.copyFrame(frame, lastFrame);
                    if (f != lastFrame) {
                        lastFrame = f;
                        invalidate(dst);
                    }
                }
                Native.vsync(t);
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) {
                return;
            }
            act.onViewSized(w, h);
            float s = getResources().getDisplayMetrics().density;
            tabRect.set(w - 36 * s, h * 0.42f, w - 3 * s, h * 0.42f + 32 * s);
            tabText.setTextSize(14 * s);
            tabLine.setStrokeWidth(Math.max(1f, s));
            relayout();
        }

        /** dat khung game vua phan man hinh con thay (ban phim Android bat thi thu nho lai cho khoi bi che) */
        void relayout() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || !started) {
                return;
            }
            int avail = h;
            try {
                getWindowVisibleDisplayFrame(vis);
                getLocationOnScreen(loc);
                int a = vis.bottom - loc[1];
                if (a >= h / 3 && a < h) {
                    avail = a;
                }
            } catch (Throwable t) {
            }
            kbShown = avail < h * 0.85f;
            int n = act.pxScale;
            if (n > 0 && gW * n <= w && gH * n <= h && (!kbShown || gameKbArea > 0)) {
                k = n;
            } else {
                k = Math.min(w / (float) gW, avail / (float) gH);
            }
            int dw = Math.round(gW * k), dh = Math.round(gH * k);
            int ox = (w - dw) / 2, oy = Math.max(0, (h - dh) / 2);
            if (kbShown && gameKbArea > 0) {
                // o chat ngay tren ban phim: day khung game len, phan duoi (vung ban phim cu cua game) nam sau ban phim
                oy = Math.min(oy, avail - Math.round(gameKbArea * k));
            } else if (kbShown) {
                oy = Math.max(0, (avail - dh) / 2);
            }
            if (dst.left != ox || dst.top != oy || dst.width() != dw || dst.height() != dh) {
                dst.set(ox, oy, ox + dw, oy + dh);
                invalidate();
            }
            String l = "[bocuc] canvas " + (loc[0] + dst.left) + " " + (loc[1] + dst.top) + " " + (loc[0] + dst.right) + " "
                    + (loc[1] + dst.bottom) + " aa " + Math.round(loc[0] + tabRect.centerX()) + " "
                    + Math.round(loc[1] + tabRect.centerY()) + " game " + gW + " " + gH;
            if (!l.equals(lastLayout)) {
                lastLayout = l;
                Log.i(TAG, l);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            Bitmap f = frame;
            if (f != null && dst.width() > 0) {
                src.set(0, 0, f.getWidth(), f.getHeight());
                c.drawBitmap(f, src, dst, banHd ? hdPaint : f.getWidth() > gW || k < 2f ? smooth : sharp);
            }
            if (started) {
                float r = 9 * getResources().getDisplayMetrics().density;
                c.drawRoundRect(tabRect, r, r, tab);
                c.drawRoundRect(tabRect, r, r, tabLine);
                c.drawText("Aa", tabRect.centerX(), tabRect.centerY() + tabText.getTextSize() * 0.35f, tabText);
            }
        }

        int gx(float x) {
            int v = (int) ((x - dst.left) / Math.max(0.01f, k));
            return Math.max(0, Math.min(gW - 1, v));
        }

        int gy(float y) {
            int v = (int) ((y - dst.top) / Math.max(0.01f, k));
            return Math.max(0, Math.min(gH - 1, v));
        }

        void releaseAll() {
            if (ptr >= 0) {
                ptr = -1;
                Native.post(Native.POINTER_RELEASE, 0, 0);
            }
            onTab = false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!started) {
                return true;
            }
            int a = e.getActionMasked();
            float x = e.getX(), y = e.getY();
            if (a == MotionEvent.ACTION_DOWN && tabRect.contains(x, y)) {
                onTab = true;
                tabDown = System.currentTimeMillis();
                return true;
            }
            if (onTab) {
                if (a == MotionEvent.ACTION_UP) {
                    onTab = false;
                    if (System.currentTimeMillis() - tabDown > 1800) {
                        act.settingsDialog();
                    } else {
                        toggleKeyboard();
                    }
                } else if (a == MotionEvent.ACTION_CANCEL) {
                    onTab = false;
                }
                return true;
            }
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                    ptr = e.getPointerId(0);
                    Native.post(Native.POINTER_PRESS, gx(x), gy(y));
                    break;
                case MotionEvent.ACTION_MOVE: {
                    int i = ptr >= 0 ? e.findPointerIndex(ptr) : -1;
                    if (i >= 0) {
                        Native.post(Native.POINTER_DRAG, gx(e.getX(i)), gy(e.getY(i)));
                    }
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = e.getActionIndex();
                    if (e.getPointerId(i) == ptr) {
                        ptr = -1;
                        Native.post(Native.POINTER_RELEASE, gx(e.getX(i)), gy(e.getY(i)));
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (ptr >= 0) {
                        ptr = -1;
                        Native.post(Native.POINTER_RELEASE, gx(x), gy(y));
                    }
                    break;
                default:
                    break;
            }
            return true;
        }

        /** game xin / tra ban phim (o go chu): areaBottom = day vung game con dung (diem anh Canvas) */
        /** kieu o chu game dang go: 1 chu thuong, 2 mat khau */
        int kieuGo = 1;

        void gameKeyboard(int kieu, int areaBottom) {
            boolean show = kieu != 0;
            InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            gameKbArea = show ? Math.max(1, areaBottom) : 0;
            if (im != null) {
                if (show) {
                    kieuGo = kieu == 2 ? 2 : 1;
                    requestFocus();
                    // o moi / kieu moi: ban phim tao lai ket noi (kieu o, bo dem go trong) - Telex khong ghep vao chu o truoc
                    im.restartInput(this);
                    im.showSoftInput(this, InputMethodManager.SHOW_FORCED);
                } else {
                    im.hideSoftInputFromWindow(getWindowToken(), 0);
                }
            }
            relayout();
        }

        void toggleKeyboard() {
            InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im == null) {
                return;
            }
            requestFocus();
            if (kbShown) {
                im.hideSoftInputFromWindow(getWindowToken(), 0);
            } else {
                im.showSoftInput(this, InputMethodManager.SHOW_FORCED);
            }
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo out) {
            // chu thuong: ban phim go duoc tieng Viet (Telex / VNI cua Gboard, Laban Key...) - truoc day de kieu "mat khau
            // hien" nen nhieu ban phim tat Telex, khong go duoc dau. Khong goi y / tu sua (ten tai khoan khong bi doi).
            out.inputType = kieuGo == 2
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN;
            out.initialSelStart = 0;
            out.initialSelEnd = 0;
            final View v = this;
            // ban sao chu da go (ban phim doc lai duoc chu truoc con tro de ghep dau). Moi lan ban phim sua: so voi chu da
            // gui vao game -> gui so lan xoa (-8) + phan chu moi. Vd Telex "tie" + "e" -> "tiê": xoa 1, go "ê".
            return new BaseInputConnection(this, true) {
                String daGui = "";

                boolean kyTuGame(char c) {
                    return c >= 32 && !(c >= 0xD800 && c <= 0xDFFF);
                }

                int soKyTu(String s, int tu) {
                    int n = 0;
                    for (int i = tu; i < s.length(); i++) {
                        if (kyTuGame(s.charAt(i))) {
                            n++;
                        }
                    }
                    return n;
                }

                void dongBo() {
                    Editable e = getEditable();
                    String t = e == null ? "" : e.toString();
                    if (t.equals(daGui)) {
                        return;
                    }
                    String cu = daGui;
                    int p = 0, n = Math.min(t.length(), cu.length());
                    while (p < n && t.charAt(p) == cu.charAt(p)) {
                        p++;
                    }
                    if (p > 0 && p < cu.length() && Character.isLowSurrogate(cu.charAt(p))) {
                        p--;
                    }
                    daGui = t;
                    int xoa = soKyTu(cu, p);
                    for (int i = 0; i < xoa; i++) {
                        keyTap(-8);
                    }
                    if (t.length() > p) {
                        typeText(t.substring(p));
                    }
                }

                /** xoa ban sao (sau Enter / chu qua dai) va bao ban phim doc lai */
                void datLai() {
                    Editable e = getEditable();
                    if (e != null) {
                        e.clear();
                    }
                    daGui = "";
                    v.post(new Runnable() {
                        public void run() {
                            InputMethodManager im = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (im != null) {
                                im.restartInput(v);
                            }
                        }
                    });
                }

                void xuongDong() {
                    dongBo();
                    keyTap(-5);
                    datLai();
                    // ban phim bat bang nut Aa (game khong xin): bam Gui / Xong la xong cau chat -> dong ban phim
                    // (tester: chat xong ban phim van mo). Game dang xin (o go chu cua game) thi de game tu dong (KbHook).
                    if (gameKbArea == 0) {
                        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        }
                    }
                }

                @Override
                public boolean commitText(CharSequence t, int pos) {
                    if (t != null && t.length() > 0 && (t.charAt(t.length() - 1) == '\n')) {
                        super.commitText(t.subSequence(0, t.length() - 1), pos);
                        xuongDong();
                        return true;
                    }
                    boolean r = super.commitText(t, pos);
                    dongBo();
                    Editable e = getEditable();
                    if (e != null && e.length() > 200 && getComposingSpanStart(e) < 0) {
                        datLai();
                    }
                    return r;
                }

                @Override
                public boolean setComposingText(CharSequence t, int pos) {
                    boolean r = super.setComposingText(t, pos);
                    dongBo();
                    return r;
                }

                @Override
                public boolean setComposingRegion(int a, int b) {
                    boolean r = super.setComposingRegion(a, b);
                    dongBo();
                    return r;
                }

                @Override
                public boolean finishComposingText() {
                    boolean r = super.finishComposingText();
                    dongBo();
                    return r;
                }

                @Override
                public boolean deleteSurroundingText(int before, int after) {
                    Editable e = getEditable();
                    if (e == null || e.length() == 0) {
                        // ban sao trong (chu da gui luc truoc / o khac): van xoa trong o game
                        for (int i = 0; i < Math.max(1, before); i++) {
                            keyTap(-8);
                        }
                        return true;
                    }
                    boolean r = super.deleteSurroundingText(before, after);
                    dongBo();
                    return r;
                }

                @Override
                public boolean sendKeyEvent(KeyEvent ev) {
                    int code = ev.getKeyCode();
                    if (code == KeyEvent.KEYCODE_DEL) {
                        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                            Editable e = getEditable();
                            int c = e == null ? 0 : android.text.Selection.getSelectionEnd(e);
                            if (e != null && c > 0) {
                                e.delete(c - 1, c);
                                dongBo();
                            } else {
                                keyTap(-8);
                            }
                        }
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_ENTER) {
                        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                            xuongDong();
                        }
                        return true;
                    }
                    return super.sendKeyEvent(ev);
                }

                @Override
                public boolean performEditorAction(int action) {
                    xuongDong();
                    return true;
                }
            };
        }
    }
}
