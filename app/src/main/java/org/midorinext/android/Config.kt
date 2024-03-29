/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android

enum class ReleaseChannel {
    Debug,
    Release;

    val isReleased: Boolean
        get() = when (this) {
            Debug -> false
            else -> true
        }

    /**
     * True if this is a debug release channel, false otherwise.
     *
     * This constant should often be used instead of [BuildConfig.DEBUG], which indicates
     * if the `debuggable` flag is set which can be true even on released channel builds
     * (e.g. performance).
     */
    val isDebug: Boolean
        get() = !this.isReleased

    val isRelease: Boolean
        get() = when (this) {
            Release -> true
            else -> false
        }
}

object Config {
    val channel = when (BuildConfig.BUILD_TYPE) {
        "debug" -> ReleaseChannel.Debug
        "release" -> ReleaseChannel.Release
        else -> {
            throw IllegalStateException("Unknown build type: ${BuildConfig.BUILD_TYPE}")
        }
    }
}
