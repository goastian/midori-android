package org.midorinext.android.ui.browser.toolbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.UrlIcon

@Composable
fun SiteSecurityIcon(toolbarState: BrowserToolbarState) {
    val siteSecurity by toolbarState.siteSecurity.collectAsState()
    val density = LocalDensity.current

    siteSecurity?.let { securityInfo ->
        Box {
            Icon(
                painter = painterResource(id = if (securityInfo.isSecure) R.drawable.icons_lock else R.drawable.icons_lock_off),
                contentDescription = "security icon",
                tint = if (securityInfo.isSecure) LocalContentColor.current else MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable { toolbarState.updateShowSiteSecurity(true) }
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
            )

            if (toolbarState.showSiteSecurity) {
                var watchEnd by remember { mutableStateOf(false) }
                var dialogOffsetTarget by remember { mutableStateOf((-100).dp) }
                val dialogOffset by animateDpAsState(
                    targetValue = dialogOffsetTarget,
                    animationSpec = tween(durationMillis = 400),
                    label = "site security dialog offset"
                )
                LaunchedEffect(true) {
                    dialogOffsetTarget = 0.dp
                    watchEnd = true
                }
                LaunchedEffect(dialogOffset) {
                    if (watchEnd && dialogOffsetTarget == (-100).dp && dialogOffset == (-100).dp)
                        toolbarState.updateShowSiteSecurity(false)
                }
                Dialog(
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    onDismissRequest = { dialogOffsetTarget = (-100).dp }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                dialogOffsetTarget = (-100).dp
                            }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                .offset {
                                    with(density) {
                                        IntOffset(0, dialogOffset.roundToPx())
                                    }
                                }
                                .background(MaterialTheme.colorScheme.background)
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                UrlIcon(
                                    browserIcons = toolbarState.browserIcons,
                                    url = toolbarState.currentUrl.value ?: "",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = securityInfo.host.ifEmpty { toolbarState.text.text },
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val icon = if (securityInfo.isSecure) R.drawable.icons_lock else R.drawable.icons_lock_off
                                val iconColor = if (securityInfo.isSecure) LocalContentColor.current else MaterialTheme.colorScheme.error
                                val text = if (securityInfo.isSecure) R.string.browser_site_secure else R.string.browser_site_insecure
                                Icon(
                                    painter = painterResource(id = icon),
                                    tint = iconColor,
                                    contentDescription = "lock",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(id = text),
                                    fontSize = 16.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    } ?: Box(modifier = Modifier.size(24.dp))
}