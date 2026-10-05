package org.midorinext.android.pwa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
