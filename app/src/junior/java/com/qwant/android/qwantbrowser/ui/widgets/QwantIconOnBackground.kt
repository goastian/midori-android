package org.midorinext.android.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.midorinext.android.ui.theme.JuniorLogoBackground
import org.midorinext.android.ui.theme.JuniorLogoForeground

@Composable
fun MidoriIconOnBackground(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = JuniorLogoForeground,
    bgColor: Color = JuniorLogoBackground,
) {
    GenericMidoriIconOnBackground(shape = shape, modifier = modifier, color = color, bgColor = bgColor)
}

@Preview
@Composable
fun MidoriIconOnBackgroundPreview() {
    MidoriIconOnBackground(shape = CircleShape, modifier = Modifier.size(32.dp))
}