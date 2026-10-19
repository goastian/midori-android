package org.midorinext.android.ext

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi

data class DefaultBrowserStatus(
    val isDefault: Boolean = false,
    val roleRequestIntent: Intent? = null,
) {
    fun requestIntent(): Intent = roleRequestIntent ?: Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}

fun Context.defaultBrowserStatus(): DefaultBrowserStatus =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        DefaultBrowserApi29.status(this)
    } else {
        DefaultBrowserStatus()
    }

// Keep API 29 types out of Compose's remembered values and generated callbacks on older Android.
@RequiresApi(Build.VERSION_CODES.Q)
private object DefaultBrowserApi29 {
    @DoNotInline
    fun status(context: Context): DefaultBrowserStatus {
        val manager = context.getSystemService(RoleManager::class.java) ?: return DefaultBrowserStatus()
        if (!manager.isRoleAvailable(RoleManager.ROLE_BROWSER)) return DefaultBrowserStatus()
        return DefaultBrowserStatus(
            isDefault = manager.isRoleHeld(RoleManager.ROLE_BROWSER),
            roleRequestIntent = manager.createRequestRoleIntent(RoleManager.ROLE_BROWSER),
        )
    }
}
