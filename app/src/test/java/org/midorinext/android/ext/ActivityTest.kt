/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ext

import android.app.Activity
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(MidoriRobolectricTestRunner::class)
class ActivityTest {

    // This will be addressed on https://github.com/mozilla-mobile/fenix/issues/17804
    @Suppress("DEPRECATION")
    @Test
    fun testEnterImmersiveMode() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val window = activity.window

        // Turn off Keep Screen on Flag if it is on
        if (shadowOf(window).getFlag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Make sure that System UI flags are not set before the test
        val flags = arrayOf(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION, View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN, View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, View.SYSTEM_UI_FLAG_FULLSCREEN, View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (flags.any { f -> (window.decorView.systemUiVisibility and f) == f }) {
            window.decorView.systemUiVisibility = 0
        }

        // Run
        activity.enterToImmersiveMode()

        // Test
        for (f in flags) assertEquals(f, window.decorView.systemUiVisibility and f)
        assertTrue(shadowOf(window).getFlag(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON))
    }
}
