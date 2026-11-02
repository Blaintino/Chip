package de.blaintino.chip.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarZoomTest {
    @Test
    fun `aufziehen wird feiner, zusammenschieben gröber, an den Enden bleibt es`() {
        assertEquals(CalendarZoom.MONTH, CalendarZoom.YEAR.zoomIn())
        assertEquals(CalendarZoom.WEEK, CalendarZoom.MONTH.zoomIn())
        assertEquals(CalendarZoom.DAY, CalendarZoom.WEEK.zoomIn())
        assertEquals(CalendarZoom.DAY, CalendarZoom.DAY.zoomIn())
        assertEquals(CalendarZoom.WEEK, CalendarZoom.DAY.zoomOut())
        assertEquals(CalendarZoom.YEAR, CalendarZoom.MONTH.zoomOut())
        assertEquals(CalendarZoom.YEAR, CalendarZoom.YEAR.zoomOut())
    }

    @Test
    fun `Wischen verschiebt um einen Zeitraum der jeweiligen Stufe`() {
        val d = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), CalendarZoom.DAY.shift(d, 1))
        assertEquals(LocalDate.of(2026, 1, 24), CalendarZoom.WEEK.shift(d, -1))
        assertEquals(LocalDate.of(2026, 2, 28), CalendarZoom.MONTH.shift(d, 1))
        assertEquals(LocalDate.of(2025, 1, 31), CalendarZoom.YEAR.shift(d, -1))
    }
}
