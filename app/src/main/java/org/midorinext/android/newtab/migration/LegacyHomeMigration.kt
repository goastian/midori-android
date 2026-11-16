package org.midorinext.android.newtab.migration

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.midorinext.android.ext.isLegacyMidoriHomeUrl
import org.mozilla.geckoview.GeckoRuntime
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyHomeMigration @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("native_home_origins", Context.MODE_PRIVATE)
    private val origins = MutableStateFlow(preferences.getStringSet("legacy", emptySet()).orEmpty().toSet())
    val legacyOrigins = origins.asStateFlow()

    fun isLegacyUrl(url: String?): Boolean = url != null &&
        (isLegacyNativeHomeUrl(url) || url.isLegacyMidoriHomeUrl() || isLegacyExtensionHome(url, origins.value))

    fun registerLegacyOrigins(runtime: GeckoRuntime) {
        runtime.webExtensionController.list().accept({ extensions ->
            val registered = extensions.orEmpty().filter { it.id == EXTENSION_ID }
                .mapNotNull { it.metaData.baseUrl }.toSet()
            if (registered.isNotEmpty()) {
                origins.value = origins.value + registered
                preferences.edit().putStringSet("legacy", origins.value).apply()
            }
        }, { _ -> })
    }

    companion object {
        private const val EXTENSION_ID = "midoritabs@astian.org"

        internal fun isLegacyExtensionHome(url: String, origins: Set<String>): Boolean = runCatching {
            val candidate = URI(url)
            candidate.scheme == "moz-extension" && candidate.rawQuery == null &&
                candidate.path in listOf("", "/", "/index.html") &&
                origins.any { URI(it).host == candidate.host }
        }.getOrDefault(false)
    }
}

const val LEGACY_NEW_TAB_URL = "about:blank#midori-newtab"
const val LEGACY_NATIVE_HOME_URL = "data:text/html;base64,PCFkb2N0eXBlIGh0bWw+PHRpdGxlPk1pZG9yaTwvdGl0bGU+PCEtLW1pZG9yaS1uYXRpdmUtaG9tZS0tPg=="

fun isLegacyNativeHomeUrl(url: String?): Boolean = url == LEGACY_NEW_TAB_URL || url == LEGACY_NATIVE_HOME_URL
