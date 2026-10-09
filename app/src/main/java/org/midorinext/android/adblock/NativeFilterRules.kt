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

/** Safe subset of ABP network rules. Cosmetic, scriptlet and redirect rules are ignored. */
internal class NativeFilterRules(
    private val hosts: DesktopHostRules,
    private val scoped: Map<String, List<ScopedRule>>,
    val supportedCount: Int,
) {
    fun decision(
        url: String,
        siteHost: String?,
        type: String = "subdocument",
        thirdParty: Boolean = true,
    ): RuleDecision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return RuleDecision.NONE
        if (uri.scheme != "http" && uri.scheme != "https") return RuleDecision.NONE
        val host = uri.host?.lowercase(Locale.ROOT) ?: return RuleDecision.NONE
        val target = buildString {
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            uri.rawQuery?.let { append('?'); append(it) }
        }.lowercase(Locale.ROOT)
        val hostDecision = hosts.decision(url)
        var blocked = hostDecision == RuleDecision.BLOCK
        var allowed = hostDecision == RuleDecision.ALLOW
        var importantBlock = false
        var importantAllow = false
        var suffix = host
        while (true) {
            scoped[suffix]?.forEach { rule ->
                if (rule.matches(target, siteHost, resourceType(type), thirdParty)) {
                    when {
                        rule.important && rule.allow -> importantAllow = true
                        rule.important -> importantBlock = true
                        rule.allow -> allowed = true
                        else -> blocked = true
                    }
                }
            }
            val dot = suffix.indexOf('.')
            if (dot < 0) break
            suffix = suffix.substring(dot + 1)
        }
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
    val path: Regex?,
    val includeSites: Set<String>,
    val excludeSites: Set<String>,
    val includeTypes: Set<String>,
    val excludeTypes: Set<String>,
    val thirdParty: Boolean?,
    val important: Boolean,
) {
    fun matches(target: String, siteHost: String?, type: String, requestIsThirdParty: Boolean): Boolean {
        if (thirdParty != null && thirdParty != requestIsThirdParty) return false
        if (includeTypes.isNotEmpty() && type !in includeTypes) return false
        if (type in excludeTypes) return false
        if (path != null && !path.containsMatchIn(target)) return false
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
    var supported = 0
    val anchored = Regex("^\\|\\|([a-z0-9](?:[a-z0-9.-]*[a-z0-9]))(.*)$", RegexOption.IGNORE_CASE)
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.length > 4096 || line.startsWith('!') || line.startsWith('#') || line.startsWith('[')) return@forEach
        val exception = line.startsWith("@@")
        val match = anchored.matchEntire(if (exception) line.drop(2) else line) ?: return@forEach
        val host = match.groupValues[1].lowercase(Locale.ROOT)
        if (!host.contains('.') || host.contains("..")) return@forEach
        val remainder = match.groupValues[2]
        val optionIndex = remainder.indexOf('$')
        val pattern = if (optionIndex >= 0) remainder.substring(0, optionIndex) else remainder
        val options = if (optionIndex >= 0) remainder.substring(optionIndex + 1) else ""
        if (pattern.isNotEmpty() && pattern[0] != '^' && pattern[0] != '/') return@forEach
        val conditions = parseOptions(options) ?: return@forEach
        val pathText = when {
            pattern.isEmpty() || pattern == "^" -> null
            pattern.startsWith('/') -> pattern
            else -> return@forEach
        }
        val path = pathText?.let(::abpPathRegex) ?: if (pathText != null) return@forEach else null
        if (path == null && conditions == RuleConditions()) {
            (if (exception) allowed else blocked).add(host)
        } else {
            scoped.getOrPut(host) { mutableListOf() } += ScopedRule(
                allow = exception, path = path,
                includeSites = conditions.includeSites, excludeSites = conditions.excludeSites,
                includeTypes = conditions.includeTypes, excludeTypes = conditions.excludeTypes,
                thirdParty = conditions.thirdParty, important = conditions.important,
            )
        }
        supported++
        onAcceptedRule?.invoke(line)
    }
    require(supported > 0) { "No supported network rules" }
    val hostRules = DesktopHostRules(
        blocked.joinToString("\n", postfix = if (blocked.isEmpty()) "" else "\n"),
        allowed.joinToString("\n", postfix = if (allowed.isEmpty()) "" else "\n"),
    )
    return NativeFilterRules(hostRules, scoped, supported)
}

private data class RuleConditions(
    val includeSites: Set<String> = emptySet(),
    val excludeSites: Set<String> = emptySet(),
    val includeTypes: Set<String> = emptySet(),
    val excludeTypes: Set<String> = emptySet(),
    val thirdParty: Boolean? = null,
    val important: Boolean = false,
)

private fun parseOptions(raw: String): RuleConditions? {
    val included = mutableSetOf<String>()
    val excluded = mutableSetOf<String>()
    val includeTypes = mutableSetOf<String>()
    val excludeTypes = mutableSetOf<String>()
    val contentTypes = setOf("script", "image", "stylesheet", "font", "media", "object", "xhr", "xmlhttprequest", "ping", "websocket", "other", "subdocument", "document")
    var thirdParty: Boolean? = null
    var important = false
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
    return RuleConditions(included, excluded, includeTypes, excludeTypes, thirdParty, important)
}

private fun validDomain(value: String): Boolean = value.contains('.') &&
    value.matches(Regex("[a-z0-9.-]+")) && !value.contains("..")

private fun abpPathRegex(pattern: String): Regex? {
    if (pattern.length > 512 || pattern.any { it == '\n' || it == '\r' }) return null
    val exact = pattern.endsWith('|')
    val body = if (exact) pattern.dropLast(1) else pattern
    val regex = buildString {
        append('^')
        body.forEach { char ->
            when (char) {
                '*' -> append(".*")
                '^' -> append("(?:[^a-zA-Z0-9_.%-]|$)")
                else -> append(Regex.escape(char.toString()))
            }
        }
        if (exact) append('$')
    }
    return runCatching { Regex(regex, RegexOption.IGNORE_CASE) }.getOrNull()
}
