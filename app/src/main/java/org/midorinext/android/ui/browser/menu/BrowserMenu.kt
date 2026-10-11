package org.midorinext.android.ui.browser.menu

import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mozilla.components.support.ktx.android.content.share
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.openDefaultAppsSystemSettings
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.vpn.MidoriVpnFeature

@Composable
fun BrowserMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel
) {
    var showTranslationSheet by rememberSaveable { mutableStateOf(false) }
    var translationRequested by remember { mutableStateOf(false) }
    var defaultPromptDismissed by rememberSaveable { mutableStateOf(false) }

    if (expanded) {
        val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
        val vpnEnabled by viewModel.isMidoriVpnActionAvailable.collectAsStateWithLifecycle()
        val showQuitApp by applicationViewModel.showQuitApp.collectAsStateWithLifecycle()
        val extensions by viewModel.installedMenuExtensions.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var showMoreOptions by rememberSaveable { mutableStateOf(false) }
        val roleManager = remember(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService(RoleManager::class.java) else null
        }
        val canRequestDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true
        } else true
        val isDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        val defaultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            defaultPromptDismissed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        }
        val showPageActions = currentUrl.isExternalPage()

        BrowserMenuSheet(onDismissRequest = {
            onDismissRequest()
            if (translationRequested) {
                translationRequested = false
                showTranslationSheet = true
            }
        }) { dismiss ->
            val openDestination: (NavDestination) -> Unit = { destination ->
                dismiss()
                navigateTo(destination)
            }
            if (showPageActions) {
                BrowserMenuGroup { BrowserNavigation(viewModel, dismiss) }
            }
            BrowserMenuMainContent(
                showDefaultPrompt = !showPageActions && canRequestDefault && !isDefault && !defaultPromptDismissed,
                vpnEnabled = vpnEnabled,
                extensionCount = extensions.size,
                onNavigate = openDestination,
                onVpnClick = {
                    dismiss()
                    viewModel.triggerInstalledExtensionAction(MidoriVpnFeature.EXTENSION_ID)
                },
                onAccountClick = {
                    dismiss()
                    viewModel.tabsUseCases.selectOrAddTab(url = "https://accounts.astian.org")
                },
                onSetDefault = {
                    dismiss()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                        defaultLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                    } else context.openDefaultAppsSystemSettings()
                },
                onDismissDefault = { defaultPromptDismissed = true },
            )
            BrowserMenuGroup {
                BrowserMenuRow(
                    text = stringResource(if (showMoreOptions) R.string.menu_back_to_main else R.string.menu_more_options),
                    icon = if (showMoreOptions) R.drawable.icons_arrow_backward else R.drawable.icons_more_vertical,
                    onClick = { showMoreOptions = !showMoreOptions },
                )
                if (showMoreOptions) {
                    BrowserPageShortcuts(
                        currentUrl = currentUrl,
                        showPageActions = showPageActions,
                        viewModel = viewModel,
                        applicationViewModel = applicationViewModel,
                        onDismissRequest = dismiss,
                        onTranslateClick = { translationRequested = true },
                    )
                    BrowserMenuRow(
                        text = stringResource(R.string.pwa_manage_title),
                        icon = R.drawable.icons_add_screen,
                        onClick = { openDestination(NavDestination.WebApps) },
                    )
                    if (showPageActions) PageActions(viewModel, applicationViewModel, dismiss)
                }
            }
            if (showQuitApp) {
                BrowserMenuGroup {
                    BrowserMenuRow(
                        text = stringResource(R.string.menu_quit_app),
                        icon = R.drawable.icons_close,
                        onClick = { applicationViewModel.quit { context.activity?.quit() } },
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
private fun BrowserPageShortcuts(
    currentUrl: String?,
    showPageActions: Boolean,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    onDismissRequest: () -> Unit,
    onTranslateClick: () -> Unit,
) {
    val canInstallWebApp by viewModel.canInstallWebApp.collectAsStateWithLifecycle()
    NewTabAction(viewModel, onDismissRequest)
    PrivateTabAction(viewModel, onDismissRequest)
    if (showPageActions && !currentUrl.isNullOrBlank()) {
        if (canInstallWebApp) {
            val installed = stringResource(R.string.pwa_install_requested)
            val failed = stringResource(R.string.pwa_install_failed)
            BrowserMenuRow(
                text = stringResource(R.string.pwa_install_action),
                icon = R.drawable.icons_add_screen,
                onClick = {
                    viewModel.installCurrentPageAsWebApp { success ->
                        applicationViewModel.showSnackbar(if (success) installed else failed)
                    }
                    onDismissRequest()
                },
            )
        }
        ShareAction(url = currentUrl, onDismissRequest = onDismissRequest)
        TranslateAction(
            viewModel = viewModel,
            onClick = {
                onDismissRequest()
                onTranslateClick()
            },
        )
    }
}

@Composable
fun BrowserNavigation(
    viewModel: BrowserScreenViewModel,
    onDismissRequest: () -> Unit,
) {
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.toolbarState.loadingProgress.collectAsStateWithLifecycle()

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).heightIn(min = 60.dp),
    ) {
        IconButton(
            onClick = {
                onDismissRequest()
                viewModel.goBack()
            },
            enabled = canGoBack
        ) {
            Icon(
                painter = painterResource(id = R.drawable.icons_arrow_backward),
                contentDescription = stringResource(R.string.nav_back),
            )
        }
        IconButton(
            onClick = {
                onDismissRequest()
                viewModel.goForward()
            },
            enabled = canGoForward
        ) {
            Icon(
                painter = painterResource(id = R.drawable.icons_arrow_forward),
                contentDescription = stringResource(R.string.nav_forward),
            )
        }
        if (loadingProgress != 1f) {
            IconButton(
                onClick = {
                    onDismissRequest()
                    viewModel.stopLoading()
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_close),
                    contentDescription = stringResource(R.string.menu_stop),
                )
            }
        } else {
            IconButton(
                onClick = {
                    onDismissRequest()
                    viewModel.reloadUrl()
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_reload),
                    contentDescription = stringResource(R.string.menu_refresh),
                )
            }
        }
    }
}

@Composable
private fun NewTabAction(viewModel: BrowserScreenViewModel, onDismissRequest: () -> Unit) {
    BrowserMenuRow(
        text = stringResource(id = R.string.browser_new_tab),
        icon = R.drawable.icons_add_tab,
        onClick = {
            onDismissRequest()
            viewModel.openNewMidoriTab(private = false)
        }
    )
}

@Composable
private fun PrivateTabAction(viewModel: BrowserScreenViewModel, onDismissRequest: () -> Unit) {
    BrowserMenuRow(
        text = stringResource(id = R.string.browser_new_tab_private),
        icon = R.drawable.icons_privacy_mask,
        onClick = {
            onDismissRequest()
            viewModel.openNewMidoriTab(private = true)
        }
    )
}

@Composable
private fun ShareAction(url: String, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    BrowserMenuRow(
        text = stringResource(id = R.string.share),
        icon = R.drawable.icons_share,
        onClick = {
            onDismissRequest()
            context.share(url)
        }
    )
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
fun PageActions(
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    onDismissRequest: () -> Unit,
) {
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val isUrlBookmarked by viewModel.isUrlBookmarked.collectAsStateWithLifecycle()
    val desktopSite by viewModel.desktopMode.collectAsStateWithLifecycle()
    val onDesktopSiteClicked = { checked: Boolean ->
        viewModel.requestDesktopSite(checked)
    }

    BrowserMenuRow(
        text = stringResource(
            id = if (isUrlBookmarked) {
                R.string.bookmark_remove_current
            } else {
                R.string.bookmark_add_current
            }
        ),
        icon = if (isUrlBookmarked) R.drawable.icons_delete_bookmark else R.drawable.icons_add_bookmark,
        onClick = {
            if (isUrlBookmarked) {
                viewModel.removeBookmark()
            } else {
                viewModel.addBookmark()
            }
            onDismissRequest()
        }
    )
    if (viewModel.isShortcutSupported) {
        val addedMessage = stringResource(R.string.pwa_shortcut_requested)
        val failedMessage = stringResource(R.string.pwa_shortcut_failed)
        BrowserMenuRow(
            text = stringResource(R.string.menu_add_to_homescreen),
            icon = R.drawable.icons_add_screen,
            onClick = {
                viewModel.addShortcutToHomeScreen { success ->
                    applicationViewModel.showSnackbar(if (success) addedMessage else failedMessage)
                }
                onDismissRequest()
            }
        )
    }
    BrowserMenuRow(
        text = stringResource(id = R.string.menu_request_desktop_site),
        icon = R.drawable.icons_laptop,
        trailing = { Switch(checked = desktopSite, onCheckedChange = onDesktopSiteClicked) },
        onClick = { onDesktopSiteClicked(!desktopSite) }
    )
    if (currentUrl?.isNotEmpty() == true) {
        BrowserMenuRow(
            text = stringResource(id = R.string.menu_find_in_page),
            icon = R.drawable.icons_search,
            onClick = {
                viewModel.updateShowFindInPage(true)
                onDismissRequest()
            }
        )
    }
    BrowserMenuRow(
        text = stringResource(id = R.string.menu_save_as_pdf),
        icon = R.drawable.icons_download,
        onClick = {
            onDismissRequest()
            viewModel.sessionUseCases.saveToPdf()
        }
    )
}

private fun String?.isExternalPage(): Boolean {
    if (isNullOrBlank() || startsWith("about:blank") || startsWith("moz-extension://")) {
        return false
    }

    return !isMidoriUrl() && toCleanHost() != BuildConfig.QWANT_BASE_URL.toCleanHost()
}
