package org.midorinext.android.intent

import android.app.SearchManager
import android.content.Intent
import kotlinx.coroutines.flow.first
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow
import org.midorinext.android.usecases.MidoriUseCases
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.externalPackage
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.pwa.intent.WebAppIntentProcessor
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.kotlin.isUrl
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import mozilla.components.support.utils.SafeIntent
import mozilla.components.support.utils.WebURLFinder

class MidoriIntentProcessor(
    private val tabsUseCases: TabsUseCases,
    private val MidoriUseCases: MidoriUseCases,
    private val store: BrowserStore,
) {
    private suspend fun processViewIntent(intent: SafeIntent): Boolean {
        val url = intent.dataString

        return if (url.isNullOrEmpty()) {
            false
        } else {
            addNewTab(url.toNormalizedUrl(), SessionState.Source.External.ActionView(intent.externalPackage()))
            true
        }
    }

    private suspend fun processSendIntent(intent: SafeIntent): Boolean {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)

        return if (extraText.isNullOrBlank()) {
            false
        } else {
            val url = WebURLFinder(extraText).bestWebURL()
            val source = SessionState.Source.External.ActionSend(intent.externalPackage())
            if (url != null) {
                addNewTab(url, source)
            } else {
                openSearch(extraText, source)
            }
            true
        }
    }

    private suspend fun processSearchIntent(intent: SafeIntent): Boolean {
        val searchQuery = intent.getStringExtra(SearchManager.QUERY)

        return if (searchQuery.isNullOrBlank()) {
            false
        } else {
            val source = SessionState.Source.External.ActionSearch(intent.externalPackage())
            if (searchQuery.isUrl()) {
                addNewTab(searchQuery, source)
            } else {
                openSearch(searchQuery, source)
            }
            true
        }
    }

    private suspend fun addNewTab(url: String, source: SessionState.Source) {
        val id = tabsUseCases.addTab(
            url.toNormalizedUrl(),
            source = source,
            flags = EngineSession.LoadUrlFlags.external(),
        )
        store.flow().first { state -> state.tabs.any { it.id == id } }
    }

    private suspend fun openSearch(query: String, source: SessionState.Source.External) {
        val existingIds = store.state.tabs.mapTo(hashSetOf()) { it.id }
        MidoriUseCases.openMidoriPage(search = query, source = source).join()
        store.flow().first { state -> state.tabs.any { it.id !in existingIds && it.source == source } }
    }

    suspend fun process(intent: Intent): Boolean {
        val safeIntent = SafeIntent(intent)
        return when (safeIntent.action) {
            WebAppIntentProcessor.ACTION_VIEW_PWA, OLD_SHORTCUTS_ACTION -> processViewIntent(safeIntent)
            Intent.ACTION_VIEW, Intent.ACTION_MAIN -> processViewIntent(safeIntent) // TODO add, test and support nft ACTION_NDEF_DISCOVERED
            Intent.ACTION_SEND -> processSendIntent(safeIntent)
            Intent.ACTION_SEARCH, Intent.ACTION_WEB_SEARCH -> processSearchIntent(safeIntent)
            else -> false
        }
    }

    companion object {
        const val OLD_SHORTCUTS_ACTION = "org.mozilla.gecko.BOOKMARK"
    }
}
