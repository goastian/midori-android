package org.midorinext.android.suggest

import android.graphics.Bitmap

sealed class Suggestion(val provider: SuggestionProvider, val search: String) {
    class SearchSuggestion(provider: SuggestionProvider, search: String,
                           val text: String
    ): Suggestion(provider, search)
    class BrandSuggestion(provider: SuggestionProvider, search: String,
                          val title: String,
                          val url: String,
                          val favicon: Bitmap?,
                          val brand: String,
                          val domain: String,
                          val rank: Int,
                          val suggestType: Int
    ): Suggestion(provider, search)
    class SelectTabSuggestion(provider: SuggestionProvider, search: String,
                              val tabId: String,
                              val title: String,
                              val url: String
    ): Suggestion(provider, search)
    class OpenTabSuggestion(provider: SuggestionProvider, search: String,
                            val title: String?,
                            val url: String?
    ): Suggestion(provider, search)
}