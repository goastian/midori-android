package org.midorinext.android

import android.content.Context
import org.midorinext.android.ext.getMidoriSERPCategory
import org.midorinext.android.ext.getMidoriSERPSearch
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.isMidoriUrlValid
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.browser.errorpages.ErrorType
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.feature.app.links.AppLinksInterceptor
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.isMidoriUrlValid
import java.lang.Exception
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRequestInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val MidoriUseCases: MidoriUseCases
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
        if (uri.isMidoriUrl()) {
            if (!uri.isMidoriUrlValid()) {
                val path = try {
                    URI(uri).path
                } catch (e: Exception) {
                    null
                }
                val redirectUrl = MidoriUseCases.getMidoriUrl(path = path, search = uri.getMidoriSERPSearch(), category = uri.getMidoriSERPCategory())
                return RequestInterceptor.InterceptionResponse.Url(redirectUrl)
            }
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
