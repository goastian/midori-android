package org.midorinext.android.newtab

import android.content.Context
import android.content.ComponentCallbacks2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.Icon
import mozilla.components.browser.icons.IconRequest
import mozilla.components.concept.engine.manifest.Size
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal data class HomeIconLinks(val resources: List<IconRequest.Resource>, val manifest: String?)

internal fun homeIconOrigin(url: String): String? = publicHomeUrl(url)?.let {
    val uri = URI(it)
    URI(uri.scheme.lowercase(Locale.ROOT), null, uri.host.lowercase(Locale.ROOT), uri.port, "/", null, null).toString()
}

private fun resolveIconUrl(base: String, value: String): String? = runCatching {
    if (value.isBlank()) null else publicHomeUrl(URI(base).resolve(value.trim()).toString())
}.getOrNull()

private fun iconSizes(raw: String) = raw.lowercase(Locale.ROOT).split(Regex("\\s+"))
    .mapNotNull(Size::parse).filter { it.width > 0 && it.height > 0 }

internal fun discoverHomeIconLinks(html: String, pageUrl: String): HomeIconLinks {
    val attributes = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    fun values(tag: String) = attributes.findAll(tag).associate { match ->
        match.groupValues[1].lowercase(Locale.ROOT) to match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
            .replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"")
    }
    val head = html.take(Regex("</head>", RegexOption.IGNORE_CASE).find(html)?.range?.first ?: html.length)
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<script\\b.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    val base = Regex("<base\\b[^>]*>", RegexOption.IGNORE_CASE).find(head)?.value?.let(::values)
        ?.get("href")?.let { resolveIconUrl(pageUrl, it) } ?: pageUrl
    val resources = mutableListOf<IconRequest.Resource>()
    var manifest: String? = null
    Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(head).take(64).forEach { match ->
        val attrs = values(match.value)
        val rel = attrs["rel"].orEmpty().lowercase(Locale.ROOT).split(Regex("\\s+"))
        val url = attrs["href"]?.let { resolveIconUrl(base, it) } ?: return@forEach
        when {
            "manifest" in rel -> if (manifest == null) manifest = url
            "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel -> resources +=
                IconRequest.Resource(url, IconRequest.Resource.Type.APPLE_TOUCH_ICON, iconSizes(attrs["sizes"].orEmpty()), attrs["type"])
            "icon" in rel -> resources +=
                IconRequest.Resource(url, IconRequest.Resource.Type.FAVICON, iconSizes(attrs["sizes"].orEmpty()), attrs["type"])
        }
    }
    return HomeIconLinks(resources.distinctBy { it.url }, manifest)
}

internal fun discoverManifestIcons(json: String, manifestUrl: String): List<IconRequest.Resource> {
    val icons = JSONObject(json).optJSONArray("icons") ?: return emptyList()
    return (0 until minOf(icons.length(), 32)).mapNotNull { index ->
        val icon = icons.optJSONObject(index) ?: return@mapNotNull null
        val purpose = icon.optString("purpose", "any").split(Regex("\\s+"))
        if ("any" !in purpose && "maskable" !in purpose) return@mapNotNull null
        val url = resolveIconUrl(manifestUrl, icon.optString("src"))?.takeIf { icon.optString("src").isNotBlank() }
            ?: return@mapNotNull null
        val type = runCatching { IconRequest.Resource.Type.valueOf(icon.optString("iconType")) }
            .getOrDefault(IconRequest.Resource.Type.MANIFEST_ICON)
        IconRequest.Resource(url, type,
            iconSizes(icon.optString("sizes")), icon.optString("type").ifBlank { null })
    }.distinctBy { it.url }
}

internal fun rankHomeIcons(resources: List<IconRequest.Resource>) = resources.sortedByDescending { resource ->
    if (resource.mimeType == "image/svg+xml") Int.MAX_VALUE
    else resource.sizes.maxOfOrNull { it.minLength } ?: when (resource.type) {
        IconRequest.Resource.Type.APPLE_TOUCH_ICON, IconRequest.Resource.Type.MANIFEST_ICON -> 192
        else -> 0
    }
}

@Singleton
class HomeFaviconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clientProvider: dagger.Lazy<Client>,
) {
    private val downloads = Semaphore(3)
    private val memory = HomeFaviconMemoryCache<Icon>(sizeOf = { it.bitmap.allocationByteCount })
    private val iconLoader = lazy { BrowserIcons(context, clientProvider.get(), preparers = emptyList()) }
    private val icons by iconLoader

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) memory.clear()
        if (iconLoader.isInitialized()) icons.onTrimMemory(level)
    }

    fun cached(url: String): Icon? = homeIconOrigin(url)?.let(memory::peek)

    suspend fun load(url: String): Icon? = withContext(Dispatchers.IO) {
        val origin = homeIconOrigin(url) ?: return@withContext null
        memory.getOrLoad(origin) { downloads.withPermit {
            try {
                val resources = resources(url, origin)
                ensureActive()
                for (resource in rankHomeIcons(resources)) {
                    ensureActive()
                    val loading = icons.loadIcon(IconRequest(origin, IconRequest.Size.LAUNCHER, listOf(resource)))
                    try {
                        val icon = loading.await()
                        if (icon.source != Icon.Source.GENERATOR) return@withPermit icon
                    } finally {
                        loading.cancel()
                    }
                }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } }
    }

    private suspend fun resources(url: String, origin: String): List<IconRequest.Resource> {
        val directory = File(context.cacheDir, "home-favicon-links").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(directory, "$key.json")
        val cachedJson = runCatching { if (cache.isFile) JSONObject(cache.readText()) else null }.getOrNull()
        val cached = runCatching { cachedJson?.let { discoverManifestIcons(it.toString(), origin) }.orEmpty() }
            .getOrDefault(emptyList())
        val lifetime = if (cachedJson?.optBoolean("discovered", true) == true) TimeUnit.DAYS.toMillis(7)
            else TimeUnit.HOURS.toMillis(1)
        if (cached.isNotEmpty() && System.currentTimeMillis() - cache.lastModified() < lifetime) return cached
        val discovered = runCatching {
            val (pageUrl, html) = fetchText(url)
            val links = discoverHomeIconLinks(html, pageUrl)
            val manifestIcons = links.manifest?.let { manifest -> runCatching {
                val (manifestUrl, json) = fetchText(manifest)
                discoverManifestIcons(json, manifestUrl)
            }.getOrDefault(emptyList()) }.orEmpty()
            manifestIcons + links.resources
        }.getOrDefault(emptyList())
        val fallbacks = listOf(
            IconRequest.Resource(origin + "apple-touch-icon.png", IconRequest.Resource.Type.APPLE_TOUCH_ICON),
            IconRequest.Resource(origin + "apple-touch-icon-precomposed.png", IconRequest.Resource.Type.APPLE_TOUCH_ICON),
            IconRequest.Resource(origin + "favicon.ico", IconRequest.Resource.Type.FAVICON),
        )
        val hasLargeIcon = (discovered + cached).any { resource ->
            resource.type == IconRequest.Resource.Type.APPLE_TOUCH_ICON ||
                resource.sizes.any { it.minLength >= 96 } || resource.mimeType == "image/svg+xml"
        }
        val resources = (discovered + cached + if (hasLargeIcon) fallbacks.takeLast(1) else fallbacks).distinctBy { it.url }
        currentCoroutineContext().ensureActive()
        if (discovered.isNotEmpty() || cached.isEmpty()) runCatching {
            val json = JSONObject().put("discovered", discovered.isNotEmpty()).put("icons", JSONArray(resources.map { resource -> JSONObject()
                .put("src", resource.url).put("sizes", resource.sizes.joinToString(" ")).put("iconType", resource.type.name)
                .put("type", resource.mimeType.orEmpty()) }))
            val pending = File.createTempFile("icons-", ".pending", directory)
            try { pending.writeText(json.toString()); check(pending.renameTo(cache)) }
            finally { pending.delete() }
            directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
                ?.drop(80)?.forEach { it.delete() }
        }
        return resources
    }

    private suspend fun fetchText(url: String): Pair<String, String> {
        val coroutineContext = currentCoroutineContext()
        val request = Request(url, connectTimeout = 2L to TimeUnit.SECONDS, readTimeout = 4L to TimeUnit.SECONDS,
            cookiePolicy = Request.CookiePolicy.OMIT)
        return clientProvider.get().fetch(request).use { response ->
            check(response.isSuccess)
            response.body.useBufferedReader { reader ->
                val buffer = CharArray(4096)
                val text = StringBuilder()
                while (text.length < 262_144) {
                    coroutineContext.ensureActive()
                    val count = reader.read(buffer, 0, minOf(buffer.size, 262_144 - text.length))
                    if (count == -1) break
                    text.append(buffer, 0, count)
                    if (text.contains("</head>", ignoreCase = true)) break
                }
                response.url to text.toString()
            }
        }
    }
}
