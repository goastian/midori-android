package org.midorinext.android.ui.readinglist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.storage.readinglist.ReadingListItem
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun ReadingListScreen(
    onBrowse: () -> Unit,
    viewModel: ReadingListViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    BackHandler { onBrowse() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(title = stringResource(R.string.reading_list_title))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::updateSearchQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.reading_list_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (items.isEmpty()) {
            EmptyPagePlaceholder(
                icon = R.drawable.icons_bookmark,
                title = stringResource(R.string.reading_list_empty_title),
                subtitle = stringResource(R.string.reading_list_empty_summary)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ReadingListCard(
                        item = item,
                        onOpen = {
                            viewModel.open(item)
                            onBrowse()
                        },
                        onToggleRead = { viewModel.toggleRead(item) },
                        onDelete = { viewModel.delete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingListCard(
    item: ReadingListItem,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.offlinePath == null) {
                    stringResource(R.string.reading_list_offline_later)
                } else {
                    stringResource(R.string.reading_list_offline_available)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                AssistChip(
                    onClick = onOpen,
                    label = { Text(stringResource(R.string.reading_list_open)) }
                )
                AssistChip(
                    onClick = onToggleRead,
                    label = {
                        Text(
                            stringResource(
                                if (item.read) R.string.reading_list_mark_unread
                                else R.string.reading_list_mark_read
                            )
                        )
                    }
                )
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.reading_list_remove))
                }
            }
        }
    }
}
