package de.blaintino.chip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RepeatTest {
    private val zone = ZoneId.of("Europe/Berlin")
    // Sonntag, 8. November 2026 = 2. Sonntag im Monat.
    private val sun = LocalDate.of(2026, 11, 8)

    @Test
    fun `Regeln werden passend zum Starttag gebaut`() {
        assertEquals("FREQ=DAILY", RepeatRules.build(Repeat(RepeatKind.DAILY), sun, false, zone))
        assertEquals("FREQ=WEEKLY;WKST=MO;BYDAY=SU", RepeatRules.build(Repeat(RepeatKind.WEEKLY), sun, false, zone))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;WKST=MO;BYDAY=SU", RepeatRules.build(Repeat(RepeatKind.BIWEEKLY), sun, false, zone))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=8", RepeatRules.build(Repeat(RepeatKind.MONTHLY_DATE), sun, false, zone))
        assertEquals("FREQ=MONTHLY;BYDAY=2SU", RepeatRules.build(Repeat(RepeatKind.MONTHLY_NTH), sun, false, zone))
        assertNull(RepeatRules.build(Repeat.NONE, sun, false, zone))
        assertNull(RepeatRules.build(Repeat(RepeatKind.CUSTOM), sun, false, zone))
    }

    @Test
    fun `letzter Wochentag des Monats`() {
        val lastSun = LocalDate.of(2026, 11, 29)
        assertTrue(RepeatRules.isLast(lastSun))
        assertEquals(5, RepeatRules.nth(lastSun))
        assertFalse(RepeatKind.MONTHLY_NTH in RepeatRules.choices(lastSun))
        assertTrue(RepeatKind.MONTHLY_LAST in RepeatRules.choices(lastSun))
        assertEquals("FREQ=MONTHLY;BYDAY=-1SU", RepeatRules.build(Repeat(RepeatKind.MONTHLY_LAST), lastSun, false, zone))
        // 4. Sonntag, der zugleich der letzte ist: beide Möglichkeiten.
        val fourth = LocalDate.of(2026, 2, 22)
        assertTrue(RepeatKind.MONTHLY_NTH in RepeatRules.choices(fourth) && RepeatKind.MONTHLY_LAST in RepeatRules.choices(fourth))
        assertEquals("Monatlich am 2. Sonntag", RepeatRules.label(RepeatKind.MONTHLY_NTH, sun))
    }

    @Test
    fun `Ende als letzter Moment des Tages in UTC bzw als Datum`() {
        val until = LocalDate.of(2026, 12, 31)
        // 31.12. 23:59:59 in Berlin (Winterzeit, UTC+1) = 22:59:59Z.
        assertEquals("FREQ=DAILY;UNTIL=20261231T225959Z", RepeatRules.build(Repeat(RepeatKind.DAILY, until), sun, false, zone))
        assertEquals("FREQ=DAILY;UNTIL=20261231", RepeatRules.build(Repeat(RepeatKind.DAILY, until), sun, true, zone))
    }

    @Test
    fun `eigene Regeln werden gelesen, fremde bleiben CUSTOM`() {
        for (kind in listOf(RepeatKind.DAILY, RepeatKind.WEEKLY, RepeatKind.BIWEEKLY, RepeatKind.MONTHLY_DATE, RepeatKind.MONTHLY_NTH)) {
            val rule = RepeatRules.build(Repeat(kind, LocalDate.of(2027, 3, 1)), sun, false, zone)
            assertEquals(Repeat(kind, LocalDate.of(2027, 3, 1)), RepeatRules.parse(rule, sun, zone))
        }
        assertEquals(Repeat(RepeatKind.WEEKLY), RepeatRules.parse("FREQ=WEEKLY", sun, zone))
        assertEquals(Repeat(RepeatKind.MONTHLY_DATE), RepeatRules.parse("FREQ=MONTHLY", sun, zone))
        assertEquals(Repeat(RepeatKind.DAILY, LocalDate.of(2026, 12, 1)), RepeatRules.parse("FREQ=DAILY;UNTIL=20261201", sun, zone))
        assertEquals(RepeatKind.CUSTOM, RepeatRules.parse("FREQ=WEEKLY;BYDAY=MO,WE", sun, zone).kind)
        assertEquals(RepeatKind.CUSTOM, RepeatRules.parse("FREQ=DAILY;COUNT=5", sun, zone).kind)
        assertEquals(RepeatKind.CUSTOM, RepeatRules.parse("FREQ=YEARLY", sun, zone).kind)
        assertEquals(RepeatKind.CUSTOM, RepeatRules.parse("FREQ=WEEKLY;INTERVAL=3", sun, zone).kind)
        assertEquals(RepeatKind.NONE, RepeatRules.parse(null, sun, zone).kind)
    }

    @Test
    fun `Serie endet vor einem Vorkommen, bisheriges Ende fällt weg`() {
        val begin = ZonedDateTime.of(2026, 11, 22, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(
            "FREQ=WEEKLY;WKST=MO;BYDAY=SU;UNTIL=20261122T085959Z",
            RepeatRules.endBefore("FREQ=WEEKLY;WKST=MO;BYDAY=SU;COUNT=10", begin, false),
        )
        val allDayBegin = LocalDate.of(2026, 11, 22).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("FREQ=DAILY;UNTIL=20261121", RepeatRules.endBefore("FREQ=DAILY;UNTIL=20270101", allDayBegin, true))
    }
}
