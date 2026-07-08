package org.midorinext.android.ui.readinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import org.midorinext.android.storage.readinglist.ReadingListItem
import org.midorinext.android.storage.readinglist.ReadingListRepository
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val readingListRepository: ReadingListRepository,
    private val tabsUseCases: TabsUseCases
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    val items = searchQuery
        .flatMapLatest { query -> readingListRepository.getItemsFlow(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        searchQuery.update { query }
    }

    fun open(item: ReadingListItem, private: Boolean = false) {
        tabsUseCases.addTab(item.url.toNormalizedUrl(), private = private)
    }

    fun toggleRead(item: ReadingListItem) {
        viewModelScope.launch(Dispatchers.IO) {
            readingListRepository.setRead(item.id, !item.read)
        }
    }

    fun delete(item: ReadingListItem) {
        viewModelScope.launch(Dispatchers.IO) {
            readingListRepository.delete(item.id)
        }
    }
}
