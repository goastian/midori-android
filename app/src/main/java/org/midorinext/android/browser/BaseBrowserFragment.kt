/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.browser

import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.withContext
import mozilla.appservices.places.BookmarkRoot
import mozilla.appservices.places.uniffi.PlacesApiException
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.CustomTabSessionState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.accounts.FxaCapability
import mozilla.components.feature.accounts.FxaWebChannelFeature
import mozilla.components.feature.app.links.AppLinksFeature
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.ContextMenuFeature
import mozilla.components.feature.downloads.DownloadsFeature
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.feature.downloads.temporary.ShareResourceFeature
import mozilla.components.feature.intent.ext.EXTRA_SESSION_ID
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.privatemode.feature.SecureWindowFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.prompts.PromptFeature.Companion.PIN_REQUEST
import mozilla.components.feature.prompts.address.AddressDelegate
import mozilla.components.feature.prompts.creditcard.CreditCardDelegate
import mozilla.components.feature.prompts.login.LoginDelegate
import mozilla.components.feature.prompts.share.ShareDelegate
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.search.SearchFeature
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.PictureInPictureFeature
import mozilla.components.feature.session.ScreenOrientationFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.webauthn.WebAuthnFeature
import mozilla.components.lib.state.ext.consumeFlow
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.service.sync.autofill.DefaultCreditCardValidationDelegate
import mozilla.components.service.sync.logins.DefaultLoginValidationDelegate
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.PermissionsFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.ktx.android.view.enterImmersiveMode
import mozilla.components.support.ktx.android.view.exitImmersiveMode
import mozilla.components.support.ktx.android.view.hideKeyboard
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import mozilla.components.ui.widgets.behavior.EngineViewClippingBehavior
import org.midorinext.android.BuildConfig
import org.midorinext.android.HomeActivity
import org.midorinext.android.IntentReceiverActivity
import org.midorinext.android.NavGraphDirections
import org.midorinext.android.OnBackLongPressedListener
import org.midorinext.android.R
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.browser.readermode.DefaultReaderModeController
import org.midorinext.android.components.FindInPageIntegration
import org.midorinext.android.components.StoreProvider
import org.midorinext.android.components.MidoriSnackbar
import org.midorinext.android.components.toolbar.BrowserFragmentState
import org.midorinext.android.components.toolbar.BrowserFragmentStore
import org.midorinext.android.components.toolbar.BrowserToolbarView
import org.midorinext.android.components.toolbar.DefaultBrowserToolbarController
import org.midorinext.android.components.toolbar.DefaultBrowserToolbarMenuController
import org.midorinext.android.components.toolbar.ToolbarIntegration
import org.midorinext.android.components.toolbar.interactor.BrowserToolbarInteractor
import org.midorinext.android.components.toolbar.interactor.DefaultBrowserToolbarInteractor
import org.midorinext.android.crashes.CrashContentIntegration
import org.midorinext.android.databinding.FragmentBrowserBinding
import org.midorinext.android.downloads.DownloadService
import org.midorinext.android.downloads.DynamicDownloadDialog
import org.midorinext.android.ext.accessibilityManager
import org.midorinext.android.ext.breadcrumb
import org.midorinext.android.ext.components
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.ext.hideToolbar
import org.midorinext.android.ext.nav
import org.midorinext.android.ext.requireComponents
import org.midorinext.android.ext.runIfFragmentIsAttached
import org.midorinext.android.ext.secure
import org.midorinext.android.ext.settings
import org.midorinext.android.home.HomeScreenViewModel
import org.midorinext.android.home.SharedViewModel
import org.midorinext.android.onboarding.MidoriOnboarding
import org.midorinext.android.perf.MarkersFragmentLifecycleCallbacks
import org.midorinext.android.settings.SupportUtils
import org.midorinext.android.settings.biometric.BiometricPromptFeature
import org.midorinext.android.theme.ThemeManager
import org.midorinext.android.utils.allowUndo
import org.midorinext.android.wifi.SitePermissionsWifiIntegration
import java.lang.ref.WeakReference
import mozilla.components.ui.widgets.behavior.ToolbarPosition as MozacToolbarPosition

/**
 * Base fragment extended by [BrowserFragment].
 * This class only contains shared code focused on the main browsing content.
 * UI code specific to the app or to custom tabs can be found in the subclasses.
 */
@Suppress("TooManyFunctions", "LargeClass")
abstract class BaseBrowserFragment :
    Fragment(),
    UserInteractionHandler,
    ActivityResultHandler,
    OnBackLongPressedListener,
    AccessibilityManager.AccessibilityStateChangeListener {

    private var _binding: FragmentBrowserBinding? = null
    protected val binding get() = _binding!!

    private lateinit var browserFragmentStore: BrowserFragmentStore
    private lateinit var browserAnimator: BrowserAnimator

    private var _browserToolbarInteractor: BrowserToolbarInteractor? = null
    protected val browserToolbarInteractor: BrowserToolbarInteractor
        get() = _browserToolbarInteractor!!

    @VisibleForTesting
    @Suppress("VariableNaming")
    internal var _browserToolbarView: BrowserToolbarView? = null
    @VisibleForTesting
    internal val browserToolbarView: BrowserToolbarView
        get() = _browserToolbarView!!

    protected val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewFeature>()
    protected val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()

    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val contextMenuFeature = ViewBoundFeatureWrapper<ContextMenuFeature>()
    private val downloadsFeature = ViewBoundFeatureWrapper<DownloadsFeature>()
    private val shareResourceFeature = ViewBoundFeatureWrapper<ShareResourceFeature>()
    private val appLinksFeature = ViewBoundFeatureWrapper<AppLinksFeature>()
    private val promptsFeature = ViewBoundFeatureWrapper<PromptFeature>()
    private val findInPageIntegration = ViewBoundFeatureWrapper<FindInPageIntegration>()
    private val toolbarIntegration = ViewBoundFeatureWrapper<ToolbarIntegration>()
    private val sitePermissionsFeature = ViewBoundFeatureWrapper<SitePermissionsFeature>()
    private val fullScreenFeature = ViewBoundFeatureWrapper<FullScreenFeature>()
    private val swipeRefreshFeature = ViewBoundFeatureWrapper<SwipeRefreshFeature>()
    private val webchannelIntegration = ViewBoundFeatureWrapper<FxaWebChannelFeature>()
    private val sitePermissionWifiIntegration =
        ViewBoundFeatureWrapper<SitePermissionsWifiIntegration>()
    private val secureWindowFeature = ViewBoundFeatureWrapper<SecureWindowFeature>()
    private var fullScreenMediaSessionFeature =
        ViewBoundFeatureWrapper<MediaSessionFullscreenFeature>()
    private val searchFeature = ViewBoundFeatureWrapper<SearchFeature>()
    private val webAuthnFeature = ViewBoundFeatureWrapper<WebAuthnFeature>()
    private val screenOrientationFeature = ViewBoundFeatureWrapper<ScreenOrientationFeature>()
    private val biometricPromptFeature = ViewBoundFeatureWrapper<BiometricPromptFeature>()
    private val crashContentIntegration = ViewBoundFeatureWrapper<CrashContentIntegration>()
    private var pipFeature: PictureInPictureFeature? = null

    var customTabSessionId: String? = null

    @VisibleForTesting
    internal var browserInitialized: Boolean = false
    private var initUIJob: Job? = null
    protected var webAppToolbarShouldBeVisible = true

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val homeViewModel: HomeScreenViewModel by activityViewModels()

    @VisibleForTesting
    internal val onboarding by lazy { MidoriOnboarding(requireContext()) }

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // DO NOT ADD ANYTHING ABOVE THIS getProfilerTime CALL!
        val profilerStartTime = requireComponents.core.engine.profiler?.getProfilerTime()

        customTabSessionId = requireArguments().getString(EXTRA_SESSION_ID)

        // Diagnostic breadcrumb for "Display already aquired" crash:
        // https://github.com/mozilla-mobile/android-components/issues/7960
        breadcrumb(
            message = "onCreateView()",
            data = mapOf(
                "customTabSessionId" to customTabSessionId.toString()
            )
        )

        _binding = FragmentBrowserBinding.inflate(inflater, container, false)

        val activity = activity as HomeActivity
        activity.themeManager.applyStatusBarTheme(activity)

        browserFragmentStore = StoreProvider.get(this) {
            BrowserFragmentStore(
                BrowserFragmentState()
            )
        }

        // DO NOT MOVE ANYTHING BELOW THIS addMarker CALL!
        requireComponents.core.engine.profiler?.addMarker(
            MarkersFragmentLifecycleCallbacks.MARKER_NAME, profilerStartTime, "BaseBrowserFragment.onCreateView",
        )
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // DO NOT ADD ANYTHING ABOVE THIS getProfilerTime CALL!
        val profilerStartTime = requireComponents.core.engine.profiler?.getProfilerTime()

        initializeUI(view)

        if (customTabSessionId == null) {
            // We currently only need this observer to navigate to home
            // in case all tabs have been removed on startup. No need to
            // this if we have a known session to display.
            observeRestoreComplete(requireComponents.core.store, findNavController())
        }

        observeTabSelection(requireComponents.core.store)

        if (!onboarding.userHasBeenOnboarded()) {
            observeTabSource(requireComponents.core.store)
        }

        requireContext().accessibilityManager.addAccessibilityStateChangeListener(this)

        // DO NOT MOVE ANYTHING BELOW THIS addMarker CALL!
        requireComponents.core.engine.profiler?.addMarker(
            MarkersFragmentLifecycleCallbacks.MARKER_NAME, profilerStartTime, "BaseBrowserFragment.onViewCreated",
        )
    }

    private fun initializeUI(view: View) {
        val tab = getCurrentTab()
        browserInitialized = if (tab != null) {
            initializeUI(view, tab)
            true
        } else {
            false
        }
    }

    @Suppress("ComplexMethod", "LongMethod", "DEPRECATION")
    // https://github.com/mozilla-mobile/fenix/issues/19920
    @CallSuper
    internal open fun initializeUI(view: View, tab: SessionState) {
        val context = requireContext()
        val store = context.components.core.store
        val activity = requireActivity() as HomeActivity

        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)

        browserAnimator = BrowserAnimator(
            fragment = WeakReference(this),
            engineView = WeakReference(binding.engineView),
            swipeRefresh = WeakReference(binding.swipeRefresh),
            viewLifecycleScope = WeakReference(viewLifecycleOwner.lifecycleScope)
        ).apply {
            beginAnimateInIfNecessary()
        }

        val openInMidoriIntent = Intent(context, IntentReceiverActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(HomeActivity.OPEN_TO_BROWSER, true)
        }

        val readerMenuController = DefaultReaderModeController(
            readerViewFeature,
            binding.readerViewControlsBar,
            isPrivate = activity.browsingModeManager.mode.isPrivate,
            onReaderModeChanged = { activity.finishActionMode() }
        )
        val browserToolbarController = DefaultBrowserToolbarController(
            store = store,
            tabsUseCases = requireComponents.useCases.tabsUseCases,
            activity = activity,
            navController = findNavController(),
            readerModeController = readerMenuController,
            engineView = binding.engineView,
            homeViewModel = homeViewModel,
            customTabSessionId = customTabSessionId,
            browserAnimator = browserAnimator,
            onTabCounterClicked = {
                thumbnailsFeature.get()?.requestScreenshot()
                findNavController().nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalTabsTrayFragment()
                )
            },
            onCloseTab = { closedSession ->
                val closedTab = store.state.findTab(closedSession.id) ?: return@DefaultBrowserToolbarController

                val snackbarMessage = if (closedTab.content.private) {
                    requireContext().getString(R.string.snackbar_private_tab_closed)
                } else {
                    requireContext().getString(R.string.snackbar_tab_closed)
                }

                viewLifecycleOwner.lifecycleScope.allowUndo(
                    binding.browserLayout,
                    snackbarMessage,
                    requireContext().getString(R.string.snackbar_deleted_undo),
                    {
                        requireComponents.useCases.tabsUseCases.undo.invoke()
                    },
                    paddedForBottomToolbar = true,
                    operation = { }
                )
            }
        )
        val browserToolbarMenuController = DefaultBrowserToolbarMenuController(
            store = store,
            activity = activity,
            navController = findNavController(),
            settings = context.settings(),
            readerModeController = readerMenuController,
            sessionFeature = sessionFeature,
            findInPageLauncher = { findInPageIntegration.withFeature { it.launch() } },
            swipeRefresh = binding.swipeRefresh,
            browserAnimator = browserAnimator,
            customTabSessionId = customTabSessionId,
            openInMidoriIntent = openInMidoriIntent,
            bookmarkTapped = { url: String, title: String ->
                viewLifecycleOwner.lifecycleScope.launch {
                    bookmarkTapped(url, title)
                }
            },
            scope = viewLifecycleOwner.lifecycleScope,
            tabCollectionStorage = requireComponents.core.tabCollectionStorage,
            topSitesStorage = requireComponents.core.topSitesStorage,
            pinnedSiteStorage = requireComponents.core.pinnedSiteStorage,
            browserStore = store
        )

        _browserToolbarInteractor = DefaultBrowserToolbarInteractor(
            browserToolbarController,
            browserToolbarMenuController
        )

        _browserToolbarView = BrowserToolbarView(
            context = context,
            container = binding.browserLayout,
            settings = context.settings(),
            interactor = browserToolbarInteractor,
            customTabSession = customTabSessionId?.let { store.state.findCustomTab(it) },
            lifecycleOwner = viewLifecycleOwner
        )

        toolbarIntegration.set(
            feature = browserToolbarView.toolbarIntegration,
            owner = this,
            view = view
        )

        findInPageIntegration.set(
            feature = FindInPageIntegration(
                store = store,
                sessionId = customTabSessionId,
                stub = binding.stubFindInPage,
                engineView = binding.engineView,
                toolbarInfo = FindInPageIntegration.ToolbarInfo(
                    browserToolbarView.view,
                    !context.settings().shouldUseFixedTopToolbar && context.settings().isDynamicToolbarEnabled,
                    !context.settings().shouldUseBottomToolbar
                )
            ),
            owner = this,
            view = view
        )

        browserToolbarView.view.display.setOnSiteSecurityClickedListener {
            showQuickSettingsDialog()
        }

        contextMenuFeature.set(
            feature = ContextMenuFeature(
                fragmentManager = parentFragmentManager,
                store = store,
                candidates = getContextMenuCandidates(context, binding.browserLayout),
                engineView = binding.engineView,
                useCases = context.components.useCases.contextMenuUseCases,
                tabId = customTabSessionId
            ),
            owner = this,
            view = view
        )

        val allowScreenshotsInPrivateMode = context.settings().allowScreenshotsInPrivateMode
        secureWindowFeature.set(
            feature = SecureWindowFeature(
                window = requireActivity().window,
                store = store,
                customTabId = customTabSessionId,
                isSecure = { !allowScreenshotsInPrivateMode && it.content.private },
                clearFlagOnStop = false
            ),
            owner = this,
            view = view
        )

        fullScreenMediaSessionFeature.set(
            feature = MediaSessionFullscreenFeature(
                requireActivity(),
                context.components.core.store,
                customTabSessionId
            ),
            owner = this,
            view = view
        )

        val shareResourceFeature = ShareResourceFeature(
            context = context.applicationContext,
            httpClient = context.components.core.client,
            store = store,
            tabId = customTabSessionId
        )

        val downloadFeature = DownloadsFeature(
            context.applicationContext,
            store = store,
            useCases = context.components.useCases.downloadUseCases,
            fragmentManager = childFragmentManager,
            tabId = customTabSessionId,
            downloadManager = FetchDownloadManager(
                context.applicationContext,
                store,
                DownloadService::class,
                notificationsDelegate = context.components.notificationsDelegate,
            ),
            shouldForwardToThirdParties = {
                PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                    context.getPreferenceKey(R.string.pref_key_external_download_manager), false
                )
            },
            promptsStyling = DownloadsFeature.PromptsStyling(
                gravity = Gravity.BOTTOM,
                shouldWidthMatchParent = true,
                positiveButtonBackgroundColor = ThemeManager.resolveAttribute(
                    R.attr.accent,
                    context
                ),
                positiveButtonTextColor = ThemeManager.resolveAttribute(
                    R.attr.textOnColorPrimary,
                    context
                ),
                positiveButtonRadius = (resources.getDimensionPixelSize(R.dimen.tab_corner_radius)).toFloat()
            ),
            onNeedToRequestPermissions = { permissions ->
                requestPermissions(permissions, REQUEST_CODE_DOWNLOAD_PERMISSIONS)
            }
        )

        downloadFeature.onDownloadStopped = { downloadState, _, downloadJobStatus ->
            // If the download is just paused, don't show any in-app notification
            if (shouldShowCompletedDownloadDialog(downloadState, downloadJobStatus)) {

                saveDownloadDialogState(
                    downloadState.sessionId,
                    downloadState,
                    downloadJobStatus
                )

                val dynamicDownloadDialog = DynamicDownloadDialog(
                    context = context,
                    downloadState = downloadState,
                    didFail = downloadJobStatus == DownloadState.Status.FAILED,
                    tryAgain = downloadFeature::tryAgain,
                    onCannotOpenFile = {
                        showCannotOpenFileError(binding.browserLayout, context, it)
                    },
                    binding = binding.viewDynamicDownloadDialog,
                    toolbarHeight = toolbarHeight
                ) { sharedViewModel.downloadDialogState.remove(downloadState.sessionId) }

                dynamicDownloadDialog.show()
                browserToolbarView.expand()
            }
        }

        resumeDownloadDialogState(
            getCurrentTab()?.id,
            store, context, toolbarHeight
        )

        this.shareResourceFeature.set(
            shareResourceFeature,
            owner = this,
            view = view
        )

        downloadsFeature.set(
            downloadFeature,
            owner = this,
            view = view
        )

        pipFeature = PictureInPictureFeature(
            store = store,
            activity = requireActivity(),
            crashReporting = context.components.analytics.crashReporter,
            tabId = customTabSessionId
        )

        appLinksFeature.set(
            feature = AppLinksFeature(
                context,
                store = store,
                sessionId = customTabSessionId,
                fragmentManager = parentFragmentManager,
                launchInApp = { context.settings().openLinksInExternalApp },
                loadUrlUseCase = context.components.useCases.sessionUseCases.loadUrl
            ),
            owner = this,
            view = view
        )

        biometricPromptFeature.set(
            feature = BiometricPromptFeature(
                context = context,
                fragment = this,
                onAuthFailure = {
                    promptsFeature.get()?.onBiometricResult(isAuthenticated = false)
                },
                onAuthSuccess = {
                    promptsFeature.get()?.onBiometricResult(isAuthenticated = true)
                }
            ),
            owner = this,
            view = view
        )

        promptsFeature.set(
            feature = PromptFeature(
                activity = activity,
                store = store,
                customTabId = customTabSessionId,
                fragmentManager = parentFragmentManager,
                tabsUseCases = requireComponents.useCases.tabsUseCases,
                fileUploadsDirCleaner = requireComponents.core.fileUploadsDirCleaner,
                creditCardValidationDelegate = DefaultCreditCardValidationDelegate(
                    context.components.core.lazyAutofillStorage
                ),
                loginValidationDelegate = DefaultLoginValidationDelegate(
                    context.components.core.lazyPasswordsStorage
                ),
                isLoginAutofillEnabled = {
                    context.settings().shouldAutofillLogins
                },
                // there is no generate password feature, add this to show the save dialog
                hideUpdateFragmentAfterSavingGeneratedPassword = { _, _ -> false },
                isSaveLoginEnabled = {
                    context.settings().shouldPromptToSaveLogins
                },
                isCreditCardAutofillEnabled = {
                    context.settings().shouldAutofillCreditCardDetails
                },
                isAddressAutofillEnabled = {
                    context.settings().addressFeature && context.settings().shouldAutofillAddressDetails
                },
                loginExceptionStorage = context.components.core.loginExceptionStorage,
                shareDelegate = object : ShareDelegate {
                    override fun showShareSheet(
                        context: Context,
                        shareData: ShareData,
                        onDismiss: () -> Unit,
                        onSuccess: () -> Unit
                    ) {
                        val directions = NavGraphDirections.actionGlobalShareFragment(
                            data = arrayOf(shareData),
                            showPage = true,
                            sessionId = getCurrentTab()?.id
                        )
                        findNavController().navigate(directions)
                    }
                },
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_PROMPT_PERMISSIONS)
                },
                loginDelegate = object : LoginDelegate {
                    override val loginPickerView
                        get() = binding.loginSelectBar
                    override val onManageLogins = {
                        browserAnimator.captureEngineViewAndDrawStatically {
                            val directions =
                                NavGraphDirections.actionGlobalSavedLoginsAuthFragment()
                            findNavController().navigate(directions)
                        }
                    }
                },
                creditCardDelegate = object : CreditCardDelegate {
                    override val creditCardPickerView
                        get() = binding.creditCardSelectBar
                    override val onManageCreditCards = {
                        val directions =
                            NavGraphDirections.actionGlobalAutofillSettingFragment()
                        findNavController().navigate(directions)
                    }
                    override val onSelectCreditCard = {
                        showBiometricPrompt(context)
                    }
                },
                addressDelegate = object : AddressDelegate {
                    override val addressPickerView
                        get() = binding.addressSelectBar
                    override val onManageAddresses = {
                        val directions = NavGraphDirections.actionGlobalAutofillSettingFragment()
                        findNavController().navigate(directions)
                    }
                }
            ),
            owner = this,
            view = view
        )

        sessionFeature.set(
            feature = SessionFeature(
                requireComponents.core.store,
                requireComponents.useCases.sessionUseCases.goBack,
                requireComponents.useCases.sessionUseCases.goForward,
                binding.engineView,
                customTabSessionId,
            ),
            owner = this,
            view = view,
        )

        crashContentIntegration.set(
            feature = CrashContentIntegration(
                browserStore = requireComponents.core.store,
                appStore = requireComponents.appStore,
                toolbar = browserToolbarView.view,
                isToolbarPlacedAtTop = !context.settings().shouldUseBottomToolbar,
                crashReporterView = binding.crashReporterView,
                components = requireComponents,
                settings = context.settings(),
                navController = findNavController(),
                sessionId = customTabSessionId
            ),
            owner = this,
            view = view
        )

        searchFeature.set(
            feature = SearchFeature(store, customTabSessionId) { request, tabId ->
                val parentSession = store.state.findTabOrCustomTab(tabId)
                val useCase = if (request.isPrivate) {
                    requireComponents.useCases.searchUseCases.newPrivateTabSearch
                } else {
                    requireComponents.useCases.searchUseCases.newTabSearch
                }

                if (parentSession is CustomTabSessionState) {
                    useCase.invoke(request.query)
                    requireActivity().startActivity(openInMidoriIntent)
                } else {
                    useCase.invoke(request.query, parentSessionId = parentSession?.id)
                }
            },
            owner = this,
            view = view
        )

        val accentHighContrastColor =
            ThemeManager.resolveAttribute(R.attr.accentHighContrast, context)

        sitePermissionsFeature.set(
            feature = SitePermissionsFeature(
                context = context,
                storage = context.components.core.geckoSitePermissionsStorage,
                fragmentManager = parentFragmentManager,
                promptsStyling = SitePermissionsFeature.PromptsStyling(
                    gravity = getAppropriateLayoutGravity(),
                    shouldWidthMatchParent = true,
                    positiveButtonBackgroundColor = accentHighContrastColor,
                    positiveButtonTextColor = R.color.photonWhite
                ),
                sessionId = customTabSessionId,
                onNeedToRequestPermissions = { permissions ->
                    requestPermissions(permissions, REQUEST_CODE_APP_PERMISSIONS)
                },
                onShouldShowRequestPermissionRationale = {
                    shouldShowRequestPermissionRationale(
                        it
                    )
                },
                store = store
            ),
            owner = this,
            view = view
        )

        sitePermissionWifiIntegration.set(
            feature = SitePermissionsWifiIntegration(
                settings = context.settings(),
                wifiConnectionMonitor = context.components.wifiConnectionMonitor
            ),
            owner = this,
            view = view
        )

        // This component feature only works on Midori when built on Mozilla infrastructure.
        if (BuildConfig.MOZILLA_OFFICIAL) {
            webAuthnFeature.set(
                feature = WebAuthnFeature(
                    engine = requireComponents.core.engine,
                    activity = requireActivity(),
                    exitFullScreen = requireComponents.useCases.sessionUseCases.exitFullscreen::invoke,
                    currentTab = { store.state.selectedTabId },
                ),
                owner = this,
                view = view,
            )
        }

        screenOrientationFeature.set(
            feature = ScreenOrientationFeature(
                engine = requireComponents.core.engine,
                activity = requireActivity()
            ),
            owner = this,
            view = view
        )

        context.settings().setSitePermissionSettingListener(viewLifecycleOwner) {
            // If the user connects to WIFI while on the BrowserFragment, this will update the
            // SitePermissionsRules (specifically autoplay) accordingly
            runIfFragmentIsAttached {
                assignSitePermissionsRules()
            }
        }
        assignSitePermissionsRules()

        fullScreenFeature.set(
            feature = FullScreenFeature(
                requireComponents.core.store,
                requireComponents.useCases.sessionUseCases,
                customTabSessionId,
                ::viewportFitChange,
                ::fullScreenChanged
            ),
            owner = this,
            view = view
        )

        closeFindInPageBarOnNavigation(store)

        store.flowScoped(viewLifecycleOwner) { flow ->
            flow.mapNotNull { state -> state.findTabOrCustomTabOrSelectedTab(customTabSessionId) }
                .distinctUntilChangedBy { tab -> tab.content.pictureInPictureEnabled }
                .collect { tab -> pipModeChanged(tab) }
        }

        binding.swipeRefresh.isEnabled = shouldPullToRefreshBeEnabled(false)

        if (binding.swipeRefresh.isEnabled) {
            val primaryTextColor =
                ThemeManager.resolveAttribute(R.attr.textPrimary, context)
            binding.swipeRefresh.setColorSchemeColors(primaryTextColor)
            swipeRefreshFeature.set(
                feature = SwipeRefreshFeature(
                    requireComponents.core.store,
                    context.components.useCases.sessionUseCases.reload,
                    binding.swipeRefresh,
                    null,
                    customTabSessionId,
                ),
                owner = this,
                view = view
            )
        }

        webchannelIntegration.set(
            feature = FxaWebChannelFeature(
                customTabSessionId,
                requireComponents.core.engine,
                requireComponents.core.store,
                requireComponents.backgroundServices.accountManager,
                requireComponents.backgroundServices.serverConfig,
                setOf(FxaCapability.CHOOSE_WHAT_TO_SYNC)
            ),
            owner = this,
            view = view
        )

        initializeEngineView(toolbarHeight)
    }

    /**
     * Shows a biometric prompt and fallback to prompting for the password.
     */
    private fun showBiometricPrompt(context: Context) {
        if (BiometricPromptFeature.canUseFeature(context)) {
            biometricPromptFeature.get()
                ?.requestAuthentication(getString(R.string.credit_cards_biometric_prompt_unlock_message))
            return
        }

        // Fallback to prompting for password with the KeyguardManager
        val manager = context.getSystemService<KeyguardManager>()
        if (manager?.isKeyguardSecure == true) {
            showPinVerification(manager)
        } else {
            // Warn that the device has not been secured
            if (context.settings().shouldShowSecurityPinWarning) {
                showPinDialogWarning(context)
            } else {
                promptsFeature.get()?.onBiometricResult(isAuthenticated = true)
            }
        }
    }

    /**
     * Shows a pin request prompt. This is only used when BiometricPrompt is unavailable.
     */
    @Suppress("Deprecation")
    private fun showPinVerification(manager: KeyguardManager) {
        val intent = manager.createConfirmDeviceCredentialIntent(
            getString(R.string.credit_cards_biometric_prompt_message_pin),
            getString(R.string.credit_cards_biometric_prompt_unlock_message)
        )
        requireActivity().startActivityForResult(intent, PIN_REQUEST)
    }

    /**
     * Shows a dialog warning about setting up a device lock PIN.
     */
    private fun showPinDialogWarning(context: Context) {
        AlertDialog.Builder(context).apply {
            setTitle(getString(R.string.credit_cards_warning_dialog_title))
            setMessage(getString(R.string.credit_cards_warning_dialog_message))

            setNegativeButton(getString(R.string.credit_cards_warning_dialog_later)) { _: DialogInterface, _ ->
                promptsFeature.get()?.onBiometricResult(isAuthenticated = false)
            }

            setPositiveButton(getString(R.string.credit_cards_warning_dialog_set_up_now)) { it: DialogInterface, _ ->
                it.dismiss()
                promptsFeature.get()?.onBiometricResult(isAuthenticated = false)
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }

            create()
        }.show().secure(activity)

        context.settings().incrementSecureWarningCount()
    }

    private fun closeFindInPageBarOnNavigation(store: BrowserStore) {
        consumeFlow(store) { flow ->
            flow.mapNotNull { state ->
                state.findCustomTabOrSelectedTab(customTabSessionId)
            }
                .ifAnyChanged {
                        tab ->
                    arrayOf(tab.content.url, tab.content.loadRequest)
                }
                .collect {
                    findInPageIntegration.onBackPressed()
                }
        }
    }

    /**
     * Preserves current state of the [DynamicDownloadDialog] to persist through tab changes and
     * other fragments navigation.
     * */
    private fun saveDownloadDialogState(
        sessionId: String?,
        downloadState: DownloadState,
        downloadJobStatus: DownloadState.Status
    ) {
        sessionId?.let { id ->
            sharedViewModel.downloadDialogState[id] = Pair(
                downloadState,
                downloadJobStatus == DownloadState.Status.FAILED
            )
        }
    }

    /**
     * Re-initializes [DynamicDownloadDialog] if the user hasn't dismissed the dialog
     * before navigating away from it's original tab.
     * onTryAgain it will use [ContentAction.UpdateDownloadAction] to re-enqueue the former failed
     * download, because [DownloadsFeature] clears any queued downloads onStop.
     * */
    @VisibleForTesting
    internal fun resumeDownloadDialogState(
        sessionId: String?,
        store: BrowserStore,
        context: Context,
        toolbarHeight: Int
    ) {
        val savedDownloadState =
            sharedViewModel.downloadDialogState[sessionId]

        if (savedDownloadState == null || sessionId == null) {
            binding.viewDynamicDownloadDialog.root.visibility = View.GONE
            return
        }

        val onTryAgain: (String) -> Unit = {
            savedDownloadState.first?.let { dlState ->
                store.dispatch(
                    ContentAction.UpdateDownloadAction(
                        sessionId, dlState.copy(skipConfirmation = true)
                    )
                )
            }
        }

        val onDismiss: () -> Unit =
            { sharedViewModel.downloadDialogState.remove(sessionId) }

        DynamicDownloadDialog(
            context = context,
            downloadState = savedDownloadState.first,
            didFail = savedDownloadState.second,
            tryAgain = onTryAgain,
            onCannotOpenFile = {
                showCannotOpenFileError(binding.browserLayout, context, it)
            },
            binding = binding.viewDynamicDownloadDialog,
            toolbarHeight = toolbarHeight,
            onDismiss = onDismiss
        ).show()

        browserToolbarView.expand()
    }

    @VisibleForTesting
    internal fun shouldPullToRefreshBeEnabled(inFullScreen: Boolean): Boolean {
        return requireContext().settings().isPullToRefreshEnabledInBrowser && !inFullScreen
    }

    @VisibleForTesting
    internal fun initializeEngineView(toolbarHeight: Int) {
        val context = requireContext()

        if (!context.settings().shouldUseFixedTopToolbar && context.settings().isDynamicToolbarEnabled) {
            getEngineView().setDynamicToolbarMaxHeight(toolbarHeight)

            val toolbarPosition = if (context.settings().shouldUseBottomToolbar) {
                MozacToolbarPosition.BOTTOM
            } else {
                MozacToolbarPosition.TOP
            }
            (getSwipeRefreshLayout().layoutParams as CoordinatorLayout.LayoutParams).behavior =
                EngineViewClippingBehavior(
                    context,
                    null,
                    getSwipeRefreshLayout(),
                    toolbarHeight,
                    toolbarPosition
                )
        } else {
            // Ensure webpage's bottom elements are aligned to the very bottom of the engineView.
            getEngineView().setDynamicToolbarMaxHeight(0)

            // Effectively place the engineView on top/below of the toolbar if that is not dynamic.
            val swipeRefreshParams =
                getSwipeRefreshLayout().layoutParams as CoordinatorLayout.LayoutParams
            if (context.settings().shouldUseBottomToolbar) {
                swipeRefreshParams.bottomMargin = toolbarHeight
            } else {
                swipeRefreshParams.topMargin = toolbarHeight
            }
        }
    }

    /**
     * Returns a list of context menu items [ContextMenuCandidate] for the context menu
     */
    protected abstract fun getContextMenuCandidates(
        context: Context,
        view: View
    ): List<ContextMenuCandidate>

    @VisibleForTesting
    internal fun observeRestoreComplete(store: BrowserStore, navController: NavController) {
        val activity = activity as HomeActivity
        consumeFlow(store) { flow ->
            flow.map { state -> state.restoreComplete }
                .distinctUntilChanged()
                .collect { restored ->
                    if (restored) {
                        // Once tab restoration is complete, if there are no tabs to show in the browser, go home
                        val tabs =
                            store.state.getNormalOrPrivateTabs(
                                activity.browsingModeManager.mode.isPrivate
                            )
                        if (tabs.isEmpty() || store.state.selectedTabId == null) {
                            navController.popBackStack(R.id.homeFragment, false)
                        }
                    }
                }
        }
    }

    @VisibleForTesting
    internal fun observeTabSelection(store: BrowserStore) {
        consumeFlow(store) { flow ->
            flow.distinctUntilChangedBy {
                it.selectedTabId
            }
                .mapNotNull {
                    it.selectedTab
                }
                .collect {
                    handleTabSelected(it)
                }
        }
    }

    @VisibleForTesting
    @Suppress("ComplexCondition")
    internal fun observeTabSource(store: BrowserStore) {
        consumeFlow(store) { flow ->
            flow.mapNotNull { state ->
                state.selectedTab
            }
                .collect {
                    if (!onboarding.userHasBeenOnboarded() &&
                        it.content.loadRequest?.triggeredByRedirect != true &&
                        it.source !is SessionState.Source.External &&
                        it.content.url !in onboardingLinksList
                    ) {
                        onboarding.finish()
                    }
                }
        }
    }

    private fun handleTabSelected(selectedTab: TabSessionState) {
        if (!this.isRemoving) {
            updateThemeForSession(selectedTab)
        }

        if (browserInitialized) {
            view?.let {
                fullScreenChanged(false)
                browserToolbarView.expand()

                val toolbarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
                val context = requireContext()
                resumeDownloadDialogState(selectedTab.id, context.components.core.store, context, toolbarHeight)
            }
        } else {
            view?.let { view -> initializeUI(view) }
        }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        val components = requireComponents

        val preferredColorScheme = components.core.getPreferredColorScheme()
        if (components.core.engine.settings.preferredColorScheme != preferredColorScheme) {
            components.core.engine.settings.preferredColorScheme = preferredColorScheme
            components.useCases.sessionUseCases.reload()
        }
        hideToolbar()
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        if (findNavController().currentDestination?.id != R.id.searchDialogFragment) {
            view?.hideKeyboard()
        }
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        initUIJob?.cancel()

        requireComponents.core.store.state.findTabOrCustomTabOrSelectedTab(customTabSessionId)
            ?.let { session ->
                // If we didn't enter PiP, exit full screen on stop
                if (!session.content.pictureInPictureEnabled && fullScreenFeature.onBackPressed()) {
                    fullScreenChanged(false)
                }
            }
    }

    @CallSuper
    override fun onBackPressed(): Boolean {
        return findInPageIntegration.onBackPressed() ||
                fullScreenFeature.onBackPressed() ||
                promptsFeature.onBackPressed() ||
                sessionFeature.onBackPressed() ||
                removeSessionIfNeeded()
    }

    override fun onBackLongPressed(): Boolean {
        findNavController().navigate(
            NavGraphDirections.actionGlobalTabHistoryDialogFragment(
                activeSessionId = customTabSessionId
            )
        )
        return true
    }

    /**
     * Saves the external app session ID to be restored later in [onViewStateRestored].
     */
    final override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CUSTOM_TAB_SESSION_ID, customTabSessionId)
    }

    /**
     * Retrieves the external app session ID saved by [onSaveInstanceState].
     */
    final override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getString(KEY_CUSTOM_TAB_SESSION_ID)?.let {
            if (requireComponents.core.store.state.findCustomTab(it) != null) {
                customTabSessionId = it
            }
        }
    }

    /**
     * Forwards permission grant results to one of the features.
     */
    final override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val feature: PermissionsFeature? = when (requestCode) {
            REQUEST_CODE_DOWNLOAD_PERMISSIONS -> downloadsFeature.get()
            REQUEST_CODE_PROMPT_PERMISSIONS -> promptsFeature.get()
            REQUEST_CODE_APP_PERMISSIONS -> sitePermissionsFeature.get()
            else -> null
        }
        feature?.onPermissionsResult(permissions, grantResults)
    }

    /**
     * Forwards activity results to the [ActivityResultHandler] features.
     */
    override fun onActivityResult(requestCode: Int, data: Intent?, resultCode: Int): Boolean {
        return listOf(
            promptsFeature,
            webAuthnFeature
        ).any { it.onActivityResult(requestCode, data, resultCode) }
    }

    /**
     * Removes the session if it was opened by an ACTION_VIEW intent
     * or if it has a parent session and no more history
     */
    protected open fun removeSessionIfNeeded(): Boolean {
        getCurrentTab()?.let { session ->
            return if (session.source is SessionState.Source.External && !session.restored) {
                activity?.finish()
                requireComponents.useCases.tabsUseCases.removeTab(session.id)
                true
            } else {
                val hasParentSession = session is TabSessionState && session.parentId != null
                if (hasParentSession) {
                    requireComponents.useCases.tabsUseCases.removeTab(session.id, selectParentIfExists = true)
                }
                // We want to return to home if this session didn't have a parent session to select.
                val goToOverview = !hasParentSession
                !goToOverview
            }
        }
        return false
    }

    protected abstract fun navToQuickSettingsSheet(
        tab: SessionState,
        sitePermissions: SitePermissions?
    )

    /**
     * Returns the layout [android.view.Gravity] for the quick settings and ETP dialog.
     */
    protected fun getAppropriateLayoutGravity(): Int =
        requireComponents.settings.toolbarPosition.androidGravity

    /**
     * Updates the site permissions rules based on user settings.
     */
    private fun assignSitePermissionsRules() {
        val rules = requireComponents.settings.getSitePermissionsCustomSettingsRules()

        sitePermissionsFeature.withFeature {
            it.sitePermissionsRules = rules
        }
    }

    /**
     * Displays the quick settings dialog,
     * which lets the user control tracking protection and site settings.
     */
    private fun showQuickSettingsDialog() {
        val tab = getCurrentTab() ?: return
        viewLifecycleOwner.lifecycleScope.launch(Main) {
            val sitePermissions: SitePermissions? = tab.content.url.getOrigin()?.let { origin ->
                val storage = requireComponents.core.permissionStorage
                storage.findSitePermissionsBy(origin, tab.content.private)
            }

            view?.let {
                navToQuickSettingsSheet(tab, sitePermissions)
            }
        }
    }

    /**
     * Set the activity normal/private theme to match the current session.
     */
    @VisibleForTesting
    internal fun updateThemeForSession(session: SessionState) {
        val sessionMode = BrowsingMode.fromBoolean(session.content.private)
        requireComponents.strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
            requireComponents.strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
                (activity as HomeActivity).browsingModeManager.mode = sessionMode
            }
        }
    }

    @VisibleForTesting
    internal fun getCurrentTab(): SessionState? {
        return requireComponents.core.store.state.findCustomTabOrSelectedTab(customTabSessionId)
    }

    private suspend fun bookmarkTapped(sessionUrl: String, sessionTitle: String) = withContext(IO) {
        val bookmarksStorage = requireComponents.core.bookmarksStorage
        val existing =
            bookmarksStorage.getBookmarksWithUrl(sessionUrl).firstOrNull { it.url == sessionUrl }
        if (existing != null) {
            // Bookmark exists, go to edit fragment
            withContext(Main) {
                nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalBookmarkEditFragment(existing.guid, true)
                )
            }
        } else {
            // Save bookmark, then go to edit fragment
            try {
                val guid = bookmarksStorage.addItem(
                    BookmarkRoot.Mobile.id,
                    url = sessionUrl,
                    title = sessionTitle,
                    position = null
                )

                withContext(Main) {
                    view?.let {
                        MidoriSnackbar.make(
                            view = binding.browserLayout,
                            duration = MidoriSnackbar.LENGTH_LONG,
                            isDisplayedWithBrowserToolbar = true
                        )
                            .setText(getString(R.string.bookmark_saved_snackbar))
                            .setAction(getString(R.string.edit_bookmark_snackbar_action)) {
                                nav(
                                    R.id.browserFragment,
                                    BrowserFragmentDirections.actionGlobalBookmarkEditFragment(
                                        guid,
                                        true
                                    )
                                )
                            }
                            .show()
                    }
                }
            } catch (e: PlacesApiException.UrlParseFailed) {
                withContext(Main) {

                    view?.let {
                        MidoriSnackbar.make(
                            view = binding.browserLayout,
                            duration = MidoriSnackbar.LENGTH_LONG,
                            isDisplayedWithBrowserToolbar = true
                        )
                            .setText(getString(R.string.bookmark_invalid_url_error))
                            .show()
                    }
                }
            }
        }
    }

    override fun onHomePressed() = pipFeature?.onHomePressed() ?: false

    /**
     * Exit fullscreen mode when exiting PIP mode
     */
    private fun pipModeChanged(session: SessionState) {
        if (!session.content.pictureInPictureEnabled && session.content.fullScreen && isAdded) {
            onBackPressed()
            fullScreenChanged(false)
        }
    }

    final override fun onPictureInPictureModeChanged(enabled: Boolean) {
        pipFeature?.onPictureInPictureModeChanged(enabled)
    }

    private fun viewportFitChange(layoutInDisplayCutoutMode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val layoutParams = activity?.window?.attributes
            layoutParams?.layoutInDisplayCutoutMode = layoutInDisplayCutoutMode
            activity?.window?.attributes = layoutParams
        }
    }

    @VisibleForTesting
    internal fun fullScreenChanged(inFullScreen: Boolean) {
        if (inFullScreen) {
            // Close find in page bar if opened
            findInPageIntegration.onBackPressed()
            MidoriSnackbar.make(
                view = binding.browserLayout,
                duration = Snackbar.LENGTH_SHORT,
                isDisplayedWithBrowserToolbar = false
            )
                .setText(getString(R.string.full_screen_notification))
                .show()
            activity?.enterImmersiveMode()
            browserToolbarView.collapse()
            browserToolbarView.view.isVisible = false
            val browserEngine = binding.swipeRefresh.layoutParams as CoordinatorLayout.LayoutParams
            browserEngine.bottomMargin = 0
            browserEngine.topMargin = 0
            binding.swipeRefresh.translationY = 0f

            binding.engineView.setDynamicToolbarMaxHeight(0)
            // Without this, fullscreen has a margin at the top.
            binding.engineView.setVerticalClipping(0)
        } else {
            activity?.exitImmersiveMode()
            (activity as? HomeActivity)?.let { activity ->
                activity.themeManager.applyStatusBarTheme(activity)
            }
            if (webAppToolbarShouldBeVisible) {
                browserToolbarView.view.isVisible = true
                val toolbarHeight = resources.getDimensionPixelSize(R.dimen.browser_toolbar_height)
                initializeEngineView(toolbarHeight)
                browserToolbarView.expand()
            }
        }

        binding.swipeRefresh.isEnabled = shouldPullToRefreshBeEnabled(inFullScreen)
    }

    /*
     * Dereference these views when the fragment view is destroyed to prevent memory leaks
     */
    override fun onDestroyView() {
        super.onDestroyView()

        // Diagnostic breadcrumb for "Display already aquired" crash:
        // https://github.com/mozilla-mobile/android-components/issues/7960
        breadcrumb(
            message = "onDestroyView()"
        )

        requireContext().accessibilityManager.removeAccessibilityStateChangeListener(this)
        _browserToolbarView = null
        _browserToolbarInteractor = null
        _binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Diagnostic breadcrumb for "Display already aquired" crash:
        // https://github.com/mozilla-mobile/android-components/issues/7960
        breadcrumb(
            message = "onAttach()"
        )
    }

    override fun onDetach() {
        super.onDetach()

        // Diagnostic breadcrumb for "Display already aquired" crash:
        // https://github.com/mozilla-mobile/android-components/issues/7960
        breadcrumb(
            message = "onDetach()"
        )
    }

    private fun showCannotOpenFileError(
        view: View,
        context: Context,
        downloadState: DownloadState
    ) {
        MidoriSnackbar.make(
            view = view,
            duration = Snackbar.LENGTH_SHORT,
            isDisplayedWithBrowserToolbar = true
        ).setText(DynamicDownloadDialog.getCannotOpenFileErrorMessage(context, downloadState))
            .show()
    }

    companion object {
        private const val KEY_CUSTOM_TAB_SESSION_ID = "custom_tab_session_id"
        private const val REQUEST_CODE_DOWNLOAD_PERMISSIONS = 1
        private const val REQUEST_CODE_PROMPT_PERMISSIONS = 2
        private const val REQUEST_CODE_APP_PERMISSIONS = 3

        val onboardingLinksList: List<String> = listOf(
            SupportUtils.getMozillaPageUrl(SupportUtils.MozillaPage.PRIVATE_NOTICE),
            SupportUtils.getMidoriAccountSumoUrl()
        )
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) {
        if (_browserToolbarView != null) {
            browserToolbarView.setToolbarBehavior(enabled)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        _browserToolbarView?.dismissMenu()
    }

    // This method is called in response to native web extension messages from
    // content scripts (e.g the reader view extension). By the time these
    // messages are processed the fragment/view may no longer be attached.
    internal fun safeInvalidateBrowserToolbarView() {
        runIfFragmentIsAttached {
            val toolbarView = _browserToolbarView
            if (toolbarView != null) {
                toolbarView.view.invalidateActions()
                toolbarView.toolbarIntegration.invalidateMenu()
            }
        }
    }

    /**
     * Convenience method for replacing EngineView (id/engineView) in unit tests.
     */
    @VisibleForTesting
    internal fun getEngineView() = binding.engineView

    /**
     * Convenience method for replacing SwipeRefreshLayout (id/swipeRefresh) in unit tests.
     */
    @VisibleForTesting
    internal fun getSwipeRefreshLayout() = binding.swipeRefresh

    @VisibleForTesting
    internal fun shouldShowCompletedDownloadDialog(
        downloadState: DownloadState,
        status: DownloadState.Status
    ): Boolean {

        val isValidStatus = status in listOf(DownloadState.Status.COMPLETED, DownloadState.Status.FAILED)
        val isSameTab = downloadState.sessionId == getCurrentTab()?.id ?: false

        return isValidStatus && isSameTab
    }
}
