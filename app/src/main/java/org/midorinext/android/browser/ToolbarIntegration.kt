/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.browser

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.menu2.BrowserMenuController
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.display.DisplayToolbar
import mozilla.components.concept.menu.MenuController
import mozilla.components.concept.menu.candidate.CompoundMenuCandidate
import mozilla.components.concept.menu.candidate.ContainerStyle
import mozilla.components.concept.menu.candidate.DrawableMenuIcon
import mozilla.components.concept.menu.candidate.MenuCandidate
import mozilla.components.concept.menu.candidate.RowMenuCandidate
import mozilla.components.concept.menu.candidate.SmallMenuCandidate
import mozilla.components.concept.menu.candidate.TextMenuCandidate
import mozilla.components.feature.pwa.WebAppUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.toolbar.ToolbarAutocompleteFeature
import mozilla.components.feature.toolbar.ToolbarFeature
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.ktx.android.view.ImeInsetsSynchronizer
import org.midorinext.android.R
import org.midorinext.android.addons.AddonsActivity
import org.midorinext.android.ext.components
import org.midorinext.android.ext.share
import org.midorinext.android.settings.SettingsActivity
import org.midorinext.android.tabs.synced.SyncedTabsActivity

@Suppress("LongParameterList")
class ToolbarIntegration(
    private val context: Context,
    toolbar: BrowserToolbar,
    toolbarParentView: View,
    historyStorage: PlacesHistoryStorage,
    store: BrowserStore,
    private val sessionUseCases: SessionUseCases,
    private val tabsUseCases: TabsUseCases,
    private val webAppUseCases: WebAppUseCases,
    sessionId: String? = null,
    private val onBookmarkTapped: ((String, String) -> Unit)? = null,
    private val onShowBookmarks: (() -> Unit)? = null,
    private val onShowCollections: (() -> Unit)? = null,
    private val onShowHistory: (() -> Unit)? = null,
    private val onShowDownloads: (() -> Unit)? = null,
) : LifecycleAwareFeature,
    UserInteractionHandler {
    private val shippedDomainsProvider = ShippedDomainsProvider().also {
        it.initialize(context)
    }

    private val scope = MainScope()

    private fun menuToolbar(session: SessionState?): RowMenuCandidate {
        val tint = ContextCompat.getColor(context, R.color.icons)

        val forward = SmallMenuCandidate(
            contentDescription = context.getString(R.string.menu_forward),
            icon = DrawableMenuIcon(
                context,
                mozilla.components.ui.icons.R.drawable.mozac_ic_forward_24,
                tint = tint,
            ),
            containerStyle = ContainerStyle(
                isEnabled = session?.content?.canGoForward == true,
            ),
        ) {
            sessionUseCases.goForward.invoke()
        }

        val refresh = SmallMenuCandidate(
            contentDescription = context.getString(R.string.menu_refresh),
            icon = DrawableMenuIcon(
                context,
                mozilla.components.ui.icons.R.drawable.mozac_ic_arrow_clockwise_24,
                tint = tint,
            ),
        ) {
            sessionUseCases.reload.invoke()
        }

        val stop = SmallMenuCandidate(
            contentDescription = context.getString(R.string.menu_stop),
            icon = DrawableMenuIcon(
                context,
                mozilla.components.ui.icons.R.drawable.mozac_ic_stop,
                tint = tint,
            ),
        ) {
            sessionUseCases.stopLoading.invoke()
        }

        return RowMenuCandidate(listOf(forward, refresh, stop))
    }

    private fun sessionMenuItems(sessionState: SessionState): List<MenuCandidate> =
        listOfNotNull(
            menuToolbar(sessionState),
            TextMenuCandidate(context.getString(R.string.menu_share)) {
                val url = sessionState.content.url
                context.share(url)
            },
            TextMenuCandidate("★ " + context.getString(R.string.bookmark_add_page)) {
                onBookmarkTapped?.invoke(
                    sessionState.content.title,
                    sessionState.content.url,
                )
            },
            CompoundMenuCandidate(
                text = context.getString(R.string.menu_request_desktop_site),
                isChecked = sessionState.content.desktopMode,
                end = CompoundMenuCandidate.ButtonType.SWITCH,
            ) { checked ->
                sessionUseCases.requestDesktopSite.invoke(checked)
            },
            if (webAppUseCases.isPinningSupported()) {
                TextMenuCandidate(
                    text = context.getString(R.string.menu_add_to_homescreen),
                    containerStyle = ContainerStyle(
                        isVisible = webAppUseCases.isPinningSupported(),
                    ),
                ) {
                    scope.launch { webAppUseCases.addToHomescreen() }
                }
            } else {
                null
            },
            TextMenuCandidate(
                text = context.getString(R.string.menu_find_in_page),
            ) {
                FindInPageIntegration.launch?.invoke()
            },
        )

    private fun menuItems(sessionState: SessionState?): List<MenuCandidate> {
        val sessionMenuItems = if (sessionState != null) {
            sessionMenuItems(sessionState)
        } else {
            emptyList()
        }

        return sessionMenuItems + listOf(
            TextMenuCandidate(text = context.getString(R.string.bookmarks_title)) {
                onShowBookmarks?.invoke()
            },
            TextMenuCandidate(text = context.getString(R.string.collections_title)) {
                onShowCollections?.invoke()
            },
            TextMenuCandidate(text = context.getString(R.string.history_title)) {
                onShowHistory?.invoke()
            },
            TextMenuCandidate(text = context.getString(R.string.downloads_title)) {
                onShowDownloads?.invoke()
            },
            TextMenuCandidate(text = context.getString(R.string.menu_addons)) {
                val intent = Intent(context, AddonsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
            TextMenuCandidate(text = context.getString(R.string.settings)) {
                val intent = Intent(context, SettingsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
        )
    }

    private val browserMenuController: MenuController = BrowserMenuController()

    init {
        toolbar.display.apply {
            indicators = listOf(
                DisplayToolbar.Indicators.SECURITY,
                DisplayToolbar.Indicators.TRACKING_PROTECTION,
            )
            displayIndicatorSeparator = true
            menuController = browserMenuController
            hint = context.getString(R.string.toolbar_hint)

            val iconColor = ContextCompat.getColor(context, R.color.toolbar_icon_color)
            val hintColor = ContextCompat.getColor(context, R.color.text_tertiary)
            val textColor = ContextCompat.getColor(context, R.color.text_primary)
            val separatorColor = ContextCompat.getColor(context, R.color.text_tertiary)

            colors = DisplayToolbar.Colors(
                siteInfoIconSecure = iconColor,
                siteInfoIconInsecure = iconColor,
                siteInfoIconLocalPdf = iconColor,
                emptyIcon = iconColor,
                menu = iconColor,
                hint = hintColor,
                title = textColor,
                text = textColor,
                trackingProtection = iconColor,
                separator = separatorColor,
                highlight = iconColor,
            )

            setUrlBackground(
                ResourcesCompat.getDrawable(context.resources, R.drawable.url_background, context.theme),
            )
        }

        toolbar.edit.apply {
            hint = context.getString(R.string.toolbar_hint)
        }

        ToolbarAutocompleteFeature(toolbar).apply {
            updateAutocompleteProviders(
                listOf(historyStorage, shippedDomainsProvider),
            )
        }

        ImeInsetsSynchronizer.setup(
            targetView = toolbar,
            onIMEAnimationStarted = { isKeyboardShowingUp, keyboardHeight ->
                // If the keyboard is hiding have the engine view immediately expand to the entire height of the
                // screen and ensure the toolbar is shown above keyboard before both would be animated down.
                if (!isKeyboardShowingUp) {
                    (toolbarParentView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = 0
                    (toolbar.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = keyboardHeight
                    toolbarParentView.requestLayout()
                }
            },
            onIMEAnimationFinished = { isKeyboardShowingUp, keyboardHeight ->
                // If the keyboard is showing up keep the engine view covering the entire height
                // of the screen until the animation is finished to avoid reflowing the web content
                // together with the keyboard animation in a short burst of updates.
                if (isKeyboardShowingUp || keyboardHeight == 0) {
                    (toolbarParentView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = keyboardHeight
                    (toolbar.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = 0
                    toolbarParentView.requestLayout()
                }
            },
        )

        scope.launch {
            store
                .flow()
                .map { state -> state.selectedTab }
                .distinctUntilChanged()
                .collect { tab ->
                    browserMenuController.submitList(menuItems(tab))
                }
        }
    }

    private val toolbarFeature: ToolbarFeature = ToolbarFeature(
        toolbar,
        context.components.core.store,
        context.components.useCases.sessionUseCases.loadUrl,
        { searchTerms ->
            context.components.useCases.searchUseCases.defaultSearch.invoke(
                searchTerms = searchTerms,
                searchEngine = null,
                parentSessionId = null,
            )
        },
        sessionId,
    )

    override fun start() {
        toolbarFeature.start()
    }

    override fun stop() {
        toolbarFeature.stop()
    }

    override fun onBackPressed(): Boolean = toolbarFeature.onBackPressed()
}
