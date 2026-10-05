package org.midorinext.android.pwa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.manifest.WebAppManifest
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flow
import org.midorinext.android.R
import org.midorinext.android.intent.IntentReceiverActivity
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.ui.browser.mozaccompose.EngineView
import org.midorinext.android.ui.browser.mozaccompose.SessionFeature
import org.midorinext.android.ui.browser.mozaccompose.WindowFeature
import org.midorinext.android.ui.browser.mozaccompose.permissions.PermissionsFeature
import org.midorinext.android.ui.browser.mozaccompose.prompts.PromptFeature
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import mozilla.components.browser.engine.gecko.permission.GeckoSitePermissionsStorage
import javax.inject.Inject

@AndroidEntryPoint
class WebAppActivity : AppCompatActivity() {
    @Inject lateinit var repository: WebAppRepository
    @Inject lateinit var engine: Engine
    @Inject lateinit var preferences: AppPreferencesRepository
    @Inject lateinit var permissionsStorage: GeckoSitePermissionsStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent?.dataString ?: return finish()
        lifecycleScope.launch {
            val app = repository.get(startUrl)
            val manifest = repository.manifest(startUrl)
            if (app != null && !app.enabled) {
                Toast.makeText(this@WebAppActivity, R.string.pwa_disabled_message, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            if (app == null || manifest == null) {
                openInBrowser(startUrl)
                finish()
                return@launch
            }
            if (manifest.display == WebAppManifest.DisplayMode.FULLSCREEN) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.systemBars())
            }
            setTaskDescription(android.app.ActivityManager.TaskDescription(app.name))
            setContentView(androidx.compose.ui.platform.ComposeView(this@WebAppActivity).apply {
                setContent {
                    MidoriBrowserTheme {
                        WebAppPage(
                            app = app,
                            manifest = manifest,
                            engine = engine,
                            repository = repository,
                            preferences = preferences,
                            permissionsStorage = permissionsStorage,
                            onOpenInBrowser = { url ->
                                openInBrowser(url)
                                finish()
                            },
                        )
                    }
                }
            })
        }
    }

    private fun openInBrowser(url: String) {
        startActivity(Intent(this, IntentReceiverActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
        })
    }
}

@Composable
private fun WebAppPage(
    app: InstalledWebApp,
    manifest: WebAppManifest,
    engine: Engine,
    repository: WebAppRepository,
    preferences: AppPreferencesRepository,
    permissionsStorage: GeckoSitePermissionsStorage,
    onOpenInBrowser: (String) -> Unit,
) {
    val store = remember(app.startUrl, engine) {
        BrowserStore(middleware = listOf(PromptMiddleware()) + EngineMiddleware.create(engine))
    }
    val tabs = remember(store) { TabsUseCases(store) }
    val sessions = remember(store) { SessionUseCases(store) }
    val selectedTab by remember(store) { store.flow().map { it.selectedTab } }
        .collectAsStateWithLifecycle(initialValue = null)
    val hasParentTab by remember(store) {
        store.flow().map { state ->
            val parentId = state.selectedTab?.parentId
            parentId != null && state.tabs.any { it.id == parentId }
        }
    }.collectAsStateWithLifecycle(initialValue = false)
    val appPreferences by preferences.flow.collectAsStateWithLifecycle(initialValue = AppPreferencesSerializer.defaultValue)

    DisposableEffect(store, app.startUrl) {
        val id = tabs.addTab(app.startUrl, title = app.name)
        store.dispatch(ContentAction.UpdateWebAppManifestAction(id, manifest))
        onDispose { tabs.removeTab(id) }
    }

    LaunchedEffect(selectedTab?.content?.url) {
        val url = selectedTab?.content?.url ?: return@LaunchedEffect
        if (url.startsWith("http") && !WebAppRepository.isWithinScope(url, app.scope)) {
            onOpenInBrowser(url)
        }
    }

    LaunchedEffect(selectedTab?.content?.webAppManifest) {
        val updated = selectedTab?.content?.webAppManifest ?: return@LaunchedEffect
        if (updated.startUrl == app.startUrl && updated != manifest) {
            repository.updateManifest(updated)
        }
    }

    androidx.activity.compose.BackHandler(enabled = hasParentTab && selectedTab?.content?.canGoBack != true) {
        selectedTab?.id?.let { tabs.removeTab(it, selectParentIfExists = true) }
    }

    WindowFeature(store = store, tabsUseCases = tabs)
    PromptFeature(store = store, exitFullscreenUseCase = sessions.exitFullscreen, appPreferences = appPreferences)
    PermissionsFeature(store = store, storage = permissionsStorage)

    EngineView(engine = engine, modifier = Modifier.fillMaxSize().safeDrawingPadding()) { engineView ->
        SessionFeature(
            engineView = engineView,
            store = store,
            canGoBack = selectedTab?.content?.canGoBack == true,
            goBackUseCase = sessions.goBack,
            goForwardUseCase = sessions.goForward,
        )
    }
}
