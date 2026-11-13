package org.midorinext.android

import android.content.Context
import org.midorinext.android.ext.getMidoriSERPSearch
import org.midorinext.android.ext.isRetiredSearchUrl
import org.midorinext.android.ext.urlDecode
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.adblock.DesktopHostFilter
import org.midorinext.android.usecases.SearchEngines
import org.midorinext.android.preferences.app.SearchEnginePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.concept.engine.request.ErrorType
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.feature.app.links.AppLinksInterceptor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRequestInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val desktopHostFilter: DesktopHostFilter,
) : RequestInterceptor {
    private val coroutineScope = MainScope()
    private var openLinksInApp = false

    init {
        coroutineScope.launch {
            appPreferencesRepository.flow
                .map { it.openLinksInApp }
                .distinctUntilChanged()
                .onEach { openLinksInApp = it }
                .collect()
        }
    }

    private val appLinksInterceptor = AppLinksInterceptor(
        context = context,
        launchInApp = { openLinksInApp },
        launchFromInterceptor = true
    )

    override fun onLoadRequest(
        engineSession: EngineSession,
        uri: String,
        lastUri: String?,
        hasUserGesture: Boolean,
        isSameDomain: Boolean,
        isRedirect: Boolean,
        isDirectNavigation: Boolean,
        isSubframeRequest: Boolean
    ): RequestInterceptor.InterceptionResponse? {
        if (!isSubframeRequest && !isRedirect) {
            desktopHostFilter.beginPage(engineSession, uri)
        }

        if (!isSubframeRequest && uri.isRetiredSearchUrl()) {
            val search = uri.getMidoriSERPSearch()?.let { encoded ->
                runCatching(encoded::urlDecode).getOrDefault(encoded)
            }.orEmpty()
            return RequestInterceptor.InterceptionResponse.Url(SearchEngines.url(SearchEnginePreference.GOOGLE, search))
        }

        return appLinksInterceptor.onLoadRequest(
            engineSession, uri, lastUri, hasUserGesture, isSameDomain, isRedirect, isDirectNavigation,
            isSubframeRequest
        )
    }

    override fun onErrorRequest(
        session: EngineSession,
        errorType: ErrorType,
        uri: String?
    ): RequestInterceptor.ErrorResponse {
        val errorPage = ErrorPages.createUrlEncodedErrorPage(context, errorType, uri)
        return RequestInterceptor.ErrorResponse(errorPage)
    }

    override fun interceptsAppInitiatedRequests() = false
}
