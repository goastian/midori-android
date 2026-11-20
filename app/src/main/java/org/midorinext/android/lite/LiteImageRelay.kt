package org.midorinext.android.lite

import android.util.Base64
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.midorinext.android.BuildConfig
import java.net.Proxy
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal class LiteImageRelay {
    private val origin = LiteSettings.origin(BuildConfig.MIDORIPRESS_IMAGE_URL)
        ?.takeIf { it.scheme == "https" }?.toString()?.toHttpUrlOrNull()
    private val endpoint = origin?.newBuilder()?.addPathSegments("api/v1/lite/image")?.build()
    private val profileEndpoint = origin?.newBuilder()?.addPathSegments("api/v1/lite/profile")?.build()

    val configured: Boolean get() = endpoint != null

    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(1600, TimeUnit.MILLISECONDS)
            .apply {
                val pem = BuildConfig.MIDORIPRESS_IMAGE_CERTIFICATE
                if (BuildConfig.DEBUG && endpoint?.host == "127.0.0.1" && pem.isNotBlank()) {
                    val certificate = CertificateFactory.getInstance("X.509")
                        .generateCertificate(pem.byteInputStream())
                    val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(null)
                        setCertificateEntry("midori-lite-pilot", certificate)
                    }
                    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                        .apply { init(store) }
                    val trust = factory.trustManagers.single() as X509TrustManager
                    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
                    sslSocketFactory(context.socketFactory, trust)
                }
            }.build()
    }

    fun selectProfile(token: String, profile: LiteCompressionProfile): Boolean = runCatching {
        val target = profileEndpoint?.newBuilder()?.addQueryParameter("profile", profile.id)?.build()
            ?: return@runCatching false
        val request = Request.Builder().url(target)
            .header("Authorization", "Bearer $token")
            .put(ByteArray(0).toRequestBody())
            .build()
        client.newCall(request).execute().use { response ->
            response.code == 204 && response.header("X-MidoPress-Profile") == profile.id
        }
    }.getOrDefault(false)

    fun fetch(address: String, token: String, profile: LiteCompressionProfile): String? = runCatching {
        if (address.length > 512) return@runCatching null
        val origin = URI(address)
        if (origin.scheme != "https" || origin.host !in eligibleHosts || origin.port != -1 ||
            origin.rawUserInfo != null || origin.rawFragment != null ||
            (origin.rawQuery != null && origin.host !in variantHosts)
        ) return@runCatching null

        val target = endpoint?.newBuilder()?.addQueryParameter("url", address)
            ?.addQueryParameter("profile", profile.id)?.build() ?: return@runCatching null
        val request = Request.Builder().url(target)
            .header("Authorization", "Bearer $token")
            .header("Accept", "image/webp,image/jpeg,image/png")
            .header("Accept-Encoding", "identity")
            .build()

        val call = client.newCall(request)
        call.timeout().timeout(2_500, TimeUnit.MILLISECONDS)
        call.execute().use { response ->
            if (response.code != 200) return@use null
            if (response.header("X-MidoPress-Profile") != profile.id) return@use null
            val original = response.header("X-MidoPress-Original-Bytes")?.toLongOrNull() ?: return@use null
            val received = response.header("X-MidoPress-Result-Bytes")?.toLongOrNull() ?: return@use null
            if (received !in 1L..524_288L || received >= original) return@use null
            val body = response.body
            val type = body.contentType() ?: return@use null
            if (type.type != "image" || type.subtype !in imageFormats) return@use null
            val source = body.source()
            val image = source.readByteArray(received)
            if (!source.exhausted()) return@use null
            "data:image/${type.subtype};base64,${Base64.encodeToString(image, Base64.NO_WRAP)}"
        }
    }.getOrNull()

    private companion object {
        val bingThumbHosts = (1..4).flatMap { listOf("ts${it}.mm.bing.net", "tse${it}.mm.bing.net") }
        val variantHosts = setOf("i.ytimg.com", "www.bing.com", "th.bing.com") + bingThumbHosts
        val eligibleHosts = variantHosts + setOf("www.gstatic.com", "images.unsplash.com")
        val imageFormats = setOf("webp", "jpeg", "png")
    }
}
