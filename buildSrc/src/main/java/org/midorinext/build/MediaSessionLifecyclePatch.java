package org.midorinext.build;

import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class MediaSessionLifecyclePatch {
    static final String DELEGATE = "mozilla/components/feature/media/service/MediaSessionServiceDelegate";
    static final String REQUEST = DELEGATE + "$startForeground$1";
    private static final String SERVICE = "mozilla/components/feature/media/service/AbstractMediaSessionService";
    private static final String HOOKS = "org/midorinext/android/mozac/media/MediaSessionService";
    private static final String STOP = "stopForeground$feature_media";
    private static final String RUNNABLE = "java/lang/Runnable";
    private static final String SESSION = "(Lmozilla/components/browser/state/state/SessionState;)V";

    private MediaSessionLifecyclePatch() {}

    static boolean hasSynchronousPromotion(byte[] original) {
        int[] calls = new int[1];
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("startForeground$feature_media") || !descriptor.equals(SESSION)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(SERVICE) && name.equals("startForeground")
                                && descriptor.equals("(ILandroid/app/Notification;)V")) calls[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls[0] == 1;
    }

    static byte[] apply(byte[] original, String className) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean delegate = className.equals(DELEGATE);
        int[] patched = new int[5];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                if (delegate) {
                    interfaces = Arrays.copyOf(interfaces, interfaces.length + 1);
                    interfaces[interfaces.length - 1] = RUNNABLE;
                }
                super.visit(version, access, name, signature, parent, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!delegate) {
                    return name.equals("invokeSuspend") ? patchPromotion(method, patched) : method;
                }
                if (name.equals("handleMediaPaused") && descriptor.equals(SESSION)) {
                    return patchPause(method, patched);
                }
                if (name.equals("handleMediaPlaying") && descriptor.equals(SESSION)) {
                    return cancelPendingStop(method, patched, 1);
                }
                if (name.equals(STOP) && descriptor.equals("()V")) {
                    return cancelPendingStop(method, patched, 2);
                }
                if (name.equals("shutdown$feature_media") && descriptor.equals("()V")) {
                    return cancelPendingStop(method, patched, 3);
                }
                return method;
            }

            @Override
            public void visitEnd() {
                if (delegate) {
                    MethodVisitor run = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "run", "()V", null, null);
                    run.visitCode();
                    run.visitVarInsn(Opcodes.ALOAD, 0);
                    run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DELEGATE, STOP, "()V", false);
                    run.visitInsn(Opcodes.RETURN);
                    run.visitMaxs(0, 0);
                    run.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);
        int[] expected = delegate ? new int[]{1, 1, 1, 1, 0} : new int[]{0, 0, 0, 0, 1};
        if (!Arrays.equals(patched, expected)) {
            throw new IllegalStateException("Mozilla media service lifecycle changed; review " + className);
        }
        return writer.toByteArray();
    }

    private static MethodVisitor cancelPendingStop(MethodVisitor method, int[] patched, int index) {
        return new MethodVisitor(Opcodes.ASM9, method) {
            @Override
            public void visitCode() {
                super.visitCode();
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitFieldInsn(Opcodes.GETFIELD, DELEGATE, "service", "L" + SERVICE + ";");
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "cancelForegroundStop", "(Landroid/app/Service;)V", false);
                patched[index]++;
            }
        };
    }

    private static MethodVisitor patchPause(MethodVisitor method, int[] patched) {
        return new MethodVisitor(Opcodes.ASM9, method) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(DELEGATE) && name.equals(STOP) && descriptor.equals("()V")) {
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, DELEGATE, "service", "L" + SERVICE + ";");
                    super.visitInsn(Opcodes.SWAP);
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, DELEGATE, "isForegroundService", "Z");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "deferForegroundStop", "(Landroid/app/Service;Ljava/lang/Runnable;Z)V", false);
                    patched[0]++;
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    private static MethodVisitor patchPromotion(MethodVisitor method, int[] patched) {
        return new MethodVisitor(Opcodes.ASM9, method) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(SERVICE) && name.equals("startForeground")
                        && descriptor.equals("(ILandroid/app/Notification;)V")) {
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, REQUEST, "$generation", "I");
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, REQUEST, "this$0", "L" + DELEGATE + ";");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, DELEGATE, "access$getFocusRequestGeneration$p", "(L" + DELEGATE + ";)I", false);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "startCurrentForeground", "(Landroid/app/Service;ILandroid/app/Notification;II)V", false);
                    patched[4]++;
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }
}
