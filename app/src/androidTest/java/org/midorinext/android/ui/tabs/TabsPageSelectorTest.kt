package org.midorinext.android.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class TabsPageSelectorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun changingCollectionClearsSearchAndExposesOneSelectedCollection() {
        var state by mutableStateOf(TabsUiState(TabsPage.PRIVATE, TabsMode.Searching("private query")))
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                TabsPageSelector(
                    selectedPage = state.page,
                    normalTabsCount = 12,
                    privateTabsCount = 3,
                    onPageSelected = { state = state.reduce(TabsUiAction.SelectPage(it)) },
                )
            }
        }

        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        compose.onNodeWithTag("tabs_page_NORMAL").assertIsNotSelected().performClick()

        compose.onNodeWithTag("tabs_page_NORMAL").assertIsSelected()
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(TabsUiState(TabsPage.NORMAL), state)
        }
    }

    @Test
    fun largeTextKeepsBothCollectionsReachableAndChangingCollectionCancelsSelection() {
        var state by mutableStateOf(
            TabsUiState(TabsPage.NORMAL, TabsMode.Selecting(setOf("one", "two"), "group")),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MidoriBrowserTheme(darkTheme = true) {
                    TabsPageSelector(
                        selectedPage = state.page,
                        normalTabsCount = 125,
                        privateTabsCount = 0,
                        onPageSelected = { state = state.reduce(TabsUiAction.SelectPage(it)) },
                    )
                }
            }
        }

        compose.onNodeWithTag("tabs_page_NORMAL").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsDisplayed().performClick()

        compose.onNodeWithTag("tabs_page_NORMAL").assertIsNotSelected()
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        compose.runOnIdle {
            assertEquals(TabsUiState(TabsPage.PRIVATE), state)
        }
    }
}
