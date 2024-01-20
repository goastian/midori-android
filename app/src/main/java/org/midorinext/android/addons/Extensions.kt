/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.addons

import android.view.View
import org.midorinext.android.components.MidoriSnackbar

/**
 * Shows the Midori Snackbar in the given view along with the provided text.
 *
 * @param view A [View] used to determine a parent for the [MidoriSnackbar].
 * @param text The text to display in the [MidoriSnackbar].
 */
internal fun showSnackBar(view: View, text: String, duration: Int = MidoriSnackbar.LENGTH_SHORT) {
    MidoriSnackbar.make(
        view = view,
        duration = duration,
        isDisplayedWithBrowserToolbar = true
    )
        .setText(text)
        .show()
}
