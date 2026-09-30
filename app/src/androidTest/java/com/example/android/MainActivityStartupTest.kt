package org.midorinext.android.integration

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.MainActivity

@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityStaysOpenAfterInitialComposition() {
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertFalse(activity.isDestroyed)
        }
    }
}
