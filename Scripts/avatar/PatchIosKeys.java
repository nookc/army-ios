import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/**
 * Client Avatar cho iOS (phoneME): thêm mã phím riêng -1000 vào main.a.keyPressed
 * -> gọi dx.b(true) (ô chữ chuyển sang gõ thẳng ký tự, không T9) trước khi app gửi chữ từ bàn phím iPhone.
 * Dùng: java -cp asm.jar:. PatchIosKeys <vào.jar> <ra.jar>
 */
public class PatchIosKeys {
    static int done;

    public static void main(String[] a) throws Exception {
        try (ZipFile zf = new ZipFile(a[0]); ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(a[1]))) {
            List<? extends ZipEntry> es = Collections.list(zf.entries());
            for (ZipEntry e : es) {
                byte[] d = read(zf.getInputStream(e));
                if (e.getName().equals("main/a.class")) d = patch(d);
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zo.putNextEntry(ne);
                zo.write(d);
                zo.closeEntry();
            }
        }
        if (done != 1) throw new IllegalStateException("khong tim thay main.a.keyPressed(I)V");
        System.out.println("ok: " + a[1]);
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }

    static byte[] patch(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);
                if (!name.equals("keyPressed") || !desc.equals("(I)V")) return mv;
                done++;
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
        }, 0);
        return cw.toByteArray();
    }
}
