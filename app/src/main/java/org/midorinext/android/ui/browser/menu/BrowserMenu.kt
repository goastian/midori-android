package org.midorinext.android.ui.browser.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem

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
    var showMoreOptions by remember { mutableStateOf(false) }
    val currentUrl by viewModel.currentUrl.collectAsState()
    val showPageActions = currentUrl.isExternalPage()

    LaunchedEffect(expanded) {
        if (!expanded) {
            showMoreOptions = false
        }
    }

    Dropdown(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (showMoreOptions) {
            MoreOptionsMenu(
                navigateTo = navigateTo,
                viewModel = viewModel,
                applicationViewModel = applicationViewModel,
                showPageActions = showPageActions,
                onBack = { showMoreOptions = false },
                onDismissRequest = onDismissRequest
            )
        } else {
            PrimaryMenu(
                navigateTo = navigateTo,
                viewModel = viewModel,
                currentUrl = currentUrl,
                showPageActions = showPageActions,
                onMoreOptionsClick = { showMoreOptions = true },
                onDismissRequest = onDismissRequest
            )
        }
    }
}

@Composable
private fun PrimaryMenu(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    currentUrl: String?,
    showPageActions: Boolean,
    onMoreOptionsClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    BrowserNavigation(viewModel)
    HorizontalDivider()
    NewTabAction(viewModel, onDismissRequest)
    if (showPageActions && !currentUrl.isNullOrBlank()) {
        ShareAction(url = currentUrl, onDismissRequest = onDismissRequest)
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
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    DropdownItem(
        text = stringResource(id = R.string.menu_more_options),
        icon = R.drawable.icons_more_vertical,
        onClick = onMoreOptionsClick
    )
}

@Composable
private fun MoreOptionsMenu(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    applicationViewModel: MidoriApplicationViewModel,
    showPageActions: Boolean,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val showQuitApp by applicationViewModel.zapOnQuit.collectAsState()

    DropdownItem(
        text = stringResource(id = R.string.menu_back_to_main),
        icon = R.drawable.icons_arrow_backward,
        onClick = onBack
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    PrivateTabAction(viewModel, onDismissRequest)
    DropdownItem(
        text = stringResource(id = R.string.browser_close_tab),
        icon = R.drawable.icons_close,
        onClick = {
            onDismissRequest()
            viewModel.closeCurrentTab()
        }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    if (BuildConfig.FLAVOR_version == "original" &&
        LocalContext.current.selectedLocale().language == "fr"
    ) {
        QwantAccount(viewModel, applicationViewModel, onDismissRequest)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
    AppNavigation(navigateTo, viewModel, onDismissRequest)
    if (showPageActions) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        PageActions(viewModel, onDismissRequest)
    }
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
fun BrowserNavigation(viewModel: BrowserScreenViewModel) {
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val loadingProgress by viewModel.toolbarState.loadingProgress.collectAsState()

    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { viewModel.goBack() }, enabled = canGoBack) {
            Icon(painter = painterResource(id = R.drawable.icons_arrow_backward), contentDescription = "back")
        }
        IconButton(onClick = { viewModel.goForward() }, enabled = canGoForward) {
            Icon(painter = painterResource(id = R.drawable.icons_arrow_forward), contentDescription = "forward")
        }
        if (loadingProgress != 1f) {
            IconButton(onClick = { viewModel.stopLoading() }) {
                Icon(painter = painterResource(id = R.drawable.icons_close), contentDescription = "stop loading")
            }
        } else {
            IconButton(onClick = { viewModel.reloadUrl() }) {
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
fun AppNavigation(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    onDismissRequest: () -> Unit
) {
    val hasReadingModeItems by viewModel.hasReadingModeItems.collectAsState()

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
        text = stringResource(id = R.string.reading_list_title),
        icon = R.drawable.icons_open,
        enabled = hasReadingModeItems,
        onClick = {
            onDismissRequest()
            navigateTo(NavDestination.ReadingList)
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
    val isUrlInReadingList by viewModel.isUrlInReadingList.collectAsState()
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
        DropdownItem(
            text = stringResource(
                id = if (isUrlInReadingList) {
                    R.string.reading_list_remove_current
                } else {
                    R.string.reading_list_add_current
                }
            ),
            icon = R.drawable.icons_bookmark,
            onClick = {
                if (isUrlInReadingList) {
                    viewModel.removeCurrentPageFromReadingList()
                } else {
                    viewModel.addCurrentPageToReadingList()
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
}

private fun String?.isExternalPage(): Boolean {
    if (isNullOrBlank() || this == "about:blank") {
        return false
    }

    return !isMidoriUrl() && toCleanHost() != BuildConfig.QWANT_BASE_URL.toCleanHost()
}
