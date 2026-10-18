package t;

import javax.microedition.midlet.MIDlet;

public class TestMidlet extends MIDlet {
    protected void startApp() {
        try {
            Suite.run(System.out);
        } catch (Throwable e) {
            System.out.println("LOI " + e);
        }
        System.out.flush();
        notifyDestroyed();
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean u) {
    }
}
