package org.midorinext.android.ui.browser.toolbar

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.UrlIcon
import org.midorinext.android.ui.animation.reduceMotionRequested
import kotlinx.coroutines.launch

@Composable
fun SiteSecurityIcon(toolbarState: BrowserToolbarState) {
    val siteSecurity by toolbarState.siteSecurity.collectAsStateWithLifecycle()
    val reduceMotion = reduceMotionRequested()

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
                val panelProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
                val coroutineScope = rememberCoroutineScope()
                var dismissing by remember { mutableStateOf(false) }
                val animationSpec = if (reduceMotion) snap<Float>() else tween(durationMillis = 220)
                val dismiss = {
                    if (!dismissing) {
                        dismissing = true
                        coroutineScope.launch {
                            panelProgress.animateTo(0f, animationSpec)
                            toolbarState.updateShowSiteSecurity(false)
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    panelProgress.animateTo(1f, animationSpec)
                }
                Dialog(
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    onDismissRequest = dismiss,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { dismiss() }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                .graphicsLayer {
                                    translationY = -size.height * (1f - panelProgress.value)
                                    alpha = panelProgress.value
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
