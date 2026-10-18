package org.midorinext.android.adblock

import java.util.Locale

private val unsupportedCosmeticOperator = Regex(":(?:has-text|contains|matches-[\\w-]+|upward|xpath|remove|style|watch-attr|others|if|if-not)\\(")
private val cosmeticDomain = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")

internal data class CosmeticSelection(
    val rules: List<CosmeticRule> = emptyList(),
    val enabled: Boolean = true,
    val genericEnabled: Boolean = true,
)

internal data class CosmeticRule(
    val selector: String,
    val exception: Boolean,
    val included: Set<String>,
    val excluded: Set<String>,
) {
    fun matches(host: String) = excluded.none { host == it || host.endsWith(".$it") }
}

internal class CosmeticFilterRules(val rules: List<CosmeticRule>) {
    private val generic = rules.filter { it.included.isEmpty() }
    private val scoped = buildMap<String, MutableList<CosmeticRule>> {
        rules.forEach { rule -> rule.included.forEach { getOrPut(it) { mutableListOf() }.add(rule) } }
    }

    fun forHost(host: String): Sequence<CosmeticRule> = sequence {
        yieldAll(generic.filter { it.matches(host) })
        var suffix = host
        while (true) {
            yieldAll(scoped[suffix].orEmpty().filter { it.matches(host) })
            val dot = suffix.indexOf('.')
            if (dot < 0) break
            suffix = suffix.substring(dot + 1)
        }
    }
}

internal fun parseCosmeticRule(line: String): CosmeticRule? {
    val exception = line.contains("#@#")
    val marker = if (exception) "#@#" else "##"
    val index = line.indexOf(marker)
    if (index < 0) return null
    val selector = line.substring(index + marker.length).trim()
    if (selector.isEmpty() || selector.startsWith("+js(") || selector.startsWith('^') ||
        selector.any { it in "{}\r\n" } ||
        unsupportedCosmeticOperator.containsMatchIn(selector)) return null
    val included = mutableSetOf<String>()
    val excluded = mutableSetOf<String>()
    val scope = line.substring(0, index)
    if (scope.isNotEmpty()) {
        for (entry in scope.split(',')) {
            val domain = entry.removePrefix("~").lowercase(Locale.ROOT)
            if (!cosmeticDomain.matches(domain) || domain.contains("..")) return null
            (if (entry.startsWith('~')) excluded else included).add(domain)
        }
    }
    return CosmeticRule(selector, exception, included, excluded)
}
