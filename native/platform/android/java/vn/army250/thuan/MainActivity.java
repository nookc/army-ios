package vn.army250.thuan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;

/**
 * Army2.5.0 - ban Android "thuan": client J2ME dich san sang ma may (libarmy2.so), khong gia lap.
 * Man hinh: khung game 320 x gH phong to (diem anh net) + ban phim ao 4x2 giong ban iOS.
 */
public final class MainActivity extends Activity {
    static final String TAG = "Army250";
    static boolean started;
    static int gW = 320, gH = 466;
    static int created;

    GameView view;
    AlertDialog dialog;
    EditText dialogEdit;
    TextReq dialogReq;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Native.act = this;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);   // phim am luong chinh tieng game
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(0xFF63CCFF);
        }
        view = new GameView(this);
        setContentView(view);
        created++;
        Log.i(TAG, "[app] activity tao (lan " + created + ", game " + (started ? "dang chay" : "chua chay") + ")");
        if (pending != null) {
            // activity bi tao lai giua luc dang nhap chu: hien lai hop (giu chu da go)
            view.post(new Runnable() {
                public void run() {
                    showPending();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.resumeFrames();
        Sound.pauseAll(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.pauseFrames();
        view.releaseAll();
        Sound.pauseAll(true);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "[app] activity huy" + (isFinishing() ? " (ket thuc)" : ""));
        closeDialogSilently();
        if (Native.act == this) {
            Native.act = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ phim cung (nut Back, ban phim ngoai)
    private static int keyCode(int kc) {
        switch (kc) {
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
            case KeyEvent.KEYCODE_SPACE:
                return -5;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_F1:
                return -6;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_F2:
                return -7;
            case KeyEvent.KEYCODE_DEL:
                return -8;
            default:
                return 0;
        }
    }

    @Override
    public boolean onKeyDown(int kc, KeyEvent e) {
        int c = keyCode(kc);
        if (c != 0) {
            if (e.getRepeatCount() == 0) {
                Native.post(Native.KEY_PRESS, c, 0);
            }
            return true;
        }
        int u = e.getUnicodeChar();
        if (u > 32 && u < 127) {
            Native.post(Native.KEY_PRESS, u, 0);
            Native.post(Native.KEY_RELEASE, u, 0);
            return true;
        }
        return super.onKeyDown(kc, e);
    }

    @Override
    public boolean onKeyUp(int kc, KeyEvent e) {
        int c = keyCode(kc);
        if (c != 0) {
            Native.post(Native.KEY_RELEASE, c, 0);
            return true;
        }
        return super.onKeyUp(kc, e);
    }

    // ------------------------------------------------------------------ hop nhap chu (TextBox cua game)
    /** yeu cau nhap chu cua game; giu tinh de song qua viec activity bi Android tao lai */
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
                if (a != null && !a.isFinishing()) {
                    a.showPending();
                }
                // chua co activity (app dang o nen): hop se hien khi activity moi duoc tao
            }
        });
    }

    /** dong hop ma khong bao ket qua cho game (activity sap huy / co hop moi); giu chu da go */
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

    /** an ban phim Android (no khong tu an khi hop dong, se che ban phim ao cua game) */
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
            return;   // da tra loi roi / da co hop moi thay the
        }
        pending = null;
        Native.textDone(ok, ok ? text : null);
    }

    void showPending() {
        final TextReq r = pending;
        if (r == null || isFinishing()) {
            return;
        }
        view.releaseAll();
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
        d.setCanceledOnTouchOutside(false);   // cham nham ra ngoai khong mat chu dang go
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface di) {
                view.post(new Runnable() {
                    public void run() {
                        hideIme(view);
                    }
                });
            }
        });
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

    // ------------------------------------------------------------------ khoi dong game (sau khi biet kich thuoc)
    void startGame(int h) {
        if (started) {
            return;
        }
        gH = h;
        File data = new File(getFilesDir(), "rms");
        data.mkdirs();
        if (!Native.start(getAssets(), data.getAbsolutePath(), gW, gH)) {
            Log.e(TAG, "khong khoi dong duoc runtime");
            return;
        }
        started = true;
        view.makeFrame();
    }

    // ================================================================== man hinh + ban phim ao
    static final class GameView extends View implements Choreographer.FrameCallback {
        static final int[] CODES = {-6, -1, -40, -7, -3, -2, -4, -5};
        static final boolean[] DIR = {false, true, false, false, true, true, true, false};
        final MainActivity act;
        Bitmap frame;
        int lastFrame;
        boolean framesOn;
        final Rect canvasR = new Rect();
        final Rect padR = new Rect();
        final Paint pix = new Paint();
        final Paint fill = new Paint();
        final Paint line = new Paint();
        final Paint arrow = new Paint();
        final Bitmap[] labels = new Bitmap[8];
        final int[] cellCount = new int[8];
        final int[] keyOwner = new int[8];
        final SparseIntArray padPtr = new SparseIntArray();
        int canvasPtr = -1;

        GameView(MainActivity a) {
            super(a);
            act = a;
            setFocusable(true);
            setFocusableInTouchMode(true);
            pix.setFilterBitmap(false);
            pix.setAntiAlias(false);
            pix.setDither(false);
            line.setColor(0xFFA1CFED);
            line.setStrokeWidth(1);
            arrow.setColor(0xFF454D5C);
            arrow.setStyle(Paint.Style.STROKE);
            arrow.setStrokeCap(Paint.Cap.ROUND);
            arrow.setStrokeJoin(Paint.Join.ROUND);
            arrow.setAntiAlias(true);
            labels[0] = decode(PadLabels.F1);
            labels[2] = decode(PadLabels.LUC);
            labels[3] = decode(PadLabels.F2);
            labels[7] = decode(PadLabels.OK);
        }

        static Bitmap decode(String b64) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(d, 0, d.length);
            } catch (Throwable e) {
                return null;
            }
        }

        void makeFrame() {
            frame = Bitmap.createBitmap(gW, gH, Bitmap.Config.ARGB_8888);
            frame.eraseColor(Color.BLACK);
            layout(getWidth(), getHeight());
            invalidate();
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
            if (frame != null) {
                int f = Native.copyFrame(frame, lastFrame);
                if (f != lastFrame) {
                    lastFrame = f;
                    invalidate(canvasR);
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (!started) {
                // chieu cao khung game: rong 320, cao vua phan tren ban phim ao (giong ban iOS)
                int padH = padHeight(w, h);
                int gh = Math.round(320f * (h - padH) / Math.max(1, w));
                gh -= gh % 2;
                gh = Math.max(360, Math.min(600, gh));
                act.startGame(gh);
            } else if (frame == null) {
                makeFrame();   // activity moi (Android tao lai) gan vao game dang chay
            }
            layout(w, h);
        }

        static int padHeight(int w, int h) {
            int ph = w / 2;
            return Math.min(ph, h * 42 / 100);
        }

        void layout(int w, int h) {
            if (w <= 0 || h <= 0) {
                return;
            }
            int ph = padHeight(w, h);
            padR.set(0, h - ph, w, h);
            padDirty = true;
            int ah = h - ph;
            float s = Math.min(w / (float) gW, ah / (float) gH);
            int cw = Math.round(gW * s), ch = Math.round(gH * s);
            int x = (w - cw) / 2, y = (ah - ch) / 2;
            canvasR.set(x, y, x + cw, y + ch);
            arrow.setStrokeWidth(Math.max(2f, w / 160f));
            post(new Runnable() {
                public void run() {
                    logLayout();
                }
            });
        }

        String lastLayout;

        void logLayout() {
            int[] o = new int[2];
            getLocationOnScreen(o);
            String l = "[bocuc] canvas " + (o[0] + canvasR.left) + " " + (o[1] + canvasR.top) + " " + (o[0] + canvasR.right) + " "
                    + (o[1] + canvasR.bottom) + " pad " + (o[0] + padR.left) + " " + (o[1] + padR.top) + " " + (o[0] + padR.right) + " "
                    + (o[1] + padR.bottom) + " game " + gW + " " + gH;
            if (!l.equals(lastLayout)) {
                lastLayout = l;
                Log.i(TAG, l);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(0xFF63CCFF);
            if (frame != null) {
                c.drawBitmap(frame, null, canvasR, pix);
            }
            drawPad(c);
        }

        Bitmap padBmp;
        boolean padDirty = true;

        void drawPad(Canvas c) {
            int w = padR.width(), h = padR.height();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (padBmp == null || padBmp.getWidth() != w || padBmp.getHeight() != h) {
                padBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                padDirty = true;
            }
            if (padDirty) {
                padDirty = false;
                Canvas pc = new Canvas(padBmp);
                pc.translate(-padR.left, -padR.top);
                drawPadCells(pc);
            }
            c.drawBitmap(padBmp, padR.left, padR.top, null);
        }

        void drawPadCells(Canvas c) {
            int w = padR.width(), h = padR.height();
            int widest = 1;
            for (Bitmap b : labels) {
                if (b != null && b.getWidth() > widest) {
                    widest = b.getWidth();
                }
            }
            int k = Math.max(1, (int) Math.floor(w / 4f * 0.8f / widest));
            for (int i = 0; i < 8; i++) {
                int col = i % 4, row = i / 4;
                int l = padR.left + col * w / 4, t = padR.top + row * h / 2;
                int r = padR.left + (col + 1) * w / 4, b = padR.top + (row + 1) * h / 2;
                fill.setColor(cellCount[i] > 0 ? 0xFFCCE8FF : Color.WHITE);
                c.drawRect(l, t, r, b, fill);
                c.drawLine(l, t, r, t, line);
                c.drawLine(l, t, l, b, line);
                float mx = (l + r) / 2f, my = (t + b) / 2f;
                if (DIR[i]) {
                    float s = (b - t) / 9f;
                    Path p = new Path();
                    if (i == 1) {
                        p.moveTo(mx - s, my + s / 2);
                        p.lineTo(mx, my - s / 2);
                        p.lineTo(mx + s, my + s / 2);
                    } else if (i == 5) {
                        p.moveTo(mx - s, my - s / 2);
                        p.lineTo(mx, my + s / 2);
                        p.lineTo(mx + s, my - s / 2);
                    } else if (i == 4) {
                        p.moveTo(mx + s / 2, my - s);
                        p.lineTo(mx - s / 2, my);
                        p.lineTo(mx + s / 2, my + s);
                    } else {
                        p.moveTo(mx - s / 2, my - s);
                        p.lineTo(mx + s / 2, my);
                        p.lineTo(mx - s / 2, my + s);
                    }
                    c.drawPath(p, arrow);
                } else if (labels[i] != null) {
                    Bitmap lb = labels[i];
                    float dw = lb.getWidth() * k, dh = lb.getHeight() * k;
                    RectF dst = new RectF(Math.round(mx - dw / 2), Math.round(my - dh / 2), Math.round(mx - dw / 2) + dw,
                            Math.round(my - dh / 2) + dh);
                    c.drawBitmap(lb, null, dst, pix);
                }
            }
        }

        // -------------------------------------------------------------- cham
        int cellAt(float x, float y) {
            if (!padR.contains((int) x, (int) y)) {
                return -1;
            }
            int col = (int) ((x - padR.left) * 4 / Math.max(1, padR.width()));
            int row = (int) ((y - padR.top) * 2 / Math.max(1, padR.height()));
            return Math.min(1, Math.max(0, row)) * 4 + Math.min(3, Math.max(0, col));
        }

        void keyDown(int i) {
            if (keyOwner[i]++ == 0) {
                Native.post(Native.KEY_PRESS, CODES[i], 0);
            }
        }

        void keyUp(int i) {
            if (keyOwner[i] <= 0) {
                return;
            }
            if (--keyOwner[i] == 0) {
                Native.post(Native.KEY_RELEASE, CODES[i], 0);
            }
        }

        void press(int i) {
            if (++cellCount[i] == 1) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                padDirty = true;
                invalidate(padR);
            }
            keyDown(i);
        }

        void release(int i) {
            if (cellCount[i] <= 0) {
                return;
            }
            cellCount[i]--;
            keyUp(i);
            if (cellCount[i] == 0) {
                padDirty = true;
                invalidate(padR);
            }
        }

        int gx(float x) {
            int v = (int) ((x - canvasR.left) * gW / Math.max(1, canvasR.width()));
            return Math.max(0, Math.min(gW - 1, v));
        }

        int gy(float y) {
            int v = (int) ((y - canvasR.top) * gH / Math.max(1, canvasR.height()));
            return Math.max(0, Math.min(gH - 1, v));
        }

        void releaseAll() {
            padPtr.clear();
            for (int i = 0; i < 8; i++) {
                cellCount[i] = 0;
                if (keyOwner[i] > 0) {
                    keyOwner[i] = 0;
                    Native.post(Native.KEY_RELEASE, CODES[i], 0);
                }
            }
            if (canvasPtr >= 0) {
                canvasPtr = -1;
                Native.post(Native.POINTER_RELEASE, 0, 0);
            }
            padDirty = true;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int a = ev.getActionMasked();
            int ix = ev.getActionIndex();
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int id = ev.getPointerId(ix);
                    float x = ev.getX(ix), y = ev.getY(ix);
                    int cell = cellAt(x, y);
                    if (cell >= 0) {
                        padPtr.put(id, cell);
                        press(cell);
                    } else if (canvasPtr < 0 && canvasR.contains((int) x, (int) y)) {
                        canvasPtr = id;
                        Native.post(Native.POINTER_PRESS, gx(x), gy(y));
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        int id = ev.getPointerId(i);
                        float x = ev.getX(i), y = ev.getY(i);
                        if (id == canvasPtr) {
                            Native.post(Native.POINTER_DRAG, gx(x), gy(y));
                        } else {
                            int old = padPtr.get(id, -1);
                            if (old < 0) {
                                continue;
                            }
                            int now = cellAt(x, y);
                            if (now >= 0 && now != old && DIR[old] && DIR[now]) {   // truot giua cac phim huong
                                release(old);
                                padPtr.put(id, now);
                                press(now);
                            }
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int id = ev.getPointerId(ix);
                    if (id == canvasPtr) {
                        canvasPtr = -1;
                        Native.post(Native.POINTER_RELEASE, gx(ev.getX(ix)), gy(ev.getY(ix)));
                    }
                    int cell = padPtr.get(id, -1);
                    if (cell >= 0) {
                        padPtr.delete(id);
                        release(cell);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    releaseAll();
                    return true;
                default:
                    return true;
            }
        }
    }
}
