package org.midorinext.build;

import static org.junit.Assert.*;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MozillaTelemetryStripperTest {
    private static final String COUNTER = "mozilla/telemetry/glean/private/CounterMetricType";
    private static final String METRICS = "org/mozilla/appservices/logins/GleanMetrics/LoginsStore";
    private static final String ERRORS = "mozilla/appservices/errorsupport/RustComponentsErrorTelemetry";

    @Test
    public void startupCompletesWithoutTheRustErrorReporter() throws Exception {
        ClassWriter writer = sample();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_3);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "Sample", "value", "I");
        method.visitFieldInsn(Opcodes.GETSTATIC, ERRORS, "INSTANCE", "L" + ERRORS + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ERRORS, "register", "()V", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "Sample", "value", "I");
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IADD);
        finish(method, Opcodes.IRETURN);
        assertEquals(5, run(writer));
    }

    @Test
    public void loginMaintenanceStillRunsWhenItsCountersAreRemoved() throws Exception {
        ClassWriter writer = sample();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        method.visitCode();
        method.visitIntInsn(Opcodes.BIPUSH, 7);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "Sample", "value", "I");
        method.visitFieldInsn(Opcodes.GETSTATIC, METRICS, "INSTANCE", "L" + METRICS + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, METRICS, "getLocalUndecryptableDeleted", "()L" + COUNTER + ";", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "Sample", "value", "I");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, COUNTER, "add", "(I)V", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "Sample", "value", "I");
        finish(method, Opcodes.IRETURN);
        assertEquals(7, run(writer));
    }

    @Test
    public void metricExtrasAreRemovedWithoutLoadingTheirConstructors() throws Exception {
        String extras = METRICS + "$Extra";
        ClassWriter writer = sample();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, extras);
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("error");
        method.visitInsn(Opcodes.LCONST_1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, extras, "<init>", "(Ljava/lang/String;J)V", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, METRICS, "record", "(L" + extras + ";)V", false);
        method.visitInsn(Opcodes.ICONST_4);
        finish(method, Opcodes.IRETURN);
        assertEquals(4, run(writer));
    }

    @Test
    public void wideMetricArgumentsDoNotCorruptTheOperandStack() throws Exception {
        ClassWriter writer = sample();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.LCONST_1);
        method.visitInsn(Opcodes.DCONST_1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, COUNTER, "record", "(JD)V", false);
        method.visitInsn(Opcodes.ICONST_2);
        finish(method, Opcodes.IRETURN);
        assertEquals(2, run(writer));
    }

    @Test
    public void remoteSettingsDoesNotRegisterATelemetryCallback() throws Exception {
        String telemetry = "mozilla/appservices/remotesettings/GleanTelemetry";
        String service = "mozilla/appservices/remotesettings/RemoteSettingsService";
        ClassWriter writer = sample();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitTypeInsn(Opcodes.NEW, telemetry);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, telemetry, "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, service, "setTelemetry", "(Lmozilla/appservices/remotesettings/RemoteSettingsTelemetry;)V", false);
        method.visitInsn(Opcodes.ICONST_1);
        finish(method, Opcodes.IRETURN);
        assertEquals(1, run(writer));
    }

    @Test
    public void telemetryClassesAreDeleted() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, METRICS, null, "java/lang/Object", null);
        writer.visitEnd();
        assertNull(MozillaTelemetryStripper.strip(writer.toByteArray()));
    }

    @Test(expected = IllegalStateException.class)
    public void unexpectedTelemetryInheritanceFailsTheBuild() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "NewMozillaClass", null, COUNTER, null);
        writer.visitEnd();
        MozillaTelemetryStripper.strip(writer.toByteArray());
    }

    private static ClassWriter sample() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Sample", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "I", null, null).visitEnd();
        return writer;
    }

    private static void finish(MethodVisitor method, int returnInstruction) {
        method.visitInsn(returnInstruction);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static Object run(ClassWriter writer) throws Exception {
        writer.visitEnd();
        byte[] stripped = MozillaTelemetryStripper.strip(writer.toByteArray());
        Class<?> sample = new ClassLoader(null) {
            Class<?> define() { return defineClass("Sample", stripped, 0, stripped.length); }
        }.define();
        return sample.getMethod("run").invoke(null);
    }
}
