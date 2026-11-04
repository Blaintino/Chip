package de.blaintino.chip.stempel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private val min = 60_000L
    private fun running(grossH: Long, grossMin: Long = 0, breaks: Long = 0): Pair<StempelStatus, Long> {
        // Seit 0 eingeloggt (since = 1 ms, damit „bekannt“), jetzt = Brutto.
        val now = 1 + grossH * hour + grossMin * min
        return StempelStatus("EINGELOGGT", null, 0, 1, breaks) to now
    }

    @Test
    fun ohnePauseStehtDerTimerAbSechsStunden() {
        val (s, now) = running(6, 10)
        assertEquals(6 * hour, s.workedMs(now))
        assertTrue(s.paused(now))
        assertEquals(now + 20 * min, s.nextChange(now))
    }

    @Test
    fun nachDerPauseLaeuftErWeiter() {
        val (s, now) = running(7)
        assertEquals(6 * hour + 30 * min, s.workedMs(now))
        assertFalse(s.paused(now))
        // Nächster Halt: netto 9 h = brutto 9:30.
        assertEquals(now + 2 * hour + 30 * min, s.nextChange(now))
    }

    @Test
    fun abNeunStundenNochmalFuenfzehn() {
        val (s, now) = running(9, 40)
        assertEquals(9 * hour, s.workedMs(now))
        assertTrue(s.paused(now))
        val (s2, now2) = running(10)
        assertEquals(9 * hour + 15 * min, s2.workedMs(now2))
        assertNull(s2.nextChange(now2))
    }

    @Test
    fun genommenePauseZaehlt() {
        val (s, now) = running(6, 10, breaks = 30 * min)
        assertEquals(6 * hour + 10 * min, s.workedMs(now))
        assertFalse(s.paused(now))
        val (s2, now2) = running(9, 10, breaks = 30 * min)
        assertTrue(s2.paused(now2))
        assertEquals(9 * hour, s2.workedMs(now2))
    }

    @Test
    fun tippenLoggtAusOderEin() {
        assertEquals(Stempelkarte.ACTION_LOGOUT, StempelStatus("EINGELOGGT", null, 0, 1).tapAction)
        assertEquals(Stempelkarte.ACTION_LOGIN, StempelStatus("NICHT_EINGELOGGT", null, 0, 0).tapAction)
        assertEquals(Stempelkarte.ACTION_LOGIN, StempelStatus("NEUTRAL", null, 0, 0).tapAction)
    }
}
