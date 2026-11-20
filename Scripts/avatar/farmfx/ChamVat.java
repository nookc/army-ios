/**
 * Nong trai cua minh: cham (khong keo) thang vao con vat -> mo menu con vat (Ban, cho an, thuoc...) ngay.
 * App goc chi cho nhan vat di toi cho cham roi phai doi con vat duoc chon + bam OK; ca boi trong ao thi
 * nhan vat dung tren bo, ca boi ra khoi tam chon la phim OK mo menu nong trai -> khong ban duoc ca.
 * PatchFarmFx goi ChamVat.cham() trong ir.a() ngay truoc khi dat diem di toi; tra ve true = da xu ly.
 */
public final class ChamVat {

    private ChamVat() {
    }

    public static boolean cham() {
        try {
            if (!(main.a.r instanceof go) || go.y || go.b != main.GameMidlet.i.w || go.i == null) {
                return false;
            }
            int x = ir.u.a, y = ir.u.b;
            int s = js.ay <= 0 ? 1 : js.ay;
            ai chon = null;
            long gan = Long.MAX_VALUE;
            for (int k = 0; k < go.i.size(); k++) {
                Object o = go.i.elementAt(k);
                if (!(o instanceof ai)) {
                    continue;
                }
                ai a = (ai) o;
                boolean ca = a instanceof ip;                                 // ca / rua trong ao
                int cao = a.aA > 0 ? a.aA : 16 * s;                           // aA = chieu cao khung hinh (diem man hinh)
                int cx = a.aw * s;
                int cy = a.ax * s - (ca ? cao / 6 : cao / 2);                 // ca ve tu tren xuong quanh diem, con tren can ve tu chan len
                int r = ca ? cao + 10 * s : cao * 3 / 4 + 8 * s;              // ca boi lien tuc: vung cham rong hon
                long dx = x - cx, dy = y - cy, d = dx * dx + dy * dy;
                if (d <= (long) r * r && d < gan) {
                    gan = d;
                    chon = a;
                }
            }
            if (chon == null) {
                return false;
            }
            ir.p = chon;
            ((go) main.a.r).a(2, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
