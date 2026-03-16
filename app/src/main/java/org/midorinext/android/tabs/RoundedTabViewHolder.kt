/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabs

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.card.MaterialCardView
import mozilla.components.browser.state.state.TabSessionState
import org.midorinext.android.R
import mozilla.components.browser.tabstray.TabViewHolder
import mozilla.components.browser.tabstray.TabsTray
import mozilla.components.browser.tabstray.TabsTrayStyling
import mozilla.components.browser.tabstray.thumbnail.TabThumbnailView
import mozilla.components.concept.base.images.ImageLoadRequest
import mozilla.components.concept.base.images.ImageLoader
import mozilla.components.support.ktx.android.util.dpToPx
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl

/**
 * A custom TabViewHolder that preserves MaterialCardView rounded corners.
 *
 * The default [mozilla.components.browser.tabstray.DefaultTabViewHolder] calls
 * itemView.setBackgroundColor() which paints a flat rectangular background over the
 * MaterialCardView, destroying its rounded corners. This implementation sets the
 * card background color via [MaterialCardView.setCardBackgroundColor] instead.
 */
class RoundedTabViewHolder(
    itemView: View,
    private val thumbnailLoader: ImageLoader? = null,
) : TabViewHolder(itemView) {

    private val cardView: MaterialCardView? = itemView as? MaterialCardView
    private val titleView: TextView = itemView.findViewById(R.id.mozac_browser_tabstray_title)
    private val urlView: TextView? = itemView.findViewById(R.id.mozac_browser_tabstray_url)
    private val closeView: AppCompatImageButton = itemView.findViewById(R.id.mozac_browser_tabstray_close)
    private val thumbnailView: TabThumbnailView = itemView.findViewById(R.id.mozac_browser_tabstray_thumbnail)

    override var tab: TabSessionState? = null
    private var styling: TabsTrayStyling? = null

    override fun bind(
        tab: TabSessionState,
        isSelected: Boolean,
        styling: TabsTrayStyling,
        delegate: TabsTray.Delegate,
    ) {
        this.tab = tab
        this.styling = styling

        val title = if (tab.content.title.isNotEmpty()) {
            tab.content.title
        } else {
            tab.content.url
        }

        titleView.text = title
        urlView?.text = tab.content.url.tryGetHostFromUrl()

        itemView.setOnClickListener {
            delegate.onTabSelected(tab)
        }

        closeView.setOnClickListener {
            delegate.onTabClosed(tab)
        }

        updateSelectedTabIndicator(isSelected)

        if (thumbnailLoader != null) {
            val thumbnailSize = THUMBNAIL_SIZE.dpToPx(thumbnailView.context.resources.displayMetrics)
            thumbnailLoader.loadIntoView(
                thumbnailView,
                ImageLoadRequest(id = tab.id, size = thumbnailSize, isPrivate = tab.content.private),
            )
        }
    }

    override fun updateSelectedTabIndicator(showAsSelected: Boolean) {
        if (showAsSelected) {
            showItemAsSelected()
        } else {
            showItemAsNotSelected()
        }
    }

    private fun showItemAsSelected() {
        styling?.let { s ->
            titleView.setTextColor(s.selectedItemTextColor)
            closeView.imageTintList = ColorStateList.valueOf(s.selectedItemTextColor)
            // Use setCardBackgroundColor to preserve rounded corners
            cardView?.setCardBackgroundColor(s.selectedItemBackgroundColor)
        }
    }

    private fun showItemAsNotSelected() {
        styling?.let { s ->
            titleView.setTextColor(s.itemTextColor)
            closeView.imageTintList = ColorStateList.valueOf(s.itemTextColor)
            // Use setCardBackgroundColor to preserve rounded corners
            cardView?.setCardBackgroundColor(s.itemBackgroundColor)
        }
    }

    companion object {
        private const val THUMBNAIL_SIZE = 100
    }
}
