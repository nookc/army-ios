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
