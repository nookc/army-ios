package j2n;

import javax.microedition.midlet.MIDlet;

public final class Boot {
    private Boot() {
    }

    /**
     * v8h-31: anh lon nhat cua game (giai nen 15-150 ms tren dien thoai) giai nen san o luong nen ngay khi vao game - lan dau gap
     * boss / sanh boss khong bi khung hinh. Thu tu: sanh cho boss truoc (hien truoc), roi anh trong tran.
     */
    static final String[] ANH_SAN = {"lob/hd.png", "lob/hl.png", "lob/hlb.png", "lob/wf_hl.png", "lob/wf_hlb.png", "lob/rex_red.png",
        "lob/rex_ice.png", "hd/hd_boss.png", "hl/hl_boss.png", "hlb/hl_boss.png", "td/rex_red.png", "td/rex_ice.png", "hl/hl_wfire.png",
        "hlb/hl_wfire.png"};

    static void napAnhSan() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }
                long t0 = System.currentTimeMillis();
                javax.microedition.lcdui.Image.napSan(ANH_SAN);
                System.out.println("[Boot] giai nen san anh lon: " + (System.currentTimeMillis() - t0) + " ms");
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
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
        napAnhSan();
        Ui.loop();
    }
}
