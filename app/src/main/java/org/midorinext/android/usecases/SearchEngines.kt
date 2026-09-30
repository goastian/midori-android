package org.midorinext.android.usecases

import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.SearchEnginePreference
import java.net.URI

data class SearchEngineOption(
    val id: String,
    val name: String,
    val builtIn: SearchEnginePreference? = null,
)

/** Search URL templates used for address-bar searches. */
object SearchEngines {
    val builtInEngines = listOf(
        SearchEnginePreference.ASTIANGO,
        SearchEnginePreference.ECOSIA,
        SearchEnginePreference.QWANT,
        SearchEnginePreference.STARTPAGE,
    )

    fun id(engine: SearchEnginePreference): String = engine.name.lowercase()

    fun options(preferences: AppPreferences): List<SearchEngineOption> =
        builtInEngines.map { SearchEngineOption(id(it), it.name, it) } +
            preferences.customSearchEnginesList.map { SearchEngineOption(it.id, it.name) }

    fun selectedId(preferences: AppPreferences, private: Boolean = false): String {
        val customId = if (private && preferences.useSeparatePrivateSearchEngine) {
            preferences.customPrivateSearchEngineId
        } else {
            preferences.customDefaultSearchEngineId
        }
        if (preferences.customSearchEnginesList.any { it.id == customId }) return customId
        val builtIn = if (private && preferences.useSeparatePrivateSearchEngine) {
            preferences.privateSearchEngine
        } else {
            preferences.searchEngine
        }
        return id(builtIn)
    }

    fun isValidTemplate(template: String): Boolean {
        if (!template.contains("%s") || template.indexOf("%s") != template.lastIndexOf("%s")) return false
        val uri = runCatching { URI(template.replace("%s", "search")) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }

    fun url(
        preferences: AppPreferences,
        engineId: String,
        query: String,
        astianGoUrl: () -> String,
    ): String {
        val custom = preferences.customSearchEnginesList.find { it.id == engineId }
        if (custom != null && isValidTemplate(custom.searchUrlTemplate)) {
            return custom.searchUrlTemplate.replace("%s", MidoriUseCases.encodeQueryValue(query))
        }
        val builtIn = builtInEngines.find { id(it) == engineId } ?: SearchEnginePreference.ASTIANGO
        return url(builtIn, query, astianGoUrl)
    }

    fun url(engine: SearchEnginePreference, query: String, astianGoUrl: () -> String): String {
        val encoded = MidoriUseCases.encodeQueryValue(query)
        return when (engine) {
            SearchEnginePreference.ECOSIA -> "https://www.ecosia.org/search?q=$encoded"
            SearchEnginePreference.QWANT -> "https://www.qwant.com/?q=$encoded"
            SearchEnginePreference.STARTPAGE -> "https://www.startpage.com/sp/search?query=$encoded"
            SearchEnginePreference.ASTIANGO, SearchEnginePreference.UNRECOGNIZED -> astianGoUrl()
        }
    }
}
