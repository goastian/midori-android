package org.midorinext.android.ui.bookmarks

import android.widget.Toast
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType

@Composable
fun BookmarkEditDialog(
    item: BookmarkNode? = null,
    onDismiss: () -> Unit = {},
    onSubmit: (title: String, url: String?) -> Unit = { _,_ -> }
) {
    val placeholderTitle = stringResource(id = R.string.bookmarks_folder_title_placeholder)
    var itemTitle by remember {
        val text = item?.title?.let {
            it.ifEmpty { placeholderTitle }
        } ?: placeholderTitle
        mutableStateOf(TextFieldValue(
            text = item?.title ?: text,
            TextRange(0, text.length))
        )
    }
    var itemUrl by remember { mutableStateOf(item?.url) }

    val context = LocalContext.current
    val allFieldsRequiredWarning = stringResource(id = R.string.all_fields_required)
    YesNoDialog(
        onDismissRequest = { onDismiss() },
        title =
            (if (item == null) stringResource(id = R.string.create)
            else stringResource(id = R.string.edit))
            + " " +
            (if (item?.type == BookmarkNodeType.ITEM) stringResource(id = R.string.bookmarks_bookmark)
            else stringResource(id = R.string.bookmarks_folder)),
        onYes = {
            if (itemTitle.text.isEmpty() || (item?.type == BookmarkNodeType.ITEM && itemUrl?.isEmpty() == true)) {
                Toast.makeText(context, allFieldsRequiredWarning, Toast.LENGTH_LONG).show()
            } else {
                onSubmit(itemTitle.text, itemUrl)
                onDismiss()
            }
        },
        onNo = { onDismiss() },
        yesText = stringResource(id = R.string.save),
        additionalContent = {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(true) {
                focusRequester.requestFocus()
            }
            TextField(
                value = itemTitle,
                onValueChange = { itemTitle = it },
                modifier = Modifier.focusRequester(focusRequester)
            )
            if (item?.type ==  BookmarkNodeType.ITEM) {
                TextField(
                    value = itemUrl ?: "",
                    onValueChange = { itemUrl = it }
                )
            }
        }
    )
}