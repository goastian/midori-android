package org.midorinext.android.ui.browser.mozaccompose.prompts.input.colorpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

class ColorPickerState(initialColor: Color?) {
    var h by mutableFloatStateOf(0f)
    var s by mutableFloatStateOf(0f)
    var v by mutableFloatStateOf(0f)
    var color by mutableStateOf(Color.hsv(h, s, v))
    var string by mutableStateOf(color.toString())

    init {
        initialColor?.let { updateColor(it) }
    }

    /* fun updateColor(color: String) {
        try {
            val intColor = color.toColorInt()
            updateColor(Color(intColor))
        } catch (_: IllegalArgumentException) {
            updateColor(Color(0))
        }
    } */

    fun updateColor(color: Color) {
        val hsv = floatArrayOf(0f, 0f, 0f)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        h = hsv[0]
        s = hsv[1]
        v = hsv[2]
        updateColorAndStringFromHSV()
    }

    internal fun updateHue(hue: Float) {
        h = hue
        updateColorAndStringFromHSV()
    }

    internal fun updateSatVal(sat: Float, value: Float) {
        s = sat
        v = value
        updateColorAndStringFromHSV()
    }

    private fun updateColorAndStringFromHSV() {
        color = Color.hsv(h, s, v)
        string = "#" + Integer.toHexString(color.toArgb()).removeRange(0..1)
    }
}

@Composable
fun rememberColorPickerState(initialColor: Color? = null): ColorPickerState {
    return remember { ColorPickerState(initialColor) }
}