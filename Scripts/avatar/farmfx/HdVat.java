import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Ban "Avatar 250 HD": 1 vat trang tri / cho chup anh do app tu them vao ban do (xem HdTrangTri). La 1 "vat the ban do"
 * (cc) nam trong danh sach ir.l nen game tu sap theo chieu sau: nguoi dung truoc vat thi che vat, dung sau thi bi vat che.
 * Ma anh f = 30000 (khong co anh nao cua game mang ma nay; cac cho game xet cc.f chi tim ma am / ma < 800).
 */
final class HdVat extends cc {
    Image im, im2;  // im2 != null: 2 khung doi nhau (lua nen)
    int lac;        // so dom lap lanh ve them tren vat
    boolean khongBong;
    int den;        // nguon sang ban dem: 0 khong, 1 cot den cao, 2 den nho / nen sat dat, 3 den treo cao vua

    HdVat(int x, int y, Image im, int lac) {
        super(30000, x, y, im == null ? 0 : im.getWidth());
        this.im = im;
        this.lac = lac;
        this.aA = (short) (im == null ? 0 : im.getHeight());
    }

    public void a(Graphics g) {
        try {
            if (im == null) {
                return;
            }
            int s = js.ay <= 0 ? 1 : js.ay;
            int x = aw * s, y = ax * s, w = im.getWidth();
            int cx = Cam.x();
            if (x + w / 2 < cx || x - w / 2 > cx + main.a.m) {
                return;
            }
            if (khongBong) {
                g.setStrokeStyle(0x4854);       // la rung, mang nhen...: nam sat dat / treo cao -> khong do bong
            }
            long now = System.currentTimeMillis();
            Image ve = im2 != null && ((now / 260 + aw) & 1) == 1 ? im2 : im;
            g.drawImage(ve, x, y, Graphics.BOTTOM | Graphics.HCENTER);
            if (den != 0 && ir.j == 1) {
                HdUi.den(g, den, x, y);
            }
            if (lac > 0) {
                Image sao = HdTrangTri.sao();
                if (sao != null) {
                    int h = im.getHeight();
                    for (int i = 0; i < lac; i++) {
                        // moi dom doi cho sau ~0,5 giay, sang / tat lech pha nhau
                        int nhip = (int) ((now + i * 173) / 520);
                        int r = (nhip * 7919 + i * 104729 + aw * 31) & 0x7FFFFFFF;
                        if ((r & 3) == 0) {
                            continue;
                        }
                        int py = 4 + (r >> 4) % (h > 12 ? h - 10 : 2);
                        int nua = w / 2 * (py + h / 6) / (h + h / 6);       // hinh non: tren hep duoi rong (cay) - cung hop voi vat khac
                        if (nua < 3) {
                            nua = 3;
                        }
                        int px = (r >> 12) % (2 * nua) - nua;
                        g.drawImage(sao, x + px, y - h + py, Graphics.VCENTER | Graphics.HCENTER);
                    }
                }
            }
        } catch (Throwable e) {
        }
    }
}
