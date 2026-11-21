package org.midorinext.android

import android.content.Context
import androidx.core.net.toUri
import org.midorinext.android.ext.getMidoriSERPSearch
import org.midorinext.android.ext.isRetiredSearchUrl
import org.midorinext.android.ext.urlDecode
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.adblock.DesktopHostFilter
import org.midorinext.android.usecases.SearchEngines
import org.midorinext.android.preferences.app.SearchEnginePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.concept.engine.request.ErrorType
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.feature.app.links.AppLinksInterceptor
import mozilla.components.feature.app.links.AppLinksUseCases
import mozilla.components.support.ktx.android.net.isHttpOrHttps
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRequestInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val desktopHostFilter: DesktopHostFilter,
) : RequestInterceptor {
    private val openLinksInApp: Boolean
        get() = appPreferencesRepository.snapshot.value?.openLinksInApp == true

    private val appLinksUseCases = AppLinksUseCases(context, launchInApp = { openLinksInApp })
    private val appLinksInterceptor = AppLinksInterceptor(
        context = context,
        launchInApp = { openLinksInApp },
        useCases = appLinksUseCases,
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

        val response = appLinksInterceptor.onLoadRequest(
            engineSession, uri, lastUri, hasUserGesture, isSameDomain, isRedirect, isDirectNavigation,
            isSubframeRequest
        )
        return when (response) {
            is RequestInterceptor.InterceptionResponse.AppIntent -> {
                if (openLinksInApp) {
                    appLinksUseCases.openAppLink(response.appIntent)
                } else {
                    response.fallbackUrl?.let { return webFallback(it) }
                }
                RequestInterceptor.InterceptionResponse.Deny
            }
            is RequestInterceptor.InterceptionResponse.Url -> webFallback(response.url)
            else -> response
        }
    }

    private fun webFallback(url: String): RequestInterceptor.InterceptionResponse =
        if (url.toUri().isHttpOrHttps) RequestInterceptor.InterceptionResponse.Url(url)
        else RequestInterceptor.InterceptionResponse.Deny

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
