package javax.microedition.midlet;

public abstract class MIDlet {
    protected MIDlet() {
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() {
        j2n.Disp.exit();
    }

    public final void notifyPaused() {
    }

    public final void resumeRequest() {
    }

    public final String getAppProperty(String key) {
        return j2n.Res.appProperty(key);
    }

    public final boolean platformRequest(String url) throws javax.microedition.io.ConnectionNotFoundException {
        return j2n.Disp.platformRequest(url);
    }

    public final int checkPermission(String p) {
        return 1;
    }

    /* j2n.Boot goi (khac goi) */
    public static void start(MIDlet m) throws MIDletStateChangeException {
        m.startApp();
    }

    public static void pause(MIDlet m) {
        m.pauseApp();
    }

    public static void destroy(MIDlet m) throws MIDletStateChangeException {
        m.destroyApp(true);
    }
}
