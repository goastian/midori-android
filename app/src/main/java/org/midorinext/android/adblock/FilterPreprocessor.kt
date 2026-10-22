package org.midorinext.android.adblock

internal fun activeFilterLines(text: String): Sequence<String> = activeFilterLines(text.lineSequence())

internal fun activeFilterLines(lines: Sequence<String>): Sequence<String> = sequence {
    data class Branch(val parent: Boolean, val condition: Boolean)
    val branches = ArrayDeque<Branch>()
    var active = true
    for (raw in lines) {
        val line = raw.trim()
        when {
            line.startsWith("!#if ") -> {
                require(branches.size < 64 && line.length <= 1024) { "Invalid filter condition" }
                val condition = filterCondition(line.removePrefix("!#if "))
                branches.addLast(Branch(active, condition))
                active = active && condition
            }
            line == "!#else" -> branches.lastOrNull()?.let { active = it.parent && !it.condition }
            line == "!#endif" -> if (branches.isNotEmpty()) active = branches.removeLast().parent
            active -> yield(line)
        }
    }
}

private fun filterCondition(expression: String): Boolean {
    val enabled = setOf("env_firefox", "env_gecko", "env_mobile", "env_native_css_has", "ext_ublock", "cap_user_stylesheet")
    val tokens = Regex("!|&&|\\|\\||\\(|\\)|[a-zA-Z0-9_]+").findAll(expression).map { it.value }.toList()
    var index = 0
    lateinit var parseOr: () -> Boolean
    fun atom(): Boolean {
        val token = tokens.getOrNull(index++) ?: return false
        return when (token) {
            "!" -> !atom()
            "(" -> parseOr().also { if (tokens.getOrNull(index) == ")") index++ }
            else -> token in enabled
        }
    }
    fun and(): Boolean {
        var result = atom()
        while (tokens.getOrNull(index) == "&&") { index++; val next = atom(); result = result && next }
        return result
    }
    parseOr = {
        var result = and()
        while (tokens.getOrNull(index) == "||") { index++; val next = and(); result = result || next }
        result
    }
    val result = parseOr()
    return index == tokens.size && result
}
