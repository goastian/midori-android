/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MidoriLightColorScheme = lightColorScheme(
    primary = Color(0xFF04A469),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEDE3),
    onPrimaryContainer = Color(0xFF024B30),
    secondary = Color(0xFF036641),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F3EC),
    onSecondaryContainer = Color(0xFF024B30),
    tertiary = Color(0xFF06E290),
    onTertiary = Color(0xFF024B30),
    background = Color(0xFFF5FAF7),
    onBackground = Color(0xFF0A1510),
    surface = Color(0xFFF5FAF7),
    onSurface = Color(0xFF0A1510),
    surfaceVariant = Color(0xFFEBF4EF),
    onSurfaceVariant = Color(0xFF3D5348),
    outline = Color(0xFF5E7A6E),
    outlineVariant = Color(0xFFDDEDE3),
)

private val MidoriDarkColorScheme = darkColorScheme(
    primary = Color(0xFF06E290),
    onPrimary = Color(0xFF024B30),
    primaryContainer = Color(0xFF1A2836),
    onPrimaryContainer = Color(0xFF2EFAAE),
    secondary = Color(0xFF04A469),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF121D2B),
    onSecondaryContainer = Color(0xFF06E290),
    tertiary = Color(0xFF2EFAAE),
    onTertiary = Color(0xFF024B30),
    background = Color(0xFF0D1117),
    onBackground = Color.White,
    surface = Color(0xFF0D1117),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121D2B),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF5A6E63),
    outlineVariant = Color(0xFF1A2836),
)

@Composable
fun MidoriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MidoriDarkColorScheme else MidoriLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MidoriTypography,
        content = content,
    )
}
