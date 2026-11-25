package org.midorinext.android.ui.browser.menu

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mozilla.components.support.ktx.android.content.share
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.ext.defaultBrowserStatus
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.animation.MidoriMotion
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import org.midorinext.android.ui.browser.liteStatusLabel
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.vpn.MidoriVpnFeature
import mozilla.components.ui.icons.R as iconsR

private const val SHOW_LITE_MODE_IN_MENU = false

@Composable
fun BrowserMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel
) {
    var showTranslationSheet by rememberSaveable { mutableStateOf(false) }
    var defaultPromptDismissed by rememberSaveable { mutableStateOf(false) }

    if (expanded) {
        val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
        val vpnEnabled by viewModel.isMidoriVpnActionAvailable.collectAsStateWithLifecycle()
        val showQuitApp by applicationViewModel.showQuitApp.collectAsStateWithLifecycle()
        val extensions by viewModel.installedMenuExtensions.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            viewModel.toolbarState.updateFocus(false)
            focusManager.clearFocus()
            keyboard?.hide()
        }
        var showMoreOptions by rememberSaveable { mutableStateOf(false) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var defaultBrowser by remember(context) {
            mutableStateOf(context.defaultBrowserStatus())
        }
        val defaultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            defaultBrowser = context.defaultBrowserStatus()
            defaultPromptDismissed = defaultBrowser.isDefault
        }
        val showPageActions = currentUrl.isExternalPage() && !viewModel.isNewTabUrl(currentUrl)

        val queueAction: (() -> Unit, () -> Unit) -> Unit = { action, dismiss ->
            if (pendingAction == null) {
                pendingAction = action
                dismiss()
            }
        }
        BrowserMenuSheet(
            onDismissRequest = {
                val action = pendingAction
                pendingAction = null
                onDismissRequest()
                action?.invoke()
            },
            navigation = if (showPageActions) {
                { dismiss -> BrowserNavigation(viewModel) { action -> queueAction(action, dismiss) } }
            } else null,
        ) { dismiss ->
            val afterDismiss: (() -> Unit) -> Unit = { action -> queueAction(action, dismiss) }
            val openDestination: (NavDestination) -> Unit = { destination ->
                afterDismiss { navigateTo(destination) }
            }
            BrowserMenuMainContent(
                showDefaultPrompt = !showPageActions && !defaultBrowser.isDefault && !defaultPromptDismissed,
                vpnEnabled = vpnEnabled,
                extensionCount = extensions.size,
                onNavigate = openDestination,
                onVpnClick = {
                    afterDismiss { viewModel.triggerInstalledExtensionAction(MidoriVpnFeature.EXTENSION_ID) }
                },
                onSetDefault = { afterDismiss { defaultLauncher.launch(defaultBrowser.requestIntent()) } },
                onDismissDefault = { defaultPromptDismissed = true },
                liteMode = {
                    if (SHOW_LITE_MODE_IN_MENU) {
                        val tab by viewModel.selectedTabSnapshot.collectAsStateWithLifecycle()
                        val liteState by viewModel.liteMode.state.collectAsStateWithLifecycle()
                        BrowserMenuRow(
                            text = stringResource(R.string.lite_mode_title),
                            icon = R.drawable.icons_lite_mode,
                            checked = liteState.enabled,
                            enabled = liteState.loaded,
                            trailing = {
                                Text(
                                    liteStatusLabel(liteState.statusFor(tab?.private == true)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = { viewModel.liteMode.setEnabled(!liteState.enabled) },
                            modifier = Modifier.testTag("browser_menu_lite_mode"),
                        )
                    }
                },
                showPageActions = showPageActions,
                pageActions = { if (showPageActions) PageActions(viewModel, afterDismiss) },
                account = {
                    BrowserMenuSyncCard(onClick = {
                        afterDismiss {
                            context.startActivity(android.content.Intent(context, org.midorinext.android.sync.SyncActivity::class.java))
                        }
                    })
                },
                moreOptions = {
                    BrowserMenuMoreOptions(expanded = showMoreOptions, onExpandedChange = { showMoreOptions = it }) {
                        BrowserPageShortcuts(
                            showPageActions = showPageActions,
                            viewModel = viewModel,
                            applicationViewModel = applicationViewModel,
                            afterDismiss = afterDismiss,
                            onTranslateClick = { afterDismiss { showTranslationSheet = true } },
                        )
                        BrowserMenuRow(
                            text = stringResource(R.string.pwa_manage_title),
                            icon = R.drawable.icons_add_screen,
                            onClick = { openDestination(NavDestination.WebApps) },
                        )
                    }
                },
            )
            if (showQuitApp) {
                BrowserMenuGroup {
                    BrowserMenuRow(
                        text = stringResource(R.string.menu_quit_app),
                        icon = R.drawable.icons_close,
                        onClick = { afterDismiss { applicationViewModel.quit { context.activity?.quit() } } },
                    )
                }
            }
            BackHandler(enabled = showMoreOptions) { showMoreOptions = false }
        }
    }

    if (showTranslationSheet) {
        val translating = stringResource(R.string.browser_translating_page)
        val translationUnavailable = stringResource(R.string.browser_translation_unavailable)
        val translationState by viewModel.translationSheetState.collectAsStateWithLifecycle()

        TranslationSheet(
            state = translationState,
            onDismissRequest = { showTranslationSheet = false },
            onOpenSettings = {
                showTranslationSheet = false
                navigateTo(NavDestination.TranslationSettings)
            },
            onUpdateOfferTranslation = viewModel::updateTranslationOffer,
            onUpdateAlwaysTranslateSource = viewModel::updateAlwaysTranslateSource,
            onUpdateNeverTranslateSource = viewModel::updateNeverTranslateSource,
            onUpdateNeverTranslateSite = viewModel::updateNeverTranslateSite,
            onTranslate = { fromLanguage, toLanguage ->
                if (viewModel.translateCurrentPage(fromLanguage, toLanguage)) {
                    applicationViewModel.showSnackbar(translating)
                    showTranslationSheet = false
                } else {
                    applicationViewModel.showSnackbar(translationUnavailable)
                }
            }
        )
    }
}

@Composable
internal fun BrowserMenuMoreOptions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(MidoriMotion.SurfaceDurationMillis),
        label = "menu expansion",
    )
    BrowserMenuRow(
        text = stringResource(R.string.menu_more_options),
        icon = R.drawable.icons_more_vertical,
        onClick = { onExpandedChange(!expanded) },
        trailing = {
            Icon(painterResource(R.drawable.icons_chevron_down_small), contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.testTag("browser_menu_more").semantics {
            if (expanded) collapse { onExpandedChange(false); true }
            else expand { onExpandedChange(true); true }
        },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(MidoriMotion.SurfaceDurationMillis)),
        exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(MidoriMotion.ExitDurationMillis)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun BrowserPageShortcuts(
    showPageActions: Boolean,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    afterDismiss: (() -> Unit) -> Unit,
    onTranslateClick: () -> Unit,
) {
    val canInstallWebApp by viewModel.canInstallWebApp.collectAsStateWithLifecycle()
    BrowserMenuRow(
        text = stringResource(R.string.browser_new_tab),
        icon = R.drawable.icons_add_tab,
        onClick = { afterDismiss { viewModel.openNewMidoriTab(private = false) } },
    )
    BrowserMenuRow(
        text = stringResource(R.string.browser_new_tab_private),
        icon = R.drawable.icons_privacy_mask,
        onClick = { afterDismiss { viewModel.openNewMidoriTab(private = true) } },
    )
    if (showPageActions) {
        TranslateAction(viewModel, onClick = onTranslateClick)
        if (canInstallWebApp) {
            val installed = stringResource(R.string.pwa_install_requested)
            val failed = stringResource(R.string.pwa_install_failed)
            BrowserMenuRow(
                text = stringResource(R.string.pwa_install_action),
                icon = R.drawable.icons_add_screen,
                onClick = {
                    afterDismiss {
                        viewModel.installCurrentPageAsWebApp { success ->
                            applicationViewModel.showSnackbar(if (success) installed else failed)
                        }
                    }
                },
            )
        }
        if (viewModel.isShortcutSupported) {
            val added = stringResource(R.string.pwa_shortcut_requested)
            val failed = stringResource(R.string.pwa_shortcut_failed)
            BrowserMenuRow(
                text = stringResource(R.string.menu_add_to_homescreen),
                icon = R.drawable.icons_add_screen,
                onClick = {
                    afterDismiss {
                        viewModel.addShortcutToHomeScreen { success ->
                            applicationViewModel.showSnackbar(if (success) added else failed)
                        }
                    }
                },
            )
        }
        BrowserMenuRow(
            text = stringResource(R.string.menu_save_as_pdf),
            icon = R.drawable.icons_download,
            onClick = { afterDismiss { viewModel.sessionUseCases.saveToPdf() } },
        )
        BrowserMenuRow(
            text = stringResource(R.string.menu_print),
            icon = iconsR.drawable.mozac_ic_print_24,
            onClick = {
                viewModel.store.state.selectedTabId?.let { tabId ->
                    afterDismiss { viewModel.sessionUseCases.printContent(tabId) }
                }
            },
        )
    }
}

@Composable
private fun BrowserNavigation(viewModel: BrowserScreenViewModel, afterDismiss: (() -> Unit) -> Unit) {
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.toolbarState.loadingProgress.collectAsStateWithLifecycle()
    val url by viewModel.currentUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loading = loadingProgress != 1f
    Box(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
        BrowserMenuGroup {
            BrowserMenuActionGrid { range ->
                range.forEach { index ->
                    when (index) {
                        0 -> BrowserMenuAction(stringResource(R.string.shortcut_back), R.drawable.icons_arrow_backward,
                            "browser_menu_back", canGoBack) { afterDismiss { viewModel.goBack() } }
                        1 -> BrowserMenuAction(stringResource(R.string.menu_forward), R.drawable.icons_arrow_forward,
                            "browser_menu_forward", canGoForward) { afterDismiss { viewModel.goForward() } }
                        2 -> BrowserMenuAction(stringResource(R.string.share), R.drawable.icons_share,
                            "browser_menu_share", !url.isNullOrBlank()) { afterDismiss { url?.let { context.share(it) } } }
                        3 -> BrowserMenuAction(stringResource(if (loading) R.string.menu_stop else R.string.menu_refresh),
                            if (loading) R.drawable.icons_close else R.drawable.icons_reload, "browser_menu_reload") {
                            afterDismiss { if (loading) viewModel.stopLoading() else viewModel.reloadUrl() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslateAction(
    viewModel: BrowserScreenViewModel,
    onClick: () -> Unit
) {
    val canTranslate by viewModel.canTranslateCurrentPage.collectAsStateWithLifecycle()

    BrowserMenuRow(
        text = stringResource(R.string.browser_translate_page),
        icon = R.drawable.icons_internet,
        enabled = canTranslate,
        onClick = onClick
    )
}

@Composable
private fun PageActions(viewModel: BrowserScreenViewModel, afterDismiss: (() -> Unit) -> Unit) {
    val isUrlBookmarked by viewModel.isUrlBookmarked.collectAsStateWithLifecycle()
    val desktopSite by viewModel.desktopMode.collectAsStateWithLifecycle()
    BrowserMenuRow(
        text = stringResource(if (isUrlBookmarked) R.string.bookmark_remove_current else R.string.bookmark_add_current),
        icon = if (isUrlBookmarked) R.drawable.icons_delete_bookmark else R.drawable.icons_add_bookmark,
        modifier = Modifier.testTag("browser_menu_bookmark_page"),
        onClick = {
            afterDismiss { if (isUrlBookmarked) viewModel.removeBookmark() else viewModel.addBookmark() }
        },
    )
    BrowserMenuRow(
        text = stringResource(R.string.menu_find_in_page),
        icon = R.drawable.icons_search,
        modifier = Modifier.testTag("browser_menu_find"),
        onClick = { afterDismiss { viewModel.updateShowFindInPage(true) } },
    )
    BrowserMenuRow(
        text = stringResource(R.string.menu_request_desktop_site),
        icon = R.drawable.icons_laptop,
        modifier = Modifier.testTag("browser_menu_desktop"),
        checked = desktopSite,
        trailing = { Switch(checked = desktopSite, onCheckedChange = null) },
        onClick = { viewModel.requestDesktopSite(!desktopSite) },
    )
}

private fun String?.isExternalPage(): Boolean {
    val uri = this?.let(android.net.Uri::parse) ?: return false
    return (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
}
