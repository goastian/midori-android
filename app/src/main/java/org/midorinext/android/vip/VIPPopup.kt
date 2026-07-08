package org.midorinext.android.vip

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Switch
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
import java.util.Locale

@Composable
fun VIPPopup(
    vipState: VIPState,
    openLink: (String) -> Unit
) {
    val isDarkTheme = LocalMidoriTheme.current.dark
    val colors = when {
        vipState.enabled && isDarkTheme -> enabledDark
        vipState.enabled && !isDarkTheme -> enabledLight
        !vipState.enabled && isDarkTheme -> disabledDark
        !vipState.enabled && !isDarkTheme -> disabledLight
        else -> throw (Exception("Invalid color set for VIP colors"))
    }.animatedColors()

    val titleText = if (vipState.enabled) R.string.vip_protection_enabled
                    else R.string.vip_protection_disabled
    val descText = if (vipState.enabled) R.string.vip_protection_enabled_hint
                   else R.string.vip_protection_disabled_hint
    val siteLabel = vipState.hostname.ifBlank { stringResource(R.string.vip_popup_hostname_fallback) }

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
                Switch(
                    checked = vipState.enabled,
                    onCheckedChange = { vipState.toggleProtection() },
                    colors = switchColorsLight(isDarkTheme)
                )
            }
            Text(text = stringResource(descText))
            if (vipState.isLoading && !vipState.hasSnapshot) {
                Text(
                    text = stringResource(R.string.vip_popup_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vipState.hasSnapshot) {
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
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_blocked),
                            value = vipState.blockedCount.toString()
                        )
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_popups),
                            value = vipState.popupBlockedCount.toString()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_saved),
                            value = formatSavedData(vipState.dataSavedBytes)
                        )
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_energy),
                            value = formatEnergy(vipState.energySavedWh)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_trackers),
                            value = vipState.trackersCount.toString()
                        )
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_ads),
                            value = vipState.adsCount.toString()
                        )
                        VIPStatCell(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.vip_stats_other),
                            value = vipState.otherCount.toString()
                        )
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    VIPModeRow(
                        label = stringResource(R.string.vip_stats_level),
                        value = modeLabel(vipState.protectionLevel)
                    )
                    VIPModeRow(
                        label = stringResource(R.string.vip_stats_antifingerprint),
                        value = if (vipState.antiFingerprintEnabled) {
                            modeLabel(vipState.antiFingerprintMode)
                        } else {
                            stringResource(R.string.vip_module_off)
                        }
                    )
                    VIPModeRow(
                        label = stringResource(R.string.vip_stats_popup_guard),
                        value = if (vipState.popupDefenseEnabled) {
                            modeLabel(vipState.popupDefenseMode)
                        } else {
                            stringResource(R.string.vip_module_off)
                        }
                    )
                }
            }
        }

        Column(modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            VIPLink(stringResource(R.string.vip_about), stringResource(R.string.vip_about_link), openLink)
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            VIPLink(stringResource(R.string.vip_comment), stringResource(R.string.vip_comment_link), openLink)
        }
    }
}

@Composable
fun VIPLink(
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
private fun VIPStatCell(
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
private fun VIPModeRow(
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
private fun formatSavedData(bytes: Long): String {
    val savedKb = if (bytes <= 0) 0.0 else bytes.toDouble() / 1024.0
    return if (savedKb >= 1024.0) {
        String.format(Locale.US, "%.1f %s", savedKb / 1024.0, stringResource(R.string.vip_saved_unit_mb))
    } else {
        String.format(Locale.US, "%.0f %s", savedKb, stringResource(R.string.vip_saved_unit_kb))
    }
}

@Composable
private fun formatEnergy(energySavedKwh: Double): String {
    val energyWh = energySavedKwh * 1000.0
    return if (energyWh >= 1000.0) {
        String.format(Locale.US, "%.2f %s", energyWh / 1000.0, stringResource(R.string.vip_energy_unit_kwh))
    } else {
        String.format(Locale.US, "%.0f %s", energyWh, stringResource(R.string.vip_energy_unit_wh))
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

