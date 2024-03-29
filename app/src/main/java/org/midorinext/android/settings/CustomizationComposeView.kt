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
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import org.midorinext.android.R
import org.midorinext.android.compose.preference.PreferenceCategory
import org.midorinext.android.compose.preference.RadioGroupItem
import org.midorinext.android.compose.preference.RadioGroupPreference
import org.midorinext.android.compose.preference.SwitchPreference
import org.midorinext.android.compose.preference.TextOnlyPreference
import org.midorinext.android.theme.MidoriTheme

class CustomizationComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var onSelectThemeClick by mutableStateOf({})

    @Composable
    override fun Content() {
        MidoriTheme {
            Column {
                PreferenceCategory(
                    title = stringResource(R.string.preferences_theme),
                    allowDividerAbove = false,
                ) {
                    TextOnlyPreference(
                        title = stringResource(id = R.string.preferences_select_theme),
                        key = stringResource(id = R.string.pref_key_select_theme),
                        onClick = onSelectThemeClick,
                    )
                }

                PreferenceCategory(title = stringResource(R.string.preferences_toolbar)) {
                    RadioGroupPreference(
                        items = listOf(
                            RadioGroupItem(
                                title = stringResource(R.string.preference_top_toolbar),
                                key = stringResource(R.string.pref_key_toolbar_top),
                                defaultValue = false,
                            ),
                            RadioGroupItem(
                                title = stringResource(R.string.preference_bottom_toolbar),
                                key = stringResource(R.string.pref_key_toolbar_bottom),
                                defaultValue = true,
                            ),
                        ),
                    )
                }

                PreferenceCategory(title = stringResource(R.string.preferences_gestures)) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_gestures_website_pull_to_refresh),
                        key = stringResource(R.string.pref_key_website_pull_to_refresh),
                        defaultValue = true,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_gestures_dynamic_toolbar),
                        key = stringResource(R.string.pref_key_dynamic_toolbar),
                        defaultValue = true,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_gestures_swipe_toolbar_switch_tabs),
                        key = stringResource(R.string.pref_key_swipe_toolbar_switch_tabs),
                        defaultValue = true,
                    )
                }
            }
        }
    }

}
