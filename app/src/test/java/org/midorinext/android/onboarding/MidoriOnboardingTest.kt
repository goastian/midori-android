/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.onboarding

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.ext.components
import org.midorinext.android.ext.settings
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.helpers.perf.TestStrictModeManager
import org.midorinext.android.onboarding.MidoriOnboarding.Companion.CURRENT_ONBOARDING_VERSION
import org.midorinext.android.onboarding.MidoriOnboarding.Companion.LAST_VERSION_ONBOARDING_KEY
import org.midorinext.android.perf.StrictModeManager
import org.midorinext.android.utils.Settings

@RunWith(MidoriRobolectricTestRunner::class)
class MidoriOnboardingTest {

    private lateinit var onboarding: MidoriOnboarding
    private lateinit var preferences: SharedPreferences
    private lateinit var preferencesEditor: SharedPreferences.Editor
    private lateinit var settings: Settings

    @Before
    fun setup() {
        preferences = mockk()
        preferencesEditor = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        val context = mockk<Context>()
        every { preferences.edit() } returns preferencesEditor
        every { context.components.strictMode } returns TestStrictModeManager() as StrictModeManager
        every { context.getSharedPreferences(any(), MODE_PRIVATE) } returns preferences
        every { context.settings() } returns settings

        onboarding = MidoriOnboarding(context)
    }

    @Test
    fun testUserHasBeenOnboarded() {
        every {
            preferences.getInt(LAST_VERSION_ONBOARDING_KEY, any())
        } returns 0
        assertFalse(onboarding.userHasBeenOnboarded())

        every {
            preferences.getInt(LAST_VERSION_ONBOARDING_KEY, any())
        } returns CURRENT_ONBOARDING_VERSION
        assertTrue(onboarding.userHasBeenOnboarded())
    }

    @Test
    fun testFinish() {
        settings.showHomeOnboardingDialog = true

        onboarding.finish()

        assertFalse(settings.showHomeOnboardingDialog)
        verify { preferencesEditor.putInt(LAST_VERSION_ONBOARDING_KEY, CURRENT_ONBOARDING_VERSION) }
    }
}
