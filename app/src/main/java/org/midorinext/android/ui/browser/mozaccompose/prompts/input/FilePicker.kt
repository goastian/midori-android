package org.midorinext.android.ui.browser.mozaccompose.prompts.input

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.ui.browser.mozaccompose.prompts.input.internalcopy.MimeType
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.support.ktx.android.content.isPermissionGranted
import mozilla.components.support.ktx.android.net.isUnderPrivateAppDirectory
import mozilla.components.support.utils.ext.getParcelableExtraCompat

@Composable
fun FilePicker(
    request: PromptRequest.File,
    consume: () -> Unit,
) {
    val context = LocalContext.current

    var permissionGranted by remember { mutableStateOf(false) }
    var captureUri: Uri? by remember { mutableStateOf(null) }

    val chooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data?.clipData != null && request.isMultipleFilesSelection) {
            result.data?.clipData?.run {
                val uris = Array<Uri>(itemCount) { index -> getItemAt(index).uri }
                val sanitizedUris = uris.removeUrisUnderPrivateAppDir(context)
                if (sanitizedUris.isEmpty()) {
                    request.onDismiss()
                } else {
                    request.onMultipleFilesSelected(context, sanitizedUris)
                }
            }
        } else {
            val uri = result.data?.data ?: result.data?.getParcelableExtraCompat(MediaStore.EXTRA_OUTPUT, Uri::class.java) ?: captureUri
            uri?.let {
                if (!it.isUnderPrivateAppDirectory(context)) {
                    request.onSingleFileSelected(context, it)
                } else {
                    request.onDismiss()
                }
            } ?: request.onDismiss()
        }
        consume()
    }

    val permissionRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsGrant ->
        if (permissionsGrant.all { it.value }) {
            permissionGranted = true
        } else {
            // TODO show file prompt permission rational if needed
            request.onDismiss()
            consume()
        }
    }

    LaunchedEffect(request, permissionGranted) {
        if (permissionGranted) permissionGranted = false
        captureUri = null

        val neededPermissions = mutableSetOf<String>()
        val intents = mutableListOf<Intent>()

        for (type in MimeType.values()) {
            val hasPermission = context.isPermissionGranted(type.permission)
            if (hasPermission && type.shouldCapture(request.mimeTypes, request.captureMode)) {
                type.buildIntent(context, request)?.also {
                    captureUri = it.getParcelableExtraCompat(MediaStore.EXTRA_OUTPUT, Uri::class.java)
                    chooserLauncher.launch(it)
                    return@LaunchedEffect
                }
            } else if (type.matches(request.mimeTypes)) {
                if (hasPermission) {
                    type.buildIntent(context, request)?.also {
                        captureUri = it.getParcelableExtraCompat(MediaStore.EXTRA_OUTPUT, Uri::class.java)
                        intents.add(it)
                    }
                } else {
                    neededPermissions.addAll(type.permission)
                }
            }
        }

        if (neededPermissions.isEmpty()) {
            val lastIntent = intents.removeAt(intents.lastIndex)
            val chooser = Intent.createChooser(lastIntent, null).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
            }
            chooserLauncher.launch(chooser)
        } else {
            permissionRequestLauncher.launch(neededPermissions.toTypedArray())
        }
    }
}

internal fun Array<Uri>.removeUrisUnderPrivateAppDir(context: Context): Array<Uri> {
    return this.filter { !it.isUnderPrivateAppDirectory(context) }.toTypedArray()
}