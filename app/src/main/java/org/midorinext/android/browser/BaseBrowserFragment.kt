/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.browser

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.compose.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.app.links.AppLinksFeature
import mozilla.components.feature.downloads.DownloadsFeature
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.feature.downloads.temporary.ShareResourceFeature
import mozilla.components.feature.findinpage.view.FindInPageBar
import mozilla.components.feature.findinpage.view.FindInPageView
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.prompts.PromptFeature
import org.midorinext.android.components.DefaultCreditCardValidationDelegate
import org.midorinext.android.components.DefaultLoginValidationDelegate
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.ScreenOrientationFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.feature.webauthn.WebAuthnFeature
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.enterImmersiveMode
import mozilla.components.support.ktx.android.view.exitImmersiveMode
import mozilla.components.support.utils.DefaultDownloadFileUtils
import mozilla.components.ui.widgets.behavior.EngineViewClippingBehavior
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.addons.AddonsActivity
import org.midorinext.android.addons.WebExtensionPromptFeature
import org.midorinext.android.downloads.DownloadService
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.ext.requireComponents
import org.midorinext.android.ext.share
import org.midorinext.android.pip.PictureInPictureIntegration
import org.midorinext.android.settings.SettingsActivity
import org.midorinext.android.tabs.LastTabFeature
import org.midorinext.android.theme.MidoriTheme

private const val BOTTOM_TOOLBAR_HEIGHT = 0

/**
 * Base fragment extended by [BrowserFragment] and [ExternalAppBrowserFragment].
 * This class only contains shared code focused on the main browsing content.
 * UI code specific to the app or to custom tabs can be found in the subclasses.
 */
abstract class BaseBrowserFragment :
    Fragment(),
    UserInteractionHandler,
    ActivityResultHandler {
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val toolbarIntegration = ViewBoundFeatureWrapper<ToolbarIntegration>()
    private val contextMenuIntegration = ViewBoundFeatureWrapper<ContextMenuIntegration>()
    private val downloadsFeature = ViewBoundFeatureWrapper<DownloadsFeature>()
    private val shareResourceFeature = ViewBoundFeatureWrapper<ShareResourceFeature>()
    private val appLinksFeature = ViewBoundFeatureWrapper<AppLinksFeature>()
    private val promptsFeature = ViewBoundFeatureWrapper<PromptFeature>()
    private val webExtensionPromptFeature = ViewBoundFeatureWrapper<WebExtensionPromptFeature>()
    private val fullScreenFeature = ViewBoundFeatureWrapper<FullScreenFeature>()
    private val findInPageIntegration = ViewBoundFeatureWrapper<FindInPageIntegration>()
    private val sitePermissionFeature = ViewBoundFeatureWrapper<SitePermissionsFeature>()
    private val pictureInPictureIntegration = ViewBoundFeatureWrapper<PictureInPictureIntegration>()
    private val swipeRefreshFeature = ViewBoundFeatureWrapper<SwipeRefreshFeature>()
    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()
    private val webAuthnFeature = ViewBoundFeatureWrapper<WebAuthnFeature>()
    private val fullScreenMediaSessionFeature = ViewBoundFeatureWrapper<MediaSessionFullscreenFeature>()
    private val lastTabFeature = ViewBoundFeatureWrapper<LastTabFeature>()
    private val screenOrientationFeature = ViewBoundFeatureWrapper<ScreenOrientationFeature>()

    private var showMenuSheet by mutableStateOf(false)
    private var showPasswordGeneratorSheet by mutableStateOf(false)

    private val menuSheetView: ComposeView
        get() = requireView().findViewById(R.id.menuSheetView)

    private val engineView: EngineView
        get() = requireView().findViewById<View>(R.id.engineView) as EngineView
    private val toolbar: BrowserToolbar
        get() = requireView().findViewById(R.id.toolbar)
    private val findInPageBar: FindInPageBar
        get() = requireView().findViewById(R.id.findInPageBar)
    private val swipeRefresh: SwipeRefreshLayout
        get() = requireView().findViewById(R.id.swipeRefresh)

    // Navigation bar buttons
    private val navButtonBack: ImageButton
        get() = requireView().findViewById(R.id.navButtonBack)
    private val navButtonForward: ImageButton
        get() = requireView().findViewById(R.id.navButtonForward)
    private val navButtonNewTab: ImageButton
        get() = requireView().findViewById(R.id.navButtonNewTab)
    private val navButtonTabs: ImageButton
        get() = requireView().findViewById(R.id.navButtonTabs)
    private val navButtonMenu: ImageButton
        get() = requireView().findViewById(R.id.navButtonMenu)
    private val navTabCount: TextView
        get() = requireView().findViewById(R.id.navTabCount)
    private val navigationBar: LinearLayout
        get() = requireView().findViewById(R.id.navigationBar)

    private val navigationScope get() = viewLifecycleOwner.lifecycleScope

    private val backButtonHandler: List<ViewBoundFeatureWrapper<*>> = listOf(
        fullScreenFeature,
        findInPageIntegration,
        toolbarIntegration,
        sessionFeature,
        lastTabFeature,
    )

    private val activityResultHandler: List<ViewBoundFeatureWrapper<*>> = listOf(
        webAuthnFeature,
        promptsFeature,
    )

    protected val sessionId: String?
        get() = arguments?.getString(SESSION_ID)

    protected var webAppToolbarShouldBeVisible = true

    private lateinit var requestDownloadPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestSitePermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestPromptsPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestDownloadPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                val permissions = results.keys.toTypedArray()
                val grantResults =
                    results.values
                        .map {
                        if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    }.toIntArray()
                downloadsFeature.withFeature {
                    it.onPermissionsResult(permissions, grantResults)
                }
            }

        requestSitePermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                val permissions = results.keys.toTypedArray()
                val grantResults =
                    results.values
                        .map {
                        if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    }.toIntArray()
                sitePermissionFeature.withFeature {
                    it.onPermissionsResult(permissions, grantResults)
                }
            }

        requestPromptsPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                val permissions = results.keys.toTypedArray()
                val grantResults =
                    results.values
                        .map {
                        if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    }.toIntArray()
                promptsFeature.withFeature {
                    it.onPermissionsResult(permissions, grantResults)
                }
            }
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_browser, container, false)

    abstract val shouldUseComposeUI: Boolean

    @CallSuper
    @Suppress("LongMethod")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        sessionFeature.set(
            feature = SessionFeature(
                requireComponents.core.store,
                requireComponents.useCases.sessionUseCases.goBack,
                requireComponents.useCases.sessionUseCases.goForward,
                engineView,
                sessionId,
            ),
            owner = this,
            view = view,
        )

        toolbarIntegration.set(
            feature = ToolbarIntegration(
                requireContext(),
                toolbar,
                view,
                requireComponents.core.historyStorage,
                requireComponents.core.store,
                requireComponents.useCases.sessionUseCases,
                requireComponents.useCases.tabsUseCases,
                requireComponents.useCases.webAppUseCases,
                sessionId,
                onBookmarkTapped = { title, url ->
                    org.midorinext.android.bookmarks.BookmarksFragment.toggleBookmark(
                        this,
                        title,
                        url,
                    )
                },
                onShowBookmarks = {
                    activity?.supportFragmentManager?.beginTransaction()?.apply {
                        setCustomAnimations(
                            R.anim.slide_in_right, R.anim.slide_out_left,
                            R.anim.slide_in_left, R.anim.slide_out_right,
                        )
                        replace(R.id.container, org.midorinext.android.bookmarks.BookmarksFragment())
                        commit()
                    }
                },
                onShowCollections = {
                    activity?.supportFragmentManager?.beginTransaction()?.apply {
                        setCustomAnimations(
                            R.anim.slide_in_right, R.anim.slide_out_left,
                            R.anim.slide_in_left, R.anim.slide_out_right,
                        )
                        replace(R.id.container, org.midorinext.android.collections.CollectionsFragment())
                        commit()
                    }
                },
                onShowHistory = {
                    activity?.supportFragmentManager?.beginTransaction()?.apply {
                        setCustomAnimations(
                            R.anim.slide_in_right, R.anim.slide_out_left,
                            R.anim.slide_in_left, R.anim.slide_out_right,
                        )
                        replace(R.id.container, org.midorinext.android.history.HistoryFragment())
                        commit()
                    }
                },
                onShowDownloads = {
                    activity?.supportFragmentManager?.beginTransaction()?.apply {
                        setCustomAnimations(
                            R.anim.slide_in_right, R.anim.slide_out_left,
                            R.anim.slide_in_left, R.anim.slide_out_right,
                        )
                        replace(R.id.container, org.midorinext.android.downloads.DownloadsFragment())
                        commit()
                    }
                },
            ),
            owner = this,
            view = view,
        )

        contextMenuIntegration.set(
            feature = ContextMenuIntegration(
                requireContext(),
                parentFragmentManager,
                requireComponents.core.store,
                requireComponents.useCases.tabsUseCases,
                requireComponents.useCases.contextMenuUseCases,
                engineView,
                view,
                sessionId,
            ),
            owner = this,
            view = view,
        )
        shareResourceFeature.set(
            ShareResourceFeature(
                context = requireContext().applicationContext,
                httpClient = requireComponents.core.client,
                store = requireComponents.core.store,
                tabId = sessionId,
            ),
            owner = this,
            view = view,
        )

        downloadsFeature.set(
            feature = DownloadsFeature(
                requireContext(),
                store = requireComponents.core.store,
                useCases = requireComponents.useCases.downloadsUseCases,
                fragmentManager = childFragmentManager,
                downloadFileUtils = DefaultDownloadFileUtils(
                    context = requireContext().applicationContext,
                    downloadLocation = {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                    },
                ),
                downloadManager = FetchDownloadManager(
                    requireContext().applicationContext,
                    requireComponents.core.store,
                    DownloadService::class,
                    notificationsDelegate = requireComponents.notificationsDelegate,
                ),
                onNeedToRequestPermissions = { permissions ->
                    requestDownloadPermissionsLauncher.launch(permissions)
                },
            ),
            owner = this,
            view = view,
        )

        appLinksFeature.set(
            feature = AppLinksFeature(
                requireContext(),
                store = requireComponents.core.store,
                sessionId = sessionId,
                fragmentManager = parentFragmentManager,
                launchInApp = {
                    prefs.getBoolean(requireContext().getPreferenceKey(R.string.pref_key_launch_external_app), false)
                },
            ),
            owner = this,
            view = view,
        )

        promptsFeature.set(
            feature = PromptFeature(
                fragment = this,
                store = requireComponents.core.store,
                tabsUseCases = requireComponents.useCases.tabsUseCases,
                customTabId = sessionId,
                fileUploadsDirCleaner = requireComponents.core.fileUploadsDirCleaner,
                fragmentManager = parentFragmentManager,
                creditCardValidationDelegate = DefaultCreditCardValidationDelegate(
                    requireComponents.core.lazyAutofillStorage,
                ),
                loginValidationDelegate = DefaultLoginValidationDelegate(
                    requireComponents.core.lazyLoginsStorage,
                ),
                isLoginAutofillEnabled = {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getBoolean(getString(R.string.pref_key_password_autofill), true)
                },
                isSaveLoginEnabled = {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getBoolean(getString(R.string.pref_key_password_autofill), true)
                },
                isCreditCardAutofillEnabled = {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getBoolean(getString(R.string.pref_key_card_autofill), true)
                },
                isAddressAutofillEnabled = {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getBoolean(getString(R.string.pref_key_address_autofill), true)
                },
                loginExceptionStorage = requireComponents.core.loginExceptionStorage,
                onNeedToRequestPermissions = { permissions ->
                    requestPromptsPermissionsLauncher.launch(permissions)
                },
            ),
            owner = this,
            view = view,
        )

        webExtensionPromptFeature.set(
            feature = WebExtensionPromptFeature(
                store = requireComponents.core.store,
                context = requireContext(),
                fragmentManager = parentFragmentManager,
            ),
            owner = this,
            view = view,
        )

        windowFeature.set(
            feature = WindowFeature(requireComponents.core.store, requireComponents.useCases.tabsUseCases),
            owner = this,
            view = view,
        )

        fullScreenFeature.set(
            feature = FullScreenFeature(
                store = requireComponents.core.store,
                sessionUseCases = requireComponents.useCases.sessionUseCases,
                tabId = sessionId,
                viewportFitChanged = ::viewportFitChanged,
                fullScreenChanged = ::fullScreenChanged,
            ),
            owner = this,
            view = view,
        )

        findInPageIntegration.set(
            feature = FindInPageIntegration(
                requireComponents.core.store,
                sessionId,
                findInPageBar as FindInPageView,
                engineView,
            ),
            owner = this,
            view = view,
        )

        sitePermissionFeature.set(
            feature = SitePermissionsFeature(
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                sessionId = sessionId,
                storage = requireComponents.core.geckoSitePermissionsStorage,
                onNeedToRequestPermissions = { permissions ->
                    requestSitePermissionsLauncher.launch(permissions)
                },
                onShouldShowRequestPermissionRationale = { shouldShowRequestPermissionRationale(it) },
                store = requireComponents.core.store,
            ),
            owner = this,
            view = view,
        )

        pictureInPictureIntegration.set(
            feature = PictureInPictureIntegration(
                requireComponents.core.store,
                requireActivity(),
                sessionId,
            ),
            owner = this,
            view = view,
        )

        fullScreenMediaSessionFeature.set(
            feature = MediaSessionFullscreenFeature(
                requireActivity(),
                requireComponents.core.store,
                sessionId,
            ),
            owner = this,
            view = view,
        )

        (swipeRefresh.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            behavior = EngineViewClippingBehavior(
                context = requireContext(),
                attrs = null,
                engineViewParent = swipeRefresh,
                topToolbarHeight = toolbar.height,
                bottomToolbarHeight = BOTTOM_TOOLBAR_HEIGHT,
            )
        }
        swipeRefreshFeature.set(
            feature = SwipeRefreshFeature(
                requireComponents.core.store,
                requireComponents.useCases.sessionUseCases.reload,
                swipeRefresh,
            ),
            owner = this,
            view = view,
        )

        lastTabFeature.set(
            feature = LastTabFeature(
                requireComponents.core.store,
                sessionId,
                requireComponents.useCases.tabsUseCases.removeTab,
                requireActivity(),
            ),
            owner = this,
            view = view,
        )

        screenOrientationFeature.set(
            feature = ScreenOrientationFeature(
                requireComponents.core.engine,
                requireActivity(),
            ),
            owner = this,
            view = view,
        )

        if (BuildConfig.MOZILLA_OFFICIAL) {
            webAuthnFeature.set(
                feature = WebAuthnFeature(
                    requireComponents.core.engine,
                    requireActivity(),
                    requireComponents.useCases.sessionUseCases.exitFullscreen::invoke,
                ) { requireComponents.core.store.state.selectedTabId },
                owner = this,
                view = view,
            )
        }

        val composeView = view.findViewById<ComposeView>(R.id.compose_view)
        if (shouldUseComposeUI) {
            composeView.visibility = View.VISIBLE
            composeView.setContent { BrowserToolbar() }

            val params = swipeRefresh.layoutParams as CoordinatorLayout.LayoutParams
            params.topMargin = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
            swipeRefresh.layoutParams = params
        }

        setupNavigationBar()
        setupToolbarPosition()
        observeTabState()
    }

    private fun setupNavigationBar() {
        navButtonBack.setOnClickListener {
            requireComponents.useCases.sessionUseCases.goBack.invoke()
        }

        navButtonForward.setOnClickListener {
            requireComponents.useCases.sessionUseCases.goForward.invoke()
        }

        navButtonNewTab.setOnClickListener {
            requireComponents.useCases.tabsUseCases.addTab.invoke(
                url = "about:blank",
                selectTab = true,
            )
        }

        navButtonMenu.setOnClickListener {
            showMenuBottomSheet()
        }

        setupMenuSheetView()
    }

    private fun showMenuBottomSheet() {
        showMenuSheet = true
        menuSheetView.visibility = View.VISIBLE
    }

    private fun hideMenuBottomSheet() {
        showMenuSheet = false
        menuSheetView.visibility = View.GONE
    }

    private fun setupMenuSheetView() {
        menuSheetView.setContent {
            MidoriTheme {
                if (showMenuSheet) {
                    val store = requireComponents.core.store
                    val session = store.state.selectedTab
                    MenuBottomSheet(
                        onDismiss = { hideMenuBottomSheet() },
                        hasSession = session != null,
                        canGoForward = session?.content?.canGoForward == true,
                        isDesktopMode = session?.content?.desktopMode == true,
                        isPinningSupported = requireComponents.useCases.webAppUseCases.isPinningSupported(),
                        onForward = { requireComponents.useCases.sessionUseCases.goForward.invoke() },
                        onRefresh = { requireComponents.useCases.sessionUseCases.reload.invoke() },
                        onStop = { requireComponents.useCases.sessionUseCases.stopLoading.invoke() },
                        onShare = {
                            session?.content?.url?.let { url ->
                                requireContext().share(url)
                            }
                        },
                        onBookmark = {
                            session?.let {
                                org.midorinext.android.bookmarks.BookmarksFragment.toggleBookmark(
                                    this@BaseBrowserFragment,
                                    it.content.title,
                                    it.content.url,
                                )
                            }
                        },
                        onDesktopModeChanged = { checked ->
                            requireComponents.useCases.sessionUseCases.requestDesktopSite.invoke(checked)
                        },
                        onAddToHomescreen = {
                            MainScope().launch {
                                requireComponents.useCases.webAppUseCases.addToHomescreen()
                            }
                        },
                        onFindInPage = { FindInPageIntegration.launch?.invoke() },
                        onBookmarks = {
                            activity?.supportFragmentManager?.beginTransaction()?.apply {
                                setCustomAnimations(
                                    R.anim.slide_in_right, R.anim.slide_out_left,
                                    R.anim.slide_in_left, R.anim.slide_out_right,
                                )
                                replace(R.id.container, org.midorinext.android.bookmarks.BookmarksFragment())
                                commit()
                            }
                        },
                        onCollections = {
                            activity?.supportFragmentManager?.beginTransaction()?.apply {
                                setCustomAnimations(
                                    R.anim.slide_in_right, R.anim.slide_out_left,
                                    R.anim.slide_in_left, R.anim.slide_out_right,
                                )
                                replace(R.id.container, org.midorinext.android.collections.CollectionsFragment())
                                commit()
                            }
                        },
                        onHistory = {
                            activity?.supportFragmentManager?.beginTransaction()?.apply {
                                setCustomAnimations(
                                    R.anim.slide_in_right, R.anim.slide_out_left,
                                    R.anim.slide_in_left, R.anim.slide_out_right,
                                )
                                replace(R.id.container, org.midorinext.android.history.HistoryFragment())
                                commit()
                            }
                        },
                        onDownloads = {
                            activity?.supportFragmentManager?.beginTransaction()?.apply {
                                setCustomAnimations(
                                    R.anim.slide_in_right, R.anim.slide_out_left,
                                    R.anim.slide_in_left, R.anim.slide_out_right,
                                )
                                replace(R.id.container, org.midorinext.android.downloads.DownloadsFragment())
                                commit()
                            }
                        },
                        onAddons = {
                            val intent = Intent(requireContext(), AddonsActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        },
                        onPasswordGenerator = {
                            showPasswordGeneratorSheet = true
                        },
                        onSettings = {
                            val intent = Intent(requireContext(), SettingsActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        },
                    )
                }

                if (showPasswordGeneratorSheet) {
                    org.midorinext.android.settings.personaldata.PasswordGeneratorSheet(
                        onDismiss = { showPasswordGeneratorSheet = false },
                    )
                }
            }
        }
    }

    protected open fun onTabsButtonClicked() {
        // Override in BrowserFragment to show tabs tray
    }

    private fun setupToolbarPosition() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isToolbarTop = prefs.getString(
            requireContext().getPreferenceKey(R.string.pref_key_toolbar_position),
            "bottom",
        ) == "top"

        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
        val navBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bar_height)

        val toolbarParams = toolbar.layoutParams as CoordinatorLayout.LayoutParams
        if (isToolbarTop) {
            toolbarParams.gravity = Gravity.TOP
            navigationBar.visibility = View.VISIBLE
            // Nav bar has tab counter + menu, so hide them from the top toolbar
            toolbar.hideMenuButton()
        } else {
            toolbarParams.gravity = Gravity.BOTTOM
            navigationBar.visibility = View.GONE
            // No nav bar, so show tab counter + menu on the bottom toolbar
            toolbar.showMenuButton()
        }
        toolbar.layoutParams = toolbarParams

        // Adjust content margins so they don't overlap with toolbar/navbar
        val contentViews = listOfNotNull(
            requireView().findViewById<View>(R.id.swipeRefresh),
            requireView().findViewById<View>(R.id.homeView),
            requireView().findViewById<View>(R.id.awesomeBar),
        )
        for (view in contentViews) {
            val params = view.layoutParams as CoordinatorLayout.LayoutParams
            if (isToolbarTop) {
                params.topMargin = toolbarHeight
                params.bottomMargin = navBarHeight
            } else {
                params.topMargin = 0
                params.bottomMargin = toolbarHeight
            }
            view.layoutParams = params
        }
    }

    private fun observeTabState() {
        val store = requireComponents.core.store
        val enabledColor = ContextCompat.getColor(requireContext(), R.color.toolbar_icon_color)
        val disabledColor = ContextCompat.getColor(requireContext(), R.color.toolbar_icon_disabled)

        navigationScope.launch {
            store.flow()
                .map { state ->
                    Triple(
                        state.selectedTab?.content?.canGoBack ?: false,
                        state.selectedTab?.content?.canGoForward ?: false,
                        state.tabs.size,
                    )
                }
                .distinctUntilChanged()
                .collect { (canGoBack, canGoForward, tabCount) ->
                    navButtonBack.isEnabled = canGoBack
                    navButtonBack.setColorFilter(
                        if (canGoBack) enabledColor else disabledColor,
                    )
                    navButtonForward.isEnabled = canGoForward
                    navButtonForward.setColorFilter(
                        if (canGoForward) enabledColor else disabledColor,
                    )
                    navTabCount.text = tabCount.toString()
                }
        }
    }

    private fun fullScreenChanged(enabled: Boolean) {
        if (enabled) {
            activity?.enterImmersiveMode()
            toolbar.visibility = View.GONE
            navigationBar.visibility = View.GONE
            engineView.setDynamicToolbarMaxHeight(0)
        } else {
            activity?.exitImmersiveMode()
            toolbar.visibility = View.VISIBLE
            // Restore navigationBar only if toolbar is at top
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val isToolbarTop = prefs.getString(
                requireContext().getPreferenceKey(R.string.pref_key_toolbar_position),
                "bottom",
            ) == "top"
            if (isToolbarTop) {
                navigationBar.visibility = View.VISIBLE
            }
            engineView.setDynamicToolbarMaxHeight(resources.getDimensionPixelSize(R.dimen.browser_toolbar_height))
        }
    }

    private fun viewportFitChanged(viewportFit: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requireActivity().window.attributes.layoutInDisplayCutoutMode = viewportFit
        }
    }

    @CallSuper
    override fun onBackPressed(): Boolean = backButtonHandler.any { it.onBackPressed() }

    final override fun onHomePressed(): Boolean = pictureInPictureIntegration.get()?.onHomePressed() ?: false

    final override fun onPictureInPictureModeChanged(enabled: Boolean) {
        val session = requireComponents.core.store.state.selectedTab
        val fullScreenMode = session?.content?.fullScreen ?: false
        // If we're exiting PIP mode and we're in fullscreen mode, then we should exit fullscreen mode as well.
        if (!enabled && fullScreenMode) {
            onBackPressed()
            fullScreenChanged(false)
        }
    }

    companion object {
        private const val SESSION_ID = "session_id"

        @JvmStatic
        protected fun Bundle.putSessionId(sessionId: String?) {
            putString(SESSION_ID, sessionId)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        data: Intent?,
        resultCode: Int,
    ): Boolean {
        Logger.info(
            "Fragment onActivityResult received with " +
                "requestCode: $requestCode, resultCode: $resultCode, data: $data",
        )

        return activityResultHandler.any { it.onActivityResult(requestCode, data, resultCode) }
    }
}
