/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ext

import android.content.SharedPreferences

/**
 * Clear everything in shared preferences and commit changes immediately.
 */
fun SharedPreferences.clearAndCommit() = this.edit().clear().commit()
