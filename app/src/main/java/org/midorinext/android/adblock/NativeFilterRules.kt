package org.midorinext.android.adblock

import java.net.URI
import java.util.Locale

internal enum class RuleDecision { NONE, BLOCK, ALLOW }

/** Safe subset of ABP network rules. Cosmetic, scriptlet and redirect rules are ignored. */
internal class NativeFilterRules(
    private val hosts: DesktopHostRules,
    private val scoped: Map<String, List<ScopedRule>>,
    val supportedCount: Int,
) {
    fun decision(url: String, siteHost: String?): RuleDecision {
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
        var suffix = host
        while (true) {
            scoped[suffix]?.forEach { rule ->
                if (rule.matches(target, siteHost)) {
                    if (rule.allow) allowed = true else blocked = true
                }
            }
            val dot = suffix.indexOf('.')
            if (dot < 0) break
            suffix = suffix.substring(dot + 1)
        }
        return when {
            allowed -> RuleDecision.ALLOW
            blocked -> RuleDecision.BLOCK
            else -> RuleDecision.NONE
        }
    }

    fun blocks(url: String, siteHost: String? = null) = decision(url, siteHost) == RuleDecision.BLOCK

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
) {
    fun matches(target: String, siteHost: String?): Boolean {
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
        if (path == null && conditions.first.isEmpty() && conditions.second.isEmpty()) {
            (if (exception) allowed else blocked).add(host)
        } else {
            scoped.getOrPut(host) { mutableListOf() } += ScopedRule(
                allow = exception, path = path,
                includeSites = conditions.first, excludeSites = conditions.second,
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

private fun parseOptions(raw: String): Pair<Set<String>, Set<String>>? {
    val included = mutableSetOf<String>()
    val excluded = mutableSetOf<String>()
    val contentTypes = setOf("script", "image", "stylesheet", "font", "media", "object", "xhr", "xmlhttprequest", "ping", "websocket", "other", "subdocument", "document")
    var positiveType = false
    var allowsSubdocument = false
    if (raw.isEmpty()) return included to excluded
    for (option in raw.split(',')) {
        val normalized = option.lowercase(Locale.ROOT)
        when {
            normalized == "third-party" || normalized == "3p" || normalized == "~first-party" || normalized == "~1p" -> Unit
            normalized == "first-party" || normalized == "1p" || normalized == "~third-party" || normalized == "~3p" -> return null
            normalized == "important" -> Unit
            normalized == "subdocument" -> { positiveType = true; allowsSubdocument = true }
            normalized == "~subdocument" || normalized == "document" -> return null
            normalized in contentTypes -> positiveType = true
            normalized.startsWith('~') && normalized.drop(1) in contentTypes -> Unit
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
    if (positiveType && !allowsSubdocument) return null
    return included to excluded
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
