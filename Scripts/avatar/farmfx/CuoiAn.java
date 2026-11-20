/**
 * Le cuoi an toan (Trong le duong, ae.e / ae.k):
 *  - cho(chuRe, coDau): chi bat dau le khi ca chu re, co dau va Chu hon (-100) dang co trong khu. Truoc day app nhan
 *    le cuoi cua cap doi khong co mat van chay: lot do moi nguoi, chen phan tu rong vao danh sach nhan vat (ir.m)
 *    -> moi lan tim nhan vat (ir.g) deu loi -> giat lag tung khung hinh, nhan vat dung im.
 *  - kiem(): dang dien le ma chu re / co dau roi khu (thoat game, mat mang) -> dung le, tra lai binh thuong
 *    (truoc day loi tung khung hinh o buoc "hon co dau").
 */
public final class CuoiAn {

    private CuoiAn() {
    }

    static boolean co(int id) {
        try {
            return ir.g(id) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean cho(int chuRe, int coDau) {
        if (chuRe == coDau || !co(chuRe) || !co(coDau) || !co(-100)) {
            return false;
        }
        return true;
    }

    public static void kiem() {
        if (!ae.t) {
            return;
        }
        if (co(ae.E) && co(ae.F) && co(-100)) {
            return;
        }
        ae.t = false;
        java.util.Vector m = ir.m;
        if (m == null) {
            return;
        }
        for (int i = m.size() - 1; i >= 0; i--) {
            Object o = m.elementAt(i);
            if (o == null) {
                m.removeElementAt(i);        // phan tu rong (ban cu de lai) -> bo
            } else if (o instanceof hn) {
                ((hn) o).G = 4;
            }
        }
    }
}
