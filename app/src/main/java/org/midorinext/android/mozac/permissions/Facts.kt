package org.midorinext.android.mozac.permissions

import mozilla.components.concept.engine.permission.Permission
import mozilla.components.feature.sitepermissions.SitePermissionsFacts
import mozilla.components.support.base.Component
import mozilla.components.support.base.facts.Action
import mozilla.components.support.base.facts.Fact
import mozilla.components.support.base.facts.collect

internal fun emitPermissionDenied(permission: Permission) = emitSitePermissionsFact(
    action = Action.CANCEL,
    permissions = permission.name,
)

internal fun emitPermissionsDenied(permissions: List<Permission>) = emitSitePermissionsFact(
    action = Action.CANCEL,
    permissions = permissions.distinctBy { it.name }.joinToString { it.name },
)

internal fun emitPermissionAllowed(permission: Permission) = emitSitePermissionsFact(
    action = Action.CONFIRM,
    permissions = permission.name,
)

internal fun emitPermissionsAllowed(permissions: List<Permission>) = emitSitePermissionsFact(
    action = Action.CONFIRM,
    permissions = permissions.distinctBy { it.name }.joinToString { it.name },
)

private fun emitSitePermissionsFact(
    action: Action,
    permissions: String,
) {
    Fact(
        Component.FEATURE_SITEPERMISSIONS,
        action,
        SitePermissionsFacts.Items.PERMISSIONS,
        permissions,
    ).collect()
}