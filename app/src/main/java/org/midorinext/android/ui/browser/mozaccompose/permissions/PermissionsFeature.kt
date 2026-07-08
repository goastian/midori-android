package org.midorinext.android.ui.browser.mozaccompose.permissions

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.mozac.permissions.PermissionsFeature
import org.midorinext.android.ui.browser.mozaccompose.ComposeFeatureWrapper
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl


@Composable
fun PermissionsFeature(
    store: BrowserStore,
    storage: SitePermissionsStorage
) {
    val context = LocalContext.current
    var feature: PermissionsFeature? = null
    var dialogData: PermissionDialogData? by remember { mutableStateOf(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        feature?.onPermissionsResult(
            result.keys.toTypedArray(),
            result.values.map { granted ->
                if (granted) PackageManager.PERMISSION_GRANTED
                else PackageManager.PERMISSION_DENIED
            }.toIntArray()
        )
    }

    feature = remember(context, store, storage) {
        PermissionsFeature(
            context = context,
            store = store,
            storage = storage,
            onPrompt = { permissionRequest, host ->
                dialogData = PermissionDialogData(
                    permissionRequest = permissionRequest,
                    host = host,
                    topLevelHost = store.state.selectedTab?.content?.url?.tryGetHostFromUrl().orEmpty()
                )
            },
            onNeedToRequestPermissions = {
                launcher.launch(it)
            }
        )
    }

    ComposeFeatureWrapper(feature = feature)

    dialogData?.let { data ->
        PermissionsDialog(
            data = data,
            onClose = { allowed: Boolean, shouldStore: Boolean ->
                if (allowed) {
                    feature.onPositiveButtonPress(data.permissionRequest.id, shouldStore)
                } else {
                    feature.onNegativeButtonPress(data.permissionRequest.id, shouldStore)
                }
                dialogData = null
            }
        )
    }
}
