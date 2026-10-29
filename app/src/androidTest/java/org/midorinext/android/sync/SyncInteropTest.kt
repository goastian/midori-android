package org.midorinext.android.sync

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.*
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.storage.bookmarks.BookmarksRepository
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.Page
import org.midorinext.android.storage.history.Visit
import org.midorinext.android.storage.history.HistoryRepository
import java.io.File

@RunWith(AndroidJUnit4::class)
class SyncInteropTest {
    @Test fun desktop_android_round_trip_uses_native_stores() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".synctest")) { "Run only in the isolated syncTest application" }
        val fixture = JSONObject(File(context.filesDir, "sync-interop-fixture.json").readText())
        val components = EntryPointAccessors.fromApplication(context, SyncInteropEntryPoint::class.java)
        val coordinator = components.coordinator()
        val stage = InstrumentationRegistry.getArguments().getString("stage", "receive")
        ActivityScenario.launch(SyncActivity::class.java).use {
            if (stage == "receive") {
                components.logins().syncTransaction { add(LoginEntry("https://desktop-sync-test.example.invalid", "https://desktop-sync-test.example.invalid", username = "desktop-sync-test", password = "synthetic password")) }
                components.autofill().addCreditCard(NewCreditCardFields("desktop-sync-test card", CreditCardNumber.Plaintext("4111111111111111"), "1111", 12, 2032, "visa"))
                components.autofill().addAddress(UpdatableAddressFields("desktop-sync-test address", "", "Desktop street", "", "Bogota", "", "", "CO", "", "desktop-sync-test@example.invalid"))
                coordinator.pair(fixture.getString("server"), fixture.getString("pairing"))
                if (!fixture.optBoolean("accountRecovery")) coordinator.recover(fixture.getString("recoveryCode"))
                coordinator.sync()
                assertEquals("ready", coordinator.ui.value.phase)
                assertNull(coordinator.ui.value.error)
                assertEquals(0, coordinator.ui.value.conflicts)
                assertEquals(1, components.logins().list().size)
                assertEquals(1, components.autofill().getAllCreditCards().size)
                assertEquals(1, components.autofill().getAllAddresses().size)
            }
            if (stage != "receive") coordinator.sync()
            val persisted = components.vault().read()
            val transport = SyncTransport(persisted.getString("server"))
            val remoteKeys = transport.request("api/v1/crypto/state", persisted.getJSONObject("session").getString("token"))
            SyncKeys(persisted.getString("scope")).use { keys ->
                keys.restore(remoteKeys, persisted.getJSONObject("keys"))
                val collections = persisted.getJSONObject("collections")
                for (name in SyncModels.schemas.keys) {
                    val values = components.stores().snapshot(name, collections.getJSONObject(name), keys)
                    if (stage == "receive") {
                        assertTrue("$name is applied to native storage", values.values.any { value -> value.toString().contains("desktop-sync-test") })
                    } else if (stage == "verify" || stage == "desktop-delete") {
                        assertTrue("$name retains Android data", values.values.any { value -> value.toString().contains("android-sync-test") })
                        if (stage == "desktop-delete") assertFalse("$name applies desktop deletion", values.values.any { value -> value.toString().contains("desktop-sync-test") })
                    }
                }
            }
            if (stage == "receive") {
                components.bookmarks().addItem("bookmarks_root", "https://android-sync-test.example.invalid/bookmark", "android-sync-test bookmark", null).getOrThrow()
                val time = fixture.getLong("visitMs") + 3000
                val dao = components.historyDatabase().historyDao()
                dao.insertIfNeeded(Page("https://android-sync-test.example.invalid/history", "android-sync-test history"))
                dao.insert(Visit("https://android-sync-test.example.invalid/history", time, VisitType.LINK))
                components.logins().syncTransaction { add(LoginEntry("https://android-sync-test.example.invalid", "https://android-sync-test.example.invalid", username = "android-sync-test", password = "synthetic password")) }
                components.autofill().addCreditCard(NewCreditCardFields("android-sync-test card", CreditCardNumber.Plaintext("5555555555554444"), "4444", 12, 2032, "mastercard"))
                components.autofill().addAddress(UpdatableAddressFields("android-sync-test address", "", "Android street", "", "Bogota", "", "110111", "CO", "", "android-sync-test@example.invalid"))
                components.forms().request("apply", "identity" to obj("fieldname" to "city", "value" to "android-sync-test form"),
                    "value" to obj("version" to 1, "fieldname" to "city", "value" to "android-sync-test form", "firstUsed" to time * 1000, "lastUsed" to time * 1000, "timesUsed" to 1))
                coordinator.sync()
            } else if (stage == "verify" || stage == "desktop-delete") {
                coordinator.sync()
                val data = components.vault().read()
                assertEquals(0, coordinator.ui.value.conflicts)
                for (name in SyncModels.schemas.keys) assertFalse("$name is complete", data.getJSONObject("collections").getJSONObject(name).has("error"))
            } else if (stage == "delete") {
                val nodes = components.bookmarks().getBookmarksWithUrl("https://android-sync-test.example.invalid/bookmark").getOrThrow()
                nodes.forEach { components.bookmarks().deleteNode(it.guid).getOrThrow() }
                components.history().deleteVisitsFor("https://android-sync-test.example.invalid/history")
                components.logins().list().filter { login -> login.origin == "https://android-sync-test.example.invalid" }.forEach { login -> components.logins().delete(login.guid) }
                components.autofill().getAllCreditCards().filter { card -> card.billingName.startsWith("android-sync-test") }.forEach { card -> components.autofill().deleteCreditCard(card.guid) }
                components.autofill().getAllAddresses().filter { address -> address.name.startsWith("android-sync-test") }.forEach { address -> components.autofill().deleteAddress(address.guid) }
                components.forms().request("apply", "identity" to obj("fieldname" to "city", "value" to "android-sync-test form"), "value" to null)
                coordinator.sync()
            }
            assertEquals(0, coordinator.ui.value.pending)
            assertNull(coordinator.ui.value.error)
            File(context.filesDir, "sync-interop-result.json").writeText(obj("stage" to stage, "collections" to 6, "conflicts" to coordinator.ui.value.conflicts, "pending" to coordinator.ui.value.pending).toString())
        }
    }
}
