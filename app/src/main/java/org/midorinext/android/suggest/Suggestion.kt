package org.midorinext.android.suggest

sealed class Suggestion(val provider: SuggestionProvider, val search: String) {
    class SearchSuggestion(provider: SuggestionProvider, search: String,
                           val text: String
    ): Suggestion(provider, search)
    class BrandSuggestion(provider: SuggestionProvider, search: String,
                          val title: String,
                          val url: String,
                          val faviconUrl: String?,
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
