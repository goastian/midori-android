package org.midorinext.android.suggest.providers

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardProvider @Inject constructor(
    @ApplicationContext context: Context
) : SuggestionProvider {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override suspend fun getSuggestions(text: String): List<Suggestion> {
        if (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
            clipboard.primaryClip?.getItemAt(0)?.let {
                if (it.text?.isNotEmpty() == true) {
                    return listOf(Suggestion.SearchSuggestion(this, text, it.text.toString()))
                } else if (it.uri != null) {
                    val uri = it.uri.toString()
                    return listOf(Suggestion.OpenTabSuggestion(this, text, uri, uri))
                }
            }
        }

        return listOf()
    }
}