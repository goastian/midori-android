package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import org.midorinext.android.R

@Composable
internal fun <T> AutofillPicker(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    detail: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    BackHandler(onBack = onDismiss)
    Popup(
        popupPositionProvider = AutofillPickerPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .padding(12.dp).testTag("autofill_picker"),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    itemsIndexed(options) { index, option ->
                        Column(
                            Modifier.fillMaxWidth().testTag("autofill_option_$index")
                                .clickable(enabled = enabled) { onSelect(option) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            detail(option).takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private object AutofillPickerPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        (windowSize.height - popupContentSize.height).coerceAtLeast(0),
    )
}
