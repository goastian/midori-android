/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import org.midorinext.android.R
import org.midorinext.android.compose.preference.SwitchPreference
import org.midorinext.android.compose.preference.TextOnlyPreference
import org.midorinext.android.theme.MidoriTheme

class PrivateBrowsingComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var onAddShortcutClick by mutableStateOf({})
    var onAllowScreenshotsChange by mutableStateOf<(Boolean) -> Unit>({})

    @Composable
    override fun Content() {
        MidoriTheme {
            Column {
                TextOnlyPreference(
                    title = stringResource(R.string.preferences_add_private_browsing_shortcut),
                    key = stringResource(R.string.pref_key_add_private_browsing_shortcut),
                    onClick = onAddShortcutClick,
                )

                SwitchPreference(
                    title = stringResource(R.string.preferences_open_links_in_a_private_tab),
                    key = stringResource(R.string.pref_key_open_links_in_a_private_tab),
                    defaultValue = false,
                )

                SwitchPreference(
                    title = stringResource(R.string.preferences_allow_screenshots_in_private_mode),
                    summary = stringResource(R.string.preferences_screenshots_in_private_mode_disclaimer),
                    key = stringResource(R.string.pref_key_allow_screenshots_in_private_mode),
                    defaultValue = false,
                    onChange = onAllowScreenshotsChange,
                )
            }
        }
    }

}
