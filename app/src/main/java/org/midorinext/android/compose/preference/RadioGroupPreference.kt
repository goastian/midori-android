/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.compose.preference

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.midorinext.android.ext.readBooleanPreference
import org.midorinext.android.ext.writeBooleanPreference
import org.midorinext.android.theme.Theme
import org.midorinext.android.theme.MidoriTheme

@Composable
fun RadioGroupPreference(
    items: List<RadioGroupItem>,
) {
    val context = LocalContext.current
    val (selected, setSelected) = remember {
        mutableStateOf(
            items.firstOrNull { context.readBooleanPreference(it.key, it.defaultValue) },
        )
    }

    return Column {
        items.forEach { item ->
            if (item.visible) {
                RadioButtonPreference(
                    title = item.title,
                    selected = selected?.key == item.key,
                    onValueChange = {
                        items.forEach { context.writeBooleanPreference(it.key, it.key == item.key) }
                        item.onClick?.invoke()
                        setSelected(item)
                    },
                )
            }
        }
    }
}

data class RadioGroupItem(
    val title: String,
    val key: String,
    val defaultValue: Boolean,
    val visible: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RadioButtonPreference(
    title: String,
    selected: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    return Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                onValueChange = if (enabled) onValueChange else { _ -> },
                role = Role.RadioButton,
            )
            .alpha(if (enabled) 1f else 0.5f)
            .semantics {
                testTagsAsResourceId = true
                testTag = "radio.button.preference"
            },
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier
                .size(48.dp)
                .padding(start = 16.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = MidoriTheme.colors.formSelected,
                unselectedColor = MidoriTheme.colors.formDefault,
            ),
        )

        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(
                    start = 24.dp,
                    end = 16.dp,
                ),
            color = MidoriTheme.colors.textPrimary,
            style = MidoriTheme.typography.subtitle1,
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun RadioButtonPreferenceOnPreview() {
    MidoriTheme(theme = Theme.getTheme()) {
        RadioButtonPreference(
            title = "List",
            selected = true,
            onValueChange = {},
            enabled = true,
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun RadioButtonPreferenceOffPreview() {
    MidoriTheme(theme = Theme.getTheme()) {
        RadioButtonPreference(
            title = "List",
            selected = false,
            onValueChange = {},
            enabled = true,
        )
    }
}
