/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.concept.storage.VisitType
import org.midorinext.android.R
import org.midorinext.android.browser.BrowserFragment
import org.midorinext.android.ext.requireComponents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private val historyItems = mutableStateOf<List<HistoryGroup>>(emptyList())
    private val isLoading = mutableStateOf(true)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HistoryScreen(
                    groups = historyItems.value,
                    isLoading = isLoading.value,
                    onBack = { closeHistory() },
                    onItemClick = { item -> openHistoryItem(item) },
                    onOpenInNewTab = { item -> openInNewTab(item, private = false) },
                    onOpenInPrivateTab = { item -> openInNewTab(item, private = true) },
                    onShareItem = { item -> shareItem(item) },
                    onDeleteItem = { item -> deleteItem(item) },
                    onDeleteAll = { deleteAllHistory() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val storage = requireComponents.core.historyStorage
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val visits = storage.getDetailedVisits(
                    start = 0,
                    end = System.currentTimeMillis(),
                    excludeTypes = listOf(VisitType.DOWNLOAD, VisitType.REDIRECT_PERMANENT, VisitType.REDIRECT_TEMPORARY),
                )
                val grouped = groupByDate(visits.sortedByDescending { it.visitTime })
                withContext(Dispatchers.Main) {
                    historyItems.value = grouped
                    isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    historyItems.value = emptyList()
                    isLoading.value = false
                }
            }
        }
    }

    private fun groupByDate(visits: List<VisitInfo>): List<HistoryGroup> {
        val now = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = todayStart - 86_400_000L
        val weekStart = todayStart - 7 * 86_400_000L
        val monthStart = todayStart - 30 * 86_400_000L

        val today = mutableListOf<VisitInfo>()
        val yesterday = mutableListOf<VisitInfo>()
        val thisWeek = mutableListOf<VisitInfo>()
        val thisMonth = mutableListOf<VisitInfo>()
        val older = mutableListOf<VisitInfo>()

        // Deduplicate by URL, keeping most recent visit
        val seen = mutableSetOf<String>()
        val deduped = visits.filter { visit ->
            val dominated = visit.url in seen
            seen.add(visit.url)
            !dominated
        }

        for (visit in deduped) {
            when {
                visit.visitTime >= todayStart -> today.add(visit)
                visit.visitTime >= yesterdayStart -> yesterday.add(visit)
                visit.visitTime >= weekStart -> thisWeek.add(visit)
                visit.visitTime >= monthStart -> thisMonth.add(visit)
                else -> older.add(visit)
            }
        }

        return buildList {
            if (today.isNotEmpty()) add(HistoryGroup(R.string.history_today, today))
            if (yesterday.isNotEmpty()) add(HistoryGroup(R.string.history_yesterday, yesterday))
            if (thisWeek.isNotEmpty()) add(HistoryGroup(R.string.history_this_week, thisWeek))
            if (thisMonth.isNotEmpty()) add(HistoryGroup(R.string.history_this_month, thisMonth))
            if (older.isNotEmpty()) add(HistoryGroup(R.string.history_older, older))
        }
    }

    private fun openHistoryItem(item: VisitInfo) {
        requireComponents.useCases.tabsUseCases.addTab(
            url = item.url,
            selectTab = true,
        )
        closeHistory()
    }

    private fun openInNewTab(item: VisitInfo, private: Boolean) {
        requireComponents.useCases.tabsUseCases.addTab(
            url = item.url,
            selectTab = true,
            private = private,
        )
        closeHistory()
    }

    private fun shareItem(item: VisitInfo) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, item.url)
            putExtra(Intent.EXTRA_SUBJECT, item.title ?: item.url)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.history_share)))
    }

    private fun deleteItem(item: VisitInfo) {
        val storage = requireComponents.core.historyStorage
        CoroutineScope(Dispatchers.IO).launch {
            storage.deleteVisitsFor(item.url)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), R.string.history_item_deleted, Toast.LENGTH_SHORT).show()
                loadHistory()
            }
        }
    }

    private fun deleteAllHistory() {
        val storage = requireComponents.core.historyStorage
        CoroutineScope(Dispatchers.IO).launch {
            storage.deleteEverything()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), R.string.history_deleted, Toast.LENGTH_SHORT).show()
                loadHistory()
            }
        }
    }

    private fun closeHistory() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_left, R.anim.slide_out_right,
                R.anim.slide_in_right, R.anim.slide_out_left,
            )
            replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }
}

data class HistoryGroup(
    val titleRes: Int,
    val items: List<VisitInfo>,
)

// ======================== Compose UI ========================

private val LightBg = Color(0xFFF5FAF7)
private val LightSurface = Color(0xFFE8F3EC)
private val LightTextPrimary = Color(0xFF0A1510)
private val LightTextSecondary = Color(0xFF3D5348)
private val LightDivider = Color(0x3304A469)

private val DarkBg = Color(0xFF0D1117)
private val DarkSurface = Color(0xFF121D2B)
private val DarkTextPrimary = Color.White
private val DarkTextSecondary = Color(0xFF8B949E)
private val DarkDivider = Color(0x1A06E290)

private val MidoriGreen = Color(0xFF04A469)
private val MidoriGreenBright = Color(0xFF06E290)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    groups: List<HistoryGroup>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onItemClick: (VisitInfo) -> Unit,
    onOpenInNewTab: (VisitInfo) -> Unit,
    onOpenInPrivateTab: (VisitInfo) -> Unit,
    onShareItem: (VisitInfo) -> Unit,
    onDeleteItem: (VisitInfo) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val divider = if (isDark) DarkDivider else LightDivider
    val accent = if (isDark) MidoriGreenBright else MidoriGreen

    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_back_24),
                    contentDescription = "Back",
                    tint = textPrimary,
                )
            }

            Text(
                text = stringResource(R.string.history_title),
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            if (groups.isNotEmpty()) {
                IconButton(onClick = { showDeleteAllDialog = true }) {
                    Icon(
                        painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_delete_24),
                        contentDescription = "Delete all",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )

        if (groups.isEmpty() && !isLoading) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_history),
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.history_empty),
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_empty_subtitle),
                    color = textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                for (group in groups) {
                    // Section header
                    item(key = "header_${group.titleRes}") {
                        Text(
                            text = stringResource(group.titleRes),
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }

                    items(group.items, key = { "${it.url}_${it.visitTime}" }) { visit ->
                        var showMenu by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onItemClick(visit) },
                                        onLongClick = { showMenu = true },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Favicon placeholder
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (visit.title?.firstOrNull()?.uppercase()
                                            ?: visit.url.removePrefix("https://").removePrefix("http://").firstOrNull()?.uppercase()
                                            ?: "?"),
                                        color = accent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = visit.title?.ifBlank { visit.url } ?: visit.url,
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row {
                                        Text(
                                            text = formatTime(visit.visitTime),
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = visit.url.removePrefix("https://").removePrefix("http://").split("/").firstOrNull() ?: "",
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                }

                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_menu_dots),
                                        contentDescription = "More",
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_open_new_tab)) },
                                    onClick = {
                                        showMenu = false
                                        onOpenInNewTab(visit)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_open_private_tab)) },
                                    onClick = {
                                        showMenu = false
                                        onOpenInPrivateTab(visit)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_share)) },
                                    onClick = {
                                        showMenu = false
                                        onShareItem(visit)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_delete_item), color = Color(0xFFE53935)) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteItem(visit)
                                    },
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 64.dp)
                                .height(1.dp)
                                .background(divider),
                        )
                    }
                }
            }
        }
    }

    // Delete all confirmation
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.history_delete_all),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(R.string.history_delete_all_confirm))
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllDialog = false
                    onDeleteAll()
                }) {
                    Text(stringResource(R.string.history_delete_all), color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.customize_addon_collection_cancel))
                }
            },
        )
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
