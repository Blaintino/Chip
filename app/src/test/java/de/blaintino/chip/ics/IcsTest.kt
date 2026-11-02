package de.blaintino.chip.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class IcsTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun cal(vararg events: String, header: String = "") =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n$header" + events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    private fun map(ics: String) = IcsMapper.map(IcsParser.parse(ics), berlin) {
        if (it == "W. Europe Standard Time") "Europe/Berlin" else null
    }

    private fun millis(ldt: String, zone: ZoneId) = LocalDateTime.parse(ldt).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `liest Kalendername und faltet Zeilen`() {
        val ics = cal(
            "UID:1\r\nSUMMARY:Sehr langer\r\n  Titel\r\nDTSTART:20261105T100000Z\r\nDTEND:20261105T110000Z",
            header = "X-WR-CALNAME:Müllabfuhr\r\n",
        )
        val parsed = IcsParser.parse(ics)
        assertEquals("Müllabfuhr", parsed.name)
        assertEquals("Sehr langer Titel", parsed.events.single().text("SUMMARY"))
    }

    @Test
    fun `Text wird entschlüsselt`() {
        assertEquals("a, b; c\nd", IcsParser.unescapeText("""a\, b\; c\nd"""))
    }

    @Test
    fun `Doppelpunkt in Parameter mit Anführungszeichen`() {
        val p = IcsParser.parseLine("""ATTENDEE;CN="Max: Muster":mailto:max@example.org""")!!
        assertEquals("Max: Muster", p.params["CN"])
        assertEquals("mailto:max@example.org", p.value)
    }

    @Test
    fun `Zeit mit TZID, UTC und ohne Zone`() {
        val rows = map(
            cal(
                "UID:a\r\nSUMMARY:A\r\nDTSTART;TZID=Europe/Berlin:20261105T100000\r\nDTEND;TZID=Europe/Berlin:20261105T113000",
                "UID:b\r\nSUMMARY:B\r\nDTSTART:20261105T100000Z\r\nDTEND:20261105T110000Z",
                "UID:c\r\nSUMMARY:C\r\nDTSTART:20261105T100000\r\nDURATION:PT45M",
            ),
        ).associateBy { it.uid }
        assertEquals(millis("2026-11-05T10:00", berlin), rows["a"]!!.dtStart)
        assertEquals(millis("2026-11-05T11:30", berlin), rows["a"]!!.dtEnd)
        assertEquals("Europe/Berlin", rows["a"]!!.timeZone)
        assertEquals(millis("2026-11-05T10:00", ZoneOffset.UTC), rows["b"]!!.dtStart)
        assertEquals("UTC", rows["b"]!!.timeZone)
        assertEquals(millis("2026-11-05T10:45", berlin), rows["c"]!!.dtEnd)
    }

    @Test
    fun `Windows- und Mozilla-Zonennamen`() {
        val rows = map(
            cal(
                "UID:w\r\nDTSTART;TZID=W. Europe Standard Time:20260701T090000",
                "UID:m\r\nDTSTART;TZID=/mozilla.org/20050126_1/America/New_York:20260701T090000",
            ),
        ).associateBy { it.uid }
        assertEquals("Europe/Berlin", rows["w"]!!.timeZone)
        assertEquals("America/New_York", rows["m"]!!.timeZone)
        assertEquals("(ohne Titel)", rows["w"]!!.title)
    }

    @Test
    fun `ganztägig in UTC, Ende mindestens ein Tag`() {
        val row = map(cal("UID:g\r\nSUMMARY:Feiertag\r\nDTSTART;VALUE=DATE:20261225")).single()
        assertTrue(row.allDay)
        assertEquals("UTC", row.timeZone)
        assertEquals(millis("2026-12-25T00:00", ZoneOffset.UTC), row.dtStart)
        assertEquals(millis("2026-12-26T00:00", ZoneOffset.UTC), row.dtEnd)
    }

    @Test
    fun `Serie bekommt Dauer statt Ende und EXDATE in UTC`() {
        val row = map(
            cal(
                "UID:s\r\nSUMMARY:Training\r\nDTSTART;TZID=Europe/Berlin:20261103T180000\r\nDTEND;TZID=Europe/Berlin:20261103T193000\r\n" +
                    "RRULE:FREQ=WEEKLY;BYDAY=TU\r\nEXDATE;TZID=Europe/Berlin:20261110T180000,20261117T180000",
            ),
        ).single()
        assertNull(row.dtEnd)
        assertEquals("P5400S", row.duration)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", row.rrule)
        assertEquals("20261110T170000Z,20261117T170000Z", row.exdate)
    }

    @Test
    fun `ganztägige Serie hat Dauer in Tagen`() {
        val row = map(cal("UID:m\r\nDTSTART;VALUE=DATE:20261102\r\nDTEND;VALUE=DATE:20261104\r\nRRULE:FREQ=MONTHLY")).single()
        assertEquals("P2D", row.duration)
        assertNull(row.dtEnd)
    }

    @Test
    fun `geänderter Einzeltermin wird Ausnahme plus eigener Termin, abgesagter nur Ausnahme`() {
        val rows = map(
            cal(
                "UID:s\r\nSUMMARY:Serie\r\nDTSTART:20261103T170000Z\r\nDURATION:PT1H\r\nRRULE:FREQ=DAILY;COUNT=5",
                "UID:s\r\nRECURRENCE-ID:20261104T170000Z\r\nSUMMARY:Verschoben\r\nDTSTART:20261104T190000Z\r\nDTEND:20261104T200000Z",
                "UID:s\r\nRECURRENCE-ID:20261105T170000Z\r\nSTATUS:CANCELLED\r\nDTSTART:20261105T170000Z",
            ),
        )
        assertEquals(2, rows.size)
        val master = rows.single { it.uid == "s" }
        assertEquals("20261104T170000Z,20261105T170000Z", master.exdate)
        val moved = rows.single { it.uid != "s" }
        assertEquals("Verschoben", moved.title)
        assertNull(moved.rrule)
        assertEquals(millis("2026-11-04T20:00", ZoneOffset.UTC), moved.dtEnd)
    }

    @Test
    fun `abgesagte Termine und Termine ohne Beginn entfallen, VALARM stört nicht`() {
        val rows = map(
            cal(
                "UID:x\r\nSTATUS:CANCELLED\r\nDTSTART:20261103T170000Z",
                "UID:y\r\nSUMMARY:ohne Beginn",
                "UID:z\r\nSUMMARY:mit Alarm\r\nDTSTART:20261103T170000Z\r\nBEGIN:VALARM\r\nTRIGGER:-PT15M\r\nDESCRIPTION:Alarm\r\nEND:VALARM",
            ),
        )
        assertEquals(listOf("z"), rows.map { it.uid })
        assertNull(rows.single().description)
    }

    @Test
    fun `Dauer lesen`() {
        assertEquals(5400L, IcsMapper.parseDurationSeconds("PT1H30M"))
        assertEquals(2 * 86400L, IcsMapper.parseDurationSeconds("P2D"))
        assertEquals(14 * 86400L, IcsMapper.parseDurationSeconds("P2W"))
        assertEquals(-900L, IcsMapper.parseDurationSeconds("-PT15M"))
        assertNull(IcsMapper.parseDurationSeconds("quatsch"))
    }
}
