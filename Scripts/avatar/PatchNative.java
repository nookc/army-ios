import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Client Avatar cho ban native (iOS + Android, chay qua j2c): va cac lop cua client goc.
 *  1. main.a.keyPressed(-1000) -> dx.b(true): o chu go thang ky tu tu ban phim dien thoai (nhu PatchIosKeys cu).
 *  2. main.a.pointerDragged: bo dong luu moi diem keo vao Vector main.a.au (khong ai doc, man cam ung 120 Hz
 *     sinh hang tram nghin doi tuong khi giu ngon tay -> GC dung game ngay cang lau = "do" khi giu nut di chuyen).
 *     Them KbHook.drag(): truot ngon tren d-pad doi huong ngay.
 *  3. main.a.pointerReleased: them KbHook.release(): nhan trong d-pad ma tha ngoai van nha phim (truoc day phim bi ket,
 *     nhan vat cu di mai).
 *  4. b (ban phim ao cua game): mo cac truong cho KbHook; b.a() / b.a(Graphics): o che do go chu thi dung ban phim
 *     dien thoai (KbHook), khong hien / khong nhan ban phim T9 cua game.
 *  5. main.a.b() (dung lai bo cuc, tao/xoa ban phim ao): them KbHook.sync() de tat ban phim dien thoai khi het go chu.
 * Dung: java -cp asm.jar:. PatchNative <vao.jar> <ra.jar> <KbHook.class>
 */
public class PatchNative {
    static final Map<String, Integer> done = new TreeMap<>();
    static final Set<String> PUBLIC_B = new HashSet<>(Arrays.asList("b", "c", "d", "e", "f", "g", "l", "u", "v"));

    public static void main(String[] a) throws Exception {
        byte[] hook = read(new FileInputStream(a[2]));
        try (ZipFile zf = new ZipFile(a[0]); ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(a[1]))) {
            List<? extends ZipEntry> es = Collections.list(zf.entries());
            for (ZipEntry e : es) {
                byte[] d = read(zf.getInputStream(e));
                if (e.getName().equals("main/a.class")) d = patchMain(d);
                else if (e.getName().equals("b.class")) d = patchPad(d);
                else if (e.getName().equals("ba.class")) d = patchWeather(d);
                else if (e.getName().equals("do.class")) d = patchCamera(d);
                if (e.getName().equals("KbHook.class")) continue;
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zo.putNextEntry(ne);
                zo.write(d);
                zo.closeEntry();
            }
            ZipEntry ne = new ZipEntry("KbHook.class");
            zo.putNextEntry(ne);
            zo.write(hook);
            zo.closeEntry();
        }
        String[] need = { "keyPressed", "pointerDragged", "pointerReleased", "main.b", "pad.tick", "pad.paint", "pad.fields", "ba.removeAt", "pointerPressed" };
        if (done.get("camera") == null || done.get("camera") != 2) throw new IllegalStateException("camera: " + done);
        for (String n : need) {
            Integer c = done.get(n);
            if (c == null || (c != 1 && !n.equals("pad.fields"))) throw new IllegalStateException("va that bai: " + n + " = " + c + " " + done);
        }
        if (done.get("pad.fields") != PUBLIC_B.size()) throw new IllegalStateException("truong b: " + done);
        System.out.println("ok: " + a[1] + " " + done);
    }

    static void mark(String n) {
        done.merge(n, 1, Integer::sum);
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    static byte[] patchMain(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (name.equals("keyPressed") && desc.equals("(I)V")) {
                    mark("keyPressed");
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            Label go = new Label();
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitIntInsn(Opcodes.SIPUSH, -1000);
                            super.visitJumpInsn(Opcodes.IF_ICMPNE, go);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "dx", "b", "(Z)V", false);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(go);
                        }
                    };
                }
                if (name.equals("pointerDragged") && desc.equals("(II)V")) {
                    mark("pointerDragged");
                    // than moi: main.a.h = x; main.a.i = y; KbHook.drag(); (bo main.a.au.addElement(new dr(x, y)))
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ILOAD, 1);
                    mv.visitFieldInsn(Opcodes.PUTSTATIC, "main/a", "h", "I");
                    mv.visitVarInsn(Opcodes.ILOAD, 2);
                    mv.visitFieldInsn(Opcodes.PUTSTATIC, "main/a", "i", "I");
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "KbHook", "drag", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    return null;
                }
                if (name.equals("pointerReleased") && desc.equals("(II)V")) {
                    mark("pointerReleased");
                    return beforeReturn(mv, "release");
                }
                if (name.equals("pointerPressed") && desc.equals("(II)V")) {
                    mark("pointerPressed");
                    return beforeReturn(mv, "press");
                }
                if (name.equals("b") && desc.equals("()V") && (acc & Opcodes.ACC_STATIC) == 0) {
                    mark("main.b");
                    return beforeReturn(mv, "sync");
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    static MethodVisitor beforeReturn(MethodVisitor mv, final String hook) {
        return new MethodVisitor(Opcodes.ASM9, mv) {
            @Override
            public void visitInsn(int op) {
                if (op == Opcodes.RETURN) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "KbHook", hook, "()V", false);
                }
                super.visitInsn(op);
            }
        };
    }

    /** ba.b(): Vector.removeElementAt -> KbHook.removeAt (khong nem loi khi danh sach da rong) */
    static byte[] patchWeather(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!name.equals("b") || !desc.equals("()V") || (acc & Opcodes.ACC_STATIC) != 0) return mv;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        if (op == Opcodes.INVOKEVIRTUAL && owner.equals("java/util/Vector") && n.equals("removeElementAt") && d.equals("(I)V")) {
                            mark("ba.removeAt");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "KbHook", "removeAt", "(Ljava/util/Vector;I)V", false);
                            return;
                        }
                        super.visitMethodInsn(op, owner, n, d, itf);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }

    /** do.c() (camera theo nhan vat): moi khung keo camera 1/8 khoang con lai -> tha nut roi ma nen van troi them ~1 giay
     *  (nhin nhu nhan vat tu chay). Doi thanh 1/4: van muot nhung bam sat, dung nhanh. (x << 1 -> x << 2, ca truc x va y) */
    static byte[] patchCamera(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!name.equals("c") || !desc.equals("()V") || (acc & Opcodes.ACC_STATIC) != 0) return mv;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    boolean pending; // vua gap ICONST_1, cho xem lenh sau co phai ISHL

                    void flush() {
                        if (pending) {
                            pending = false;
                            super.visitInsn(Opcodes.ICONST_1);
                        }
                    }

                    @Override
                    public void visitInsn(int op) {
                        if (pending && op == Opcodes.ISHL) {
                            pending = false;
                            mark("camera");
                            super.visitInsn(Opcodes.ICONST_2);
                            super.visitInsn(Opcodes.ISHL);
                            return;
                        }
                        flush();
                        if (op == Opcodes.ICONST_1) {
                            pending = true;
                            return;
                        }
                        super.visitInsn(op);
                    }

                    @Override public void visitIntInsn(int op, int v) { flush(); super.visitIntInsn(op, v); }
                    @Override public void visitVarInsn(int op, int v) { flush(); super.visitVarInsn(op, v); }
                    @Override public void visitTypeInsn(int op, String t) { flush(); super.visitTypeInsn(op, t); }
                    @Override public void visitFieldInsn(int op, String o, String n, String d) { flush(); super.visitFieldInsn(op, o, n, d); }
                    @Override public void visitMethodInsn(int op, String o, String n, String d, boolean i) { flush(); super.visitMethodInsn(op, o, n, d, i); }
                    @Override public void visitJumpInsn(int op, Label l) { flush(); super.visitJumpInsn(op, l); }
                    @Override public void visitLabel(Label l) { flush(); super.visitLabel(l); }
                    @Override public void visitLdcInsn(Object v) { flush(); super.visitLdcInsn(v); }
                    @Override public void visitIincInsn(int v, int inc) { flush(); super.visitIincInsn(v, inc); }
                    @Override public void visitTableSwitchInsn(int a, int b, Label d, Label... l) { flush(); super.visitTableSwitchInsn(a, b, d, l); }
                    @Override public void visitLookupSwitchInsn(Label d, int[] k, Label[] l) { flush(); super.visitLookupSwitchInsn(d, k, l); }
                    @Override public void visitMultiANewArrayInsn(String d, int n) { flush(); super.visitMultiANewArrayInsn(d, n); }
                    @Override public void visitLineNumber(int line, Label start) { flush(); super.visitLineNumber(line, start); }
                    @Override public void visitFrame(int t, int nl, Object[] l, int ns, Object[] st) { flush(); super.visitFrame(t, nl, l, ns, st); }
                    @Override public void visitMaxs(int a, int b) { flush(); super.visitMaxs(a, b); }
                    @Override public void visitEnd() { flush(); super.visitEnd(); }
                };
            }
        }, 0);
        return cw.toByteArray();
    }

    static byte[] patchPad(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public FieldVisitor visitField(int acc, String name, String desc, String sig, Object val) {
                if ((acc & Opcodes.ACC_STATIC) == 0 && PUBLIC_B.contains(name)) {
                    acc = (acc & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                    mark("pad.fields");
                }
                return super.visitField(acc, name, desc, sig, val);
            }

            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!name.equals("a") || (acc & Opcodes.ACC_STATIC) != 0) return mv;
                final boolean paint = desc.equals("(Ljavax/microedition/lcdui/Graphics;)V");
                if (!paint && !desc.equals("()V")) return mv;
                mark(paint ? "pad.paint" : "pad.tick");
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label go = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        if (paint) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "KbHook", "paint", "(Lb;Ljavax/microedition/lcdui/Graphics;)Z", false);
                        } else {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "KbHook", "tick", "(Lb;)Z", false);
                        }
                        super.visitJumpInsn(Opcodes.IFEQ, go);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(go);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
