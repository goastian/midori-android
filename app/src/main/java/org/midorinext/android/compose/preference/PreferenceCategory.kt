/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.compose.preference

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.midorinext.android.compose.Divider
import org.midorinext.android.theme.Theme
import org.midorinext.android.theme.MidoriTheme

@Composable
fun PreferenceCategory(
    title: String,
    allowDividerAbove: Boolean = true,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!visible) {
        return
    }

    return Column {
        if (allowDividerAbove) {
            Divider()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 72.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.CenterStart),
                color = MidoriTheme.colors.textAccent,
                style = MidoriTheme.typography.body2,
            )
        }

        content()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PreferenceCategoryPreview() {
    MidoriTheme(theme = Theme.getTheme()) {
        PreferenceCategory("Tab view") {
            RadioButtonPreference(
                title = "List",
                selected = true,
                onValueChange = {},
            )

            RadioButtonPreference(
                title = "Grid",
                selected = false,
                onValueChange = {},
            )
        }
    }
}
