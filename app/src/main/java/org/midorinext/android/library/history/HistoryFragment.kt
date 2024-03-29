/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library.history

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.SpannableString
import android.view.*
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.lib.state.ext.consumeFrom
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.support.base.feature.UserInteractionHandler
import org.midorinext.android.*
import org.midorinext.android.addons.showSnackBar
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.components.StoreProvider
import org.midorinext.android.components.history.DefaultPagedHistoryProvider
import org.midorinext.android.databinding.FragmentHistoryBinding
import org.midorinext.android.ext.*
import org.midorinext.android.library.LibraryPageFragment
import org.midorinext.android.utils.allowUndo

@SuppressWarnings("TooManyFunctions", "LargeClass")
class HistoryFragment : LibraryPageFragment<History>(), UserInteractionHandler {
    private lateinit var historyStore: HistoryFragmentStore
    private lateinit var historyInteractor: HistoryInteractor
    private lateinit var historyProvider: DefaultPagedHistoryProvider

    private var history: Flow<PagingData<History>> = Pager(
        PagingConfig(PAGE_SIZE),
        null
    ) {
        HistoryDataSource(
            historyProvider = historyProvider
        )
    }.flow

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val view = binding.root
        historyStore = StoreProvider.get(this) {
            HistoryFragmentStore(
                HistoryFragmentState(
                    items = listOf(),
                    mode = HistoryFragmentState.Mode.Normal,
                    pendingDeletionItems = emptySet(),
                    isEmpty = false,
                    isDeletingItems = false
                )
            )
        }
        val historyController: HistoryController = DefaultHistoryController(
            store = historyStore,
            appStore = requireContext().components.appStore,
            browserStore = requireComponents.core.store,
            historyStorage = requireComponents.core.historyStorage,
            historyProvider = historyProvider,
            navController = findNavController(),
            scope = lifecycleScope,
            openToBrowser = ::openItem,
            displayDeleteTimeRange = ::displayDeleteTimeRange,
            invalidateOptionsMenu = ::invalidateOptionsMenu,
            deleteSnackbar = ::deleteSnackbar,
            onTimeFrameDeleted = ::onTimeFrameDeleted,
            syncHistory = ::syncHistory,
            settings = requireContext().components.settings,
        )
        historyInteractor = DefaultHistoryInteractor(
            historyController
        )
        binding.historyContent.interactor = historyInteractor
        binding.historyContent.onZeroItemsLoaded = {
            historyStore.dispatch(HistoryFragmentAction.ChangeEmptyState(isEmpty = true))
        }
        binding.historyContent.onEmptyStateChanged = {
            historyStore.dispatch(HistoryFragmentAction.ChangeEmptyState(it))
        }

        return view
    }

    /**
     * All the current selected items. Individual history entries and entries from a group.
     * When a history group is selected, this will instead contain all the history entries in that group.
     */
    override val selectedItems
        get() = historyStore.state.mode.selectedItems.fold(emptyList<History>()) { accumulator, item ->
            when (item) {
                is History.Group -> accumulator + item.items
                else -> accumulator + item
            }
        }.toSet()

    private fun invalidateOptionsMenu() {
        activity?.invalidateOptionsMenu()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        historyProvider = DefaultPagedHistoryProvider(requireComponents.core.historyStorage)

        setHasOptionsMenu(true)
    }

    private fun deleteSnackbar(
        items: Set<History>,
        undo: suspend (items: Set<History>) -> Unit,
        delete: (Set<History>) -> suspend (context: Context) -> Unit
    ) {
        CoroutineScope(IO).allowUndo(
            requireActivity().getRootView()!!,
            getMultiSelectSnackBarMessage(items),
            getString(R.string.snackbar_deleted_undo),
            {
                undo.invoke(items)
            },
            delete(items)
        )
    }

    private fun onTimeFrameDeleted() {
        runIfFragmentIsAttached {
            showSnackBar(
                binding.root,
                getString(R.string.preferences_delete_browsing_data_snackbar)
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        consumeFrom(historyStore) { state ->
            when (state.mode) {
                is HistoryFragmentState.Mode.Normal -> setUiForNormalMode(context?.getString(R.string.library_history))
                is HistoryFragmentState.Mode.Editing -> setUiForSelectingMode(
                    context?.getString(
                        R.string.history_multi_select_title,
                        state.mode.selectedItems.size,
                    ),
                )
                else -> Unit
            }
            binding.historyContent.updateState(state)
        }

        requireContext().components.appStore.flowScoped(viewLifecycleOwner) { flow ->
            flow.mapNotNull { state -> state.pendingDeletionHistoryItems }.collect { items ->
                historyStore.dispatch(
                    HistoryFragmentAction.UpdatePendingDeletionItems(pendingDeletionItems = items)
                )
            }
        }

        binding.historyContent.history = history
    }

    override fun onResume() {
        super.onResume()

        (activity as NavHostActivity).getSupportActionBarAndInflateIfNecessary().show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (historyStore.state.mode is HistoryFragmentState.Mode.Editing) {
            inflater.inflate(R.menu.history_select_multi, menu)
            menu.findItem(R.id.share_history_multi_select)?.isVisible = true
            menu.findItem(R.id.delete_history_multi_select)?.title =
                SpannableString(getString(R.string.bookmark_menu_delete_button)).apply {
                    setTextColor(requireContext(), R.attr.textWarning)
                }
        } else {
            inflater.inflate(R.menu.history_menu, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.share_history_multi_select -> {
            val selectedHistory = historyStore.state.mode.selectedItems
            val shareTabs = mutableListOf<ShareData>()

            for (history in selectedHistory) {
                when (history) {
                    is History.Regular -> {
                        shareTabs.add(ShareData(url = history.url, title = history.title))
                    }
                    is History.Group -> {
                        shareTabs.addAll(
                            history.items.map { metadata ->
                                ShareData(url = metadata.url, title = metadata.title)
                            }
                        )
                    }
                    else -> {
                        // no-op, There is no [History.Metadata] in the HistoryFragment.
                    }
                }
            }

            share(shareTabs)
            historyStore.dispatch(HistoryFragmentAction.ExitEditMode)
            true
        }
        R.id.delete_history_multi_select -> {
            historyInteractor.onDeleteSome(historyStore.state.mode.selectedItems)
            historyStore.dispatch(HistoryFragmentAction.ExitEditMode)
            true
        }
        R.id.open_history_in_new_tabs_multi_select -> {
            openItemsInNewTab { selectedItem ->
                (selectedItem as? History.Regular)?.url ?: (selectedItem as? History.Metadata)?.url
            }

            showTabTray()
            historyStore.dispatch(HistoryFragmentAction.ExitEditMode)
            true
        }
        R.id.open_history_in_private_tabs_multi_select -> {
            openItemsInNewTab(private = true) { selectedItem ->
                (selectedItem as? History.Regular)?.url ?: (selectedItem as? History.Metadata)?.url
            }

            (activity as HomeActivity).apply {
                browsingModeManager.mode = BrowsingMode.Private
                supportActionBar?.hide()
            }

            showTabTray()
            historyStore.dispatch(HistoryFragmentAction.ExitEditMode)
            true
        }
        R.id.history_search -> {
            historyInteractor.onSearch()
            true
        }
        R.id.history_delete -> {
            historyInteractor.onDeleteTimeRange()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showTabTray() {
        findNavController().nav(
            R.id.historyFragment,
            HistoryFragmentDirections.actionGlobalTabsTrayFragment()
        )
    }

    private fun getMultiSelectSnackBarMessage(historyItems: Set<History>): String {
        return if (historyItems.size > 1) {
            getString(R.string.history_delete_multiple_items_snackbar)
        } else {
            val historyItem = historyItems.first()

            String.format(
                requireContext().getString(R.string.history_delete_single_item_snackbar),
                if (historyItem is History.Regular) {
                    historyItem.url.toShortUrl(requireComponents.publicSuffixList)
                } else {
                    historyItem.title
                }
            )
        }
    }

    override fun onBackPressed(): Boolean {
        return binding.historyContent.onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openItem(item: History.Regular) {
        (activity as HomeActivity).openToBrowserAndLoad(
            searchTermOrURL = item.url,
            newTab = true,
            from = BrowserDirection.FromHistory
        )
    }

    private fun displayDeleteTimeRange() {
        DeleteConfirmationDialogFragment(
            historyInteractor = historyInteractor
        ).show(childFragmentManager, null)
    }

    private fun share(data: List<ShareData>) {
        val directions = HistoryFragmentDirections.actionGlobalShareFragment(
            data = data.toTypedArray()
        )
        navigateToHistoryFragment(directions)
    }

    private fun navigateToHistoryFragment(directions: NavDirections) {
        findNavController().nav(
            R.id.historyFragment,
            directions
        )
    }

    @Suppress("UnusedPrivateMember")
    private suspend fun syncHistory() {
        val accountManager = requireComponents.backgroundServices.accountManager
        accountManager.syncNow(SyncReason.User)
    }

    internal class DeleteConfirmationDialogFragment(
        private val historyInteractor: HistoryInteractor
    ) : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireContext()).apply {
                val layout = LayoutInflater.from(context)
                    .inflate(R.layout.delete_history_time_range_dialog, null)
                val radioGroup = layout.findViewById<RadioGroup>(R.id.radio_group)
                radioGroup.check(R.id.last_hour_button)
                setView(layout)

                setNegativeButton(R.string.delete_browsing_data_prompt_cancel) { dialog: DialogInterface, _ ->
                    dialog.cancel()
                }
                setPositiveButton(R.string.delete_browsing_data_prompt_allow) { dialog: DialogInterface, _ ->
                    val selectedTimeFrame = when (radioGroup.checkedRadioButtonId) {
                        R.id.last_hour_button -> RemoveTimeFrame.LastHour
                        R.id.today_and_yesterday_button -> RemoveTimeFrame.TodayAndYesterday
                        R.id.everything_button -> null
                        else -> throw IllegalStateException("Unexpected radioButtonId")
                    }
                    historyInteractor.onDeleteTimeRangeConfirmed(selectedTimeFrame)
                    dialog.dismiss()
                }
            }.create()
    }

    @Suppress("UnusedPrivateMember")
    companion object {
        private const val PAGE_SIZE = 25
    }
}
