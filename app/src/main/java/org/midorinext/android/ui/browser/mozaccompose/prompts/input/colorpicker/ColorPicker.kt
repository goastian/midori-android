package org.midorinext.android.ui.browser.mozaccompose.prompts.input.colorpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.midorinext.android.R

@Composable
fun ColorPicker(
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
    colorPickerState: ColorPickerState = rememberColorPickerState(),
) {
    Dialog(onDismissRequest = {}) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SatValPanel(
                colorPickerState = colorPickerState,
                modifier = Modifier.size(300.dp)
            )
            HueBar(
                colorPickerState = colorPickerState,
                modifier = Modifier
                    .width(300.dp)
                    .height(40.dp)
                    .fillMaxWidth()
            )
            Box(modifier = Modifier.width(300.dp)) {
                Box(modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .background(colorPickerState.color)
                    .clip(RoundedCornerShape(8.dp))
                )
                Text(text = colorPickerState.string, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Button(
                    onClick = { onConfirm(colorPickerState.color) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        }
    }
}