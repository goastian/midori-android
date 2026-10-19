package org.midorinext.android.ui.browser.toolbar

import androidx.activity.ComponentActivity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.datastore.core.DataStore
import androidx.test.platform.app.InstrumentationRegistry
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.BuildConfig
import org.midorinext.android.MidoriApplication
import org.midorinext.android.newtab.MidoriNewTabFeature
import org.midorinext.android.newtab.NATIVE_HOME_URL
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import org.json.JSONObject
import org.midorinext.android.ui.browser.mozaccompose.EngineView as BrowserEngineView

class ToolbarInputDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val preferences = MutableStateFlow(AppPreferences.getDefaultInstance())
    private val first = createTab("https://www.example.org/search?q=midori#results")
    private val second = createTab("https://second.example.org/article?from=tab", private = true)
    private val store = BrowserStore(BrowserState(tabs = listOf(first, second), selectedTabId = first.id))
    private lateinit var state: BrowserToolbarState
    private var committedText: String? = null
    private var viewportSession: EngineSession? = null

    @After
    fun finish() {
        if (viewportSession != null) {
            compose.activityRule.scenario.close()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { viewportSession?.close() }
        }
        scope.cancel()
    }

    @Test
    fun toolbarTransitionsKeepTheSameSurfaceAndBufferSize() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MidoriApplication
        val pageMetrics = AtomicReference<JSONObject?>()
        preferences.value = preferences.value.toBuilder().setHideToolbarOnScroll(true).build()
        compose.runOnIdle {
            state = createState()
            viewportSession = app.engine.get().createSession().apply {
                register(object : EngineSession.Observer {
                    override fun onTitleChange(title: String) {
                        if (title.startsWith('{')) pageMetrics.set(JSONObject(title))
                    }
                })
                loadUrl("data:text/html," + Uri.encode("""
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>body{margin:0;height:3000px}footer{position:fixed;bottom:0;height:24px}</style>
                    <footer>Controls</footer><script>setInterval(()=>document.title=JSON.stringify({
                      height:visualViewport.height,bottom:document.querySelector('footer').getBoundingClientRect().bottom
                    }),50)</script>
                """.trimIndent()))
            }
        }
        compose.setContent {
            var engineView by remember { mutableStateOf<EngineView?>(null) }
            HideOnScrollToolbar(
                toolbarState = state,
                toolbar = { Box(it.height(ToolbarHeight)) },
                engineView = engineView,
                modifier = Modifier.fillMaxSize(),
            ) { modifier ->
                BrowserEngineView(app.engine.get(), modifier) { view ->
                    DisposableEffect(view) {
                        view.render(viewportSession!!)
                        onDispose { view.release() }
                    }
                    SideEffect { engineView = view }
                }
            }
        }
        compose.waitUntil { state.shouldHideOnScroll.value }
        compose.waitUntil(20_000) { pageMetrics.get() != null }
        for (position in listOf(ToolbarPosition.TOP, ToolbarPosition.BOTTOM)) {
            compose.runOnIdle {
                preferences.value = preferences.value.toBuilder().setToolbarPosition(position).build()
            }
            compose.waitUntil { state.toolbarPosition.value == position }
            compose.waitForIdle()
            val visibleHeight = pageMetrics.get()!!.getDouble("height")
            val surface = compose.runOnIdle { findSurface(compose.activity.window.decorView)!! }
            val initialSize = compose.runOnIdle { surface.width to surface.height }
            val resizes = AtomicInteger()
            val callback = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    resizes.incrementAndGet()
                }
            }
            compose.runOnIdle { surface.holder.addCallback(callback) }
            try {
                repeat(10) { index ->
                    compose.runOnIdle { state.updateVisibility(index % 2 == 0) }
                    compose.waitForIdle()
                }
                compose.runOnIdle { state.updateVisibility(true) }
                compose.waitForIdle()
                assertEquals("Toolbar visibility must not resize the native surface ($position)", 0, resizes.get())
                compose.runOnIdle { state.updateVisibility(false) }
                compose.waitUntil(10_000) { pageMetrics.get()!!.getDouble("height") > visibleHeight + 1 }
                compose.runOnIdle { state.updateVisibility(true) }
                compose.waitUntil(10_000) { pageMetrics.get()!!.getDouble("height") <= visibleHeight + 1 }
                repeat(3) {
                    compose.runOnIdle { state.updateFocus(true) }
                    compose.waitForIdle()
                    compose.runOnIdle { state.updateFocus(false) }
                    compose.waitForIdle()
                }
                compose.runOnIdle {
                    assertSame("Editing must preserve GeckoView ($position)", surface, findSurface(compose.activity.window.decorView))
                    assertEquals(initialSize, surface.width to surface.height)
                }
                assertEquals(0, resizes.get())
            } finally {
                compose.runOnIdle { surface.holder.removeCallback(callback) }
            }
        }
    }

    private fun findSurface(view: View): SurfaceView? = when (view) {
        is SurfaceView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findSurface(view.getChildAt(it)) }
        else -> null
    }

    @Test
    fun composingAnUnfocusedAddressPreservesFindFieldFocus() {
        var showAddress by mutableStateOf(false)
        val requester = FocusRequester()
        compose.runOnIdle { state = createState() }
        compose.setContent {
            MidoriBrowserTheme {
                Column {
                    BasicTextField("Find in page", onValueChange = {}, modifier = Modifier.focusRequester(requester))
                    if (showAddress) ToolbarInput(toolbarState = state, onCommit = {})
                }
            }
        }
        compose.runOnIdle { requester.requestFocus() }
        compose.onNode(hasSetTextAction()).assertIsFocused()

        compose.runOnIdle { showAddress = true }

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun tappingAddressSelectsFullUrlAtEitherToolbarPosition() {
        showToolbar()
        for (position in listOf(ToolbarPosition.TOP, ToolbarPosition.BOTTOM)) {
            compose.runOnIdle {
                preferences.value = preferences.value.toBuilder().setToolbarPosition(position).build()
            }
            compose.waitUntil { state.toolbarPosition.value == position }
            repeat(3) {
                compose.onNodeWithText("example.org").performTouchInput { click(center) }
                assertEditing(first.content.url)
                compose.runOnIdle { assertTrue(state.isQueryPrefilled) }
                compose.activityRule.scenario.onActivity {
                    it.onBackPressedDispatcher.onBackPressed()
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertFalse(state.hasFocus)
                    assertEquals("example.org", state.text.text)
                }
            }
        }
    }

    @Test
    fun tappingAddressWhenToolbarHidesOnScrollSelectsFullUrl() {
        preferences.value = preferences.value.toBuilder().setHideToolbarOnScroll(true).build()
        showToolbar()
        compose.waitUntil { state.shouldHideOnScroll.value }

        compose.onNodeWithText("example.org").performTouchInput { click(center) }

        assertEditing(first.content.url)
    }

    @Test
    fun editingImmediatelyAfterCreationUsesSelectedStoreUrl() {
        compose.runOnIdle {
            state = createState()
            assertEquals(first.content.url, state.currentUrl.value)
            assertEquals("example.org", state.text.text)

            state.updateFocus(true)

            assertEquals(first.content.url, state.text.text)
            assertEquals(TextRange(0, first.content.url.length), state.text.selection)
        }
    }

    @Test
    fun enteringEditModeBeforeUrlFlowUpdatesUsesLatestPage() {
        showToolbar()
        val redirected = "https://redirected.example.org/path?query=full#anchor"
        compose.runOnIdle {
            store.dispatch(ContentAction.UpdateUrlAction(first.id, redirected))
            state.updateFocus(true)

            assertEquals(redirected, state.text.text)
        }
        assertEditing(redirected)
    }

    @Test
    fun focusAndPageUpdatesPreserveDraftUntilEditingEnds() {
        showToolbar()
        compose.onNodeWithText("example.org").performTouchInput { click(center) }
        compose.onNode(hasSetTextAction()).performTextInput("my new search")
        val redirected = "https://www.example.org/redirected?query=latest"

        compose.runOnIdle {
            state.updateFocus(true)
            store.dispatch(ContentAction.UpdateUrlAction(first.id, redirected))
        }
        compose.waitUntil { state.currentUrl.value == redirected }
        compose.onNode(hasSetTextAction()).assertTextEquals("my new search")
        compose.runOnIdle {
            assertFalse(state.isQueryPrefilled)
            state.updateFocus(false)
        }
        compose.onNodeWithText("example.org").performTouchInput { click(center) }
        assertEditing(redirected)
    }

    @Test
    fun switchingTabsClosesEditingAndShowsNewSelectedUrl() {
        showToolbar()
        compose.onNodeWithText("example.org").performTouchInput { click(center) }
        compose.onNode(hasSetTextAction()).performTextInput("unfinished draft")

        compose.runOnIdle { store.dispatch(TabListAction.SelectTabAction(second.id)) }
        compose.waitUntil { !state.hasFocus && state.currentUrl.value == second.content.url }
        compose.runOnIdle { assertEquals("second.example.org", state.text.text) }
        compose.onNodeWithText("second.example.org").performTouchInput { click(center) }

        assertEditing(second.content.url)
    }

    @Test
    fun switchingTabsWithSameUrlStillResetsDraft() {
        store.dispatch(ContentAction.UpdateUrlAction(second.id, first.content.url))
        showToolbar()
        compose.onNodeWithText("example.org").performTouchInput { click(center) }
        compose.onNode(hasSetTextAction()).performTextInput("unfinished draft")

        compose.runOnIdle { store.dispatch(TabListAction.SelectTabAction(second.id)) }
        compose.waitUntil { !state.hasFocus }
        compose.runOnIdle { assertEquals("example.org", state.text.text) }
        compose.onNodeWithText("example.org").performTouchInput { click(center) }

        assertEditing(first.content.url)
    }

    @Test
    fun clearingAddressAndSubmittingNewTextCommitsEntireUrl() {
        showToolbar()
        compose.onNodeWithText("example.org").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        val url = "https://example.org/new/path?query=typed#section"
        compose.onNode(hasSetTextAction()).performTextInput(url)
        compose.onNode(hasSetTextAction()).performImeAction()

        compose.runOnIdle {
            assertEquals(url, committedText)
            assertFalse(state.hasFocus)
            assertEquals("example.org", state.text.text)
        }
    }

    @Test
    fun homeStartsEmptyAndMidoriSearchSelectsDecodedQuery() {
        val searchUrl = "${BuildConfig.QWANT_BASE_URL}/?q=caf%C3%A9+midori"
        compose.runOnIdle {
            store.dispatch(ContentAction.UpdateUrlAction(first.id, NATIVE_HOME_URL))
            state = createState()
            state.updateFocus(true)
            assertEquals("", state.text.text)

            state.updateFocus(false)
            store.dispatch(ContentAction.UpdateUrlAction(first.id, searchUrl))
            state.updateFocus(true)

            assertEquals("café midori", state.text.text)
            assertEquals(TextRange(0, "café midori".length), state.text.selection)
        }
    }

    private fun showToolbar() {
        compose.runOnIdle { state = createState() }
        compose.setContent {
            MidoriBrowserTheme {
                HideOnScrollToolbar(
                    toolbarState = state,
                    engineView = null,
                    modifier = Modifier.fillMaxSize(),
                    toolbar = { modifier ->
                        Toolbar(
                            onTextCommit = { committedText = it },
                            modifier = modifier,
                            toolbarState = state,
                            browserIcons = state.browserIcons,
                            beforeTextFieldVisible = { false },
                        )
                    },
                ) { modifier -> Box(modifier) }
            }
        }
        compose.waitUntil { state.currentUrl.value == first.content.url }
    }

    private fun createState(): BrowserToolbarState {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MidoriApplication
        val dataStore = object : DataStore<AppPreferences> {
            override val data = preferences
            override suspend fun updateData(transform: suspend (AppPreferences) -> AppPreferences): AppPreferences {
                preferences.value = transform(preferences.value)
                return preferences.value
            }
        }
        return BrowserToolbarState(
            store = store,
            appPreferencesRepository = AppPreferencesRepository(dataStore),
            suggestionProviders = Lazy { emptyList() },
            browserIcons = app.browserIcons.get(),
            datahub = Lazy { error("Toolbar editing should not initialize Datahub") },
            newTabFeature = MidoriNewTabFeature(app),
            coroutineScope = scope,
        )
    }

    private fun assertEditing(url: String) {
        compose.onNode(hasSetTextAction()).assertIsFocused().assertTextEquals(url)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, url.length)))
    }
}
