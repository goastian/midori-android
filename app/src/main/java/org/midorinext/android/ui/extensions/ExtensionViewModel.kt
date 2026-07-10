package org.midorinext.android.ui.extensions

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.feature.addons.Addon
import mozilla.components.lib.state.ext.flow
import org.midorinext.android.R
import org.midorinext.android.extensions.ExtensionItem
import org.midorinext.android.extensions.ExtensionManager
import javax.inject.Inject

private const val TAG = "ExtensionViewModel"

enum class ExtensionUiError(@StringRes val messageRes: Int) {
    LOAD(R.string.extensions_error_load_actionable),
    ACTION(R.string.extensions_error_action_actionable)
}

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    private val extensionManager: ExtensionManager,
    private val store: BrowserStore,
) : ViewModel() {

    val webExtensionStates: StateFlow<Map<String, WebExtensionState>> = store.flow()
        .map { it.extensions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val extensions: StateFlow<List<ExtensionItem>> = extensionManager.extensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<ExtensionUiError?>(null)
    val error: StateFlow<ExtensionUiError?> = _error.asStateFlow()

    private val _installSuccess = MutableStateFlow(false)
    val installSuccess: StateFlow<Boolean> = _installSuccess.asStateFlow()

    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    private val _addonsLoading = MutableStateFlow(false)
    val addonsLoading: StateFlow<Boolean> = _addonsLoading.asStateFlow()

    private val _addonsError = MutableStateFlow<ExtensionUiError?>(null)
    val addonsError: StateFlow<ExtensionUiError?> = _addonsError.asStateFlow()

    private val _installingAddonId = MutableStateFlow<String?>(null)
    val installingAddonId: StateFlow<String?> = _installingAddonId.asStateFlow()

    init {
        loadAddons()
    }

    fun loadAddons(allowCache: Boolean = true) {
        viewModelScope.launch {
            _addonsLoading.value = true
            _addonsError.value = null
            try {
                _addons.value = extensionManager.getAddons(allowCache)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load add-ons", e)
                _addonsError.value = ExtensionUiError.LOAD
            } finally {
                _addonsLoading.value = false
            }
        }
    }

    fun installAddon(addon: Addon) {
        val url = addon.downloadUrl
        if (url.isBlank()) return
        viewModelScope.launch {
            _installingAddonId.value = addon.id
            _error.value = null

            // Watch for GeckoView install prompts and auto-approve them.
            // GeckoView dispatches a WebExtensionPromptRequest to the
            // BrowserStore when permissions need to be confirmed. Without
            // handling this, the install callback never fires.
            val promptJob = launch {
                store.flow()
                    .map { it.webExtensionPromptRequest }
                    .filterNotNull()
                    .collect { request ->
                        when (request) {
                            is WebExtensionPromptRequest.InstallationRequested -> {
                                store.dispatch(
                                    WebExtensionAction.ConsumePromptRequestWebExtensionAction
                                )
                            }
                            is WebExtensionPromptRequest.AfterInstallation.Permissions.Required -> {
                                request.onConfirm(
                                    PermissionPromptResponse(isPermissionsGranted = true)
                                )
                                store.dispatch(
                                    WebExtensionAction.ConsumePromptRequestWebExtensionAction
                                )
                            }
                            is WebExtensionPromptRequest.AfterInstallation.Permissions.Optional -> {
                                request.onConfirm(true)
                                store.dispatch(
                                    WebExtensionAction.ConsumePromptRequestWebExtensionAction
                                )
                            }
                            is WebExtensionPromptRequest.AfterInstallation.PostInstallation -> {
                                store.dispatch(
                                    WebExtensionAction.ConsumePromptRequestWebExtensionAction
                                )
                            }
                            is WebExtensionPromptRequest.BeforeInstallation.InstallationFailed -> {
                                store.dispatch(
                                    WebExtensionAction.ConsumePromptRequestWebExtensionAction
                                )
                            }
                        }
                    }
            }

            try {
                extensionManager.install(url)
                _installSuccess.value = true
                // Refresh the list to update install states
                loadAddons(allowCache = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install add-on", e)
                _error.value = ExtensionUiError.ACTION
            } finally {
                _installingAddonId.value = null
                promptJob.cancel()
            }
        }
    }

    fun install(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                extensionManager.install(url.trim())
                _installSuccess.value = true
                loadAddons(allowCache = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install extension from URL", e)
                _error.value = ExtensionUiError.ACTION
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uninstall(extensionId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                extensionManager.uninstall(extensionId)
                loadAddons(allowCache = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to uninstall extension", e)
                _error.value = ExtensionUiError.ACTION
            }
        }
    }

    fun toggleEnabled(extensionId: String, currentlyEnabled: Boolean) {
        viewModelScope.launch {
            _error.value = null
            try {
                extensionManager.setEnabled(extensionId, !currentlyEnabled)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle extension", e)
                _error.value = ExtensionUiError.ACTION
            }
        }
    }

    fun getAddonById(addonId: String): Addon? = _addons.value.find { it.id == addonId }

    fun clearError() { _error.value = null }
    fun clearInstallSuccess() { _installSuccess.value = false }

    fun triggerBrowserAction(addonId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            store.state.extensions[addonId]?.browserAction?.onClick?.invoke()
        }
    }
}
