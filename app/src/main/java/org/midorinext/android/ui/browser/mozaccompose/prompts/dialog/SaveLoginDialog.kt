package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.LoginEntry
import org.midorinext.android.R
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.storage.autofill.LoginSaveDecision
import mozilla.components.feature.prompts.R as mozacR

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SaveLoginDialog(
    login: LoginEntry,
    repository: LoginAutofillRepository,
    onSave: suspend (LoginEntry, String?) -> Boolean,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf(login.username) }
    var password by remember { mutableStateOf(login.password) }
    val entry = login.copy(username = username, password = password)
    var validated by remember { mutableStateOf<Pair<LoginEntry, LoginSaveDecision>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var initialCheckFinished by remember { mutableStateOf(login.password.isEmpty()) }
    var validationAttempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }

    LaunchedEffect(entry, validationAttempt) {
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
        } finally {
            initialCheckFinished = true
        }
    }

    val decision = validated?.takeIf { it.first == entry }?.second
    if (!initialCheckFinished || (entry == login && decision == LoginSaveDecision.Unchanged)) return
    val updating = decision is LoginSaveDecision.Update
    ModalBottomSheet(
        modifier = Modifier.testTag("save_login_dialog"),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(login.origin)
            Text(
                stringResource(if (updating) mozacR.string.mozac_feature_prompt_login_update_headline_2 else R.string.prompt_save_login_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.passwords_username)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("save_login_username"),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.passwords_password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            painterResource(if (showPassword) R.drawable.icons_eye_off else R.drawable.icons_eye),
                            stringResource(if (showPassword) R.string.passwords_hide else R.string.passwords_show),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !saving,
                isError = password.isEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("save_login_password"),
            )
            if (failed) {
                Text(stringResource(R.string.secure_storage_error), color = MaterialTheme.colorScheme.error)
                if (decision == null && password.isNotEmpty()) {
                    TextButton(onClick = { validationAttempt++ }) { Text(stringResource(R.string.try_again)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = !saving && password.isNotEmpty() && (decision == LoginSaveDecision.Create || updating),
                    onClick = {
                        saving = true
                        failed = false
                        scope.launch {
                            try {
                                failed = !onSave(entry, (decision as? LoginSaveDecision.Update)?.guid)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_login_confirm"),
                ) {
                    Text(stringResource(if (updating) mozacR.string.mozac_feature_prompt_update_confirmation else mozacR.string.mozac_feature_prompt_save_confirmation))
                }
            }
        }
    }
}
