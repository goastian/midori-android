/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android

import android.app.Activity
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
import android.net.Uri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import mozilla.components.feature.intent.processing.IntentProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.components.IntentProcessorType
import org.midorinext.android.components.IntentProcessors
import org.midorinext.android.customtabs.ExternalAppBrowserActivity
import org.midorinext.android.ext.components
import org.midorinext.android.ext.settings
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.helpers.perf.TestStrictModeManager
import org.midorinext.android.shortcut.NewTabShortcutIntentProcessor
import org.midorinext.android.utils.Settings
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(MidoriRobolectricTestRunner::class)
class IntentReceiverActivityTest {

    private lateinit var settings: Settings
    private lateinit var intentProcessors: IntentProcessors

    @Before
    fun setup() {
        mockkStatic("org.midorinext.android.ext.ContextKt")
        settings = mockk()
        intentProcessors = mockk()

        every { settings.openLinksInAPrivateTab } returns false
        every { intentProcessors.intentProcessor } returns mockIntentProcessor()
        every { intentProcessors.privateIntentProcessor } returns mockIntentProcessor()
        every { intentProcessors.customTabIntentProcessor } returns mockIntentProcessor()
        every { intentProcessors.privateCustomTabIntentProcessor } returns mockIntentProcessor()
        every { intentProcessors.externalAppIntentProcessors } returns emptyList()
        every { intentProcessors.fennecPageShortcutIntentProcessor } returns mockIntentProcessor()
        every { intentProcessors.externalDeepLinkIntentProcessor } returns mockIntentProcessor()
        every { intentProcessors.webNotificationsIntentProcessor } returns mockIntentProcessor()

        coEvery { intentProcessors.intentProcessor.process(any()) } returns true
    }

    @After
    fun teardown() {
        unmockkStatic("org.midorinext.android.ext.ContextKt")
    }

    @Test
    fun `process intent with flag launched from history`() = runTest {
        val intent = Intent()
        intent.flags = FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        val shadow = shadowOf(activity)
        val actualIntent = shadow.peekNextStartedActivity()

        assertEquals(HomeActivity::class.java.name, actualIntent.component?.className)
        assertEquals(true, actualIntent.flags == FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
    }

    @Test
    fun `GIVEN a deeplink intent WHEN processing the intent THEN add the className HomeActivity`() =
        runTest {
            val uri = Uri.parse(BuildConfig.DEEP_LINK_SCHEME + "://settings_wallpapers")
            val intent = Intent("", uri)

            coEvery { intentProcessors.intentProcessor.process(any()) } returns false
            coEvery { intentProcessors.externalDeepLinkIntentProcessor.process(any()) } returns true

            val activity =
                Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
            attachMocks(activity)
            activity.processIntent(intent)

            val shadow = shadowOf(activity)
            val actualIntent = shadow.peekNextStartedActivity()

            assertEquals(HomeActivity::class.java.name, actualIntent.component?.className)
        }

    @Test
    fun `process intent with action OPEN_PRIVATE_TAB`() = runTest {
        val intent = Intent()
        intent.action = NewTabShortcutIntentProcessor.ACTION_OPEN_PRIVATE_TAB

        coEvery { intentProcessors.intentProcessor.process(intent) } returns false
        coEvery { intentProcessors.customTabIntentProcessor.process(intent) } returns false
        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        val shadow = shadowOf(activity)
        val actualIntent = shadow.peekNextStartedActivity()

        assertEquals(HomeActivity::class.java.name, actualIntent.component?.className)
        assertEquals(true, actualIntent.getBooleanExtra(HomeActivity.PRIVATE_BROWSING_MODE, false))
        assertEquals(false, actualIntent.getBooleanExtra(HomeActivity.OPEN_TO_BROWSER, true))
    }

    @Test
    fun `process intent with action OPEN_TAB`() = runTest {
        val intent = Intent()
        intent.action = NewTabShortcutIntentProcessor.ACTION_OPEN_TAB

        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        val shadow = shadowOf(activity)
        val actualIntent = shadow.peekNextStartedActivity()

        assertEquals(HomeActivity::class.java.name, actualIntent.component?.className)
        assertEquals(false, actualIntent.getBooleanExtra(HomeActivity.PRIVATE_BROWSING_MODE, false))
    }

    @Test
    fun `process intent starts Activity`() = runTest {
        val intent = Intent()
        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        val shadow = shadowOf(activity)
        val actualIntent = shadow.peekNextStartedActivity()

        assertEquals(HomeActivity::class.java.name, actualIntent.component?.className)
        assertEquals(true, actualIntent.getBooleanExtra(HomeActivity.OPEN_TO_BROWSER, true))
    }

    @Test
    fun `process intent with launchLinksInPrivateTab set to true`() = runTest {
        every { settings.openLinksInAPrivateTab } returns true

        coEvery { intentProcessors.intentProcessor.process(any()) } returns false
        coEvery { intentProcessors.privateIntentProcessor.process(any()) } returns true

        val intent = Intent()
        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        val shadow = shadowOf(activity)
        val actualIntent = shadow.peekNextStartedActivity()

        val normalProcessor = intentProcessors.intentProcessor
        verify(exactly = 0) { normalProcessor.process(intent) }
        verify { intentProcessors.privateIntentProcessor.process(intent) }
        assertEquals(HomeActivity::class.java.name, actualIntent.component?.className)
        assertTrue(actualIntent.getBooleanExtra(HomeActivity.PRIVATE_BROWSING_MODE, false))
    }

    @Test
    fun `process intent with launchLinksInPrivateTab set to false`() = runTest {
        val intent = Intent()

        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        coVerify(exactly = 0) { intentProcessors.privateIntentProcessor.process(intent) }
        coVerify { intentProcessors.intentProcessor.process(intent) }
    }

    @Test
    fun `process custom tab intent`() = runTest {
        val intent = Intent()
        coEvery { intentProcessors.intentProcessor.process(intent) } returns false
        coEvery { intentProcessors.customTabIntentProcessor.process(intent) } returns true

        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        coVerify(exactly = 0) { intentProcessors.privateCustomTabIntentProcessor.process(intent) }
        coVerify { intentProcessors.customTabIntentProcessor.process(intent) }

        assertEquals(ExternalAppBrowserActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(HomeActivity.OPEN_TO_BROWSER, false))
    }

    @Test
    fun `process private custom tab intent`() = runTest {
        every { settings.openLinksInAPrivateTab } returns true

        val intent = Intent()
        coEvery { intentProcessors.privateCustomTabIntentProcessor.process(intent) } returns true

        val activity = Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get()
        attachMocks(activity)
        activity.processIntent(intent)

        val normalProcessor = intentProcessors.customTabIntentProcessor
        coVerify(exactly = 0) { normalProcessor.process(intent) }
        coVerify { intentProcessors.privateCustomTabIntentProcessor.process(intent) }

        assertEquals(ExternalAppBrowserActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(HomeActivity.OPEN_TO_BROWSER, false))
    }

    @Test
    fun `process web notifications click intent`() {
        val intent = Intent()
        every { intentProcessors.webNotificationsIntentProcessor.process(intent) } returns true
        val activity = spyk(Robolectric.buildActivity(IntentReceiverActivity::class.java, intent).get())
        attachMocks(activity)
        every { activity.launch(any(), any()) } just Runs
        activity.processIntent(intent)

        verify { intentProcessors.webNotificationsIntentProcessor.process(intent) }
        verify { activity.launch(intent, IntentProcessorType.NEW_TAB) }
    }

    private fun attachMocks(activity: Activity) {
        every { activity.settings() } returns settings
        every { activity.components.analytics } returns mockk(relaxed = true)
        every { activity.components.intentProcessors } returns intentProcessors
        every { activity.components.strictMode } returns TestStrictModeManager()
    }

    private inline fun <reified T : IntentProcessor> mockIntentProcessor(): T {
        return mockk {
            coEvery { process(any()) } returns false
        }
    }
}
