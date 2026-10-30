package org.midorinext.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.map
import mozilla.components.concept.storage.VisitInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.paging.cachedIn
import org.midorinext.android.storage.history.HistoryPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.tabs.TabsUseCases
import javax.inject.Inject

sealed interface HistoryRow {
    data class Entry(val visit: VisitInfo) : HistoryRow
    data class DateHeader(val date: LocalDate) : HistoryRow
}

internal fun VisitInfo.historyDate(): LocalDate =
    Instant.ofEpochMilli(visitTime).atZone(ZoneId.systemDefault()).toLocalDate()

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
            initialLoadSize = 100,
            prefetchDistance = 25,
            maxSize = 300,
            enablePlaceholders = false
        ),
        pagingSourceFactory = source
    )

    val historyItems = pager.flow.map { data ->
        data.map { HistoryRow.Entry(it) }
            .insertSeparators<HistoryRow.Entry, HistoryRow> { before, after ->
                after?.visit?.historyDate()?.takeIf { it != before?.visit?.historyDate() }
                    ?.let(HistoryRow::DateHeader)
            }
    }.cachedIn(viewModelScope)

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