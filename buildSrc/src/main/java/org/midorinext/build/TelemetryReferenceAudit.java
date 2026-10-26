package org.midorinext.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class TelemetryReferenceAudit extends ClassVisitor {
    private final String className;

    private TelemetryReferenceAudit(String className) {
        super(Opcodes.ASM9);
        this.className = className;
    }

    static void assertClean(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        reader.accept(new TelemetryReferenceAudit(reader.getClassName()), ClassReader.SKIP_DEBUG);
    }

    private void check(String name) {
        if (MozillaTelemetryStripper.isTelemetryClass(name)) {
            throw new IllegalStateException("Telemetry reference remains in " + className + ": " + name);
        }
    }

    private void checkType(Type type) {
        if (type.getSort() == Type.METHOD) {
            for (Type argument : type.getArgumentTypes()) checkType(argument);
            checkType(type.getReturnType());
        } else if (type.getSort() == Type.ARRAY) {
            checkType(type.getElementType());
        } else if (type.getSort() == Type.OBJECT) {
            check(type.getInternalName());
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        check(name);
        check(superName);
        for (String iface : interfaces) check(iface);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        checkType(Type.getType(descriptor));
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        checkType(Type.getMethodType(descriptor));
        if (exceptions != null) for (String exception : exceptions) check(exception);
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                check(owner);
                checkType(Type.getMethodType(desc));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                check(owner);
                checkType(Type.getType(desc));
            }

            @Override
            public void visitTypeInsn(int opcode, String type) { check(type); }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type) checkType((Type) value);
                if (value instanceof Handle) checkHandle((Handle) value);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... arguments) {
                checkType(Type.getMethodType(desc));
                checkHandle(bootstrap);
                for (Object argument : arguments) {
                    if (argument instanceof Handle) checkHandle((Handle) argument);
                    if (argument instanceof Type) checkType((Type) argument);
                }
            }

            private void checkHandle(Handle handle) {
                check(handle.getOwner());
                checkType(Type.getType(handle.getDesc()));
            }
        };
    }
}
