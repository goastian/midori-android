/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import org.midorinext.android.R
import org.midorinext.android.browser.BrowserFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadItem(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
)

class DownloadsFragment : Fragment() {

    private val downloadItems = mutableStateOf<List<DownloadItem>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                DownloadsScreen(
                    downloads = downloadItems.value,
                    onBack = { closeDownloads() },
                    onOpenFile = { item -> openFile(item) },
                    onShareFile = { item -> shareFile(item) },
                    onDeleteFile = { item -> deleteFile(item) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDownloads()
    }

    private fun loadDownloads() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadsDir?.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        downloadItems.value = files.map { file ->
            DownloadItem(
                file = file,
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified(),
            )
        }
    }

    private fun openFile(item: DownloadItem) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                item.file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(item.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No app to open this file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(item: DownloadItem) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                item.file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(item.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.downloads_share)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot share file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(item: DownloadItem) {
        if (item.file.delete()) {
            Toast.makeText(requireContext(), R.string.downloads_deleted, Toast.LENGTH_SHORT).show()
            loadDownloads()
        }
    }

    private fun closeDownloads() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_left, R.anim.slide_out_right,
                R.anim.slide_in_right, R.anim.slide_out_left,
            )
            replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "*/*"
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
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onBack: () -> Unit,
    onOpenFile: (DownloadItem) -> Unit,
    onShareFile: (DownloadItem) -> Unit,
    onDeleteFile: (DownloadItem) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val divider = if (isDark) DarkDivider else LightDivider
    val accent = if (isDark) MidoriGreenBright else MidoriGreen

    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }

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
                text = stringResource(R.string.downloads_title),
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )

        if (downloads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_downloads),
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.downloads_empty),
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.downloads_empty_subtitle),
                    color = textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloads, key = { it.file.absolutePath }) { item ->
                    var showMenu by remember { mutableStateOf(false) }

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenFile(item) },
                                    onLongClick = { showMenu = true },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // File type icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = getFileExtIcon(item.name),
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row {
                                    Text(
                                        text = formatFileSize(item.size),
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatDate(item.lastModified),
                                        color = textSecondary,
                                        fontSize = 11.sp,
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
                                text = { Text(stringResource(R.string.downloads_open)) },
                                onClick = {
                                    showMenu = false
                                    onOpenFile(item)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_share)) },
                                onClick = {
                                    showMenu = false
                                    onShareFile(item)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_delete), color = Color(0xFFE53935)) },
                                onClick = {
                                    showMenu = false
                                    deleteTarget = item
                                },
                            )
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 70.dp)
                            .height(1.dp)
                            .background(divider),
                    )
                }
            }
        }
    }

    // Delete confirmation
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(R.string.downloads_delete_confirm))
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    onDeleteFile(item)
                }) {
                    Text(stringResource(R.string.downloads_delete), color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.customize_addon_collection_cancel))
                }
            },
        )
    }
}

private fun getFileExtIcon(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").uppercase()
    return if (ext.length <= 4) ext else ext.take(3)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
