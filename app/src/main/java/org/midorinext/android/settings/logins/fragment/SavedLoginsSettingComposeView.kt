/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.logins.fragment

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import org.midorinext.android.R
import org.midorinext.android.compose.preference.RadioGroupItem
import org.midorinext.android.compose.preference.RadioGroupPreference
import org.midorinext.android.theme.MidoriTheme

class SavedLoginsSettingComposeView@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var onAskToSaveClick by mutableStateOf({})
    var onNeverSaveClick by mutableStateOf({})

    @Composable
    override fun Content() {
        MidoriTheme {
            RadioGroupPreference(
                items = listOf(
                    RadioGroupItem(
                        title = stringResource(R.string.preferences_passwords_save_logins_ask_to_save),
                        key = stringResource(R.string.pref_key_save_logins),
                        defaultValue = true,
                        onClick = onAskToSaveClick,
                    ),
                    RadioGroupItem(
                        title = stringResource(R.string.preferences_passwords_save_logins_never_save),
                        key = stringResource(R.string.pref_key_never_save_logins),
                        defaultValue = false,
                        onClick = onNeverSaveClick,
                    ),
                ),
            )
        }
    }

}
