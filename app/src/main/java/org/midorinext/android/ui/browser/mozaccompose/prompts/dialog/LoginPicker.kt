package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginHint
import org.midorinext.android.R
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import mozilla.components.feature.prompts.R as mozacR

@Composable
internal fun LoginPicker(
    logins: List<Login>,
    repository: LoginAutofillRepository,
    onSelect: (Login) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    AutofillPicker(
        title = stringResource(R.string.prompt_fill_login_title),
        options = logins,
        label = {
            when {
                it.hint == LoginHint.GENERATED -> stringResource(mozacR.string.mozac_feature_prompts_suggest_strong_password_title)
                it.username.isBlank() -> stringResource(R.string.passwords_no_username)
                else -> it.username
            }
        },
        detail = { it.origin },
        enabled = !selecting,
        onSelect = { login ->
            selecting = true
            scope.launch {
                try {
                    repository.resolveSelection(login)?.let(onSelect) ?: onDismiss()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss,
    )
}
