package org.midorinext.android.ui.browser

import android.text.format.Formatter
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import org.midorinext.android.R
import org.midorinext.android.lite.LiteCompressionProfile
import org.midorinext.android.lite.LiteMode
import java.text.NumberFormat

@Composable
internal fun liteStatusLabel(status: LiteMode.Status): String = stringResource(
    when (status) {
        LiteMode.Status.OFF -> R.string.lite_mode_off
        LiteMode.Status.CONNECTING -> R.string.lite_mode_connecting
        LiteMode.Status.ACTIVE -> R.string.lite_mode_on
        LiteMode.Status.DIRECT -> R.string.lite_mode_direct
        LiteMode.Status.UNAVAILABLE -> R.string.lite_mode_unavailable
        LiteMode.Status.STORAGE_ERROR -> R.string.lite_mode_storage_error
    },
)

@Composable
internal fun LiteModePanel(
    state: LiteMode.State,
    privateBrowsing: Boolean,
    onDismiss: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCompressionSelected: (LiteCompressionProfile) -> Unit,
) {
    val context = LocalContext.current
    val title = stringResource(R.string.lite_mode_title)
    val status = state.statusFor(privateBrowsing)
    val savings = state.savings.takeUnless { privateBrowsing }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val inSettings = showSettings && !privateBrowsing
    Dialog(
        onDismissRequest = { if (inSettings) showSettings = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { window.setGravity(Gravity.TOP) }
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("lite_mode_panel"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                if (inSettings) {
                    LiteCompressionSettings(
                        selected = state.compression,
                        onSelected = onCompressionSelected,
                        onDone = { showSettings = false },
                    )
                    return@Surface
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.icons_lite_mode), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            val subtitle = if (savings != null && status == LiteMode.Status.ACTIVE) {
                                val locale = context.resources.configuration.locales[0]
                                val percentage = NumberFormat.getPercentInstance(locale).format(savings.fraction)
                                stringResource(R.string.lite_mode_saved_percent, percentage)
                            } else liteStatusLabel(status)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.enabled,
                            enabled = state.loaded,
                            onCheckedChange = onToggle,
                            modifier = Modifier.semantics { contentDescription = title }.testTag("lite_mode_switch"),
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!privateBrowsing) {
                            Text(stringResource(R.string.lite_mode_data_saved), style = MaterialTheme.typography.labelLarge)
                            Text(
                                savings?.let { Formatter.formatShortFileSize(context, it.saved) } ?: "—",
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.testTag("lite_mode_savings"),
                            )
                        }
                        Text(
                            stringResource(if (privateBrowsing) R.string.lite_mode_private_scope else R.string.lite_mode_savings_scope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (!privateBrowsing) {
                            TextButton(
                                onClick = { showSettings = true },
                                enabled = state.loaded,
                                modifier = Modifier.testTag("lite_mode_settings"),
                            ) {
                                Text(stringResource(R.string.settings))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiteCompressionSettings(
    selected: LiteCompressionProfile,
    onSelected: (LiteCompressionProfile) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
        Text(
            stringResource(R.string.lite_mode_compression),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            for (profile in LiteCompressionProfile.entries) {
                val (title, description) = when (profile) {
                    LiteCompressionProfile.LOSSLESS -> R.string.lite_mode_lossless to R.string.lite_mode_lossless_summary
                    LiteCompressionProfile.BALANCED -> R.string.lite_mode_balanced to R.string.lite_mode_balanced_summary
                    LiteCompressionProfile.SAVER -> R.string.lite_mode_saver to R.string.lite_mode_saver_summary
                    LiteCompressionProfile.EXTREME -> R.string.lite_mode_extreme to R.string.lite_mode_extreme_summary
                }
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(
                            selected = profile == selected,
                            onClick = { onSelected(profile) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = profile == selected, onClick = null)
                    Column {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) {
            Text(stringResource(R.string.summary_done))
        }
    }
}
