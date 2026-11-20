/**
 * Gia luong cua mon do > 32.767: may chu gui short -32768 roi int gia that (chi khi app bao "2.5.8L" o lenh -17).
 * PatchFarmFx thay readShort() doc gia luong trong fx.a(byte[], boolean) (danh muc do -16 / -97) bang GiaLuong.doc(in).
 * May chu cu khong bao gio gui -32768 -> doc nhu cu.
 */
public final class GiaLuong {

    private GiaLuong() {
    }

    public static int doc(java.io.DataInputStream in) throws java.io.IOException {
        int v = in.readShort();
        return v == -32768 ? in.readInt() : v;
    }
}
