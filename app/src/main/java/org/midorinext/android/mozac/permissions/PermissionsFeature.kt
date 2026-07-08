package org.midorinext.android.mozac.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import org.midorinext.android.ext.activity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.permission.PermissionRequest
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.OnNeedToRequestPermissions
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.support.ktx.kotlinx.coroutines.flow.filterChanged

class PermissionsFeature(
    private val context: Context,
    private val store: BrowserStore,
    private val storage: SitePermissionsStorage,
    private val onPrompt: (permissionRequest: PermissionRequest, host: String) -> Unit,
    override val onNeedToRequestPermissions: OnNeedToRequestPermissions
) : LifecycleAwareFeature, mozilla.components.support.base.feature.PermissionsFeature {
    private val ioCoroutineScope by lazy { coroutineScopeInitializer() }
    private var coroutineScopeInitializer = { CoroutineScope(Dispatchers.IO) }
    private var sitePermissionScope: CoroutineScope? = null
    private var appPermissionScope: CoroutineScope? = null
    private var loadingScope: CoroutineScope? = null

    override fun start() {
        setupPermissionRequestsCollector()
        setupAppPermissionRequestsCollector()
        setupLoadingCollector()
    }

    override fun stop() {
        sitePermissionScope?.cancel()
        appPermissionScope?.cancel()
        loadingScope?.cancel()
        storage.clearTemporaryPermissions()
    }

    private fun setupLoadingCollector() {
        loadingScope = store.flowScoped(dispatcher = Dispatchers.IO) { flow -> flow
            .mapNotNull { state -> state.selectedTab }
            .distinctUntilChangedBy { it.content.loading }
            .collect { tab ->
                if (tab.content.loading) {
                    storage.clearTemporaryPermissions()
                }
            }
        }
    }

    private fun setupAppPermissionRequestsCollector() {
        appPermissionScope = store.flowScoped(dispatcher = Dispatchers.IO) { flow -> flow
            .mapNotNull { state -> state.selectedTab?.content?.appPermissionRequestsList }
            .filterChanged { it }
            .collect { appPermissionRequest ->
                val permissions = appPermissionRequest.permissions.map { it.id ?: "" }
                onNeedToRequestPermissions(permissions.toTypedArray())
            }
        }
    }

    private fun setupPermissionRequestsCollector() {
        sitePermissionScope = store.flowScoped(dispatcher = Dispatchers.IO) { flow -> flow
            .mapNotNull { state -> state.selectedTab?.content?.permissionRequestsList }
            .filterChanged { it }
            .collect { permissionRequest ->
                val origin: String = permissionRequest.uri?.getOrigin().orEmpty()
                if (origin.isEmpty()) {
                    permissionRequest.consumeAndReject()
                } else {
                    if (permissionRequest.permissions.all { it.isSupported() }) {
                        onContentPermissionRequested(permissionRequest, origin)
                    } else {
                        permissionRequest.consumeAndReject()
                    }
                }
            }
        }
    }

    override fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        val currentTabState = getCurrentTabState()
        val currentContentSate = currentTabState?.content
        val appPermissionRequest = findRequestedAppPermission(permissions)

        if (appPermissionRequest != null && currentContentSate != null) {
            val allPermissionWereGranted = grantResults.all { grantResult ->
                grantResult == PackageManager.PERMISSION_GRANTED
            }

            if (grantResults.isNotEmpty() && allPermissionWereGranted) {
                appPermissionRequest.grant()
            } else {
                appPermissionRequest.reject()
                permissions.forEach { systemPermission ->
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(context.activity as Activity, systemPermission)) {
                        // The system permission is denied permanently
                        storeSitePermissions(currentContentSate, appPermissionRequest, status = SitePermissions.Status.BLOCKED)
                    }
                }
            }

            store.dispatch(ContentAction.ConsumeAppPermissionsRequest(currentTabState.id, appPermissionRequest))
        }
    }

    private suspend fun onContentPermissionRequested(
        permissionRequest: PermissionRequest,
        origin: String,
        coroutineScope: CoroutineScope = ioCoroutineScope
    ) {
        // We want to warranty that all media permissions have the required system
        // permissions are granted first, otherwise, we reject the request
        if (permissionRequest.isMedia && !permissionRequest.areAllMediaPermissionsGranted(context)) {
            permissionRequest.consumeAndReject()
        }

        val private: Boolean = store.state.selectedTab?.content?.private
            ?: throw IllegalStateException("Unable to find selected session")

        val permissionFromStorage = withContext(Dispatchers.IO) {
            storage.findSitePermissionsBy(origin, private = private)
        }

        if (shouldShowPrompt(permissionRequest, permissionFromStorage)) {
            onPrompt(permissionRequest, origin)
        } else {
            if (permissionFromStorage.isGranted(permissionRequest)) {
                permissionRequest.grant()
            } else {
                permissionRequest.reject()
            }
            consumePermissionRequest(permissionRequest)
        }
    }

    private fun shouldShowPrompt(
        permissionRequest: PermissionRequest,
        permissionFromStorage: SitePermissions?
    ): Boolean {
        return if (permissionRequest.isForAutoplay()) {
            false
        } else {
            (permissionFromStorage == null || !permissionRequest.doNotAskAgain(permissionFromStorage))
        }
    }

    internal fun onPositiveButtonPress(
        permissionId: String,
        shouldStore: Boolean
    ) {
        findRequestedPermission(permissionId)?.let { permissionRequest ->
            consumePermissionRequest(permissionRequest)
            onContentPermissionGranted(permissionRequest, shouldStore)

            if (!permissionRequest.containsVideoAndAudioSources()) {
                emitPermissionAllowed(permissionRequest.permissions.first())
            } else {
                emitPermissionsAllowed(permissionRequest.permissions)
            }
        }
    }

    private fun onContentPermissionGranted(
        permissionRequest: PermissionRequest,
        shouldStore: Boolean
    ) {
        permissionRequest.grant()
        if (shouldStore) {
            getCurrentContentState()?.let { contentState ->
                storeSitePermissions(contentState, permissionRequest, SitePermissions.Status.ALLOWED)
            }
        } else {
            storage.saveTemporary(permissionRequest)
        }
    }

    fun onNegativeButtonPress(
        permissionId: String,
        shouldStore: Boolean
    ) {
        findRequestedPermission(permissionId)?.let { permissionRequest ->
            consumePermissionRequest(permissionRequest)
            onContentPermissionDeny(permissionRequest, shouldStore)

            if (!permissionRequest.containsVideoAndAudioSources()) {
                emitPermissionDenied(permissionRequest.permissions.first())
            } else {
                emitPermissionsDenied(permissionRequest.permissions)
            }
        }
    }

    private fun onContentPermissionDeny(
        permissionRequest: PermissionRequest,
        shouldStore: Boolean
    ) {
        permissionRequest.reject()
        if (shouldStore) {
            getCurrentContentState()?.let { contentState ->
                storeSitePermissions(contentState, permissionRequest, SitePermissions.Status.BLOCKED)
            }
        } else {
            storage.saveTemporary(permissionRequest)
        }
    }

    /*
    ** Storage
     */

    private fun storeSitePermissions(
        contentState: ContentState,
        request: PermissionRequest,
        status: SitePermissions.Status,
        coroutineScope: CoroutineScope = ioCoroutineScope
    ) {
        if (contentState.private) {
            return
        }

        coroutineScope.launch {
            request.uri?.getOrigin()?.let { origin ->
                val sitePermissions = storage.findSitePermissionsBy(origin, private = false)
                if (sitePermissions == null) {
                    storage.save(
                        request.toSitePermissions(origin, status = status, permissions = request.permissions),
                        request,
                        private = false
                    )
                } else {
                    storage.update(
                        sitePermissions = request.toSitePermissions(origin, status, sitePermissions),
                        private = false
                    )
                }
            }
        }
    }

    /*
    ** HELPERS
     */

    private fun getCurrentTabState() = store.state.selectedTab
    private fun getCurrentContentState() = getCurrentTabState()?.content

    private fun findRequestedAppPermission(permissions: Array<String>): PermissionRequest? {
        return getCurrentContentState()?.appPermissionRequestsList?.find {
            permissions.contains(it.permissions.first().id)
        }
    }

    private fun findRequestedPermission(permissionId: String): PermissionRequest? {
        return getCurrentContentState()?.permissionRequestsList?.find {
            it.id == permissionId
        }
    }

    private fun PermissionRequest.consumeAndReject() {
        consumePermissionRequest(this)
        this.reject()
    }

    private fun consumePermissionRequest(permissionRequest: PermissionRequest) {
        getCurrentTabState()?.id?.let { sessionId ->
            store.dispatch(ContentAction.ConsumePermissionsRequest(sessionId, permissionRequest))
        }
    }
}