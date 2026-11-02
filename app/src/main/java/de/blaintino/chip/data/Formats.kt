package de.blaintino.chip.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private val DAY = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
    private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
    private val SHORT = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
    private val DATE_TIME = DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)

    /** Uhrzeit eines Termins aus Sicht von [day] (mehrtägige Termine: „ab …“/„bis …“). */
    fun timeRange(ev: EventInstance, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        if (ev.allDay) {
            return if (ev.startDay != ev.endDay) "ganztägig (${short(ev.startDay)} – ${short(ev.endDay)})" else "ganztägig"
        }
        val s = Instant.ofEpochMilli(ev.begin).atZone(zone)
        val e = Instant.ofEpochMilli(ev.end).atZone(zone)
        val startsToday = s.toLocalDate() == day
        val endsToday = e.toLocalDate() == day
        return when {
            startsToday && endsToday -> if (ev.begin == ev.end) TIME.format(s) else "${TIME.format(s)} – ${TIME.format(e)}"
            startsToday -> "ab ${TIME.format(s)}"
            endsToday -> "bis ${TIME.format(e)}"
            else -> "ganztägig"
        }
    }

    fun dayHeader(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Heute · ${DAY.format(day)}"
        today.plusDays(1) -> "Morgen · ${DAY.format(day)}"
        else -> DAY.format(day)
    }

    fun dayLong(day: LocalDate): String = DAY_LONG.format(day)
    fun short(day: LocalDate): String = SHORT.format(day)
    fun month(month: YearMonth): String = MONTH.format(month).replaceFirstChar { it.uppercase() }
    fun time(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    fun dateTime(millis: Long): String = DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
