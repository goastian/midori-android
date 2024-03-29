/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library

import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import mozilla.components.support.ktx.android.content.getColorFromAttr
import androidx.navigation.fragment.findNavController
import org.midorinext.android.HomeActivity
import org.midorinext.android.R
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.ext.components
import org.midorinext.android.ext.setToolbarColors

abstract class LibraryPageFragment<T> : Fragment() {

    abstract val selectedItems: Set<T>

    protected fun close() {
        if (!findNavController().popBackStack(R.id.browserFragment, false)) {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
    }

    protected fun openItemsInNewTab(private: Boolean = false, toUrl: (T) -> String?) {
        context?.components?.useCases?.tabsUseCases?.let { tabsUseCases ->
            selectedItems.asSequence()
                .mapNotNull(toUrl)
                .forEach { url ->
                    tabsUseCases.addTab.invoke(url, private = private)
                }
        }

        (activity as HomeActivity).browsingModeManager.mode = BrowsingMode.fromBoolean(private)
    }

    protected fun setUiForNormalMode(title: String?) {
        context?.let { context ->
            updateToolbar(
                title = title,
                foregroundColor = context.getColorFromAttr(R.attr.textPrimary),
                backgroundColor = context.getColorFromAttr(R.attr.layer1)
            )
        }
    }

    protected fun setUiForSelectingMode(title: String?) {
        context?.let { context ->
            updateToolbar(
                title = title,
                foregroundColor = ContextCompat.getColor(
                    context,
                    R.color.fx_mobile_text_color_oncolor_primary
                ),
                backgroundColor = context.getColorFromAttr(R.attr.accent)
            )
        }
    }

    private fun updateToolbar(title: String?, foregroundColor: Int, backgroundColor: Int) {
        activity?.title = title
        val toolbar = activity?.findViewById<Toolbar>(R.id.navigationToolbar)
        toolbar?.setToolbarColors(foregroundColor, backgroundColor)
        toolbar?.setNavigationIcon(R.drawable.ic_back_button)
        toolbar?.navigationIcon?.setTint(foregroundColor)
    }

    override fun onDetach() {
        super.onDetach()
        context?.let {
            activity?.findViewById<Toolbar>(R.id.navigationToolbar)?.setToolbarColors(
                it.getColorFromAttr(R.attr.textPrimary),
                it.getColorFromAttr(R.attr.layer1)
            )
        }
    }

}
