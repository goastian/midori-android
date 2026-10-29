package org.midorinext.android.adblock

import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.content.blocking.Tracker
import mozilla.components.concept.engine.permission.PermissionRequest

internal class TrackingStatisticsObserver(
    private val recordBlocked: () -> Unit,
) : EngineSession.Observer {
    override fun onTrackerBlocked(tracker: Tracker) {
        recordBlocked()
    }

    override fun onAppPermissionRequest(permissionRequest: PermissionRequest) = Unit

    override fun onContentPermissionRequest(permissionRequest: PermissionRequest) = Unit
}
