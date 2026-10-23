package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import org.midorinext.android.R
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.newtab.defaultHomeShortcuts
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.homeIconOrigin
import org.midorinext.android.newtab.publicHomeUrl
import org.midorinext.android.preferences.app.HomeShortcut
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.tabs.TabThumbnail
import org.midorinext.android.ui.theme.LocalMidoriTheme
import kotlin.math.roundToInt

internal val wallpaperTextShadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeHomeScreen(
    tabId: String?,
    onOpenUrl: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onSwitchBrowsingMode: () -> Unit,
    onSearch: () -> Unit,
    onFirstFrameDrawn: (String?) -> Unit,
    navigateTo: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NativeHomeViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var dataReady by remember(tabId) { mutableStateOf(false) }
    LaunchedEffect(tabId) {
        withFrameNanos { }
        withFrameNanos { }
        onFirstFrameDrawn(tabId)
        dataReady = true
        viewModel.initialize()
    }
    val recent = key(tabId) {
        if (dataReady && !preferences.hideHomeRecentVisits) {
            viewModel.recentVisits.collectAsStateWithLifecycle(initialValue = emptyList()).value
        } else emptyList()
    }
    val continuation = if (dataReady && !preferences.hideHomeContinue) {
        viewModel.continuation.collectAsStateWithLifecycle().value
    } else null
    val blocked = if (dataReady && !preferences.hideHomePrivacy) {
        viewModel.blockedCount.collectAsStateWithLifecycle().value
    } else 0L
    val configuration = LocalConfiguration.current
    val windowSize = LocalWindowInfo.current.containerSize
    val language = configuration.locales[0].language
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(dataReady, preferences.hideHomeStories, language, lifecycleOwner) {
        if (dataReady && !preferences.hideHomeStories) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    viewModel.loadStories(language).join()
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    viewModel.stopStories()
                }
            }
        }
    }
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val shortcuts = if (preferences.homeShortcutsInitialized) preferences.homeShortcutsList else remember { defaultHomeShortcuts() }
    val shortcutState = rememberSaveable(saver = HomeShortcutUiState.Saver) { HomeShortcutUiState() }
    var homeMenuPosition by remember { mutableStateOf<Offset?>(null) }
    val haptics = LocalHapticFeedback.current
    val homeOptionsLabel = stringResource(R.string.home_context_menu)
    val wallpaper = HomeWallpaper.fromId(preferences.homeWallpaperId)
    val hasWallpaper = wallpaper != HomeWallpaper.NONE
    var wallpaperReady by remember(wallpaper) { mutableStateOf(!hasWallpaper) }
    val colors = MaterialTheme.colorScheme
    val background = if (LocalMidoriTheme.current.dark) colors.surface else Color(0xFFF5F8F5)
    val cardColor = if (LocalMidoriTheme.current.dark) colors.surfaceContainer else Color.White
    val insetColor = if (LocalMidoriTheme.current.dark) colors.surfaceContainerHigh else Color(0xFFE8F0E8)
    val shortcutColumns = ((windowSize.width / LocalDensity.current.density - 32) /
        if (preferences.homeLargeShortcuts) 88 else 72).toInt().coerceIn(3, 6)
    val listState = rememberLazyListState()
    val thumbnailContent = remember(preferences, recent, continuation, blocked, stories, background, configuration, windowSize, wallpaperReady) {
        listOf(preferences, recent, continuation, blocked, stories, background, configuration, windowSize, wallpaperReady)
    }
    val thumbnailModifier = rememberNativeHomeThumbnail(
        tabId = tabId.takeIf { dataReady && wallpaperReady },
        contentKey = thumbnailContent,
        scrolling = listState.isScrollInProgress,
        onThumbnail = viewModel::updateThumbnail,
    )

    Box(modifier.fillMaxSize().then(thumbnailModifier).background(background)
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = { position ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                homeMenuPosition = position
            })
        }
        .semantics {
            onLongClick(label = homeOptionsLabel) { homeMenuPosition = Offset.Zero; true }
        }
        .testTag(if (wallpaperReady) "native_home_background:${wallpaper.id}" else "native_home_loading_background:${wallpaper.id}")) {
        if (hasWallpaper) {
            HomeWallpaperImage(wallpaper, Modifier.matchParentSize(), onReady = { wallpaperReady = true })
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.32f),
            ))))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("native_home"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                CompositionLocalProvider(LocalContentColor provides if (hasWallpaper) Color.White else LocalContentColor.current) {
                    NativeHomeHeader(private = false, onSwitchBrowsingMode = onSwitchBrowsingMode,
                        showLogo = !preferences.hideHomeLogo,
                        onCustomize = if (preferences.hideHomeCustomizeShortcut) null else {
                            { navigateTo(NavDestination.HomepageSettings) }
                        })
                }
            }
            if (!preferences.hideHomeShortcuts) {
                item(key = "shortcuts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeSectionHeading(R.string.home_shortcuts, R.string.home_show_all,
                            onAdd = if (preferences.hideHomeAddShortcut) null else { { shortcutState.pickerParentId = "" } }, wallpaper = hasWallpaper) {
                            shortcutState.collectionId = ""
                        }
                        HomeShortcutGrid(shortcuts.filter { it.parentId.isEmpty() }, shortcuts, preferences,
                            shortcutColumns, insetColor, hasWallpaper, viewModel, dataReady, shortcutState, onOpenUrl)
                    }
                }
            }
            if (!preferences.hideHomePrivacy) {
                item(key = "privacy") {
                    Surface(onClick = { navigateTo(NavDestination.AdBlockSettings) }, shape = RoundedCornerShape(12.dp),
                            color = if (hasWallpaper) Color.Transparent else colors.primary.copy(alpha = 0.06f),
                            modifier = Modifier.fillMaxWidth().testTag("native_home_protection")) {
                            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(mozilla.components.ui.icons.R.drawable.mozac_ic_shield_24), null,
                                    tint = if (hasWallpaper) Color.White else colors.primary, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.home_blocked, blocked), Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall.copy(shadow = if (hasWallpaper) wallpaperTextShadow else null),
                                    color = if (hasWallpaper) Color.White else colors.onSurface)
                                Icon(painterResource(R.drawable.icons_arrow_forward), null, Modifier.size(16.dp),
                                    tint = if (hasWallpaper) Color.White else colors.primary)
                            }
                        }
                }
            }
            if (!preferences.hideHomeContinue) {
                item(key = "continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeSectionHeading(R.string.home_continue, wallpaper = hasWallpaper)
                        HomeCard(cardColor, Modifier.testTag("native_home_continue"),
                            onClick = { continuation?.let { onSelectTab(it.tabId) } ?: onSearch() }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(insetColor),
                                    contentAlignment = Alignment.Center) {
                                    HomeFavicon(continuation?.url, viewModel, dataReady, Modifier.size(28.dp))
                                    if (dataReady && continuation != null) {
                                        TabThumbnail(continuation.tabId, false, viewModel.thumbnailStorage)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(continuation?.title ?: stringResource(R.string.home_start_browsing),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text(continuation?.url?.toCleanHost() ?: stringResource(R.string.home_continue_empty),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            if (!preferences.hideHomeRecentVisits) {
                item(key = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HomeSectionHeading(R.string.home_recent_visits, R.string.home_show_all, wallpaper = hasWallpaper) { navigateTo(NavDestination.History) }
                        if (recent.isEmpty()) {
                            HomeCard(cardColor, onClick = onSearch) {
                                Text(stringResource(R.string.home_recent_empty), Modifier.padding(12.dp),
                                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(14.dp), color = cardColor, contentColor = colors.onSurface) {
                                Column {
                                    recent.take(3).forEach { page ->
                                        key(page.url) {
                                            Surface(onClick = { onOpenUrl(page.url) }, color = cardColor, contentColor = colors.onSurface,
                                                modifier = Modifier.fillMaxWidth().testTag("native_home_recent")) {
                                                Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    HomeFavicon(page.url, viewModel, dataReady, Modifier.size(24.dp))
                                                    Text(page.title, Modifier.weight(1f), maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!preferences.hideHomeStories) {
                item(key = "stories") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeSectionHeading(R.string.home_stories, R.string.home_all_stories, wallpaper = hasWallpaper) {
                            onOpenUrl(if (language == "es") "https://www.bbc.com/mundo" else "https://www.bbc.com/news/world")
                        }
                        if (stories.stories.isEmpty()) {
                            HomeCard(cardColor, onClick = { viewModel.loadStories(language, force = true) }) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(if (stories.loading) R.string.home_stories_loading else R.string.home_stories_unavailable),
                                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    if (!stories.loading) Text(stringResource(R.string.extensions_retry), color = colors.primary)
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(stories.stories, key = { it.url }) { story ->
                                    HomeCard(cardColor, Modifier.widthIn(max = 300.dp).width(280.dp), onClick = { onOpenUrl(story.url) }) {
                                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(insetColor),
                                                contentAlignment = Alignment.Center) {
                                                Icon(painterResource(R.drawable.icons_internet), null, Modifier.size(24.dp), tint = colors.onSurfaceVariant)
                                                if (story.imageUrl != null) AsyncImage(story.imageUrl, null,
                                                    Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                            }
                                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(story.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium)
                                                Text(story.publisher, color = colors.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!preferences.hideHomeCustomizeShortcut) item(key = "customize") {
                TextButton(onClick = { navigateTo(NavDestination.HomepageSettings) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (hasWallpaper) Color.White else colors.primary)) {
                    Icon(painterResource(R.drawable.icons_settings), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.menu_customize_homepage),
                        style = MaterialTheme.typography.labelLarge.copy(shadow = if (hasWallpaper) wallpaperTextShadow else null))
                }
            }
        }
        homeMenuPosition?.let { position ->
            Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { homeMenuPosition = null },
                    modifier = Modifier.testTag("home_context_menu")) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_customize_homepage)) },
                        leadingIcon = { Icon(painterResource(R.drawable.icons_settings), null) },
                        modifier = Modifier.testTag("home_context_customize"), onClick = {
                            homeMenuPosition = null; navigateTo(NavDestination.HomepageSettings)
                        })
                    DropdownMenuItem(text = { Text(stringResource(R.string.home_add_shortcut)) },
                        leadingIcon = { Icon(painterResource(R.drawable.icons_add_tab), null) },
                        modifier = Modifier.testTag("home_context_add_shortcut"), onClick = {
                            homeMenuPosition = null; shortcutState.pickerParentId = ""
                        })
                    DropdownMenuItem(text = { Text(stringResource(R.string.home_add_folder)) },
                        leadingIcon = { Icon(painterResource(R.drawable.icons_folder_add), null) },
                        modifier = Modifier.testTag("home_context_add_folder"), onClick = {
                            homeMenuPosition = null; shortcutState.folderEditorId = null; shortcutState.showFolderEditor = true
                        })
                }
            }
        }
    }

    if (preferences.homeShortcutsInitialized) {
        HomeShortcutDialogs(shortcutState, shortcuts, viewModel, dataReady, onOpenUrl)
    }
}

@Composable
private fun HomeSectionHeading(title: Int, action: Int? = null, onAdd: (() -> Unit)? = null,
    wallpaper: Boolean = false, onAction: () -> Unit = {}) {
    val contentColor = if (wallpaper) Color.White else LocalContentColor.current
    val shadow = if (wallpaper) wallpaperTextShadow else null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(shadow = shadow),
            fontWeight = FontWeight.SemiBold, color = contentColor)
        if (action != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(start = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = if (wallpaper) Color.White else MaterialTheme.colorScheme.primary)) {
                Text(stringResource(action), style = MaterialTheme.typography.labelMedium.copy(shadow = shadow))
                Text("›", Modifier.padding(start = 4.dp), fontSize = 18.sp, style = LocalTextStyle.current.copy(shadow = shadow))
            }
        }
        if (onAdd != null) IconButton(onClick = onAdd, modifier = Modifier.testTag("home_add_shortcut")) {
            Icon(painterResource(R.drawable.icons_add_tab), stringResource(R.string.home_add_shortcut), Modifier.size(20.dp), tint = contentColor)
        }
    }
}

@Composable
private fun HomeCard(color: Color, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = color, contentColor = MaterialTheme.colorScheme.onSurface, content = content)
}

@Composable
internal fun HomeFavicon(url: String?, viewModel: NativeHomeViewModel, enabled: Boolean, modifier: Modifier = Modifier) {
    val icon = if (enabled && url != null) {
        remember(viewModel, url) { viewModel.favicon(url) }.collectAsStateWithLifecycle(initialValue = null).value
    } else null
    if (icon != null) {
        Image(icon.bitmap.asImageBitmap(), null, modifier.testTag("home_favicon:${homeIconOrigin(url!!)}"))
    } else if (url == null) {
        Icon(painterResource(R.drawable.icons_internet), null, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Text(url.toCleanHost().firstOrNull()?.uppercase().orEmpty(), fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun HomeShortcutEditor(site: HomeShortcut?, busy: Boolean = false, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable(site?.id) { mutableStateOf(site?.title.orEmpty()) }
    var url by rememberSaveable(site?.id) { mutableStateOf(site?.url.orEmpty()) }
    var attempted by rememberSaveable(site?.id) { mutableStateOf(false) }
    val valid = title.isNotBlank() && publicHomeUrl(url) != null
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (site == null) R.string.home_add_shortcut else R.string.home_edit_shortcut)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.home_shortcut_title)) },
                    singleLine = true, modifier = Modifier.testTag("home_shortcut_title"))
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.home_shortcut_url)) }, singleLine = true,
                    isError = attempted && !valid, modifier = Modifier.testTag("home_shortcut_url"))
                if (attempted && !valid) Text(stringResource(R.string.home_shortcut_invalid), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(enabled = !busy, onClick = { attempted = true; if (valid) onSave(title, url) }, modifier = Modifier.testTag("home_shortcut_save")) {
                Text(stringResource(R.string.browser_save_tab_group))
            }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adblock_cancel)) } })
}
