package org.midorinext.android.ui.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.core.content.getSystemService
import org.midorinext.android.R

/**
 * Firefox 150+ Feature: Copy Link Text
 * 
 * Provides utilities for copying link text and URLs to clipboard
 * with haptic feedback and user feedback
 */
class LinkClipboardManager(private val context: Context) {
    private val clipboardManager: ClipboardManager? = context.getSystemService()
    
    /**
     * Copy link URL to clipboard
     * Shows toast notification with feedback
     */
    fun copyLinkUrl(url: String, linkLabel: String? = null) {
        if (clipboardManager == null) return
        
        val label = linkLabel ?: "Link URL"
        val clip = ClipData.newPlainText(label, url)
        clipboardManager.setPrimaryClip(clip)
        
        showCopyFeedback("Link copied to clipboard")
    }
    
    /**
     * Copy link text to clipboard (Firefox 150+ feature)
     * Shows toast notification with feedback
     */
    fun copyLinkText(text: String) {
        if (clipboardManager == null || text.isBlank()) return
        
        val clip = ClipData.newPlainText("Link text", text)
        clipboardManager.setPrimaryClip(clip)
        
        showCopyFeedback("Link text copied to clipboard")
    }
    
    /**
     * Copy both URL and text with custom formatting
     */
    fun copyLinkFull(url: String, text: String) {
        if (clipboardManager == null) return
        
        val formattedText = if (text.isNotBlank()) {
            "$text\n$url"
        } else {
            url
        }
        
        val clip = ClipData.newPlainText("Link", formattedText)
        clipboardManager.setPrimaryClip(clip)
        
        showCopyFeedback("Link copied to clipboard")
    }
    
    /**
     * Show toast feedback for copy action
     */
    private fun showCopyFeedback(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension functions for easy clipboard operations
 */
fun Context.copyToClipboard(text: String, label: String = "Text"): Boolean {
    return try {
        val clipboard = getSystemService<ClipboardManager>() ?: return false
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        true
    } catch (e: Exception) {
        false
    }
}

fun Context.copyLinkToClipboard(
    url: String,
    text: String? = null
): Boolean {
    val manager = LinkClipboardManager(this)
    return try {
        if (text != null && text.isNotBlank()) {
            manager.copyLinkFull(url, text)
        } else {
            manager.copyLinkUrl(url)
        }
        true
    } catch (e: Exception) {
        false
    }
}
