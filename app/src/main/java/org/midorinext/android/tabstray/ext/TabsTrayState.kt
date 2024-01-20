/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.ext

import org.midorinext.android.tabstray.TabsTrayState.Mode

/**
 * A helper to check if we're in [Mode.Select] mode.
 */
fun Mode.isSelect() = this is Mode.Select
