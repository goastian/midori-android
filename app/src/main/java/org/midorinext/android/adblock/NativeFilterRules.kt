package org.midorinext.android.adblock

import java.net.URI
import java.util.Locale

internal enum class RuleDecision { NONE, BLOCK, ALLOW, IMPORTANT_BLOCK, IMPORTANT_ALLOW }

internal fun resourceType(type: String): String = when (type) {
    "main_frame" -> "document"
    "sub_frame" -> "subdocument"
    "xhr" -> "xmlhttprequest"
    "imageset" -> "image"
    "object_subrequest" -> "object"
    else -> type
}

/** Network and CSS filtering; executable filters and response rewriting are excluded. */
internal class NativeFilterRules(
    private val hosts: DesktopHostRules,
    private val scoped: Map<String, List<ScopedRule>>,
    val supportedCount: Int,
    private val indexed: Map<String, List<ScopedRule>> = emptyMap(),
    private val unindexed: List<ScopedRule> = emptyList(),
    val cosmetics: CosmeticFilterRules = CosmeticFilterRules(emptyList()),
    private val pageExceptions: List<ScopedRule> = emptyList(),
) {
    fun pageException(url: String, type: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        val lowercase = url.lowercase(Locale.ROOT)
        return pageExceptions.any { it.matches(url, lowercase, host, type, false) }
    }

    fun decision(
        url: String,
        siteHost: String?,
        type: String = "subdocument",
        thirdParty: Boolean = true,
    ): RuleDecision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return RuleDecision.NONE
        if (uri.scheme != "http" && uri.scheme != "https") return RuleDecision.NONE
        val host = uri.host?.lowercase(Locale.ROOT) ?: return RuleDecision.NONE
        val lowercaseUrl = url.lowercase(Locale.ROOT)
        val hostDecision = hosts.decision(url)
        var blocked = hostDecision == RuleDecision.BLOCK
        var allowed = hostDecision == RuleDecision.ALLOW
        var importantBlock = false
        var importantAllow = false
        fun evaluate(rule: ScopedRule) {
            if (rule.matches(url, lowercaseUrl, siteHost, resourceType(type), thirdParty)) {
                when {
                    rule.important && rule.allow -> importantAllow = true
                    rule.important -> importantBlock = true
                    rule.allow -> allowed = true
                    else -> blocked = true
                }
            }
        }
        var suffix = host
        while (true) {
            scoped[suffix]?.forEach(::evaluate)
            val dot = suffix.indexOf('.')
            if (dot < 0) break
            suffix = suffix.substring(dot + 1)
        }
        val seen = HashSet<String>()
        for (offset in 0..lowercaseUrl.length - NetworkPattern.INDEX_LENGTH) {
            val key = lowercaseUrl.substring(offset, offset + NetworkPattern.INDEX_LENGTH)
            if (seen.add(key)) indexed[key]?.forEach(::evaluate)
        }
        unindexed.forEach(::evaluate)
        return when {
            importantAllow -> RuleDecision.IMPORTANT_ALLOW
            importantBlock -> RuleDecision.IMPORTANT_BLOCK
            allowed -> RuleDecision.ALLOW
            blocked -> RuleDecision.BLOCK
            else -> RuleDecision.NONE
        }
    }

    fun blocks(url: String, siteHost: String? = null, type: String = "subdocument", thirdParty: Boolean = true) =
        decision(url, siteHost, type, thirdParty) in setOf(RuleDecision.BLOCK, RuleDecision.IMPORTANT_BLOCK)

    companion object {
        fun bundled(blocked: String, allowed: String): NativeFilterRules = NativeFilterRules(
            DesktopHostRules(blocked, allowed), emptyMap(), blocked.count { it == '\n' } + allowed.count { it == '\n' },
        )
    }
}

internal data class ScopedRule(
    val allow: Boolean,
    val pattern: NetworkPattern?,
    val includeSites: Set<String>,
    val excludeSites: Set<String>,
    val includeTypes: Set<String>,
    val excludeTypes: Set<String>,
    val thirdParty: Boolean?,
    val important: Boolean,
) {
    fun matches(url: String, lowercaseUrl: String, siteHost: String?, type: String, requestIsThirdParty: Boolean): Boolean {
        if (thirdParty != null && thirdParty != requestIsThirdParty) return false
        if (includeTypes.isNotEmpty() && type !in includeTypes) return false
        if (type in excludeTypes) return false
        if (pattern != null && !pattern.matches(url, lowercaseUrl)) return false
        if (includeSites.isNotEmpty() && (siteHost == null || includeSites.none { siteHost.isDomainOrSubdomainOf(it) })) return false
        if (siteHost != null && excludeSites.any { siteHost.isDomainOrSubdomainOf(it) }) return false
        return true
    }
}

private fun String.isDomainOrSubdomainOf(domain: String): Boolean = this == domain || endsWith(".$domain")

internal fun parseHostSource(text: String, onAcceptedRule: ((String) -> Unit)? = null): NativeFilterRules {
    val blocked = sortedSetOf<String>()
    val allowed = sortedSetOf<String>()
    val scoped = mutableMapOf<String, MutableList<ScopedRule>>()
    val indexed = mutableMapOf<String, MutableList<ScopedRule>>()
    val unindexed = mutableListOf<ScopedRule>()
    val cosmetics = mutableListOf<CosmeticRule>()
    val pageExceptions = mutableListOf<ScopedRule>()
    var supported = 0
    val anchored = Regex("^\\|\\|([a-z0-9](?:[a-z0-9.-]*[a-z0-9]))(.*)$", RegexOption.IGNORE_CASE)
    val disabled = text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        val options = line.substringAfter('$', "").split(',')
        if ("badfilter" !in options) null else line.substringBefore('$') +
            options.filterNot { it == "badfilter" }.joinToString(",").let { if (it.isEmpty()) "" else "$$it" }
    }.toSet()
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.length > 4096 || line.startsWith('!') || line.startsWith('[') || line in disabled) return@forEach
        parseCosmeticRule(line)?.let {
            cosmetics += it
            supported++
            onAcceptedRule?.invoke(line)
            return@forEach
        }
        if (line.startsWith('#') || line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) return@forEach
        val exception = line.startsWith("@@")
        val body = if (exception) line.drop(2) else line
        val optionIndex = body.indexOf('$')
        val patternText = if (optionIndex >= 0) body.substring(0, optionIndex) else body
        val options = if (optionIndex >= 0) body.substring(optionIndex + 1) else ""
        if (patternText.isEmpty() || patternText.length > 512 || patternText.any { it.isWhitespace() || it in "<>{}" } ||
            patternText.startsWith('/') && patternText.endsWith('/')) return@forEach
        val conditions = parseOptions(options) ?: return@forEach
        if (!exception && conditions.includeTypes.any { it in setOf("elemhide", "generichide") }) return@forEach
        val match = anchored.matchEntire(patternText)
        val host = match?.groupValues?.get(1)?.lowercase(Locale.ROOT)?.takeIf(::validDomain)
        val remainder = match?.groupValues?.get(2)
        val hostOnly = host != null && remainder == "^" && !conditions.matchCase
        val scopedHost = host?.takeIf { remainder == "^" || remainder?.startsWith('/') == true || remainder?.startsWith('^') == true }
        if (hostOnly && conditions == RuleConditions()) {
            (if (exception) allowed else blocked).add(host!!)
        } else {
            val pattern = if (hostOnly && !exception) null else NetworkPattern(patternText, conditions.matchCase)
            val rule = ScopedRule(
                allow = exception, pattern = pattern,
                includeSites = conditions.includeSites, excludeSites = conditions.excludeSites,
                includeTypes = conditions.includeTypes, excludeTypes = conditions.excludeTypes,
                thirdParty = conditions.thirdParty, important = conditions.important,
            )
            if (exception && conditions.includeTypes.any { it in setOf("document", "elemhide", "generichide") }) {
                pageExceptions += rule
            }
            when {
                scopedHost != null -> scoped.getOrPut(scopedHost) { mutableListOf() } += rule
                else -> {
                    val key = pattern!!.indexKeys().minByOrNull { indexed[it]?.size ?: 0 }
                    if (key == null) unindexed += rule else indexed.getOrPut(key) { mutableListOf() } += rule
                }
            }
        }
        supported++
        onAcceptedRule?.invoke(line)
    }
    require(supported > 0) { "No supported filter rules" }
    val hostRules = DesktopHostRules(
        blocked.joinToString("\n", postfix = if (blocked.isEmpty()) "" else "\n"),
        allowed.joinToString("\n", postfix = if (allowed.isEmpty()) "" else "\n"),
    )
    return NativeFilterRules(hostRules, scoped, supported, indexed, unindexed, CosmeticFilterRules(cosmetics), pageExceptions)
}

private data class RuleConditions(
    val includeSites: Set<String> = emptySet(),
    val excludeSites: Set<String> = emptySet(),
    val includeTypes: Set<String> = emptySet(),
    val excludeTypes: Set<String> = emptySet(),
    val thirdParty: Boolean? = null,
    val important: Boolean = false,
    val matchCase: Boolean = false,
)

private fun parseOptions(raw: String): RuleConditions? {
    val included = mutableSetOf<String>()
    val excluded = mutableSetOf<String>()
    val includeTypes = mutableSetOf<String>()
    val excludeTypes = mutableSetOf<String>()
    val contentTypes = setOf("script", "image", "stylesheet", "font", "media", "object", "xhr", "xmlhttprequest", "ping", "websocket", "other", "subdocument", "document", "elemhide", "generichide")
    var thirdParty: Boolean? = null
    var important = false
    var matchCase = false
    if (raw.isEmpty()) return RuleConditions()
    for (option in raw.split(',')) {
        val normalized = option.lowercase(Locale.ROOT)
        when {
            normalized in setOf("third-party", "3p", "~first-party", "~1p") -> {
                if (thirdParty == false) return null
                thirdParty = true
            }
            normalized in setOf("first-party", "1p", "~third-party", "~3p") -> {
                if (thirdParty == true) return null
                thirdParty = false
            }
            normalized == "important" -> important = true
            normalized == "match-case" -> matchCase = true
            normalized in contentTypes -> includeTypes += resourceType(normalized)
            normalized.startsWith('~') && normalized.drop(1) in contentTypes -> excludeTypes += resourceType(normalized.drop(1))
            normalized.startsWith("domain=") -> {
                normalized.removePrefix("domain=").split('|').forEach { domain ->
                    val value = domain.removePrefix("~")
                    if (!validDomain(value)) return null
                    (if (domain.startsWith('~')) excluded else included).add(value)
                }
            }
            else -> return null
        }
    }
    return RuleConditions(included, excluded, includeTypes, excludeTypes, thirdParty, important, matchCase)
}

private fun validDomain(value: String): Boolean = value.contains('.') &&
    value.matches(Regex("[a-z0-9.-]+")) && !value.contains("..")

