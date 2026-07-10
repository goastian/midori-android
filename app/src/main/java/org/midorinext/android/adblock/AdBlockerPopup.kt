package org.midorinext.android.adblock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R
import org.midorinext.android.ui.theme.LocalMidoriTheme

@Composable
fun AdBlockerPopup(
    adBlockerState: AdBlockerState,
    openLink: (String) -> Unit
) {
    val isDarkTheme = LocalMidoriTheme.current.dark
    val colors = when {
        adBlockerState.enabled && isDarkTheme -> enabledDark
        adBlockerState.enabled && !isDarkTheme -> enabledLight
        !adBlockerState.enabled && isDarkTheme -> disabledDark
        else -> disabledLight
    }.animatedColors()

    val titleText = if (adBlockerState.enabled) R.string.vip_protection_enabled
        else R.string.vip_protection_disabled
    val descText = if (adBlockerState.enabled) R.string.vip_protection_enabled_hint
        else R.string.vip_protection_disabled_hint
    val siteLabel = adBlockerState.hostname.ifBlank { stringResource(R.string.vip_popup_hostname_fallback) }

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = colors.content)
                .border(2.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(titleText),
                        color = colors.toggleBorder,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = siteLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = modeLabel(adBlockerState.protectionLevel),
                    color = colors.toggleBorder,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(text = stringResource(descText))
        }

        if (adBlockerState.hasSnapshot) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vip_stats_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdBlockerStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.home_protected_pages),
                            value = adBlockerState.protectedPageCount.toString()
                        )
                        AdBlockerStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.home_last_site),
                            value = adBlockerState.hostname.ifBlank { "-" }
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    AdBlockerModeRow(
                        label = stringResource(R.string.vip_stats_level),
                        value = modeLabel(adBlockerState.protectionLevel)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            AdBlockerLink(stringResource(R.string.vip_about), stringResource(R.string.vip_about_link), openLink)
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            AdBlockerLink(stringResource(R.string.vip_comment), stringResource(R.string.vip_comment_link), openLink)
        }
    }
}

@Composable
fun AdBlockerLink(
    text: String,
    url: String,
    openLink: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openLink(url) }
            .padding(8.dp)
    ) {
        Text(text = text, modifier = Modifier.weight(2f))
        Icon(
            painter = painterResource(R.drawable.icons_arrow_tab),
            contentDescription = "arrow"
        )
    }
}

@Composable
private fun AdBlockerStatCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdBlockerModeRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun modeLabel(mode: String): String = when (mode.lowercase()) {
    "strict" -> stringResource(R.string.vip_mode_strict)
    "basic" -> stringResource(R.string.vip_mode_basic)
    "balanced" -> stringResource(R.string.vip_mode_balanced)
    "relaxed" -> stringResource(R.string.vip_mode_relaxed)
    "off" -> stringResource(R.string.vip_mode_off)
    "on" -> stringResource(R.string.vip_module_on)
    else -> stringResource(R.string.vip_mode_standard)
}
