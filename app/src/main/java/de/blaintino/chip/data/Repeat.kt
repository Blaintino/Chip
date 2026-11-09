package de.blaintino.chip.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Wiederholungsarten des Editors (Wunsch des Nutzers). [CUSTOM] = Regel, die Chip nicht selbst
 * abbildet (z. B. aus Google angelegt); sie bleibt beim Speichern unverändert.
 */
enum class RepeatKind { NONE, DAILY, WEEKLY, BIWEEKLY, MONTHLY_DATE, MONTHLY_NTH, MONTHLY_LAST, CUSTOM }

/** Wiederholung mit optionalem letzten Tag ([until] einschließlich). */
data class Repeat(val kind: RepeatKind, val until: LocalDate? = null) {
    companion object {
        val NONE = Repeat(RepeatKind.NONE)
    }
}

/** Umsetzung zwischen [Repeat] und RRULE (RFC 5545), reines Kotlin. */
object RepeatRules {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val CODES = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )

    /** Der wievielte Wochentag seiner Art im Monat (1–5). */
    fun nth(date: LocalDate): Int = (date.dayOfMonth - 1) / 7 + 1

    /** Liegt [date] in der letzten Woche des Monats (es gibt keinen weiteren gleichen Wochentag)? */
    fun isLast(date: LocalDate): Boolean = date.dayOfMonth + 7 > date.lengthOfMonth()

    /** Arten, die für einen Starttag sinnvoll sind (Reihenfolge wie im Editor). */
    fun choices(start: LocalDate): List<RepeatKind> = buildList {
        add(RepeatKind.NONE)
        add(RepeatKind.DAILY)
        add(RepeatKind.WEEKLY)
        add(RepeatKind.BIWEEKLY)
        add(RepeatKind.MONTHLY_DATE)
        if (nth(start) <= 4) add(RepeatKind.MONTHLY_NTH)
        if (isLast(start)) add(RepeatKind.MONTHLY_LAST)
    }

    fun label(kind: RepeatKind, start: LocalDate): String {
        val weekday = start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
        return when (kind) {
            RepeatKind.NONE -> "Nie"
            RepeatKind.DAILY -> "Täglich"
            RepeatKind.WEEKLY -> "Wöchentlich am $weekday"
            RepeatKind.BIWEEKLY -> "Alle 2 Wochen am $weekday"
            RepeatKind.MONTHLY_DATE -> "Monatlich am ${start.dayOfMonth}."
            RepeatKind.MONTHLY_NTH -> "Monatlich am ${nth(start)}. $weekday"
            RepeatKind.MONTHLY_LAST -> "Monatlich am letzten $weekday"
            RepeatKind.CUSTOM -> "Eigene Regel (bleibt unverändert)"
        }
    }

    /**
     * RRULE für [repeat] ab [start]. null bei [RepeatKind.NONE] und [RepeatKind.CUSTOM] (dann behält der
     * Aufrufer die bisherige Regel). [until] wird bei ganztägigen Terminen als Datum geschrieben, sonst als
     * letzter Moment des Tages in UTC.
     */
    fun build(repeat: Repeat, start: LocalDate, allDay: Boolean, zone: ZoneId): String? {
        val day = CODES.getValue(start.dayOfWeek)
        val base = when (repeat.kind) {
            RepeatKind.NONE, RepeatKind.CUSTOM -> return null
            RepeatKind.DAILY -> "FREQ=DAILY"
            RepeatKind.WEEKLY -> "FREQ=WEEKLY;WKST=MO;BYDAY=$day"
            RepeatKind.BIWEEKLY -> "FREQ=WEEKLY;INTERVAL=2;WKST=MO;BYDAY=$day"
            RepeatKind.MONTHLY_DATE -> "FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}"
            RepeatKind.MONTHLY_NTH -> "FREQ=MONTHLY;BYDAY=${nth(start)}$day"
            RepeatKind.MONTHLY_LAST -> "FREQ=MONTHLY;BYDAY=-1$day"
        }
        val until = repeat.until?.let { maxOf(it, start) } ?: return base
        val value = if (allDay) {
            DATE.format(until)
        } else {
            val last = until.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)
            DATE_TIME_UTC.format(last.atZone(ZoneOffset.UTC))
        }
        return "$base;UNTIL=$value"
    }

    /** Liest [rrule] (Serie mit erstem Termin am [start]); alles, was nicht genau passt, ist [RepeatKind.CUSTOM]. */
    fun parse(rrule: String?, start: LocalDate, zone: ZoneId): Repeat {
        if (rrule.isNullOrBlank()) return Repeat.NONE
        val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate {
            val i = it.indexOf('=')
            if (i < 0) it.uppercase() to "" else it.substring(0, i).uppercase() to it.substring(i + 1).uppercase()
        }
        val custom = Repeat(RepeatKind.CUSTOM)
        val allowed = setOf("FREQ", "INTERVAL", "WKST", "BYDAY", "BYMONTHDAY", "UNTIL")
        if (parts.keys.any { it !in allowed }) return custom
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: if (parts["INTERVAL"] == null) 1 else return custom
        val byDay = parts["BYDAY"]
        val byMonthDay = parts["BYMONTHDAY"]
        val day = CODES.getValue(start.dayOfWeek)
        val kind = when (parts["FREQ"]) {
            "DAILY" -> if (interval == 1 && byDay == null && byMonthDay == null) RepeatKind.DAILY else null
            "WEEKLY" -> when {
                byMonthDay != null || (byDay != null && byDay != day) -> null
                interval == 1 -> RepeatKind.WEEKLY
                interval == 2 -> RepeatKind.BIWEEKLY
                else -> null
            }
            "MONTHLY" -> when {
                interval != 1 -> null
                byDay == null && (byMonthDay == null || byMonthDay == start.dayOfMonth.toString()) -> RepeatKind.MONTHLY_DATE
                byMonthDay != null -> null
                byDay == "${nth(start)}$day" && nth(start) <= 4 -> RepeatKind.MONTHLY_NTH
                byDay == "-1$day" && isLast(start) -> RepeatKind.MONTHLY_LAST
                else -> null
            }
            else -> null
        } ?: return custom
        val until = parts["UNTIL"]?.let { parseUntil(it, zone) ?: return custom }
        return Repeat(kind, until)
    }

    /** Ersetzt ein bisheriges Ende (UNTIL/COUNT) so, dass die Serie vor [cutoff] (Instances.BEGIN) endet. */
    fun endBefore(rrule: String, cutoff: Long, allDay: Boolean): String {
        val kept = rrule.removePrefix("RRULE:").split(';')
            .filter { it.isNotBlank() && !it.uppercase().startsWith("UNTIL=") && !it.uppercase().startsWith("COUNT=") }
        val until = if (allDay) {
            DATE.format(Instant.ofEpochMilli(cutoff).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1))
        } else {
            DATE_TIME_UTC.format(Instant.ofEpochMilli(cutoff).minusSeconds(1).atZone(ZoneOffset.UTC))
        }
        return (kept + "UNTIL=$until").joinToString(";")
    }

    private fun parseUntil(value: String, zone: ZoneId): LocalDate? = runCatching {
        when {
            value.length == 8 -> LocalDate.parse(value, DATE)
            value.endsWith("Z") -> java.time.LocalDateTime.parse(value, DATE_TIME_UTC.withZone(ZoneOffset.UTC))
                .atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate()
            else -> java.time.LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toLocalDate()
        }
    }.getOrNull()
}
