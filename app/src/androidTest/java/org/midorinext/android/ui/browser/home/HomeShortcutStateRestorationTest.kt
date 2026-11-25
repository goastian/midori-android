package org.midorinext.android.ui.browser.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeShortcutStateRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recreatesShortcutStateThroughComposeSaveableRegistry() {
        val restoration = StateRestorationTester(compose)
        lateinit var state: HomeShortcutUiState
        restoration.setContent { state = rememberHomeShortcutUiState() }
        lateinit var original: HomeShortcutUiState
        compose.runOnIdle {
            original = state
            state.collectionId = "folder-id"
            state.editorParentId = "parent-id"
            state.showEditor = true
            state.busy = true
        }

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            assertNotSame(original, state)
            assertEquals("folder-id", state.collectionId)
            assertEquals("parent-id", state.editorParentId)
            assertTrue(state.showEditor)
            assertFalse(state.busy)
        }
    }

    @Test
    fun incompatibleIntegerKeysFallBackToFreshStateDuringComposition() {
        val registry = object : SaveableStateRegistry by SaveableStateRegistry(null, canBeSaved = { true }) {
            override fun consumeRestored(key: String): Any = arrayListOf(1, 0)
        }
        lateinit var state: HomeShortcutUiState
        compose.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                state = rememberHomeShortcutUiState()
            }
        }

        compose.runOnIdle {
            assertNull(state.collectionId)
            assertEquals("", state.editorParentId)
            assertFalse(state.showEditor)
        }
    }
}
