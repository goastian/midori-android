package org.midorinext.android.ui.browser.mozaccompose.prompts

import android.security.KeyChain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import androidx.core.content.ContextCompat
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.AlertDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.AuthenticationDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.AutofillPicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.ChoiceDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.ConfirmDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.PromptDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.RepostDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.LoginPicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.SaveLoginDialog
import org.midorinext.android.ui.browser.mozaccompose.prompts.dialog.internalcopy.PromptAbuserDetector
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.colorpicker.ColorPicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.DateTimePicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.FilePicker
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.colorpicker.rememberColorPickerState
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.concept.storage.LoginHint
import mozilla.components.feature.prompts.share.DefaultShareDelegate
import mozilla.components.feature.prompts.share.ShareDelegate
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.lib.state.ext.observeAsComposableState
import kotlin.reflect.KClass
import mozilla.components.feature.prompts.R as mozacR

@Composable
fun rememberDefaultShareDelegate(): DefaultShareDelegate {
    return remember { DefaultShareDelegate() }
}

@Composable
@Suppress("REDUNDANT_ELSE_IN_WHEN")
fun PromptFeature(
    store: BrowserStore,
    exitFullscreenUseCase: SessionUseCases.ExitFullScreenUseCase,
    appPreferences: AppPreferences,
    loginRepository: LoginAutofillRepository,
    shareDelegate: ShareDelegate = rememberDefaultShareDelegate()
) {
    val context = LocalContext.current

    val promptAbuserDetector = remember { PromptAbuserDetector() }

    val promptState by store.observeAsComposableState { state ->
        state.selectedTab?.let { PromptState(it.id, it.content.loading, it.content.promptRequests) }
    }
    val loading = promptState?.loading
    val lastRequest = promptState?.requests?.activePrompt()

    LaunchedEffect(true) {
        promptAbuserDetector.resetJSAlertAbuseState()
    }

    LaunchedEffect(promptState?.sessionId, loading) {
        if (loading == false) {
            promptAbuserDetector.resetJSAlertAbuseState()
        }
    }

    LaunchedEffect(lastRequest) {
        lastRequest?.executeIfWindowedPrompt { exitFullscreenUseCase() }
    }

    LaunchedEffect(promptState) {
        promptState?.sessionId?.let { dismissObsoleteAutofillRequests(store, it) }
    }

    val session = promptState?.sessionId ?: return
    val request = lastRequest ?: return
    key(session, request.uid) {
        val response = remember { PromptResponse(store, session, request) }
        val consume: () -> Unit = { response.respond() }
        val consumeAfter: (() -> Unit) -> Unit = response::respond

        DisposableEffect(request.uid) {
            onDispose {
                if (request is PromptRequest.SelectLoginPrompt || request is PromptRequest.SelectCreditCard ||
                    request is PromptRequest.SelectAddress || request is PromptRequest.SaveLoginPrompt
                ) response.dismiss()
            }
        }

        when (request) {
            is PromptRequest.Share -> LaunchedEffect(request.uid) {
                shareDelegate.showShareSheet(
                    context = context,
                    shareData = request.data,
                    onDismiss = { consumeAfter { request.onDismiss() } },
                    onSuccess = { consumeAfter { request.onSuccess() } }
                )
            }

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
            is PromptRequest.Redirect -> YesNoDialog(
                onDismissRequest = { consumeAfter { request.onDismiss() } },
                onYes = { consumeAfter { request.onAllow() } },
                onNo = { consumeAfter { request.onDeny() } },
                title = stringResource(mozacR.string.mozac_feature_prompts_redirect_dialog_title),
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
                LaunchedEffect(request.uid) {
                    context.activity?.let { activity ->
                        KeyChain.choosePrivateKeyAlias(
                            activity,
                            { alias -> ContextCompat.getMainExecutor(context).execute { consumeAfter { request.onComplete(alias) } } },
                            arrayOf("RSA", "EC"), // keyTypes
                            null, // issuers - currently not supported
                            request.host, // the host that requested the certificate
                            -1, // specify the default port to simplify the UI
                            null // alias - leave null for now to not preselect a certificate
                        )
                    } ?: run {
                        consumeAfter { request.onComplete(null) }
                    }
                }
            }

            is PromptRequest.SaveLoginPrompt -> {
                val login = request.logins.firstOrNull()
                if (appPreferences.savePasswordsEnabled && login != null) {
                    SaveLoginDialog(
                        login = login,
                        repository = loginRepository,
                        onSave = { entry, existingGuid -> consumeAfter {
                            loginRepository.authorizeSave(entry, existingGuid)
                            request.onConfirm(entry)
                        } },
                        onDismiss = response::dismiss,
                    )
                } else {
                    ConsumePromptEffect(request.uid) { consumeAfter { request.onDismiss() } }
                }
            }
            is PromptRequest.SelectLoginPrompt -> {
                val logins = request.logins.filter { it.hint != LoginHint.EMAIL_MASK }
                if (appPreferences.passwordAutofillEnabled && logins.isNotEmpty()) {
                    LoginPicker(
                        logins = logins,
                        repository = loginRepository,
                        onSelect = { login -> consumeAfter { request.onConfirm(login) } },
                        onDismiss = response::dismiss,
                    )
                } else {
                    ConsumePromptEffect(request.uid) { consumeAfter { request.onDismiss() } }
                }
            }
            is PromptRequest.SaveCreditCard -> {
                if (appPreferences.autofillCardsEnabled && request.creditCard.isValid) {
                    YesNoDialog(
                        onDismissRequest = { consumeAfter { request.onDismiss() } },
                        onYes = { consumeAfter { request.onConfirm(request.creditCard) } },
                        onNo = { consumeAfter { request.onDismiss() } },
                        title = stringResource(R.string.prompt_save_card_title),
                        description = stringResource(
                            R.string.prompt_save_card_description,
                            request.creditCard.obfuscatedCardNumber
                        )
                    )
                } else {
                    ConsumePromptEffect(request.uid) { consumeAfter { request.onDismiss() } }
                }
            }
            is PromptRequest.SelectCreditCard -> {
                if (appPreferences.autofillCardsEnabled && request.creditCards.isNotEmpty()) {
                    AutofillPicker(
                        title = stringResource(R.string.prompt_fill_card_title),
                        options = request.creditCards,
                        label = { it.obfuscatedCardNumber },
                        detail = { it.name },
                        onSelect = { creditCard -> consumeAfter { request.onConfirm(creditCard) } },
                        onDismiss = response::dismiss,
                    )
                } else {
                    ConsumePromptEffect(request.uid) { consumeAfter { request.onDismiss() } }
                }
            }
            is PromptRequest.SelectAddress -> {
                if (appPreferences.autofillAddressesEnabled && request.addresses.isNotEmpty()) {
                    AutofillPicker(
                        title = stringResource(R.string.prompt_fill_address_title),
                        options = request.addresses,
                        label = { it.addressLabel },
                        detail = { it.name },
                        onSelect = { address -> consumeAfter { request.onConfirm(address) } },
                        onDismiss = response::dismiss,
                    )
                } else {
                    ConsumePromptEffect(request.uid) { consumeAfter { request.onDismiss() } }
                }
            }
            is PromptRequest.SaveAddress -> {
                if (appPreferences.autofillAddressesEnabled) {
                    YesNoDialog(
                        onDismissRequest = response::dismiss,
                        onYes = { consumeAfter { request.onConfirm(request.address) } },
                        onNo = response::dismiss,
                        title = stringResource(mozacR.string.mozac_feature_prompts_save_address_prompt_title),
                        description = request.address.addressLabel,
                        yesText = stringResource(mozacR.string.mozac_feature_prompt_save_confirmation),
                    )
                } else {
                    ConsumePromptEffect(request.uid, response::dismiss)
                }
            }
            is PromptRequest.FolderUploadPrompt -> YesNoDialog(
                onDismissRequest = { consumeAfter { request.onDismiss() } },
                onYes = { consumeAfter { request.onConfirm() } },
                onNo = { consumeAfter { request.onDismiss() } },
                title = request.folderName,
                description = request.folderName,
                yesText = stringResource(mozacR.string.mozac_feature_prompts_allow),
                noText = stringResource(mozacR.string.mozac_feature_prompts_deny),
            )
            is PromptRequest.WebAuthnRelatedOriginPrompt -> YesNoDialog(
                onDismissRequest = { consumeAfter { request.onDismiss() } },
                onYes = { consumeAfter { request.onConfirm() } },
                onNo = { consumeAfter { request.onDismiss() } },
                title = request.rpId,
                description = request.origin,
                yesText = stringResource(mozacR.string.mozac_feature_prompts_allow),
                noText = stringResource(mozacR.string.mozac_feature_prompts_deny),
            )
            is PromptRequest.IdentityCredential,
            is PromptRequest.Folder -> ConsumePromptEffect(request.uid) {
                consumeAfter { (request as PromptRequest.Dismissible).onDismiss() }
            }

            // A browser must never terminate because Gecko introduced a prompt that this UI does
            // not render yet. Consume unknown prompts and dismiss them when the API supports it.
            else -> ConsumePromptEffect(request.uid) {
                consumeAfter { (request as? PromptRequest.Dismissible)?.onDismiss?.invoke() }
            }
        }
    }
}

private data class PromptState(val sessionId: String, val loading: Boolean, val requests: List<PromptRequest>)

private val PromptRequest.isAutofillSelection: Boolean
    get() = this is PromptRequest.SelectLoginPrompt || this is PromptRequest.SelectCreditCard || this is PromptRequest.SelectAddress

private fun List<PromptRequest>.activePrompt(): PromptRequest? = lastOrNull { !it.isAutofillSelection } ?: lastOrNull()

internal fun dismissObsoleteAutofillRequests(store: BrowserStore, sessionId: String) {
    val session = store.state.findTabOrCustomTab(sessionId) ?: return
    val activeUid = session.content.promptRequests.activePrompt()?.uid
    session.content.promptRequests.filter {
        it.isAutofillSelection && (session.content.loading || it.uid != activeUid)
    }.forEach { PromptResponse(store, sessionId, it).dismiss() }
}

@Composable
private fun ConsumePromptEffect(uid: String, consume: () -> Unit) {
    LaunchedEffect(uid) { consume() }
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
    try {
        consume()
    } finally {
        dispatch(ContentAction.ConsumePromptRequestAction(sessionId, request))
    }
}
