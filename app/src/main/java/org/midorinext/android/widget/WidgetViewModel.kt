package org.midorinext.android.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.ext.getMidoriSERPCategory
import org.midorinext.android.stats.Datahub
import org.midorinext.android.suggest.providers.ClipboardProvider
import org.midorinext.android.suggest.providers.DomainProvider
import org.midorinext.android.suggest.providers.MidoriSuggestProvider
import org.midorinext.android.ui.browser.toolbar.ToolbarState
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.permission.PermissionRequest
import javax.inject.Inject

@HiltViewModel
class WidgetViewModel @Inject constructor(
    val engine: Engine,
    val datahub: Datahub,
    val MidoriUseCases: MidoriUseCases,
    clipboardProvider: ClipboardProvider,
    midoriSuggestProvider: MidoriSuggestProvider,
    domainProvider: DomainProvider,
    browserIcons: BrowserIcons,
)  : ViewModel() {
    val toolbarState: ToolbarState = ToolbarState(
        browserIcons, datahub, listOf(clipboardProvider, midoriSuggestProvider, domainProvider), viewModelScope
    )

    var currentCategory: String? by mutableStateOf(null)

    val session = engine.createSession().also {
        it.register(object: EngineSession.Observer {
            override fun onLocationChange(url: String, hasUserGesture: Boolean) {
                currentCategory = url.getMidoriSERPCategory()
            }
            override fun onAppPermissionRequest(permissionRequest: PermissionRequest) = Unit
            override fun onContentPermissionRequest(permissionRequest: PermissionRequest) = Unit
        })
    }

    var currentSearch: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            snapshotFlow { currentSearch }
                .distinctUntilChanged()
                .filterNotNull()
                .onEach { search ->
                    session.loadUrl(MidoriUseCases.getMidoriUrl(null, search, currentCategory, true))
                }
                .collect()
        }
    }

    fun commitSearch() {
        currentSearch = toolbarState.text.text
    }

    fun resetSearch() {
        toolbarState.updateText("")
        toolbarState.updateFocus(true)
        currentSearch = null
        currentCategory = null
    }
}