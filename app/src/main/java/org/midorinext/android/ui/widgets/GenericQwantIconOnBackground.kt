package org.midorinext.android.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.R

@Composable
fun GenericMidoriIconOnBackground(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color,
    bgColor: Color
) {
    Box(modifier = modifier
        .background(bgColor, shape)
    ) {
        Icon(
            painterResource(id = R.drawable.qwant_logo),
            contentDescription = "Midori",
            tint = color,
            modifier = Modifier
                .padding(bottom = 6.dp, start = 6.dp, top = 4.dp, end = 4.dp)
                .align(Alignment.Center)
        )
    }
}
