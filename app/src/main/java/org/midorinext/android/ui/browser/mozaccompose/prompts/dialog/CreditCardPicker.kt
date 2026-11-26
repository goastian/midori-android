package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import mozilla.components.concept.storage.CreditCardEntry
import org.midorinext.android.R
import org.midorinext.android.storage.autofill.isValidSecurityCode

@Composable
internal fun CreditCardPicker(
    cards: List<CreditCardEntry>,
    askForSecurityCode: Boolean,
    bottomPadding: Dp,
    onSelect: (CreditCardEntry, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<CreditCardEntry?>(null) }
    var code by remember { mutableStateOf("") }
    val dismiss = { code = ""; onDismiss() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dismiss() }
    val card = selected
    if (card == null) {
        AutofillPicker(
            title = stringResource(R.string.prompt_fill_card_title),
            options = cards,
            bottomPadding = bottomPadding,
            label = { it.obfuscatedCardNumber },
            detail = { it.name },
            onSelect = { if (askForSecurityCode) selected = it else onSelect(it, "") },
            onDismiss = dismiss,
        )
    } else {
        AlertDialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.card_security_code_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(card.obfuscatedCardNumber)
                    Text(stringResource(R.string.card_security_code_note))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { digit -> digit in '0'..'9' }.take(4) },
                        label = { Text(stringResource(R.string.card_security_code_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.testTag("card_security_code"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isValidSecurityCode(card.number, code),
                    modifier = Modifier.testTag("card_security_code_confirm"),
                    onClick = {
                        val value = code
                        code = ""
                        onSelect(card, value)
                    },
                ) { Text(stringResource(R.string.prompt_fill_card_title)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
