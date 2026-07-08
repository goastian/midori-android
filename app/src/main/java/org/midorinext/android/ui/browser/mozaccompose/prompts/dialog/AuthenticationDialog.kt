package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.feature.prompts.R as mozacR

@Composable
fun AuthenticationDialog(
    request: PromptRequest.Authentication,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    YesNoDialog(
        onDismissRequest = onDismiss,
        onYes = { onConfirm(login, password) },
        onNo = onDismiss,
        title = request.title.ifBlank { stringResource(mozacR.string.mozac_feature_prompt_sign_in) },
        description = request.message,
        additionalContent = {
            if (!request.onlyShowPassword) {
                TextField(value = login, onValueChange = { login = it })
            }
            TextField(value = password, onValueChange = { password = it })
        }
    )
}