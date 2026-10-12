package org.midorinext.android.ui.browser.mozaccompose.prompts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
        compose.onNodeWithTag("page_password").performTextClearance()
        compose.onNodeWithTag("page_password").assertTextEquals("")
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
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

        compose.waitUntil { compose.onNodeWithTag("save_login_confirm").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
        compose.onNodeWithTag("save_login_password").performTextClearance()
        compose.onNodeWithTag("save_login_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("save_login_username").performTextClearance()
        compose.onNodeWithTag("save_login_username").performTextInput("edited-user")
        compose.onNodeWithTag("save_login_password").performTextInput("edited-password")
        compose.waitUntil { compose.onNodeWithTag("save_login_confirm").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
        compose.onNodeWithTag("save_login_confirm").assertIsEnabled().performClick()

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

        compose.onNodeWithTag("save_login_confirm").assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()

        compose.onNodeWithTag("save_login_dialog").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertTrue(store.state.tabs.single().content.promptRequests.isEmpty())
        }
    }

    @androidx.compose.runtime.Composable
    private fun prompts(store: BrowserStore, repository: LoginAutofillRepository) {
        PromptFeature(
            store = store,
            exitFullscreenUseCase = SessionUseCases(store).exitFullscreen,
            appPreferences = AppPreferencesSerializer.defaultValue.toBuilder()
                .setPasswordAutofillEnabled(true).setSavePasswordsEnabled(true).build(),
            loginRepository = repository,
        )
    }

    private fun store(request: PromptRequest): BrowserStore {
        val tab = createTab("https://example.org").let { it.copy(content = it.content.copy(promptRequests = listOf(request))) }
        return BrowserStore(BrowserState(tabs = listOf(tab), selectedTabId = tab.id))
    }

    private fun repository(logins: List<Login>) = LoginAutofillRepository(lazy {
        Proxy.newProxyInstance(LoginsStorage::class.java.classLoader, arrayOf(LoginsStorage::class.java)) { _, method, args ->
            when (method.name) {
                "get" -> logins.firstOrNull { it.guid == args!![0] }
                "findLoginToUpdate" -> null
                else -> error("Unexpected storage operation: ${method.name}")
            }
        } as LoginsStorage
    })

    private fun login(guid: String, username: String) = Login(guid, username, "password-$guid", "https://example.org", "https://example.org")
}
