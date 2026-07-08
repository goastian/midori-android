package org.midorinext.android.ui.browser.mozaccompose.prompts.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.feature.prompts.R
import java.util.Calendar
import java.util.Date

fun Date.getComponent(component: Int) = Calendar.getInstance().let {
    it.time = this
    it.get(component)
}

fun dateFromUTC(date: Date): Date {
    return Date(date.time + Calendar.getInstance().timeZone.getOffset(Date().time))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePicker(
    request: PromptRequest.TimeSelection,
    consume: () -> Unit
) {
    val showDate = request.type == PromptRequest.TimeSelection.Type.DATE || request.type == PromptRequest.TimeSelection.Type.DATE_AND_TIME
    val showTime = request.type == PromptRequest.TimeSelection.Type.TIME || request.type == PromptRequest.TimeSelection.Type.DATE_AND_TIME
    val showMonth = request.type == PromptRequest.TimeSelection.Type.MONTH

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateFromUTC(request.initialDate).time,
        yearRange = request.minimumDate?.getComponent(Calendar.YEAR)?.let { min ->
            request.maximumDate?.getComponent(Calendar.YEAR)?.let { max ->
                IntRange(min, max)
            }
        } ?: DatePickerDefaults.YearRange
    )
    val timePickerState = rememberTimePickerState(
        initialHour = request.initialDate.getComponent(Calendar.HOUR_OF_DAY),
        initialMinute = request.initialDate.getComponent(Calendar.MINUTE),
    )

    val onDismiss = {
        request.onDismiss()
        consume()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(20.dp)
        ) {
            Column(modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
            ) {
                if (showMonth) {
                    // TODO MonthPicker
                } else {
                    if (showDate) {
                        DatePicker(state = datePickerState, showModeToggle = true)
                    }
                    if (showTime) {
                        TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.mozac_feature_prompts_cancel),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Button(
                    onClick = {
                        request.onConfirm(Calendar.getInstance().apply {
                            if (showMonth) {
                                // TODO month picker result
                            } else {
                                if (showDate) {
                                    datePickerState.selectedDateMillis?.let { timeInMillis = it }
                                }
                                if (showTime) {
                                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(Calendar.MINUTE, timePickerState.minute)
                                }
                            }
                        }.time)
                        consume()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text(text = stringResource(R.string.mozac_feature_prompts_set_date))
                }
            }
        }
    }
}