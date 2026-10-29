package org.midorinext.android.sync

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncAccountAccessTest {
    @Test fun mobile_creates_an_account_that_desktop_can_open_without_codes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".synctest"))
        val fixture = JSONObject(File(context.filesDir, "sync-interop-fixture.json").readText())
        assertFalse(fixture.has("recoveryCode"))
        val components = EntryPointAccessors.fromApplication(context, SyncInteropEntryPoint::class.java)
        val coordinator = components.coordinator()
        ActivityScenario.launch(SyncActivity::class.java).use {
            val url = "https://account-recovery-android.example.invalid/"
            components.bookmarks().addItem("bookmarks_root", url, "Android account recovery fixture", null).getOrThrow()
            coordinator.pair(fixture.getString("server"), fixture.getString("pairing"))
            coordinator.sync()
            assertEquals("ready", coordinator.ui.value.phase)
            assertNull(coordinator.ui.value.error)
            val stored = components.vault().read()
            val remote = SyncTransport(stored.getString("server")).request("api/v1/crypto/state", stored.getJSONObject("session").getString("token"))
            assertTrue(remote.getBoolean("account_recovery"))
            File(context.filesDir, "sync-account-access-result.json").writeText(obj(
                "expectedActiveKeyId" to remote.getString("active_key_id"),
                "expectedIdentityKeyId" to remote.getJSONObject("identity_key").getString("key_id"),
                "androidBookmark" to url, "recoveryCodeUsed" to false,
            ).toString())
        }
    }
}
