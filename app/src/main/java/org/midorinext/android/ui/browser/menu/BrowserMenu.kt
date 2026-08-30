package org.midorinext.android.ui.browser.menu

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
import mozilla.components.support.ktx.android.content.share
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.ext.toCleanHost
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
    val currentUrl by viewModel.currentUrl.collectAsState()
    val showPageActions = currentUrl.isExternalPage()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var showTranslationSheet by rememberSaveable { mutableStateOf(false) }

    Box {
        Dropdown(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            // Keep the wide browser menu close to the overflow button while
            // preserving a 16 dp inset from the screen edge.
            offset = DpOffset(24.dp, 0.dp),
            modifier = Modifier.widthIn(min = 280.dp, max = 320.dp)
        ) {
            Column(
                modifier = Modifier
                    // The previous fixed 640 dp cap forced an internal scroll on tall phones
                    // even when the complete menu fit on screen. Leave a small edge inset while
                    // allowing the popup to use the actual available display height.
                    .heightIn(max = (screenHeight - 16.dp).coerceAtLeast(1.dp))
                    .verticalScroll(rememberScrollState())
            ) {
                BrowserMenuContent(
                    navigateTo = navigateTo,
                    viewModel = viewModel,
                    applicationViewModel = applicationViewModel,
                    currentUrl = currentUrl,
                    showPageActions = showPageActions,
                    onDismissRequest = onDismissRequest,
                    onTranslateClick = { showTranslationSheet = true }
                )
            }
        }
    }

    if (showTranslationSheet) {
        val translating = stringResource(R.string.browser_translating_page)
        val translationUnavailable = stringResource(R.string.browser_translation_unavailable)
        val translationState by viewModel.translationSheetState.collectAsState()

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
    onDismissRequest: () -> Unit,
    onTranslateClick: () -> Unit,
) {
    val isMidoriVpnActionAvailable by viewModel.isMidoriVpnActionAvailable.collectAsState()
    val showQuitApp by applicationViewModel.zapOnQuit.collectAsState()

    BrowserNavigation(viewModel, onDismissRequest)
    HorizontalDivider()
    DropdownItem(
        text = stringResource(id = R.string.menu_midori_vpn),
        icon = R.drawable.ic_midori_vpn_action,
        enabled = isMidoriVpnActionAvailable,
        onClick = {
            onDismissRequest()
            viewModel.triggerInstalledExtensionAction(MidoriVpnFeature.EXTENSION_ID)
        }
    )
    HorizontalDivider()
    NewTabAction(viewModel, onDismissRequest)
    PrivateTabAction(viewModel, onDismissRequest)
    if (showPageActions && !currentUrl.isNullOrBlank()) {
        ShareAction(url = currentUrl, onDismissRequest = onDismissRequest)
        TranslateAction(
            viewModel = viewModel,
            onClick = {
                onDismissRequest()
                onTranslateClick()
            }
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    if (BuildConfig.FLAVOR_version == "original" &&
        LocalContext.current.selectedLocale().language == "fr"
    ) {
        QwantAccount(viewModel, applicationViewModel, onDismissRequest)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
    AppNavigation(navigateTo, onDismissRequest)
    if (showPageActions) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        PageActions(viewModel, onDismissRequest)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    ExtensionsSection(
        viewModel = viewModel,
        onExtensionsClick = {
            onDismissRequest()
            navigateTo(NavDestination.Extensions)
        }
    )
    DropdownItem(
        text = stringResource(id = R.string.settings),
        icon = R.drawable.icons_settings,
        onClick = {
            onDismissRequest()
            navigateTo(NavDestination.Preferences)
        }
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
                    } else {
                        // TODO handle clear on quit fails
                    }
                }
            }
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
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val loadingProgress by viewModel.toolbarState.loadingProgress.collectAsState()

    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = {
                onDismissRequest()
                viewModel.goBack()
            },
            enabled = canGoBack
        ) {
            Icon(painter = painterResource(id = R.drawable.icons_arrow_backward), contentDescription = "back")
        }
        IconButton(
            onClick = {
                onDismissRequest()
                viewModel.goForward()
            },
            enabled = canGoForward
        ) {
            Icon(painter = painterResource(id = R.drawable.icons_arrow_forward), contentDescription = "forward")
        }
        if (loadingProgress != 1f) {
            IconButton(
                onClick = {
                    onDismissRequest()
                    viewModel.stopLoading()
                }
            ) {
                Icon(painter = painterResource(id = R.drawable.icons_close), contentDescription = "stop loading")
            }
        } else {
            IconButton(
                onClick = {
                    onDismissRequest()
                    viewModel.reloadUrl()
                }
            ) {
                Icon(painter = painterResource(id = R.drawable.icons_reload), contentDescription = "reload")
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
    val canTranslate by viewModel.canTranslateCurrentPage.collectAsState()

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
    val installedExtensions by viewModel.installedMenuExtensions.collectAsState()
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
    onDismissRequest: () -> Unit,
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isUrlBookmarked by viewModel.isUrlBookmarked.collectAsState()
    val desktopSite by viewModel.desktopMode.collectAsState()
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
            DropdownItem(
                text = stringResource(id = R.string.menu_add_to_homescreen),
                icon = R.drawable.icons_add_screen,
                onClick = {
                    viewModel.addShortcutToHomeScreen()
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
