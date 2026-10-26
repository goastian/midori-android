package org.midorinext.build;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class GeckoPortLifecyclePatchTest {
    @Test
    public void upstreamLifecycleReproducesLateDisconnectCrash() throws Exception {
        Object port = port(false);
        call(port, "disconnect");
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> call(port, "remoteDisconnect"));
        assertTrue(error.getCause() instanceof NullPointerException);
    }

    @Test
    public void lateRemoteDisconnectDoesNotReleaseNativeDispatcherAgain() throws Exception {
        Object port = port(true);
        call(port, "disconnect");
        call(port, "remoteDisconnect");
        call(port, "remoteDisconnect");
        assertEquals(1, count(port, "shutdowns"));
        assertEquals(0, count(port, "notifications"));
    }

    @Test
    public void duplicateRemoteDisconnectNotifiesDelegateOnce() throws Exception {
        Object port = port(true);
        call(port, "remoteDisconnect");
        call(port, "remoteDisconnect");
        call(port, "disconnect");
        assertEquals(1, count(port, "shutdowns"));
        assertEquals(1, count(port, "notifications"));
    }

    @Test
    public void delegateMayDisconnectPortDuringRemoteNotification() throws Exception {
        Object port = port(true);
        port.getClass().getField("reenterFromDelegate").setBoolean(port, true);
        call(port, "remoteDisconnect");
        assertEquals(1, count(port, "shutdowns"));
        assertEquals(1, count(port, "notifications"));
    }

    @Test
    public void unrelatedClassesRemainUnchanged() {
        byte[] bytes = emptyClass("OtherClass");
        assertSame(bytes, GeckoPortLifecyclePatch.apply(bytes));
    }

    @Test
    public void changedUpstreamMethodsRequireReview() {
        assertThrows(IllegalStateException.class,
                () -> GeckoPortLifecyclePatch.apply(emptyClass(GeckoPortLifecyclePatch.PORT)));
    }

    private static Object port(boolean patched) throws Exception {
        byte[] bytes;
        try (InputStream input = GeckoPortLifecyclePatchTest.class.getResourceAsStream(
                "/" + GeckoPortLifecyclePatch.PORT + ".class")) {
            bytes = input.readAllBytes();
        }
        byte[] transformed = patched ? GeckoPortLifecyclePatch.apply(bytes) : bytes;
        Class<?> type = new ClassLoader(GeckoPortLifecyclePatchTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(GeckoPortLifecyclePatch.PORT.replace('/', '.'), transformed, 0, transformed.length);
            }
        }.define();
        return type.getConstructor().newInstance();
    }

    private static void call(Object port, String method) throws Exception {
        port.getClass().getMethod(method).invoke(port);
    }

    private static int count(Object port, String field) throws Exception {
        return port.getClass().getField(field).getInt(port);
    }

    private static byte[] emptyClass(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
