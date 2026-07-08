package org.midorinext.android.mozac.permissions

import android.Manifest
import android.content.Context
import mozilla.components.concept.engine.permission.Permission
import mozilla.components.concept.engine.permission.PermissionRequest
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.support.ktx.android.content.isPermissionGranted
import java.security.InvalidParameterException

internal fun PermissionRequest.toSitePermissions(
    host: String,
    status: SitePermissions.Status,
    initialSitePermission: SitePermissions = SitePermissions(host, savedAt = System.currentTimeMillis()),
    permissions: List<Permission> = this.permissions,
): SitePermissions {
    var sitePermissions = initialSitePermission
    for (permission in permissions) {
        sitePermissions = updateSitePermissionsStatus(status, permission, sitePermissions)
    }
    return sitePermissions
}

internal fun updateSitePermissionsStatus(
    status: SitePermissions.Status,
    permission: Permission,
    sitePermissions: SitePermissions,
): SitePermissions {
    return when (permission) {
        is Permission.ContentGeoLocation, is Permission.AppLocationCoarse, is Permission.AppLocationFine -> {
            sitePermissions.copy(location = status)
        }
        is Permission.ContentNotification -> {
            sitePermissions.copy(notification = status)
        }
        is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone, is Permission.AppAudio -> {
            sitePermissions.copy(microphone = status)
        }
        is Permission.ContentVideoCamera, is Permission.ContentVideoCapture, is Permission.AppCamera -> {
            sitePermissions.copy(camera = status)
        }
        is Permission.ContentAutoPlayAudible -> {
            sitePermissions.copy(autoplayAudible = status.toAutoplayStatus())
        }
        is Permission.ContentAutoPlayInaudible -> {
            sitePermissions.copy(autoplayInaudible = status.toAutoplayStatus())
        }
        is Permission.ContentPersistentStorage -> {
            sitePermissions.copy(localStorage = status)
        }
        is Permission.ContentMediaKeySystemAccess -> {
            sitePermissions.copy(mediaKeySystemAccess = status)
        }
        is Permission.ContentCrossOriginStorageAccess -> {
            sitePermissions.copy(crossOriginStorageAccess = status)
        }
        else ->
            throw InvalidParameterException("$permission is not a valid permission.")
    }
}

internal fun PermissionRequest.isForAutoplay() =
    this.permissions.any { it is Permission.ContentAutoPlayInaudible || it is Permission.ContentAutoPlayAudible }

internal fun Permission.isSupported(): Boolean {
    return when (this) {
        is Permission.ContentGeoLocation,
        is Permission.ContentNotification,
        is Permission.ContentPersistentStorage,
        is Permission.ContentCrossOriginStorageAccess,
        is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone,
        is Permission.ContentVideoCamera, is Permission.ContentVideoCapture,
        is Permission.ContentAutoPlayAudible, is Permission.ContentAutoPlayInaudible,
        is Permission.ContentMediaKeySystemAccess,
        -> true
        else -> false
    }
}

internal val PermissionRequest.isMedia: Boolean
    get() {
        return when (permissions.first()) {
            is Permission.ContentVideoCamera, is Permission.ContentVideoCapture,
            is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone,
            -> true
            else -> false
        }
    }

internal fun PermissionRequest.areAllMediaPermissionsGranted(context: Context): Boolean {
    val systemPermissions = mutableListOf<String>()
    permissions.forEach { permission ->
        when (permission) {
            is Permission.ContentVideoCamera, is Permission.ContentVideoCapture -> {
                systemPermissions.add(Manifest.permission.CAMERA)
            }
            is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone -> {
                systemPermissions.add(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // no-op
            }
        }
    }
    return systemPermissions.all { context.isPermissionGranted((it)) }
}

internal fun PermissionRequest.doNotAskAgain(permissionFromStore: SitePermissions): Boolean {
    return permissions.any { permission ->
        when (permission) {
            is Permission.ContentGeoLocation -> {
                permissionFromStore.location.doNotAskAgain()
            }
            is Permission.ContentNotification -> {
                permissionFromStore.notification.doNotAskAgain()
            }
            is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone -> {
                permissionFromStore.microphone.doNotAskAgain()
            }
            is Permission.ContentVideoCamera, is Permission.ContentVideoCapture -> {
                permissionFromStore.camera.doNotAskAgain()
            }
            is Permission.ContentPersistentStorage -> {
                permissionFromStore.localStorage.doNotAskAgain()
            }
            is Permission.ContentMediaKeySystemAccess -> {
                permissionFromStore.mediaKeySystemAccess.doNotAskAgain()
            }
            is Permission.ContentCrossOriginStorageAccess -> {
                permissionFromStore.crossOriginStorageAccess.doNotAskAgain()
            }
            else -> false
        }
    }
}

internal fun SitePermissions?.isGranted(permissionRequest: PermissionRequest): Boolean {
    return if (this != null) {
        permissionRequest.permissions.all { permission ->
            isPermissionGranted(permission, this)
        }
    } else {
        false
    }
}

private fun isPermissionGranted(
    permission: Permission,
    permissionFromStorage: SitePermissions,
): Boolean {
    return when (permission) {
        is Permission.ContentGeoLocation -> {
            permissionFromStorage.location.isAllowed()
        }
        is Permission.ContentNotification -> {
            permissionFromStorage.notification.isAllowed()
        }
        is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone -> {
            permissionFromStorage.microphone.isAllowed()
        }
        is Permission.ContentVideoCamera, is Permission.ContentVideoCapture -> {
            permissionFromStorage.camera.isAllowed()
        }
        is Permission.ContentPersistentStorage -> {
            permissionFromStorage.localStorage.isAllowed()
        }
        is Permission.ContentCrossOriginStorageAccess -> {
            permissionFromStorage.crossOriginStorageAccess.isAllowed()
        }
        is Permission.ContentMediaKeySystemAccess -> {
            permissionFromStorage.mediaKeySystemAccess.isAllowed()
        }
        is Permission.ContentAutoPlayAudible -> {
            permissionFromStorage.autoplayAudible.isAllowed()
        }
        is Permission.ContentAutoPlayInaudible -> {
            permissionFromStorage.autoplayInaudible.isAllowed()
        }
        else ->
            throw InvalidParameterException("$permission is not a valid permission.")
    }
}
