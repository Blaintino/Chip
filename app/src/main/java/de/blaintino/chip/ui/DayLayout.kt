package de.blaintino.chip.ui

/**
 * Anordnung sich überschneidender Termine eines Tages in Spalten (reines Kotlin, testbar).
 * Termine, die sich direkt oder über eine Kette überschneiden, bilden eine Gruppe; jede Gruppe
 * teilt die Tagesbreite in so viele Spalten, wie gleichzeitig nötig sind.
 */
data class DaySlot(val index: Int, val startMin: Int, val endMin: Int, val column: Int, val columns: Int)

object DayLayout {
    /** Mindesthöhe in Minuten, damit kurze Termine lesbar bleiben (zählt auch für Überschneidungen). */
    const val MIN_MINUTES = 20

    /** @param segments je Termin (Beginn, Ende) in Minuten ab Tagesbeginn, Index = Position in der Liste. */
    fun layout(segments: List<Pair<Int, Int>>): List<DaySlot> {
        val items = segments.mapIndexed { i, (s, e) -> Triple(i, s, maxOf(e, s + MIN_MINUTES)) }
            .sortedWith(compareBy({ it.second }, { -it.third }))
        val result = mutableListOf<DaySlot>()
        var group = mutableListOf<Pair<Triple<Int, Int, Int>, Int>>() // Termin, Spalte
        val columnEnds = mutableListOf<Int>()
        var groupEnd = -1

        fun flush() {
            val cols = columnEnds.size
            group.forEach { (t, c) -> result += DaySlot(t.first, t.second, t.third, c, cols) }
            group = mutableListOf()
            columnEnds.clear()
        }

        for (t in items) {
            if (t.second >= groupEnd && group.isNotEmpty()) flush()
            var col = columnEnds.indexOfFirst { it <= t.second }
            if (col < 0) {
                col = columnEnds.size
                columnEnds += t.third
            } else {
                columnEnds[col] = t.third
            }
            group += t to col
            groupEnd = if (group.size == 1) t.third else maxOf(groupEnd, t.third)
        }
        if (group.isNotEmpty()) flush()
        return result.sortedBy { it.index }
    }
}
