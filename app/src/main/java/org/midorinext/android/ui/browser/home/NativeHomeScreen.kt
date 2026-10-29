package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.roundToInt

internal val wallpaperTextShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 2f)

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
    val storedPreferences by viewModel.preferences.collectAsStateWithLifecycle()
    val preferences = storedPreferences ?: return
    LaunchedEffect(tabId) {
        viewModel.initialize()
    }
    val recent = if (!preferences.hideHomeRecentVisits) {
        viewModel.recentVisits.collectAsStateWithLifecycle().value
    } else emptyList()
    val continuation = if (!preferences.hideHomeContinue) {
        viewModel.continuation.collectAsStateWithLifecycle().value
    } else null
    val blocked = if (!preferences.hideHomePrivacy) {
        viewModel.blockedCount.collectAsStateWithLifecycle().value
    } else 0L
    val configuration = LocalConfiguration.current
    val windowSize = LocalWindowInfo.current.containerSize
    val language = configuration.locales[0].language
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(preferences.hideHomeStories, language, lifecycleOwner) {
        if (!preferences.hideHomeStories) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val request = viewModel.loadStories(language)
                try {
                    request.join()
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    viewModel.stopStories(request)
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
    val wallpaperAsset by key(wallpaper) {
        produceState(viewModel.cachedWallpaper(wallpaper), viewModel, wallpaper) {
            if (value == null) value = viewModel.loadWallpaper(wallpaper)
        }
    }
    LaunchedEffect(tabId, wallpaperAsset) {
        if (wallpaperAsset == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        onFirstFrameDrawn(tabId)
    }
    val colors = MaterialTheme.colorScheme
    val solidColor = wallpaper.color?.let { Color(it) }
    val background = solidColor ?: colors.background
    if (wallpaperAsset == null) {
        Box(modifier.fillMaxSize().background(background).testTag("native_home_loading_background:${wallpaper.id}"))
        return
    }
    val wallpaperBitmap = wallpaperAsset?.bitmap
    val hasWallpaper = wallpaperBitmap != null || solidColor != null
    val foreground = if (solidColor != null && solidColor.luminance() > 0.179f) Color.Black else Color.White
    val palette = HomePalette(
        primary = if (hasWallpaper) foreground else colors.onSurface,
        secondary = if (hasWallpaper) foreground.copy(alpha = 0.78f) else colors.onSurfaceVariant,
        panel = if (hasWallpaper) (if (foreground == Color.Black) Color.White else Color.Black).copy(alpha = 0.32f) else colors.surface,
        panelBorder = if (hasWallpaper) BorderStroke(0.5.dp, foreground.copy(alpha = 0.16f)) else null,
        divider = if (hasWallpaper) foreground.copy(alpha = 0.18f) else colors.outlineVariant.copy(alpha = 0.55f),
        tile = if (hasWallpaper) Color.White.copy(alpha = 0.94f) else lerp(colors.surface, colors.surfaceContainer, 0.25f),
        shadow = if (wallpaperBitmap != null) wallpaperTextShadow else null,
    )
    val shortcutWidth = (if (preferences.homeLargeShortcuts) 88 else 72) * configuration.fontScale.coerceAtLeast(1f)
    val shortcutColumns = ((windowSize.width / LocalDensity.current.density - 32) / shortcutWidth)
        .toInt().coerceIn(if (configuration.fontScale > 1.2f) 2 else 3, 6)
    val listState = rememberLazyListState()
    val thumbnailContent = remember(preferences, recent, continuation, blocked, stories, background, configuration, windowSize, wallpaperAsset) {
        listOf(preferences, recent, continuation, blocked, stories, background, configuration, windowSize, wallpaperAsset)
    }
    val thumbnailModifier = rememberNativeHomeThumbnail(
        tabId = tabId,
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
        .testTag("native_home_background:${wallpaper.id}")) {
        if (wallpaperBitmap != null) {
            Image(wallpaperBitmap.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = 0.54f), Color.Black.copy(alpha = 0.54f), Color.Black.copy(alpha = 0.62f),
            ))))
        } else if (solidColor == null) Box(Modifier.matchParentSize().homeGrain())
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("native_home"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                CompositionLocalProvider(LocalContentColor provides palette.primary) {
                    NativeHomeHeader(private = false, onSwitchBrowsingMode = onSwitchBrowsingMode,
                        showLogo = !preferences.hideHomeLogo,
                        onCustomize = if (preferences.hideHomeCustomizeShortcut) null else {
                            { navigateTo(NavDestination.HomepageSettings) }
                        }, wallpaper = hasWallpaper, wallpaperTint = foreground)
                }
            }
            if (!preferences.hideHomeShortcuts) {
                item(key = "shortcuts") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HomeSectionHeading(R.string.home_shortcuts, palette, R.string.home_show_all) {
                            shortcutState.collectionId = ""
                        }
                        HomeShortcutGrid(shortcuts.filter { it.parentId.isEmpty() }, shortcuts, preferences,
                            shortcutColumns, palette, hasWallpaper, viewModel, true, shortcutState, onOpenUrl)
                    }
                }
            }
            if (!preferences.hideHomePrivacy) {
                item(key = "privacy") {
                    val interactions = remember { MutableInteractionSource() }
                    Surface(onClick = { navigateTo(NavDestination.AdBlockSettings) }, shape = RoundedCornerShape(12.dp),
                        color = if (hasWallpaper) palette.panel else Color.Transparent,
                        contentColor = palette.primary, interactionSource = interactions,
                        modifier = Modifier.fillMaxWidth().homePressFeedback(interactions).testTag("native_home_protection")) {
                        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp),
                                color = if (hasWallpaper) foreground.copy(alpha = 0.14f) else colors.primary.copy(alpha = 0.08f)) {
                                Icon(painterResource(mozilla.components.ui.icons.R.drawable.mozac_ic_shield_24), null,
                                    tint = if (hasWallpaper) palette.primary else colors.primary,
                                    modifier = Modifier.padding(7.dp).size(18.dp))
                            }
                            Text(stringResource(R.string.home_blocked, blocked), Modifier.weight(1f),
                                color = palette.secondary,
                                style = MaterialTheme.typography.bodySmall.copy(shadow = palette.shadow))
                            Icon(painterResource(R.drawable.icons_chevron_forward), null, Modifier.size(16.dp),
                                tint = palette.secondary)
                        }
                    }
                }
            }
            if (!preferences.hideHomeContinue && continuation != null) {
                item(key = "continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeSectionHeading(R.string.home_continue, palette)
                        HomeCard(palette, Modifier.testTag("native_home_continue"),
                            onClick = { onSelectTab(continuation.tabId) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainer),
                                    contentAlignment = Alignment.Center) {
                                    HomeFavicon(continuation.url, viewModel, true, Modifier.size(28.dp))
                                    TabThumbnail(continuation.tabId, false, viewModel.thumbnailStorage)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(continuation.title ?: continuation.url.toCleanHost(),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text(continuation.url.toCleanHost(),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.secondary,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(painterResource(R.drawable.icons_chevron_forward), null,
                                    Modifier.size(18.dp), tint = palette.secondary)
                            }
                        }
                    }
                }
            }
            if (!preferences.hideHomeRecentVisits && recent.isNotEmpty()) {
                item(key = "recent") {
                    Column {
                        HomeSectionHeading(R.string.home_recent_visits, palette, R.string.home_show_all) { navigateTo(NavDestination.History) }
                        recent.take(3).forEach { page ->
                            key(page.url) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
                                    .clickable(role = Role.Button) { onOpenUrl(page.url) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp).testTag("native_home_recent"),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.tile),
                                        contentAlignment = Alignment.Center) {
                                        HomeFavicon(page.url, viewModel, true, Modifier.size(20.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = palette.primary,
                                            style = MaterialTheme.typography.bodyMedium.copy(shadow = palette.shadow))
                                        Text(page.url.toCleanHost(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = palette.secondary,
                                            style = MaterialTheme.typography.bodySmall.copy(shadow = palette.shadow))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!preferences.hideHomeStories) {
                item(key = "stories") {
                    Column {
                        HomeSectionHeading(R.string.home_stories, palette, R.string.home_all_stories) {
                            onOpenUrl(if (language == "es") "https://www.bbc.com/mundo" else "https://www.bbc.com/news/world")
                        }
                        if (stories.stories.isEmpty()) {
                            Text(stringResource(if (stories.failed) R.string.home_stories_unavailable else R.string.home_stories_loading),
                                Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                                color = palette.secondary, style = MaterialTheme.typography.bodyMedium.copy(shadow = palette.shadow))
                            if (stories.failed) TextButton(onClick = { viewModel.loadStories(language, force = true) },
                                modifier = Modifier.testTag("home_stories_retry")) {
                                Text(stringResource(R.string.extensions_retry), color = palette.primary)
                            }
                        } else {
                            val visibleStories = stories.stories.take(6)
                            visibleStories.forEachIndexed { index, story ->
                                key(story.url) {
                                    if (index > 0) HorizontalDivider(color = palette.divider)
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button) { onOpenUrl(story.url) }
                                        .padding(horizontal = 4.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(story.title, maxLines = 3, overflow = TextOverflow.Ellipsis, color = palette.primary,
                                                style = (if (index == 0) MaterialTheme.typography.titleMedium
                                                    else MaterialTheme.typography.bodyMedium).copy(shadow = palette.shadow),
                                                fontWeight = FontWeight.Medium)
                                            Text(story.publisher, color = palette.secondary,
                                                style = MaterialTheme.typography.bodySmall.copy(shadow = palette.shadow))
                                        }
                                        if (story.imageUrl != null) {
                                            AsyncImage(story.imageUrl, null,
                                                Modifier.size(width = 104.dp, height = if (index == 0) 88.dp else 72.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.surfaceContainer),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
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
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.secondary)) {
                    Icon(painterResource(R.drawable.icons_settings), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.menu_customize_homepage),
                        style = MaterialTheme.typography.labelLarge.copy(shadow = palette.shadow))
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
        HomeShortcutDialogs(shortcutState, shortcuts, viewModel, true, onOpenUrl)
    }
}

@Immutable
internal class HomePalette(
    val primary: Color,
    val secondary: Color,
    val panel: Color,
    val panelBorder: BorderStroke?,
    val divider: Color,
    val tile: Color,
    val shadow: Shadow?,
)

@Composable
private fun HomeSectionHeading(title: Int, palette: HomePalette, action: Int? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(shadow = palette.shadow),
            fontWeight = FontWeight.SemiBold, color = palette.primary)
        if (action != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.secondary)) {
                Text(stringResource(action), style = MaterialTheme.typography.labelMedium.copy(shadow = palette.shadow))
                Spacer(Modifier.width(2.dp))
                Icon(painterResource(R.drawable.icons_chevron_forward), null, Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun HomeCard(palette: HomePalette, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().homePressFeedback(interactions),
        shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp,
        interactionSource = interactions, color = palette.panel, contentColor = palette.primary,
        border = palette.panelBorder, content = content)
}

@Composable
internal fun HomeFavicon(url: String?, viewModel: NativeHomeViewModel, enabled: Boolean, modifier: Modifier = Modifier) {
    val cached = remember(viewModel, url) { url?.let(viewModel::cachedFavicon) }
    val icon = if (enabled && url != null) {
        remember(viewModel, url) { viewModel.favicon(url) }.collectAsStateWithLifecycle(initialValue = cached).value
    } else cached
    if (icon != null) {
        Image(icon.bitmap.asImageBitmap(), null, modifier.testTag("home_favicon:${homeIconOrigin(url!!)}"))
    } else if (url == null) {
        Icon(painterResource(R.drawable.icons_internet), null, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center) {
            Text(url.toCleanHost().firstOrNull()?.uppercase().orEmpty(), fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
