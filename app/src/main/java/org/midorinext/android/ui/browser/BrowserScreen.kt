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
import org.midorinext.android.ext.*
import org.midorinext.android.contentBlocker.ContentBlockerOverlay
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.home.HomeScreen
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
import org.midorinext.android.ui.zap.ZapButton
import kotlinx.coroutines.delay
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
    val tabCount by viewModel.tabCount.collectAsState()
    val appPrefs by viewModel.appPreferences.collectAsState()
    val private by appViewModel.isPrivate.collectAsState()

    var engineViewHolder: EngineView? by remember { mutableStateOf(null) }

    LaunchedEffect(openNewTab) {
        when (openNewTab) {
            TabOpening.NORMAL -> viewModel.openNewMidoriTab(private = false)
            TabOpening.PRIVATE -> viewModel.openNewMidoriTab(private = true)
            else -> {}
        }
    }
    LaunchedEffect(true) {
        if (tabCount == 0) {
            viewModel.openSafetyTabIfNeeded()
        }
    }

    LaunchedEffect(currentUrl) {
        viewModel.adBlockerState.updateSelectedTab(currentUrl)
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

    val showHome = currentUrl?.let { it.isMidoriUrl() && !it.isMidoriSERPUrl() } == true

    if (showHome) {
        HomeScreen(
            adBlockerState = viewModel.adBlockerState,
            preferences = appPrefs,
            tabCount = tabCount,
            onSearch = { text -> viewModel.commitSearch(text) },
            onOpenUrl = { url -> viewModel.tabsUseCases.selectOrAddTab(url = url) },
            onOpenHome = { viewModel.goToHomepage() },
            onOpenBookmarks = { navigateTo(NavDestination.Bookmarks) },
            onOpenTabs = { navigateTo(NavDestination.Tabs) },
            onOpenSettings = { navigateTo(NavDestination.Preferences) }
        )
        return
    }

    HideOnScrollToolbar(
        toolbarState = viewModel.toolbarState,
        toolbar = { modifier ->
            Toolbar(
                onTextCommit = { text -> viewModel.commitSearch(text, currentUrl?.getMidoriSERPCategory()) },
                modifier = modifier,
                toolbarState = viewModel.toolbarState,
                browserIcons = viewModel.browserIcons,
                beforeTextField = { AdBlockerAction(viewModel.adBlockerState, openLink = { url -> viewModel.tabsUseCases.addTab(url) }) },
                beforeTextFieldVisible = { !viewModel.toolbarState.hasFocus && currentUrl?.isNotBlank() == true && currentUrl?.isMidoriUrl() == false && currentUrl != "about:blank" },
                afterTextField = { AfterActions(navigateTo, viewModel, appViewModel) },
                afterTextFieldVisible = { !viewModel.toolbarState.hasFocus },
                onMidoriIconClicked = { viewModel.goToHomepage() }
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
            Box(modifier = Modifier
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
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(ToolbarActionWidth)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = true,
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
    appViewModel: MidoriApplicationViewModel
) {
    val private = appViewModel.isPrivate.collectAsState()
    val privateBeforeClick = private.value
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
        ZapButton(appViewModel, afterZap = { success ->
            if (success) {
                viewModel.openNewMidoriTab(privateBeforeClick)
            }
        })
        TabsButton(navigateTo, viewModel)
        BrowserMenuButton(navigateTo, viewModel, appViewModel)
        if (BuildConfig.FLAVOR_target == "canaltoys") {
            ExitButton(appViewModel = appViewModel)
        }
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
            DropdownItem(
                text = stringResource(id = R.string.browser_close_tab),
                icon = R.drawable.icons_close,
                onClick = {
                    viewModel.closeCurrentTab()
                    showTabsDropdown = false
                }
            )
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
