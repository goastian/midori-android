/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import org.midorinext.android.R
import org.midorinext.android.components.components
import org.midorinext.android.ext.showToolbar
import org.midorinext.android.theme.MidoriTheme

class SecretDebugSettingsFragment : Fragment() {

    override fun onResume() {
        super.onResume()

        showToolbar(getString(R.string.preferences_debug_info))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MidoriTheme {
                    DebugInfo()
                }
            }
        }
    }
}

@Composable
private fun DebugInfo() {
    val store = components.core.store

    Column(
        modifier = Modifier
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.debug_info_region_home),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(4.dp),
        )
        Text(
            text = store.state.search.region?.home ?: "Unknown",
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(4.dp)
        )
        Text(
            text = stringResource(R.string.debug_info_region_current),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(4.dp)
        )
        Text(
            text = store.state.search.region?.current ?: "Unknown",
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(4.dp)
        )
    }
}
