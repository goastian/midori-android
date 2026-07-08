package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.runtime.Composable
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.internalcopy.PromptAbuserDetector
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.concept.engine.prompt.PromptRequest

@Composable
fun ConfirmDialog(
    promptAbuserDetector: PromptAbuserDetector,
    request: PromptRequest.Confirm,
    onConfirm: () -> Unit,
    onRefuse: () -> Unit,
    onDismiss: () -> Unit,
) {
    PromptAbuserConsumer(
        promptAbuserDetector = promptAbuserDetector,
        onAbuse = onDismiss
    ) { abusingConfirm, abusingControls ->
        YesNoDialog(
            onDismissRequest = onDismiss,
            onYes = {
                abusingConfirm()
                onConfirm()
            },
            onNo = onRefuse,
            title = request.title,
            description = request.message,
            additionalContent = { abusingControls() }
        )
    }
}