/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home

import android.content.Context
import android.content.SharedPreferences

enum class SpeedDialSize(val key: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    LIST("list");

    companion object {
        fun fromKey(key: String): SpeedDialSize =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

data class HomeCustomization(
    val wallpaperName: String,
    val customColor: Int?,
    val showSpeedDials: Boolean,
    val showSuggestedSites: Boolean,
    val speedDialSize: SpeedDialSize,
    val showCustomizeButton: Boolean,
)

class HomeCustomizationStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_customization", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WALLPAPER = "wallpaper_name"
        private const val KEY_CUSTOM_COLOR = "custom_color"
        private const val KEY_HAS_CUSTOM_COLOR = "has_custom_color"
        private const val KEY_SHOW_SPEED_DIALS = "show_speed_dials"
        private const val KEY_SHOW_SUGGESTED_SITES = "show_suggested_sites"
        private const val KEY_SPEED_DIAL_SIZE = "speed_dial_size"
        private const val KEY_SHOW_CUSTOMIZE_BUTTON = "show_customize_button"

        const val WALLPAPER_NONE = "none"
        const val WALLPAPER_GRADIENT_OCEAN = "gradient_ocean"
        const val WALLPAPER_GRADIENT_SUNSET = "gradient_sunset"
        const val WALLPAPER_GRADIENT_FOREST = "gradient_forest"
        const val WALLPAPER_GRADIENT_AURORA = "gradient_aurora"
        const val WALLPAPER_GRADIENT_MIDNIGHT = "gradient_midnight"
        const val WALLPAPER_GRADIENT_SAKURA = "gradient_sakura"
        const val WALLPAPER_GRADIENT_DESERT = "gradient_desert"
        const val WALLPAPER_GRADIENT_ARCTIC = "gradient_arctic"

        val ALL_WALLPAPERS = listOf(
            WALLPAPER_NONE,
            WALLPAPER_GRADIENT_OCEAN,
            WALLPAPER_GRADIENT_SUNSET,
            WALLPAPER_GRADIENT_FOREST,
            WALLPAPER_GRADIENT_AURORA,
            WALLPAPER_GRADIENT_MIDNIGHT,
            WALLPAPER_GRADIENT_SAKURA,
            WALLPAPER_GRADIENT_DESERT,
            WALLPAPER_GRADIENT_ARCTIC,
        )
    }

    fun load(): HomeCustomization {
        val hasColor = prefs.getBoolean(KEY_HAS_CUSTOM_COLOR, false)
        return HomeCustomization(
            wallpaperName = prefs.getString(KEY_WALLPAPER, WALLPAPER_NONE) ?: WALLPAPER_NONE,
            customColor = if (hasColor) prefs.getInt(KEY_CUSTOM_COLOR, 0) else null,
            showSpeedDials = prefs.getBoolean(KEY_SHOW_SPEED_DIALS, true),
            showSuggestedSites = prefs.getBoolean(KEY_SHOW_SUGGESTED_SITES, false),
            speedDialSize = SpeedDialSize.fromKey(
                prefs.getString(KEY_SPEED_DIAL_SIZE, SpeedDialSize.MEDIUM.key) ?: SpeedDialSize.MEDIUM.key,
            ),
            showCustomizeButton = prefs.getBoolean(KEY_SHOW_CUSTOMIZE_BUTTON, true),
        )
    }

    fun saveWallpaper(name: String) {
        prefs.edit()
            .putString(KEY_WALLPAPER, name)
            .putBoolean(KEY_HAS_CUSTOM_COLOR, false)
            .apply()
    }

    fun saveCustomColor(color: Int) {
        prefs.edit()
            .putInt(KEY_CUSTOM_COLOR, color)
            .putBoolean(KEY_HAS_CUSTOM_COLOR, true)
            .putString(KEY_WALLPAPER, WALLPAPER_NONE)
            .apply()
    }

    fun clearCustomColor() {
        prefs.edit()
            .putBoolean(KEY_HAS_CUSTOM_COLOR, false)
            .apply()
    }

    fun saveShowSpeedDials(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SPEED_DIALS, show).apply()
    }

    fun saveShowSuggestedSites(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SUGGESTED_SITES, show).apply()
    }

    fun saveSpeedDialSize(size: SpeedDialSize) {
        prefs.edit().putString(KEY_SPEED_DIAL_SIZE, size.key).apply()
    }

    fun saveShowCustomizeButton(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CUSTOMIZE_BUTTON, show).apply()
    }
}
