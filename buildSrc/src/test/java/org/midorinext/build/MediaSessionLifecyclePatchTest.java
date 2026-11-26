package org.midorinext.build;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MediaSessionLifecyclePatchTest {
    @Test
    public void recognizesTheSynchronousPromotionIn161() {
        assertTrue(MediaSessionLifecyclePatch.hasSynchronousPromotion(promotion(1, "startForeground$feature_media")));
    }

    @Test
    public void missingDuplicateAndUnrelatedCallsStillRequireReview() {
        assertFalse(MediaSessionLifecyclePatch.hasSynchronousPromotion(promotion(0, "startForeground$feature_media")));
        assertFalse(MediaSessionLifecyclePatch.hasSynchronousPromotion(promotion(2, "startForeground$feature_media")));
        assertFalse(MediaSessionLifecyclePatch.hasSynchronousPromotion(promotion(1, "otherMethod")));
    }

    private static byte[] promotion(int calls, String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, MediaSessionLifecyclePatch.DELEGATE, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name,
                "(Lmozilla/components/browser/state/state/SessionState;)V", null, null);
        method.visitCode();
        for (int index = 0; index < calls; index++) {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ICONST_0);
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "mozilla/components/feature/media/service/AbstractMediaSessionService", "startForeground",
                    "(ILandroid/app/Notification;)V", false);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
