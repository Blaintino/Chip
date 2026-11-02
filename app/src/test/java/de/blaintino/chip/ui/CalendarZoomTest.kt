package de.blaintino.chip.ui

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
