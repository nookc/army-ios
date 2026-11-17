import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Mo lai form dang ky co san trong man dang nhap cua client 2.5.8 (lop es):
 *  - es.z (o dang chon) -> public de RegHook dat lai
 *  - es.a(II)V: le.a().a(vector, n) cua Menu -> RegHook.menu(le, vector, n) (them "Dang ky tai khoan")
 *  - es.d(II)V: n == 0 -> RegHook.open(this) (mo form; truoc day chi bat co i nen khong hien gi)
 * Dung: java -cp asm.jar:. PatchReg <vao.jar> <ra.jar> <thu muc chua RegHook.class, RegClose.class | ->
 */
public class PatchReg {
    static final Map<String, Integer> done = new TreeMap<>();

    public static void main(String[] a) throws Exception {
        File hookDir = a[2].equals("-") ? null : new File(a[2]);
        try (ZipFile zf = new ZipFile(a[0]); ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(a[1]))) {
            for (ZipEntry e : Collections.list(zf.entries())) {
                if (e.getName().equals("RegHook.class") || e.getName().equals("RegClose.class")) continue;
                byte[] d = read(zf.getInputStream(e));
                if (e.getName().equals("es.class")) d = patchEs(d);
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zo.putNextEntry(ne);
                zo.write(d);
                zo.closeEntry();
            }
            if (hookDir != null) {
                for (String n : new String[] { "RegHook.class", "RegClose.class" }) {
                    zo.putNextEntry(new ZipEntry(n));
                    zo.write(read(new FileInputStream(new File(hookDir, n))));
                    zo.closeEntry();
                }
            }
        }
        for (String n : new String[] { "es.z", "es.menu", "es.open" }) {
            if (!Integer.valueOf(1).equals(done.get(n))) throw new IllegalStateException("va that bai: " + n + " " + done);
        }
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

    static byte[] patchEs(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public FieldVisitor visitField(int acc, String name, String desc, String sig, Object val) {
                if (name.equals("z") && desc.equals("I")) {
                    mark("es.z");
                    acc = (acc & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }
                return super.visitField(acc, name, desc, sig, val);
            }

            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (name.equals("a") && desc.equals("(II)V")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                            if (op == Opcodes.INVOKEVIRTUAL && owner.equals("le") && n.equals("a") && d.equals("(Ljava/util/Vector;I)V")) {
                                mark("es.menu");
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "RegHook", "menu", "(Lle;Ljava/util/Vector;I)V", false);
                                return;
                            }
                            super.visitMethodInsn(op, owner, n, d, itf);
                        }
                    };
                }
                if (name.equals("d") && desc.equals("(II)V")) {
                    mark("es.open");
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            Label go = new Label();
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitJumpInsn(Opcodes.IFNE, go);
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "RegHook", "open", "(Les;)V", false);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(go);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }
}
