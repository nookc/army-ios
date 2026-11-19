import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

/**
 * Ban JAR (J2ME, may cam ung): bam bong chat hoac nut "abc" tren ban phim ao -> mo thang o nhap chu cua dien thoai
 * (TextBox: J2ME Loader hien ban phim Android), khong hien khung T9 cua game. OK = gui chat, Huy = dong.
 * Ban app (APK / IPA) da co KbHook dung ban phim he thong nen lop nay khong lam gi o do.
 * Goi moi khung tu DauTruong.capNhat() (man ban do). Ket qua tu hop nhap xu ly o luong game (khong o luong su kien).
 */
public final class NhapChat implements CommandListener {

    private static int loai = -1;          // 1 = ban J2ME (khong co KbHook), 0 = ban app
    private static NhapChat mo;            // hop nhap dang mo
    private static boolean xong;           // nguoi choi da bam OK / Huy (cho luong game xu ly)
    private static boolean gui;
    private static String chu;

    private final TextBox tb;
    private final Command ok, huy;
    private final boolean tuAbc;           // mo tu nut abc (o chat chua mo): xong thi tra ban phim ao ve mui ten

    private NhapChat(String dau, boolean abc) {
        tuAbc = abc;
        tb = new TextBox("Chat", dau == null ? "" : dau, 100, TextField.ANY);
        ok = new Command("Gửi", Command.OK, 1);
        huy = new Command("Hủy", Command.BACK, 2);
        tb.addCommand(ok);
        tb.addCommand(huy);
        tb.setCommandListener(this);
    }

    static boolean j2me() {
        if (loai < 0) {
            try {
                Class.forName("KbHook");
                loai = 0;
            } catch (Throwable e) {
                loai = 1;
            }
        }
        return loai == 1;
    }

    public static void kiem() {
        if (!main.a.H || !j2me() || main.a.a == null || main.GameMidlet.h == null) {
            return;
        }
        if (mo != null) {
            if (xong) {
                ketThuc();
            }
            return;
        }
        b p = main.a.z;
        boolean chat = im.c && im.a != null;
        boolean abc = !chat && p != null && p.a && main.a.r == ae.b() && main.a.Z <= 0;
        if (!chat && !abc) {
            return;
        }
        try {
            String dau = chat && im.a.b != null ? im.a.b.f() : "";
            mo = new NhapChat(dau, abc);
            xong = false;
            Display.getDisplay(main.GameMidlet.h).setCurrent(mo.tb);
        } catch (Throwable e) {
            mo = null;
            loai = 0;                      // may khong mo duoc hop nhap: de nguyen ban phim cua game
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (xong) {
            return;
        }
        chu = tb.getString();
        gui = c == ok && chu != null && chu.trim().length() > 0;
        xong = true;
        try {
            Display.getDisplay(main.GameMidlet.h).setCurrent(main.a.a);
        } catch (Throwable e) {
        }
    }

    /** luong game: gui / dong o chat, het che do go chu */
    private static void ketThuc() {
        NhapChat n = mo;
        mo = null;
        xong = false;
        im m = im.d();
        if (gui) {
            if (m.d == null) {
                m.d = ae.b();
            }
            m.b.a(chu.trim());
            m.a(1, 0);                     // nut "Chat" cua o chat: gui roi dong
        }
        if (n.tuAbc) {
            if (im.c) {
                m.a(0, 0);
            }
            if (main.a.z != null) {
                main.a.z.a = false;        // ban phim ao ve lai mui ten (khong tat)
            }
        } else {
            m.a();                         // dong o chat nhu nut "Dong" (game tu tat ban phim go chu)
        }
        chu = null;
    }
}
