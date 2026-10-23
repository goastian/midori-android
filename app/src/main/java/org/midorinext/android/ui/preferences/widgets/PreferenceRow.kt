package org.midorinext.android.ui.preferences.widgets

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PreferenceRow(
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClicked: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClicked() }
            .minimumInteractiveComponentSize()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = if (description != null) 12.dp else 2.dp)
    ) {
        Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(label),
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
            AnimatedVisibility(visible = (description != null)) {
                description?.let {
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        trailing()
    }

}
