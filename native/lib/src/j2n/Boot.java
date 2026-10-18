package j2n;

import javax.microedition.midlet.MIDlet;

public final class Boot {
    private Boot() {
    }

    /** runtime goi tren luong chinh cua Java (khong phai luong UI cua nen tang) */
    public static void main(String midletClass) {
        System.out.println("[Boot] khoi dong " + midletClass);
        try {
            Class c = Class.forName(midletClass);
            MIDlet m = (MIDlet) c.newInstance();
            MIDlet.start(m);
        } catch (Throwable e) {
            System.err.println("[Boot] loi khoi dong: " + e);
        }
        Ui.loop();
    }
}
