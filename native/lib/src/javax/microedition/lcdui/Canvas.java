package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    boolean fullScreen;

    protected Canvas() {
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    public boolean hasPointerEvents() {
        return true;
    }

    public boolean hasPointerMotionEvents() {
        return true;
    }

    public boolean hasRepeatEvents() {
        return false;
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP:
                return -1;
            case DOWN:
                return -2;
            case LEFT:
                return -3;
            case RIGHT:
                return -4;
            case FIRE:
                return -5;
            case GAME_A:
                return '1';
            case GAME_B:
                return '3';
            case GAME_C:
                return '7';
            case GAME_D:
                return '9';
            default:
                throw new IllegalArgumentException();
        }
    }

    public String getKeyName(int keyCode) {
        switch (getGameAction(keyCode)) {
            case UP:
                return "UP";
            case DOWN:
                return "DOWN";
            case LEFT:
                return "LEFT";
            case RIGHT:
                return "RIGHT";
            case FIRE:
                return "FIRE";
            default:
                break;
        }
        if (keyCode == -6) {
            return "SOFT1";
        }
        if (keyCode == -7) {
            return "SOFT2";
        }
        if (keyCode > 0) {
            return String.valueOf((char) keyCode);
        }
        return "KEY" + keyCode;
    }

    public int getGameAction(int keyCode) {
        if (keyCode == -1 || keyCode == '2') {
            return UP;
        }
        if (keyCode == -2 || keyCode == '8') {
            return DOWN;
        }
        if (keyCode == -3 || keyCode == '4') {
            return LEFT;
        }
        if (keyCode == -4 || keyCode == '6') {
            return RIGHT;
        }
        if (keyCode == -5 || keyCode == '5') {
            return FIRE;
        }
        if (keyCode == '1') {
            return GAME_A;
        }
        if (keyCode == '3') {
            return GAME_B;
        }
        if (keyCode == '7') {
            return GAME_C;
        }
        if (keyCode == '9') {
            return GAME_D;
        }
        return 0;
    }

    public void setFullScreenMode(boolean f) {
        fullScreen = f;
    }

    protected void keyPressed(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    public final void repaint(int x, int y, int w, int h) {
        j2n.Ui.repaint(this);
    }

    public final void repaint() {
        j2n.Ui.repaint(this);
    }

    public final void serviceRepaints() {
        j2n.Ui.serviceRepaints(this);
    }

    protected void showNotify() {
    }

    protected void hideNotify() {
    }

    protected abstract void paint(Graphics g);

    void showNotify0() {
        showNotify();
    }

    void hideNotify0() {
        hideNotify();
    }

    /* goi tu j2n.Ui (cung goi, khac goi: dung cau noi) */
    final void doPaint(Graphics g) {
        paint(g);
    }

    final void doKey(int type, int code) {
        if (type == 1) {
            keyPressed(code);
        } else if (type == 2) {
            keyReleased(code);
        } else {
            keyRepeated(code);
        }
    }

    final void doPointer(int type, int x, int y) {
        if (type == 3) {
            pointerPressed(x, y);
        } else if (type == 4) {
            pointerDragged(x, y);
        } else {
            pointerReleased(x, y);
        }
    }

    final void doSize(int w, int h) {
        sizeChanged(w, h);
    }
}
