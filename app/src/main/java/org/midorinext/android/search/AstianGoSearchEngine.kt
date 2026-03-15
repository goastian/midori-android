/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.store.BrowserStore
import org.midorinext.android.R

/**
 * Registers AstianGo (astiango.com) as a custom search engine and sets it as the
 * default for regular browsing sessions.
 */
object AstianGoSearchEngine {

    private const val ASTIANGO_ID = "astiango"
    private const val ASTIANGO_NAME = "AstianGo"
    private const val ASTIANGO_SEARCH_URL = "https://astiango.com/?q={searchTerms}"
    private const val ASTIANGO_SUGGEST_URL = "https://duckduckgo.com/ac/?q=={searchTerms}"

    fun install(context: Context, store: BrowserStore) {
        val icon = getSearchIcon(context)

        val astianGo = SearchEngine(
            id = ASTIANGO_ID,
            name = ASTIANGO_NAME,
            icon = icon,
            type = SearchEngine.Type.CUSTOM,
            resultUrls = listOf(ASTIANGO_SEARCH_URL),
            suggestUrl = ASTIANGO_SUGGEST_URL,
        )

        store.dispatch(
            SearchAction.UpdateCustomSearchEngineAction(astianGo),
        )
        store.dispatch(
            SearchAction.SelectSearchEngineAction(
                searchEngineId = ASTIANGO_ID,
                searchEngineName = ASTIANGO_NAME,
            ),
        )
    }

    private fun getSearchIcon(context: Context): Bitmap {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_midori_welcome_logo)
            ?: return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)

        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 48, 48)
        drawable.draw(canvas)
        return bitmap
    }
}
