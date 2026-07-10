package org.midorinext.android.ui.browser.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.midorinext.android.R
import org.midorinext.android.adblock.AdBlockerState
import org.midorinext.android.preferences.app.AppPreferences
import java.util.Calendar
import java.util.TimeZone

private const val HomePhotoUrl =
    "https://images.unsplash.com/photo-1548679847-1d4ff48016c7?auto=format&fit=crop&crop=entropy&w=1440&h=2560&q=86"

private data class HomeShortcut(
    val title: String,
    val url: String,
    @DrawableRes val icon: Int
)

@Composable
fun HomeScreen(
    adBlockerState: AdBlockerState,
    preferences: AppPreferences,
    tabCount: Int,
    onSearch: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenHome: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchFocusRequester = remember { FocusRequester() }
    val pinnedShortcuts = remember {
        listOf(
            HomeShortcut("AstianGO", "https://astiango.com", R.drawable.icons_search),
            HomeShortcut("Astian", "https://astian.org", R.drawable.icons_internet),
            HomeShortcut("Privacy", "https://astian.org/privacy", R.drawable.icons_lock),
            HomeShortcut("Midori", "https://astian.org/midori-browser", R.drawable.icons_tab_smiley)
        )
    }
    val carouselShortcuts = remember {
        listOf(
            HomeShortcut("News", "https://news.google.com", R.drawable.icons_internet),
            HomeShortcut("YouTube", "https://youtube.com", R.drawable.icons_open),
            HomeShortcut("GitHub", "https://github.com", R.drawable.icons_laptop),
            HomeShortcut("Wikipedia", "https://wikipedia.org", R.drawable.icons_information),
            HomeShortcut("Stack Overflow", "https://stackoverflow.com", R.drawable.icons_laptop),
            HomeShortcut("Maps", "https://www.openstreetmap.org", R.drawable.icons_internet)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (preferences.homepageBackgroundPhotoEnabled) {
            AsyncImage(
                model = HomePhotoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB0000000),
                        0.45f to Color(0x66000000),
                        1f to Color(0xE6000000)
                    )
                )
        )

        if (preferences.homepageWeatherEnabled) {
            HomeWeatherChip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 28.dp, end = 16.dp, bottom = 148.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )

            Spacer(Modifier.height(26.dp))
            if (preferences.homepagePrivacyStatsEnabled) {
                PrivacyStatsCard(adBlockerState)
            }

            if (preferences.homepageShortcutsEnabled) {
                Spacer(Modifier.height(22.dp))
                SectionTitle(text = stringResource(R.string.home_pinned_shortcuts))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pinnedShortcuts.forEach { shortcut ->
                        PinnedShortcut(
                            shortcut = shortcut,
                            onClick = { onOpenUrl(shortcut.url) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionTitle(text = stringResource(R.string.home_quick_access))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(carouselShortcuts) { shortcut ->
                        CarouselShortcut(shortcut = shortcut, onClick = { onOpenUrl(shortcut.url) })
                    }
                }
            }
        }

        if (preferences.homepageBackgroundPhotoEnabled) {
            PhotoCreditChip(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 136.dp)
            )
        }

        HomeSearchBar(
            onSearch = onSearch,
            focusRequester = searchFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 76.dp)
        )

        HomeBottomNavigation(
            tabCount = tabCount,
            onHome = onOpenHome,
            onBookmarks = onOpenBookmarks,
            onSearch = { searchFocusRequester.requestFocus() },
            onTabs = onOpenTabs,
            onSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HomeWeatherChip(modifier: Modifier = Modifier) {
    val weatherLocation = remember {
        TimeZone.getDefault().id
            .substringAfterLast('/')
            .replace('_', ' ')
            .ifBlank { "Local" }
    }
    val weatherStatus = stringResource(localWeatherStatusLabel(), weatherLocation)

    Surface(
        color = Color.White.copy(alpha = 0.17f),
        contentColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 7.dp, end = 12.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.icons_weather_sunny),
                contentDescription = null,
                tint = Color(0xFFFFD36A),
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = stringResource(R.string.home_weather_local),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = weatherStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PhotoCreditChip(modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.30f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.home_photo_credit),
            color = Color.White.copy(alpha = 0.80f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun PrivacyStatsCard(adBlockerState: AdBlockerState) {
    Surface(
        color = Color.White.copy(alpha = 0.13f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.icons_lock),
                    contentDescription = null,
                    tint = Color(0xFF8AF0BE),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.home_privacy_stats),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeStat(
                    value = if (adBlockerState.enabled) stringResource(R.string.home_shield_on) else stringResource(R.string.home_shield_off),
                    label = stringResource(R.string.home_shield),
                    color = Color(0xFF8AF0BE),
                    modifier = Modifier.weight(1f)
                )
                HomeStat(
                    value = adBlockerState.protectedPageCount.toString(),
                    label = stringResource(R.string.home_protected_pages),
                    color = Color(0xFFFFC978),
                    modifier = Modifier.weight(1f)
                )
                HomeStat(
                    value = adBlockerState.protectionLevel.replaceFirstChar { it.uppercaseChar() },
                    label = stringResource(R.string.home_level),
                    color = Color(0xFF9BC6FF),
                    modifier = Modifier.weight(1f)
                )
            }
            if (adBlockerState.hasSnapshot) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeStat(
                        value = adBlockerState.hostname.ifBlank { "-" },
                        label = stringResource(R.string.home_last_site),
                        color = Color(0xFFB6F7D1),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStat(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 1
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PinnedShortcut(
    shortcut: HomeShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        modifier = modifier
            .aspectRatio(0.92f)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ShortcutIcon(shortcut.icon)
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CarouselShortcut(
    shortcut: HomeShortcut,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        modifier = Modifier
            .width(104.dp)
            .height(84.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ShortcutIcon(shortcut.icon, size = 30)
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShortcutIcon(@DrawableRes icon: Int, size: Int = 34) {
    Box(
        modifier = Modifier
            .size((size + 16).dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
private fun HomeSearchBar(
    onSearch: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var value by remember { mutableStateOf("") }

    Surface(
        color = Color(0xFFF7F3EA),
        contentColor = Color(0xFF141414),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(50.dp)
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE6DDCD)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.icons_search),
                    contentDescription = null,
                    tint = Color(0xFF141414),
                    modifier = Modifier.size(20.dp)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF141414),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val query = value.trim()
                    if (query.isNotEmpty()) {
                        onSearch(query)
                    }
                }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = stringResource(R.string.browser_toolbar_hint),
                                color = Color(0x99141414),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            if (value.isNotBlank()) {
                Icon(
                    painter = painterResource(R.drawable.icons_close),
                    contentDescription = stringResource(R.string.home_clear_search),
                    tint = Color(0xFF4E4A43),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { value = "" }
                        .padding(9.dp)
                )
            }
            Icon(
                painter = painterResource(
                    if (value.isBlank()) R.drawable.icons_mic else R.drawable.icons_arrow_forward
                ),
                contentDescription = stringResource(
                    if (value.isBlank()) R.string.home_voice_search else R.string.home_nav_search
                ),
                tint = Color(0xFF141414),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        val query = value.trim()
                        if (query.isNotEmpty()) {
                            onSearch(query)
                        } else {
                            focusRequester.requestFocus()
                        }
                    }
                    .padding(9.dp)
            )
        }
    }
}

@Composable
private fun HomeBottomNavigation(
    tabCount: Int,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onSearch: () -> Unit,
    onTabs: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEE151515),
        contentColor = Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomNavItem(R.drawable.icons_internet, stringResource(R.string.home_nav_home), onHome)
            BottomNavItem(R.drawable.icons_bookmark, stringResource(R.string.home_nav_bookmarks), onBookmarks)
            BottomNavItem(R.drawable.icons_search, stringResource(R.string.home_nav_search), onSearch, selected = true)
            BottomNavItem(R.drawable.icons_tab_smiley, tabCount.coerceAtLeast(0).toString(), onTabs)
            BottomNavItem(R.drawable.icons_more_vertical, stringResource(R.string.home_nav_more), onSettings)
        }
    }
}

@Composable
private fun BottomNavItem(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (selected) Color(0xFF8AF0BE) else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@StringRes
private fun localWeatherStatusLabel(): Int {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.home_weather_morning
        in 12..17 -> R.string.home_weather_afternoon
        in 18..21 -> R.string.home_weather_evening
        else -> R.string.home_weather_night
    }
}
