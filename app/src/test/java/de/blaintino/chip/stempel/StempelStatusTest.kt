package de.blaintino.chip.stempel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StempelStatusTest {
    private val hour = 3_600_000L

    @Test
    fun laufendeZeitPlusAbgeschlossene() {
        val s = StempelStatus("EINGELOGGT", null, workClosedMs = 2 * hour, workRunningSince = 10 * hour)
        assertEquals(3 * hour, s.workedMs(11 * hour))
        assertTrue(s.timeKnown)
    }

    @Test
    fun ausgeloggtZaehltNichtWeiter() {
        val s = StempelStatus("NEUTRAL", null, workClosedMs = 2 * hour, workRunningSince = 0)
        assertEquals(2 * hour, s.workedMs(20 * hour))
        assertFalse(s.timeKnown)
    }

    @Test
    fun eingeloggtOhneBekanntenBeginn() =
        assertFalse(StempelStatus("EINGELOGGT", null, 0, 0).timeKnown)

    @Test
    fun tippenLoggtAusOderEin() {
        assertEquals(Stempelkarte.ACTION_LOGOUT, StempelStatus("EINGELOGGT", null, 0, 1).tapAction)
        assertEquals(Stempelkarte.ACTION_LOGIN, StempelStatus("NICHT_EINGELOGGT", null, 0, 0).tapAction)
        assertEquals(Stempelkarte.ACTION_LOGIN, StempelStatus("NEUTRAL", null, 0, 0).tapAction)
    }
}
