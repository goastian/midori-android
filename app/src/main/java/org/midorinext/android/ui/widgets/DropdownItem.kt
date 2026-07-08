package org.midorinext.android.ui.widgets

import androidx.annotation.DrawableRes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun DropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = { Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Normal) },
        leadingIcon = { icon?.let { Icon(painter = painterResource(id = it), contentDescription = text) } },
        trailingIcon = trailing,
        colors = MenuDefaults.itemColors(leadingIconColor = LocalContentColor.current.copy(0.6f)),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}