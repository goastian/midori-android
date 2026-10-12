package org.midorinext.android.ui.browser.mozaccompose.prompts

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginsStorage
import mozilla.components.feature.session.SessionUseCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import org.midorinext.android.R
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import java.lang.reflect.Proxy

class AutofillPromptUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clearingPagePasswordDoesNotFillUntilUserSelectsAccount() {
        val first = login("first", "first-user")
        val second = login("second", "second-user")
        var pagePassword by mutableStateOf("typed-password")
        val selected = mutableListOf<Login>()
        val request = PromptRequest.SelectLoginPrompt(
            logins = listOf(first, second),
            generatedPassword = null,
            onConfirm = { selected += it; pagePassword = it.password },
            onDismiss = {},
        )
        val store = store(request)
        val repository = repository(listOf(first, second))
        compose.setContent {
            MidoriBrowserTheme {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(pagePassword, { pagePassword = it }, modifier = Modifier.testTag("page_password"))
                }
                prompts(store, repository)
            }
        }

        compose.onNodeWithTag("autofill_picker").assertIsDisplayed()
        compose.onNodeWithTag("autofill_option_0").assertDoesNotExist()
        compose.onNodeWithTag("page_password").performTextClearance()
        compose.onNodeWithTag("page_password").assertTextEquals("")
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
        compose.onNodeWithTag("autofill_expand").performClick()
        compose.onNodeWithTag("autofill_option_1").performClick()
        compose.waitUntil { selected.isNotEmpty() }

        compose.onNodeWithTag("page_password").assertTextEquals(second.password)
        compose.onNodeWithTag("autofill_picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(second), selected) }
    }

    @Test
    fun savePromptRejectsEmptyPasswordAndPreservesUserEdits() {
        val entry = login("new", "old-user").toEntry()
        val saved = mutableListOf<LoginEntry>()
        val request = PromptRequest.SaveLoginPrompt(hint = 0, logins = listOf(entry), onConfirm = { saved += it }, onDismiss = {})
        val store = store(request)
        val repository = repository(emptyList())
        compose.setContent { MidoriBrowserTheme { prompts(store, repository) } }

        compose.waitUntil { saveIsEnabled() }
        compose.onNodeWithTag("save_login_password").performTextClearance()
        compose.onNodeWithTag("save_login_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("save_login_username").performTextClearance()
        compose.onNodeWithTag("save_login_username").performTextInput("edited-user")
        compose.onNodeWithTag("save_login_password").performTextInput("edited-password")
        compose.waitUntil { saveIsEnabled() }
        compose.onNodeWithTag("save_login_confirm").assertIsEnabled().performClick()
        compose.waitUntil { saved.isNotEmpty() }

        compose.onNodeWithTag("save_login_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(entry.copy(username = "edited-user", password = "edited-password")), saved) }
    }

    @Test
    fun failedValidationStillAllowsCancelAndUnblocksRequest() {
        var dismissed = 0
        val request = PromptRequest.SaveLoginPrompt(
            hint = 0,
            logins = listOf(login("new", "user").toEntry()),
            onConfirm = { error("Unavailable storage cannot save") },
            onDismiss = { dismissed++ },
        )
        val store = store(request)
        val repository = LoginAutofillRepository(lazy { error("Encrypted storage unavailable") })
        compose.setContent { MidoriBrowserTheme { prompts(store, repository) } }

        compose.waitUntil { compose.onAllNodesWithText(compose.activity.getString(R.string.secure_storage_error)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("save_login_confirm").assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()

        compose.onNodeWithTag("save_login_dialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertTrue(store.state.tabs.single().content.promptRequests.isEmpty())
        }
    }

    @Test
    fun failedSaveKeepsEditedCredentialsAndCanBeRetried() {
        val entry = login("new", "user").toEntry()
        val saved = mutableListOf<LoginEntry>()
        var attempts = 0
        val repository = repository(emptyList(), beforeAdd = { if (attempts++ == 0) error("Storage temporarily unavailable") })
        val request = PromptRequest.SaveLoginPrompt(0, listOf(entry), { saved += it }, {})
        val store = store(request)
        compose.setContent { MidoriBrowserTheme { prompts(store, repository) } }

        compose.waitUntil { saveIsEnabled() }
        compose.onNodeWithTag("save_login_confirm").performClick()
        compose.waitUntil { compose.onAllNodesWithText(compose.activity.getString(R.string.secure_storage_error)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("save_login_username")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(entry.username)))
        compose.onNodeWithTag("save_login_confirm").assertIsEnabled().performClick()
        compose.waitUntil { saved.isNotEmpty() }
        compose.runOnIdle { assertEquals(listOf(entry), saved) }
    }

    @Test
    fun deletingOneSuggestionDoesNotDismissOtherAccounts() {
        val first = login("first", "first-user")
        val second = login("second", "second-user")
        val selected = mutableListOf<Login>()
        val repository = repository(listOf(first, second))
        val request = PromptRequest.SelectLoginPrompt(listOf(first, second), null, { selected += it }, {})
        val store = store(request)
        compose.setContent { MidoriBrowserTheme { prompts(store, repository) } }
        kotlinx.coroutines.runBlocking { repository.delete(first.guid) }

        compose.onNodeWithTag("autofill_expand").performClick()
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.waitUntil { compose.onAllNodesWithText(first.username).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.waitUntil { selected.isNotEmpty() }

        compose.runOnIdle { assertEquals(listOf(second), selected) }
    }

    @Test
    fun deletingBestDuplicateKeepsOtherMatchingRecordSelectable() {
        val root = login("root", "same-user")
        val child = root.copy(guid = "child", origin = "https://app.example.org")
        val selected = mutableListOf<Login>()
        val repository = repository(listOf(root, child))
        val request = PromptRequest.SelectLoginPrompt(listOf(root, child), null, { selected += it }, {})
        val store = store(request)
        compose.setContent { MidoriBrowserTheme { prompts(store, repository) } }
        kotlinx.coroutines.runBlocking { repository.delete(root.guid) }

        compose.onNodeWithTag("autofill_expand").performClick()
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.waitUntil { compose.onAllNodesWithText(child.origin).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("autofill_option_0").performClick()
        compose.waitUntil { selected.isNotEmpty() }

        compose.runOnIdle { assertEquals(listOf(child), selected) }
    }

    @Test
    fun focusingAddressBarDismissesPasswordSuggestions() {
        val login = login("stored", "user")
        var browserInputFocused by mutableStateOf(false)
        var dismissed = 0
        val request = PromptRequest.SelectLoginPrompt(listOf(login), null, {}, { dismissed++ })
        val store = store(request)
        val repository = repository(listOf(login))
        compose.setContent {
            MidoriBrowserTheme {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        "Address bar",
                        Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter)
                            .testTag("browser_toolbar").clickable { browserInputFocused = true },
                    )
                    prompts(store, repository, browserInputFocused, autofillBottomPadding = 64.dp)
                }
            }
        }

        compose.onNodeWithTag("autofill_picker").assertIsDisplayed()
        val target = compose.onNodeWithTag("browser_toolbar").fetchSemanticsNode().boundsInWindow.center
        val windowLocation = IntArray(2)
        compose.runOnIdle { compose.activity.window.decorView.getLocationOnScreen(windowLocation) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val eventTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(eventTime, eventTime, action, target.x + windowLocation[0], target.y + windowLocation[1], 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        compose.waitForIdle()

        compose.onNodeWithTag("autofill_picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    private fun saveIsEnabled(): Boolean = compose.onAllNodesWithTag("save_login_confirm")
        .fetchSemanticsNodes().singleOrNull()?.config
        ?.contains(SemanticsProperties.Disabled) == false

    @androidx.compose.runtime.Composable
    private fun prompts(
        store: BrowserStore,
        repository: LoginAutofillRepository,
        hideAutofillSuggestions: Boolean = false,
        autofillBottomPadding: Dp = 0.dp,
    ) {
        PromptFeature(
            store = store,
            exitFullscreenUseCase = SessionUseCases(store).exitFullscreen,
            appPreferences = AppPreferencesSerializer.defaultValue.toBuilder()
                .setPasswordAutofillEnabled(true).setSavePasswordsEnabled(true).build(),
            loginRepository = repository,
            hideAutofillSuggestions = hideAutofillSuggestions,
            autofillBottomPadding = autofillBottomPadding,
        )
    }

    private fun store(request: PromptRequest): BrowserStore {
        val tab = createTab("https://example.org").let { it.copy(content = it.content.copy(promptRequests = listOf(request))) }
        return BrowserStore(BrowserState(tabs = listOf(tab), selectedTabId = tab.id))
    }

    private fun repository(logins: List<Login>, beforeAdd: () -> Unit = {}): LoginAutofillRepository {
        val rows = logins.associateBy { it.guid }.toMutableMap()
        return LoginAutofillRepository(lazy {
            Proxy.newProxyInstance(LoginsStorage::class.java.classLoader, arrayOf(LoginsStorage::class.java)) { _, method, args ->
                when (method.name) {
                    "get" -> rows[args!![0]]
                    "delete" -> rows.remove(args!![0]) != null
                    "findLoginToUpdate" -> null
                    "add" -> (args!![0] as LoginEntry).let { entry ->
                        beforeAdd()
                        Login("saved", entry.username, entry.password, entry.origin, entry.formActionOrigin)
                            .also { rows[it.guid] = it }
                    }
                    else -> error("Unexpected storage operation: ${method.name}")
                }
            } as LoginsStorage
        })
    }

    private fun login(guid: String, username: String) = Login(guid, username, "password-$guid", "https://example.org", "https://example.org")
}
