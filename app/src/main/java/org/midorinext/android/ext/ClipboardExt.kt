package org.midorinext.android.ext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copy text to the system clipboard
 */
fun Context.copyToClipboard(text: String, label: String = "text") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Get text from the system clipboard
 */
fun Context.getFromClipboard(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}
