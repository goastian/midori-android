package org.midorinext.android.adblock

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class RuleDecision { NONE, BLOCK, ALLOW, IMPORTANT_BLOCK, IMPORTANT_ALLOW }

private val networkDomain = Regex("[a-z0-9.-]+")
private val networkContentTypes = setOf("script", "image", "stylesheet", "font", "media", "object", "xhr", "xmlhttprequest", "ping", "websocket", "other", "subdocument", "document", "elemhide", "generichide")
private val thirdPartyOptions = setOf("third-party", "3p", "~first-party", "~1p")
private val firstPartyOptions = setOf("first-party", "1p", "~third-party", "~3p")

internal fun resourceType(type: String): String = when (type) {
    "main_frame" -> "document"
    "sub_frame" -> "subdocument"
    "xhr" -> "xmlhttprequest"
    "imageset" -> "image"
    "object_subrequest" -> "object"
    else -> type
}

/** Declarative network, CSS and bundled scriptlet filtering. */
internal class NativeFilterRules(
    private val hosts: DesktopHostRules,
    private val scoped: Map<String, List<ScopedRule>>,
    val supportedCount: Int,
    private val indexed: Map<String, List<ScopedRule>> = emptyMap(),
    private val unindexed: List<ScopedRule> = emptyList(),
    val cosmetics: CosmeticFilterRules = CosmeticFilterRules(emptyList()),
    private val pageExceptions: List<ScopedRule> = emptyList(),
    val scriptlets: List<ScriptletRule> = emptyList(),
) {
    fun compiled(): JSONObject = JSONObject()
        .put("format", 1).put("count", supportedCount)
        .put("blocked", hosts.blockedHosts.text).put("allowed", hosts.allowedHosts.text)
        .put("scoped", encodeRuleIndex(scoped)).put("indexed", encodeRuleIndex(indexed))
        .put("unindexed", JSONArray(unindexed.map(ScopedRule::compiled)))
        .put("pageExceptions", JSONArray(pageExceptions.map(ScopedRule::compiled)))
        .put("cosmetics", JSONArray(cosmetics.rules.map { rule -> JSONArray(listOf(
            rule.selector, rule.exception, JSONArray(rule.included.toList()), JSONArray(rule.excluded.toList()),
        )) }))
        .put("scriptlets", JSONArray(scriptlets.map { it.toJson(false) }))

    fun documentExceptions(tracker: Boolean): List<JSONObject> = pageExceptions.filter {
        "document" in it.includeTypes && "document" !in it.excludeTypes && it.thirdParty != true && it.pattern != null
    }.map { rule ->
        JSONObject().put("pattern", rule.pattern!!.source).put("matchCase", rule.pattern.matchCase)
            .put("included", JSONArray(rule.includeSites.toList()))
            .put("excluded", JSONArray(rule.excludeSites.toList())).put("tracker", tracker)
    }

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
        fun fromCompiled(text: String): NativeFilterRules {
            val data = JSONObject(text)
            require(data.getInt("format") == 1)
            return NativeFilterRules(
                DesktopHostRules(data.getString("blocked"), data.getString("allowed")),
                decodeRuleIndex(data.getJSONObject("scoped")), data.getInt("count"),
                decodeRuleIndex(data.getJSONObject("indexed")), data.getJSONArray("unindexed").rules(),
                CosmeticFilterRules(data.getJSONArray("cosmetics").let { values ->
                    (0 until values.length()).map { index ->
                        val row = values.getJSONArray(index)
                        CosmeticRule(row.getString(0), row.getBoolean(1), row.getJSONArray(2).strings().toSet(), row.getJSONArray(3).strings().toSet())
                    }
                }),
                data.getJSONArray("pageExceptions").rules(),
                data.getJSONArray("scriptlets").let { values ->
                    (0 until values.length()).map { index ->
                        val row = values.getJSONObject(index)
                        val id = row.optString("id").takeUnless { row.isNull("id") }
                        require(id == null || ScriptletCatalog.containsId(id)) { "Outdated compiled scriptlet catalogue" }
                        ScriptletRule(id, row.getJSONArray("args").strings(),
                            row.getJSONArray("included").strings(), row.getJSONArray("excluded").strings(),
                            row.getBoolean("exception"), row.getBoolean("ublock"))
                    }
                },
            )
        }

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
    fun compiled(): JSONArray = JSONArray(listOf(
        allow, pattern?.source ?: JSONObject.NULL, pattern?.matchCase ?: false,
        JSONArray(includeSites.toList()), JSONArray(excludeSites.toList()),
        JSONArray(includeTypes.toList()), JSONArray(excludeTypes.toList()), thirdParty ?: JSONObject.NULL, important,
    ))

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

private fun encodeRuleIndex(index: Map<String, List<ScopedRule>>): JSONObject = JSONObject().also { output ->
    index.forEach { (key, rules) -> output.put(key, JSONArray(rules.map(ScopedRule::compiled))) }
}

private fun decodeRuleIndex(index: JSONObject): Map<String, List<ScopedRule>> = buildMap {
    index.keys().forEach { key -> put(key, index.getJSONArray(key).rules()) }
}

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONArray.rules(): List<ScopedRule> = (0 until length()).map { index ->
    val row = getJSONArray(index)
    ScopedRule(row.getBoolean(0), if (row.isNull(1)) null else NetworkPattern(row.getString(1), row.getBoolean(2)),
        row.getJSONArray(3).strings().toSet(), row.getJSONArray(4).strings().toSet(),
        row.getJSONArray(5).strings().toSet(), row.getJSONArray(6).strings().toSet(),
        if (row.isNull(7)) null else row.getBoolean(7), row.getBoolean(8))
}

private fun String.isDomainOrSubdomainOf(domain: String): Boolean = this == domain || endsWith(".$domain")

internal fun parseHostSource(text: String, trusted: Boolean = false, onAcceptedRule: ((String) -> Unit)? = null): NativeFilterRules {
    val blocked = hashSetOf<String>()
    val allowed = hashSetOf<String>()
    val scoped = mutableMapOf<String, MutableList<ScopedRule>>()
    val indexed = mutableMapOf<String, MutableList<ScopedRule>>()
    val unindexed = mutableListOf<ScopedRule>()
    val cosmetics = mutableListOf<CosmeticRule>()
    val pageExceptions = mutableListOf<ScopedRule>()
    val scriptlets = mutableListOf<ScriptletRule>()
    var supported = 0
    val anchored = Regex("^\\|\\|([a-z0-9](?:[a-z0-9.-]*[a-z0-9]))(.*)$", RegexOption.IGNORE_CASE)
    val disabled = activeFilterLines(text).mapNotNull { raw ->
        if (!raw.contains("badfilter")) return@mapNotNull null
        val line = raw.trim()
        val options = line.substringAfter('$', "").split(',')
        if ("badfilter" !in options) null else line.substringBefore('$') +
            options.filterNot { it == "badfilter" }.joinToString(",").let { if (it.isEmpty()) "" else "$$it" }
    }.toSet()
    activeFilterLines(text).forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.length > 32_768 || line.startsWith('!') || line.startsWith('[') || line in disabled) return@forEach
        val hostRule = line.removePrefix("@@")
        if (hostRule.startsWith("||") && hostRule.endsWith('^')) {
            val host = hostRule.substring(2, hostRule.length - 1).lowercase(Locale.ROOT)
            if (host.length <= 253 && host.contains('.') && !host.contains("..") &&
                host.firstOrNull()?.let { it in 'a'..'z' || it in '0'..'9' } == true &&
                host.lastOrNull()?.let { it in 'a'..'z' || it in '0'..'9' } == true &&
                host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }) {
                (if (line.startsWith("@@")) allowed else blocked).add(host)
                supported++
                onAcceptedRule?.invoke(line)
                return@forEach
            }
        }
        parseScriptletRule(line, trusted)?.let {
            scriptlets += it
            supported++
            onAcceptedRule?.invoke(line)
            return@forEach
        }
        if (line.length > 4096) return@forEach
        parseCosmeticRule(line)?.let {
            cosmetics += it
            supported++
            onAcceptedRule?.invoke(line)
            return@forEach
        }
        if (line.startsWith('#') || line.contains("##") || line.contains("#@#") || line.contains("#?#") ||
            line.contains("#$#") || line.contains("#%#") || line.contains("#@%#")) return@forEach
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
        blocked.sorted().joinToString("\n", postfix = if (blocked.isEmpty()) "" else "\n"),
        allowed.sorted().joinToString("\n", postfix = if (allowed.isEmpty()) "" else "\n"),
    )
    return NativeFilterRules(hostRules, scoped, supported, indexed, unindexed, CosmeticFilterRules(cosmetics), pageExceptions, scriptlets)
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
    if (raw.isEmpty()) return RuleConditions()
    val included = mutableSetOf<String>()
    val excluded = mutableSetOf<String>()
    val includeTypes = mutableSetOf<String>()
    val excludeTypes = mutableSetOf<String>()
    var thirdParty: Boolean? = null
    var important = false
    var matchCase = false
    for (option in raw.split(',')) {
        val normalized = option.lowercase(Locale.ROOT)
        when {
            normalized in thirdPartyOptions -> {
                if (thirdParty == false) return null
                thirdParty = true
            }
            normalized in firstPartyOptions -> {
                if (thirdParty == true) return null
                thirdParty = false
            }
            normalized == "important" -> important = true
            normalized == "match-case" -> matchCase = true
            normalized in networkContentTypes -> includeTypes += resourceType(normalized)
            normalized.startsWith('~') && normalized.drop(1) in networkContentTypes -> excludeTypes += resourceType(normalized.drop(1))
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
    networkDomain.matches(value) && !value.contains("..")

