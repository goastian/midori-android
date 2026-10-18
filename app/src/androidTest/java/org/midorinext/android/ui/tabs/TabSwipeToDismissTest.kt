package org.midorinext.android.ui.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.ui.animation.LocalReduceMotion

class TabSwipeToDismissTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun upwardSwipeClosesOnceAndDownwardSwipeRestoresTheCarouselCard() {
        val fixture = showFixture(vertical = true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(0, fixture.closed) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, fixture.closed) }
    }

    @Test
    fun bothHorizontalDirectionsCloseRowsAndGridCards() {
        val fixture = showFixture()
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, fixture.closed) }
        compose.runOnIdle { fixture.id++ }
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(2, fixture.closed) }
    }

    @Test
    fun shortSlowSwipeReturnsWithoutClosingOrSelecting() {
        val fixture = showFixture()
        compose.onNodeWithTag("swipe").performTouchInput {
            swipe(center, center + Offset(-width * 0.15f, 0f), durationMillis = 800)
        }
        compose.runOnIdle { assertEquals(0, fixture.closed) }
    }

    @Test
    fun selectionModeDisablesSwipeDismissal() {
        val fixture = showFixture(selectionMode = true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, fixture.closed) }
    }

    @Test
    fun reducedMotionStillCommitsTheSwipe() {
        val fixture = showFixture(vertical = true, reduceMotion = true)
        compose.onNodeWithTag("swipe").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1, fixture.closed) }
    }

    @Test
    fun fastShortSwipeClosesACardThatMovesWithTheFinger() {
        val fixture = showFixture()
        compose.onNodeWithTag("swipe").performTouchInput {
            swipeWithVelocity(center, center + Offset(-width * 0.25f, 0f), endVelocity = width * 5f, durationMillis = 80)
        }
        compose.runOnIdle { assertEquals(1, fixture.closed) }
    }

    private fun showFixture(vertical: Boolean = false, selectionMode: Boolean = false, reduceMotion: Boolean = false): Fixture {
        val fixture = Fixture()
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                val dismissal = rememberTabDismissal(fixture.id.toString()) { fixture.closed++ }
                Box(Modifier.size(240.dp, 320.dp).testTag("swipe")
                    .graphicsLayer {
                        if (vertical) translationY = dismissal.dragOffset else translationX = dismissal.dragOffset
                    }
                    .tabSwipeToDismiss(dismissal, !selectionMode && !dismissal.requested, vertical))
            }
        }
        return fixture
    }

    private class Fixture {
        var id by mutableStateOf(0)
        var closed = 0
    }
}
