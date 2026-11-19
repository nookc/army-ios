/**
 * Ban phim ao cua app (lop b, bat / tat bang nut tay cam nho o goc tren).
 * Cham vao vung ban phim thi KHONG bat co "vua cham man choi" (main.a.g).
 * Truoc day: ban phim chi xoa co main.a.e, con main.a.g van bat -> menu icon (cd.l) thay g ma khong trung icon nao
 * nen tu dong ngay khi bam mui ten tren ban phim ao (khong chon duoc Ban than / Giao dich...).
 * PatchFarmFx thay "main.a.g = true" trong main.a.pointerPressed bang KeyTap.g(x, y).
 */
public final class KeyTap {

    private KeyTap() {
    }

    /**
     * Cuoi b.a(Graphics) (ve ban phim ao): ghi chu cho cac o truoc day de trong - phim mem trai "F1", phim mem phai
     * "F2", phim giua "OK" (cung kieu chu voi o "abc"). Dang go chu (ban phim T9) thi khong ghi.
     */
    public static void nhan(b p, javax.microedition.lcdui.Graphics g) {
        if (p == null || p.a || p.u == null || p.l <= 0 || p.f <= 0 || p.g <= 0 || main.a.K == null) {
            return;
        }
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.setClip(p.b, p.c, p.d, p.e);
        for (int i = 0; i < p.u.length; i++) {
            int k = p.u[i];
            String s = k == -6 ? "F1" : k == -7 ? "F2" : k == -5 ? "OK" : null;
            if (s == null) {
                continue;
            }
            main.a.K.a(g, s, p.b + i % p.l * p.f + p.f / 2, p.c + i / p.l * p.g + p.g / 2 - 5, 2);
        }
        g.setClip(cx, cy, cw, ch);
    }

    public static void g(int x, int y) {
        if (main.a.z != null) {
            // man doc: ban phim nam duoi (y >= chieu cao man choi); man ngang: ban phim nam ben phai
            if (main.a.G ? x >= main.a.m : y >= main.a.n) {
                return;
            }
        }
        main.a.g = true;
    }
}
