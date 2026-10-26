package org.midorinext.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class GeckoPortLifecyclePatch {
    static final String PORT = "org/mozilla/geckoview/WebExtension$Port";
    private static final String REMOTE_DISCONNECT = "(Lorg/mozilla/gecko/util/EventCallback;)V";

    private GeckoPortLifecyclePatch() {}

    static byte[] apply(byte[] original) {
        ClassReader reader = new ClassReader(original);
        if (!reader.getClassName().equals(PORT)) return original;

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] patched = {0};
        boolean[] hasDisconnected = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (name.equals("disconnected") && descriptor.equals("Z") && (access & Opcodes.ACC_STATIC) == 0) {
                    hasDisconnected[0] = true;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean cleanup = name.equals("disconnected") && descriptor.equals("()V");
                boolean remote = name.equals("disconnectFromExtension") && descriptor.equals(REMOTE_DISCONNECT);
                if (!cleanup && !remote) return method;
                if ((access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    throw new IllegalStateException("Unexpected GeckoView Port disconnect method");
                }
                patched[0]++;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label open = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitFieldInsn(Opcodes.GETFIELD, PORT, "disconnected", "Z");
                        super.visitJumpInsn(Opcodes.IFEQ, open);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(open);
                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        if (cleanup) {
                            // Native shutdown must run once, including when a delegate reenters disconnect().
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitFieldInsn(Opcodes.PUTFIELD, PORT, "disconnected", "Z");
                        }
                    }
                };
            }
        }, 0);
        if (patched[0] != 2 || !hasDisconnected[0]) {
            throw new IllegalStateException("GeckoView Port lifecycle changed; review the disconnect patch");
        }
        return writer.toByteArray();
    }
}
