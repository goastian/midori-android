package org.midorinext.android.ui.preferences.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role

@Composable
fun PreferenceToggle(
    @StringRes label: Int,
    @StringRes description: Int? = null,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    PreferenceRow(
        label = label,
        modifier = Modifier.toggleable(value = value, role = Role.Switch, onValueChange = onValueChange),
        description = description?.let { stringResource(id = it) },
        trailing = { Switch(checked = value, onCheckedChange = null) },
    )
}
