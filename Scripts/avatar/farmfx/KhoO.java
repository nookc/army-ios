/**
 * Kho hang nong trai: o nao dung la o dang chon (dp.j trong tab dp.g) thi o do hien thong tin.
 * App goc so chi so cua o (vi tri trong go.f / go.e) voi o dang chon tru go.f.size(): danh sach thay doi khi kho
 * dang mo (vua ban, tu dong thu hoach them nong san moi...) la lech mot o -> chon Qua Khe lai hien "Tinh dau huong duong".
 * PatchFarmFx doi phep so do (j.a(), ko.a()) thanh KhoO.chon(this).
 */
public final class KhoO {

    private KhoO() {
    }

    public static boolean chon(Object o) {
        try {
            java.util.Vector v = dp.b().h[dp.g];
            return v != null && dp.j >= 0 && dp.j < v.size() && v.elementAt(dp.j) == o;
        } catch (Throwable t) {
            return false;
        }
    }
}
