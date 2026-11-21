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
                case "main/a.class": data = va(va(va(data, PatchFarmFx::keyTap, can, name), PatchFarmFx::tocDoVongLap, can, name), PatchFarmFx::veNoi, can, name); break;
                case "dm.class": data = va(data, PatchFarmFx::tocDoCaiDat, can, name); break;
                case "kc.class": data = va(data, PatchFarmFx::cayRong, can, name); break;
                case "ir.class": data = va(va(data, PatchFarmFx::oDat, can, name), PatchFarmFx::chamVat, can, name); break;
                case "go.class": data = va(va(va(data, PatchFarmFx::luatDat, can, name), PatchFarmFx::tromCay, can, name), PatchFarmFx::nhanChon, can, name); break;
                case "kz.class": data = va(data, PatchFarmFx::lenh118, can, name); break;
                case "b.class": data = va(data, PatchFarmFx::banPhim, can, name); break;
                case "ae.class": data = va(va(va(data, PatchFarmFx::dauTruongAe, can, name), PatchFarmFx::cuoiAn, can, name), PatchFarmFx::nutShop, can, name); break;
                case "hn.class": data = va(data, PatchFarmFx::dauTruongHn, can, name); break;
                case "ce.class": data = va(data, PatchFarmFx::dauTruongCe, can, name); break;
                case "do.class": data = va(data, PatchFarmFx::camMo, can, name); break;
                case "ic.class":
                case "bb.class": data = va(data, PatchFarmFx::tatHuongDan, can, name); break;
                case "fx.class": data = va(data, PatchFarmFx::giaLuong, can, name); break;
                case "cx.class": data = va(data, PatchFarmFx::phienBan, can, name); break;
                case "j.class":
                case "ko.class": data = va(data, PatchFarmFx::oKho, can, name); break;
                case "dh.class": data = va(data, PatchFarmFx::chuHoa, can, name); break;
                case "ea.class": data = va(data, PatchFarmFx::nhanBanDo, can, name); break;
                case "dx.class": data = va(data, PatchFarmFx::oChu, can, name); break;
                case "cy.class": data = va(data, PatchFarmFx::danhSach, can, name); break;
                case "gt.class": data = va(va(data, PatchFarmFx::hoiShop, can, name), PatchFarmFx::moTruong, can, name); break;
                case "jx.class": data = va(va(va(data, PatchFarmFx::thongTinXem, can, name), PatchFarmFx::moTruong, can, name), PatchFarmFx::veO, can, name); break;
                case "fz.class": data = va(data, PatchFarmFx::veO, can, name); break;
                case "dp.class": data = va(data, PatchFarmFx::dauShop, can, name); break;
                default: break;
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
                && can.getOrDefault("tocdo_khung", 0) == 2 && can.getOrDefault("tocdo_ve", 0) == 1 && can.getOrDefault("tocdo_ngu", 0) == 2
                && can.getOrDefault("tocdo_caidat", 0) == 1 && can.getOrDefault("tocdo_doi", 0) == 1
                && can.getOrDefault("dt_k", 0) >= 1 && can.getOrDefault("dt_nen", 0) == 1 && can.getOrDefault("dt_tren", 0) == 1
                && can.getOrDefault("dt_hud", 0) >= 1 && can.getOrDefault("dt_thay", 0) == 1 && can.getOrDefault("dt_sau", 0) == 1
                && can.getOrDefault("dt_tin", 0) == 1 && can.getOrDefault("cam_mo", 0) == 1 && can.getOrDefault("dt_ten", 0) == 1
                && can.getOrDefault("hd_tat", 0) == 2 && can.getOrDefault("hd_hoi", 0) == 1
                && can.getOrDefault("gia_luong", 0) == 1 && can.getOrDefault("phien_ban", 0) == 1
                && can.getOrDefault("trom_cay", 0) == 1 && can.getOrDefault("o_kho", 0) == 2
                && can.getOrDefault("cham_vat", 0) == 1 && can.getOrDefault("nhan_chon", 0) == 1
                && can.getOrDefault("cuoi_cho", 0) == 1 && can.getOrDefault("cuoi_kiem", 0) == 1
                && can.getOrDefault("chu_hoa", 0) == 2 && can.getOrDefault("nhan_bd", 0) == 1
                && can.getOrDefault("o_chu", 0) == 1 && can.getOrDefault("o_chu_h", 0) == 1
                && can.getOrDefault("ds_cao", 0) == 1 && can.getOrDefault("ds_cat", 0) == 1
                && can.getOrDefault("nut_shop", 0) == 1 && can.getOrDefault("hoi_shop", 0) == 2
                && can.getOrDefault("tt_xem", 0) == 1 && can.getOrDefault("mo_truong", 0) == 2
                && can.getOrDefault("dau_shop", 0) == 1 && can.getOrDefault("ve_o", 0) == 2
                && can.getOrDefault("ve_preview", 0) == 1 && can.getOrDefault("ve_noi", 0) == 1;
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
                    ((LdcInsnNode) n).cst = "2.5.8.19L";
                    dem(can, "phien_ban");
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
