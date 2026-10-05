package org.midorinext.android.pwa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mozilla.components.concept.engine.Engine
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class WebAppsViewModel @Inject constructor(
    private val repository: WebAppRepository,
    private val engine: Engine,
) : ViewModel() {
    val apps = repository.installedApps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _installingUrl = MutableStateFlow<String?>(null)
    val installingUrl = _installingUrl.asStateFlow()

    fun install(suggestion: WebAppSuggestion, onResult: (Boolean) -> Unit) {
        if (_installingUrl.value != null) return
        viewModelScope.launch {
            _installingUrl.value = suggestion.url
            val success = runCatching { repository.installSite(suggestion.url, suggestion.name) }.isSuccess
            _installingUrl.value = null
            onResult(success)
        }
    }

    suspend fun loadIcon(url: String) = repository.loadIcon(url)

    fun setEnabled(app: InstalledWebApp, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(app, enabled) }
    }

    fun uninstall(app: InstalledWebApp) {
        viewModelScope.launch { repository.uninstall(app) }
    }

    fun clearSiteData(app: InstalledWebApp, onResult: (Boolean) -> Unit) {
        val host = runCatching { URI(app.startUrl).host }.getOrNull()
        if (host.isNullOrBlank()) {
            onResult(false)
            return
        }
        engine.clearData(
            data = Engine.BrowsingData.allSiteData(),
            host = host,
            onSuccess = { viewModelScope.launch(Dispatchers.Main) { onResult(true) } },
            onError = { viewModelScope.launch(Dispatchers.Main) { onResult(false) } },
        )
    }
}
