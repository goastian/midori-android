package org.midorinext.android.ui.preferences.widgets

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun PreferenceSelectionPopup(
    @StringRes label: Int,
    popupContent: @Composable () -> Unit,
    @StringRes description: Int? = null,
    icon: @Composable () -> Unit = {},
    fullscreenPopup: Boolean = false,
    disableScreenHeader: Boolean = false
) {
    var showPopup by remember { mutableStateOf(false) }

    BackHandler(showPopup) {
        showPopup = false
    }

    Box {
        PreferenceRow(
            label = label,
            description = description?.let { stringResource(it) },
            trailing = icon,
            onClicked = { showPopup = true }
        )
        if (showPopup) {
            if (fullscreenPopup) {
                Dialog(
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    onDismissRequest = { showPopup = false }
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (disableScreenHeader) {
                            popupContent()
                        } else {
                            val scrollState = rememberScrollState()
                            Column {
                                ScreenHeader(title = stringResource(id = label), scrollableState = scrollState)
                                Box(modifier = Modifier.verticalScroll(scrollState).padding(top = 16.dp)) {
                                    popupContent()
                                }
                            }
                        }
                    }
                }
            } else {
                Dropdown(
                    expanded = true,
                    onDismissRequest = { showPopup = false },
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    popupContent()
                }
            }
        }
    }
}