package org.midorinext.android.adblock

import java.util.Locale

internal class NetworkPattern(pattern: String, val matchCase: Boolean = false) {
    val source = pattern
    private val domainAnchor = pattern.startsWith("||")
    private val startAnchor = domainAnchor || pattern.startsWith('|')
    private val endAnchor = pattern.endsWith('|')
    private val body = pattern.removePrefix(if (domainAnchor) "||" else "|").removeSuffix("|")
        .let { if (matchCase) it else it.lowercase(Locale.ROOT) }
    private val glob = (if (startAnchor) "" else "*") + body + (if (endAnchor) "" else "*")

    fun indexKeys(): List<String> = body.lowercase(Locale.ROOT).split('*', '^')
        .flatMap { literal -> literal.windowed(INDEX_LENGTH) }.distinct()

    fun matches(url: String, lowercaseUrl: String = url.lowercase(Locale.ROOT)): Boolean {
        val target = if (matchCase) url else lowercaseUrl
        if (!domainAnchor) return matchesAt(target, 0)
        var start = target.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return false
        val hostEnd = target.indexOfAny(charArrayOf(':', '/', '?', '#'), start).let { if (it < 0) target.length else it }
        while (true) {
            if (matchesAt(target, start)) return true
            val dot = target.indexOf('.', start)
            if (dot < 0 || dot >= hostEnd) return false
            start = dot + 1
        }
    }

    private fun matchesAt(target: String, start: Int): Boolean {
        var position = start
        var rule = 0
        var star = -1
        var retry = start
        while (position < target.length) {
            val expected = glob.getOrNull(rule)
            when {
                expected == '*' -> { star = rule++; retry = position }
                expected == target[position] || expected == '^' && target[position].isSeparator() -> { position++; rule++ }
                star >= 0 -> { rule = star + 1; position = ++retry }
                else -> return false
            }
        }
        while (glob.getOrNull(rule) == '*' || glob.getOrNull(rule) == '^') rule++
        return rule == glob.length
    }

    private fun Char.isSeparator() = this !in 'a'..'z' && this !in 'A'..'Z' && this !in '0'..'9' && this !in "_.%-"

    companion object {
        const val INDEX_LENGTH = 5
    }
}
