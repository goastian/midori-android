package org.midorinext.android.sync

internal fun uncoveredHistoryWindows(day: Long, covered: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
    val result = mutableListOf<Pair<Long, Long>>()
    fun visit(start: Long, length: Long) {
        val end = start + length
        if (covered.any { (left, right) -> left <= start && right >= end }) return
        if (covered.none { (left, right) -> left < end && right > start }) {
            result.add(start to length)
        } else {
            requireSync(length > 10_546_875L, "invalid_history_window")
            visit(start, length / 2)
            visit(start + length / 2, length / 2)
        }
    }
    visit(day, 86_400_000_000L)
    return result
}
