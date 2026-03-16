/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.privateTabs
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.tabstray.TabsAdapter
import mozilla.components.browser.tabstray.TabsTray
import mozilla.components.browser.tabstray.TabsTrayStyling
import mozilla.components.browser.tabstray.ViewHolderProvider
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.feature.tabs.tabstray.TabsFeature
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.feature.UserInteractionHandler
import org.midorinext.android.R
import org.midorinext.android.browser.BrowserFragment
import org.midorinext.android.ext.components
import org.midorinext.android.ext.requireComponents

/**
 * A fragment for displaying the tabs tray with a modern grid layout.
 */
class TabsTrayFragment :
    Fragment(),
    UserInteractionHandler {
    private var tabsFeature: TabsFeature? = null
    private var isPrivateMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_tabstray, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val trayAdapter = createAndSetupTabsTray(requireContext())

        tabsFeature = TabsFeature(
            trayAdapter,
            requireComponents.core.store,
            ::closeTabsTray,
        ) { !it.content.private }

        setupHeader(view)
        setupSegmentedPanel(view)
        setupBottomBar(view)
        observeTabCount()
    }

    override fun onStart() {
        super.onStart()
        tabsFeature?.start()
    }

    override fun onStop() {
        super.onStop()
        tabsFeature?.stop()
    }

    override fun onBackPressed(): Boolean {
        closeTabsTray()
        return true
    }

    private fun closeTabsTray() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_left, R.anim.slide_out_right,
                R.anim.slide_in_right, R.anim.slide_out_left,
            )
            replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }

    private fun setupHeader(view: View) {
        view.findViewById<ImageButton>(R.id.tabTrayBackButton).setOnClickListener {
            closeTabsTray()
        }
        updateTitle()
    }

    private fun setupSegmentedPanel(view: View) {
        val normalTab = view.findViewById<TextView>(R.id.tabPanelNormal)
        val privateTab = view.findViewById<TextView>(R.id.tabPanelPrivate)

        normalTab.setOnClickListener {
            if (isPrivateMode) {
                isPrivateMode = false
                updatePanelSelection(normalTab, privateTab)
                tabsFeature?.filterTabs { !it.content.private }
                updateThemeForMode()
                updateTitle()
            }
        }

        privateTab.setOnClickListener {
            if (!isPrivateMode) {
                isPrivateMode = true
                updatePanelSelection(normalTab, privateTab)
                tabsFeature?.filterTabs { it.content.private }
                updateThemeForMode()
                updateTitle()
            }
        }
    }

    private fun updatePanelSelection(normalTab: TextView, privateTab: TextView) {
        if (isPrivateMode) {
            normalTab.setBackgroundResource(R.drawable.bg_panel_tab_unselected)
            normalTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_tray_panel_text_unselected))
            normalTab.setTypeface(normalTab.typeface, android.graphics.Typeface.NORMAL)
            privateTab.setBackgroundResource(R.drawable.bg_panel_tab_selected)
            privateTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_tray_panel_text_selected))
            privateTab.setTypeface(privateTab.typeface, android.graphics.Typeface.BOLD)
        } else {
            normalTab.setBackgroundResource(R.drawable.bg_panel_tab_selected)
            normalTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_tray_panel_text_selected))
            normalTab.setTypeface(normalTab.typeface, android.graphics.Typeface.BOLD)
            privateTab.setBackgroundResource(R.drawable.bg_panel_tab_unselected)
            privateTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_tray_panel_text_unselected))
            privateTab.setTypeface(privateTab.typeface, android.graphics.Typeface.NORMAL)
        }
    }

    private fun updateThemeForMode() {
        val root = requireView().findViewById<ConstraintLayout>(R.id.tabTrayRoot)
        val bottomBar = requireView().findViewById<LinearLayout>(R.id.tabTrayBottomBar)
        val newTabBtn = requireView().findViewById<TextView>(R.id.tabTrayNewTab)
        val closeAllBtn = requireView().findViewById<TextView>(R.id.tabTrayCloseAll)
        val collectionBtn = requireView().findViewById<ImageButton>(R.id.tabTraySaveCollection)
        val titleText = requireView().findViewById<TextView>(R.id.tabTrayTitle)
        val backBtn = requireView().findViewById<ImageButton>(R.id.tabTrayBackButton)

        if (isPrivateMode) {
            root.setBackgroundResource(R.drawable.bg_tab_tray_private_gradient)
            bottomBar.setBackgroundColor(Color.parseColor("#CC024B30"))
            newTabBtn.setBackgroundResource(R.drawable.bg_new_tab_button_private)
            newTabBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary_container))

            // White text/icons on dark private background
            val privateTextColor = Color.WHITE
            closeAllBtn.setTextColor(privateTextColor)
            closeAllBtn.compoundDrawablesRelative[0]?.setTint(privateTextColor)
            collectionBtn.setColorFilter(privateTextColor)
            titleText.setTextColor(privateTextColor)
            backBtn.setColorFilter(privateTextColor)
        } else {
            root.setBackgroundResource(R.drawable.bg_tab_tray_gradient)
            bottomBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.tab_tray_bottom_bg))
            newTabBtn.setBackgroundResource(R.drawable.bg_new_tab_button)
            newTabBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_tray_new_tab_text))

            // Theme-aware text/icons for normal mode
            val normalTextColor = ContextCompat.getColor(requireContext(), R.color.tab_tray_text_primary)
            closeAllBtn.setTextColor(normalTextColor)
            closeAllBtn.compoundDrawablesRelative[0]?.setTint(normalTextColor)
            collectionBtn.setColorFilter(normalTextColor)
            titleText.setTextColor(normalTextColor)
            backBtn.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }

        // Update tab card colors in RecyclerView
        val tabsTray = requireView().findViewById<RecyclerView>(R.id.tabsTray)
        val cardColor = if (isPrivateMode) {
            ContextCompat.getColor(requireContext(), R.color.tab_tray_card_private)
        } else {
            ContextCompat.getColor(requireContext(), R.color.tab_tray_card)
        }
        val textColor = if (isPrivateMode) Color.WHITE else ContextCompat.getColor(requireContext(), R.color.tab_tray_text_primary)
        val urlColor = if (isPrivateMode) Color.parseColor("#AAFFFFFF") else ContextCompat.getColor(requireContext(), R.color.tab_tray_text_url)
        for (i in 0 until tabsTray.childCount) {
            val child = tabsTray.getChildAt(i)
            val card = child?.findViewById<View>(R.id.mozac_browser_tabstray_card)
            if (card is com.google.android.material.card.MaterialCardView) {
                card.setCardBackgroundColor(cardColor)
            }
            child?.findViewById<TextView>(R.id.mozac_browser_tabstray_title)?.setTextColor(textColor)
            child?.findViewById<TextView>(R.id.mozac_browser_tabstray_url)?.setTextColor(urlColor)
        }
    }

    private fun updateTitle() {
        val store = requireComponents.core.store
        val count = if (isPrivateMode) {
            store.state.privateTabs.size
        } else {
            store.state.normalTabs.size
        }
        val title = requireView().findViewById<TextView>(R.id.tabTrayTitle)
        title.text = if (count == 1) {
            getString(R.string.tab_tray_title_single)
        } else {
            getString(R.string.tab_tray_title, count)
        }

        val emptyText = requireView().findViewById<TextView>(R.id.tabTrayEmptyText)
        val tabsTray = requireView().findViewById<RecyclerView>(R.id.tabsTray)
        if (count == 0) {
            emptyText.text = if (isPrivateMode) {
                getString(R.string.tab_tray_empty_private)
            } else {
                getString(R.string.tab_tray_empty)
            }
            emptyText.visibility = View.VISIBLE
            tabsTray.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            tabsTray.visibility = View.VISIBLE
        }
    }

    private fun setupBottomBar(view: View) {
        val tabsUseCases = requireComponents.useCases.tabsUseCases

        view.findViewById<TextView>(R.id.tabTrayNewTab).setOnClickListener {
            if (isPrivateMode) {
                tabsUseCases.addTab.invoke("about:privatebrowsing", selectTab = true, private = true)
            } else {
                tabsUseCases.addTab.invoke("about:blank", selectTab = true)
            }
            closeTabsTray()
        }

        view.findViewById<TextView>(R.id.tabTrayCloseAll).setOnClickListener {
            if (isPrivateMode) {
                tabsUseCases.removePrivateTabs.invoke()
            } else {
                tabsUseCases.removeNormalTabs.invoke()
            }
            tabsUseCases.addTab.invoke("about:blank", selectTab = true)
            closeTabsTray()
        }

        view.findViewById<ImageButton>(R.id.tabTraySaveCollection).setOnClickListener {
            showSaveCollectionDialog()
        }
    }

    private fun showSaveCollectionDialog() {
        val store = requireComponents.core.store
        val tabs = if (isPrivateMode) store.state.privateTabs else store.state.normalTabs
        if (tabs.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No tabs to save", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val editText = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.collection_name_hint)
            setPadding(48, 32, 48, 16)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.collection_new)
            .setView(editText)
            .setPositiveButton(R.string.collection_save) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotBlank()) {
                    val collectionTabs = tabs.map {
                        org.midorinext.android.collections.CollectionTab(
                            title = it.content.title.ifBlank { it.content.url },
                            url = it.content.url,
                        )
                    }
                    val collection = org.midorinext.android.collections.TabCollection(
                        id = java.util.UUID.randomUUID().toString(),
                        title = name,
                        tabs = collectionTabs,
                    )
                    org.midorinext.android.collections.CollectionStorage(requireContext()).saveCollection(collection)
                    android.widget.Toast.makeText(requireContext(), R.string.collection_saved, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.customize_addon_collection_cancel, null)
            .show()
    }

    private fun observeTabCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            requireComponents.core.store.flow()
                .map { state ->
                    Pair(state.normalTabs.size, state.privateTabs.size)
                }
                .distinctUntilChanged()
                .collect { _ ->
                    updateTitle()
                }
        }
    }

    private fun createAndSetupTabsTray(context: Context): TabsTray {
        val layoutManager = GridLayoutManager(context, 2)
        val thumbnailLoader = ThumbnailLoader(context.components.core.thumbnailStorage)
        val trayStyling = TabsTrayStyling(itemBackgroundColor = Color.TRANSPARENT, itemTextColor = Color.WHITE)
        val viewHolderProvider: ViewHolderProvider = { viewGroup ->
            val view = LayoutInflater
                .from(context)
                .inflate(R.layout.browser_tabstray_item, viewGroup, false)

            RoundedTabViewHolder(view, thumbnailLoader)
        }
        val tabsAdapter = TabsAdapter(
            thumbnailLoader = thumbnailLoader,
            viewHolderProvider = viewHolderProvider,
            styling = trayStyling,
            delegate = object : TabsTray.Delegate {
                override fun onTabSelected(
                    tab: TabSessionState,
                    source: String?,
                ) {
                    requireComponents.useCases.tabsUseCases.selectTab(tab.id)
                    closeTabsTray()
                }

                override fun onTabClosed(
                    tab: TabSessionState,
                    source: String?,
                ) {
                    val store = requireComponents.core.store
                    val tabsUseCases = requireComponents.useCases.tabsUseCases
                    val isLastTab = if (isPrivateMode) {
                        store.state.privateTabs.size <= 1
                    } else {
                        store.state.normalTabs.size <= 1
                    }
                    tabsUseCases.removeTab(tab.id)
                    if (isLastTab && !isPrivateMode) {
                        tabsUseCases.addTab.invoke("about:blank", selectTab = true)
                        closeTabsTray()
                    }
                    updateTitle()
                }
            },
        )

        val tabsTray = requireView().findViewById<RecyclerView>(R.id.tabsTray)
        tabsTray.layoutManager = layoutManager
        tabsTray.adapter = tabsAdapter

        TabsTouchHelper {
            val store = requireComponents.core.store
            val tabsUseCases = requireComponents.useCases.tabsUseCases
            val isLastTab = if (isPrivateMode) {
                store.state.privateTabs.size <= 1
            } else {
                store.state.normalTabs.size <= 1
            }
            tabsUseCases.removeTab(it.id)
            if (isLastTab && !isPrivateMode) {
                tabsUseCases.addTab.invoke("about:blank", selectTab = true)
                closeTabsTray()
            }
            updateTitle()
        }.attachToRecyclerView(tabsTray)

        return tabsAdapter
    }
}
