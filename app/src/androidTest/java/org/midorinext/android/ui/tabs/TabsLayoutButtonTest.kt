package org.midorinext.android.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.R
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.preferences.app.nextTabsViewOption
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class TabsLayoutButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachClickCyclesViewAndAnnouncesNextView() {
        var selectedView by mutableStateOf(TabsViewOption.GRID)
        val changes = mutableListOf<TabsViewOption>()
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                TabsLayoutButton(
                    tabsViewOption = selectedView,
                    onCycleTabsView = {
                        selectedView = selectedView.nextTabsViewOption()
                        changes += selectedView
                    },
                )
            }
        }

        assertView(R.string.available_tabs_view_grid, R.string.available_tabs_view_carousel)
        compose.onNodeWithTag("tabs_layout_toggle").performClick()
        assertView(R.string.available_tabs_view_carousel, R.string.available_tabs_view_list)
        compose.onNodeWithTag("tabs_layout_toggle").performClick()
        assertView(R.string.available_tabs_view_list, R.string.available_tabs_view_grid)
        compose.onNodeWithTag("tabs_layout_toggle").performClick()
        assertView(R.string.available_tabs_view_grid, R.string.available_tabs_view_carousel)
        compose.runOnIdle {
            assertEquals(listOf(TabsViewOption.CAROUSEL, TabsViewOption.LIST, TabsViewOption.GRID), changes)
        }
    }

    @Test
    fun clicksRemainIndependentWhilePersistedViewIsPending() {
        var selectedView by mutableStateOf(TabsViewOption.GRID)
        var cycleRequests = 0
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                TabsLayoutButton(selectedView, onCycleTabsView = { cycleRequests++ })
            }
        }

        repeat(3) { compose.onNodeWithTag("tabs_layout_toggle").performClick() }
        compose.runOnIdle { assertEquals(3, cycleRequests) }
        assertView(R.string.available_tabs_view_grid, R.string.available_tabs_view_carousel)

        compose.runOnIdle { selectedView = TabsViewOption.LIST }
        assertView(R.string.available_tabs_view_list, R.string.available_tabs_view_grid)
    }

    private fun assertView(currentLabel: Int, nextLabel: Int) {
        compose.onNodeWithTag("tabs_layout_toggle")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, compose.activity.getString(currentLabel)))
            .assertContentDescriptionEquals(
                compose.activity.getString(R.string.tab_tray_switch_view, compose.activity.getString(nextLabel))
            )
    }
}
