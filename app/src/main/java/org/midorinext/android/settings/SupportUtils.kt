/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import mozilla.components.support.ktx.android.content.getColorFromAttr
import org.midorinext.android.BuildConfig
import org.midorinext.android.IntentReceiverActivity
import org.midorinext.android.R
import org.midorinext.android.settings.account.AuthIntentReceiverActivity
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale

object SupportUtils {
    const val RATE_APP_URL = "market://details?id=" + BuildConfig.APPLICATION_ID
    const val WIKIPEDIA_URL = "https://www.wikipedia.org/"
    const val MIDORI_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
    const val GOOGLE_URL = "https://www.google.com/"
    const val EXPEDIA_URL = "https://expedia.com/affiliate/WyybenA"
    const val AMAZON_URL = "https://www.amazon.com/?&_encoding=UTF8&tag=astian-20&linkCode=ur2&linkId=62378ad479147f7dc4e1be82192e6d10&camp=1789&creative=9325"
    const val EBAY_URL = "https://ebay.us/mcNp23"
    const val ENEBA_URL = "https://www.eneba.com/latam/?af_id=Astian"
    const val HOTELS_URL = "https://www.hotels.com/affiliate/Ya3mgrV"
    const val ALIEXPRESS_URL = "https://s.click.aliexpress.com/e/_DCpnkVX"
    const val GOOGLE_US_URL = "https://www.google.com/webhp?client=midori-b-1-m&channel=ts"
    const val GOOGLE_XX_URL = "https://www.google.com/webhp?client=midori-b-m&channel=ts"

    enum class SumoTopic(internal val topicStr: String) {
        HELP("faq-android"),
        PRIVATE_BROWSING_MYTHS("common-myths-about-private-browsing"),
        YOUR_RIGHTS("your-rights"),
        TRACKING_PROTECTION("tracking-protection-midori-android"),
        TOTAL_COOKIE_PROTECTION("enhanced-tracking-protection-android"),
        SEND_TABS("send-tab-preview"),
        SET_AS_DEFAULT_BROWSER("set-midori-android-default"),
        SEARCH_SUGGESTION("how-search-midori-android"),
        CUSTOM_SEARCH_ENGINES("custom-search-engines"),
        SYNC_SETUP("how-set-sync-midori-android"),
        QR_CAMERA_ACCESS("qr-camera-access"),
        SMARTBLOCK("smartblock-enhanced-tracking-protection"),
        SPONSOR_PRIVACY("sponsor-privacy"),
        HTTPS_ONLY_MODE("https-only-mode-midori-android"),
        UNSIGNED_ADDONS("unsigned-addons"),
    }

    enum class MozillaPage(internal val path: String) {
        PRIVATE_NOTICE("astian-privacy-policies/"),
        MANIFESTO("")
    }

    /**
     * Gets a support page URL for the corresponding topic.
     */
    fun getSumoURLForTopic(
        context: Context,
        topic: SumoTopic,
        locale: Locale = Locale.getDefault()
    ): String {
        val escapedTopic = getEncodedTopicUTF8(topic.topicStr)
        // Remove the whitespace so a search is not triggered:
        val osTarget = "midori-android"
        return "https://astian.org/knowledge/$osTarget/"
    }

    /**
     * Gets a support page URL for the corresponding topic.
     * Used when the app version and os are not part of the URL.
     */
    fun getGenericSumoURLForTopic(topic: SumoTopic, locale: Locale = Locale.getDefault()): String {
        val escapedTopic = getEncodedTopicUTF8(topic.topicStr)
        val osTarget = "midori-android"
        return "https://astian.org/docs/$osTarget/$escapedTopic"
    }

    fun getMidoriAccountSumoUrl(): String {
        return "https://support.mozilla.org/kb/access-mozilla-services-firefox-account"
    }

    fun getMozillaPageUrl(page: MozillaPage, locale: Locale = Locale.getDefault()): String {
        val path = page.path
        val langTag = getLanguageTag(locale)
        return "https://astian.org/$path"
    }

    fun createCustomTabIntent(context: Context, url: String): Intent = CustomTabsIntent.Builder()
        .setInstantAppsEnabled(false)
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder().setToolbarColor(context.getColorFromAttr(R.attr.layer1)).build()
        )
        .build()
        .intent
        .setData(url.toUri())
        .setClassName(context, IntentReceiverActivity::class.java.name)
        .setPackage(context.packageName)

    fun createAuthCustomTabIntent(context: Context, url: String): Intent =
        createCustomTabIntent(context, url).setClassName(context, AuthIntentReceiverActivity::class.java.name)

    private fun getEncodedTopicUTF8(topic: String): String {
        try {
            return URLEncoder.encode(topic, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException("utf-8 should always be available", e)
        }
    }

    private fun getLanguageTag(locale: Locale): String {
        val language = locale.language
        val country = locale.country // Can be an empty string.
        return if (country.isEmpty()) language else "$language-$country"
    }
}
