package org.midorinext.android.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.MidoriApplication
import org.midorinext.android.newtab.NATIVE_HOME_URL
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class TabsSwipeLayoutsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyLayoutClosesNormalAndPrivateTabsWithoutConfusingScrollingWithDismissal() {
        var layout by mutableStateOf(TabsViewOption.GRID)
        var tabs by mutableStateOf(emptyList<TabSessionState>())
        var selectionMode by mutableStateOf(false)
        var closed = 0
        var opened = 0
        val app = compose.activity.application as MidoriApplication
        val storage = ThumbnailStorage(compose.activity)
        compose.setContent {
            MidoriBrowserTheme {
                TabView(
                    tabs = tabs,
                    private = tabs.firstOrNull()?.content?.private == true,
                    selectedTabId = tabs.firstOrNull()?.id,
                    thumbnailStorage = storage,
                    browserIcons = app.browserIcons.get(),
                    onTabSelected = { opened++ },
                    onTabDeleted = { deleted -> closed++; tabs = tabs.filterNot { it.id == deleted.id } },
                    listState = rememberLazyListState(),
                    gridState = rememberLazyGridState(),
                    tabsViewOption = layout,
                    selectionMode = selectionMode,
                )
            }
        }
        for (private in listOf(false, true)) {
            for (mode in listOf(TabsViewOption.GRID, TabsViewOption.LIST, TabsViewOption.CAROUSEL)) {
                val id = "$mode-$private-0"
                compose.runOnIdle {
                    layout = mode
                    selectionMode = false
                    tabs = (0..7).map { TabSessionState("$mode-$private-$it", ContentState(NATIVE_HOME_URL, private = private)) }
                }
                val container = compose.onNodeWithTag("tabs_${mode.name.lowercase()}")
                container.performScrollToIndex(0)
                val tag = if (mode == TabsViewOption.LIST) "tab_row:$id" else "tab_card:$id"
                val before = closed
                compose.onNodeWithTag(tag).performTouchInput {
                    if (mode == TabsViewOption.CAROUSEL) swipeLeft() else swipeUp()
                }
                compose.runOnIdle { assertEquals("Scrolling $mode", before, closed) }
                container.performScrollToIndex(0)
                compose.onNodeWithTag(tag).performTouchInput {
                    if (mode == TabsViewOption.CAROUSEL) swipeUp() else swipeLeft()
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals("Closing in $mode, private=$private", before + 1, closed)
                    assertEquals(0, opened)
                    selectionMode = true
                }
                container.performScrollToIndex(0)
                val remaining = if (mode == TabsViewOption.LIST) "tab_row:$mode-$private-1" else "tab_card:$mode-$private-1"
                compose.onNodeWithTag(remaining).performTouchInput {
                    if (mode == TabsViewOption.CAROUSEL) swipeUp() else swipeLeft()
                }
                compose.runOnIdle { assertEquals("Selection in $mode", before + 1, closed) }
            }
        }
    }
}
