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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import mozilla.components.concept.storage.VisitInfo
import org.midorinext.android.R
import java.util.Calendar
import java.util.Date
import mozilla.components.feature.contextmenu.R as mozacR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryList(
    visits: LazyPagingItems<VisitInfo>,
    historyViewModel: HistoryViewModel,
    onItemSelected: (visit: VisitInfo, private: Boolean) -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val todayString = stringResource(id = R.string.history_today)
    val yesterdayString = stringResource(id = R.string.history_yesterday)

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (visits.itemCount > 0) {
                val calendar = Calendar.getInstance()
                val todayDayOfYear = calendar.apply { time = Date() }.get(Calendar.DAY_OF_YEAR)
                var lastDayOfYear: Int? = null

                for (i in 0 until visits.itemCount) {
                    visits[i]?.let { item ->
                        calendar.apply { time = Date(item.visitTime) }
                        val visitDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                        if (lastDayOfYear == null || visitDayOfYear != lastDayOfYear) {
                            val dateString =
                                when (todayDayOfYear - calendar.get(Calendar.DAY_OF_YEAR)) {
                                    0 -> todayString
                                    1 -> yesterdayString
                                    else -> "${calendar.get(Calendar.DAY_OF_MONTH)}/${
                                        calendar.get(
                                            Calendar.MONTH
                                        )
                                    }"
                                }
                            item(key = "date-$dateString-${calendar.get(Calendar.YEAR)}", contentType = 0) {
                                Text(
                                    text = dateString,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            lastDayOfYear = visitDayOfYear
                        }

                        item(key = item.url, contentType = 1) {
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
                    }
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

