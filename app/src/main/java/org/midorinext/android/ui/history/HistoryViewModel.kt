package org.midorinext.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import org.midorinext.android.storage.history.HistoryPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.tabs.TabsUseCases
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyStorage: HistoryStorage,
    val browserIcons: BrowserIcons,
    tabsUseCases: TabsUseCases
) : ViewModel() {

    private val source = InvalidatingPagingSourceFactory { HistoryPagingSource(historyStorage) }
    private val pager = Pager(
        config = PagingConfig(
            pageSize = 50,
            enablePlaceholders = false
        ),
        pagingSourceFactory = source
    )

    val historyItems = pager.flow.cachedIn(viewModelScope)

    val openNewTab = tabsUseCases.addTab

    fun deleteUrlFromHistory(url: String) {
        viewModelScope.launch {
            historyStorage.deleteVisitsFor(url)
            source.invalidate()
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            historyStorage.deleteEverything()
            source.invalidate()
        }
    }
}