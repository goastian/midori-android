package org.midorinext.android.ui.extensions

import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPermissionResponseTest {

    @Test
    fun `required extension permissions are approved`() {
        val response = approvedPermissionResponse(emptyList())

        assertTrue(response.isPermissionsGranted)
        assertFalse(response.isPrivateModeGranted)
        assertFalse(response.isTechnicalAndInteractionDataGranted)
    }

    @Test
    fun `confirming an install prompt that is already complete does not throw`() {
        var completed = false
        val onConfirm: (PermissionPromptResponse) -> Unit = {
            check(!completed) { "result is already complete" }
            completed = true
        }

        assertTrue(confirmInstallPermissions(emptyList(), onConfirm))
        assertFalse(confirmInstallPermissions(emptyList(), onConfirm))
    }

    @Test
    fun `technical and interaction data is approved when requested`() {
        val response = approvedPermissionResponse(
            listOf("browsingActivity", "technicalAndInteraction"),
        )

        assertTrue(response.isPermissionsGranted)
        assertTrue(response.isTechnicalAndInteractionDataGranted)
    }
}
