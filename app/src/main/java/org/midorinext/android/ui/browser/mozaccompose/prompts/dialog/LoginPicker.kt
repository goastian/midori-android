package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginHint
import org.midorinext.android.R
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.storage.autofill.loginOptions
import mozilla.components.feature.prompts.R as mozacR

@Composable
internal fun LoginPicker(
    logins: List<Login>,
    pageUrl: String,
    repository: LoginAutofillRepository,
    onSelect: (Login) -> Unit,
    onDismiss: () -> Unit,
    onManageLogins: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    var selecting by remember { mutableStateOf(false) }
    var rejectedGuids by remember { mutableStateOf(emptySet<String>()) }
    var failed by remember { mutableStateOf(false) }
    val options = loginOptions(logins.filter { it.guid !in rejectedGuids }, pageUrl)
    AutofillPicker(
        title = stringResource(mozacR.string.mozac_feature_prompts_saved_logins_2),
        options = options,
        label = {
            when {
                it.hint == LoginHint.GENERATED -> stringResource(mozacR.string.mozac_feature_prompts_suggest_strong_password_title)
                it.username.isBlank() -> stringResource(R.string.passwords_no_username)
                else -> it.username
            }
        },
        detail = { it.origin },
        enabled = !selecting,
        expandable = true,
        bottomPadding = bottomPadding,
        onManageOptions = onManageLogins,
        errorText = if (failed) stringResource(R.string.secure_storage_error) else null,
        onSelect = { login ->
            selecting = true
            failed = false
            scope.launch {
                try {
                    val resolved = repository.resolveSelection(login)
                    if (resolved != null) {
                        onSelect(resolved)
                    } else {
                        rejectedGuids = rejectedGuids + login.guid
                        if (loginOptions(logins.filter { it.guid !in rejectedGuids }, pageUrl).isEmpty()) onDismiss()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed = true
                } finally {
                    selecting = false
                }
            }
        },
        onDismiss = onDismiss,
    )
}
