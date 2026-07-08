package org.midorinext.android.ui.browser.mozaccompose.prompts.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.midorinext.android.R
import mozilla.components.concept.engine.prompt.Choice

@Composable
fun ChoiceDialog(
    choices: Array<Choice>,
    onSelected: (Array<Choice>) -> Unit,
    onDismissRequest: () -> Unit,
    multipleSelectionAllowed: Boolean = false
) {
    val selection = remember {
        if (multipleSelectionAllowed) {
            choices.flatMap { choice ->
                choice.children?.let {
                    listOf(choice).plus(it)
                } ?: listOf()
            }
            .filter { it.selected }
            .toMutableStateList()
        } else null
    }

    val onChoiceClicked: (Choice, Boolean) -> Unit = { choice, selected ->
        if (multipleSelectionAllowed) {
            if (selected) selection?.add(choice)
            else selection?.remove(choice)
        } else {
            onSelected(arrayOf(choice))
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
        ) {
            ChoicesRec(choices, onChoiceClicked, selection)
            if (multipleSelectionAllowed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Button(
                        onClick = { onSelected(selection?.toTypedArray() ?: arrayOf()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicesRec(
    choices: Array<Choice>,
    onChoiceClicked: (Choice, Boolean) -> Unit,
    selection: List<Choice>?,
    disabled: Boolean = false
) {
    choices.forEach { choice ->
        val newDisabled = disabled || !choice.enable
        when {
            choice.isASeparator -> HorizontalDivider()
            choice.isGroupType -> {
                Text(
                    text = choice.label,
                    fontWeight = FontWeight.Bold
                )
                choice.children?.let { ChoicesRec(it, onChoiceClicked, selection, newDisabled) }
            }
            else -> ChoiceRow(choice, onChoiceClicked, selection, newDisabled)
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: Choice,
    onChoiceClicked: (Choice, Boolean) -> Unit,
    selection: List<Choice>?,
    disabled: Boolean = false
) {
    val newDisabled = disabled || !choice.enable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(
                if (!newDisabled) Modifier.clickable { onChoiceClicked(choice, true) }
                else Modifier
            )
    ) {
        if (selection != null) {
            Checkbox(
                checked = choice in selection,
                enabled = !newDisabled,
                onCheckedChange = { onChoiceClicked(choice, it) }
            )
        } else {
            RadioButton(
                selected = choice.selected,
                enabled = !newDisabled,
                onClick = { onChoiceClicked(choice, true) }
            )
        }

        Text(text = choice.label, color = LocalContentColor.current.copy(if (newDisabled) 0.6f else 1f))
    }
}