package org.midorinext.android.ui.preferences.widgets

import androidx.annotation.StringRes
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun PreferenceToggle(
    @StringRes label: Int,
    @StringRes description: Int? = null,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    PreferenceRow(
        label = label,
        description = description?.let { stringResource(id = it) },
        trailing = { Switch(checked = value, onCheckedChange = onValueChange) }
    )
}