/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.images.ImageLoader
import mozilla.components.support.test.robolectric.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.R
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.selection.SelectionHolder
import org.midorinext.android.tabstray.TabsTrayStore
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import org.midorinext.android.ext.components

@RunWith(MidoriRobolectricTestRunner::class)
class AbstractBrowserTabViewHolderTest {

    val store = TabsTrayStore()
    val browserStore = BrowserStore()
    val interactor = mockk<BrowserTrayInteractor>(relaxed = true)

    @Test
    fun `WHEN itemView is clicked THEN interactor invokes open`() {
        every { testContext.components.publicSuffixList } returns PublicSuffixList(testContext)
        val view = LayoutInflater.from(testContext).inflate(R.layout.tab_tray_item, null)
        val holder = TestTabTrayViewHolder(
            view,
            mockk(relaxed = true),
            store,
            null,
            browserStore,
            interactor
        )

        holder.bind(createTab(url = "url"), false, mockk(), mockk())

        holder.itemView.performClick()

        verify { interactor.onTabSelected(any(), null) }
    }

    @Test
    fun `WHEN itemView is clicked with a selection holder THEN the select holder is invoked`() {
        every { testContext.components.publicSuffixList } returns PublicSuffixList(testContext)
        val view = LayoutInflater.from(testContext).inflate(R.layout.tab_tray_item, null)
        val selectionHolder = TestSelectionHolder(emptySet())
        val holder = TestTabTrayViewHolder(
            view,
            mockk(relaxed = true),
            store,
            selectionHolder,
            browserStore,
            interactor
        )

        val tab = createTab(url = "url")
        holder.bind(tab, false, mockk(), mockk())

        holder.itemView.performClick()

        verify { interactor.onMultiSelectClicked(tab, any(), null) }
    }

    @Test
    fun `WHEN the current media state is paused AND playPause button is clicked THEN the media is played`() {
        every { testContext.components.publicSuffixList } returns PublicSuffixList(testContext)
        val view = LayoutInflater.from(testContext).inflate(R.layout.tab_tray_item, null)
        val mediaSessionController = mockk<MediaSession.Controller>(relaxed = true)
        val mediaTab = createTab(
            url = "url",
            mediaSessionState = MediaSessionState(
                mediaSessionController,
                playbackState = MediaSession.PlaybackState.PAUSED
            )
        )
        val mediaBrowserStore = BrowserStore(
            initialState =
            BrowserState(listOf(mediaTab))
        )
        val holder = TestTabTrayViewHolder(
            view,
            mockk(relaxed = true),
            store,
            TestSelectionHolder(emptySet()),
            mediaBrowserStore,
            interactor
        )

        holder.bind(mediaTab, false, mockk(), mockk())

        holder.itemView.findViewById<ImageButton>(R.id.play_pause_button).performClick()

        verify { mediaSessionController.play() }
    }

    @Test
    fun `WHEN the current media state is playing AND playPause button is clicked THEN the media is paused`() {
        every { testContext.components.publicSuffixList } returns PublicSuffixList(testContext)
        val view = LayoutInflater.from(testContext).inflate(R.layout.tab_tray_item, null)
        val mediaSessionController = mockk<MediaSession.Controller>(relaxed = true)
        val mediaTab = createTab(
            url = "url",
            mediaSessionState = MediaSessionState(
                mediaSessionController,
                playbackState = MediaSession.PlaybackState.PLAYING
            )
        )
        val mediaBrowserStore = BrowserStore(
            initialState =
            BrowserState(listOf(mediaTab))
        )
        val holder = TestTabTrayViewHolder(
            view,
            mockk(relaxed = true),
            store,
            TestSelectionHolder(emptySet()),
            mediaBrowserStore,
            interactor
        )

        holder.bind(mediaTab, false, mockk(), mockk())

        holder.itemView.findViewById<ImageButton>(R.id.play_pause_button).performClick()

        verify { mediaSessionController.pause() }
    }

    @Suppress("LongParameterList")
    class TestTabTrayViewHolder(
        itemView: View,
        imageLoader: ImageLoader,
        trayStore: TabsTrayStore,
        selectionHolder: SelectionHolder<TabSessionState>?,
        store: BrowserStore,
        override val browserTrayInteractor: BrowserTrayInteractor
    ) : AbstractBrowserTabViewHolder(itemView, imageLoader, trayStore, selectionHolder, store) {
        override val thumbnailSize: Int
            get() = 30

        override fun updateSelectedTabIndicator(showAsSelected: Boolean) {
            // do nothing
        }
    }

    class TestSelectionHolder(
        private val testItems: Set<TabSessionState>
    ) : SelectionHolder<TabSessionState> {
        override val selectedItems: Set<TabSessionState>
            get() {
                invoked = true
                return testItems
            }

        var invoked = false
    }
}
