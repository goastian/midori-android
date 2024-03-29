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
import org.midorinext.android.compose.preference.PreferenceCategory
import org.midorinext.android.compose.preference.RadioGroupItem
import org.midorinext.android.compose.preference.RadioGroupPreference
import org.midorinext.android.compose.preference.SwitchPreference
import org.midorinext.android.theme.MidoriTheme

class TabsSettingsComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var onCloseTabsNeverClick by mutableStateOf({})
    var onCloseTabsAfterOneDayClick by mutableStateOf({})
    var onCloseTabsAfterOneWeekClick by mutableStateOf({})
    var onCloseTabsAfterOneMonthClick by mutableStateOf({})
    var inactiveTabsCategoryEnabled by mutableStateOf(true)

    @Composable
    override fun Content() {
        MidoriTheme {
            Column {
                PreferenceCategory(
                    title = stringResource(R.string.preferences_tab_view),
                    allowDividerAbove = false,
                ) {
                    RadioGroupPreference(
                        items = listOf(
                            RadioGroupItem(
                                title = stringResource(R.string.tab_view_list),
                                key = stringResource(R.string.pref_key_tab_view_list_do_not_use),
                                defaultValue = false,
                            ),
                            RadioGroupItem(
                                title = stringResource(R.string.tab_view_grid),
                                key = stringResource(R.string.pref_key_tab_view_grid),
                                defaultValue = true,
                            ),
                        ),
                    )
                }

                PreferenceCategory(title = stringResource(R.string.preferences_close_tabs)) {
                    RadioGroupPreference(
                        items = listOf(
                            RadioGroupItem(
                                title = stringResource(R.string.close_tabs_manually),
                                key = stringResource(R.string.pref_key_close_tabs_manually),
                                defaultValue = true,
                                onClick = onCloseTabsNeverClick,
                            ),
                            RadioGroupItem(
                                title = stringResource(R.string.close_tabs_after_one_day),
                                key = stringResource(R.string.pref_key_close_tabs_after_one_day),
                                defaultValue = false,
                                onClick = onCloseTabsAfterOneDayClick,
                            ),
                            RadioGroupItem(
                                title = stringResource(R.string.close_tabs_after_one_week),
                                key = stringResource(R.string.pref_key_close_tabs_after_one_week),
                                defaultValue = false,
                                onClick = onCloseTabsAfterOneWeekClick,
                            ),
                            RadioGroupItem(
                                title = stringResource(R.string.close_tabs_after_one_month),
                                key = stringResource(R.string.pref_key_close_tabs_after_one_month),
                                defaultValue = false,
                                onClick = onCloseTabsAfterOneMonthClick,
                            ),
                        ),
                    )
                }

                PreferenceCategory(
                    title = stringResource(R.string.preferences_inactive_tabs),
                    visible = false,
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preferences_inactive_tabs_title),
                        key = stringResource(R.string.pref_key_inactive_tabs),
                        defaultValue = true,
                        enabled = inactiveTabsCategoryEnabled,
                    )
                }
            }
        }
    }

}
