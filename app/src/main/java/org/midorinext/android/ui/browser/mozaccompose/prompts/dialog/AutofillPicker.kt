package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import org.midorinext.android.R
import mozilla.components.feature.prompts.R as mozacR

@Composable
internal fun <T> AutofillPicker(
    title: String,
    options: List<T>,
    label: @Composable (T) -> String,
    detail: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    expandable: Boolean = false,
    onManageOptions: (() -> Unit)? = null,
    errorText: String? = null,
    bottomPadding: Dp = 0.dp,
) {
    var expanded by remember { mutableStateOf(!expandable) }
    val keyboard = LocalSoftwareKeyboardController.current
    val bottomPaddingPx = with(LocalDensity.current) { bottomPadding.roundToPx() }
    LaunchedEffect(expanded) {
        if (expandable && expanded) keyboard?.hide()
    }
    BackHandler { if (expandable && expanded) expanded = false else onDismiss() }
    Popup(
        popupPositionProvider = remember(bottomPaddingPx) { AutofillPickerPositionProvider(bottomPaddingPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = !expandable),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .padding(12.dp).testTag("autofill_picker"),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        .testTag("autofill_expand").clickable(enabled = expandable) { expanded = !expanded }
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (expandable) {
                        Icon(
                            painterResource(if (expanded) R.drawable.icons_arrow_up else R.drawable.icons_chevron_down_small),
                            contentDescription = stringResource(if (expanded) mozacR.string.mozac_feature_prompts_collapse_logins_content_description_2 else mozacR.string.mozac_feature_prompts_expand_logins_content_description_2),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.icons_close), stringResource(R.string.cancel))
                        }
                    } else {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    }
                }
                if (expanded) {
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
                    errorText?.let {
                        Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    }
                    onManageOptions?.let { manage ->
                        HorizontalDivider()
                        TextButton(onClick = manage, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("autofill_manage")) {
                            Text(stringResource(mozacR.string.mozac_feature_prompts_manage_logins_2))
                        }
                    }
                }
            }
        }
    }
}

private class AutofillPickerPositionProvider(private val bottomPaddingPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        (windowSize.height - popupContentSize.height - bottomPaddingPx).coerceAtLeast(0),
    )
}
