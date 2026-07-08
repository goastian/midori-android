package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.internalcopy.PromptAbuserDetector
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.feature.prompts.R as mozacR

@Composable
fun RepostDialog(
    promptAbuserDetector: PromptAbuserDetector,
    onConfirm: () -> Unit,
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
            onNo = onDismiss,
            title = stringResource(mozacR.string.mozac_feature_prompt_repost_title),
            description = stringResource(mozacR.string.mozac_feature_prompt_repost_message),
            additionalContent = { abusingControls() },
            yesText = stringResource(mozacR.string.mozac_feature_prompt_repost_positive_button_text)
        )
    }
}