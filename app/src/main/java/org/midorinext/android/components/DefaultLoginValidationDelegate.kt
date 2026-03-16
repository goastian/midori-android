/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginValidationDelegate
import mozilla.components.concept.storage.LoginsStorage

/**
 * A delegate that will check against [loginsStorage] to see if a given login can be saved
 * or updated.
 */
class DefaultLoginValidationDelegate(
    private val loginsStorage: Lazy<LoginsStorage>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : LoginValidationDelegate {

    override fun shouldUpdateOrCreateAsync(
        entry: LoginEntry,
    ): Deferred<LoginValidationDelegate.Result> {
        val deferred = CompletableDeferred<LoginValidationDelegate.Result>()
        scope.launch {
            try {
                val found = loginsStorage.value.findLoginToUpdate(entry)
                deferred.complete(
                    if (found != null) {
                        LoginValidationDelegate.Result.CanBeUpdated(found)
                    } else {
                        LoginValidationDelegate.Result.CanBeCreated
                    },
                )
            } catch (e: Exception) {
                deferred.complete(LoginValidationDelegate.Result.CanBeCreated)
            }
        }
        return deferred
    }
}
