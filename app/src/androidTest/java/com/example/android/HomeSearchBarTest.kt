package org.midorinext.android.integration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.adblock.AdBlockerState
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.ui.browser.home.HomeScreen
import org.midorinext.android.ui.browser.home.HomeSearchFieldTestTag

class HomeSearchBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun imeActionNavigatesToEnteredAddress() {
        var submittedValue: String? = null

        composeRule.setContent {
            HomeScreen(
                adBlockerState = AdBlockerState(),
                preferences = AppPreferencesSerializer.defaultValue,
                tabCount = 1,
                onSearch = { submittedValue = it },
                onOpenUrl = {},
                onOpenHome = {},
                onOpenBookmarks = {},
                onOpenTabs = {},
                onOpenSettings = {}
            )
        }

        val searchField = composeRule.onNodeWithTag(HomeSearchFieldTestTag)
        searchField.performClick()
        searchField.performTextInput("example.com")
        searchField.performImeAction()

        composeRule.runOnIdle {
            assertEquals("example.com", submittedValue)
        }
    }
}
