package de.blaintino.chip.ui

import de.blaintino.chip.data.EventInstance

private const val DAY_MS = 86_400_000L

/**
 * Mehrtägiger Termin (Wunsch des Nutzers: in allen Ansichten verbunden darstellen): ganztägig über mehrere
 * Tage oder mindestens 24 Stunden lang. Kürzere Nachttermine (22–6 Uhr) bleiben im Stundenraster.
 */
fun EventInstance.isSpanning(): Boolean = startDay != endDay && (allDay || end - begin >= DAY_MS)

/** Gehört in die Leiste über dem Stundenraster: ganztägig oder mehrtägig. */
fun EventInstance.inAllDayStrip(): Boolean = allDay || isSpanning()

/**
 * Reihen (Lanes) für Balken über mehrere Spalten, reines Kotlin (Tests `SpanLayoutTest`).
 * Eingabe je Balken (erste Spalte, letzte Spalte), jeweils einschließlich; Ausgabe die Reihe je Balken in
 * derselben Reihenfolge. Frühere Balken zuerst, bei gleichem Beginn der längere; jede Reihe so weit oben wie frei.
 */
object SpanLayout {
    fun lanes(spans: List<Pair<Int, Int>>): List<Int> {
        val order = spans.indices.sortedWith(compareBy({ spans[it].first }, { -(spans[it].second - spans[it].first) }))
        val laneEnds = mutableListOf<Int>() // letzte belegte Spalte je Reihe
        val result = IntArray(spans.size)
        for (i in order) {
            val (start, end) = spans[i]
            var lane = laneEnds.indexOfFirst { it < start }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds += end
            } else {
                laneEnds[lane] = end
            }
            result[i] = lane
        }
        return result.toList()
    }
}
