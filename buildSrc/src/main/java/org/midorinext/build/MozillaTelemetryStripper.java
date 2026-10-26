package org.midorinext.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class MozillaTelemetryStripper {
    private MozillaTelemetryStripper() {}

    public static boolean isTelemetryClass(String name) {
        return name != null && (name.startsWith("mozilla/telemetry/glean/")
                || name.contains("/GleanMetrics/")
                || name.startsWith("mozilla/appservices/errorsupport/RustComponentsErrorTelemetry")
                || name.startsWith("mozilla/appservices/errorsupport/ErrorEventSink")
                || name.startsWith("mozilla/appservices/errorsupport/TracingErrorFields")
                || name.equals("mozilla/appservices/logins/LoginsStoreCounterMetrics")
                || name.equals("mozilla/appservices/remotesettings/GleanTelemetry")
                || name.equals("org/mozilla/experiments/nimbus/Nimbus$Pings"));
    }

    private static boolean telemetryType(String descriptor) {
        return descriptor.contains("mozilla/telemetry/glean/")
                || descriptor.contains("/GleanMetrics/");
    }

    public static byte[] strip(byte[] original) {
        String constants = new String(original, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!constants.contains("Glean") && !constants.contains("mozilla/telemetry/glean/")
                && !constants.contains("RustComponentsErrorTelemetry")
                && !constants.contains("ErrorEventSink") && !constants.contains("TracingErrorFields")
                && !constants.contains("LoginsStoreCounterMetrics")) {
            return original;
        }
        ClassReader reader = new ClassReader(original);
        if (isTelemetryClass(reader.getClassName())) {
            return null;
        }
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                if (isTelemetryClass(superName)) {
                    throw new IllegalStateException("Unsupported telemetry superclass in " + name);
                }
                for (String iface : interfaces) {
                    if (isTelemetryClass(iface)) {
                        throw new IllegalStateException("Unsupported telemetry interface in " + name);
                    }
                }
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (telemetryType(descriptor)) {
                    return null;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (telemetryType(descriptor)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        boolean telemetryCallback = owner.equals(
                                "mozilla/appservices/remotesettings/RemoteSettingsService")
                                && method.equals("setTelemetry");
                        if (isTelemetryClass(owner) || telemetryType(desc) || telemetryCallback) {
                            Type[] arguments = Type.getArgumentTypes(desc);
                            for (int i = arguments.length - 1; i >= 0; i--) {
                                pop(arguments[i]);
                            }
                            if (opcode != Opcodes.INVOKESTATIC) {
                                super.visitInsn(Opcodes.POP);
                            }
                            defaultValue(Type.getReturnType(desc));
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        if (isTelemetryClass(owner) || telemetryType(desc)) {
                            if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                                pop(Type.getType(desc));
                            }
                            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
                                super.visitInsn(Opcodes.POP);
                            }
                            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                                defaultValue(Type.getType(desc));
                            }
                            return;
                        }
                        super.visitFieldInsn(opcode, owner, field, desc);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (isTelemetryClass(type)) {
                            if (opcode == Opcodes.NEW) {
                                super.visitInsn(Opcodes.ACONST_NULL);
                            } else if (opcode == Opcodes.INSTANCEOF) {
                                super.visitInsn(Opcodes.POP);
                                super.visitInsn(Opcodes.ICONST_0);
                            } else if (opcode != Opcodes.CHECKCAST) {
                                throw new IllegalStateException("Unsupported telemetry type instruction: " + type);
                            }
                            return;
                        }
                        super.visitTypeInsn(opcode, type);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type && telemetryType(((Type) value).getDescriptor())) {
                            value = Type.getType(Object.class);
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitLocalVariable(String local, String desc, String sig,
                            org.objectweb.asm.Label start, org.objectweb.asm.Label end, int index) {
                        if (!telemetryType(desc)) {
                            super.visitLocalVariable(local, desc, sig, start, end, index);
                        }
                    }

                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local,
                            int numStack, Object[] stack) {
                        super.visitFrame(type, numLocal, cleanFrame(local), numStack, cleanFrame(stack));
                    }

                    private Object[] cleanFrame(Object[] frame) {
                        if (frame == null) return null;
                        Object[] cleaned = frame.clone();
                        for (int i = 0; i < cleaned.length; i++) {
                            if (cleaned[i] instanceof String && isTelemetryClass((String) cleaned[i])) {
                                cleaned[i] = "java/lang/Object";
                            }
                        }
                        return cleaned;
                    }

                    private void pop(Type type) {
                        super.visitInsn(type.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
                    }

                    private void defaultValue(Type type) {
                        switch (type.getSort()) {
                            case Type.VOID: break;
                            case Type.LONG: super.visitInsn(Opcodes.LCONST_0); break;
                            case Type.FLOAT: super.visitInsn(Opcodes.FCONST_0); break;
                            case Type.DOUBLE: super.visitInsn(Opcodes.DCONST_0); break;
                            case Type.OBJECT:
                            case Type.ARRAY: super.visitInsn(Opcodes.ACONST_NULL); break;
                            default: super.visitInsn(Opcodes.ICONST_0);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
