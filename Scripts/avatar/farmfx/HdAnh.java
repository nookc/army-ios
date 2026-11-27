/**
 * Avatar HD (lenh -17 thong tin may): byte "loai tai nguyen" goc la gy.Y - 1 (J2ME = 0, bo anh thuong). App native co ve
 * anh net cao (System.getProperty("j2n.hd") = "1") thi bao 1 -> may chu gui bo anh HD x2 (nhan vat, do, ban do, nha...) co
 * danh dau he so; app van chay toa do thuong (gy.Y = 1), chi ve net gap doi. Ban J2ME (J2ME Loader) khong co thuoc tinh do
 * -> giu nguyen 0.
 */
public final class HdAnh {

    private HdAnh() {
    }

    public static int loai(int goc) {
        try {
            if ("1".equals(System.getProperty("j2n.hd"))) {
                return 1;
            }
        } catch (Throwable e) {
        }
        return goc;
    }
}
