package org.midorinext.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.midorinext.android.ui.extensions.confirmInstallPermissions

@RunWith(AndroidJUnit4::class)
class InstallPromptDeviceTest {
    @Test
    fun answeringTheSameGeckoPromptTwiceDoesNotCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val result = GeckoResult<Boolean>()
            val onConfirm = { _: PermissionPromptResponse ->
                result.complete(true)
                Unit
            }

            assertTrue(confirmInstallPermissions(emptyList(), onConfirm))
            assertFalse(confirmInstallPermissions(emptyList(), onConfirm))
        }
    }
}
