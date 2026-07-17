package org.midorinext.android.ui.extensions

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
    fun `technical and interaction data is approved when requested`() {
        val response = approvedPermissionResponse(
            listOf("browsingActivity", "technicalAndInteraction"),
        )

        assertTrue(response.isPermissionsGranted)
        assertTrue(response.isTechnicalAndInteractionDataGranted)
    }
}
