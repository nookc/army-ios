import java.util.Vector;

import javax.microedition.lcdui.Image;

/**
 * Ban "Avatar 250 HD": vat trang tri / cho chup anh do app tu ve (anh x4 trong jar: hdx/p_<ten>.png), dat theo ban do va
 * theo chu de quan tri chon (HdUi.chuDe). Moi vat la 1 HdVat (vat the ban do) chen vao ir.l dung thu tu chieu sau.
 * Bang HdViTri.VT: {ma ban do, chu de (-1 = luc nao cung co, 0 = chi khi khong co chu de, 1..4 = chu de do), so anh, x, y}
 * (x, y = diem CHAN GIUA cua vat, don vi ban do: 1 o = 24).
 * capNhat() duoc goi moi khung (HdUi.nenTg): ban do / chu de doi, hoac game nap lai ban do (xoa ir.l) thi dat lai.
 */
public final class HdTrangTri {
    private HdTrangTri() {
    }

    static final String[] TEN = HdViTri.TEN;

    /** so dom lap lanh ve them tren vat (theo ten), 0 = khong */
    static int lapLanh(String ten) {
        if (ten.equals("cay_den")) {
            return 8;
        }
        if (ten.equals("cay_thong") || ten.equals("cong_tim")) {
            return 5;
        }
        if (ten.equals("dai_phun") || ten.equals("bi_to") || ten.equals("chu_love") || ten.equals("khung_anh") || ten.equals("qua_cau")) {
            return 3;
        }
        return 0;
    }

    private static Image[] kheAnh = new Image[5];
    private static boolean[] kheThu = new boolean[5];

    /** cay khe nong trai ve lai (bac 0 cay non .. 4 co thu ngan nam), null neu khong co anh */
    static Image khe(int bac) {
        if (bac < 0 || bac > 4) {
            bac = 0;
        }
        if (!kheThu[bac]) {
            kheThu[bac] = true;
            try {
                kheAnh[bac] = Image.createImage("/hdx/p_khe_" + bac + ".png");
            } catch (Throwable e) {
            }
        }
        return kheAnh[bac];
    }

    static Image sao;       // dom lap lanh
    private static boolean saoThu;

    static Image sao() {
        if (!saoThu) {
            saoThu = true;
            try {
                sao = Image.createImage("/hdx/p_lap_lanh.png");
            } catch (Throwable e) {
            }
        }
        return sao;
    }

    private static Image[] anh = new Image[TEN.length];
    private static boolean[] thu = new boolean[TEN.length];
    private static Vector dang = new Vector();      // cac HdVat dang nam trong ir.l
    private static int mapDang = -999, cdDang = -999;
    private static Object manDang;

    static Image lay(int i) {
        if (i < 0 || i >= TEN.length) {
            return null;
        }
        if (!thu[i]) {
            thu[i] = true;
            try {
                anh[i] = Image.createImage("/hdx/p_" + TEN[i] + ".png");
            } catch (Throwable e) {
                anh[i] = null;
            }
        }
        return anh[i];
    }

    private static void go() {
        try {
            Vector l = ir.l;
            if (l != null) {
                for (int i = 0; i < dang.size(); i++) {
                    l.removeElement(dang.elementAt(i));
                }
            }
        } catch (Throwable e) {
        }
        dang.removeAllElements();
    }

    public static void capNhat() {
        try {
            Vector l = ir.l;
            // chi cac man khu (ae) va nong trai (go): cac man khac co cho ep kieu thang phan tu ban do
            Object man = main.a.r;
            if (l == null || !(man instanceof ae || man instanceof go)) {
                return;
            }
            int map = ir.a, cd = HdUi.chuDe;
            boolean con = dang.size() == 0 || l.contains(dang.elementAt(0));
            if (map == mapDang && cd == cdDang && man == manDang && con) {
                return;
            }
            go();
            mapDang = map;
            cdDang = cd;
            manDang = man;
            int[] v = HdViTri.VT;
            for (int i = 0; i + 4 < v.length; i += 5) {
                if (v[i] != map || (v[i + 1] != -1 && v[i + 1] != cd)) {
                    continue;
                }
                Image im = lay(v[i + 2]);
                if (im == null) {
                    continue;
                }
                HdVat o = new HdVat(v[i + 3], v[i + 4], im, lapLanh(TEN[v[i + 2]]));
                if (TEN[v[i + 2]].equals("nen")) {
                    try {
                        o.im2 = Image.createImage("/hdx/p_nen_2.png");      // lua nen chap chon (2 khung)
                    } catch (Throwable e) {
                    }
                }
                int k = 0, n = l.size();
                while (k < n && ((js) l.elementAt(k)).ax <= o.ax) {
                    k++;
                }
                l.insertElementAt(o, k);
                dang.addElement(o);
            }
        } catch (Throwable e) {
        }
    }
}
