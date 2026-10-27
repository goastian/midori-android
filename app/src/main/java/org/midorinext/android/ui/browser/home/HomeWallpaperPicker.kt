package org.midorinext.android.ui.browser.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.IOException
import kotlinx.coroutines.launch
import org.midorinext.android.R
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.HomeWallpaperCategory
import org.midorinext.android.ui.theme.MidoriDialogSystemBars

@Composable
internal fun HomeWallpaperImage(
    wallpaper: HomeWallpaper,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {},
    thumbnail: Boolean = false,
) {
    val image = if (thumbnail) wallpaper.thumbnail else wallpaper.image
    if (image == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surface))
    } else {
        val context = LocalContext.current
        val request = remember(context, image) {
            ImageRequest.Builder(context).data(image).crossfade(false).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onSuccess = { onReady(true) },
            onError = { onReady(false) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWallpaperPicker(
    current: HomeWallpaper,
    onDismiss: () -> Unit,
    onApply: suspend (HomeWallpaper) -> Unit,
) {
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<HomeWallpaperCategory?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val preview = previewId?.let { HomeWallpaper.fromId(it) }
    var previewReady by remember(preview) { mutableStateOf(preview?.image == null) }
    var previewFailed by remember(preview) { mutableStateOf(false) }
    val close = { if (!saving) onDismiss() }
    val visibleWallpapers = remember(category) {
        HomeWallpaper.entries.filter { category == null || it.category == category }
    }
    val galleryState = key(category) {
        rememberLazyGridState(initialFirstVisibleItemIndex = visibleWallpapers.indexOf(current).coerceAtLeast(0))
    }
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("home_wallpaper_picker"),
    ) {
        MidoriDialogSystemBars()
        BackHandler(enabled = preview != null && !saving) { previewId = null }
        Column(
            Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f)
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (preview != null) {
                    IconButton(onClick = { previewId = null; saveFailed = false }, enabled = !saving,
                        modifier = Modifier.testTag("home_wallpaper_back")) {
                        Icon(painterResource(R.drawable.icons_arrow_backward), stringResource(R.string.home_wallpaper_back))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (preview == null) R.string.home_wallpaper_title else preview.title),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (preview == null) R.string.home_wallpaper_description else R.string.home_wallpaper_preview),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = close, enabled = !saving) {
                    Icon(painterResource(R.drawable.icons_close), stringResource(R.string.cancel))
                }
            }
            if (preview == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { category = null },
                        label = { Text(stringResource(R.string.home_wallpaper_all)) },
                        modifier = Modifier.testTag("home_wallpaper_filter:all"))
                    HomeWallpaperCategory.entries.forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option },
                            label = { Text(stringResource(option.title)) },
                            modifier = Modifier.testTag("home_wallpaper_filter:${option.name.lowercase()}"))
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    state = galleryState,
                    modifier = Modifier.weight(1f, fill = false).testTag("home_wallpaper_gallery"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(visibleWallpapers, key = { it.id }) { wallpaper ->
                        WallpaperTile(wallpaper, wallpaper == current) { previewId = wallpaper.id }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .testTag(if (previewReady && !previewFailed) "home_wallpaper_preview:${preview.id}" else "home_wallpaper_loading_preview:${preview.id}")) {
                    HomeWallpaperImage(preview, Modifier.fillMaxSize(), onReady = {
                        previewReady = true
                        previewFailed = !it
                    })
                    if (!previewReady) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    if (previewFailed) {
                        Text(stringResource(R.string.home_wallpaper_load_error), Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (preview == HomeWallpaper.NONE) {
                        Icon(painterResource(R.drawable.icons_wallpaper), null,
                            Modifier.size(48.dp).align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                preview.sourceUrl?.let { source ->
                    TextButton(onClick = { uriHandler.openUri(source) },
                        modifier = Modifier.fillMaxWidth().testTag("home_wallpaper_credit")) {
                        Text("${preview.photographer} · Unsplash", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (saveFailed) {
                    Text(stringResource(R.string.home_wallpaper_save_error), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            saveFailed = false
                            try {
                                onApply(preview)
                                onDismiss()
                            } catch (_: IOException) {
                                saveFailed = true
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && previewReady && !previewFailed,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 52.dp)
                        .testTag("home_wallpaper_apply"),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(R.string.home_wallpaper_apply)) }
            }
        }
    }
}

@Composable
private fun WallpaperTile(wallpaper: HomeWallpaper, isSelected: Boolean, onClick: () -> Unit) {
    val title = stringResource(wallpaper.title)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(if (isSelected) 3.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).testTag("home_wallpaper_option:${wallpaper.id}")
                .semantics { selected = isSelected; contentDescription = title },
        ) {
            Box {
                HomeWallpaperImage(wallpaper, Modifier.fillMaxSize(), thumbnail = true)
                if (wallpaper == HomeWallpaper.NONE) {
                    Icon(painterResource(R.drawable.icons_wallpaper), null, Modifier.size(32.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isSelected) {
                    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(8.dp).align(Alignment.BottomEnd)) {
                        Icon(painterResource(R.drawable.icons_check), stringResource(R.string.home_wallpaper_selected),
                            Modifier.padding(4.dp).size(18.dp))
                    }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.labelMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
