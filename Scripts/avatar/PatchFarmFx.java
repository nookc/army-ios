import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Va client Avatar 2.5.8 (ca ban J2ME lan ban native iOS / Android):
 *  1) main/a.pointerPressed: "main.a.g = true" -> KeyTap.g(x, y)  (cham ban phim ao khong con tinh la cham man choi
 *     -> menu icon khong tu dong khi bam mui ten)
 *  2) kc.a(Graphics) (cay khe): CayRong.sau() dau ham, ve anh cay qua CayRong.veCay() (doi mau theo cap), CayRong.truoc()
 *     truoc moi return (than thu bay quanh cay tu cap 100, 300, 600, 900)
 *  3) ir: ve tile ban do qua LandFx.oDat() (o dat cap 3..10: dong, bac, vang, ngoc... kim cuong cau vong)
 *  4) go: luat cap o dat (gieo hat / hinh dat / nang cap) doc qua LandFx.logic() / LandFx.dau() -> nang dat toi cap 10
 *  5) kz.a(jr): lenh 118 (cap that cua o dat) -> LandFx.tin()
 *  6) dh (phong chu): String.indexOf(char) -> KeyTap.chu(): chu hoa co dau khong co trong phong chu thi ve chu thuong
 *     (truoc day bien mat: "Ơi" -> " i")
 *  7) ea.b(Graphics) (ban do thanh pho): ve nhan khu qua KeyTap.nhanBanDo(): nhan kep mep khong chong len nhau
 *  8) dx.e(): KeyTap.oChu(this) (o chu dang chon, KbHook ban app biet o mat khau); dx.H mo public
 *  9) cy (danh sach co nhan vat: bang xep hang, ban be): dong cao hon (KeyTap.caoHang) + nhan vat cat theo o dong
 * 11) kd.l() (Tien len) / kg.l() (Phom): main.a.l() (lech doc khi keo) -> KeyTap.keoDoc(): keo ngang de doi cho la bai
 *     khong con bi "chon / bo chon" chen ngang khi ngon tay hoi xien (lan 34)
 * 12) ja (Bau Cua, lan 34): bam dat chip "az < 6" (6 lan / van) -> KeyTap.chipO(O, V): cuoc khong gioi han, moi o toi da
 *     127 chip (1 byte); tien bay giua 2 ghe (lenh 60) luu short (tren 32.767 hien sai) -> KeyTap.datTienBay / tienBay
 * 10) ae.a(...) / gt.a(): luoi do "chi xem" (cua hang 27 / 28 may chu gui khi xem ruong / do dang mac nguoi khac):
 *     nut "Chi tiết" thay "Mua", bam vao hien hop thong tin thay vi hoi Co / Khong; jx.a(): khung thong tin cua mon
 *     ghi dong may chu gui (KeyTap.thongTinXem) thay cho tien cua minh. Truong cua jx / gt mo public (KeyTap doc)
 * Dung: java -cp asm.jar:asm-tree.jar:. PatchFarmFx vao.jar ra.jar thu_muc_class_moi
 * (thu muc chua KeyTap.class, LandFx.class, CayRong.class da bien dich -target 1.3)
 */
public class PatchFarmFx {

    static int sua = 0;
    static final String G = "Ljavax/microedition/lcdui/Graphics;";

    public static void main(String[] a) throws Exception {
        Map<String, byte[]> moi = new LinkedHashMap<>();
        for (File f : Objects.requireNonNull(new File(a[2]).listFiles())) {
            if (f.getName().endsWith(".class")) {
                moi.put(f.getName(), java.nio.file.Files.readAllBytes(f.toPath()));
            }
        }
        for (String c : new String[]{"KeyTap.class", "LandFx.class", "CayRong.class", "TocDo.class", "DauTruong.class", "GiaLuong.class", "KhoO.class", "ChamVat.class", "CuoiAn.class"}) {
            if (!moi.containsKey(c)) {
                throw new IllegalStateException("thieu " + c);
            }
        }
        moi.put("Cam.class", taoCam());              // lop "do" (camera) trung tu khoa Java -> lop Cam goi ho
        Map<String, Integer> can = new HashMap<>();
        ZipInputStream zin = new ZipInputStream(new FileInputStream(a[0]));
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(a[1]));
        ZipEntry e;
        byte[] buf = new byte[1 << 16];
        while ((e = zin.getNextEntry()) != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int r;
            while ((r = zin.read(buf)) > 0) {
                bo.write(buf, 0, r);
            }
            byte[] data = bo.toByteArray();
            String name = e.getName();
            if (moi.containsKey(name)) {
                continue;                      // ban cu cua lop moi (chay lai patcher tren jar da va)
            }
            switch (name) {
                case "main/a.class": data = va(va(va(va(data, PatchFarmFx::keyTap, can, name), PatchFarmFx::tocDoVongLap, can, name), PatchFarmFx::veNoi, can, name), PatchFarmFx::hd12, can, name); data = va(data, PatchFarmFx::hdPhimAo, can, name); data = va(data, PatchFarmFx::hdDai, can, name); data = va(data, PatchFarmFx::hdHop, can, name); break;
                case "jj.class": data = va(data, PatchFarmFx::moTruong, can, name); break;
                case "le.class": data = va(data, PatchFarmFx::hdLe, can, name); break;
                case "im.class": data = va(data, PatchFarmFx::hdOChat, can, name); break;
                case "hj.class": data = va(data, PatchFarmFx::hdGoc, can, name); break;
                case "dm.class": data = va(data, PatchFarmFx::tocDoCaiDat, can, name); break;
                case "kc.class": data = va(data, PatchFarmFx::cayRong, can, name); break;
                case "ir.class": data = va(va(va(data, PatchFarmFx::oDat, can, name), PatchFarmFx::chamVat, can, name), PatchFarmFx::hdVeVat, can, name);
                    if (System.getProperty("goloi") != null) {
                        data = va(data, PatchFarmFx::goLoiIr, can, name);       // CHI ban thu (-Dgoloi=1): chen diem theo doi
                    }
                    break;
                case "go.class": data = va(va(va(va(data, PatchFarmFx::luatDat, can, name), PatchFarmFx::tromCay, can, name), PatchFarmFx::nhanChon, can, name), PatchFarmFx::chamDat, can, name); break;
                case "kz.class": data = va(data, PatchFarmFx::lenh118, can, name); break;
                case "b.class": data = va(data, PatchFarmFx::banPhim, can, name); break;
                case "ae.class": data = va(va(va(data, PatchFarmFx::dauTruongAe, can, name), PatchFarmFx::cuoiAn, can, name), PatchFarmFx::nutShop, can, name); break;
                case "hn.class": data = va(va(va(data, PatchFarmFx::dauTruongHn, can, name), PatchFarmFx::moTruong, can, name), PatchFarmFx::hdDi, can, name);
                    if (System.getProperty("goloi") != null) {
                        data = va(data, PatchFarmFx::goLoiHn, can, name);       // CHI ban thu
                    }
                    break;
                case "ce.class": data = va(data, PatchFarmFx::dauTruongCe, can, name); break;
                case "do.class": data = va(va(data, PatchFarmFx::camMo, can, name), PatchFarmFx::hdCamTruoc, can, name); break;
                case "gr.class": data = va(va(va(va(data, PatchFarmFx::moTruong, can, name), PatchFarmFx::hdThu, can, name), PatchFarmFx::thuAnToan, can, name), PatchFarmFx::thuMuot, can, name); break;
                case "fa.class": data = va(data, PatchFarmFx::thuMuot, can, name); break;
                case "ic.class":
                case "bb.class": data = va(data, PatchFarmFx::tatHuongDan, can, name); break;
                case "fx.class": data = va(data, PatchFarmFx::giaLuong, can, name); break;
                case "cx.class": data = va(data, PatchFarmFx::phienBan, can, name); break;
                case "j.class":
                case "ko.class": data = va(data, PatchFarmFx::oKho, can, name); break;
                case "dh.class": data = va(data, PatchFarmFx::chuHoa, can, name); break;
                case "ea.class": data = va(va(data, PatchFarmFx::nhanBanDo, can, name), PatchFarmFx::hdTp, can, name); break;
                case "dx.class": data = va(data, PatchFarmFx::oChu, can, name); break;
                case "cy.class": data = va(data, PatchFarmFx::danhSach, can, name); break;
                case "gt.class": data = va(va(data, PatchFarmFx::hoiShop, can, name), PatchFarmFx::moTruong, can, name); break;
                case "jx.class": data = va(va(va(data, PatchFarmFx::thongTinXem, can, name), PatchFarmFx::moTruong, can, name), PatchFarmFx::veO, can, name); break;
                case "fz.class": data = va(data, PatchFarmFx::veO, can, name); break;
                case "dp.class": data = va(data, PatchFarmFx::dauShop, can, name); break;
                case "ho.class": data = va(data, PatchFarmFx::hdChonO, can, name); break;
                case "kd.class":
                case "kg.class": data = va(data, PatchFarmFx::keoBai, can, name); break;
                case "ja.class": data = va(data, PatchFarmFx::bauCua, can, name); break;
                case "gw.class": data = va(data, PatchFarmFx::hdGw, can, name); break;
                case "jy.class":
                case "hb.class": data = va(data, PatchFarmFx::hdDay, can, name); break;
                case "gy.class": data = va(data, PatchFarmFx::hdGy, can, name); break;
                default: break;
            }
            if (name.equals("dx.class") || name.equals("hi.class") || name.equals("if.class") || name.equals("jb.class")
                    || name.equals("je.class") || name.equals("cz.class") || name.equals("dp.class") || name.equals("jj.class")
                    || name.equals("ki.class") || name.equals("gy.class")) {
                data = va(data, PatchFarmFx::hdKhung, can, name);        // giao dien 3D: khung hoi thoai / cua so / menu / phim mem
            }
            if (name.equals("aq.class")) {
                data = va(data, PatchFarmFx::hdCot5, can, name);         // mon nau an: vung cat "5 * dp.e" -> theo so cot moi
            }
            if (name.equals("hn.class") || name.equals("db.class")) {
                data = va(data, PatchFarmFx::hdLac, can, name);          // chuyen dong song cho canh / thu cung khong co hoat hinh
            }
            if (name.equals("ez.class")) {
                data = va(data, PatchFarmFx::tpEz, can, name);           // danh sach thanh pho dang mo (TpMo)
            }
            if (name.equals("es.class")) {
                data = va(data, PatchFarmFx::tpDangNhap, can, name);
            }
            if (name.equals("ae.class")) {
                data = va(data, PatchFarmFx::menuLenh, can, name);
            }
            if (name.equals("cz.class")) {
                data = va(data, PatchFarmFx::duaChip, can, name);
            }
            if (name.equals("dp.class")) {
                data = va(data, PatchFarmFx::hdBangTo, can, name);
            }
            if (name.equals("je.class")) {
                data = va(data, PatchFarmFx::hdOLuoi, can, name);
            }
            if (name.equals("kx.class")) {
                data = va(data, PatchFarmFx::hdPhongCho, can, name);
            }
            if (name.equals("gw.class")) {
                data = va(data, PatchFarmFx::hdGwTheme, can, name);
            }
            if (name.equals("hw.class")) {
                data = va(data, PatchFarmFx::hdQuay, can, name);
                data = va(data, PatchFarmFx::quayNhanh, can, name);
                data = va(data, PatchFarmFx::quayLai, can, name);
            }
            if (name.equals("main/a.class")) {
                data = va(data, PatchFarmFx::tpNoi, can, name);
            }
            if (name.equals("ae.class") || name.equals("cz.class") || name.equals("ei.class") || name.equals("es.class")
                    || name.equals("ez.class") || name.equals("go.class") || name.equals("ic.class")) {
                data = va(data, PatchFarmFx::hdTheGioi, can, name);     // cac man khu ve the gioi qua ir.b / ir.d
            }
            zo.putNextEntry(new ZipEntry(name));
            zo.write(data);
            zo.closeEntry();
        }
        zin.close();
        for (Map.Entry<String, byte[]> m : moi.entrySet()) {
            zo.putNextEntry(new ZipEntry(m.getKey()));
            zo.write(m.getValue());
            zo.closeEntry();
        }
        zo.close();
        System.out.println("PatchFarmFx: " + can);
        // dung so cho: 1 + 2 (sau + it nhat 1 truoc) + 1 + (2 dau + 3 logic) + 1
        boolean ok = can.getOrDefault("keytap", 0) == 1 && can.getOrDefault("rong_sau", 0) == 1 && can.getOrDefault("rong_truoc", 0) >= 1
                && can.getOrDefault("vecay", 0) == 1
                && can.getOrDefault("odat", 0) == 1 && can.getOrDefault("dau", 0) == 2 && can.getOrDefault("logic", 0) == 3
                && can.getOrDefault("l118", 0) == 1 && can.getOrDefault("banphim", 0) >= 1
                && can.getOrDefault("tocdo_khung", 0) == 2 && can.getOrDefault("tocdo_ve", 0) == 1 && can.getOrDefault("tocdo_ngu", 0) == 2 && can.getOrDefault("tocdo_loi", 0) >= 1
                && can.getOrDefault("tocdo_caidat", 0) == 1 && can.getOrDefault("tocdo_doi", 0) == 1
                && can.getOrDefault("dt_k", 0) >= 1 && can.getOrDefault("dt_nen", 0) == 1 && can.getOrDefault("dt_tren", 0) == 1
                && can.getOrDefault("dt_hud", 0) >= 1 && can.getOrDefault("dt_thay", 0) == 1 && can.getOrDefault("dt_sau", 0) == 1
                && can.getOrDefault("dt_tin", 0) == 1 && can.getOrDefault("cam_mo", 0) == 1 && can.getOrDefault("dt_ten", 0) == 1
                && can.getOrDefault("hd_tat", 0) == 2 && can.getOrDefault("hd_hoi", 0) == 1
                && can.getOrDefault("gia_luong", 0) == 1 && can.getOrDefault("phien_ban", 0) == 1
                && can.getOrDefault("loai_anh", 0) == 1
                && can.getOrDefault("hd_phim_ve", 0) == 1 && can.getOrDefault("hd_chat_ve", 0) == 1
                && can.getOrDefault("hd_map", 0) == 1 && can.getOrDefault("hd_vung", 0) == 2
                && can.getOrDefault("hd_tg_nen", 0) == 7 && can.getOrDefault("hd_tg_tren", 0) == 7
                && can.getOrDefault("hd_phim_mem", 0) == 1 && can.getOrDefault("hd_ve_vat", 0) == 1
                && can.getOrDefault("hd_lac_phan", 0) == 2 && can.getOrDefault("hd_lac_thu", 0) == 1 && can.getOrDefault("hd_lac_xem", 0) >= 1
                && can.getOrDefault("hd_ve_khac", 0) == 1
                && can.getOrDefault("hd_mail_ve", 0) == 1 && can.getOrDefault("hd_mail_chu", 0) == 1
                && can.getOrDefault("hd_phim_nao", 0) == 1 && can.getOrDefault("hd_12", 0) == 2
                && can.getOrDefault("hd_hop_l", 0) == 1 && can.getOrDefault("hd_hop_ve", 0) == 1
                && can.getOrDefault("hd_chon_o", 0) == 4
                && can.getOrDefault("hd_le", 0) >= 1 && can.getOrDefault("hd_ve_dong", 0) == 1
                && can.getOrDefault("hd_goc", 0) == 2 && can.getOrDefault("hd_tp", 0) == 1
                && can.getOrDefault("hd_phim_ao", 0) == 1 && can.getOrDefault("hd_o_chat", 0) >= 1
                && can.getOrDefault("hd_dai", 0) == 3
                && can.getOrDefault("hd_day_thanh", 0) == 2 && can.getOrDefault("hd_day_phim", 0) == 2
                && can.getOrDefault("hd_k_hop", 0) == 1 && can.getOrDefault("hd_k_khung", 0) == 5
                && can.getOrDefault("hd_k_menu", 0) == 4 && can.getOrDefault("hd_k_phim", 0) == 1
                && can.getOrDefault("trom_cay", 0) == 1 && can.getOrDefault("o_kho", 0) == 2
                && can.getOrDefault("cham_vat", 0) == 1 && can.getOrDefault("nhan_chon", 0) == 1
                && can.getOrDefault("cuoi_cho", 0) == 1 && can.getOrDefault("cuoi_kiem", 0) == 1
                && can.getOrDefault("chu_hoa", 0) == 2 && can.getOrDefault("nhan_bd", 0) == 1
                && can.getOrDefault("o_chu", 0) == 1 && can.getOrDefault("o_chu_h", 0) == 1
                && can.getOrDefault("ds_cao", 0) == 1 && can.getOrDefault("ds_cat", 0) == 1
                && can.getOrDefault("nut_shop", 0) == 1 && can.getOrDefault("hoi_shop", 0) == 2
                && can.getOrDefault("tt_xem", 0) == 1 && can.getOrDefault("mo_truong", 0) == 5
                && can.getOrDefault("hd_di_muot", 0) == 2 && can.getOrDefault("hd_di_buoc", 0) == 1 && can.getOrDefault("hd_thu", 0) == 2 && can.getOrDefault("thu_muot", 0) == 2 && can.getOrDefault("thu_muot_fa", 0) == 1
                && can.getOrDefault("tp_tin", 0) == 1 && can.getOrDefault("tp_nap", 0) == 2 && can.getOrDefault("tp_cap_nhat", 0) == 1
                && can.getOrDefault("tp_noi", 0) == 1 && can.getOrDefault("menu_lenh", 0) == 1 && can.getOrDefault("bang_o", 0) == 1 && can.getOrDefault("bang_y", 0) == 1 && can.getOrDefault("bc_ta_ve", 0) >= 1 && can.getOrDefault("cot5_khac", 0) == 2 && can.getOrDefault("quay_dong", 0) == 1 && can.getOrDefault("quay_lenh", 0) == 1 && can.getOrDefault("quay_tick", 0) == 1 && can.getOrDefault("quay_mo", 0) == 1 && can.getOrDefault("quay_dau", 0) == 1 && can.getOrDefault("quay_giam", 0) == 1 && can.getOrDefault("quay_bo", 0) == 1 && can.getOrDefault("bang_cot", 0) >= 8 && can.getOrDefault("phong_cho", 0) == 1 && can.getOrDefault("ghe_kx", 0) >= 2 && can.getOrDefault("ghe_ja", 0) >= 2 && can.getOrDefault("gw_khung", 0) == 1 && can.getOrDefault("gw_chon", 0) == 1 && can.getOrDefault("o_luoi", 0) == 1 && can.getOrDefault("o_chon", 0) >= 1 && can.getOrDefault("quay_giu", 0) == 1 && can.getOrDefault("quay_san", 0) == 2 && can.getOrDefault("quay_luc", 0) == 1 && can.getOrDefault("dua_chip", 0) == 1 && can.getOrDefault("dua_nhan", 0) == 1 && can.getOrDefault("tp_dang_nhap", 0) == 2 && can.getOrDefault("thu_cap", 0) == 1 && can.getOrDefault("thu_ngau", 0) >= 2
                && can.getOrDefault("hd_cam_truoc", 0) == 1 && can.getOrDefault("cham_dat", 0) >= 6
                && can.getOrDefault("dau_shop", 0) == 1 && can.getOrDefault("ve_o", 0) == 2
                && can.getOrDefault("ve_preview", 0) == 1 && can.getOrDefault("ve_noi", 0) == 1
                && can.getOrDefault("keo_bai", 0) == 2
                && can.getOrDefault("bc_chip", 0) == 2 && can.getOrDefault("bc_bay", 0) == 1 && can.getOrDefault("bc_bay_ve", 0) == 1
                && can.getOrDefault("bc_gui_ta", 0) == 1 && can.getOrDefault("bc_cung_o", 0) == 2;
        if (!ok) {
            System.out.println("PatchFarmFx: SO CHO SUA KHONG KHOP -> dung");
            new File(a[1]).delete();
            System.exit(1);
        }
    }

    // 6) dh: moi String.indexOf(I)I (tim chu trong bang phong chu) -> KeyTap.chu(String, int)
    static void chuHoa(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (m.getOpcode() == Opcodes.INVOKEVIRTUAL && m.owner.equals("java/lang/String") && m.name.equals("indexOf")
                            && m.desc.equals("(I)I")) {
                        MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "chu", "(Ljava/lang/String;I)I", false);
                        mn.instructions.set(m, moi);
                        n = moi;
                        dem(can, "chu_hoa");
                    }
                }
            }
        }
    }

    // 7) ea.b(Graphics): invokeinterface cs.a(Graphics,String,III)V -> invokestatic KeyTap.nhanBanDo(cs,Graphics,String,III)V
    static void nhanBanDo(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("b") || !mn.desc.equals("(" + G + ")V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (m.getOpcode() == Opcodes.INVOKEINTERFACE && m.owner.equals("cs") && m.name.equals("a")
                            && m.desc.equals("(" + G + "Ljava/lang/String;III)V")) {
                        MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "nhanBanDo",
                                "(Lcs;" + G + "Ljava/lang/String;III)V", false);
                        mn.instructions.set(m, moi);
                        n = moi;
                        dem(can, "nhan_bd");
                    }
                }
            }
        }
    }

    // 8) dx: truong H (kieu o: 2 = mat khau) public; dau e()V goi KeyTap.oChu(this)
    static void oChu(ClassNode cn, Map<String, Integer> can) {
        for (FieldNode f : cn.fields) {
            if (f.name.equals("H") && f.desc.equals("I")) {
                f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                dem(can, "o_chu_h");
            }
        }
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("e") || !mn.desc.equals("()V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            InsnList dau = new InsnList();
            dau.add(new VarInsnNode(Opcodes.ALOAD, 0));
            dau.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "oChu", "(Ldx;)V", false));
            mn.instructions.insert(dau);
            dem(can, "o_chu");
        }
    }

    // 9) cy: <init> "bipush 40 (* gy.Y) -> l" thanh KeyTap.caoHang(); a(Graphics): hn.a(Graphics,IIZ)V -> KeyTap.veHangAvatar
    static void danhSach(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<init>") && mn.desc.equals("()V")) {
                for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) n).operand == 40) {
                        AbstractInsnNode s1 = sau(n), s2 = s1 == null ? null : sau(s1), s3 = s2 == null ? null : sau(s2);
                        if (s1 instanceof FieldInsnNode && ((FieldInsnNode) s1).owner.equals("gy") && s2 != null
                                && s2.getOpcode() == Opcodes.IMUL && s3 instanceof FieldInsnNode
                                && ((FieldInsnNode) s3).owner.equals("cy") && ((FieldInsnNode) s3).name.equals("l")) {
                            MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "caoHang", "()I", false);
                            mn.instructions.set(n, moi);
                            n = moi;
                            dem(can, "ds_cao");
                        }
                    }
                }
            }
            if (mn.name.equals("a") && mn.desc.equals("(" + G + ")V")) {
                for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) n;
                        if (m.getOpcode() == Opcodes.INVOKEVIRTUAL && m.owner.equals("hn") && m.name.equals("a")
                                && m.desc.equals("(" + G + "IIZ)V")) {
                            MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "veHangAvatar",
                                    "(Lhn;" + G + "IIZ)V", false);
                            mn.instructions.set(m, moi);
                            n = moi;
                            dem(can, "ds_cat");
                        }
                    }
                }
            }
        }
    }

    // 10a) ae.a(BILjava/lang/String;[SI[Ljava/lang/String;)V: getstatic kf.Z (chu "Mua") -> iload_2 (ma cua hang); KeyTap.nutShop(I)
    static void nutShop(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(BILjava/lang/String;[SI[Ljava/lang/String;)V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("kf") && ((FieldInsnNode) n).name.equals("Z")) {
                    InsnList moi = new InsnList();
                    moi.add(new VarInsnNode(Opcodes.ILOAD, 2));
                    MethodInsnNode goi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "nutShop", "(I)Ljava/lang/String;", false);
                    moi.add(goi);
                    mn.instructions.insert(n, moi);
                    mn.instructions.remove(n);
                    n = goi;
                    dem(can, "nut_shop");
                }
            }
        }
    }

    // 10b) gt.a()V: main/a.a(String, ii) -> aload_0; getfield gt.e (ma cua hang); KeyTap.hoiShop(String, ii, I)
    static void hoiShop(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (m.getOpcode() == Opcodes.INVOKESTATIC && m.owner.equals("main/a") && m.name.equals("a")
                            && m.desc.equals("(Ljava/lang/String;Lii;)V")) {
                        InsnList moi = new InsnList();
                        moi.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        moi.add(new FieldInsnNode(Opcodes.GETFIELD, "gt", "e", "I"));
                        MethodInsnNode goi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "hoiShop", "(Ljava/lang/String;Lii;I)V", false);
                        moi.add(goi);
                        mn.instructions.insert(m, moi);
                        mn.instructions.remove(m);
                        n = goi;
                        dem(can, "hoi_shop");
                    }
                }
            }
        }
    }

    // dp.a(Graphics): sau BigMoney.a3(...) (dong tien cua minh dau luoi) -> KeyTap.dauShop(String);
    //                 hn.a(Graphics,IIZ)V (nhan vat xem truoc) -> KeyTap.vePreview(hn, Graphics, IIZ)
    static void dauShop(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(" + G + ")V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals("hn") && ((MethodInsnNode) n).name.equals("a")
                        && ((MethodInsnNode) n).desc.equals("(" + G + "IIZ)V") && n.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "vePreview", "(Lhn;" + G + "IIZ)V", false);
                    mn.instructions.set(n, moi);
                    n = moi;
                    dem(can, "ve_preview");
                    continue;
                }
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals("BigMoney") && ((MethodInsnNode) n).name.equals("a3")) {
                    MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "dauShop", "(Ljava/lang/String;)Ljava/lang/String;", false);
                    mn.instructions.insert(n, moi);
                    n = moi;
                    dem(can, "dau_shop");
                }
            }
        }
    }

    // jx / fz .a(Graphics,II)V (o luoi): ld.a(Graphics,III)V -> KeyTap.veO(ld, Graphics, III) (thu nho hinh to hon o)
    static void veO(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(" + G + "II)V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (m.getOpcode() == Opcodes.INVOKEVIRTUAL && m.owner.equals("ld") && m.name.equals("a")
                            && m.desc.equals("(" + G + "III)V")) {
                        MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "veO", "(Lld;" + G + "III)V", false);
                        mn.instructions.set(m, moi);
                        n = moi;
                        dem(can, "ve_o");
                    }
                }
            }
        }
    }

    // main/a.paint(Graphics): cp.a(Graphics)V (chu bay) -> KeyTap.veNoi(cp, Graphics): khong ve len hop thoai / menu
    static void veNoi(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("paint") || !mn.desc.equals("(" + G + ")V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("cp")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(" + G + ")V")) {
                    MethodInsnNode moi = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "veNoi", "(Lcp;" + G + ")V", false);
                    mn.instructions.set(n, moi);
                    n = moi;
                    dem(can, "ve_noi");
                }
            }
        }
    }

    // jx.a()V dau ham: if (KeyTap.thongTinXem(this)) return;
    static void thongTinXem(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("()V")) {
                continue;
            }
            InsnList dau = new InsnList();
            LabelNode tiep = new LabelNode();
            dau.add(new VarInsnNode(Opcodes.ALOAD, 0));
            dau.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "thongTinXem", "(Ljx;)Z", false));
            dau.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
            dau.add(new InsnNode(Opcodes.RETURN));
            dau.add(tiep);
            mn.instructions.insert(dau);
            dem(can, "tt_xem");
        }
    }

    // mo public moi truong (khong static) cua lop: KeyTap doc truc tiep
    // 11) kd.l() / kg.l(): INVOKESTATIC main/a.l()I (lech doc tu luc cham) -> KeyTap.keoDoc()I. Moi lop dung 1 cho.
    static void keoBai(ClassNode cn, Map<String, Integer> can) {
        int n0 = 0;
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("l") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) n;
                    if (m.getOpcode() == Opcodes.INVOKESTATIC && m.owner.equals("main/a") && m.name.equals("l") && m.desc.equals("()I")) {
                        m.owner = "KeyTap";
                        m.name = "keoDoc";
                        n0++;
                    }
                }
            }
        }
        if (n0 == 1) {
            dem(can, "keo_bai");
        } else {
            System.out.println("keoBai " + cn.name + ": " + n0 + " cho (can 1)");
        }
    }

    // 12) ja (Bau Cua): ALOAD x; GETFIELD ja.az B; BIPUSH 6; IF_ICMPGE L (l() va j()) -> ALOAD x; GETFIELD ja.O; ALOAD x;
    //     GETFIELD ja.V; INVOKESTATIC KeyTap.chipO(Vector,I)Z; IFEQ L. ja.a(BBI)V: I2S; PUTFIELD dn.o S -> KeyTap.datTienBay.
    //     ja.a(Graphics): GETFIELD dn.o S; StringBuffer.append(I) -> KeyTap.tienBay(dn) + append(String)
    static void bauCua(ClassNode cn, Map<String, Integer> can) {
        hdGhe(cn, can, "ja", "aK");
        for (MethodNode mn : cn.methods) {
            // 12d) "ta kho": cuoi ja.b(Graphics) ve chi dan luot ta + khung noi o da chon (ban goc chi nhap nhay 1 hinh chu nhat
            //      nam DUOI anh o nen gan nhu khong thay): HdUi.taVe(g, aE, f, T, U, R, S)
            if (mn.name.equals("b") && mn.desc.equals("(" + G + ")V")) {
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n.getOpcode() == Opcodes.RETURN) {
                        InsnList l = new InsnList();
                        l.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        for (String[] f : new String[][]{{"aE", "Z"}, {"f", "Z"}, {"T", "B"}, {"U", "B"}, {"R", "I"}, {"S", "I"}}) {
                            l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            l.add(new FieldInsnNode(Opcodes.GETFIELD, "ja", f[0], f[1]));
                        }
                        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "taVe", "(" + G + "ZZIIII)V", false));
                        mn.instructions.insertBefore(n, l);
                        dem(can, "bc_ta_ve");
                    }
                }
            }
            // 12b) "lag khong ta duoc" (lan 36): ja.x() (gui lenh ta khi bam o thu 2) bo qua neu hoat canh ta cua nguoi truoc
            //      con chay (ja.aF) - mang lag / may cham thi luot minh toi cung luc hoat canh -> bam nhanh 2 o la mat luot
            //      (U da gan, khong chon lai duoc, het gio tu bo luot). Bo dieu kien aF: luon gui (may chu quyet).
            if (mn.name.equals("x") && mn.desc.equals("()V")) {
                for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).owner.equals("ja") && ((FieldInsnNode) n).name.equals("aF")
                            && n.getPrevious() != null && n.getPrevious().getOpcode() == Opcodes.ALOAD
                            && n.getNext() != null && n.getNext().getOpcode() == Opcodes.IFNE) {
                        AbstractInsnNode truoc = n.getPrevious(), nhay = n.getNext();
                        mn.instructions.remove(truoc);
                        mn.instructions.remove(nhay);
                        mn.instructions.remove(n);
                        dem(can, "bc_gui_ta");
                        break;
                    }
                }
                continue;
            }
            // 12c) bam o thu 2 trung o thu 1 (cham dup / tay rung) truoc day gui ta o X -> X: may chu bo luot. Nay bo qua lan
            //      bam do, cho bam o khac. Mau (l, j): ALOAD r; ALOAD r; GETFIELD V; PUTFIELD U; ALOAD r; INVOKESPECIAL x
            //      -> chen truoc: ALOAD r; GETFIELD V; ALOAD r; GETFIELD T; IF_ICMPEQ sau_x
            if ((mn.name.equals("l") || mn.name.equals("j")) && mn.desc.equals("()V")) {
                for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                    if (n.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) n).owner.equals("ja") && ((MethodInsnNode) n).name.equals("x")) {
                        AbstractInsnNode a5 = n.getPrevious(), u = a5 == null ? null : a5.getPrevious(),
                                v = u == null ? null : u.getPrevious(), a2 = v == null ? null : v.getPrevious(),
                                a1 = a2 == null ? null : a2.getPrevious();
                        if (a5 instanceof VarInsnNode && u instanceof FieldInsnNode && ((FieldInsnNode) u).name.equals("U")
                                && u.getOpcode() == Opcodes.PUTFIELD && v instanceof FieldInsnNode && ((FieldInsnNode) v).name.equals("V")
                                && a2 instanceof VarInsnNode && a1 instanceof VarInsnNode) {
                            int r = ((VarInsnNode) a1).var;
                            LabelNode bo = new LabelNode();
                            InsnList chen = new InsnList();
                            chen.add(new VarInsnNode(Opcodes.ALOAD, r));
                            chen.add(new FieldInsnNode(Opcodes.GETFIELD, "ja", "V", "B"));
                            chen.add(new VarInsnNode(Opcodes.ALOAD, r));
                            chen.add(new FieldInsnNode(Opcodes.GETFIELD, "ja", "T", "B"));
                            chen.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, bo));
                            mn.instructions.insertBefore(a1, chen);
                            mn.instructions.insert(n, bo);
                            dem(can, "bc_cung_o");
                            break;
                        }
                    }
                }
            }
            for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).owner.equals("ja") && ((FieldInsnNode) n).name.equals("az")) {
                    AbstractInsnNode truoc = n.getPrevious(), sau = n.getNext(), nhay = sau == null ? null : sau.getNext();
                    if (truoc instanceof VarInsnNode && truoc.getOpcode() == Opcodes.ALOAD && sau instanceof IntInsnNode
                            && ((IntInsnNode) sau).operand == 6 && nhay != null && nhay.getOpcode() == Opcodes.IF_ICMPGE) {
                        int x = ((VarInsnNode) truoc).var;
                        FieldInsnNode f = (FieldInsnNode) n;
                        f.name = "O";
                        f.desc = "Ljava/util/Vector;";
                        InsnList them = new InsnList();
                        them.add(new VarInsnNode(Opcodes.ALOAD, x));
                        them.add(new FieldInsnNode(Opcodes.GETFIELD, "ja", "V", "B"));
                        them.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "chipO", "(Ljava/util/Vector;I)Z", false));
                        mn.instructions.insert(sau, them);
                        mn.instructions.remove(sau);
                        ((JumpInsnNode) nhay).setOpcode(Opcodes.IFEQ);
                        n = nhay;
                        dem(can, "bc_chip");
                        continue;
                    }
                }
                if (n.getOpcode() == Opcodes.PUTFIELD && ((FieldInsnNode) n).owner.equals("dn") && ((FieldInsnNode) n).name.equals("o")
                        && mn.desc.equals("(BBI)V") && n.getPrevious() != null && n.getPrevious().getOpcode() == Opcodes.I2S) {
                    mn.instructions.remove(n.getPrevious());
                    MethodInsnNode m = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "datTienBay", "(Ldn;I)V", false);
                    mn.instructions.set(n, m);
                    n = m;
                    dem(can, "bc_bay");
                    continue;
                }
                if (n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).owner.equals("dn") && ((FieldInsnNode) n).name.equals("o")
                        && n.getNext() instanceof MethodInsnNode && ((MethodInsnNode) n.getNext()).owner.equals("java/lang/StringBuffer")
                        && ((MethodInsnNode) n.getNext()).name.equals("append") && ((MethodInsnNode) n.getNext()).desc.equals("(I)Ljava/lang/StringBuffer;")) {
                    ((MethodInsnNode) n.getNext()).desc = "(Ljava/lang/String;)Ljava/lang/StringBuffer;";
                    MethodInsnNode m = new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "tienBay", "(Ldn;)Ljava/lang/String;", false);
                    mn.instructions.set(n, m);
                    n = m;
                    dem(can, "bc_bay_ve");
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): gw.a(Graphics) ve nut ban phim ao hj.ap va bong chat hj.aq -> qua HdUi.vePhimAo / veChat;
     * gw.a(int map) tu bat ban phim ao o khu cau ca -> doi tham so qua HdUi.mapPhim (khong them nhanh moi).
     */
    static void hdGw(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("a") && mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")) {
                String dang = null;
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("hj")) {
                        String f = ((FieldInsnNode) n).name;
                        dang = f.equals("ap") ? "vePhimAo" : f.equals("aq") ? "veChat" : dang;
                    }
                    if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("hj")
                            && ((FieldInsnNode) n).name.equals("ao")) {
                        dang = "veMail";
                    }
                    if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEINTERFACE && ((MethodInsnNode) n).owner.equals("cs")
                            && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Graphics;Ljava/lang/String;III)V")) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "chuMail",
                                "(Lcs;Ljavax/microedition/lcdui/Graphics;Ljava/lang/String;III)V", false));
                        dem(can, "hd_mail_chu");
                        continue;
                    }
                    if (dang != null && n instanceof MethodInsnNode && ((MethodInsnNode) n).name.equals("drawImage")
                            && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Image;III)V")) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", dang,
                                "(Ljavax/microedition/lcdui/Graphics;Ljavax/microedition/lcdui/Image;III)V", false));
                        dem(can, dang.equals("vePhimAo") ? "hd_phim_ve" : dang.equals("veMail") ? "hd_mail_ve" : "hd_chat_ve");
                        dang = null;
                    }
                }
            }
            if (mn.name.equals("a") && mn.desc.equals("(I)V")) {
                InsnList il = new InsnList();
                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "mapPhim", "(I)I", false));
                il.add(new VarInsnNode(Opcodes.ISTORE, 1));
                mn.instructions.insert(il);
                dem(can, "hd_map");
            }
        }
    }

    /**
     * Ban HD (HdUi): moi man khu ve the gioi bang ir.b(g) (nen) ... ir.d(g) (lop tren). Doi 2 cho goi do sang
     * HdUi.nenTg / HdUi.trenTg (cung tham so tren ngan xep: doi tuong ir + Graphics) de may ve biet doan ve the gioi.
     */
    static void hdTheGioi(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("ir")
                        && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")) {
                    String ten = ((MethodInsnNode) n).name;
                    if (ten.equals("b") || ten.equals("d")) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", ten.equals("b") ? "nenTg" : "trenTg",
                                "(Lir;Ljavax/microedition/lcdui/Graphics;)V", false));
                        dem(can, ten.equals("b") ? "hd_tg_nen" : "hd_tg_tren");
                    }
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): hn.a(Graphics) ve tung mon do bang ld.a(Graphics, IIII) -> HdUi.vePhan; db.a(Graphics) (thu cung di
     * theo) ve bang ld.a(Graphics, III) -> HdUi.veThu. Cung tham so tren ngan xep (doi tuong ld o dau).
     */
    static void hdLac(ClassNode cn, Map<String, Integer> can) {
        final String G = "Ljavax/microedition/lcdui/Graphics;";
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            boolean chinh = mn.name.equals("a") && mn.desc.equals("(" + G + ")V");
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEVIRTUAL || !((MethodInsnNode) n).owner.equals("ld")
                        || !((MethodInsnNode) n).name.equals("a")) {
                    continue;
                }
                String d = ((MethodInsnNode) n).desc;
                if (cn.name.equals("hn") && d.equals("(" + G + "IIII)V")) {
                    if (chinh) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "vePhan", "(Lld;" + G + "IIII)V", false));
                        dem(can, "hd_lac_phan");
                    } else {
                        // hinh nhan vat ve o noi khac (bang "Ban than", phong game...): boc an toan - 1 mon loi khong lam mat ca khung
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "vePhanAn", "(Lld;" + G + "IIII)V", false));
                        dem(can, "hd_lac_xem");
                    }
                } else if (chinh && cn.name.equals("db") && d.equals("(" + G + "III)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "veThu", "(Lld;" + G + "III)V", false));
                    dem(can, "hd_lac_thu");
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): hj.a(Graphics) ve nhan o goc phai tren (phien ban "2.5.8" luc chua dang nhap / luu luong kt.k) bang
     * main.a.M tai main.a.af -> HdUi.chuGoc (ban HD lui vao trong le man hinh: goc man hinh dien thoai bo tron)
     */
    static void hdGoc(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEINTERFACE && ((MethodInsnNode) n).owner.equals("cs")
                        && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Graphics;Ljava/lang/String;III)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "chuGoc",
                            "(Lcs;Ljavax/microedition/lcdui/Graphics;Ljava/lang/String;III)V", false));
                    dem(can, "hd_goc");
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): ea = man BAN DO THANH PHO. ea.a(Graphics) goi this.b(g) ve ca ban do (o gach, bieu tuong khu, may, ten
     * khu) -> HdUi.veTp(ea, g): ve xong thi cho may ve hau ky canh (bong may troi, nuoc lap lanh, nang, toi goc)
     */
    static void hdTp(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("ea")
                        && ((MethodInsnNode) n).name.equals("b") && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "veTp",
                            "(Lea;Ljavax/microedition/lcdui/Graphics;)V", false));
                    dem(can, "hd_tp");
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): main.a.b() tao ban phim ao (z = new b()) luc vao che do go chu (bam bong chat). Man NGANG thi ban phim
     * ao chiem 1/3 ben phai va game co lai (main.a.m = m - m/3 - 1) - ban HD dung ban phim dien thoai nen ngay sau lenh
     * putstatic main.a.z goi HdUi.sauPhimAo(): tra lai be rong day du, cho vung ban phim ao ve 0 (khong nuot cham).
     */
    static void hdPhimAo(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("b") || !mn.desc.equals("()V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) n).owner.equals("main/a")
                        && ((FieldInsnNode) n).name.equals("z") && n.getPrevious() instanceof MethodInsnNode
                        && ((MethodInsnNode) n.getPrevious()).name.equals("<init>")) {
                    mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "sauPhimAo", "()V", false));
                    dem(can, "hd_phim_ao");
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): im.b() dat vi tri o chat (im.b: y = q - T - cao - 5, rong = m - 10). Truoc moi lenh return goi
     * HdUi.oChat(this): lui o vao trong le man hinh (tai tho) + nang len theo phim mem.
     */
    static void hdOChat(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("b") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "oChat", "(Lim;)V", false));
                mn.instructions.insertBefore(n, l);
                dem(can, "hd_o_chat");
            }
        }
    }

    /**
     * Ban HD (HdUi): main.a.paint ve DAI CHU CHAY thong bao o mep tren: lat anh nen main.a.aa (drawImage) + vach ke (fillRect)
     * + dat vung cat cho dong chu (setClip) -> HdUi.veDai / vachDai / clipDai (ban HD: khong nen toi, chu chay trong khoang
     * trong giua 2 cum nut tren)
     */
    static void hdDai(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("paint") || !mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")) {
                continue;
            }
            int buoc = 0;
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (buoc == 0) {
                    if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("main/a")
                            && ((FieldInsnNode) n).name.equals("aa")) {
                        buoc = 1;
                    }
                    continue;
                }
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !((MethodInsnNode) n).owner.equals("javax/microedition/lcdui/Graphics")) {
                    continue;
                }
                MethodInsnNode m = (MethodInsnNode) n;
                if (buoc == 1 && m.name.equals("drawImage") && m.desc.equals("(Ljavax/microedition/lcdui/Image;III)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "veDai",
                            "(Ljavax/microedition/lcdui/Graphics;Ljavax/microedition/lcdui/Image;III)V", false));
                    buoc = 2;
                    dem(can, "hd_dai");
                } else if (buoc == 2 && m.name.equals("fillRect") && m.desc.equals("(IIII)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "vachDai",
                            "(Ljavax/microedition/lcdui/Graphics;IIII)V", false));
                    buoc = 3;
                    dem(can, "hd_dai");
                } else if (buoc == 3 && m.name.equals("setClip") && m.desc.equals("(IIII)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "clipDai",
                            "(Ljavax/microedition/lcdui/Graphics;IIII)V", false));
                    buoc = 4;
                    dem(can, "hd_dai");
                }
            }
        }
    }

    /** Ban HD (HdUi): main.a.a(Graphics) (static) ve nhan "12+" -> HdUi.ve12 (ban HD doi xuong goc trai duoi) */
    static void hd12(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            // main.a.a(Graphics) (man the gioi) va main.a.b(Graphics) (man kieu OnGame: sanh, phong cho, ban choi) deu ve nhan 12+
            if ((!mn.name.equals("a") && !mn.name.equals("b")) || !mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")
                    || (mn.access & Opcodes.ACC_STATIC) == 0) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).name.equals("drawImage")
                        && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Image;III)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "ve12",
                            "(Ljavax/microedition/lcdui/Graphics;Ljavax/microedition/lcdui/Image;III)V", false));
                    dem(can, "hd_12");
                }
            }
        }
    }

    /** CHI ban thu: chen HdUi.goDiem2(ma) truoc moi lenh goi ham / doc mang trong hn.b() (ma = thu tu) de tim cho nem ngoai le */
    static void goLoiHn(ClassNode cn, Map<String, Integer> can) {
        String ten = System.getProperty("goham", "b");
        String kieu = System.getProperty("gokieu", "()V");
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals(ten) || !mn.desc.equals(kieu)) {
                continue;
            }
            int thu = 0;
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                int op = n.getOpcode();
                boolean goi = n instanceof MethodInsnNode;
                boolean mang = op == Opcodes.SALOAD || op == Opcodes.BALOAD || op == Opcodes.IALOAD || op == Opcodes.AALOAD
                        || op == Opcodes.CALOAD || op == Opcodes.LALOAD;
                if (!goi && !mang) {
                    continue;
                }
                thu++;
                InsnList l = new InsnList();
                l.add(new IntInsnNode(Opcodes.SIPUSH, thu));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "goDiem2", "(I)V", false));
                if (goi) {
                    // truoc lenh goi thi ngan xep da co tham so -> chen SAU lenh goi (ma = lenh goi thu may vua xong)
                    mn.instructions.insert(n, l);
                } else {
                    mn.instructions.insert(n, l);
                }
                System.out.println("  [goham] " + thu + " " + (goi ? ((MethodInsnNode) n).owner + "." + ((MethodInsnNode) n).name
                        + ((MethodInsnNode) n).desc : "mang op " + op));
            }
        }
        dem(can, "go_loi_hn");
    }

    /** CHI ban thu: chen HdUi.goDiem(ma) vao ir.b() (truoc go.N, truoc moi lan goi ir.i) va dau ir.i(int) de theo doi viec chon vat */
    static void goLoiIr(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("b") && mn.desc.equals("()V")) {
                {
                    InsnList l0 = new InsnList();
                    l0.add(new IntInsnNode(Opcodes.SIPUSH, 3000));
                    l0.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "goDiem", "(I)V", false));
                    mn.instructions.insert(l0);
                }
                int thu = 0;
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    // truoc moi lan doc danh sach vat (m / n / l) va truoc khi doc main.a.l: danh dau tien do
                    if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((((FieldInsnNode) n).owner.equals("main/a")
                            && ((FieldInsnNode) n).name.equals("l")) || (((FieldInsnNode) n).owner.equals("ir")
                            && ((FieldInsnNode) n).desc.equals("Ljava/util/Vector;")))) {
                        thu++;
                        if (thu <= 40) {
                            InsnList l1 = new InsnList();
                            l1.add(new IntInsnNode(Opcodes.SIPUSH, 3000 + thu));
                            l1.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "goDiem", "(I)V", false));
                            mn.instructions.insertBefore(n, l1);
                        }
                    }
                    if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("go")
                            && ((FieldInsnNode) n).name.equals("N")) {
                        InsnList l = new InsnList();
                        l.add(new IntInsnNode(Opcodes.SIPUSH, 1000));
                        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "goDiem", "(I)V", false));
                        mn.instructions.insertBefore(n, l);
                    }
                    if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("ir")
                            && ((MethodInsnNode) n).name.equals("i") && ((MethodInsnNode) n).desc.equals("(I)Z")) {
                        InsnList l = new InsnList();
                        l.add(new IntInsnNode(Opcodes.SIPUSH, 2000));
                        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "goDiem", "(I)V", false));
                        mn.instructions.insertBefore(n.getPrevious(), l);
                    }
                }
            }
            if (mn.name.equals("i") && mn.desc.equals("(I)Z")) {
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ILOAD, 0));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "goDiem", "(I)V", false));
                mn.instructions.insert(l);
            }
        }
        dem(can, "go_loi");
    }

    /**
     * Ban HD (HdUi): luoi mon cua bang cua hang - "chon do kho", "keo tim do khong duoc". Bo cuon chung (ho.a()) trong luc ngon
     * tay con de tren luoi thi moi vong goi man hinh.a(o duoi ngon tay, false); dp.a(o, false) gap o DANG CHON la chay luon
     * phim giua "Mua" -> cham vao mon nao (giu qua 1 vong ~50 ms) hay keo de cuon cung bat ra hop hoi mua. Doi moi cho goi
     * hj.a(IZ)V trong ho.a() sang HdUi.chonO: ban HD + bang cua hang + dang giu tay thi chi CHON mon (va khong doi mon khi dang
     * keo de cuon); mua bang nut "Mua". Ban thuong / man khac: nhu goc.
     */
    static void hdChonO(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("hj")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(IZ)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "chonO", "(Lhj;IZ)V", false));
                    dem(can, "hd_chon_o");
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): hop thoai dang mo (main.a.v) - main.a.paint goi v.a(g), vong lap goi v.l(). Doi 2 cho goi do sang
     * HdUi.hopVe(gy, Graphics) / HdUi.hopL(gy): hop thong bao / hop hoi chuan (jj) ve kieu moi nho gon + nut bam thang.
     */
    static void hdHop(ClassNode cn, Map<String, Integer> can) {
        final String G = "Ljavax/microedition/lcdui/Graphics;";
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEVIRTUAL || !((MethodInsnNode) n).owner.equals("gy")) {
                    continue;
                }
                MethodInsnNode m = (MethodInsnNode) n;
                AbstractInsnNode p1 = n.getPrevious();
                while (p1 != null && p1.getOpcode() < 0) {
                    p1 = p1.getPrevious();
                }
                if (m.name.equals("l") && m.desc.equals("()V") && laTruongV(p1)) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "hopL", "(Lgy;)V", false));
                    dem(can, "hd_hop_l");
                } else if (m.name.equals("a") && m.desc.equals("(" + G + ")V") && p1 != null && p1.getOpcode() == Opcodes.ALOAD) {
                    AbstractInsnNode p2 = p1.getPrevious();
                    while (p2 != null && p2.getOpcode() < 0) {
                        p2 = p2.getPrevious();
                    }
                    if (laTruongV(p2)) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "hopVe", "(Lgy;" + G + ")V", false));
                        dem(can, "hd_hop_ve");
                    }
                }
            }
        }
    }

    private static boolean laTruongV(AbstractInsnNode n) {
        return n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("main/a")
                && ((FieldInsnNode) n).name.equals("v") && ((FieldInsnNode) n).desc.equals("Lgs;");
    }

    /**
     * Ban HD (HdUi): le.a(Vector, int kieu) dat vi tri bang menu noi cua phim mem (a = x, b = m = y). Truoc moi lenh return:
     * a = HdUi.menuX(a, kieu, c); b = HdUi.menuY(b, kieu); m = b  (ban HD: bang ben trai xo xuong duoi nut MENU o goc tren)
     */
    static void hdLe(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(Ljava/util/Vector;I)V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "le", "a", "I"));
                l.add(new VarInsnNode(Opcodes.ILOAD, 2));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "le", "c", "I"));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "menuX", "(III)I", false));
                l.add(new FieldInsnNode(Opcodes.PUTFIELD, "le", "a", "I"));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "le", "b", "I"));
                l.add(new VarInsnNode(Opcodes.ILOAD, 2));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "menuY", "(II)I", false));
                l.add(new FieldInsnNode(Opcodes.PUTFIELD, "le", "b", "I"));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "le", "b", "I"));
                l.add(new FieldInsnNode(Opcodes.PUTFIELD, "le", "m", "I"));
                mn.instructions.insertBefore(n, l);
                dem(can, "hd_le");
            }
        }
    }

    /**
     * Ban HD (HdUi): ir.d(Graphics) tron 3 danh sach da sap theo chieu sau roi ve tung vat bang js.a(g). Cho ve vat the tinh
     * (truong W, lay tu ir.l): getfield W; aload; invokevirtual js.a(Graphics) -> invokestatic HdUi.veVat(js, Graphics).
     */
    static void hdVeVat(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("d") || !mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("js")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(Ljavax/microedition/lcdui/Graphics;)V")) {
                    AbstractInsnNode p1 = n.getPrevious();
                    while (p1 != null && p1.getOpcode() < 0) {
                        p1 = p1.getPrevious();
                    }
                    AbstractInsnNode p2 = p1 == null ? null : p1.getPrevious();
                    while (p2 != null && p2.getOpcode() < 0) {
                        p2 = p2.getPrevious();
                    }
                    if (p2 instanceof FieldInsnNode && p2.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) p2).name.equals("W")
                            && ((FieldInsnNode) p2).desc.equals("Ljs;")) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "veVat",
                                "(Ljs;Ljavax/microedition/lcdui/Graphics;)V", false));
                        dem(can, "hd_ve_vat");
                    } else if (p2 instanceof FieldInsnNode && p2.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) p2).name.equals("V")
                            && ((FieldInsnNode) p2).desc.equals("Ljs;")) {
                        // vat the dong (truong V, lay tu ir.m) -> HdUi.veDong: bao duong chan de do bong
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "veDong",
                                "(Ljs;Ljavax/microedition/lcdui/Graphics;)V", false));
                        dem(can, "hd_ve_dong");
                    } else if (p2 instanceof FieldInsnNode && p2.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) p2).name.equals("X")
                            && ((FieldInsnNode) p2).desc.equals("Ljs;")) {
                        // danh sach thu 3 (truong X, lay tu ir.n) -> HdUi.veKhac: chi boc an toan (1 vat loi khong lam mat ca khung)
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "veKhac",
                                "(Ljs;Ljavax/microedition/lcdui/Graphics;)V", false));
                        dem(can, "hd_ve_khac");
                    }
                }
            }
        }
    }

    /**
     * Ban HD (HdUi) - giao dien 3D: cac cho goi ham ve khung cua giao dien (invokeinterface ew.a voi 4 kieu tham so) doi sang
     * ham tinh cua HdUi nhan them doi tuong ew o dau (cung ngan xep) - HdUi ve bong do + lop bong sang quanh ham goc.
     */
    static void hdKhung(ClassNode cn, Map<String, Integer> can) {
        final String G = "Ljavax/microedition/lcdui/Graphics;";
        String[][] bang = {
            {"(" + G + "IIIILdx;Z)V", "hopThoai", "hd_k_hop"},
            {"(" + G + "IIIIIII)V", "khung7", "hd_k_khung"},
            {"(" + G + "IIIIIIIIIII[I[ILjava/lang/String;)V", "menu", "hd_k_menu"},
            {"(" + G + "Lby;Lby;Lby;)V", "phimMem", "hd_k_phim"},
        };
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEINTERFACE) {
                    continue;
                }
                MethodInsnNode m = (MethodInsnNode) n;
                if (!m.owner.equals("ew") || !m.name.equals("a")) {
                    continue;
                }
                for (String[] b : bang) {
                    if (m.desc.equals(b[0])) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", b[1], "(Lew;" + b[0].substring(1), false));
                        dem(can, b[2]);
                    }
                }
            }
        }
    }

    /**
     * Ban HD (HdUi): cac man kieu OnGame (sanh, danh sach phong / ban, phong cho, ban choi, o nhap hb...) ve phim mem qua
     * jy.a(Graphics, by, by, by) / hb.a(Graphics): ew.c(Graphics) = thanh day + ew.b(Graphics, by, by, by) = chu tren thanh.
     * Doi sang HdUi.thanhDay / HdUi.phimMemB: ban HD khong ve thanh, ve nut kieu moi va GHI KHUNG NUT - gy.l() cua ban HD chi
     * nhan cham trong khung nut da ve (truoc day cac man nay khong co khung nao -> "dung do", khong bam duoc Menu / Bat dau).
     */
    static void hdDay(ClassNode cn, Map<String, Integer> can) {
        final String G = "Ljavax/microedition/lcdui/Graphics;";
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof MethodInsnNode) || n.getOpcode() != Opcodes.INVOKEINTERFACE) {
                    continue;
                }
                MethodInsnNode m = (MethodInsnNode) n;
                if (!m.owner.equals("ew")) {
                    continue;
                }
                if (m.name.equals("c") && m.desc.equals("(" + G + ")V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "thanhDay", "(Lew;" + G + ")V", false));
                    dem(can, "hd_day_thanh");
                } else if (m.name.equals("b") && m.desc.equals("(" + G + "Lby;Lby;Lby;)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "phimMemB",
                            "(Lew;" + G + "Lby;Lby;Lby;)V", false));
                    dem(can, "hd_day_phim");
                }
            }
        }
    }

    /** Ban HD (HdUi): gy.l() - vung cham bat / tat ban phim ao (lan 1) va bong chat (lan 2) cua main.a.a(IIII)Z */
    static void hdGy(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("l") || !mn.desc.equals("()V")) {
                continue;
            }
            int lan = 0;
            boolean mem = false;
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                // chi so phim mem vua cham: invokeinterface ew.d()I -> HdUi.phimNao(ew) (ban HD: nut MENU o goc tren = phim trai)
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEINTERFACE && ((MethodInsnNode) n).owner.equals("ew")
                        && ((MethodInsnNode) n).name.equals("d") && ((MethodInsnNode) n).desc.equals("()I")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "phimNao", "(Lew;)I", false));
                    dem(can, "hd_phim_nao");
                    continue;
                }
                // lan goi main.a.b(IIII)Z dau tien = vung dai phim mem (0, y, rong - 1, cao dai) -> HdUi.vungPhimMem
                if (!mem && n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("main/a")
                        && ((MethodInsnNode) n).name.equals("b") && ((MethodInsnNode) n).desc.equals("(IIII)Z")) {
                    mem = true;
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "vungPhimMem", "(IIII)Z", false));
                    dem(can, "hd_phim_mem");
                    continue;
                }
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("main/a")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(IIII)Z")) {
                    lan++;
                    if (lan <= 2) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", lan == 1 ? "vungPhimAo" : "vungChat",
                                "(IIII)Z", false));
                        dem(can, "hd_vung");
                    }
                }
            }
        }
    }

    /**
     * go.l() (xu ly cham trong nong trai): Vector.elementAt -> HdUi.phanTuDat(Vector, int) - cham vao o dat chua mua khong con
     * nem loi lien tuc (xem HdUi.phanTuDat).
     */
    static void chamDat(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("l") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("java/util/Vector")
                        && ((MethodInsnNode) n).name.equals("elementAt")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "phanTuDat",
                            "(Ljava/util/Vector;I)Ljava/lang/Object;", false));
                    dem(can, "cham_dat");
                }
            }
        }
    }

    /**
     * Ban HD (HdDi): di chuyen muot. hn.m() (tim duong sau khi cham): truoc moi lenh return TRU cai dau (cham ngay trong o
     * dang dung) goi HdDi.muot(this) - rut gon duong di thanh cac doan thang. hn.b() (vong logic cua nhan vat): truoc return
     * goi HdDi.sauBuoc(this) - nhan vat minh di dung tren doan thang, bao vi tri deu cho may chu.
     */
    static void hdDi(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            boolean m = mn.name.equals("m") && mn.desc.equals("()V"), b = mn.name.equals("b") && mn.desc.equals("()V");
            if (!m && !b) {
                continue;
            }
            int thu = 0;
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                if (m && thu++ == 0) {
                    continue;
                }
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", m ? "muot" : "sauBuoc", "(Lhn;)V", false));
                // cac lenh nhay toi "return" phai chay qua loi goi: chen TRUOC nhan cua return (nhan nam truoc return trong danh sach)
                mn.instructions.insertBefore(n, l);
                dem(can, m ? "hd_di_muot" : "hd_di_buoc");
            }
        }
    }

    /**
     * Ban HD (HdDi): thu cung di theo chu (gr). Dau gr.e() (1 buoc di): if (HdDi.buocThu(this)) return;
     * dau gr.g() (chon dich ke tiep): if (HdDi.dichThu(this)) return;
     */
    static void hdThu(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            boolean e = mn.name.equals("e") && mn.desc.equals("()V"), g = mn.name.equals("g") && mn.desc.equals("()V");
            if (!e && !g) {
                continue;
            }
            InsnList l = new InsnList();
            LabelNode tiep = new LabelNode();
            l.add(new VarInsnNode(Opcodes.ALOAD, 0));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", e ? "buocThu" : "dichThu", "(Lgr;)Z", false));
            l.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
            l.add(new InsnNode(Opcodes.RETURN));
            l.add(tiep);
            mn.instructions.insert(l);
            dem(can, "hd_thu");
        }
    }

    /**
     * Thu cung co chu ky muot (ThuMuot): moi "fx.b(short)" trong gr (ve thu di theo) va fa (ve mon do theo khung) ->
     * truoc do doi ma anh: gr: ThuMuot.anh(ma, this); fa: ThuMuot.anh(ma, null).
     */
    static void thuMuot(ClassNode cn, Map<String, Integer> can) {
        boolean thu = cn.name.equals("gr");
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("fx") && ((MethodInsnNode) n).name.equals("b")
                        && ((MethodInsnNode) n).desc.equals("(S)Lf;")) {
                    InsnList l = new InsnList();
                    l.add(thu ? new VarInsnNode(Opcodes.ALOAD, 0) : new InsnNode(Opcodes.ACONST_NULL));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ThuMuot", "anh", "(SLgr;)S", false));
                    mn.instructions.insertBefore(n, l);
                    dem(can, thu ? "thu_muot" : "thu_muot_fa");
                }
            }
        }
    }

    /**
     * Thu cung, moi ban (ca ban doc): ban goc lay "cap" mon do lam toc do (Q): cap 0 -> aw.e(0) nem loi moi lan thu toi noi
     * (ca vong lap bi bo do), cap 3 -> chia cho 0, cap 28 -> chay 30 diem / nhip.
     *  - "getfield fa.a" trong gr -> HdDi.capThu(cap): chi con 1 hoac 2 nhu ban goc tinh;
     *  - moi aw.e(int) trong gr -> HdDi.ngauNhien(int) (khong nem loi khi so <= 0).
     */
    static void thuAnToan(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).owner.equals("fa") && ((FieldInsnNode) n).name.equals("a")) {
                    mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "capThu", "(I)I", false));
                    dem(can, "thu_cap");
                }
                if (n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("aw") && ((MethodInsnNode) n).name.equals("e")
                        && ((MethodInsnNode) n).desc.equals("(I)I")) {
                    ((MethodInsnNode) n).owner = "HdDi";
                    ((MethodInsnNode) n).name = "ngauNhien";
                    dem(can, "thu_ngau");
                }
            }
        }
    }

    /**
     * Man "Chon T. Pho" (ez): dau a() va e() -> TpMo.nap() (loc danh sach theo thanh pho dang mo);
     * c(): nut "Cap nhat" (tai danh sach may chu tu trang cua TeaMobi, ghi de danh sach cua app) bo han:
     * "getstatic GameMidlet.f" trong c() -> iconst_1 (dieu kien f == 0 khong bao gio dung).
     */
    static void tpEz(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if ((mn.name.equals("a") || mn.name.equals("e")) && mn.desc.equals("()V") && (mn.access & Opcodes.ACC_STATIC) == 0) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "TpMo", "nap", "()V", false));
                dem(can, "tp_nap");
            }
            if (mn.name.equals("c") && mn.desc.equals("()V")) {
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("main/GameMidlet") && ((FieldInsnNode) n).name.equals("f")) {
                        mn.instructions.set(n, new InsnNode(Opcodes.ICONST_1));
                        dem(can, "tp_cap_nhat");
                    }
                }
            }
        }
    }

    /**
     * Truong dua pet (cz): muc cuoc lon hon + o "Nhap so".
     *  - "putfield cz.aG [I" -> truoc do TpMo.chipDua(mang goc);
     *  - c(Graphics): String.valueOf(int) (chu tren nut) -> TpMo.nhanChip(int).
     */
    static void duaChip(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.PUTFIELD && ((FieldInsnNode) n).name.equals("aG") && ((FieldInsnNode) n).desc.equals("[I")) {
                    mn.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TpMo", "chipDua", "([I)[I", false));
                    dem(can, "dua_chip");
                }
                if (mn.name.equals("c") && mn.desc.equals("(Ljavax/microedition/lcdui/Graphics;)V") && n.getOpcode() == Opcodes.INVOKESTATIC
                        && ((MethodInsnNode) n).owner.equals("java/lang/String") && ((MethodInsnNode) n).name.equals("valueOf")
                        && ((MethodInsnNode) n).desc.equals("(I)Ljava/lang/String;")) {
                    ((MethodInsnNode) n).owner = "TpMo";
                    ((MethodInsnNode) n).name = "nhanChip";
                    dem(can, "dua_nhan");
                }
            }
        }
    }

    /**
     * Theme bang moi cua ban HD, va ngay trong gw (lop ve giao dien goc) de ca cac loi goi noi bo cung doi:
     *  - dau gw.a(Graphics,IIIIIII)V: "if (HdUi.khungHd(g, x, y, w, h, a, b, c)) return;"
     *  - gw.b(Graphics,IIII)V (dong dang chon cua bang menu noi): mau 0xECFBE9 -> HdUi.mauChon(mau).
     */
    static void hdGwTheme(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("a") && mn.desc.equals("(" + G + "IIIIIII)V")) {
                InsnList il = new InsnList();
                LabelNode tiep = new LabelNode();
                for (int i = 1; i <= 8; i++) {
                    il.add(new VarInsnNode(i == 1 ? Opcodes.ALOAD : Opcodes.ILOAD, i));
                }
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "khungHd", "(" + G + "IIIIIII)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(tiep);
                mn.instructions.insert(il);
                dem(can, "gw_khung");
            }
            if (mn.name.equals("b") && mn.desc.equals("(" + G + "IIII)V")) {
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n instanceof LdcInsnNode && Integer.valueOf(15530985).equals(((LdcInsnNode) n).cst)) {
                        mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "mauChon", "(I)I", false));
                        dem(can, "gw_chon");
                    }
                }
            }
        }
    }

    /** moi "putstatic <lop>.<truong> [Ldr;" (bang vi tri ghe): chen HdUi.ghe(dr[]) truoc do - ban HD lui ghe 2 ben vao trong le */
    static void hdGhe(ClassNode cn, Map<String, Integer> can, String lop, String truong) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) n).owner.equals(lop) && ((FieldInsnNode) n).name.equals(truong)
                        && ((FieldInsnNode) n).desc.equals("[Ldr;")) {
                    mn.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "ghe", "([Ldr;)[Ldr;", false));
                    dem(can, "ghe_" + lop);
                }
            }
        }
    }

    /** kx.e(Graphics) (nen phong cho tro choi): invokeinterface ew.b(Graphics) -> HdUi.nenPhong(ew, Graphics) (ban ni + ghe trong) */
    static void hdPhongCho(ClassNode cn, Map<String, Integer> can) {
        hdGhe(cn, can, "kx", "F");
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("e") || !mn.desc.equals("(" + G + ")V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.INVOKEINTERFACE && ((MethodInsnNode) n).owner.equals("ew") && ((MethodInsnNode) n).name.equals("b")
                        && ((MethodInsnNode) n).desc.equals("(" + G + ")V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "nenPhong", "(Lew;" + G + ")V", false));
                    dem(can, "phong_cho");
                }
            }
        }
    }

    /** je.a(Graphics,IIII) (1 o cua luoi do): dau ham them "if (HdUi.oLuoiVe(g, x, y, w, h)) return;" - theme moi ban HD */
    static void hdOLuoi(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("a") && mn.desc.equals("(" + G + "IIII)V") && (mn.access & Opcodes.ACC_STATIC) != 0) {
                InsnList il = new InsnList();
                LabelNode tiep = new LabelNode();
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new VarInsnNode(Opcodes.ILOAD, 2));
                il.add(new VarInsnNode(Opcodes.ILOAD, 3));
                il.add(new VarInsnNode(Opcodes.ILOAD, 4));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "oLuoiVe", "(" + G + "IIII)Z", false));
                il.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
                il.add(new InsnNode(Opcodes.RETURN));
                il.add(tiep);
                mn.instructions.insert(il);
                dem(can, "o_luoi");
            }
        }
    }

    /**
     * Vong quay so (hw.l()): ban HD nut "Quay" nam o goc phai, game goc do vung GIU theo 3 o phim mem cu nen giu nut khong an.
     *  - invokeinterface ew.d() -> HdUi.phimGiu(ew) (nut dang giu / vua nha theo khung nut HD);
     *  - moi lan doc hw.q -> HdUi.quaySan(q) (HD: khoi bam "Quay" mo truoc);
     *  - truoc lan doc main.a.c[5] (vua nha): hw.k = HdUi.quayLuc(hw.k) (HD: cham nhanh van quay luc nho nhat).
     */
    /**
     * Vong quay so (hw) - quan tri: "quay so bi lag". Ban goc: j = 100 + luc (100..280 nhip), moi nhip giam 1 -> quay ~5-14 giay,
     * xong con BO rat cham (duoi 0,5 do / nhip) toi khi kim vao giua o -> 1 luot mat 8-18 giay, nhin nhu bi dung hinh.
     * Sua (moi ban): j dau = HdDi.quayDau (cao hon -> quay nhanh ro), moi nhip giam HdDi.quayGiam (5), bo cuoi tu j < 20
     * (~0,9 do / nhip, van di qua tung do nen chac chan can duoc vao o). 1 luot con ~4-5 giay. May chu khong doi.
     */
    static void quayNhanh(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            boolean nhan = mn.name.equals("a") && mn.desc.equals("(IILjava/util/Vector;)V"), chay = mn.name.equals("k") && mn.desc.equals("()V");
            if (!nhan && !chay) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                boolean ghiJ = n.getOpcode() == Opcodes.PUTFIELD && ((FieldInsnNode) n).owner.equals("hw") && ((FieldInsnNode) n).name.equals("j");
                if (nhan && ghiJ) {
                    // j = 100 + (m - 90)  ->  j = HdDi.quayDau(...)
                    mn.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "quayDau", "(I)I", false));
                    dem(can, "quay_dau");
                } else if (chay && ghiJ) {
                    AbstractInsnNode a = n.getPrevious(), b = a == null ? null : a.getPrevious();
                    if (a != null && b != null && a.getOpcode() == Opcodes.ISUB && b.getOpcode() == Opcodes.ICONST_1) {
                        // --j  ->  j = HdDi.quayGiam(j)
                        mn.instructions.remove(b);
                        mn.instructions.set(a, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "quayGiam", "(I)I", false));
                        dem(can, "quay_giam");
                    }
                } else if (chay && n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).owner.equals("hw") && ((FieldInsnNode) n).name.equals("j")) {
                    AbstractInsnNode a = n.getNext();
                    if (a != null && a.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) a).operand == 10) {
                        // if (j < 10) can vao o  ->  j < 20: doan bo cuoi nhanh gap doi
                        mn.instructions.set(a, new IntInsnNode(Opcodes.BIPUSH, 20));
                        dem(can, "quay_bo");
                    }
                }
            }
        }
    }

    /**
     * Vong quay so (hw) - quan tri: "chon mon xong co cai vong quay, vong quay do phai quay duoc nhieu lan hoac auto quay ngay
     * o do" (ban goc quay 1 lan la phai Dong, mo lai menu NPC, chon lai mon). Moi ban (JAR / doc / HD):
     *  - dau hw.a(II)V: if (HdDi.quayLenh(this, lenh, s, m)) return;  (phim giua sau khi ra qua = "Quay tiep": dat lai vong +
     *    quay luon voi luc cu; phim trai "Tu quay" / "Dung tu quay"; phim phai "Dong")
     *  - trong hw.a(II)V: this.r.a() (roi man hinh) -> HdDi.quayDong(r) (chi roi khi bam "Dong")
     *  - dau hw.k()V: HdDi.quayTick(this, s, m, t, v) (dat nhan phim, tu quay lien tuc)
     *  - dau hw.a(Lhj;S)V: HdDi.quayMo() (vao man hinh: tat tu quay)
     */
    static void quayLai(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.instructions == null) {
                continue;
            }
            if (mn.name.equals("a") && mn.desc.equals("(II)V")) {
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("()V")
                            && n.getPrevious() != null && n.getPrevious().getOpcode() == Opcodes.GETFIELD
                            && ((FieldInsnNode) n.getPrevious()).owner.equals("hw") && ((FieldInsnNode) n.getPrevious()).name.equals("r")) {
                        mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "quayDong", "(Lhj;)V", false));
                        dem(can, "quay_dong");
                    }
                }
                LabelNode tiep = new LabelNode();
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ILOAD, 1));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "s", "S"));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "m", "I"));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "quayLenh", "(Lhw;III)Z", false));
                l.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
                l.add(new InsnNode(Opcodes.RETURN));
                l.add(tiep);
                mn.instructions.insert(l);
                dem(can, "quay_lenh");
            } else if (mn.name.equals("k") && mn.desc.equals("()V")) {
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "s", "S"));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "m", "I"));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "t", "Lby;"));
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "v", "Lby;"));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "quayTick", "(Lhw;IILby;Lby;)V", false));
                mn.instructions.insert(l);
                dem(can, "quay_tick");
            } else if (mn.name.equals("a") && mn.desc.equals("(Lhj;S)V")) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "quayMo", "()V", false));
                dem(can, "quay_mo");
            }
        }
    }

    static void hdQuay(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("l") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.INVOKEINTERFACE && ((MethodInsnNode) n).owner.equals("ew") && ((MethodInsnNode) n).name.equals("d")
                        && ((MethodInsnNode) n).desc.equals("()I")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "phimGiu", "(Lew;)I", false));
                    dem(can, "quay_giu");
                } else if (n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).owner.equals("hw") && ((FieldInsnNode) n).name.equals("q")) {
                    mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "quaySan", "(Z)Z", false));
                    dem(can, "quay_san");
                } else if (n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("main/a") && ((FieldInsnNode) n).name.equals("c")
                        && ((FieldInsnNode) n).desc.equals("[Z")) {
                    AbstractInsnNode a = n.getNext(), b = a == null ? null : a.getNext();
                    if (a != null && b != null && a.getOpcode() == Opcodes.ICONST_5 && b.getOpcode() == Opcodes.BALOAD && can.getOrDefault("quay_luc", 0) == 0) {
                        InsnList il = new InsnList();
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        il.add(new FieldInsnNode(Opcodes.GETFIELD, "hw", "k", "I"));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "quayLuc", "(I)I", false));
                        il.add(new FieldInsnNode(Opcodes.PUTFIELD, "hw", "k", "I"));
                        mn.instructions.insertBefore(n, il);
                        dem(can, "quay_luc");
                    }
                }
            }
        }
    }

    /**
     * Ban HD: bang do / shop / ruong (dp) TO hon (quan tri: "bang nho xiu", "phong to bang"): man ngang rong nen tang so cot
     * tu 5 len HdUi.soCot() (8) va o luoi 30 -> HdUi.oLuoi (32). Ban goc viet cung so 5:
     *  - moi "iconst_5" di ngay truoc imul / idiv / irem, hoac ngay truoc "putstatic dp.r" -> HdUi.soCot();
     *  - dau dp.e() (tinh kich thuoc bang): dp.e = HdUi.oLuoi(dp.e).
     * Ban doc / JAR: soCot() = 5, oLuoi giu nguyen -> khong doi gi.
     */
    /** lop mon do ve trong bang dp tu dat lai vung cat "5 * dp.e" (5 cot cu) -> HdUi.soCot() * dp.e (khong thi cot 6..8 bi cat mat) */
    static void hdCot5(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                AbstractInsnNode a = n.getNext(), b = a == null ? null : a.getNext();
                if (n.getOpcode() == Opcodes.ICONST_5 && a != null && b != null && a.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) a).owner.equals("dp")
                        && ((FieldInsnNode) a).name.equals("e") && b.getOpcode() == Opcodes.IMUL) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "soCot", "()I", false));
                    dem(can, "cot5_khac");
                }
            }
        }
    }

    static void hdBangTo(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                // sau khi dat khung bang je.a().a(ten, rong, cao, soTab): HdUi.bangY() can bang giua CA man hinh (ban goc can
                // trong phan tren thanh phim mem nen o man ngang tab bi cat mat mep tren)
                if (n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("je") && ((MethodInsnNode) n).name.equals("a")
                        && ((MethodInsnNode) n).desc.equals("(Ljava/lang/String;III)V")) {
                    mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "bangY", "()V", false));
                    dem(can, "bang_y");
                }
                // o dang chon: je.a(x, y, w, h, 0x22FF77, g) -> HdUi.oChon (HD: nen trang vien cam)
                if (n instanceof LdcInsnNode && Integer.valueOf(2293623).equals(((LdcInsnNode) n).cst)) {
                    AbstractInsnNode k = n.getNext();
                    for (int buoc = 0; k != null && buoc < 4; buoc++, k = k.getNext()) {
                        if (k.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) k).owner.equals("je") && ((MethodInsnNode) k).name.equals("a")
                                && ((MethodInsnNode) k).desc.equals("(IIIII" + G + ")V")) {
                            ((MethodInsnNode) k).owner = "HdUi";
                            ((MethodInsnNode) k).name = "oChon";
                            dem(can, "o_chon");
                            break;
                        }
                    }
                }
            }
        }
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() != Opcodes.ICONST_5) {
                    continue;
                }
                AbstractInsnNode nx = n.getNext();
                while (nx != null && nx.getOpcode() < 0) {
                    nx = nx.getNext();
                }
                if (nx == null) {
                    continue;
                }
                int op = nx.getOpcode();
                if (op == Opcodes.GETSTATIC && ((FieldInsnNode) nx).owner.equals("dp") && ((FieldInsnNode) nx).name.equals("e")) {
                    AbstractInsnNode n2 = nx.getNext();          // "5 * dp.e" (vung cat cua luoi)
                    while (n2 != null && n2.getOpcode() < 0) {
                        n2 = n2.getNext();
                    }
                    if (n2 != null && n2.getOpcode() == Opcodes.IMUL) {
                        op = Opcodes.IMUL;
                    }
                }
                boolean cot = op == Opcodes.IMUL || op == Opcodes.IDIV || op == Opcodes.IREM
                        || (op == Opcodes.PUTSTATIC && ((FieldInsnNode) nx).owner.equals("dp") && ((FieldInsnNode) nx).name.equals("r"));
                if (cot) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "soCot", "()I", false));
                    dem(can, "bang_cot");
                }
            }
            if (mn.name.equals("e") && mn.desc.equals("()V") && (mn.access & Opcodes.ACC_STATIC) != 0) {
                InsnList l = new InsnList();
                l.add(new FieldInsnNode(Opcodes.GETSTATIC, "dp", "e", "I"));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "HdUi", "oLuoi", "(I)I", false));
                l.add(new FieldInsnNode(Opcodes.PUTSTATIC, "dp", "e", "I"));
                mn.instructions.insert(l);
                dem(can, "bang_o");
            }
        }
    }

    /** ae.d(II)V (chon muc trong MENU do may chu dat): cx.h(vi tri) -> cx.h(TpMo.maLenh(vi tri)) */
    static void menuLenh(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("d") || !mn.desc.equals("(II)V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("cx") && ((MethodInsnNode) n).name.equals("h")
                        && ((MethodInsnNode) n).desc.equals("(I)V")) {
                    mn.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TpMo", "maLenh", "(I)I", false));
                    dem(can, "menu_lenh");
                }
            }
        }
    }

    /** es.i() (gui dang nhap): chuoi phien ban "2.5.8" -> TpMo.dangNhap() = "2.5.8;tp=<thanh pho dang chon>" */
    static void tpDangNhap(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("i") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof LdcInsnNode && "2.5.8".equals(((LdcInsnNode) n).cst)) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TpMo", "dangNhap", "()Ljava/lang/String;", false));
                    dem(can, "tp_dang_nhap");
                }
            }
        }
    }

    /** main.a.c() (noi may chu): dau ham -> TpMo.nap() */
    static void tpNoi(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("c") && mn.desc.equals("()V") && (mn.access & Opcodes.ACC_STATIC) != 0) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "TpMo", "nap", "()V", false));
                dem(can, "tp_noi");
            }
        }
    }

    /** Ban HD (HdDi): do.b(int) dat vung nhin truoc cua may quay do.p = main.a.m / 10 -> qua HdDi.camTruoc (ban HD = 0) */
    static void hdCamTruoc(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("b") || !mn.desc.equals("(I)V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) n).owner.equals("do") && ((FieldInsnNode) n).name.equals("p")) {
                    mn.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdDi", "camTruoc", "(I)I", false));
                    dem(can, "hd_cam_truoc");
                }
            }
        }
    }

    static void moTruong(ClassNode cn, Map<String, Integer> can) {
        for (FieldNode f : cn.fields) {
            if ((f.access & Opcodes.ACC_STATIC) == 0) {
                f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            }
        }
        dem(can, "mo_truong");
    }

    interface Va {
        void va(ClassNode cn, Map<String, Integer> can);
    }

    static byte[] va(byte[] data, Va v, Map<String, Integer> can, String name) {
        ClassReader cr = new ClassReader(data);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        v.va(cn, can);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    static void dem(Map<String, Integer> can, String k) {
        can.merge(k, 1, Integer::sum);
    }

    static AbstractInsnNode sau(AbstractInsnNode n) {
        n = n.getNext();
        while (n != null && n.getOpcode() < 0) {
            n = n.getNext();
        }
        return n;
    }

    // 1) main/a.pointerPressed(II)V: iconst_1; putstatic main/a.g:Z  ->  iload_1; iload_2; invokestatic KeyTap.g(II)V
    static void keyTap(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("pointerPressed") || !mn.desc.equals("(II)V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.ICONST_1) {
                    AbstractInsnNode p = sau(n);
                    if (p instanceof FieldInsnNode && p.getOpcode() == Opcodes.PUTSTATIC
                            && ((FieldInsnNode) p).owner.equals("main/a") && ((FieldInsnNode) p).name.equals("g")) {
                        InsnList l = new InsnList();
                        l.add(new VarInsnNode(Opcodes.ILOAD, 1));
                        l.add(new VarInsnNode(Opcodes.ILOAD, 2));
                        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "g", "(II)V", false));
                        mn.instructions.insert(p, l);
                        mn.instructions.remove(n);
                        mn.instructions.remove(p);
                        dem(can, "keytap");
                        return;
                    }
                }
            }
        }
    }

    // 7) toc do game: main/a.run() - hang so 50 (ms / khung) -> TocDo.khung(), Canvas.repaint() -> TocDo.ve(canvas)
    static void tocDoVongLap(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("run") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof LdcInsnNode && Long.valueOf(50L).equals(((LdcInsnNode) n).cst)) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TocDo", "khung", "()J", false));
                    dem(can, "tocdo_khung");
                } else if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && ((MethodInsnNode) n).name.equals("repaint") && ((MethodInsnNode) n).desc.equals("()V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TocDo", "ve", "(Ljavax/microedition/lcdui/Canvas;)V", false));
                    dem(can, "tocdo_ve");
                } else if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("java/lang/Thread")
                        && ((MethodInsnNode) n).name.equals("sleep") && ((MethodInsnNode) n).desc.equals("(J)V")) {
                    // ngu giua 2 vong: x1 giu nguyen; x2..x10 ngu theo lich logic (bat kip thi khong ngu)
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TocDo", "ngu", "(J)V", false));
                    dem(can, "tocdo_ngu");
                } else if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).name.equals("printStackTrace")
                        && ((MethodInsnNode) n).desc.equals("()V")) {
                    // loi logic trong vong lap: khong de vong lap quay het toc (xem TocDo.loiVong)
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TocDo", "loiVong", "(Ljava/lang/Throwable;)V", false));
                    dem(can, "tocdo_loi");
                }
            }
        }
    }

    //    dm (man Cai dat): mo truong l / g / b; dau e(): TocDo.caiDat(this); dau e(int): if (TocDo.doi(this, n)) return;
    static void tocDoCaiDat(ClassNode cn, Map<String, Integer> can) {
        for (FieldNode f : cn.fields) {
            if ((f.name.equals("l") || f.name.equals("g") || f.name.equals("b")) && (f.access & Opcodes.ACC_STATIC) == 0) {
                f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            }
        }
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("e") && mn.desc.equals("()V") && (mn.access & Opcodes.ACC_STATIC) == 0) {
                InsnList l = new InsnList();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TocDo", "caiDat", "(Ldm;)V", false));
                mn.instructions.insert(l);
                dem(can, "tocdo_caidat");
            } else if (mn.name.equals("e") && mn.desc.equals("(I)V") && (mn.access & Opcodes.ACC_STATIC) == 0) {
                InsnList l = new InsnList();
                LabelNode tiep = new LabelNode();
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                l.add(new VarInsnNode(Opcodes.ILOAD, 1));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TocDo", "doi", "(Ldm;I)Z", false));
                l.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
                l.add(new InsnNode(Opcodes.RETURN));
                l.add(tiep);
                mn.instructions.insert(l);
                dem(can, "tocdo_doi");
            }
        }
    }

    // 6) b (ban phim ao): mo cac truong (ban J2ME de private) va goi KeyTap.nhan(this, g) truoc moi return cua a(Graphics)V
    //    -> ghi "F1" / "F2" / "OK" vao 3 o truoc day de trong
    static void banPhim(ClassNode cn, Map<String, Integer> can) {
        for (FieldNode f : cn.fields) {
            if ("a b c d e f g l u v".contains(f.name) && f.name.length() == 1 && (f.access & Opcodes.ACC_STATIC) == 0) {
                f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            }
        }
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(" + G + ")V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.RETURN) {
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    l.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "KeyTap", "nhan", "(Lb;" + G + ")V", false));
                    mn.instructions.insertBefore(n, l);
                    dem(can, "banphim");
                }
            }
        }
    }

    // 2) kc.a(Graphics)V: CayRong.sau(g, this) o dau, lenh ve anh cay (gb.a(g, id, x, y, neo) dau tien) -> CayRong.veCay(.., this),
    //    CayRong.truoc(g, this) truoc moi return
    static void cayRong(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(" + G + ")V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) n).owner.equals("gb")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(" + G + "IIII)V")) {
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "CayRong", "veCay", "(" + G + "IIIILkc;)V", false));
                    mn.instructions.insertBefore(n, l);
                    mn.instructions.remove(n);
                    dem(can, "vecay");
                    break;                      // chi lenh dau tien (anh cay); cac lenh sau ve qua khe
                }
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.RETURN) {
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "CayRong", "truoc", "(" + G + "Lkc;)V", false));
                    mn.instructions.insertBefore(n, l);
                    dem(can, "rong_truoc");
                }
            }
            InsnList l = new InsnList();
            l.add(new VarInsnNode(Opcodes.ALOAD, 1));
            l.add(new VarInsnNode(Opcodes.ALOAD, 0));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "CayRong", "sau", "(" + G + "Lkc;)V", false));
            mn.instructions.insert(l);
            dem(can, "rong_sau");
        }
    }

    // 3) ir: trong ham ve tile (doc ir.d), invokevirtual ht.b(IIII Graphics)V -> invokestatic LandFx.oDat(ht, IIII, Graphics)V
    static void oDat(ClassNode cn, Map<String, Integer> can) {
        String d = "(IIII" + G + ")V";
        for (MethodNode mn : cn.methods) {
            boolean docD = false;
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("ir")
                        && ((FieldInsnNode) n).name.equals("d") && ((FieldInsnNode) n).desc.equals("[S")) {
                    docD = true;
                }
            }
            if (!docD) {
                continue;
            }
            List<MethodInsnNode> goi = new ArrayList<>();
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("ht")
                        && ((MethodInsnNode) n).name.equals("b") && ((MethodInsnNode) n).desc.equals(d)) {
                    goi.add((MethodInsnNode) n);
                }
            }
            if (goi.size() == 1) {
                mn.instructions.set(goi.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, "LandFx", "oDat", "(Lht;IIII" + G + ")V", false));
                dem(can, "odat");
            }
        }
    }

    // 4) go: moi getfield cf.p:
    //    "cf.p == 1 && i == 0" (iconst_1, if_icmpne, iload)           -> invokestatic LandFx.dau(cf)I
    //    "cf.p == 1/2" o luat gieo hat / chon hinh dat (iconst_1|2, if_icmpne) -> invokestatic LandFx.logic(cf)I
    //    so sanh 2 o voi nhau (cf2.p < cf3.p) giu nguyen (cap that)
    static void luatDat(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != Opcodes.GETFIELD || !((FieldInsnNode) n).owner.equals("cf")
                        || !((FieldInsnNode) n).name.equals("p")) {
                    continue;
                }
                AbstractInsnNode k = sau(n);
                if (k == null || (k.getOpcode() != Opcodes.ICONST_1 && k.getOpcode() != Opcodes.ICONST_2)) {
                    continue;
                }
                AbstractInsnNode j = sau(k);
                if (j == null || (j.getOpcode() != Opcodes.IF_ICMPNE && j.getOpcode() != Opcodes.IF_ICMPEQ)) {
                    continue;
                }
                AbstractInsnNode t = sau(j);
                boolean dau = k.getOpcode() == Opcodes.ICONST_1 && j.getOpcode() == Opcodes.IF_ICMPNE
                        && t instanceof VarInsnNode && t.getOpcode() == Opcodes.ILOAD;
                mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "LandFx", dau ? "dau" : "logic", "(Lcf;)I", false));
                dem(can, dau ? "dau" : "logic");
            }
        }
    }

    // 5) kz.a(jr)V: if (LandFx.tin(jr)) return;
    static void lenh118(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(Ljr;)V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            InsnList l = new InsnList();
            LabelNode tiep = new LabelNode();
            l.add(new VarInsnNode(Opcodes.ALOAD, 1));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "LandFx", "tin", "(Ljr;)Z", false));
            l.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
            l.add(new InsnNode(Opcodes.RETURN));
            l.add(tiep);
            mn.instructions.insert(l);
            dem(can, "l118");
        }
    }

    // 8) Dau truong boss (DauTruong): ae.k() truoc moi return -> capNhat(); ae.b(Graphics): sau ir.b(g) -> veNen(g),
    //    sau ir.d(g) -> veTren(g), truoc moi return -> veHud(g)
    static void dauTruongAe(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            if (mn.name.equals("k") && mn.desc.equals("()V")) {
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n.getOpcode() == Opcodes.RETURN) {
                        mn.instructions.insertBefore(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", "capNhat", "()V", false));
                        dem(can, "dt_k");
                    }
                }
            } else if (mn.name.equals("b") && mn.desc.equals("(" + G + ")V")) {
                for (AbstractInsnNode n : mn.instructions.toArray()) {
                    if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("ir")
                            && ((MethodInsnNode) n).desc.equals("(" + G + ")V")) {
                        String nm = ((MethodInsnNode) n).name;
                        if (nm.equals("b") || nm.equals("d")) {
                            InsnList l = new InsnList();
                            l.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", nm.equals("b") ? "veNen" : "veTren", "(" + G + ")V", false));
                            mn.instructions.insert(n, l);
                            dem(can, nm.equals("b") ? "dt_nen" : "dt_tren");
                        }
                    } else if (n.getOpcode() == Opcodes.RETURN) {
                        InsnList l = new InsnList();
                        l.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", "veHud", "(" + G + ")V", false));
                        mn.instructions.insertBefore(n, l);
                        dem(can, "dt_hud");
                    }
                }
            }
        }
    }

    // le cuoi: ae.e(II)V dau ham  if (!CuoiAn.cho(chuRe, coDau)) return;   ae.k()V dau ham  CuoiAn.kiem();
    static void cuoiAn(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if ((mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            if (mn.name.equals("e") && mn.desc.equals("(II)V")) {
                InsnList l = new InsnList();
                LabelNode tiep = new LabelNode();
                l.add(new VarInsnNode(Opcodes.ILOAD, 1));
                l.add(new VarInsnNode(Opcodes.ILOAD, 2));
                l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "CuoiAn", "cho", "(II)Z", false));
                l.add(new JumpInsnNode(Opcodes.IFNE, tiep));
                l.add(new InsnNode(Opcodes.RETURN));
                l.add(tiep);
                mn.instructions.insert(l);
                dem(can, "cuoi_cho");
            } else if (mn.name.equals("k") && mn.desc.equals("()V")) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "CuoiAn", "kiem", "()V", false));
                dem(can, "cuoi_kiem");
            }
        }
    }

    //    hn.a(Graphics)V: dau ham if (DauTruong.veThay(this, g)) return;  truoc super.a(g) (gx.a) -> DauTruong.sauNguoi(this, g)
    static void dauTruongHn(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(" + G + ")V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) n).owner.equals("gx")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(" + G + ")V")) {
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    l.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", "sauNguoi", "(Lhn;" + G + ")V", false));
                    mn.instructions.insertBefore(n, l);
                    dem(can, "dt_sau");
                }
                // ten tren dau: this.a(g, x, y) -> DauTruong.veTen(this, g, x, y) (dau truong: bo ten de len ten khac)
                if (n instanceof MethodInsnNode && n.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) n).owner.equals("hn")
                        && ((MethodInsnNode) n).name.equals("a") && ((MethodInsnNode) n).desc.equals("(" + G + "II)V")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", "veTen", "(Lhn;" + G + "II)V", false));
                    dem(can, "dt_ten");
                }
            }
            InsnList l = new InsnList();
            LabelNode tiep = new LabelNode();
            l.add(new VarInsnNode(Opcodes.ALOAD, 0));
            l.add(new VarInsnNode(Opcodes.ALOAD, 1));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", "veThay", "(Lhn;" + G + ")Z", false));
            l.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
            l.add(new InsnNode(Opcodes.RETURN));
            l.add(tiep);
            mn.instructions.insert(l);
            dem(can, "dt_thay");
        }
    }

    //    ce.a(jr)V (xu ly tin tu may chu): if (DauTruong.tin(jr)) return;   (lenh 119)
    static void dauTruongCe(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("(Ljr;)V") || (mn.access & Opcodes.ACC_STATIC) != 0) {
                continue;
            }
            InsnList l = new InsnList();
            LabelNode tiep = new LabelNode();
            l.add(new VarInsnNode(Opcodes.ALOAD, 1));
            l.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "DauTruong", "tin", "(Ljr;)Z", false));
            l.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
            l.add(new InsnNode(Opcodes.RETURN));
            l.add(tiep);
            mn.instructions.insert(l);
            dem(can, "dt_tin");
        }
    }

    // 9) Huong dan tan thu (lop bb) tat han: ban goc hoi "Ban co muon xem huong dan choi game khong?" nhung ca "Co" lan
    //    "Khong" deu goi ic.f() -> main.a.I = true -> vao nong trai camera tu keo toi o dat / chuong / cay khe..., khoa phim
    //    di chuyen trong luc hien chu; nhieu buoc tro toi NPC / khu khong co tren may chu nay nen de ket.
    //    - moi "iconst_1; putstatic main/a.I" (ic.f, bb.h) -> iconst_0
    //    - ic.a(II)V: "main.a.a(kf.dC, menu Co/Khong)" -> goi thang ic.f() (vao game luon, khong hoi)
    static void tatHuongDan(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.ICONST_1) {
                    AbstractInsnNode p = sau(n);
                    if (p instanceof FieldInsnNode && p.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) p).owner.equals("main/a")
                            && ((FieldInsnNode) p).name.equals("I") && ((FieldInsnNode) p).desc.equals("Z")) {
                        mn.instructions.set(n, new InsnNode(Opcodes.ICONST_0));
                        dem(can, "hd_tat");
                    }
                }
            }
            if (!cn.name.equals("ic") || !mn.name.equals("a") || !mn.desc.equals("(II)V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) n).owner.equals("kf")
                        && ((FieldInsnNode) n).name.equals("dC")) {
                    AbstractInsnNode v = sau(n);
                    AbstractInsnNode g = v == null ? null : sau(v);
                    if (v != null && v.getOpcode() == Opcodes.ALOAD && g instanceof MethodInsnNode && g.getOpcode() == Opcodes.INVOKESTATIC
                            && ((MethodInsnNode) g).owner.equals("main/a") && ((MethodInsnNode) g).name.equals("a")
                            && ((MethodInsnNode) g).desc.equals("(Ljava/lang/String;Ljava/util/Vector;)V")) {
                        mn.instructions.set(g, new MethodInsnNode(Opcodes.INVOKESTATIC, "ic", "f", "()V", false));
                        mn.instructions.remove(v);
                        mn.instructions.remove(n);
                        dem(can, "hd_hoi");
                    }
                }
            }
        }
    }

    // 10) Gia luong > 32.767: fx.a([BZ) (doc danh muc do): readShort() ngay sau readInt() (gia xu) -> GiaLuong.doc(in)
    static void giaLuong(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("([BZ)Ljava/util/Vector;")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals("java/io/DataInputStream")
                        && ((MethodInsnNode) n).name.equals("readInt")) {
                    // aload in; invokevirtual readShort -> chi doi lenh goi
                    AbstractInsnNode st = sau(n);           // istore (gia xu)
                    AbstractInsnNode ld = st == null ? null : sau(st);
                    AbstractInsnNode rs = ld == null ? null : sau(ld);
                    if (rs instanceof MethodInsnNode && ((MethodInsnNode) rs).name.equals("readShort")) {
                        mn.instructions.set(rs, new MethodInsnNode(Opcodes.INVOKESTATIC, "GiaLuong", "doc", "(Ljava/io/DataInputStream;)I", false));
                        dem(can, "gia_luong");
                    }
                    break;
                }
            }
        }
    }

    // 11) An trom: go.u() (thu hoach o dat chin) chi chay o trai minh ("if (w != go.b) return") -> bo dieu kien:
    //     o trai nguoi khac app gui lenh 66 voi id chu trai, may chu tinh la an trom (o dat ben chu van con cay)
    static void tromCay(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("u") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n.getOpcode() == Opcodes.IF_ICMPEQ) {
                    AbstractInsnNode r = sau(n);
                    if (r != null && r.getOpcode() == Opcodes.RETURN) {
                        mn.instructions.insertBefore(n, new InsnNode(Opcodes.POP2));
                        ((JumpInsnNode) n).setOpcode(Opcodes.GOTO);
                        dem(can, "trom_cay");
                    }
                    break;
                }
            }
        }
    }

    // 12) Kho hang: o hien thong tin = o dang chon (so sanh chinh doi tuong o, khong so chi so)
    //     j.a():  aload0; getfield g; getstatic dp.j; getstatic go.f; size(); isub; if_icmpne L
    //     ko.a(): aload0; getfield f; getstatic dp.j; if_icmpne L
    //     -> aload0; invokestatic KhoO.chon(Object)Z; ifeq L
    static void oKho(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != Opcodes.GETSTATIC
                        || !((FieldInsnNode) n).owner.equals("dp") || !((FieldInsnNode) n).name.equals("j")) {
                    continue;
                }
                AbstractInsnNode gf = n.getPrevious();
                while (gf != null && gf.getOpcode() < 0) {
                    gf = gf.getPrevious();
                }
                AbstractInsnNode a0 = gf == null ? null : gf.getPrevious();
                while (a0 != null && a0.getOpcode() < 0) {
                    a0 = a0.getPrevious();
                }
                if (!(gf instanceof FieldInsnNode) || gf.getOpcode() != Opcodes.GETFIELD || a0 == null || a0.getOpcode() != Opcodes.ALOAD) {
                    break;
                }
                java.util.List<AbstractInsnNode> bo = new java.util.ArrayList<>();
                bo.add(gf);
                bo.add(n);
                AbstractInsnNode k = sau(n);
                if (cn.name.equals("j")) {
                    // getstatic go.f; invokevirtual size; isub
                    AbstractInsnNode k2 = sau(k), k3 = k2 == null ? null : sau(k2);
                    if (!(k instanceof FieldInsnNode) || !((FieldInsnNode) k).owner.equals("go") || !((FieldInsnNode) k).name.equals("f")
                            || !(k2 instanceof MethodInsnNode) || !((MethodInsnNode) k2).name.equals("size")
                            || k3 == null || k3.getOpcode() != Opcodes.ISUB) {
                        break;
                    }
                    bo.add(k);
                    bo.add(k2);
                    bo.add(k3);
                    k = sau(k3);
                }
                if (k == null || k.getOpcode() != Opcodes.IF_ICMPNE) {
                    break;
                }
                for (AbstractInsnNode x : bo) {
                    mn.instructions.remove(x);
                }
                mn.instructions.insert(a0, new MethodInsnNode(Opcodes.INVOKESTATIC, "KhoO", "chon", "(Ljava/lang/Object;)Z", false));
                ((JumpInsnNode) k).setOpcode(Opcodes.IFEQ);
                dem(can, "o_kho");
                break;
            }
        }
    }

    // 13) Cham vao con vat trong nong trai -> mo menu con vat: ir.a() (cham ban do = di toi)
    //     ... getstatic GameMidlet.i; getstatic ir.u; putfield hn.av; ... -> truoc do: if (ChamVat.cham()) return;
    static void chamVat(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("a") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.PUTFIELD && ((FieldInsnNode) n).owner.equals("hn")
                        && ((FieldInsnNode) n).name.equals("av")) {
                    AbstractInsnNode u = n.getPrevious();
                    while (u != null && u.getOpcode() < 0) {
                        u = u.getPrevious();
                    }
                    AbstractInsnNode g = u == null ? null : u.getPrevious();
                    while (g != null && g.getOpcode() < 0) {
                        g = g.getPrevious();
                    }
                    if (!(g instanceof FieldInsnNode) || !((FieldInsnNode) g).name.equals("i") || !((FieldInsnNode) g).owner.equals("main/GameMidlet")) {
                        break;
                    }
                    InsnList chen = new InsnList();
                    LabelNode tiep = new LabelNode();
                    chen.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ChamVat", "cham", "()Z", false));
                    chen.add(new JumpInsnNode(Opcodes.IFEQ, tiep));
                    chen.add(new InsnNode(Opcodes.RETURN));
                    chen.add(tiep);
                    mn.instructions.insertBefore(g, chen);
                    dem(can, "cham_vat");
                    break;
                }
            }
        }
    }

    // 14) Phim giua khi dang chon con vat (go.aQ = new by(null, 2)) khong co chu -> tren dien thoai cam ung khong ai biet
    //     bam vao dau de mo menu con vat (Ban / cho an). go.c(): aconst_null; iconst_2; invokespecial by.<init>; putstatic go.aQ
    //     -> getstatic kf.O ("Chon") thay cho aconst_null: hien "Chon" o giua thanh phim khi dang chon con vat.
    static void nhanChon(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("c") || !mn.desc.equals("()V")) {
                continue;
            }
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (!(n instanceof FieldInsnNode) || n.getOpcode() != Opcodes.PUTSTATIC || !((FieldInsnNode) n).owner.equals("go")
                        || !((FieldInsnNode) n).name.equals("aQ")) {
                    continue;
                }
                AbstractInsnNode init = truocThat(n), hai = truocThat(init), rong = truocThat(hai);
                if (init instanceof MethodInsnNode && ((MethodInsnNode) init).owner.equals("by") && ((MethodInsnNode) init).name.equals("<init>")
                        && ((MethodInsnNode) init).desc.equals("(Ljava/lang/String;I)V") && hai != null && hai.getOpcode() == Opcodes.ICONST_2
                        && rong != null && rong.getOpcode() == Opcodes.ACONST_NULL) {
                    mn.instructions.set(rong, new FieldInsnNode(Opcodes.GETSTATIC, "kf", "O", "Ljava/lang/String;"));
                    dem(can, "nhan_chon");
                }
            }
        }
    }

    static AbstractInsnNode truocThat(AbstractInsnNode n) {
        AbstractInsnNode u = n == null ? null : n.getPrevious();
        while (u != null && u.getOpcode() < 0) {
            u = u.getPrevious();
        }
        return u;
    }

    //    cx.b() (lenh -17 thong tin may): "2.5.8" -> "2.5.8L" = app doc duoc gia luong lon
    static void phienBan(ClassNode cn, Map<String, Integer> can) {
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode n : mn.instructions.toArray()) {
                if (n instanceof LdcInsnNode && "2.5.8".equals(((LdcInsnNode) n).cst) && mn.name.equals("b") && mn.desc.equals("()V")) {
                    // "L" cuoi: doc duoc gia luong lon; ".19": ban giao lan 19 (may chu: Session.banApp - luoi xem do co trang phuc)
                    ((LdcInsnNode) n).cst = "2.5.8.21L";
                    dem(can, "phien_ban");
                }
                // chuoi thu 3 cuoi lenh -17 (ae.j, may chu bo qua) -> TpMo.tin(): "tp=<thanh pho da chon>;ban=..;hd=.."
                if (n.getOpcode() == Opcodes.GETSTATIC && mn.name.equals("b") && mn.desc.equals("()V")
                        && ((FieldInsnNode) n).owner.equals("ae") && ((FieldInsnNode) n).name.equals("j")
                        && ((FieldInsnNode) n).desc.equals("Ljava/lang/String;")
                        && n.getNext() instanceof MethodInsnNode && ((MethodInsnNode) n.getNext()).name.equals("writeUTF")) {
                    mn.instructions.set(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "TpMo", "tin", "()Ljava/lang/String;", false));
                    dem(can, "tp_tin");
                    continue;
                }
                // byte loai tai nguyen "gy.Y - 1" -> HdAnh.loai(gy.Y - 1): app native ve net cao xin bo anh HD cua may chu
                if (n.getOpcode() == Opcodes.ISUB && mn.name.equals("b") && mn.desc.equals("()V")) {
                    AbstractInsnNode p1 = n.getPrevious(), p2 = p1 == null ? null : p1.getPrevious();
                    AbstractInsnNode nx = n.getNext();
                    if (p1 != null && p1.getOpcode() == Opcodes.ICONST_1 && p2 instanceof FieldInsnNode
                            && ((FieldInsnNode) p2).owner.equals("gy") && ((FieldInsnNode) p2).name.equals("Y")
                            && nx instanceof MethodInsnNode && ((MethodInsnNode) nx).name.equals("writeByte")) {
                        mn.instructions.insert(n, new MethodInsnNode(Opcodes.INVOKESTATIC, "HdAnh", "loai", "(I)I", false));
                        dem(can, "loai_anh");
                    }
                }
            }
        }
    }

    /** lop Cam: x() = do.a().a, y() = do.a().b, dich(dx, dy) cong vao camera (Java khong viet duoc ten lop "do") */
    // camera ("do"): mo truong k (gioi han cuon doc) de dau truong dat goc nhin doc co dinh (san nam tron tren bang dieu khien)
    static void camMo(ClassNode cn, Map<String, Integer> can) {
        for (FieldNode f : cn.fields) {
            if (f.name.equals("k") && f.desc.equals("I") && (f.access & Opcodes.ACC_STATIC) == 0) {
                f.access = (f.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                dem(can, "cam_mo");
            }
        }
    }

    static byte[] taoCam() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_1, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, "Cam", null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        for (String f : new String[]{"a", "b"}) {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, f.equals("a") ? "x" : "y", "()I", null, null);
            mv.visitCode();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "do", "a", "()Ldo;", false);
            mv.visitFieldInsn(Opcodes.GETFIELD, "do", f, "I");
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dich", "(II)V", null, null);
        mv.visitCode();
        for (int k = 0; k < 2; k++) {
            String f = k == 0 ? "a" : "b";
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "do", "a", "()Ldo;", false);
            mv.visitInsn(Opcodes.DUP);
            mv.visitFieldInsn(Opcodes.GETFIELD, "do", f, "I");
            mv.visitVarInsn(Opcodes.ILOAD, k);
            mv.visitInsn(Opcodes.IADD);
            mv.visitFieldInsn(Opcodes.PUTFIELD, "do", f, "I");
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        // datDoc(y): camera doc co dinh = y (dich d, gioi han k >= y, vi tri b)
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "datDoc", "(I)V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "do", "a", "()Ldo;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "do", "d", "I");
        org.objectweb.asm.Label du = new org.objectweb.asm.Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.GETFIELD, "do", "k", "I");
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, du);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "do", "k", "I");
        mv.visitLabel(du);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "do", "b", "I");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        // datNgang(x): dich ngang cua camera (c) - camera tu truot muot toi (do.c())
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "datNgang", "(I)V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "do", "a", "()Ldo;", false);
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "do", "c", "I");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
