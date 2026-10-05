package org.midorinext.android.ui.browser.menu

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import mozilla.components.support.ktx.android.content.share
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import org.midorinext.android.vpn.MidoriVpnFeature

// TODO replace canaltoys exception with either specific source file
//  or buildconfig field regarding android capabilities

@Composable
fun BrowserMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel
) {
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val toolbarPosition by applicationViewModel.toolbarPosition.collectAsStateWithLifecycle()
    val toolbarAtBottom = toolbarPosition == ToolbarPosition.BOTTOM
    val showPageActions = currentUrl.isExternalPage()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val menuMaxHeight = if (toolbarAtBottom) {
        (screenHeight * 0.52f).coerceAtMost(400.dp)
    } else {
        (screenHeight - 16.dp).coerceAtLeast(1.dp)
    }
    val menuScrollState = rememberScrollState()
    var showTranslationSheet by rememberSaveable { mutableStateOf(false) }
    var showMoreOptions by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (!expanded) {
            showMoreOptions = false
        }
    }

    LaunchedEffect(expanded, toolbarAtBottom, showMoreOptions) {
        if (!expanded) return@LaunchedEffect

        snapshotFlow { menuScrollState.maxValue }
            .first { maxValue -> maxValue > 0 || !toolbarAtBottom }
        menuScrollState.scrollTo(if (toolbarAtBottom) menuScrollState.maxValue else 0)
    }

    val dismissMenu = {
        showMoreOptions = false
        onDismissRequest()
    }

    Box {
        Dropdown(
            expanded = expanded,
            onDismissRequest = dismissMenu,
            // Keep the wide browser menu close to the overflow button while
            // preserving a 16 dp inset from the screen edge.
            offset = DpOffset(24.dp, if (toolbarAtBottom) (-8).dp else 0.dp),
            modifier = Modifier.widthIn(min = 280.dp, max = 320.dp)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = menuMaxHeight)
                    .verticalScroll(menuScrollState)
            ) {
                BrowserMenuContent(
                    navigateTo = navigateTo,
                    viewModel = viewModel,
                    applicationViewModel = applicationViewModel,
                    currentUrl = currentUrl,
                    showPageActions = showPageActions,
                    toolbarPosition = toolbarPosition,
                    showMoreOptions = showMoreOptions,
                    onShowMoreOptionsChange = { showMoreOptions = it },
                    onDismissRequest = dismissMenu,
                    onTranslateClick = { showTranslationSheet = true }
                )
            }
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
private fun BrowserMenuContent(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    currentUrl: String?,
    showPageActions: Boolean,
    toolbarPosition: ToolbarPosition,
    showMoreOptions: Boolean,
    onShowMoreOptionsChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onTranslateClick: () -> Unit,
) {
    val isMidoriVpnActionAvailable by viewModel.isMidoriVpnActionAvailable.collectAsStateWithLifecycle()
    val showQuitApp by applicationViewModel.zapOnQuit.collectAsStateWithLifecycle()

    val toolbarAtBottom = toolbarPosition == ToolbarPosition.BOTTOM

    if (showMoreOptions && showPageActions) {
        if (toolbarAtBottom) {
            PageActions(viewModel, applicationViewModel, onDismissRequest)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        DropdownItem(
            text = stringResource(R.string.menu_back_to_main),
            icon = R.drawable.icons_arrow_backward,
            onClick = { onShowMoreOptionsChange(false) },
        )
        if (!toolbarAtBottom) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            PageActions(viewModel, applicationViewModel, onDismissRequest)
        }
        return
    }

    if (toolbarAtBottom) {
        BrowserMenuSettings(
            navigateTo = navigateTo,
            viewModel = viewModel,
            applicationViewModel = applicationViewModel,
            showQuitApp = showQuitApp,
            onDismissRequest = onDismissRequest,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BrowserMenuDestinations(
            navigateTo = navigateTo,
            viewModel = viewModel,
            applicationViewModel = applicationViewModel,
            showPageActions = showPageActions,
            onShowMoreOptionsChange = onShowMoreOptionsChange,
            onDismissRequest = onDismissRequest,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BrowserPageShortcuts(
            currentUrl = currentUrl,
            showPageActions = showPageActions,
            viewModel = viewModel,
            applicationViewModel = applicationViewModel,
            onDismissRequest = onDismissRequest,
            onTranslateClick = onTranslateClick,
        )
        HorizontalDivider()
        MidoriVpnAction(
            enabled = isMidoriVpnActionAvailable,
            viewModel = viewModel,
            onDismissRequest = onDismissRequest,
        )
        HorizontalDivider()
        BrowserNavigation(viewModel, onDismissRequest)
    } else {
        BrowserNavigation(viewModel, onDismissRequest)
        HorizontalDivider()
        MidoriVpnAction(
            enabled = isMidoriVpnActionAvailable,
            viewModel = viewModel,
            onDismissRequest = onDismissRequest,
        )
        HorizontalDivider()
        BrowserPageShortcuts(
            currentUrl = currentUrl,
            showPageActions = showPageActions,
            viewModel = viewModel,
            applicationViewModel = applicationViewModel,
            onDismissRequest = onDismissRequest,
            onTranslateClick = onTranslateClick,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BrowserMenuDestinations(
            navigateTo = navigateTo,
            viewModel = viewModel,
            applicationViewModel = applicationViewModel,
            showPageActions = showPageActions,
            onShowMoreOptionsChange = onShowMoreOptionsChange,
            onDismissRequest = onDismissRequest,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        BrowserMenuSettings(
            navigateTo = navigateTo,
            viewModel = viewModel,
            applicationViewModel = applicationViewModel,
            showQuitApp = showQuitApp,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
private fun MidoriVpnAction(
    enabled: Boolean,
    viewModel: BrowserScreenViewModel,
    onDismissRequest: () -> Unit,
) {
    DropdownItem(
        text = stringResource(id = R.string.menu_midori_vpn),
        icon = R.drawable.ic_midori_vpn_action,
        enabled = enabled,
        onClick = {
            onDismissRequest()
            viewModel.triggerInstalledExtensionAction(MidoriVpnFeature.EXTENSION_ID)
        },
    )
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
            DropdownItem(
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
private fun BrowserMenuDestinations(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    showPageActions: Boolean,
    onShowMoreOptionsChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (BuildConfig.FLAVOR_version == "original" &&
        LocalContext.current.selectedLocale().language == "fr"
    ) {
        QwantAccount(viewModel, applicationViewModel, onDismissRequest)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
    AppNavigation(navigateTo, onDismissRequest)
    if (showPageActions) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        DropdownItem(
            text = stringResource(R.string.menu_more_options),
            icon = R.drawable.icons_more_vertical,
            onClick = { onShowMoreOptionsChange(true) },
        )
    }
}

@Composable
private fun BrowserMenuSettings(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    showQuitApp: Boolean,
    onDismissRequest: () -> Unit,
) {
    ExtensionsSection(
        viewModel = viewModel,
        onExtensionsClick = {
            onDismissRequest()
            navigateTo(NavDestination.Extensions)
        },
    )
    DropdownItem(
        text = stringResource(id = R.string.settings),
        icon = R.drawable.icons_settings,
        onClick = {
            onDismissRequest()
            navigateTo(NavDestination.Preferences)
        },
    )
    if (showQuitApp && BuildConfig.FLAVOR_target != "canaltoys") {
        val activity = LocalContext.current.activity
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        DropdownItem(
            text = stringResource(id = R.string.menu_quit_app),
            icon = R.drawable.icons_close,
            onClick = {
                applicationViewModel.zap(skipConfirmation = true) { success ->
                    if (success) {
                        activity?.quit()
                    }
                }
            },
        )
    }
}

@Composable
fun QwantAccount(
    viewModel: BrowserScreenViewModel,
    appViewModel: MidoriApplicationViewModel,
    onDismissRequest: () -> Unit
) {
    val isAccountConnected = appViewModel.cookieState.isConnected

    DropdownItem(
        text = stringResource(if (isAccountConnected) R.string.menu_account else R.string.menu_login),
        icon = R.drawable.icons_account,
        onClick = {
            onDismissRequest()
            viewModel.tabsUseCases.selectOrAddTab(url = "https://accounts.astian.org")
        }
    )
}

@Composable
fun BrowserNavigation(
    viewModel: BrowserScreenViewModel,
    onDismissRequest: () -> Unit,
) {
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.toolbarState.loadingProgress.collectAsStateWithLifecycle()

    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
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
    DropdownItem(
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
    DropdownItem(
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
    DropdownItem(
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

    DropdownItem(
        text = stringResource(R.string.browser_translate_page),
        icon = R.drawable.icons_internet,
        enabled = canTranslate,
        onClick = onClick
    )
}

@Composable
fun AppNavigation(
    navigateTo: (NavDestination) -> Unit,
    onDismissRequest: () -> Unit
) {
    DropdownItem(
        text = stringResource(id = R.string.history),
        icon = R.drawable.icons_history,
        onClick = {
            onDismissRequest()
            navigateTo(NavDestination.History)
        }
    )
    DropdownItem(
        text = stringResource(id = R.string.bookmarks),
        icon = R.drawable.icons_bookmark,
        onClick = {
            onDismissRequest()
            navigateTo(NavDestination.Bookmarks)
        }
    )
    DropdownItem(
        text = stringResource(R.string.pwa_manage_title),
        icon = R.drawable.icons_add_screen,
        onClick = {
            onDismissRequest()
            navigateTo(NavDestination.WebApps)
        }
    )
    if (BuildConfig.FLAVOR_target != "canaltoys") {
        DropdownItem(
            text = stringResource(id = R.string.browser_downloads),
            icon = R.drawable.icons_download,
            onClick = {
                onDismissRequest()
                navigateTo(NavDestination.Downloads)
            }
        )
    }
}

@Composable
private fun ExtensionsSection(
    viewModel: BrowserScreenViewModel,
    onExtensionsClick: () -> Unit,
) {
    val installedExtensions by viewModel.installedMenuExtensions.collectAsStateWithLifecycle()
    val extensionCount = installedExtensions.size
    val extensionsLabel = if (extensionCount > 0) {
        stringResource(R.string.extensions_title) + " $extensionCount"
    } else {
        stringResource(R.string.extensions_title)
    }

    DropdownItem(
        text = extensionsLabel,
        icon = R.drawable.icons_extension,
        onClick = onExtensionsClick
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

    if (BuildConfig.FLAVOR_target != "canaltoys") {
        DropdownItem(
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
            DropdownItem(
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
    }
    DropdownItem(
        text = stringResource(id = R.string.menu_request_desktop_site),
        icon = R.drawable.icons_laptop,
        trailing = { Switch(checked = desktopSite, onCheckedChange = onDesktopSiteClicked) },
        onClick = { onDesktopSiteClicked(!desktopSite) }
    )
    if (currentUrl?.isNotEmpty() == true) {
        DropdownItem(
            text = stringResource(id = R.string.menu_find_in_page),
            icon = R.drawable.icons_search,
            onClick = {
                viewModel.updateShowFindInPage(true)
                onDismissRequest()
            }
        )
    }
    DropdownItem(
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
