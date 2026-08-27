package org.midorinext.android.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.adblock.AdBlockerAction
import org.midorinext.android.adblock.MidoriPrivacyFeature
import org.midorinext.android.ext.*
import org.midorinext.android.contentBlocker.ContentBlockerOverlay
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.newtab.MidoriNewTabFeature
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.home.HomePrivateBrowsing
import org.midorinext.android.ui.browser.menu.BrowserMenu
import org.midorinext.android.ui.browser.mozaccompose.*
import org.midorinext.android.ui.browser.pullToRefresh.PullToRefreshBox
import org.midorinext.android.ui.browser.toolbar.*
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.theme.LocalMidoriTheme
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import org.midorinext.android.ui.widgets.TabCounter
import kotlinx.coroutines.delay
import mozilla.components.support.ktx.android.content.share
import mozilla.components.concept.engine.EngineView
import org.midorinext.android.BuildConfig

enum class TabOpening {
    NONE, NORMAL, PRIVATE
}

private val ToolbarActionWidth = 44.dp
private val ToolbarActionRippleRadius = 22.dp

@Composable
fun BrowserScreen(
    navigateTo: (NavDestination) -> Unit,
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    viewModel: BrowserScreenViewModel = hiltViewModel(),
    openNewTab: TabOpening = TabOpening.NONE
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val selectedTabSnapshot by viewModel.selectedTabSnapshot.collectAsState()
    val tabCount by viewModel.tabCount.collectAsState()
    val restoreComplete by viewModel.restoreComplete.collectAsState()
    val appPrefs by viewModel.appPreferences.collectAsState()
    val private by appViewModel.isPrivate.collectAsState()
    val newTabState by viewModel.newTabState.collectAsState()
    val isMidoriPrivacyActionAvailable by viewModel.isMidoriPrivacyActionAvailable.collectAsState()
    val context = LocalContext.current

    var engineViewHolder: EngineView? by remember { mutableStateOf(null) }
    var pageSummary by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openNewTab) {
        when (openNewTab) {
            TabOpening.NORMAL -> viewModel.openNewMidoriTab(private = false)
            TabOpening.PRIVATE -> viewModel.openNewMidoriTab(private = true)
            else -> {}
        }
    }
    LaunchedEffect(restoreComplete, tabCount) {
        if (restoreComplete && tabCount == 0) {
            viewModel.openSafetyTabIfNeeded()
        }
    }

    LaunchedEffect(selectedTabSnapshot, newTabState) {
        val selectedTab = selectedTabSnapshot ?: return@LaunchedEffect
        when {
            selectedTab.url.isLegacyMidoriHomeUrl() && viewModel.isNewTabEnabled -> {
                viewModel.replaceTabWithNewTab(selectedTab.id, selectedTab.url)
            }
            viewModel.isNewTabLoadingUrl(selectedTab.url) &&
                newTabState is MidoriNewTabFeature.InstallState.Ready -> {
                viewModel.replaceTabWithNewTab(selectedTab.id, selectedTab.url)
            }
        }
    }

    /* val activity = LocalContext.current.activity
    Onboarding { success ->
        if (success) {
            viewModel.toolbarState.updateFocus(true)
        } else {
            activity?.quit()
        }
    } */

    KeyboardObserver(toolbarState = viewModel.toolbarState)
    ShakeToSummarizeEffect(enabled = appPrefs.shakeToSummarizeEnabled) {
        viewModel.summarizeCurrentPage(
            onResult = { summary ->
                if (summary.isBlank()) {
                    appViewModel.showSnackbar(context.getString(R.string.summary_unavailable))
                } else {
                    pageSummary = summary
                }
            },
            onError = { appViewModel.showSnackbar(context.getString(R.string.summary_unavailable)) }
        )
    }

    pageSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { pageSummary = null },
            title = { Text(stringResource(R.string.summary_title)) },
            text = { Text(summary) },
            confirmButton = {
                TextButton(onClick = { pageSummary = null }) {
                    Text(stringResource(R.string.summary_done))
                }
            }
        )
    }

    HideOnScrollToolbar(
        toolbarState = viewModel.toolbarState,
        toolbar = { modifier ->
            Toolbar(
                onTextCommit = { text -> viewModel.commitSearch(text, currentUrl?.getMidoriSERPCategory()) },
                modifier = modifier,
                toolbarState = viewModel.toolbarState,
                browserIcons = viewModel.browserIcons,
                beforeTextField = {
                    AdBlockerAction(enabled = isMidoriPrivacyActionAvailable) {
                        viewModel.triggerInstalledExtensionAction(MidoriPrivacyFeature.EXTENSION_ID)
                    }
                },
                beforeTextFieldVisible = {
                    !viewModel.toolbarState.hasFocus &&
                        currentUrl?.isNotBlank() == true &&
                        currentUrl?.isMidoriUrl() == false &&
                        !viewModel.isNewTabUrl(currentUrl) &&
                        currentUrl != "about:blank"
                },
                afterTextField = {
                    AfterActions(
                        navigateTo,
                        viewModel,
                        appViewModel,
                        appPrefs.toolbarShortcut,
                        onSummarize = {
                            viewModel.summarizeCurrentPage(
                                onResult = { summary ->
                                    if (summary.isBlank()) {
                                        appViewModel.showSnackbar(context.getString(R.string.summary_unavailable))
                                    } else {
                                        pageSummary = summary
                                    }
                                },
                                onError = {
                                    appViewModel.showSnackbar(context.getString(R.string.summary_unavailable))
                                }
                            )
                        }
                    )
                },
                afterTextFieldVisible = { !viewModel.toolbarState.hasFocus },
                onMidoriIconClicked = { viewModel.goToHomepage() },
                onSwipeUp = {
                    if (appPrefs.swipeToolbarToShowTabsEnabled) navigateTo(NavDestination.Tabs)
                },
                onSwipeDown = {
                    if (appPrefs.swipeToolbarToShowTabsEnabled) navigateTo(NavDestination.Tabs)
                },
                onSwipeLeft = {
                    if (appPrefs.swipeAddressBarToSwitchTabsEnabled) viewModel.switchTab(1)
                },
                onSwipeRight = {
                    if (appPrefs.swipeAddressBarToSwitchTabsEnabled) viewModel.switchTab(-1)
                }
            )
        },
        engineView = engineViewHolder,
        modifier = Modifier.fillMaxSize(),
        lock = { viewModel.showFindInPage }
    ) { modifier ->
        if (currentUrl != null) {
            if (currentUrl == "" && private) {
                HomePrivateBrowsing(modifier)
            } else {
                GlobalFeatures(appViewModel, viewModel)

                PullToRefreshBox(
                    onRefresh = { viewModel.reloadUrl() },
                    enabled = {
                        appPrefs.pullToRefreshEnabled &&
                        engineViewHolder?.canScrollVerticallyUp() == false &&
                        engineViewHolder?.getInputResultDetail()?.let {
                            it.canOverscrollTop() &&
                            it.canOverscrollLeft() &&
                            it.canOverscrollRight()
                        } == true
                    },
                    modifier = modifier
                ) {
                    val contentBlockerStatus = viewModel.contentBlockerState.status
                    if (contentBlockerStatus != ContentBlockerState.Status.ALLOWED) {
                        ContentBlockerOverlay(
                            contentBlockerStatus,
                            viewModel.contentBlockerState.blockReason,
                            imageModifier = Modifier.width(300.dp),
                            imageScale = ContentScale.FillWidth,
                            alignment = Alignment.TopCenter
                        )
                    } else {
                        EngineView(
                            engine = viewModel.engine,
                            modifier = Modifier.fillMaxSize()
                        ) { engineView ->
                            engineViewHolder = engineView
                            EngineViewFeatures(engineView, viewModel)
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarAction( // TODO rename ToolbarAction to SmallIconButton and move to global widgets
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(ToolbarActionWidth)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = false,
                    radius = ToolbarActionRippleRadius
                )
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun AfterActions(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    appViewModel: MidoriApplicationViewModel,
    toolbarShortcut: org.midorinext.android.preferences.app.ToolbarShortcut,
    onSummarize: () -> Unit,
) {
    Row {
        if (BuildConfig.FLAVOR_target == "canaltoys") {
            val canGoBack by viewModel.canGoBack.collectAsState()
            val canGoForward by viewModel.canGoForward.collectAsState()
            IconButton(
                onClick = { viewModel.goBack() },
                enabled = canGoBack,
                modifier = Modifier
                    .width(ToolbarActionWidth)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Icon(painter = painterResource(id = R.drawable.icons_arrow_backward), contentDescription = "back")
            }
            IconButton(
                onClick = { viewModel.goForward() },
                enabled = canGoForward,
                modifier = Modifier
                    .width(ToolbarActionWidth)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Icon(painter = painterResource(id = R.drawable.icons_arrow_forward), contentDescription = "forward")
            }
        }
        ToolbarShortcutAction(
            shortcut = toolbarShortcut,
            viewModel = viewModel,
            onSummarize = onSummarize
        )
        TabsButton(navigateTo, viewModel)
        BrowserMenuButton(navigateTo, viewModel, appViewModel)
        if (BuildConfig.FLAVOR_target == "canaltoys") {
            ExitButton(appViewModel = appViewModel)
        }
    }
}

@Composable
private fun ToolbarShortcutAction(
    shortcut: org.midorinext.android.preferences.app.ToolbarShortcut,
    viewModel: BrowserScreenViewModel,
    onSummarize: () -> Unit,
) {
    val context = LocalContext.current
    when (shortcut) {
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_NEW_TAB -> ToolbarAction(
            onClick = { viewModel.openNewMidoriTab() }
        ) {
            Icon(painterResource(R.drawable.icons_add_tab), stringResource(R.string.shortcut_new_tab))
        }
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_SHARE -> ToolbarAction(
            onClick = { viewModel.currentUrl.value?.takeIf { it.isNotBlank() }?.let(context::share) }
        ) {
            Icon(painterResource(R.drawable.icons_share), stringResource(R.string.shortcut_share))
        }
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_BOOKMARK -> ToolbarAction(
            onClick = { viewModel.addBookmark() }
        ) {
            Icon(painterResource(R.drawable.icons_add_bookmark), stringResource(R.string.shortcut_bookmark))
        }
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_HOMEPAGE -> ToolbarAction(
            onClick = viewModel::goToHomepage
        ) {
            Icon(painterResource(R.drawable.icons_home), stringResource(R.string.shortcut_homepage))
        }
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_BACK -> ToolbarAction(
            onClick = { viewModel.goBack() }
        ) {
            Icon(painterResource(R.drawable.icons_arrow_backward), stringResource(R.string.shortcut_back))
        }
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_SUMMARIZE -> ToolbarAction(
            onClick = onSummarize
        ) {
            Icon(painterResource(R.drawable.icons_summarize), stringResource(R.string.shortcut_summarize))
        }
        org.midorinext.android.preferences.app.ToolbarShortcut.TOOLBAR_SHORTCUT_NONE,
        org.midorinext.android.preferences.app.ToolbarShortcut.UNRECOGNIZED -> Unit
    }
}

@Composable
fun ExitButton(
    appViewModel: MidoriApplicationViewModel
) {
    val shouldZapOnQuit by appViewModel.zapOnQuit.collectAsState()
    val activity = LocalContext.current.activity

    ToolbarAction(onClick = {
        if (shouldZapOnQuit) {
            appViewModel.zap(skipConfirmation = true) { success ->
                if (success) {
                    activity?.quit()
                } else {
                    // TODO handle clear on quit fails
                }
            }
        } else {
            activity?.quit()
        }
    }) {
        Icon(
            painter = painterResource(id = R.drawable.icons_close),
            contentDescription = "Close app",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabsButton(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel
) {
    val tabCount by viewModel.tabCount.collectAsState()
    var showTabsDropdown by remember { mutableStateOf(false) }

    val private = LocalMidoriTheme.current.private

    var badgeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(private, viewModel.toolbarState.visible) {
        badgeVisible = if (private && viewModel.toolbarState.visible) {
            delay(200)
            true
        } else {
            false
        }
    }

    Box(
        modifier = Modifier
            .width(ToolbarActionWidth)
            .fillMaxHeight()
            .combinedClickable(
                onClick = {
                    navigateTo(NavDestination.Tabs)
                },
                onLongClick = { showTabsDropdown = true },
                enabled = true,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = false,
                    radius = ToolbarActionRippleRadius
                )
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeVisible) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(18.dp)
                            .offset(x = (-4).dp, y = 2.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            )
                    ) {
                        Icon(
                            painterResource(R.drawable.icons_privacy_mask_small),
                            contentDescription = "private navigation indicator"
                        )
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            TabCounter(tabCount)
        }

        Dropdown(
            expanded = showTabsDropdown,
            onDismissRequest = { showTabsDropdown = false },
            modifier = Modifier.defaultMinSize(minWidth = 240.dp)
        ) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DropdownItem(
                text = stringResource(id = R.string.browser_new_tab),
                icon = R.drawable.icons_add_tab,
                onClick = {
                    viewModel.openNewMidoriTab(false)
                    showTabsDropdown = false
                }
            )
            DropdownItem(
                text = stringResource(id = R.string.browser_new_tab_private),
                icon = R.drawable.icons_privacy_mask,
                onClick = {
                    viewModel.openNewMidoriTab(true)
                    showTabsDropdown = false
                }
            )
        }
    }
}

@Composable
fun BrowserMenuButton(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel,
    appViewModel: MidoriApplicationViewModel
) {
    Box {
        var showMenu by remember { mutableStateOf(false) }

        ToolbarAction(onClick = {
            showMenu = true
        }) {
            Icon(
                painter = painterResource(id = R.drawable.icons_more_vertical),
                contentDescription = "menu",
                modifier = Modifier.fillMaxSize()
            )
        }

        BrowserMenu(
            expanded = showMenu,
            onDismissRequest= { showMenu = false },
            navigateTo = navigateTo,
            viewModel = viewModel,
            applicationViewModel = appViewModel
        )
    }
}
