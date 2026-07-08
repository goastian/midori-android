package org.midorinext.android.ui.browser.mozaccompose

import android.view.View
import android.os.Environment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import org.midorinext.android.BuildConfig
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.downloads.temporary.CopyDownloadFeature
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.ui.widgets.SnackbarDelegate

@Composable
fun ContextMenuFeature(
    store: BrowserStore,
    client: Client,
    tabsUseCases: TabsUseCases,
    contextMenuUseCases: ContextMenuUseCases,
    showSnackbar: (String, MidoriApplicationViewModel.SnackbarAction?, Boolean, SnackbarDuration) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val copyDownloadFeature = remember {
        CopyDownloadFeature(
            context = context,
            store = store,
            httpClient = client,
            tabId = null,
            onCopyConfirmation = { showSnackbar("Copied", null, true, SnackbarDuration.Short) }
        )
    }
    ComposeFeatureWrapper(feature = copyDownloadFeature)

    val candidates = remember(context, view) {
        ContextMenuCandidate.defaultCandidates(
            context = context,
            tabsUseCases = tabsUseCases,
            contextMenuUseCases = contextMenuUseCases,
            snackBarParentView = view, // Useless with compose, but keeps compatibility with mozac
            snackbarDelegate = object : SnackbarDelegate {
                override fun show(
                    snackBarParentView: View,
                    text: Int,
                    subText: String?,
                    subTextOverflow: TextOverflow?,
                    duration: Int,
                    isError: Boolean,
                    action: Int,
                    withDismissAction: Boolean,
                    listener: ((v: View) -> Unit)?
                ) {
                    if (text != 0) {
                        this.show(
                            snackBarParentView,
                            context.getString(text),
                            subText,
                            subTextOverflow,
                            duration,
                            isError,
                            if (action != 0) context.getString(action) else null,
                            withDismissAction,
                            listener
                        )
                    }
                }

                override fun show(
                    snackBarParentView: View,
                    text: String,
                    subText: String?,
                    subTextOverflow: TextOverflow?,
                    duration: Int,
                    isError: Boolean,
                    action: String?,
                    withDismissAction: Boolean,
                    listener: ((View) -> Unit)?
                ) {
                    showSnackbar(
                        text,
                        if (action != null) {
                            MidoriApplicationViewModel.SnackbarAction(action) {
                                listener?.invoke(view)
                            }
                        } else null,
                        withDismissAction,
                        when (duration) {
                            SnackbarDuration.Short.ordinal -> SnackbarDuration.Short
                            SnackbarDuration.Long.ordinal -> SnackbarDuration.Long
                            SnackbarDuration.Indefinite.ordinal -> SnackbarDuration.Indefinite
                            else -> SnackbarDuration.Short
                        }
                    )
                }
            },
            downloadsLocation = { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path }
        )
    }
    val tab by store.observeAsComposableState { state -> state.findTabOrCustomTabOrSelectedTab() }
    val hitResult by store.observeAsComposableState { state -> state.selectedTab?.content?.hitResult }
    val validCandidates: List<ContextMenuCandidate> by remember(tab, hitResult) { mutableStateOf(
        tab?.let { session ->
            hitResult?.let { hit ->
                val baseCandidates = candidates.filter { candidate -> candidate.showFor(session, hit) }
                if (BuildConfig.FLAVOR_target == "canaltoys") {
                    baseCandidates.filter { candidate -> !candidate.id.startsWith("mozac.feature.contextmenu.share") }
                } else baseCandidates
            }
        } ?: listOf()
    )}

    tab?.let { session ->
        hitResult?.let { hit ->
            if (validCandidates.isNotEmpty()) {
                LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)
                Dropdown(
                    expanded = validCandidates.isNotEmpty(),
                    onDismissRequest = { contextMenuUseCases.consumeHitResult(session.id) },
                ) {
                    validCandidates.forEach { candidate ->
                        DropdownItem(text = candidate.label, onClick = {
                            candidate.action.invoke(session, hit)
                            contextMenuUseCases.consumeHitResult(session.id)
                        })
                    }
                }
            } else {
                contextMenuUseCases.consumeHitResult(session.id)
            }
        }
    }
}