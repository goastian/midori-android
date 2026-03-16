/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.collections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import org.midorinext.android.browser.BrowserFragment
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import org.midorinext.android.R
import org.midorinext.android.ext.requireComponents

class CollectionsFragment : Fragment() {

    private lateinit var storage: CollectionStorage
    private val collections = mutableStateOf<List<TabCollection>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        storage = CollectionStorage(requireContext())

        return ComposeView(requireContext()).apply {
            setContent {
                CollectionsScreen(
                    collections = collections.value,
                    onBack = { closeCollections() },
                    onOpenTab = { tab -> openTab(tab) },
                    onOpenAllTabs = { collection -> openAllTabs(collection) },
                    onDeleteCollection = { collection -> deleteCollection(collection) },
                    onRemoveTab = { collection, tab -> removeTab(collection, tab) },
                    onRenameCollection = { collection, newName -> renameCollection(collection, newName) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        collections.value = storage.getCollections()
    }

    private fun openTab(tab: CollectionTab) {
        requireComponents.useCases.tabsUseCases.addTab(
            url = tab.url,
            selectTab = true,
        )
        closeCollections()
    }

    private fun openAllTabs(collection: TabCollection) {
        for (tab in collection.tabs) {
            requireComponents.useCases.tabsUseCases.addTab(
                url = tab.url,
                selectTab = false,
            )
        }
        if (collection.tabs.isNotEmpty()) {
            requireComponents.useCases.tabsUseCases.addTab(
                url = collection.tabs.first().url,
                selectTab = true,
            )
        }
        Toast.makeText(
            requireContext(),
            "${collection.tabs.size} tabs opened",
            Toast.LENGTH_SHORT,
        ).show()
        closeCollections()
    }

    private fun closeCollections() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_left, R.anim.slide_out_right,
                R.anim.slide_in_right, R.anim.slide_out_left,
            )
            replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }

    private fun deleteCollection(collection: TabCollection) {
        storage.deleteCollection(collection.id)
        collections.value = storage.getCollections()
        Toast.makeText(requireContext(), R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
    }

    private fun removeTab(collection: TabCollection, tab: CollectionTab) {
        storage.removeTabFromCollection(collection.id, tab.url)
        collections.value = storage.getCollections()
    }

    private fun renameCollection(collection: TabCollection, newName: String) {
        storage.saveCollection(collection.copy(title = newName))
        collections.value = storage.getCollections()
    }

    companion object {
        fun saveCurrentTabs(
            fragment: Fragment,
            collectionName: String,
        ) {
            val store = fragment.requireComponents.core.store
            val tabs = store.state.tabs.filter { !it.content.private }
            if (tabs.isEmpty()) {
                Toast.makeText(fragment.requireContext(), "No tabs to save", Toast.LENGTH_SHORT).show()
                return
            }
            val collectionTabs = tabs.map {
                CollectionTab(
                    title = it.content.title.ifBlank { it.content.url },
                    url = it.content.url,
                )
            }
            val collection = TabCollection(
                id = java.util.UUID.randomUUID().toString(),
                title = collectionName,
                tabs = collectionTabs,
            )
            val storage = CollectionStorage(fragment.requireContext())
            storage.saveCollection(collection)
            Toast.makeText(fragment.requireContext(), R.string.collection_saved, Toast.LENGTH_SHORT).show()
        }
    }
}

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
fun CollectionsScreen(
    collections: List<TabCollection>,
    onBack: () -> Unit,
    onOpenTab: (CollectionTab) -> Unit,
    onOpenAllTabs: (TabCollection) -> Unit,
    onDeleteCollection: (TabCollection) -> Unit,
    onRemoveTab: (TabCollection, CollectionTab) -> Unit,
    onRenameCollection: (TabCollection, String) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val divider = if (isDark) DarkDivider else LightDivider
    val accent = if (isDark) MidoriGreenBright else MidoriGreen

    var editingCollection by remember { mutableStateOf<TabCollection?>(null) }
    var deletingCollection by remember { mutableStateOf<TabCollection?>(null) }

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
                text = stringResource(R.string.collections_title),
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )

        if (collections.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.collections_empty),
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.collections_empty_subtitle),
                    color = textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(collections, key = { it.id }) { collection ->
                    var expanded by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                    ) {
                        // Collection header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_folder),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = collection.title,
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${collection.tabs.size} tabs",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                )
                            }

                            // Open all
                            IconButton(onClick = { onOpenAllTabs(collection) }) {
                                Icon(
                                    painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_open_in),
                                    contentDescription = stringResource(R.string.collection_open_all),
                                    tint = accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Edit
                            IconButton(onClick = { editingCollection = collection }) {
                                Icon(
                                    painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_edit_24),
                                    contentDescription = "Edit",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            // Delete
                            IconButton(onClick = { deletingCollection = collection }) {
                                Icon(
                                    painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_delete_24),
                                    contentDescription = stringResource(R.string.collection_delete),
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Expanded tabs
                        if (expanded) {
                            for (tab in collection.tabs) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenTab(tab) }
                                        .padding(start = 70.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tab.title,
                                            color = textPrimary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = tab.url,
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveTab(collection, tab) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_cross_24),
                                            contentDescription = "Remove",
                                            tint = textSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(divider),
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    editingCollection?.let { collection ->
        var name by remember { mutableStateOf(collection.title) }
        val accent2 = if (isSystemInDarkTheme()) MidoriGreenBright else MidoriGreen

        AlertDialog(
            onDismissRequest = { editingCollection = null },
            title = { Text("Rename collection", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.collection_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent2,
                        cursorColor = accent2,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onRenameCollection(collection, name.trim())
                        editingCollection = null
                    }
                }) {
                    Text(stringResource(R.string.collection_save), color = accent2)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCollection = null }) {
                    Text(stringResource(R.string.customize_addon_collection_cancel))
                }
            },
        )
    }

    // Delete confirmation dialog
    deletingCollection?.let { collection ->
        AlertDialog(
            onDismissRequest = { deletingCollection = null },
            title = { Text(stringResource(R.string.collection_delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.collection_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCollection(collection)
                    deletingCollection = null
                }) {
                    Text(stringResource(R.string.bookmark_delete), color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCollection = null }) {
                    Text(stringResource(R.string.customize_addon_collection_cancel))
                }
            },
        )
    }
}
