package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import mozilla.components.concept.storage.LoginEntry
import org.midorinext.android.R
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.storage.autofill.LoginSaveDecision
import mozilla.components.feature.prompts.R as mozacR

@Composable
internal fun SaveLoginDialog(
    login: LoginEntry,
    repository: LoginAutofillRepository,
    onSave: (LoginEntry, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf(login.username) }
    var password by remember { mutableStateOf(login.password) }
    val entry = login.copy(username = username, password = password)
    var validated by remember { mutableStateOf<Pair<LoginEntry, LoginSaveDecision>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(entry) {
        failed = false
        if (entry.password.isEmpty()) return@LaunchedEffect
        try {
            val decision = repository.validate(entry)
            validated = entry to decision
            if (entry == login && decision == LoginSaveDecision.Unchanged) onDismiss()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }

    val decision = validated?.takeIf { it.first == entry }?.second
    val updating = decision is LoginSaveDecision.Update
    AlertDialog(
        modifier = Modifier.testTag("save_login_dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (updating) mozacR.string.mozac_feature_prompt_login_update_headline_2 else R.string.prompt_save_login_title))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(login.origin)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.passwords_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("save_login_username"),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.passwords_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = password.isEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("save_login_password"),
                )
                if (failed) Text(stringResource(R.string.secure_storage_error))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !failed && password.isNotEmpty() && (decision == LoginSaveDecision.Create || updating),
                onClick = { onSave(entry, (decision as? LoginSaveDecision.Update)?.guid) },
                modifier = Modifier.testTag("save_login_confirm"),
            ) {
                Text(stringResource(if (updating) mozacR.string.mozac_feature_prompt_update_confirmation else mozacR.string.mozac_feature_prompt_save_confirmation))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
