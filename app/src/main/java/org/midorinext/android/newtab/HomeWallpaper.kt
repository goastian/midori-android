package org.midorinext.android.newtab

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.midorinext.android.R

enum class HomeWallpaper(val id: String, @StringRes val title: Int, @DrawableRes val image: Int? = null) {
    NONE("none", R.string.home_wallpaper_none),
    FOREST("forest", R.string.home_wallpaper_forest, R.drawable.home_wallpaper_forest),
    DAWN("dawn", R.string.home_wallpaper_dawn, R.drawable.home_wallpaper_dawn),
    DUNES("dunes", R.string.home_wallpaper_dunes, R.drawable.home_wallpaper_dunes),
    SILK("silk", R.string.home_wallpaper_silk, R.drawable.home_wallpaper_silk);

    companion object {
        fun fromId(id: String): HomeWallpaper = entries.firstOrNull { it.id == id } ?: NONE
    }
}
