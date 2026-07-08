package org.midorinext.android.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Dropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest= onDismissRequest,
        offset = DpOffset(8.dp, 0.dp),
        properties = PopupProperties(
            focusable = focusable,
            usePlatformDefaultWidth = true
        ),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                MaterialTheme.shapes.extraSmall
            )
            .then(modifier)
    ) {
        content()
    }
}