import java.util.Vector;

/**
 * Dang ky ngay tai man dang nhap (J2ME + ban native).
 * Client 2.5.8 con nguyen form dang ky trong man dang nhap (es: Ten / Mat khau / Nhap lai / So di dong hoac email,
 * phim "Dang ky" -> dong y dieu khoan -> gui REGISTER_BY_EMAIL) nhung khong con loi nao mo duoc no.
 * PatchReg noi vao:
 *  - es.a(0) (Menu cua man dang nhap): them muc "Dang ky" len dau  -> RegHook.menu
 *  - es.d(0) (truoc day chi bat co i):  mo han form dang ky          -> RegHook.open
 * Server tra ve ma 2 {ten, mat khau}: client tu dien vao o dang nhap, bao "Dang ky thanh cong."; bam Chon -> Choi tiep.
 */
public final class RegHook {

    /** thay cho le.a().a(vector, n) trong es.a(0) */
    public static void menu(le l, Vector v, int n) {
        es e = es.a;
        if (e != null && main.a.r == e && !e.i) {
            v.insertElementAt(new by("Đăng ký tài khoản", 0), 0);
        }
        l.a(v, n);
    }

    /** es.d(0): mo form dang ky */
    public static void open(es e) {
        String u = e.b.f();
        String p = e.c.f();
        e.i = true;
        es.v = false;
        e.ac = new by("Đóng", new RegClose(e, u, p));
        e.b.a("");
        e.c.a("");
        e.d.a("");
        e.e.a("");
        e.z = 0;
        e.b.a(true);
        e.c.a(false);
        e.d.a(false);
        e.e.a(false);
        main.a.S.a(e);
    }
}

/** "Dong" khi dang o form dang ky: chua gui thi tra lai ten / mat khau cu, ve man Choi tiep / Choi moi */
final class RegClose implements ii {
    private final es e;
    private final String u;
    private final String p;

    RegClose(es e, String u, String p) {
        this.e = e;
        this.u = u;
        this.p = p;
    }

    public final void a() {
        if (e.i) {
            e.i = false; // chua gui dang ky -> tra lai nhu cu
            e.b.a(u);
            e.c.a(p);
        }
        e.d.a("");
        e.e.a("");
        main.a.S.a(e);
        String now = e.b.f();
        if (now.length() > 0 && !now.equals(u)) {
            e.a(1, 0); // vua dang ky xong: ve danh sach "Choi tiep, <ten moi>"
        } else {
            new bq(e).a();
        }
    }
}
