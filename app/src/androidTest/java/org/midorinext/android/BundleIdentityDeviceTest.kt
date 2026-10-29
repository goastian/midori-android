package org.midorinext.android

import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.midorinext.android.adblock.NativeContentBlockingFeature

class BundleIdentityDeviceTest {
    @Test fun installedBundleMatchesTheMeasuredApk(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MidoriApplication
        context.startActivity(Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        withTimeout(120_000) { app.nativeContentBlockingFeature.get().ready.await() }
        val version = CompletableDeferred<String>()
        instrumentation.runOnMainSync {
            app.geckoRuntime.get().webExtensionController.list().accept({ extensions ->
                version.complete(extensions.orEmpty().firstOrNull {
                    it.id == NativeContentBlockingFeature.EXTENSION_ID
                }?.metaData?.version.orEmpty())
            }, { error -> version.completeExceptionally(error ?: IllegalStateException("Could not list extensions")) })
        }
        val installed = withTimeout(30_000) { version.await() }
        instrumentation.sendStatus(0, Bundle().apply { putString("installedBundleVersion", installed) })
        val expected = InstrumentationRegistry.getArguments().getString("expectedBundleVersion", BuildConfig.ADBLOCK_BUNDLE_VERSION)
        assertEquals(expected, installed)
    }
}
