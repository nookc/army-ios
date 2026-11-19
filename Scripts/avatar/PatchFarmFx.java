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
        for (String c : new String[]{"KeyTap.class", "LandFx.class", "CayRong.class", "TocDo.class", "DauTruong.class"}) {
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
                case "main/a.class": data = va(va(data, PatchFarmFx::keyTap, can, name), PatchFarmFx::tocDoVongLap, can, name); break;
                case "dm.class": data = va(data, PatchFarmFx::tocDoCaiDat, can, name); break;
                case "kc.class": data = va(data, PatchFarmFx::cayRong, can, name); break;
                case "ir.class": data = va(data, PatchFarmFx::oDat, can, name); break;
                case "go.class": data = va(data, PatchFarmFx::luatDat, can, name); break;
                case "kz.class": data = va(data, PatchFarmFx::lenh118, can, name); break;
                case "b.class": data = va(data, PatchFarmFx::banPhim, can, name); break;
                case "ae.class": data = va(data, PatchFarmFx::dauTruongAe, can, name); break;
                case "hn.class": data = va(data, PatchFarmFx::dauTruongHn, can, name); break;
                case "ce.class": data = va(data, PatchFarmFx::dauTruongCe, can, name); break;
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
                && can.getOrDefault("tocdo_khung", 0) == 2 && can.getOrDefault("tocdo_ve", 0) == 1
                && can.getOrDefault("tocdo_caidat", 0) == 1 && can.getOrDefault("tocdo_doi", 0) == 1
                && can.getOrDefault("dt_k", 0) >= 1 && can.getOrDefault("dt_nen", 0) == 1 && can.getOrDefault("dt_tren", 0) == 1
                && can.getOrDefault("dt_hud", 0) >= 1 && can.getOrDefault("dt_thay", 0) == 1 && can.getOrDefault("dt_sau", 0) == 1
                && can.getOrDefault("dt_tin", 0) == 1;
        if (!ok) {
            System.out.println("PatchFarmFx: SO CHO SUA KHONG KHOP -> dung");
            new File(a[1]).delete();
            System.exit(1);
        }
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

    /** lop Cam: x() = do.a().a, y() = do.a().b, dich(dx, dy) cong vao camera (Java khong viet duoc ten lop "do") */
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
        cw.visitEnd();
        return cw.toByteArray();
    }
}
