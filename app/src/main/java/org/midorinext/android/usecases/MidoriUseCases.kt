package org.midorinext.android.usecases

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.midorinext.android.BuildConfig
import org.midorinext.android.newtab.MidoriNewTabFeature
import org.midorinext.android.storage.MidoriClientProvider
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

// TODO separate MidoriUseCases into multiple use cases ?
@Singleton
class MidoriUseCases @Inject constructor(
    @ApplicationContext val context: Context,
    private val clientProvider: MidoriClientProvider,
    private val newTabFeature: MidoriNewTabFeature,
) {
    // TODO Use constructor injection for those. lazy init the usecase itself if needed
    @Inject lateinit var sessionUseCases: Lazy<SessionUseCases>
    @Inject lateinit var tabsUseCases: Lazy<TabsUseCases>

    // TODO find a different way of handling fs=1 one time parameter
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val firstRequestKey = "pref_key_first_request"
    private val clKey = "pref_key_utm_campaign"

    private fun midoriUrl(path: String? = null, search: String? = null, category: String? = null, widget: Boolean = false): String = buildString {
        append(BuildConfig.QWANT_BASE_URL)
        path?.let {
            append(path)
        }
        append("?client=").append(encodeQueryValue(clientProvider.clientState.value))
        append("&omnibar=1")
        search?.let {
            append("&q=").append(encodeQueryValue(it))
        }
        category?.let {
            append("&t=").append(encodeQueryValue(it))
        }
        // TODO check if fs & cl are still used at Midori. Else remove them
        if (prefs.getBoolean(firstRequestKey, true)) {
            append("&fs=1")
            prefs.edit().putBoolean(firstRequestKey, false).apply()
        }
        prefs.getString(clKey, null)?.let {
            append("&cl=").append(encodeQueryValue(it))
        }
        if (widget) {
            append("&widget=1")
        }
        append("&qbc=1")
    }

    inner class OpenMidoriPageUseCase internal constructor(
        private val tabsUseCases: TabsUseCases,
    ) {
        @SuppressLint("ApplySharedPref")
        operator fun invoke(search: String? = null, private: Boolean = false, selectIfExists: Boolean = false) {
            val usesLocalNewTab = search == null && !private && newTabFeature.isEnabled
            val url = if (usesLocalNewTab) {
                newTabFeature.currentOrLoadingUrl()
            } else {
                midoriUrl(search = search)
            }
            if (selectIfExists) {
                tabsUseCases.selectOrAddTab.invoke(url, private = private)
            } else {
                tabsUseCases.addTab.invoke(
                    url,
                    selectTab = true,
                    private = private
                )
            }
        }
    }

    inner class LoadNewTabPageUseCase internal constructor(
        private val sessionUseCases: SessionUseCases,
    ) {
        operator fun invoke(tabId: String, replaceCurrent: Boolean = false) {
            val url = if (newTabFeature.isEnabled) {
                newTabFeature.currentOrLoadingUrl()
            } else {
                midoriUrl()
            }
            sessionUseCases.loadUrl(
                url = url,
                flags = loadFlags(replaceCurrent),
                sessionId = tabId,
            )
        }
    }

    private fun loadFlags(replaceCurrent: Boolean): EngineSession.LoadUrlFlags =
        if (replaceCurrent) {
            EngineSession.LoadUrlFlags.select(EngineSession.LoadUrlFlags.LOAD_FLAGS_REPLACE_HISTORY)
        } else {
            EngineSession.LoadUrlFlags.none()
        }

    inner class GetMidoriUrlUseCase internal constructor() {
        operator fun invoke(path: String? = null, search: String? = null, category: String? = null, widget: Boolean = false) = midoriUrl(path, search, category, widget)
    }

    inner class LoadSERPPageUseCase internal constructor(
        private val sessionUseCases: SessionUseCases
    ) {
        operator fun invoke(search: String, category: String? = null) {
            sessionUseCases.loadUrl(midoriUrl(search = search, category = category))
        }
    }

    class OpenPrivatePageUseCase internal constructor(
        private val tabsUseCases: TabsUseCases
    ) {
        operator fun invoke() {
            tabsUseCases.addTab(
                "",
                selectTab = true,
                private = true
            )
        }
    }

    class OpenTestPageUseCase internal constructor(
        private val context: Context,
        private val tabsUseCases: TabsUseCases,
        private val sessionUseCases: SessionUseCases
    ) {
        operator fun invoke(test: String) {
            val testHtml = context.assets.open("tests/$test.html")
                .bufferedReader().use {
                    it.readText()
                }

            sessionUseCases.loadData(
                data = testHtml,
                mimeType = "text/html",
                tabId = tabsUseCases.addTab(
                    "about:test:$test",
                    selectTab = true
                )
            )
        }
    }

    val openMidoriPage: OpenMidoriPageUseCase by lazy {
        OpenMidoriPageUseCase(tabsUseCases.get())
    }
    val loadNewTabPage: LoadNewTabPageUseCase by lazy {
        LoadNewTabPageUseCase(sessionUseCases.get())
    }
    val getMidoriUrl: GetMidoriUrlUseCase by lazy {
        GetMidoriUrlUseCase()
    }
    val loadSERPPage: LoadSERPPageUseCase by lazy {
        LoadSERPPageUseCase(sessionUseCases.get())
    }
    val openPrivatePage: OpenPrivatePageUseCase by lazy {
        OpenPrivatePageUseCase(tabsUseCases.get())
    }
    val openTestPageUseCase: OpenTestPageUseCase by lazy {
        OpenTestPageUseCase(context, tabsUseCases.get(), sessionUseCases.get())
    }

    fun isNewTabUrl(url: String?): Boolean = newTabFeature.isNewTabUrl(url)

    fun isNewTabLoadingUrl(url: String?): Boolean = newTabFeature.isLoadingUrl(url)

    val isNewTabEnabled: Boolean get() = newTabFeature.isEnabled

    val newTabState: StateFlow<MidoriNewTabFeature.InstallState> get() = newTabFeature.state

    val newTabPageUrl: StateFlow<String?> get() = newTabFeature.pageUrl

    companion object {
        internal fun encodeQueryValue(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
