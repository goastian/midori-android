/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.util.Locale

class SupportUtilsTest {

    @Test
    @Ignore("Fix this as soon as the appropriate Midori pages get available. Note: As for now only English locale is supported.")
    fun getSumoURLForTopic() {
        assertEquals(
            "https://support.mozilla.org/1/mobile/1.6/Android/en-US/common-myths-about-private-browsing",
            SupportUtils.getSumoURLForTopic(mockContext("1.6"), SupportUtils.SumoTopic.PRIVATE_BROWSING_MYTHS, Locale("en", "US"))
        )
        assertEquals(
            "https://support.mozilla.org/1/mobile/20/Android/fr/tracking-protection-firefox-android",
            SupportUtils.getSumoURLForTopic(mockContext("2 0"), SupportUtils.SumoTopic.TRACKING_PROTECTION, Locale("fr"))
        )
    }

    @Test
    @Ignore("Fix this as soon as a Midori FAQ page gets available. Note: As for now only English locale is supported.")
    fun getGenericSumoURLForTopic() {
        assertEquals(
            "https://support.mozilla.org/en-GB/kb/faq-android",
            SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.HELP, Locale("en", "GB"))
        )
        assertEquals(
            "https://support.mozilla.org/de/kb/your-rights",
            SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.YOUR_RIGHTS, Locale("de"))
        )
    }

    @Test
    @Ignore("Fix this as soon as the appropriate Midori pages get available. Note: As for now only English locale is supported.")
    fun getMozillaPageUrl() {
        assertEquals(
            "https://www.mozilla.org/en-US/about/manifesto/",
            SupportUtils.getMozillaPageUrl(SupportUtils.MozillaPage.MANIFESTO, Locale("en", "US"))
        )
        assertEquals(
            "https://www.mozilla.org/zh/privacy/midori/",
            SupportUtils.getMozillaPageUrl(SupportUtils.MozillaPage.PRIVATE_NOTICE, Locale("zh"))
        )
    }

    private fun mockContext(versionName: String): Context {
        val context: Context = mockk()
        val packageManager: PackageManager = mockk()
        val packageInfo = PackageInfo()

        every { context.packageName } returns "org.midorinext.android"
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("org.midorinext.android", 0) } returns packageInfo
        packageInfo.versionName = versionName

        return context
    }
}
