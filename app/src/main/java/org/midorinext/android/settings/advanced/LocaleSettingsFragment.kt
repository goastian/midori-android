/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.advanced

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import mozilla.components.lib.state.ext.consumeFrom
import mozilla.components.support.ktx.android.view.hideKeyboard
import mozilla.components.support.locale.LocaleUseCases
import org.midorinext.android.R
import org.midorinext.android.components.StoreProvider
import org.midorinext.android.databinding.FragmentLocaleSettingsBinding
import org.midorinext.android.ext.components
import org.midorinext.android.ext.showToolbar

class LocaleSettingsFragment : Fragment() {

    private lateinit var localeSettingsStore: LocaleSettingsStore
    private lateinit var interactor: LocaleSettingsInteractor

    private var _binding: FragmentLocaleSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocaleSettingsBinding.inflate(inflater, container, false)

        val browserStore = requireContext().components.core.store
        val localeUseCase = LocaleUseCases(browserStore)

        localeSettingsStore = StoreProvider.get(this) {
            LocaleSettingsStore(
                createInitialLocaleSettingsState(requireContext())
            )
        }
        interactor = LocaleSettingsInteractor(
            controller = DefaultLocaleSettingsController(
                activity = requireActivity(),
                localeSettingsStore = localeSettingsStore,
                localeUseCase = localeUseCase
            )
        )
        binding.localeSettingsContent.interactor = interactor

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.languages_list, menu)
        val searchItem = menu.findItem(R.id.search)
        val searchView: SearchView = searchItem.actionView as SearchView
        searchView.imeOptions = EditorInfo.IME_ACTION_DONE
        searchView.queryHint = getString(R.string.locale_search_hint)
        searchView.maxWidth = Int.MAX_VALUE

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                interactor.onSearchQueryTyped(newText)
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_language))
    }

    override fun onPause() {
        view?.hideKeyboard()
        super.onPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        consumeFrom(localeSettingsStore) {
            binding.localeSettingsContent.update(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
