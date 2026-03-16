/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import org.midorinext.android.R
import org.midorinext.android.theme.MidoriTheme
import java.util.Calendar
import kotlin.math.sin

// --- Flat Material 3 Palette ---
// Light theme
private val LightBg = Color(0xFFF7F9F8)
private val LightSurface = Color(0xFFEDF2EE)
private val LightSearchBarBg = Color(0xFFE8EDE9)
private val LightTextPrimary = Color(0xFF1A1C1B)
private val LightTextSecondary = Color(0xFF43524A)
private val LightTextTertiary = Color(0xFF73796F)
private val LightShortcutBg = Color(0xFFE0E8E2)

// Dark theme
private val DarkBg = Color(0xFF111418)
private val DarkSurface = Color(0xFF1A1F25)
private val DarkSearchBarBg = Color(0xFF1E252D)
private val DarkTextPrimary = Color(0xFFE3E3E0)
private val DarkTextSecondary = Color(0xFF8B949E)
private val DarkTextTertiary = Color(0xFF5A6366)
private val DarkShortcutBg = Color(0xFF232B33)

// Brand colors
private val MidoriGreenPrimary = Color(0xFF04A469)
private val MidoriGreenBright = Color(0xFF06E290)
private val MidoriGold = Color(0xFFD4A93C)

// Dynamic gradient colors based on time of day
@Composable
private fun dynamicGradient(): Brush {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val isDark = isSystemInDarkTheme()
    val (startColor, endColor) = when {
        hour in 5..8 -> { // Morning – warm green/gold
            if (isDark) Color(0xFF0A2018) to Color(0xFF1A2B1F)
            else Color(0xFFF0F7F2) to Color(0xFFF5F0E0)
        }
        hour in 9..12 -> { // Mid-morning – bright green
            if (isDark) Color(0xFF0C1A14) to Color(0xFF112218)
            else Color(0xFFF2F9F5) to Color(0xFFE8F5EC)
        }
        hour in 13..16 -> { // Afternoon – neutral green
            if (isDark) Color(0xFF0E1612) to Color(0xFF14201B)
            else Color(0xFFF5F9F7) to Color(0xFFEDF4EF)
        }
        hour in 17..20 -> { // Evening – warm amber/green
            if (isDark) Color(0xFF14180E) to Color(0xFF1A1C10)
            else Color(0xFFF8F5EE) to Color(0xFFF0EDE0)
        }
        else -> { // Night – deep blue/green
            if (isDark) Color(0xFF0A0E14) to Color(0xFF101820)
            else Color(0xFFF0F4FA) to Color(0xFFE8EEF6)
        }
    }
    return Brush.verticalGradient(listOf(startColor, endColor))
}

// Floating particles data
private data class Particle(
    val xFraction: Float,
    val yFraction: Float,
    val radius: Float,
    val speed: Float,
    val color: Color,
)

private val defaultParticles = listOf(
    Particle(0.15f, 0.2f, 3f, 1.0f, MidoriGreenPrimary.copy(alpha = 0.15f)),
    Particle(0.75f, 0.1f, 2.5f, 0.8f, MidoriGold.copy(alpha = 0.12f)),
    Particle(0.4f, 0.5f, 2f, 1.2f, MidoriGreenBright.copy(alpha = 0.1f)),
    Particle(0.9f, 0.35f, 3.5f, 0.6f, MidoriGreenPrimary.copy(alpha = 0.1f)),
    Particle(0.25f, 0.7f, 2f, 1.4f, MidoriGold.copy(alpha = 0.15f)),
    Particle(0.6f, 0.85f, 2.5f, 0.9f, MidoriGreenBright.copy(alpha = 0.08f)),
    Particle(0.85f, 0.6f, 1.8f, 1.1f, MidoriGold.copy(alpha = 0.1f)),
    Particle(0.1f, 0.9f, 3f, 0.7f, MidoriGreenPrimary.copy(alpha = 0.12f)),
    Particle(0.5f, 0.3f, 2.2f, 1.3f, MidoriGreenBright.copy(alpha = 0.1f)),
    Particle(0.3f, 0.45f, 1.5f, 1.0f, MidoriGold.copy(alpha = 0.08f)),
)

@Composable
private fun FloatingParticles() {
    val transition = rememberInfiniteTransition(label = "particles")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particleTime",
    )
    Canvas(modifier = Modifier.fillMaxSize().alpha(0.8f)) {
        defaultParticles.forEach { p ->
            val offsetX = sin((time * p.speed + p.xFraction * 360).toDouble()).toFloat() * 30f
            val offsetY = sin((time * p.speed * 0.7f + p.yFraction * 360).toDouble()).toFloat() * 25f
            drawCircle(
                color = p.color,
                radius = p.radius.dp.toPx(),
                center = Offset(
                    x = size.width * p.xFraction + offsetX.dp.toPx(),
                    y = size.height * p.yFraction + offsetY.dp.toPx(),
                ),
            )
        }
    }
}

data class TopSite(
    val title: String,
    val url: String,
    val iconRes: Int? = null,
    val initial: String = title.firstOrNull()?.uppercase() ?: "?",
)

data class SuggestedSite(
    val title: String,
    val url: String,
)

data class Shortcut(
    val titleRes: Int,
    val iconRes: Int,
    val action: ShortcutAction,
)

enum class ShortcutAction {
    NEW_PRIVATE_TAB,
    HISTORY,
    BOOKMARKS,
    DOWNLOADS,
}

@Composable
fun HomeScreen(
    topSites: List<TopSite>,
    suggestedSites: List<SuggestedSite>,
    trackersBlocked: Int,
    customization: HomeCustomization,
    onSearchBarClick: () -> Unit,
    onTopSiteClick: (TopSite) -> Unit,
    onSuggestedSiteClick: (SuggestedSite) -> Unit,
    onShortcutClick: (ShortcutAction) -> Unit,
    onCustomizeClick: () -> Unit,
) {
    val greetingRes = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
    }

    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) DarkSurface else LightSurface
    val searchBarBg = if (isDark) DarkSearchBarBg else LightSearchBarBg
    val textPrimary = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val textTertiary = if (isDark) DarkTextTertiary else LightTextTertiary
    val accentColor = if (isDark) MidoriGreenBright else MidoriGreenPrimary
    val searchBarHint = if (isDark) Color(0xFF5A6E63) else Color(0xFF8A9E93)

    // Determine background
    val wallpaperBrush = getWallpaperBrush(customization.wallpaperName)
    val customBgColor = customization.customColor?.let { Color(it) }
    val hasWallpaperOrColor = wallpaperBrush != null || customBgColor != null

    val scrollState = rememberScrollState()

    // Background layer
    Box(modifier = Modifier.fillMaxSize()) {
        // Dynamic gradient background (changes with time of day)
        when {
            wallpaperBrush != null -> {
                Box(modifier = Modifier.fillMaxSize().background(wallpaperBrush))
            }
            customBgColor != null -> {
                Box(modifier = Modifier.fillMaxSize().background(customBgColor))
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(dynamicGradient()))
            }
        }

        // Floating particles overlay
        FloatingParticles()

        // Content on top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 40.dp, bottom = 16.dp),
        ) {
            val contentTextPrimary = if (hasWallpaperOrColor) Color.White else textPrimary
            val contentTextSecondary = if (hasWallpaperOrColor) Color.White.copy(alpha = 0.8f) else textSecondary
            val contentTextTertiary = if (hasWallpaperOrColor) Color.White.copy(alpha = 0.6f) else textTertiary
            val contentAccent = if (hasWallpaperOrColor) Color.White else accentColor
            val contentSearchBarBg = if (hasWallpaperOrColor) Color.White.copy(alpha = 0.15f) else searchBarBg
            val contentSurfaceColor = if (hasWallpaperOrColor) Color.White.copy(alpha = 0.1f) else surfaceColor
            val contentSearchHint = if (hasWallpaperOrColor) Color.White.copy(alpha = 0.5f) else searchBarHint

            // Logo + branding row + customize button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "Midori",
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Midori",
                        color = contentTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(greetingRes),
                        color = contentAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                // Customize button — flat circle, no border
                if (customization.showCustomizeButton) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(contentSurfaceColor)
                            .clickable(onClick = onCustomizeClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_customize),
                            contentDescription = stringResource(R.string.home_customize_title),
                            modifier = Modifier.size(22.dp),
                            tint = contentAccent,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Search bar — flat, no border, no shadow (Material 3 Flat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(contentSearchBarBg)
                    .clickable(onClick = onSearchBarClick)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(contentAccent),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.home_search_hint),
                    color = contentSearchHint,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    alpha = 0.25f,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Privacy summary card — flat, no border, no shadow
            if (trackersBlocked > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(contentSurfaceColor)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(contentAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_shield),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(contentAccent),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_trackers_blocked, trackersBlocked),
                                color = contentTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.home_trackers_subtitle),
                                color = contentTextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Speed Dials
            if (customization.showSpeedDials && topSites.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.home_top_sites),
                    color = contentTextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )

                when (customization.speedDialSize) {
                    SpeedDialSize.LIST -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            topSites.forEach { site ->
                                TopSiteListItem(
                                    site = site,
                                    onClick = { onTopSiteClick(site) },
                                    accentColor = contentAccent,
                                    cardBg = contentSurfaceColor,
                                    textColor = contentTextPrimary,
                                    subtitleColor = contentTextSecondary,
                                )
                            }
                        }
                    }
                    else -> {
                        val (columns, iconSize, fontSize, itemHeight) = when (customization.speedDialSize) {
                            SpeedDialSize.SMALL -> GridConfig(5, 42.dp, 10.sp, 80.dp)
                            SpeedDialSize.LARGE -> GridConfig(3, 64.dp, 13.sp, 110.dp)
                            else -> GridConfig(4, 52.dp, 11.sp, 95.dp)
                        }
                        val gridHeight = ((topSites.size + columns - 1) / columns) * (itemHeight.value + 18f)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridHeight.dp),
                            userScrollEnabled = false,
                        ) {
                            items(topSites) { site ->
                                TopSiteGridItem(
                                    site = site,
                                    onClick = { onTopSiteClick(site) },
                                    accentColor = contentAccent,
                                    cardBg = contentSurfaceColor,
                                    textColor = contentTextSecondary,
                                    iconSize = iconSize,
                                    fontSize = fontSize,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Suggested Sites
            if (customization.showSuggestedSites && suggestedSites.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.home_suggested_sites),
                    color = contentTextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    suggestedSites.take(6).forEach { site ->
                        SuggestedSiteItem(
                            site = site,
                            onClick = { onSuggestedSiteClick(site) },
                            accentColor = contentAccent,
                            cardBg = contentSurfaceColor,
                            textColor = contentTextPrimary,
                            subtitleColor = contentTextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom branding
            Text(
                text = "Powered by Astian",
                color = contentTextTertiary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

// Helper data class for grid configuration
private data class GridConfig(
    val columns: Int,
    val iconSize: androidx.compose.ui.unit.Dp,
    val fontSize: androidx.compose.ui.unit.TextUnit,
    val itemHeight: androidx.compose.ui.unit.Dp,
)

@Composable
private fun TopSiteGridItem(
    site: TopSite,
    onClick: () -> Unit,
    accentColor: Color = MidoriGreenPrimary,
    cardBg: Color = LightSurface,
    textColor: Color = LightTextSecondary,
    iconSize: androidx.compose.ui.unit.Dp = 52.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(14.dp))
                .background(cardBg),
            contentAlignment = Alignment.Center,
        ) {
            if (site.iconRes != null) {
                Image(
                    painter = painterResource(id = site.iconRes),
                    contentDescription = site.title,
                    modifier = Modifier.size(iconSize * 0.54f),
                )
            } else {
                Text(
                    text = site.initial,
                    color = accentColor,
                    fontSize = (iconSize.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = site.title,
            color = textColor,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TopSiteListItem(
    site: TopSite,
    onClick: () -> Unit,
    accentColor: Color,
    cardBg: Color,
    textColor: Color,
    subtitleColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (site.iconRes != null) {
                Image(
                    painter = painterResource(id = site.iconRes),
                    contentDescription = site.title,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = site.initial,
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = site.title,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = site.url.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                color = subtitleColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SuggestedSiteItem(
    site: SuggestedSite,
    onClick: () -> Unit,
    accentColor: Color,
    cardBg: Color,
    textColor: Color,
    subtitleColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_suggested_sites),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accentColor,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = site.title.ifEmpty {
                    site.url.removePrefix("https://").removePrefix("http://")
                        .split("/").firstOrNull() ?: site.url
                },
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = site.url.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                color = subtitleColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShortcutItem(
    shortcut: Shortcut,
    onClick: () -> Unit,
    accentColor: Color = MidoriGreenPrimary,
    bgColor: Color = LightShortcutBg,
    textColor: Color = LightTextSecondary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = shortcut.iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(accentColor),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(shortcut.titleRes),
            color = textColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
