package org.midorinext.android.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.preferences.app.nextTabsViewOption
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class TabsLayoutTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun oneClickFadesToNextLayoutAndDisposesOutgoingLayout() {
        setFixture()
        compose.onNodeWithTag("layout_GRID").assertIsDisplayed()

        compose.onNodeWithTag("tabs_layout_toggle").performClick()
        compose.mainClock.advanceTimeBy(64)

        compose.onNodeWithTag("layout_GRID").assertExists()
        compose.onNodeWithTag("layout_CAROUSEL").assertExists()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("layout_GRID").assertDoesNotExist()
        compose.onNodeWithTag("layout_CAROUSEL").assertIsDisplayed()
    }

    @Test
    fun rapidClicksSettleOnLatestLayoutWithoutLeavingOldContent() {
        val state = setFixture()

        repeat(4) {
            compose.onNodeWithTag("tabs_layout_toggle").performClick()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(240)

        compose.runOnIdle {
            assertEquals(4, state.clicks)
            assertEquals(TabsViewOption.CAROUSEL, state.layout)
        }
        compose.onNodeWithTag("layout_CAROUSEL").assertIsDisplayed()
        compose.onNodeWithTag("layout_GRID").assertDoesNotExist()
        compose.onNodeWithTag("layout_LIST").assertDoesNotExist()
    }

    @Test
    fun reducedMotionReplacesLayoutWithoutWaitingForFadeDuration() {
        setFixture(reduceMotion = true)

        compose.onNodeWithTag("tabs_layout_toggle").performClick()
        compose.mainClock.advanceTimeBy(48)

        compose.onNodeWithTag("layout_GRID").assertDoesNotExist()
        compose.onNodeWithTag("layout_CAROUSEL").assertIsDisplayed()
    }

    private fun setFixture(reduceMotion: Boolean = false): FixtureState {
        val state = FixtureState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    Column(Modifier.fillMaxSize()) {
                        TabsLayoutButton(
                            tabsViewOption = state.layout,
                            onCycleTabsView = {
                                state.layout = state.layout.nextTabsViewOption()
                                state.clicks++
                            },
                        )
                        Box(Modifier.weight(1f)) {
                            TabsLayoutTransition(state.layout) { layout ->
                                val tag = "layout_${layout.name}"
                                Text(tag, Modifier.testTag(tag))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        return state
    }

    private class FixtureState {
        var layout by mutableStateOf(TabsViewOption.GRID)
        var clicks = 0
    }
}
