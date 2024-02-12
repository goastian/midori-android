package org.midorinext.android.theme

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class MidoriThemeColorScheme(
    val id: Long,
    val lightColors: MidoriColors,
    val darkColors: MidoriColors,
    @StyleRes val resourceId: Int,
    val lightPrimaryColor: Color,
    val darkPrimaryColor: Color,
    val brush: Brush? = null,
)
