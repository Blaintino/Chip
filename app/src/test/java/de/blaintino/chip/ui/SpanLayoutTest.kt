package de.blaintino.chip.ui

import de.blaintino.chip.data.EventInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpanLayoutTest {
    @Test
    fun `nicht überlappende Balken teilen sich eine Reihe`() {
        assertEquals(listOf(0, 0), SpanLayout.lanes(listOf(0 to 1, 2 to 4)))
    }

    @Test
    fun `überlappende Balken bekommen eigene Reihen, freie Reihen werden wieder genutzt`() {
        // A Mo–Mi, B Di–Do, C Do–Fr: C passt neben A in Reihe 0.
        assertEquals(listOf(0, 1, 0), SpanLayout.lanes(listOf(0 to 2, 1 to 3, 3 to 4)))
    }

    @Test
    fun `bei gleichem Beginn kommt der längere nach oben, Reihenfolge der Eingabe bleibt`() {
        assertEquals(listOf(1, 0), SpanLayout.lanes(listOf(2 to 2, 2 to 5)))
    }

    private val d = LocalDate.of(2026, 11, 2)
    private fun ev(begin: Long, end: Long, allDay: Boolean, days: Long) =
        EventInstance(1, 1, "T", "", begin, end, allDay, 0, false, d, d.plusDays(days))

    @Test
    fun `mehrtägig heißt ganztägig über mehrere Tage oder mindestens 24 Stunden`() {
        assertTrue(ev(0, 3 * 86_400_000L, allDay = true, days = 2).isSpanning())
        assertFalse(ev(0, 86_400_000L, allDay = true, days = 0).isSpanning())
        assertTrue(ev(0, 30 * 3_600_000L, allDay = false, days = 1).isSpanning())
        // Nachttermin 22–6 Uhr: zwei Kalendertage, aber nur 8 Stunden.
        assertFalse(ev(0, 8 * 3_600_000L, allDay = false, days = 1).isSpanning())
    }
}
