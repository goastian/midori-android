package org.midorinext.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.mozac.GeckoPreferences
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.preferences.app.DoHProvider
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

@RunWith(AndroidJUnit4::class)
class DohSettingsDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MidoriApplication
    private lateinit var runtime: GeckoRuntime
    private var originalMode = 0
    private var originalUri = ""
    private var originalAutoselect = false

    @Before
    fun remember() {
        runtime = app.geckoRuntime.get()
        originalMode = runtime.settings.trustedRecusiveResolverMode
        originalUri = runtime.settings.trustedRecursiveResolverUri
        originalAutoselect = runtime.settings.dohAutoselectEnabled
    }

    @After
    fun restore() {
        runtime.settings
            .setTrustedRecursiveResolverMode(originalMode)
            .setTrustedRecursiveResolverUri(originalUri)
            .setDohAutoselectEnabled(originalAutoselect)
    }

    private fun applyProvider(provider: DoHProvider) {
        val base = AppPreferencesSerializer.defaultValue
        GeckoPreferences.initialize(
            runtime,
            GeckoPreferences.UserSettings(
                globalPrivacyControl = base.privacyGlobalPrivacyControl,
                fingerprintingProtection = base.privacyFingerprintingProtection,
                cookiePartitioning = base.privacyCookiePartitioning,
                strictTrackingProtection = false,
                trackingProtectionLevel = base.trackingProtectionLevel,
                dohProvider = provider,
            ),
        )
    }

    private fun geckoPref(name: String): Any? {
        val value = AtomicReference<Any?>()
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            GeckoPreferenceController.getGeckoPref(name).accept(
                { pref -> value.set(pref?.value); done.countDown() },
                { error -> Log.e(TAG, "Could not read $name", error); done.countDown() },
            )
        }
        assertTrue("Gecko did not answer for $name", done.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun eventually(name: String, expected: Any): Any? {
        var last: Any? = null
        repeat(20) {
            last = geckoPref(name)
            if (last == expected) return last
            Thread.sleep(250)
        }
        return last
    }

    @Test
    fun everyProviderReachesGeckoWithTheExpectedResolverMode() {
        val expected = mapOf(
            DoHProvider.DOH_OFF to Triple(GeckoRuntimeSettings.TRR_MODE_OFF, "", false),
            DoHProvider.DOH_DEFAULT to Triple(GeckoRuntimeSettings.TRR_MODE_FIRST, "", true),
            DoHProvider.DOH_INCREASED_PROTECTION to Triple(GeckoRuntimeSettings.TRR_MODE_FIRST, CLOUDFLARE, false),
            DoHProvider.DOH_MAX_PROTECTION to Triple(GeckoRuntimeSettings.TRR_MODE_ONLY, CLOUDFLARE, false),
        )

        for ((provider, wanted) in expected) {
            applyProvider(provider)
            val (mode, uri, autoselect) = wanted
            assertEquals("$provider mode", mode, runtime.settings.trustedRecusiveResolverMode)
            assertEquals("$provider uri", uri, runtime.settings.trustedRecursiveResolverUri)
            assertEquals("$provider autoselect", autoselect, runtime.settings.dohAutoselectEnabled)

            val geckoMode = eventually("network.trr.mode", mode)
            val geckoUri = eventually("network.trr.uri", uri)
            Log.i(TAG, "RESULT $provider settings=$wanted gecko network.trr.mode=$geckoMode network.trr.uri=$geckoUri")
            assertEquals("$provider network.trr.mode in Gecko", mode, geckoMode)
            assertEquals("$provider network.trr.uri in Gecko", uri, geckoUri)
        }
    }

    private companion object {
        const val TAG = "DohSettings"
        const val CLOUDFLARE = "https://cloudflare-dns.com/dns-query"
    }
}
