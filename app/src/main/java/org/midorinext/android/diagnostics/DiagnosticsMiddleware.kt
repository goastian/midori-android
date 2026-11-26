package org.midorinext.android.diagnostics

import io.sentry.Sentry
import io.sentry.Breadcrumb
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.CrashAction
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

class DiagnosticsMiddleware : Middleware<BrowserState, BrowserAction> {
    override fun invoke(store: Store<BrowserState, BrowserAction>, next: (BrowserAction) -> Unit, action: BrowserAction) {
        val wasCrashed = (action as? CrashAction.SessionCrashedAction)?.let { crash ->
            store.state.tabs.find { it.id == crash.tabId }?.engineState?.crashed
                ?: store.state.customTabs.find { it.id == crash.tabId }?.engineState?.crashed
        }
        val downloadFailed = action is DownloadAction.UpdateDownloadAction &&
            action.download.status == DownloadState.Status.FAILED &&
            store.state.downloads[action.download.id]?.status != DownloadState.Status.FAILED
        next(action)
        if (downloadFailed) DiagnosticsErrors.downloadFailed()
        DiagnosticsRuntime.tabs(store.state.tabs.size + store.state.customTabs.size)
        if (action is CrashAction.SessionCrashedAction && wasCrashed == false) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "midori.gecko"
                message = "content_crashed"
            })
        } else if (action is CrashAction.RestoreCrashedSessionAction) {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "midori.gecko"
                message = "content_recovered"
            })
        }
    }
}
