package org.midorinext.android.mozac

import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.midorinext.android.preferences.app.TrackingProtectionLevel
import org.midorinext.android.preferences.app.AppTrackingProtectionMode
import org.midorinext.android.preferences.app.HttpsOnlyLevel
import org.midorinext.android.preferences.app.DoHProvider

/**
 * Firefox 146-151 Security & Privacy Preferences
 *
 * Uses the public GeckoView typed API to enable tracking protection,
 * fingerprinting protection, and cookie controls.
 */
object GeckoPreferences {

    data class UserSettings(
        val globalPrivacyControl: Boolean,
        val fingerprintingProtection: Boolean,
        val cookiePartitioning: Boolean,
        val strictTrackingProtection: Boolean,
        val trackingProtectionLevel: TrackingProtectionLevel,
        val httpsOnlyLevel: HttpsOnlyLevel = HttpsOnlyLevel.OFF,
        val dohProvider: DoHProvider = DoHProvider.DOH_DEFAULT,
        val appTrackingProtectionMode: AppTrackingProtectionMode = AppTrackingProtectionMode.BROWSER_FIRST
    )

    fun initialize(runtime: GeckoRuntime, settings: UserSettings) {
        applyRuntimeSettings(runtime, settings)
        applyContentBlockingSettings(runtime, settings)
        applyHttpsSettings(runtime, settings)
        applyDnsSettings(runtime, settings.dohProvider)
    }

    private fun applyRuntimeSettings(runtime: GeckoRuntime, settings: UserSettings) {
        runtime.settings
            // Global Privacy Control (GPC) header — Firefox 86+
            .setGlobalPrivacyControl(settings.globalPrivacyControl)
            // Fingerprinting protection — Firefox 119+
            .setFingerprintingProtection(settings.fingerprintingProtection)
            .setFingerprintingProtectionPrivateBrowsing(settings.fingerprintingProtection)
            // Cookie partitioning opt-in (prevents cross-site tracking)
            .setCookieBehaviorOptInPartitioning(settings.cookiePartitioning)
            .setCookieBehaviorOptInPartitioningPBM(settings.cookiePartitioning)
    }

    private fun applyContentBlockingSettings(runtime: GeckoRuntime, settings: UserSettings) {
        val hybridSystemModeSelected = settings.appTrackingProtectionMode == AppTrackingProtectionMode.HYBRID_SYSTEM
        val appTrackingProtectionEnabled =
            settings.strictTrackingProtection || settings.trackingProtectionLevel == TrackingProtectionLevel.STRICT
        val effectiveTrackingProtectionLevel = if (appTrackingProtectionEnabled) {
            TrackingProtectionLevel.STRICT
        } else {
            settings.trackingProtectionLevel
        }

        if (hybridSystemModeSelected) {
            // Reserved hook: a future VPN/proxy layer can subscribe here while Gecko keeps browser-first protection.
        }

        runtime.settings.getContentBlocking()
            // Anti-tracking profile changes with user setting.
            .setAntiTracking(when (effectiveTrackingProtectionLevel) {
                TrackingProtectionLevel.STRICT -> ContentBlocking.AntiTracking.STRICT
                TrackingProtectionLevel.STANDARD, TrackingProtectionLevel.CUSTOM -> ContentBlocking.AntiTracking.DEFAULT
                else -> ContentBlocking.AntiTracking.DEFAULT
            })
            // Block third-party tracker cookies in normal mode
            .setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            // Block all third-party cookies in private mode
            .setCookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_NONE)
            // ETP level follows the tracking protection level.
            .setEnhancedTrackingProtectionLevel(
                when (effectiveTrackingProtectionLevel) {
                    TrackingProtectionLevel.STANDARD -> ContentBlocking.EtpLevel.DEFAULT
                    TrackingProtectionLevel.STRICT -> ContentBlocking.EtpLevel.STRICT
                    TrackingProtectionLevel.CUSTOM -> ContentBlocking.EtpLevel.DEFAULT
                    else -> ContentBlocking.EtpLevel.DEFAULT
                }
            )
            // Block cross-site tracking via social media
            .setStrictSocialTrackingProtection(
                appTrackingProtectionEnabled
            )
    }

    private fun applyHttpsSettings(runtime: GeckoRuntime, settings: UserSettings) {
        val allowInsecureConnections = when (settings.httpsOnlyLevel) {
            HttpsOnlyLevel.ALL_TABS -> GeckoRuntimeSettings.HTTPS_ONLY
            HttpsOnlyLevel.PRIVATE_TABS -> GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
            HttpsOnlyLevel.OFF,
            HttpsOnlyLevel.UNRECOGNIZED -> GeckoRuntimeSettings.ALLOW_ALL
        }

        runtime.settings.setAllowInsecureConnections(allowInsecureConnections)
    }

    private fun applyDnsSettings(runtime: GeckoRuntime, settings: DoHProvider) {
        val (mode, uri, dohAutoselect) = when (settings) {
            DoHProvider.DOH_MAX_PROTECTION -> Triple(
                GeckoRuntimeSettings.TRR_MODE_ONLY,
                CLOUDFLARE_DOH_URI,
                false
            )
            DoHProvider.DOH_INCREASED_PROTECTION -> Triple(
                GeckoRuntimeSettings.TRR_MODE_FIRST,
                CLOUDFLARE_DOH_URI,
                false
            )
            DoHProvider.DOH_DEFAULT -> Triple(
                GeckoRuntimeSettings.TRR_MODE_FIRST,
                "",
                true
            )
            DoHProvider.DOH_OFF,
            DoHProvider.UNRECOGNIZED -> Triple(
                GeckoRuntimeSettings.TRR_MODE_OFF,
                "",
                false
            )
        }

        runtime.settings
            .setTrustedRecursiveResolverMode(mode)
            .setTrustedRecursiveResolverUri(uri)
            .setDohAutoselectEnabled(dohAutoselect)
    }

    private const val CLOUDFLARE_DOH_URI = "https://cloudflare-dns.com/dns-query"
}
