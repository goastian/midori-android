/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.logins.view

import android.view.View
import androidx.core.view.isVisible
import org.midorinext.android.databinding.LoginsItemBinding
import org.midorinext.android.ext.components
import org.midorinext.android.ext.loadIntoView
import org.midorinext.android.settings.logins.SavedLogin
import org.midorinext.android.settings.logins.interactor.SavedLoginsInteractor
import org.midorinext.android.utils.view.ViewHolder

class LoginsListViewHolder(
    val view: View,
    private val interactor: SavedLoginsInteractor
) : ViewHolder(view) {

    private var loginItem: SavedLogin? = null

    fun bind(item: SavedLogin) {
        this.loginItem = SavedLogin(
            guid = item.guid,
            origin = item.origin,
            password = item.password,
            username = item.username,
            timeLastUsed = item.timeLastUsed
        )
        val binding = LoginsItemBinding.bind(view)
        binding.webAddressView.text = item.origin
        binding.usernameView.isVisible = item.username.isNotEmpty()
        binding.usernameView.text = item.username

        updateFavIcon(binding, item.origin)

        itemView.setOnClickListener {
            interactor.onItemClicked(item)
        }
    }

    private fun updateFavIcon(binding: LoginsItemBinding, url: String) {
        itemView.context.components.core.icons.loadIntoView(binding.faviconImage, url)
    }
}
