package org.midorinext.android.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import org.midorinext.android.ui.widgets.ScreenHeader
import org.midorinext.android.ui.widgets.YesNoDialog

@Composable
fun HistoryScreen(
    historyViewModel: HistoryViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }

    val visits = historyViewModel.historyItems.collectAsLazyPagingItems()
    val visitsEmpty = (visits.itemCount == 0
            && visits.loadState.append != LoadState.Loading
            && visits.loadState.prepend != LoadState.Loading
            && visits.loadState.refresh != LoadState.Loading)

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = stringResource(id = R.string.history),
            scrollableState = lazyListState,
            actions = {
                if (!visitsEmpty) {
                    IconButton(onClick = { showDeleteAllConfirmation = true }) {
                        Icon(painter = painterResource(id = R.drawable.icons_trash), contentDescription = "Delete history")
                    }
                }
            }
        )

        if (!visitsEmpty) {
            HistoryList(
                visits = visits,
                historyViewModel = historyViewModel,
                lazyListState = lazyListState,
                onItemSelected = { visit, private ->
                    historyViewModel.openNewTab.invoke(url = visit.url, selectTab = true, private = private)
                    onClose()
                }
            )
        } else {
            EmptyPagePlaceholder(
                icon = R.drawable.icons_history,
                title = stringResource(id = R.string.history_empty_title),
                subtitle = stringResource(id = R.string.history_empty_message)
            )
        }
    }

    if (showDeleteAllConfirmation) {
        YesNoDialog(
            onDismissRequest = { showDeleteAllConfirmation = false },
            title = stringResource(id = R.string.history_clear_all_confirm_title),
            description = stringResource(id = R.string.history_clear_all_confirm_message),
            yesText = stringResource(id = R.string.history_clear_all_confirm_ok),
            onYes = {
                historyViewModel.deleteAllHistory()
                showDeleteAllConfirmation = false
            },
            onNo = { showDeleteAllConfirmation = false }
        )
    }
}