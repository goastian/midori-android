/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.browser

import android.view.View
import androidx.annotation.StringRes
import mozilla.components.ui.widgets.SnackbarDelegate
import org.midorinext.android.components.MidoriSnackbar

class MidoriSnackbarDelegate(private val view: View) : SnackbarDelegate {

    override fun show(
        snackBarParentView: View,
        @StringRes text: Int,
        duration: Int,
        @StringRes action: Int,
        listener: ((v: View) -> Unit)?
    ) {
        if (listener != null && action != 0) {
            MidoriSnackbar.make(
                view = view,
                duration = MidoriSnackbar.LENGTH_SHORT,
                isDisplayedWithBrowserToolbar = true
            )
                .setText(view.context.getString(text))
                .setAction(view.context.getString(action)) { listener.invoke(view) }
                .show()
        } else {
            MidoriSnackbar.make(
                view,
                duration = MidoriSnackbar.LENGTH_SHORT,
                isDisplayedWithBrowserToolbar = true
            )
                .setText(view.context.getString(text))
                .show()
        }
    }
}
