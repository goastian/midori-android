package org.midorinext.android.ui.browser

import android.app.Activity
import android.view.KeyEvent
import mozilla.components.browser.state.store.BrowserStore

/**
 * Firefox 150+ Feature: CloseWatcher API Support
 * 
 * Implements JavaScript CloseWatcher API listener for proper
 * handling of back button and dialog closures
 * 
 * Reference: https://github.com/WICG/close-watcher
 */
class CloseWatcherHandler(
    private val activity: Activity,
    private val store: BrowserStore
) {
    
    private val closers: MutableList<CloseListener> = mutableListOf()
    private var isProcessing = false
    
    fun interface CloseListener {
        fun onClose(): Boolean
    }
    
    /**
     * Register a close listener (typically from a dialog or popover)
     */
    fun registerCloseListener(listener: CloseListener) {
        closers.add(listener)
    }
    
    /**
     * Unregister a close listener
     */
    fun unregisterCloseListener(listener: CloseListener) {
        closers.remove(listener)
    }
    
    /**
     * Handle back navigation with CloseWatcher protocol
     * 
     * The close watcher protocol states that:
     * 1. The most recently added close watcher should be signaled to close first
     * 2. If it handles the close request, stop bubbling
     * 3. Otherwise, continue to the next dialog/watcher
     */
    fun handleBackPress(): Boolean {
        if (closers.isEmpty()) {
            return false
        }
        
        if (isProcessing) {
            return false
        }
        
        isProcessing = true
        try {
            // Process in reverse order (LIFO - Last In First Out)
            for (i in closers.size - 1 downTo 0) {
                val listener = closers.getOrNull(i)
                if (listener?.onClose() == true) {
                    // Listener handled the close request
                    closers.removeAt(i)
                    return true
                }
            }
            return false
        } finally {
            isProcessing = false
        }
    }
    
    /**
     * Clear all registered listeners (e.g., when activity is destroyed)
     */
    fun clear() {
        closers.clear()
    }
    
    /**
     * Check if there are any active listeners
     */
    fun hasActiveListeners(): Boolean = closers.isNotEmpty()
    
    /**
     * Get the count of registered listeners
     */
    fun listenerCount(): Int = closers.size
}

/**
 * Default close listeners for common scenarios
 */
object DefaultCloseListeners {
    /**
     * Create a close listener that closes a dialog
     */
    fun createDialogCloseListener(onClose: () -> Unit): CloseWatcherHandler.CloseListener {
        return CloseWatcherHandler.CloseListener { 
            onClose()
            true // Indicate close was handled
        }
    }
    
    /**
     * Create a close listener for an overlay/popover
     */
    fun createOverlayCloseListener(
        isVisible: () -> Boolean,
        onClose: () -> Unit
    ): CloseWatcherHandler.CloseListener {
        return CloseWatcherHandler.CloseListener {
            if (isVisible()) {
                onClose()
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Create a close listener that delegates to a callback
     */
    fun createDelegateListener(
        shouldClose: () -> Boolean,
        onClose: () -> Unit
    ): CloseWatcherHandler.CloseListener {
        return CloseWatcherHandler.CloseListener {
            if (shouldClose()) {
                onClose()
                true
            } else {
                false
            }
        }
    }
}
