package org.midorinext.android.ui.browser

import android.content.Context
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import org.midorinext.android.R

/**
 * Firefox 150+ Feature: Copy Link Text Context Menu
 *
 * Provides ContextMenuCandidate instances for:
 * - Copy link text (new in Firefox 150+)
 * - Copy link URL
 *
 * In GeckoView a long-pressed hyperlink emits HitResult.UNKNOWN
 * with `src` = the href and `linkText` = the visible text.
 */
object LinkContextMenuExtensions {

    /** "Copy link text" — only shown when the link has visible text */
    fun createCopyLinkTextCandidate(context: Context): ContextMenuCandidate =
        ContextMenuCandidate(
            id = "copy_link_text_ff150",
            label = context.getString(R.string.context_menu_copy_link_text),
            showFor = { _: SessionState, hitResult: HitResult ->
                hitResult is HitResult.UNKNOWN && !hitResult.linkText.isNullOrBlank()
            },
            action = { _: SessionState, hitResult: HitResult ->
                (hitResult as? HitResult.UNKNOWN)?.linkText?.takeIf { it.isNotBlank() }?.let {
                    LinkClipboardManager(context).copyLinkText(it)
                }
            }
        )

    /** "Copy link URL" */
    fun createCopyLinkUrlCandidate(context: Context): ContextMenuCandidate =
        ContextMenuCandidate(
            id = "copy_link_url_ff150",
            label = context.getString(R.string.context_menu_copy_link),
            showFor = { _: SessionState, hitResult: HitResult ->
                hitResult is HitResult.UNKNOWN
            },
            action = { _: SessionState, hitResult: HitResult ->
                (hitResult as? HitResult.UNKNOWN)?.src?.let {
                    LinkClipboardManager(context).copyLinkUrl(it)
                }
            }
        )

    /** "Copy link text and URL" — only shown when visible text exists */
    fun createCopyLinkFullCandidate(context: Context): ContextMenuCandidate =
        ContextMenuCandidate(
            id = "copy_link_full_ff150",
            label = context.getString(R.string.context_menu_copy_link_and_text),
            showFor = { _: SessionState, hitResult: HitResult ->
                hitResult is HitResult.UNKNOWN && !hitResult.linkText.isNullOrBlank()
            },
            action = { _: SessionState, hitResult: HitResult ->
                (hitResult as? HitResult.UNKNOWN)?.let {
                    LinkClipboardManager(context).copyLinkFull(it.src, it.linkText ?: "")
                }
            }
        )

    /** Returns all three link candidates in priority order */
    fun allLinkCandidates(context: Context): List<ContextMenuCandidate> = listOf(
        createCopyLinkTextCandidate(context),
        createCopyLinkUrlCandidate(context),
        createCopyLinkFullCandidate(context)
    )
}
