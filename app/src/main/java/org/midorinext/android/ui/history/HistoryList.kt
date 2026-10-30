package org.midorinext.android.ui.history

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import mozilla.components.concept.storage.VisitInfo
import org.midorinext.android.R
import mozilla.components.feature.contextmenu.R as mozacR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    visits: LazyPagingItems<HistoryRow>,
    historyViewModel: HistoryViewModel,
    onItemSelected: (visit: VisitInfo, private: Boolean) -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val retainedRows = visits.itemCount
    SideEffect { org.midorinext.android.diagnostics.ScenarioMetrics.rows(
        org.midorinext.android.diagnostics.ScenarioMetrics.Rows.HISTORY, retainedRows
    ) }
    val todayString = stringResource(id = R.string.history_today)
    val yesterdayString = stringResource(id = R.string.history_yesterday)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            items(
                count = visits.itemCount,
                key = visits.itemKey { row ->
                    when (row) {
                        is HistoryRow.DateHeader -> "date-${row.date}"
                        is HistoryRow.Entry -> "visit-${row.visit.url}"
                    }
                },
                contentType = { index -> if (visits.peek(index) is HistoryRow.DateHeader) 0 else 1 }
            ) { index ->
                when (val row = visits[index]) {
                    is HistoryRow.DateHeader -> {
                        val today = java.time.LocalDate.now()
                        val dateString = when (row.date) {
                            today -> todayString
                            today.minusDays(1) -> yesterdayString
                            else -> "${row.date.dayOfMonth}/${row.date.monthValue}/${row.date.year}"
                        }
                        Text(text = dateString, modifier = Modifier.padding(start = 8.dp))
                    }
                    is HistoryRow.Entry -> {
                        val item = row.visit
                        val clipboard = LocalClipboard.current.nativeClipboard
                        HistoryItem(
                            visit = item,
                            browserIcons = historyViewModel.browserIcons,
                            onItemSelected = onItemSelected,
                            menuItems = listOf(
                                MenuItem(
                                    stringResource(mozacR.string.mozac_feature_contextmenu_open_link_in_new_tab),
                                    R.drawable.icons_search
                                ) {
                                    onItemSelected(item, false)
                                },
                                MenuItem(
                                    stringResource(mozacR.string.mozac_feature_contextmenu_open_link_in_private_tab),
                                    R.drawable.icons_privacy_mask
                                ) {
                                    onItemSelected(item, true)
                                },
                                MenuItem(
                                    stringResource(mozacR.string.mozac_feature_contextmenu_copy_link),
                                    R.drawable.icons_download
                                ) {
                                    clipboard.setPrimaryClip(ClipData.newRawUri(item.url, item.url.toUri()))
                                },
                                MenuItem(
                                    stringResource(R.string.delete),
                                    R.drawable.icons_close
                                ) {
                                    historyViewModel.deleteUrlFromHistory(item.url)
                                }
                            ),
                            modifier = Modifier.animateItem()
                        )
                    }
                    null -> Unit
                }
            }

            if (visits.loadState.append == LoadState.Loading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

