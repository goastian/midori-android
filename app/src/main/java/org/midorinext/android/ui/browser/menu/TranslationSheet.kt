package org.midorinext.android.ui.browser.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mozilla.components.concept.engine.translate.Language
import org.midorinext.android.R
import org.midorinext.android.ui.browser.BrowserScreenViewModel.TranslationSheetState

/**
 * A confirmation-first translation flow. The page and the selected language pair remain visible
 * until the user explicitly starts the on-device translation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSheet(
    state: TranslationSheetState,
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateOfferTranslation: (Boolean) -> Unit,
    onUpdateAlwaysTranslateSource: (Boolean) -> Unit,
    onUpdateNeverTranslateSource: (Boolean) -> Unit,
    onUpdateNeverTranslateSite: (Boolean) -> Unit,
    onTranslate: (fromLanguage: String, toLanguage: String) -> Unit,
) {
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var showQuickSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.sourceLanguage, state.sourceLanguages) {
        if (selectedSource !in state.sourceLanguages.map { it.code }) {
            selectedSource = state.sourceLanguage?.takeIf { code ->
                state.sourceLanguages.any { it.code == code }
            } ?: state.sourceLanguages.firstOrNull()?.code
        }
    }
    LaunchedEffect(state.targetLanguage, state.targetLanguages) {
        if (selectedTarget !in state.targetLanguages.map { it.code }) {
            selectedTarget = state.targetLanguage?.takeIf { code ->
                state.targetLanguages.any { it.code == code }
            } ?: state.targetLanguages.firstOrNull()?.code
        }
    }

    val canTranslate = state.enabled &&
        !selectedSource.isNullOrBlank() &&
        !selectedTarget.isNullOrBlank() &&
        !sameLanguage(selectedSource, selectedTarget)

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        if (showQuickSettings) {
            QuickTranslationSettings(
                state = state,
                onBack = { showQuickSettings = false },
                onUpdateOfferTranslation = onUpdateOfferTranslation,
                onUpdateAlwaysTranslateSource = onUpdateAlwaysTranslateSource,
                onUpdateNeverTranslateSource = onUpdateNeverTranslateSource,
                onUpdateNeverTranslateSite = onUpdateNeverTranslateSite,
                onOpenSettings = onOpenSettings
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.browser_translation_sheet_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showQuickSettings = true }) {
                    Icon(
                        painter = painterResource(R.drawable.icons_settings),
                        contentDescription = stringResource(R.string.browser_translation_sheet_settings)
                    )
                }
            }
            Text(
                text = stringResource(R.string.browser_translation_sheet_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TranslationLanguagePicker(
                label = stringResource(R.string.browser_translation_sheet_from),
                selectedCode = selectedSource,
                languages = state.sourceLanguages,
                onSelected = { selectedSource = it }
            )
            TranslationLanguagePicker(
                label = stringResource(R.string.browser_translation_sheet_to),
                selectedCode = selectedTarget,
                languages = state.targetLanguages,
                onSelected = { selectedTarget = it }
            )

            if (!canTranslate) {
                Text(
                    text = stringResource(R.string.browser_translation_sheet_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.browser_translation_sheet_not_now))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    enabled = canTranslate,
                    onClick = {
                        onTranslate(selectedSource.orEmpty(), selectedTarget.orEmpty())
                    }
                ) {
                    Text(stringResource(R.string.browser_translate_page))
                }
            }
            }
        }
    }
}

@Composable
private fun QuickTranslationSettings(
    state: TranslationSheetState,
    onBack: () -> Unit,
    onUpdateOfferTranslation: (Boolean) -> Unit,
    onUpdateAlwaysTranslateSource: (Boolean) -> Unit,
    onUpdateNeverTranslateSource: (Boolean) -> Unit,
    onUpdateNeverTranslateSite: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sourceName = state.sourceLanguages.firstOrNull { it.code == state.sourceLanguage }
        ?.localizedDisplayName ?: state.sourceLanguage
        ?: stringResource(R.string.browser_translation_sheet_detecting)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.icons_arrow_backward),
                    contentDescription = stringResource(R.string.browser_translation_sheet_back)
                )
            }
            Text(
                text = stringResource(R.string.browser_translation_quick_title),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        QuickTranslationToggle(
            label = stringResource(R.string.browser_translation_quick_offer),
            checked = state.offerTranslation,
            onCheckedChange = onUpdateOfferTranslation
        )
        HorizontalDivider()
        QuickTranslationToggle(
            label = stringResource(R.string.browser_translation_quick_always_source, sourceName),
            checked = state.alwaysTranslateSource,
            onCheckedChange = onUpdateAlwaysTranslateSource
        )
        QuickTranslationToggle(
            label = stringResource(R.string.browser_translation_quick_never_source, sourceName),
            checked = state.neverTranslateSource,
            onCheckedChange = onUpdateNeverTranslateSource
        )
        HorizontalDivider()
        QuickTranslationToggle(
            label = stringResource(R.string.browser_translation_quick_never_site),
            checked = state.neverTranslateSite,
            onCheckedChange = onUpdateNeverTranslateSite
        )
        HorizontalDivider()
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.browser_translation_quick_full_settings),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun QuickTranslationToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TranslationLanguagePicker(
    label: String,
    selectedCode: String?,
    languages: List<Language>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLanguage = languages.firstOrNull { it.code == selectedCode }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            TextButton(
                enabled = languages.isNotEmpty(),
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedLanguage?.localizedDisplayName ?: selectedCode
                        ?: stringResource(R.string.browser_translation_sheet_detecting),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Text("⌄", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.localizedDisplayName ?: language.code) },
                        onClick = {
                            onSelected(language.code)
                            expanded = false
                        }
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

private fun sameLanguage(first: String?, second: String?): Boolean =
    first?.substringBefore('-')?.equals(second?.substringBefore('-'), ignoreCase = true) == true
