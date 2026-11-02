package de.blaintino.chip.ui

import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.widget.WidgetFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WidgetFilterTest {
    private val day = LocalDate.of(2026, 11, 2)

    private fun ev(id: Long, begin: Long, end: Long, allDay: Boolean = false) =
        EventInstance(id, 1, "T$id", "", begin, end, allDay, 0, false, day, day)

    @Test
    fun `vergangene Termine fallen weg, laufende und kommende bleiben`() {
        val list = listOf(
            ev(1, 100, 200), // vorbei
            ev(2, 250, 400), // läuft
            ev(3, 500, 600), // kommt
            ev(4, 0, 86_400_000, allDay = true),
            ev(5, 150, 150), // ohne Dauer, vorbei
            ev(6, 700, 700), // ohne Dauer, kommt
        )
        assertEquals(listOf(2L, 3L, 4L, 6L), WidgetFilter.upcoming(list, now = 300).map { it.eventId })
    }

    @Test
    fun `nächste Änderung ist das früheste Ende danach, ganztägige zählen nicht`() {
        val list = listOf(ev(2, 250, 400), ev(3, 500, 600), ev(4, 0, 86_400_000, allDay = true))
        assertEquals(400L, WidgetFilter.nextChange(list, now = 300))
        assertNull(WidgetFilter.nextChange(listOf(ev(4, 0, 86_400_000, allDay = true)), now = 300))
    }
}
