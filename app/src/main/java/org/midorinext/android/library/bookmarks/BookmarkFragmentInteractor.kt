/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library.bookmarks

import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.utils.Do

/**
 * Interactor for the Bookmarks screen.
 * Provides implementations for the BookmarkViewInteractor.
 *
 * @property bookmarkStore bookmarks state
 * @property viewModel view state
 * @property bookmarksController view controller
 */
@SuppressWarnings("TooManyFunctions")
class BookmarkFragmentInteractor(
    private val bookmarksController: BookmarkController,
) : BookmarkViewInteractor {

    override fun onBookmarksChanged(node: BookmarkNode) {
        bookmarksController.handleBookmarkChanged(node)
    }

    override fun onSelectionModeSwitch(mode: BookmarkFragmentState.Mode) {
        bookmarksController.handleSelectionModeSwitch()
    }

    override fun onEditPressed(node: BookmarkNode) {
        bookmarksController.handleBookmarkEdit(node)
    }

    override fun onAllBookmarksDeselected() {
        bookmarksController.handleAllBookmarksDeselected()
    }

    override fun onSearch() {
        bookmarksController.handleSearch()
    }

    /**
     * Copies the URL of the given BookmarkNode into the copy and paste buffer.
     */
    override fun onCopyPressed(item: BookmarkNode) {
        require(item.type == BookmarkNodeType.ITEM)
        item.url?.let {
            bookmarksController.handleCopyUrl(item)
        }
    }

    override fun onSharePressed(item: BookmarkNode) {
        require(item.type == BookmarkNodeType.ITEM)
        item.url?.let {
            bookmarksController.handleBookmarkSharing(item)
        }
    }

    override fun onOpenInNormalTab(item: BookmarkNode) {
        require(item.type == BookmarkNodeType.ITEM)
        item.url?.let {
            bookmarksController.handleOpeningBookmark(item, BrowsingMode.Normal)
        }
    }

    override fun onOpenInPrivateTab(item: BookmarkNode) {
        require(item.type == BookmarkNodeType.ITEM)
        item.url?.let {
            bookmarksController.handleOpeningBookmark(item, BrowsingMode.Private)
        }
    }

    override fun onDelete(nodes: Set<BookmarkNode>) {
        if (nodes.find { it.type == BookmarkNodeType.SEPARATOR } != null) {
            throw IllegalStateException("Cannot delete separators")
        }
        val eventType = when (nodes.singleOrNull()?.type) {
            BookmarkNodeType.ITEM,
            BookmarkNodeType.SEPARATOR -> BookmarkRemoveType.SINGLE
            BookmarkNodeType.FOLDER -> BookmarkRemoveType.FOLDER
            null -> BookmarkRemoveType.MULTIPLE
        }
        if (eventType == BookmarkRemoveType.FOLDER) {
            bookmarksController.handleBookmarkFolderDeletion(nodes)
        } else {
            bookmarksController.handleBookmarkDeletion(nodes, eventType)
        }
    }

    override fun onBackPressed() {
        bookmarksController.handleBackPressed()
    }

    override fun open(item: BookmarkNode) {
        Do exhaustive when (item.type) {
            BookmarkNodeType.ITEM -> {
                bookmarksController.handleBookmarkTapped(item)
            }
            BookmarkNodeType.FOLDER -> bookmarksController.handleBookmarkExpand(item)
            BookmarkNodeType.SEPARATOR -> throw IllegalStateException("Cannot open separators")
        }
    }

    override fun select(item: BookmarkNode) {
        bookmarksController.handleBookmarkSelected(item)
    }

    override fun deselect(item: BookmarkNode) {
        bookmarksController.handleBookmarkDeselected(item)
    }

    override fun onRequestSync() {
        bookmarksController.handleRequestSync()
    }
}
