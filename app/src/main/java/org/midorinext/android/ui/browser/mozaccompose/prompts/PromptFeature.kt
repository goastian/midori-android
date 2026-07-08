package org.midorinext.android.ui.browser.mozaccompose.prompts

import android.security.KeyChain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import org.midorinext.android.ext.activity
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.AlertDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.AuthenticationDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.ChoiceDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.ConfirmDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.PromptDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.RepostDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.internalcopy.PromptAbuserDetector
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.colorpicker.ColorPicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.DateTimePicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.FilePicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.colorpicker.rememberColorPickerState
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.feature.prompts.share.DefaultShareDelegate
import mozilla.components.feature.prompts.share.ShareDelegate
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.lib.state.ext.observeAsComposableState
import java.security.InvalidParameterException
import kotlin.reflect.KClass
import mozilla.components.feature.prompts.R as mozacR

@Composable
fun rememberDefaultShareDelegate(): DefaultShareDelegate {
    return remember { DefaultShareDelegate() }
}

@Composable
fun PromptFeature(
    store: BrowserStore,
    exitFullscreenUseCase: SessionUseCases.ExitFullScreenUseCase,
    shareDelegate: ShareDelegate = rememberDefaultShareDelegate()
) {
    val context = LocalContext.current

    val promptAbuserDetector = remember { PromptAbuserDetector() }

    val sessionId by store.observeAsComposableState { state ->
        state.selectedTab?.id
    }
    val loading by store.observeAsComposableState { state ->
        state.selectedTab?.content?.loading
    }
    val lastRequest by store.observeAsComposableState { state ->
        state.selectedTab?.content?.promptRequests?.lastOrNull()
    }

    LaunchedEffect(true) {
        promptAbuserDetector.resetJSAlertAbuseState()
    }

    LaunchedEffect(loading) {
        if (loading == false) {
            promptAbuserDetector.resetJSAlertAbuseState()
        }
    }

    LaunchedEffect(lastRequest) {
        lastRequest?.executeIfWindowedPrompt { exitFullscreenUseCase() }
    }

    sessionId?.let { session -> lastRequest?.let { request ->
        val consume: () -> Unit = { store.consumeRequest(session, request) }
        val consumeAfter: (() -> Unit) -> Unit = { block ->
            store.consumeRequest(session, request, block)
        }

        when (request) {
            is PromptRequest.Share -> shareDelegate.showShareSheet(
                context = context,
                shareData = request.data,
                onDismiss = { consumeAfter { request.onDismiss() } },
                onSuccess = { consumeAfter { request.onSuccess() } }
            )

            is PromptRequest.File -> FilePicker(request, consume)
            is PromptRequest.TimeSelection -> DateTimePicker(request, consume) // TODO report to mozilla that datetime is not opening when clicking on time component
            is PromptRequest.Color -> {
                val colorPickerState = rememberColorPickerState(
                    initialColor = Color(request.defaultColor.toColorInt())
                )
                ColorPicker(
                    onConfirm = { consumeAfter { request.onConfirm(colorPickerState.string) } },
                    onDismiss = { consumeAfter { request.onDismiss() } },
                    colorPickerState = colorPickerState
                )
            }

            is PromptRequest.SingleChoice -> ChoiceDialog(
                choices = request.choices,
                onSelected = { choices -> consumeAfter {
                    choices.firstOrNull()?.let { request.onConfirm(it) } ?: request.onDismiss()
                } },
                onDismissRequest = { consumeAfter { request.onDismiss() }},
            )
            is PromptRequest.MenuChoice -> ChoiceDialog(
                choices = request.choices,
                onSelected = { choices -> consumeAfter {
                    choices.firstOrNull()?.let { request.onConfirm(it) } ?: request.onDismiss()
                } },
                onDismissRequest = { consumeAfter { request.onDismiss() }},
            )
            is PromptRequest.MultipleChoice -> ChoiceDialog(
                choices = request.choices,
                onSelected = { choices -> consumeAfter { request.onConfirm(choices)} },
                onDismissRequest = { consumeAfter { request.onDismiss() }},
                multipleSelectionAllowed = true
            )

            is PromptRequest.Alert -> AlertDialog(
                promptAbuserDetector = promptAbuserDetector,
                title = request.title,
                message = request.message,
                onConfirm = { consumeAfter {
                    request.onConfirm(promptAbuserDetector.shouldShowMoreDialogs)
                }},
                onDismiss = { consumeAfter { request.onDismiss() }}
            )
            is PromptRequest.TextPrompt -> PromptDialog(
                promptAbuserDetector = promptAbuserDetector,
                title = request.title,
                label = request.inputLabel,
                value = request.inputValue,
                onConfirm = { result -> consumeAfter {
                    request.onConfirm(promptAbuserDetector.shouldShowMoreDialogs, result)
                }},
                onDismiss = { consumeAfter { request.onDismiss() }}
            )
            is PromptRequest.BeforeUnload -> YesNoDialog( // TODO ask about BeforeUnload not reported by the engine on mobile ? (as other browsers ...)
                onDismissRequest = { consumeAfter { request.onStay() } },
                onYes = { consumeAfter { request.onLeave() } },
                onNo = { consumeAfter { request.onStay() } },
                title = stringResource(mozacR.string.mozac_feature_prompt_before_unload_dialog_title),
                description = stringResource(mozacR.string.mozac_feature_prompt_before_unload_dialog_body),
                yesText = stringResource(mozacR.string.mozac_feature_prompts_before_unload_leave),
                noText = stringResource(mozacR.string.mozac_feature_prompts_before_unload_stay),
            )
            is PromptRequest.Confirm -> ConfirmDialog( // TODO ask about neutral third action of confirm prompt ?
                promptAbuserDetector = promptAbuserDetector,
                request = request,
                onConfirm = { consumeAfter {
                    request.onConfirmPositiveButton(promptAbuserDetector.shouldShowMoreDialogs)
                } },
                onRefuse = { consumeAfter {
                    request.onConfirmNegativeButton(promptAbuserDetector.shouldShowMoreDialogs)
                } },
                onDismiss = { consumeAfter { request.onDismiss() } }
            )
            is PromptRequest.Repost -> RepostDialog(
                promptAbuserDetector = promptAbuserDetector,
                onConfirm = { consumeAfter { request.onConfirm() } },
                onDismiss = { consumeAfter { request.onDismiss() } }
            )
            // TODO ask about 'popup' prompt request not triggered. Directly opening a new tab
            // TODO popup should be protected by promptAbuserDetector
            is PromptRequest.Popup -> YesNoDialog(
                onDismissRequest = { consumeAfter { request.onDismiss() } },
                onYes = { consumeAfter {
                    // TODO Popup should open new tab ?
                    request.onAllow()
                } },
                onNo = { consumeAfter { request.onDeny() } },
                title = stringResource(mozacR.string.mozac_feature_prompts_popup_dialog_title),
                description = request.targetUri,
                yesText = stringResource(mozacR.string.mozac_feature_prompts_allow),
                noText = stringResource(mozacR.string.mozac_feature_prompts_deny),
            )
            is PromptRequest.Authentication -> AuthenticationDialog(
                request = request,
                onConfirm = { login, password ->
                    consumeAfter { request.onConfirm(login, password) }
                },
                onDismiss = { consumeAfter { request.onDismiss() } },
            )

            is PromptRequest.CertificateRequest -> {
                context.activity?.let { activity ->
                    KeyChain.choosePrivateKeyAlias(
                        activity,
                        { alias -> request.onComplete(alias) },
                        arrayOf("RSA", "EC"), // keyTypes
                        null, // issuers - currently not supported
                        request.host, // the host that requested the certificate
                        -1, // specify the default port to simplify the UI
                        null // alias - leave null for now to not preselect a certificate
                    )
                } ?: consumeAfter { request.onComplete(null) }
            }

            // TODO password, addresses and credit card manager
            // dismiss and consume immediately as we don't have suggestions yet
            is PromptRequest.SelectCreditCard -> consumeAfter { request.onDismiss() }
            is PromptRequest.SaveCreditCard -> consumeAfter { request.onDismiss() }
            is PromptRequest.SelectLoginPrompt -> consumeAfter { request.onDismiss() }
            is PromptRequest.SaveLoginPrompt -> consumeAfter { request.onDismiss() }
            is PromptRequest.SelectAddress -> consumeAfter { request.onDismiss() }

            else -> throw InvalidParameterException("Not a valid prompt request type $request")
        }
    }}
}

/**
 * List of all prompts who are not to be shown in fullscreen.
 */
@PublishedApi
internal val PROMPTS_TO_EXIT_FULLSCREEN_FOR = listOf<KClass<out PromptRequest>>(
    PromptRequest.Alert::class,
    PromptRequest.TextPrompt::class,
    PromptRequest.Confirm::class,
    PromptRequest.Popup::class,
)

/**
 * Convenience method for executing code if the current [PromptRequest] is one that
 * should not be shown in fullscreen tabs.
 */
internal inline fun <reified T> T.executeIfWindowedPrompt(
    block: () -> Unit,
) where T : PromptRequest {
    PROMPTS_TO_EXIT_FULLSCREEN_FOR
        .firstOrNull {
            this::class == it
        }?.let { block.invoke() }
}

fun BrowserStore.consumeRequest(sessionId: String, request: PromptRequest, consume: () -> Unit = {}) {
    consume()
    dispatch(ContentAction.ConsumePromptRequestAction(sessionId, request))
}


