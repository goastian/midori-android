package org.midorinext.android.ui.browser.mozaccompose.permissions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.BuildConfig
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.concept.engine.permission.Permission
import mozilla.components.concept.engine.permission.PermissionRequest
import mozilla.components.feature.sitepermissions.R
import mozilla.components.support.ktx.kotlin.stripDefaultPort
import java.security.InvalidParameterException
import mozilla.components.ui.icons.R as iconsR
import org.midorinext.android.R as midoriR

data class PermissionDialogData(
    val permissionRequest: PermissionRequest,
    val host: String,
    val topLevelHost: String
)
private data class PermissionsDialogDisplayData(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @StringRes val helper: Int? = null,
    @StringRes val noText: Int = R.string.mozac_feature_sitepermissions_not_allow,
)

// TODO move junior exception and juniorGeolocBlockedDialog to junior sources

@Composable
fun PermissionsDialog(
    data: PermissionDialogData,
    onClose: (allowed: Boolean, shouldStore: Boolean) -> Unit
) {
    val permission by remember(data) { derivedStateOf { data.permissionRequest.permissions.first() } }

    if (BuildConfig.FLAVOR_version == "junior" && permission is Permission.ContentGeoLocation) {
        JuniorGeolocBlockedDialog(onClose)
    } else {
        var shouldStore by remember {
            mutableStateOf(
                permission is Permission.ContentNotification
            )
        }

        val displayData by remember(data) {
            derivedStateOf {
                if (data.permissionRequest.containsVideoAndAudioSources()) {
                    PermissionsDialogDisplayData(
                        R.string.mozac_feature_sitepermissions_camera_and_microphone,
                        iconsR.drawable.mozac_ic_microphone_24
                    )
                } else {
                    when (permission) {
                        is Permission.ContentGeoLocation -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_location_title,
                            iconsR.drawable.mozac_ic_location_24,
                        )

                        is Permission.ContentNotification -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_notification_title,
                            iconsR.drawable.mozac_ic_notification_24,
                        )

                        is Permission.ContentAudioCapture, is Permission.ContentAudioMicrophone -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_microfone_title,
                            iconsR.drawable.mozac_ic_microphone_24,
                        )

                        is Permission.ContentVideoCamera, is Permission.ContentVideoCapture -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_camera_title,
                            iconsR.drawable.mozac_ic_camera_24,
                        )

                        is Permission.ContentPersistentStorage -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_persistent_storage_title,
                            iconsR.drawable.mozac_ic_storage_24,
                        )

                        is Permission.ContentMediaKeySystemAccess -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_media_key_system_access_title,
                            iconsR.drawable.mozac_ic_link_24,
                        )

                        is Permission.ContentCrossOriginStorageAccess -> PermissionsDialogDisplayData(
                            R.string.mozac_feature_sitepermissions_storage_access_title,
                            iconsR.drawable.mozac_ic_storage_24,
                            R.string.mozac_feature_sitepermissions_storage_access_message,
                            R.string.mozac_feature_sitepermissions_storage_access_not_allow,
                        )
                        else -> throw InvalidParameterException("$permission is not a valid permission.")
                    }
                }
            }
        }
        val requestHost = data.host.stripDefaultPort()
        val topLevelHost = data.topLevelHost.ifBlank { requestHost }.stripDefaultPort()
        val description = if (permission is Permission.ContentCrossOriginStorageAccess) {
            stringResource(id = displayData.title, requestHost, topLevelHost)
        } else {
            stringResource(id = displayData.title, requestHost)
        }
        val helperText = displayData.helper?.let { stringResource(id = it, requestHost) }

        YesNoDialog(
            onDismissRequest = { onClose(false, false) },
            onYes = { onClose(true, shouldStore) },
            onNo = { onClose(false, shouldStore) },
            description = description,
            icon = displayData.icon,
            yesText = stringResource(id = R.string.mozac_feature_sitepermissions_allow),
            noText = stringResource(id = displayData.noText),
            additionalContent = {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    helperText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shouldStore,
                            onCheckedChange = { shouldStore = !shouldStore })
                        Text(text = stringResource(id = R.string.mozac_feature_sitepermissions_do_not_ask_again_on_this_site2))
                    }
                }
            }
        )
    }
}

@Composable
fun JuniorGeolocBlockedDialog(
    onClose: (allowed: Boolean, shouldStore: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onClose(false, false) },
        confirmButton = { Button(
            onClick = { onClose(false, false) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) { Text(text = stringResource(midoriR.string.browser_understood)) }},
        icon = { Icon(painterResource(id = iconsR.drawable.mozac_ic_location_24), contentDescription = "icon") },
        text = { Text(text = stringResource(midoriR.string.browser_permission_geoloc_blocked)) },
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 0.dp,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
    )
}
