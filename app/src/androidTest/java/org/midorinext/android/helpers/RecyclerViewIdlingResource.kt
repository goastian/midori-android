/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.helpers

import androidx.test.espresso.IdlingResource
import androidx.test.espresso.IdlingResource.ResourceCallback

class RecyclerViewIdlingResource constructor(private val recycler: androidx.recyclerview.widget.RecyclerView, val minItemCount: Int = 0) :
    IdlingResource {

    private var callback: ResourceCallback? = null

    override fun isIdleNow(): Boolean {
        if (recycler.adapter != null && recycler.adapter!!.itemCount >= minItemCount) {
            if (callback != null) {
                callback!!.onTransitionToIdle()
            }
            return true
        }
        return false
    }

    override fun registerIdleTransitionCallback(callback: ResourceCallback) {
        this.callback = callback
    }

    override fun getName(): String {
        return RecyclerViewIdlingResource::class.java.name + ":" + recycler.id
    }
}
