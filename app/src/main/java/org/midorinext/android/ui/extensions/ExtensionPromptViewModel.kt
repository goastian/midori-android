package org.midorinext.android.ui.extensions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtensionInstallException
import mozilla.components.lib.state.ext.flow
import org.midorinext.android.extensions.ExtensionManager

@HiltViewModel
class ExtensionPromptViewModel @Inject constructor(
    private val store: BrowserStore,
    private val extensionManager: ExtensionManager,
) : ViewModel() {
    val prompt = store.flow().map { it.webExtensionPromptRequest }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.state.webExtensionPromptRequest)

    private val _installing = MutableStateFlow(false)
    val installing = _installing.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()
    private var installation: Job? = null

    init {
        viewModelScope.launch {
            prompt.filterNotNull().collect { request ->
                when (request) {
                    is WebExtensionPromptRequest.AfterInstallation.PostInstallation -> {
                        if (consume(request)) {
                            try {
                                extensionManager.rememberInstalled(request.extension)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e(TAG, "Could not save installed extension", e)
                            }
                        }
                    }
                    is WebExtensionPromptRequest.BeforeInstallation.InstallationFailed -> {
                        if (consume(request) && request.exception !is WebExtensionInstallException.UserCancelled) {
                            _failed.value = true
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun install(request: WebExtensionPromptRequest.InstallationRequested) {
        if (installation?.isActive == true) return
        installation = viewModelScope.launch {
            if (!consume(request)) return@launch
            _installing.value = true
            _failed.value = false
            try {
                withTimeout(120_000L) {
                    extensionManager.install(request.url, request.installationMethod)
                }
            } catch (e: TimeoutCancellationException) {
                _failed.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e !is WebExtensionInstallException.UserCancelled) {
                    Log.e(TAG, "Website extension installation failed", e)
                    _failed.value = true
                }
            } finally {
                _installing.value = false
            }
        }
    }

    fun respond(request: WebExtensionPromptRequest, granted: Boolean) {
        viewModelScope.launch {
            if (!consume(request)) return@launch
            try {
                when (request) {
                    is WebExtensionPromptRequest.AfterInstallation.Permissions.Required -> {
                        if (granted) {
                            confirmInstallPermissions(request.dataCollectionPermissions, request.onConfirm)
                        } else {
                            request.onConfirm(PermissionPromptResponse(false, false, false))
                        }
                    }
                    is WebExtensionPromptRequest.AfterInstallation.Permissions.Optional -> request.onConfirm(granted)
                    else -> Unit
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Extension prompt was already answered", e)
            }
        }
    }

    fun cancelInstallation() {
        installation?.cancel()
    }

    fun clearError() {
        _failed.value = false
    }

    private fun consume(request: WebExtensionPromptRequest): Boolean {
        if (store.state.webExtensionPromptRequest !== request) return false
        store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
        return true
    }

    private companion object {
        const val TAG = "ExtensionPrompt"
    }
}
