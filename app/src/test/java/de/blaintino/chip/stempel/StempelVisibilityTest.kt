package de.blaintino.chip.stempel

import de.blaintino.chip.data.EventInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StempelVisibilityTest {
    private val h = 3_600_000L
    private val dayStart = 0L
    private val dayEnd = 24 * h
    private val day = LocalDate.of(2026, 11, 3)
    private val work = setOf(1L)

    private fun ev(cal: Long, from: Long, to: Long, allDay: Boolean = false) =
        EventInstance(1, cal, "T", "", from, to, allDay, 0, false, day, day)

    private val neutral = StempelStatus("NEUTRAL", null, 0, 0)

    @Test
    fun fensterEineStundeVorUndNach() {
        val w = StempelVisibility.window(listOf(ev(1, 10 * h, 12 * h), ev(1, 14 * h, 16 * h)), work, dayStart, dayEnd)
        assertEquals(StempelVisibility.Window(9 * h, 17 * h), w)
    }

    @Test
    fun andereKalenderUndGanztaegigeZaehlenNicht() {
        val events = listOf(ev(2, 10 * h, 12 * h), ev(1, dayStart, dayEnd, allDay = true))
        assertNull(StempelVisibility.window(events, work, dayStart, dayEnd))
    }

    @Test
    fun termineAndererTageZaehlenNicht() =
        assertNull(StempelVisibility.window(listOf(ev(1, dayEnd + 2 * h, dayEnd + 3 * h)), work, dayStart, dayEnd))

    @Test
    fun neutralNurImFenster() {
        val w = StempelVisibility.Window(9 * h, 17 * h)
        assertFalse(StempelVisibility.visible(neutral, w, 8 * h))
        assertTrue(StempelVisibility.visible(neutral, w, 9 * h))
        assertFalse(StempelVisibility.visible(neutral, w, 17 * h))
        assertFalse(StempelVisibility.visible(neutral, null, 12 * h))
    }

    @Test
    fun erkanntOderEingeloggtImmer() {
        assertTrue(StempelVisibility.visible(StempelStatus("NICHT_EINGELOGGT", null, 0, 0), null, 20 * h))
        assertTrue(StempelVisibility.visible(StempelStatus("EINGELOGGT", null, 0, 1), null, 20 * h))
    }

    /** Wunsch des Nutzers: nach dem letzten Termin + 1 h weg, auch wenn heute gearbeitet wurde. */
    @Test
    fun nachArbeitHeuteNichtMehrSichtbar() =
        assertFalse(StempelVisibility.visible(StempelStatus("NEUTRAL", null, 2 * h, 0), null, 20 * h))

    @Test
    fun naechsterWechsel() {
        val w = StempelVisibility.Window(9 * h, 17 * h)
        assertEquals(9 * h, StempelVisibility.nextChange(w, 8 * h))
        assertEquals(17 * h, StempelVisibility.nextChange(w, 10 * h))
        assertNull(StempelVisibility.nextChange(w, 18 * h))
        assertNull(StempelVisibility.nextChange(null, 8 * h))
    }

    @Test
    fun arbeitskalenderNachName() {
        assertTrue(StempelVisibility.isWorkCalendar("Stundenplan WS 26/27"))
        assertTrue(StempelVisibility.isWorkCalendar("Arbeit"))
        assertFalse(StempelVisibility.isWorkCalendar("Langners"))
    }
}
