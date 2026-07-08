package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog


import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.internalcopy.PromptAbuserDetector
import org.midorinext.android.ui.widgets.YesNoDialog

@Composable
fun PromptDialog(
    promptAbuserDetector: PromptAbuserDetector,
    title: String,
    label: String,
    value: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PromptAbuserConsumer(
        promptAbuserDetector = promptAbuserDetector,
        onAbuse = onDismiss
    ) { abusingConfirm, abusingControls ->
        var promptValue by remember { mutableStateOf(value) }

        YesNoDialog(
            onDismissRequest = onDismiss,
            onYes = {
                abusingConfirm()
                onConfirm(promptValue)
            },
            onNo = onDismiss,
            title = title,
            description = label,
            additionalContent = {
                TextField(value = promptValue, onValueChange = { promptValue = it })
                abusingControls()
            }
        )
    }

}