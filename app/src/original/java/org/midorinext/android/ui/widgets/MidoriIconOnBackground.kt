package org.midorinext.android.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun MidoriIconOnBackground(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    bgColor: Color = MaterialTheme.colorScheme.tertiary
) {
    GenericMidoriIconOnBackground(shape = shape, modifier = modifier, color = color, bgColor = bgColor)
}

@Preview
@Composable
fun MidoriIconOnBackgroundPreview() {
    MidoriIconOnBackground(shape = CircleShape, modifier = Modifier.size(32.dp))
}