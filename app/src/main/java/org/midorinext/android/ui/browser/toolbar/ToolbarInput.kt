package org.midorinext.android.ui.browser.toolbar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import org.midorinext.android.ext.activity
import org.midorinext.android.ui.widgets.MidoriIconOnBackground

@Composable
fun ToolbarInput(
    toolbarState: ToolbarState,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onMidoriIconClicked: () -> Unit = {}
) {
    val localStyle = LocalTextStyle.current
    val mergedStyle = localStyle.merge(TextStyle(color = LocalContentColor.current))

    val focusManager = LocalFocusManager.current
    val activity = LocalContext.current.activity
    val focusRequester = remember { FocusRequester() }
    BackHandler(toolbarState.hasFocus) {
        toolbarState.updateFocus(false)
        focusManager.clearFocus()
        activity?.forceHideKeyboard()
    }
    LaunchedEffect(toolbarState.hasFocus) {
        if (toolbarState.hasFocus) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
            activity?.forceHideKeyboard()
        }
    }

    BasicTextField(
        value = toolbarState.text,
        onValueChange = { toolbarState.updateTextFromUser(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(
            onGo = {
                onCommit(toolbarState.text.text)
                toolbarState.updateFocus(false)
                focusManager.clearFocus()
                activity?.forceHideKeyboard()
            }
        ),
        singleLine = true,
        enabled = true,
        textStyle = mergedStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                // Entering edit mode moves the toolbar into a full-screen composition. The old
                // text field reports focus loss while it is disposed, so only promote focus here;
                // all exit paths explicitly clear the toolbar state.
                if (it.hasFocus) {
                    toolbarState.updateFocus(true)
                }
            }
    ) { innerTextField ->
        ToolbarDecorator(
            state = toolbarState,
            hintColor = mergedStyle.color.copy(alpha = 0.6f),
            innerTextField = innerTextField,
            trailingIcons = {
                if (toolbarState.hasFocus) {
                    if (toolbarState.text.text.isNotEmpty()) {
                        IconButton(
                            onClick = { toolbarState.updateTextFromUser("") },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painterResource(id = R.drawable.icons_close_circled),
                                contentDescription = "Clear",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else {
                    trailingContent?.invoke()
                }
            },
            leadingContent = leadingContent,
            onMidoriIconClicked = onMidoriIconClicked
        )
    }
}

/** Search-engine affordance used by the focused Firefox-style address bar. */
@Composable
fun ToolbarSearchSelector() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(52.dp)
            .height(40.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MidoriIconOnBackground(
                shape = CircleShape,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.icons_chevron_down_small),
                contentDescription = null,
                modifier = Modifier.size(8.dp),
            )
        }
    }
}

// TODO move this elsewhere
@Composable
fun KeyboardObserver(
    toolbarState: ToolbarState
) {
    val activity = LocalContext.current.activity
    DisposableEffect(activity) {
        activity?.registerOnKeyboardHiddenCallback {
            if (toolbarState.hasFocus) {
                toolbarState.updateFocus(false)
            }
        }
        onDispose {
            activity?.unregisterOnKeyboardHiddenCallback()
        }
    }
}
