/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.midorinext.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCustomizationSheet(
    currentCustomization: HomeCustomization,
    onDismiss: () -> Unit,
    onWallpaperSelected: (String) -> Unit,
    onCustomColorSelected: (Int) -> Unit,
    onShowSpeedDialsChanged: (Boolean) -> Unit,
    onShowSuggestedSitesChanged: (Boolean) -> Unit,
    onSpeedDialSizeChanged: (SpeedDialSize) -> Unit,
    onShowCustomizeButtonChanged: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWallpaper by remember { mutableStateOf(currentCustomization.wallpaperName) }
    var showSpeedDials by remember { mutableStateOf(currentCustomization.showSpeedDials) }
    var showSuggested by remember { mutableStateOf(currentCustomization.showSuggestedSites) }
    var speedDialSize by remember { mutableStateOf(currentCustomization.speedDialSize) }
    var showCustomizeBtn by remember { mutableStateOf(currentCustomization.showCustomizeButton) }
    var hasCustomColor by remember { mutableStateOf(currentCustomization.customColor != null) }
    var customColorValue by remember { mutableIntStateOf(currentCustomization.customColor ?: 0xFF04A469.toInt()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_customize),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_customize_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- WALLPAPER SECTION ---
            SectionHeader(
                iconRes = R.drawable.ic_wallpaper,
                title = stringResource(R.string.home_customize_wallpaper),
                subtitle = stringResource(R.string.home_customize_wallpaper_desc),
            )

            Spacer(modifier = Modifier.height(12.dp))

            WallpaperSelector(
                selected = if (hasCustomColor) HomeCustomizationStorage.WALLPAPER_NONE else selectedWallpaper,
                onSelected = { wallpaper ->
                    selectedWallpaper = wallpaper
                    hasCustomColor = false
                    onWallpaperSelected(wallpaper)
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- CUSTOM COLOR SECTION ---
            SectionHeader(
                iconRes = R.drawable.ic_color_palette,
                title = stringResource(R.string.home_customize_color),
                subtitle = stringResource(R.string.home_customize_color_desc),
            )

            Spacer(modifier = Modifier.height(12.dp))

            ColorSelector(
                selectedColor = if (hasCustomColor) customColorValue else null,
                onColorSelected = { color ->
                    customColorValue = color
                    hasCustomColor = true
                    selectedWallpaper = HomeCustomizationStorage.WALLPAPER_NONE
                    onCustomColorSelected(color)
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(16.dp))

            // --- FEATURES SECTION ---
            Text(
                text = stringResource(R.string.home_customize_features),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Speed Dials toggle
            FeatureToggle(
                iconRes = R.drawable.ic_grid_view,
                title = stringResource(R.string.home_customize_speed_dials),
                subtitle = stringResource(R.string.home_customize_speed_dials_desc),
                checked = showSpeedDials,
                onCheckedChange = {
                    showSpeedDials = it
                    onShowSpeedDialsChanged(it)
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Suggested Sites toggle
            FeatureToggle(
                iconRes = R.drawable.ic_suggested_sites,
                title = stringResource(R.string.home_customize_suggested_sites),
                subtitle = stringResource(R.string.home_customize_suggested_sites_desc),
                checked = showSuggested,
                onCheckedChange = {
                    showSuggested = it
                    onShowSuggestedSitesChanged(it)
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Customization button toggle
            FeatureToggle(
                iconRes = R.drawable.ic_customize,
                title = stringResource(R.string.home_customize_shortcut),
                subtitle = stringResource(R.string.home_customize_shortcut_desc),
                checked = showCustomizeBtn,
                onCheckedChange = {
                    showCustomizeBtn = it
                    onShowCustomizeButtonChanged(it)
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(16.dp))

            // --- SPEED DIAL SIZE SECTION ---
            SectionHeader(
                iconRes = R.drawable.ic_grid_view,
                title = stringResource(R.string.home_customize_dial_size),
                subtitle = stringResource(R.string.home_customize_dial_size_desc),
            )

            Spacer(modifier = Modifier.height(12.dp))

            SpeedDialSizeSelector(
                selected = speedDialSize,
                onSelected = {
                    speedDialSize = it
                    onSpeedDialSizeChanged(it)
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    iconRes: Int,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureToggle(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

// --- Wallpaper data ---
data class WallpaperGradient(
    val name: String,
    val colors: List<Color>,
)

val wallpaperGradients = listOf(
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_NONE,
        listOf(Color(0xFFF5FAF7), Color(0xFFEBF4EF)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_OCEAN,
        listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_SUNSET,
        listOf(Color(0xFFFA709A), Color(0xFFFEE140)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_FOREST,
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_AURORA,
        listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_MIDNIGHT,
        listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_SAKURA,
        listOf(Color(0xFFE8B4BC), Color(0xFFF5D5CB), Color(0xFFF9E9DD)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_DESERT,
        listOf(Color(0xFFEB5757), Color(0xFFF2994A), Color(0xFFF2C94C)),
    ),
    WallpaperGradient(
        HomeCustomizationStorage.WALLPAPER_GRADIENT_ARCTIC,
        listOf(Color(0xFFE0EAFC), Color(0xFFCFDEF3)),
    ),
)

fun getWallpaperBrush(wallpaperName: String): Brush? {
    val gradient = wallpaperGradients.find { it.name == wallpaperName }
        ?: return null
    if (gradient.name == HomeCustomizationStorage.WALLPAPER_NONE) return null
    return Brush.linearGradient(
        colors = gradient.colors,
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
}

@Composable
private fun WallpaperSelector(
    selected: String,
    onSelected: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        wallpaperGradients.forEach { gradient ->
            val isSelected = gradient.name == selected
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(14.dp),
                            )
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp),
                            )
                        },
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = gradient.colors,
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelected(gradient.name) },
                contentAlignment = Alignment.Center,
            ) {
                if (gradient.name == HomeCustomizationStorage.WALLPAPER_NONE) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_circle),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isSelected && gradient.name != HomeCustomizationStorage.WALLPAPER_NONE) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

// --- Preset colors ---
private val presetColors = listOf(
    0xFF04A469.toInt(), // Midori Green
    0xFF667EEA.toInt(), // Indigo
    0xFFEB5757.toInt(), // Red
    0xFFF2994A.toInt(), // Orange
    0xFFF2C94C.toInt(), // Yellow
    0xFF27AE60.toInt(), // Green
    0xFF2D9CDB.toInt(), // Blue
    0xFF9B51E0.toInt(), // Purple
    0xFFFA709A.toInt(), // Pink
    0xFF1A1A2E.toInt(), // Dark Navy
    0xFF2C3E50.toInt(), // Dark Gray Blue
    0xFF34495E.toInt(), // Wet Asphalt
    0xFF8E44AD.toInt(), // Wisteria
    0xFF16A085.toInt(), // Green Sea
    0xFFD35400.toInt(), // Pumpkin
    0xFFC0392B.toInt(), // Pomegranate
)

@Composable
private fun ColorSelector(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        presetColors.forEach { color ->
            val isSelected = selectedColor == color
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                3.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape,
                            )
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                        },
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    drawCircle(color = Color(color))
                }
                if (isSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialSizeSelector(
    selected: SpeedDialSize,
    onSelected: (SpeedDialSize) -> Unit,
) {
    val sizes = listOf(
        SpeedDialSize.SMALL to R.string.home_customize_size_small,
        SpeedDialSize.MEDIUM to R.string.home_customize_size_medium,
        SpeedDialSize.LARGE to R.string.home_customize_size_large,
        SpeedDialSize.LIST to R.string.home_customize_size_list,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        sizes.forEach { (size, labelRes) ->
            val isSelected = size == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                )
                        },
                    )
                    .clickable { onSelected(size) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
