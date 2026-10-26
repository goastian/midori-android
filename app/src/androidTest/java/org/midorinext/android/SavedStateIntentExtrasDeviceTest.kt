package org.midorinext.android

import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.ui.extensions.ExtensionViewModel

@RunWith(AndroidJUnit4::class)
class SavedStateIntentExtrasDeviceTest {
    @Test
    fun customTabsExtrasDoNotBreakActivityStateSaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtras(
                Bundle().apply {
                    putSparseParcelableArray(
                        "androidx.browser.customtabs.extra.COLOR_SCHEME_PARAMS",
                        SparseArray<Bundle>().apply { put(1, Bundle()) },
                    )
                },
            )
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ExtensionViewModel::class.java]
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            assertEquals(Lifecycle.State.CREATED, scenario.state)
        }
    }
}
