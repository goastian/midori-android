package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.BuildConfig
import org.midorinext.android.legacy.ClFeature
import org.midorinext.android.mozac.downloads.openDownloadedFile
import org.midorinext.android.contentBlocker.ContentBlockerObserver
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import org.midorinext.android.ui.browser.mozaccompose.downloads.DownloadFeature
import org.midorinext.android.ui.browser.mozaccompose.permissions.PermissionsFeature
import org.midorinext.android.ui.browser.mozaccompose.prompts.PromptFeature
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.R as mozacR


@Composable
fun GlobalFeatures(
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    viewModel: BrowserScreenViewModel = hiltViewModel(),
) {
    FullScreenFeature(
        store = viewModel.store,
        toolbarState = viewModel.toolbarState,
        sessionUseCases = viewModel.sessionUseCases
    )

    WindowFeature(
        store = viewModel.store,
        tabsUseCases = viewModel.tabsUseCases
    )

    ContextMenuFeature(
        store = viewModel.store,
        client = viewModel.client,
        tabsUseCases = viewModel.tabsUseCases,
        contextMenuUseCases = viewModel.contextMenuUseCases,
        showSnackbar = { message, action, dismiss, duration ->
            appViewModel.showSnackbar(message, action, dismiss, duration)
        }
    )

    PermissionsFeature(
        store = viewModel.store,
        storage = viewModel.permissionStorage
    )

    val context = LocalContext.current
    val completedDownloadText = stringResource(id = mozacR.string.mozac_feature_downloads_completed_notification_text2)
    val failedDownloadText = stringResource(id = mozacR.string.mozac_feature_downloads_failed_notification_text2)
    DownloadFeature(
        store = viewModel.store,
        useCases = viewModel.downloadUseCases,
        downloadManager = viewModel.downloadManager,
        fileSizeFormatter = viewModel.fileSizeFormatter,
        onDownloadStopped = { state, _, status ->
            if (status == DownloadState.Status.COMPLETED) {
                appViewModel.showSnackbar(
                    completedDownloadText,
                    if (BuildConfig.FLAVOR_target != "canaltoys") {
                        MidoriApplicationViewModel.SnackbarAction("Open") {
                            context.openDownloadedFile(state.filePath, state.contentType)
                        }
                    } else {
                        null
                    }
                )
            } else if (status == DownloadState.Status.FAILED) {
                appViewModel.showSnackbar(failedDownloadText)
            }
        },
        showSnackbar = { message, action -> appViewModel.showSnackbar(message, action) }
    )

    val appPreferences by viewModel.appPreferences.collectAsState()
    PromptFeature(
        store = viewModel.store,
        exitFullscreenUseCase = viewModel.sessionUseCases.exitFullscreen,
        appPreferences = appPreferences
    )

    ClFeature()

    EngineSettingsFeature(
        appViewModel = appViewModel,
        engine = viewModel.engine,
        appPreferences = appPreferences,
    )

    val session by viewModel.currentEngineSession.collectAsState()
    ToolbarAlwaysVisibleWhenScrolledToTopFeature(
        toolbarState = viewModel.toolbarState,
        session = session
    )

    ContentBlockerObserver(
        contentBlockerState = viewModel.contentBlockerState,
        session = session
    )
}
