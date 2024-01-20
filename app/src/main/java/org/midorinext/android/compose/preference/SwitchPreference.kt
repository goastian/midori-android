/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.compose.preference

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.midorinext.android.compose.Divider
import org.midorinext.android.ext.readBooleanPreference
import org.midorinext.android.ext.writeBooleanPreference
import org.midorinext.android.theme.Theme
import org.midorinext.android.theme.MidoriTheme

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    key: String,
    defaultValue: Boolean,
    onChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    allowDividerAbove: Boolean = false,
    iconSpaceReserved: Boolean = true,
) {
    val context = LocalContext.current
    val (checked, setChecked) = remember {
        mutableStateOf(context.readBooleanPreference(key, defaultValue))
    }

    val stored = context.readBooleanPreference(key, defaultValue)
    if (stored != checked) {
        setChecked(stored)
    }
    return SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { value ->
            context.writeBooleanPreference(key, value)
            onChange?.invoke(value)
            setChecked(value)
        },
        enabled = enabled,
        allowDividerAbove = allowDividerAbove,
        iconSpaceReserved = iconSpaceReserved,
    )
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    allowDividerAbove: Boolean = false,
    iconSpaceReserved: Boolean = true,
) {
    return Column {
        if (allowDividerAbove) {
            Divider()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 8.dp)
                .toggleable(
                    value = checked,
                    onValueChange = if (enabled) onCheckedChange else { _ -> },
                    role = Role.Switch,
                )
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (iconSpaceReserved) 72.dp else 16.dp,
                        end = 8.dp,
                    ),
            ) {
                Text(
                    text = title,
                    color = MidoriTheme.colors.textPrimary,
                    style = MidoriTheme.typography.subtitle1,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        color = MidoriTheme.colors.textSecondary,
                        style = MidoriTheme.typography.body2,
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 16.dp),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MidoriTheme.colors.formSurface,
                    checkedThumbColor = MidoriTheme.colors.formSelected,
                    uncheckedTrackColor = MidoriTheme.colors.formDefault,
                    uncheckedThumbColor = MidoriTheme.colors.indicatorInactive,
                ),
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SwitchPreferenceOnPreview() {
    MidoriTheme(theme = Theme.getTheme()) {
        SwitchPreference(
            title = "Tabs you haven't viewed for two weeks get moved to the inactive section.",
            checked = true,
            onCheckedChange = {},
            enabled = true,
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SwitchPreferenceOffPreview() {
    MidoriTheme(theme = Theme.getTheme()) {
        SwitchPreference(
            title = "Autofill in Midori",
            summary = "Fill and save usernames and passwords in websites while using Midori.",
            checked = false,
            onCheckedChange = {},
            enabled = true,
        )
    }
}
