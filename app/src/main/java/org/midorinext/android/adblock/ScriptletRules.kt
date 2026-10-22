package org.midorinext.android.adblock

import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN
import java.util.Locale

private val scriptletDomain = Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")

internal data class ScriptletRule(
    val id: String?,
    val args: List<String>,
    val included: List<String>,
    val excluded: List<String>,
    val exception: Boolean,
    val ublock: Boolean,
) {
    fun toJson(tracker: Boolean): JSONObject = JSONObject()
        .put("id", id ?: JSONObject.NULL)
        .put("args", JSONArray(args))
        .put("included", JSONArray(included))
        .put("excluded", JSONArray(excluded))
        .put("exception", exception)
        .put("ublock", ublock)
        .put("tracker", tracker)

    /** Writes the same JSON as [toJson] without building the intermediate objects. */
    fun appendJson(out: StringBuilder, tracker: Boolean) {
        fun strings(values: List<String>) {
            out.append('[')
            values.forEachIndexed { index, value ->
                if (index > 0) out.append(',')
                out.append(JSONObject.quote(value))
            }
            out.append(']')
        }
        out.append("{\"id\":").append(if (id == null) "null" else JSONObject.quote(id)).append(",\"args\":")
        strings(args)
        out.append(",\"included\":")
        strings(included)
        out.append(",\"excluded\":")
        strings(excluded)
        out.append(",\"exception\":").append(exception).append(",\"ublock\":").append(ublock)
            .append(",\"tracker\":").append(tracker).append('}')
    }
}

internal fun parseScriptletRule(line: String, trusted: Boolean = false): ScriptletRule? {
    if (line.length > 32_768 || line.startsWith('!')) return null
    val ublock = line.contains("##+js(") || line.contains("#@#+js(")
    val exception = if (ublock) line.contains("#@#+js(") else line.contains("#@%#//scriptlet(")
    val marker = when {
        ublock && exception -> "#@#+js("
        ublock -> "##+js("
        exception -> "#@%#//scriptlet("
        else -> "#%#//scriptlet("
    }
    val position = line.indexOf(marker)
    if (position < 0 || !line.endsWith(')')) return null
    val args = parseScriptletArguments(line.substring(position + marker.length, line.length - 1), ublock) ?: return null
    val entry = args.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { ScriptletCatalog.resolve(it, ublock) }
    if (entry == null && (args.isNotEmpty() || !exception)) return null
    if (entry?.trusted == true && !trusted && !exception) return null
    val included = mutableListOf<String>()
    val excluded = mutableListOf<String>()
    val scope = line.substring(0, position)
    if (scope.isNotEmpty()) {
        for (part in splitScriptletScope(scope) ?: return null) {
            val negative = part.startsWith('~')
            val value = normalizeScriptletScope(part.removePrefix("~")) ?: return null
            (if (negative) excluded else included).add(value)
        }
    }
    if (ublock && !exception && included.isEmpty()) return null
    if (ublock && exception && scope.isNotEmpty() && included.isEmpty()) return null
    return ScriptletRule(entry?.id, args.drop(1), included,
        if (ublock && exception) emptyList() else excluded, exception, ublock)
}

internal fun parseScriptletArguments(text: String, ublock: Boolean = false): List<String>? {
    if (text.isBlank()) return emptyList()
    val result = mutableListOf<String>()
    var index = 0
    while (index <= text.length) {
        while (index < text.length && text[index].isWhitespace()) index++
        val quote = text.getOrNull(index)?.takeIf { it == '\'' || it == '"' || it == '`' }
        if (quote != null) index++
        val value = StringBuilder()
        var closed = quote == null
        while (index < text.length) {
            val char = text[index++]
            if (char == '\\' && ublock) {
                val start = index - 1
                while (text.getOrNull(index) == '\\') index++
                val count = index - start
                val separator = quote ?: ','
                val escaped = text.getOrNull(index) == separator && count % 2 == 1
                repeat(count - if (escaped) 1 else 0) { value.append('\\') }
                if (escaped) value.append(text[index++])
            } else if (char == '\\' && index < text.length) {
                val next = text[index]
                if (next == ',' || next == quote || next == '\\') {
                    value.append(next)
                    index++
                } else value.append(char)
            } else if (quote != null && char == quote) {
                closed = true
                break
            } else if (quote == null && char == ',') {
                index--
                break
            } else value.append(char)
        }
        if (!closed || value.length > 8192 || result.size >= 64) return null
        if (quote != null) while (index < text.length && text[index].isWhitespace()) index++
        if (index < text.length && text[index] != ',') return null
        result += if (quote == null) value.toString().trim() else value.toString()
        if (index == text.length) break
        index++
    }
    return result
}

private fun splitScriptletScope(scope: String): List<String>? {
    val values = mutableListOf<String>()
    var start = 0
    var regex = false
    var escaped = false
    scope.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            char == '/' -> regex = !regex
            char == ',' && !regex -> { values += scope.substring(start, index); start = index + 1 }
        }
    }
    if (regex || escaped) return null
    values += scope.substring(start)
    return values
}

private fun normalizeScriptletScope(value: String): String? {
    if (value == "*") return value
    if (value.startsWith('/') && value.endsWith('/') && value.length in 3..512 &&
        value.none { it == '\n' || it == '\r' }) return value
    val ancestor = value.endsWith(">>")
    val host = value.removeSuffix(">>")
    val entity = host.endsWith(".*")
    val rawDomain = host.removeSuffix(".*")
    val domain = (if (rawDomain.all { it.code < 128 }) rawDomain else runCatching { IDN.toASCII(rawDomain) }.getOrNull())
        ?.lowercase(Locale.ROOT) ?: return null
    if (domain.isEmpty() || domain.length > 253 || domain.contains("..") ||
        !scriptletDomain.matches(domain)) return null
    return domain + (if (entity) ".*" else "") + (if (ancestor) ">>" else "")
}
