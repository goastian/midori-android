package org.midorinext.android.newtab

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
class MidoriNewTabFeature @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("native_home_origins", Context.MODE_PRIVATE)
    private val origins = MutableStateFlow(preferences.getStringSet("legacy", emptySet()).orEmpty().toSet())
    val legacyOrigins = origins.asStateFlow()

    fun currentPageUrl(): String = NATIVE_HOME_URL

    fun currentOrLoadingUrl(): String = NATIVE_HOME_URL

    fun isLoadingUrl(url: String?): Boolean = url == LEGACY_NEW_TAB_URL

    fun isNewTabUrl(url: String?): Boolean = isNativeHomeUrl(url) || isLegacyUrl(url)

    fun isLegacyUrl(url: String?): Boolean = url != null &&
        (url == LEGACY_NEW_TAB_URL || url == LEGACY_NATIVE_HOME_URL || url.isLegacyMidoriHomeUrl() || isLegacyExtensionHome(url, origins.value))

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
        const val EXTENSION_ID = "midoritabs@astian.org"
        const val LOADING_URL = LEGACY_NEW_TAB_URL

        internal fun isLegacyExtensionHome(url: String, origins: Set<String>): Boolean = runCatching {
            val candidate = URI(url)
            candidate.scheme == "moz-extension" && candidate.rawQuery == null &&
                candidate.path in listOf("", "/", "/index.html") &&
                origins.any { URI(it).host == candidate.host }
        }.getOrDefault(false)
    }
}
