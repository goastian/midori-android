package org.midorinext.android.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Properties

class GeckoBuildMetadataTest {
    @Test
    fun mappingUuidMatchesTheSentryBuildMetadata() {
        val uuid = "9bf7c02e-e5da-30fa-b041-8ff8f21522a2"
        assertEquals(uuid, GeckoBuildMetadata.proguardUuid(metadata(" $uuid ")))
    }

    @Test
    fun missingOrAmbiguousMappingsAreNotAssignedToGeckoProfiles() {
        for (value in listOf("", "not-a-uuid", "1-1-1-1-1", "9bf7c02e-e5da-30fa-b041-8ff8f21522a2,9bf7c02e-e5da-30fa-b041-8ff8f21522a2")) {
            assertNull(GeckoBuildMetadata.proguardUuid(metadata(value)))
        }
        assertNull(GeckoBuildMetadata.proguardUuid(Properties()))
    }

    private fun metadata(uuid: String) = Properties().apply { setProperty("io.sentry.ProguardUuids", uuid) }
}
