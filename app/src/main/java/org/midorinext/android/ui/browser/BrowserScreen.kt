package org.midorinext.android.ui.browser

import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.preferences.app.ToolbarShortcut
import org.midorinext.android.adblock.AdBlockerAction
import org.midorinext.android.adblock.BlockingLevel
import org.midorinext.android.lite.LiteMode
import org.midorinext.android.ui.preferences.BlockingLevelRow
import org.midorinext.android.ext.*
import org.midorinext.android.MidoriApplication
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.home.HomePrivateBrowsing
import org.midorinext.android.ui.browser.home.NativeHomeScreen
import org.midorinext.android.newtab.BrowserSurface
import org.midorinext.android.newtab.browserSurface
import org.midorinext.android.ui.browser.menu.BrowserMenu
import org.midorinext.android.ui.browser.mozaccompose.*
import org.midorinext.android.ui.browser.pullToRefresh.PullToRefreshBox
import org.midorinext.android.ui.browser.toolbar.*
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.theme.LocalMidoriTheme
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import org.midorinext.android.ui.widgets.TabCounter
import mozilla.components.support.ktx.android.content.share
import mozilla.components.concept.engine.EngineView
import mozilla.components.ui.icons.R as iconsR

enum class TabOpening {
    NONE, NORMAL, PRIVATE
}

private val ToolbarActionWidth = 48.dp
private val ToolbarActionRippleRadius = 24.dp

@Composable
fun BrowserScreen(
    navigateTo: (NavDestination) -> Unit,
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    viewModel: BrowserScreenViewModel = hiltViewModel(),
    openNewTab: TabOpening = TabOpening.NONE,
    allowSafetyTab: Boolean = true,
) {
    val selectedTabSnapshot by viewModel.selectedTabSnapshot.collectAsStateWithLifecycle()
    val currentUrl = selectedTabSnapshot?.url
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val restoreComplete by viewModel.restoreComplete.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    val tabStripTabs = if (appPrefs.showTabStrip) {
        viewModel.tabStripTabs.collectAsStateWithLifecycle().value
    } else emptyList()
    val private = selectedTabSnapshot?.private == true
    val legacyHomeOrigins by viewModel.legacyHomeOrigins.collectAsStateWithLifecycle()
    val surface = currentUrl?.let { browserSurface(it, selectedTabSnapshot?.private == true, viewModel.isNewTabUrl(it)) }
    val nativeHome = surface == BrowserSurface.HOME
    val privateHome = surface == BrowserSurface.PRIVATE_HOME
    val blankPage = surface == BrowserSurface.BLANK
    val nativeBlockerState by viewModel.nativeBlockerState.collectAsStateWithLifecycle()
    val liteState by viewModel.liteMode.state.collectAsStateWithLifecycle()
    val liteStatus = liteState.statusFor(private)
    val readerModeStatus by viewModel.readerModeStatus.collectAsStateWithLifecycle()
    val hasParentTab by viewModel.hasParentTab.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val switchBrowsingMode: (Boolean) -> Unit = { privateMode ->
        focusManager.clearFocus()
        keyboard?.hide()
        viewModel.switchBrowsingMode(privateMode)
    }
    val restoredReaderPage = currentUrl?.let { url ->
        url.startsWith("moz-extension://", ignoreCase = true) &&
            url.substringBefore('?').substringBefore('#').endsWith("/readerview.html", ignoreCase = true)
    } == true
    val readerViewFeature = if (readerModeStatus.isAvailable || readerModeStatus.isActive || restoredReaderPage) {
        rememberReaderViewFeature(
            store = viewModel.store,
            engine = viewModel.engine,
            appearance = appPrefs.appearance,
        )
    } else null
    readerViewFeature?.let { ComposeFeatureWrapper(it) }

    var engineViewHolder: EngineView? by remember { mutableStateOf(null) }
    var pageSummary by remember { mutableStateOf<String?>(null) }
    var showBlockerDialog by remember { mutableStateOf(false) }
    var showLitePanel by rememberSaveable { mutableStateOf(false) }
    var newTabIntentConsumed by rememberSaveable(openNewTab) { mutableStateOf(false) }
    var nativeHomeReady by remember(selectedTabSnapshot?.id) { mutableStateOf(false) }

    // Navigation to the tab tray disposes the browser surface. Reassert background media after
    // that surface is released so the playing tab remains active without keeping the UI alive.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.toolbarState.updateFocus(false)
            context.activity?.forceHideKeyboard()
            viewModel.keepPlayingMediaWhileSurfaceIsHidden()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onBrowserSurfaceVisible()
    }

    LaunchedEffect(openNewTab) {
        if (!newTabIntentConsumed) {
            newTabIntentConsumed = true
            when (openNewTab) {
                TabOpening.NORMAL -> viewModel.openNewMidoriTab(private = false)
                TabOpening.PRIVATE -> viewModel.openNewMidoriTab(private = true)
                else -> {}
            }
        }
    }
    LaunchedEffect(restoreComplete, selectedTabSnapshot?.id, allowSafetyTab) {
        if (allowSafetyTab && restoreComplete && selectedTabSnapshot == null) {
            viewModel.openSafetyTabIfNeeded()
        }
    }

    LaunchedEffect(selectedTabSnapshot, legacyHomeOrigins) {
        val selectedTab = selectedTabSnapshot ?: return@LaunchedEffect
        viewModel.replaceTabWithNewTab(selectedTab.id, selectedTab.url)
    }

    LaunchedEffect(nativeHome, privateHome, blankPage) {
        if (nativeHome || privateHome || blankPage) {
            engineViewHolder = null
            viewModel.toolbarState.updateVisibility(true)
        }
    }

    LaunchedEffect(privateHome, blankPage, selectedTabSnapshot?.id) {
        if (privateHome || blankPage) {
            withFrameNanos { }
            withFrameNanos { }
            (context.applicationContext as MidoriApplication).onNativeHomeFrameDrawn()
            if (privateHome) viewModel.prepareNativeHomeSession(selectedTabSnapshot?.id)
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

    if (showBlockerDialog) {
        AlertDialog(
            onDismissRequest = { showBlockerDialog = false },
            title = { Text(stringResource(R.string.native_blocker_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.adblock_site_domain), style = MaterialTheme.typography.labelMedium)
                    Text(currentUrl.orEmpty().toCleanHost(), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.native_blocker_blocked_count, nativeBlockerState.blockedCount))
                    BlockingLevel.entries.forEach { level ->
                        BlockingLevelRow(level, nativeBlockerState.level) {
                            viewModel.setNativeBlockerLevelForSite(level)
                            showBlockerDialog = false
                        }
                    }
                    Text(stringResource(R.string.adblock_scope_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNativeBlockerLevelForSite(null)
                    showBlockerDialog = false
                }) {
                    Text(stringResource(R.string.adblock_default_level))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockerDialog = false }) {
                    Text(stringResource(R.string.summary_done))
                }
            },
        )
    }

    if (showLitePanel) {
        LiteModePanel(
            state = liteState,
            privateBrowsing = private,
            onDismiss = { showLitePanel = false },
            onToggle = viewModel.liteMode::setEnabled,
            onCompressionSelected = viewModel.liteMode::setCompression,
        )
    }

    BackHandler(enabled = appPrefs.closeTabsOnExit && !isFullScreen) {
        appViewModel.quit { context.activity?.quit() }
    }

    // Register before the engine handler so page history and text selection take priority.
    // A tab opened by a site returns to its opener when it has no history of its own.
    BackHandler(enabled = hasParentTab && !isFullScreen) {
        viewModel.returnToParentTab()
    }

    if (currentUrl == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (appPrefs.showTabStrip && !isFullScreen && !viewModel.toolbarState.hasFocus) {
            BrowserTabStrip(
                tabs = tabStripTabs,
                selectedTabId = selectedTabSnapshot?.id,
                browserIcons = viewModel.browserIcons,
                onTabSelected = viewModel::selectTab,
                onTabClosed = viewModel::closeTab,
                onNewTab = {
                    viewModel.openNewMidoriTab(private = private)
                },
            )
        }
        HideOnScrollToolbar(
            toolbarState = viewModel.toolbarState,
            toolbar = { modifier ->
                Toolbar(
                    onTextCommit = { text -> viewModel.commitSearch(text, currentUrl.getMidoriSERPCategory()) },
                    modifier = modifier,
                    toolbarState = viewModel.toolbarState,
                    browserIcons = viewModel.browserIcons,
                    beforeTextField = {
                        if (liteState.enabled) {
                            IconButton(
                                onClick = { showLitePanel = true },
                                modifier = Modifier.size(40.dp).testTag("lite_mode_toolbar"),
                            ) {
                                Icon(
                                    painterResource(R.drawable.icons_lite_mode),
                                    stringResource(R.string.lite_mode_title),
                                    tint = if (liteStatus == LiteMode.Status.ACTIVE || liteStatus == LiteMode.Status.DIRECT) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!readerModeStatus.isActive) {
                            AdBlockerAction(protectionEnabled = nativeBlockerState.level != BlockingLevel.OFF) {
                                showBlockerDialog = true
                            }
                        }
                    },
                    beforeTextFieldVisible = {
                        readerModeStatus.isActive ||
                            (!viewModel.toolbarState.hasFocus &&
                                currentUrl.isNotBlank() &&
                                !currentUrl.isMidoriUrl() &&
                                !viewModel.isNewTabUrl(currentUrl) &&
                                currentUrl != "about:blank")
                    },
                    pageEndAction = {
                        ReaderModeAction(
                            active = readerModeStatus.isActive,
                            onClick = {
                                if (readerModeStatus.isActive) {
                                    readerViewFeature?.hideReaderView()
                                } else {
                                    readerViewFeature?.showReaderView()
                                }
                            },
                        )
                    },
                    pageEndActionVisible = {
                        readerModeStatus.isAvailable || readerModeStatus.isActive
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
            modifier = Modifier.fillMaxWidth().weight(1f),
            isFullScreen = isFullScreen,
            lock = { viewModel.showFindInPage || nativeHome || privateHome || blankPage }
        ) { modifier ->
            if (nativeHome) {
                if (nativeHomeReady) GlobalFeatures(appViewModel, viewModel, onManageLogins = { navigateTo(NavDestination.SavedPasswords) })
                val canGoBack by viewModel.canGoBackInPage.collectAsStateWithLifecycle()
                BackHandler(enabled = canGoBack && !viewModel.toolbarState.hasFocus) {
                    viewModel.sessionUseCases.goBack()
                }
                key(selectedTabSnapshot?.id) {
                    NativeHomeScreen(
                        tabId = selectedTabSnapshot?.id,
                        onOpenUrl = viewModel::openHomeUrl,
                        onSelectTab = viewModel::selectTab,
                        onSwitchBrowsingMode = { switchBrowsingMode(true) },
                        onSearch = { viewModel.toolbarState.updateFocus(true) },
                        onFirstFrameDrawn = { tabId ->
                            nativeHomeReady = true
                            (context.applicationContext as MidoriApplication).onNativeHomeFrameDrawn()
                            context.activity?.reportFullyDrawn()
                            viewModel.prepareNativeHomeSession(tabId)
                        },
                        navigateTo = navigateTo,
                        modifier = modifier,
                    )
                }
            } else if (privateHome || blankPage) {
                val canGoBack by viewModel.canGoBackInPage.collectAsStateWithLifecycle()
                BackHandler(enabled = canGoBack && !viewModel.toolbarState.hasFocus) {
                    viewModel.sessionUseCases.goBack()
                }
                if (privateHome) HomePrivateBrowsing(
                    onSwitchBrowsingMode = { switchBrowsingMode(false) },
                    modifier = modifier,
                )
                else Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("native_blank_home"))
            } else {
                GlobalFeatures(appViewModel, viewModel, onManageLogins = { navigateTo(NavDestination.SavedPasswords) })

                PullToRefreshBox(
                    onRefresh = { viewModel.reloadUrl() },
                    enabled = {
                        !isFullScreen && appPrefs.pullToRefreshEnabled &&
                        engineViewHolder?.canScrollVerticallyUp() == false &&
                        engineViewHolder?.getInputResultDetail()?.let {
                            it.canOverscrollTop() &&
                            it.canOverscrollLeft() &&
                            it.canOverscrollRight()
                        } == true
                    },
                    modifier = modifier
                ) {
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
    }

    // Register after the engine session handler so Back exits Reader View before navigating away.
    BackHandler(enabled = readerModeStatus.isActive && !isFullScreen && !viewModel.toolbarState.hasFocus) {
        readerViewFeature?.onBackPressed()
    }
}


@Composable
private fun ReaderModeAction(
    active: Boolean,
    onClick: () -> Unit,
) {
    ToolbarAction(onClick = onClick) {
        Icon(
            painter = painterResource(
                if (active) {
                    iconsR.drawable.mozac_ic_reader_view_fill_24
                } else {
                    iconsR.drawable.mozac_ic_reader_view_24
                },
            ),
            contentDescription = stringResource(
                if (active) {
                    R.string.browser_reader_view_close
                } else {
                    R.string.browser_reader_view_open
                },
            ),
            modifier = Modifier.size(24.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarAction( // TODO rename ToolbarAction to SmallIconButton and move to global widgets
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .width(ToolbarActionWidth)
            .fillMaxHeight()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
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
        ToolbarShortcutAction(
            shortcut = toolbarShortcut,
            viewModel = viewModel,
            onSummarize = onSummarize,
            onEdit = { navigateTo(NavDestination.ToolbarShortcutSettings) },
        )
        TabsButton(navigateTo, viewModel)
        BrowserMenuButton(navigateTo, viewModel, appViewModel)
    }
}

@Composable
private fun ToolbarShortcutAction(
    shortcut: ToolbarShortcut,
    viewModel: BrowserScreenViewModel,
    onSummarize: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val (icon, label) = when (shortcut) {
        ToolbarShortcut.TOOLBAR_SHORTCUT_NEW_TAB -> R.drawable.icons_add_tab to R.string.shortcut_new_tab
        ToolbarShortcut.TOOLBAR_SHORTCUT_SHARE -> R.drawable.icons_share to R.string.shortcut_share
        ToolbarShortcut.TOOLBAR_SHORTCUT_BOOKMARK -> R.drawable.icons_add_bookmark to R.string.shortcut_bookmark
        ToolbarShortcut.TOOLBAR_SHORTCUT_HOMEPAGE -> R.drawable.icons_home to R.string.shortcut_homepage
        ToolbarShortcut.TOOLBAR_SHORTCUT_BACK -> R.drawable.icons_arrow_backward to R.string.shortcut_back
        ToolbarShortcut.TOOLBAR_SHORTCUT_SUMMARIZE -> R.drawable.icons_summarize to R.string.shortcut_summarize
        ToolbarShortcut.TOOLBAR_SHORTCUT_NONE, ToolbarShortcut.UNRECOGNIZED -> return
    }
    var showEditMenu by remember(shortcut) { mutableStateOf(false) }
    Box {
        ToolbarAction(
            onClick = {
                when (shortcut) {
                    ToolbarShortcut.TOOLBAR_SHORTCUT_NEW_TAB -> viewModel.openNewMidoriTab()
                    ToolbarShortcut.TOOLBAR_SHORTCUT_SHARE -> viewModel.currentUrl.value?.takeIf { it.isNotBlank() }?.let(context::share)
                    ToolbarShortcut.TOOLBAR_SHORTCUT_BOOKMARK -> viewModel.addBookmark()
                    ToolbarShortcut.TOOLBAR_SHORTCUT_HOMEPAGE -> viewModel.goToHomepage()
                    ToolbarShortcut.TOOLBAR_SHORTCUT_BACK -> viewModel.goBack()
                    ToolbarShortcut.TOOLBAR_SHORTCUT_SUMMARIZE -> onSummarize()
                    ToolbarShortcut.TOOLBAR_SHORTCUT_NONE, ToolbarShortcut.UNRECOGNIZED -> Unit
                }
            },
            onLongClick = { showEditMenu = true },
            onLongClickLabel = stringResource(R.string.edit),
            modifier = Modifier.testTag("toolbar_shortcut"),
        ) {
            Icon(painterResource(icon), stringResource(label), modifier = Modifier.size(24.dp))
        }
        Dropdown(expanded = showEditMenu, onDismissRequest = { showEditMenu = false }) {
            DropdownItem(
                text = stringResource(R.string.edit),
                icon = R.drawable.icons_edit,
                onClick = { showEditMenu = false; onEdit() },
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabsButton(
    navigateTo: (NavDestination) -> Unit,
    viewModel: BrowserScreenViewModel
) {
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val tabsDescription = if (tabCount == 1) {
        stringResource(R.string.tab_tray_title_single)
    } else {
        stringResource(R.string.tab_tray_title, tabCount)
    }
    var showTabsDropdown by remember { mutableStateOf(false) }

    val private = LocalMidoriTheme.current.private

    val badgeVisible = private && viewModel.toolbarState.visible

    Box(
        modifier = Modifier
            .semantics { contentDescription = tabsDescription }
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
                            contentDescription = stringResource(R.string.tab_tray_private_tabs)
                        )
                    }
                }
            },
            modifier = Modifier.size(24.dp)
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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box {
        val selectedTab by viewModel.selectedTabSnapshot.collectAsStateWithLifecycle()
        var showMenu by rememberSaveable(selectedTab?.id) { mutableStateOf(false) }
        val menuFocus = remember { FocusRequester() }
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        var restoreMenuFocus by remember(selectedTab?.id) { mutableStateOf(false) }
        LaunchedEffect(showMenu, restoreMenuFocus, windowFocused) {
            if (!showMenu && restoreMenuFocus && windowFocused) {
                withFrameNanos { }
                menuFocus.requestFocus()
                restoreMenuFocus = false
            }
        }

        ToolbarAction(modifier = Modifier.focusRequester(menuFocus).focusProperties { canFocus = true }
            .testTag("browser_menu_button"), onClick = {
            viewModel.toolbarState.updateFocus(false)
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            showMenu = true
        }) {
            Icon(
                painter = painterResource(id = R.drawable.icons_more_vertical),
                contentDescription = stringResource(R.string.nav_menu),
                modifier = Modifier.size(24.dp)
            )
        }

        BrowserMenu(
            expanded = showMenu,
            onDismissRequest= { showMenu = false; restoreMenuFocus = true },
            navigateTo = navigateTo,
            viewModel = viewModel,
            applicationViewModel = appViewModel
        )
    }
}
