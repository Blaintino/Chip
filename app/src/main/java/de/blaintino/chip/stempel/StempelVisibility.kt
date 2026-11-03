package de.blaintino.chip.stempel

import android.content.Context
import de.blaintino.chip.data.CalendarInfo
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.EventInstance
import de.blaintino.chip.data.Formats
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wann der Stempel-Knopf im Widget erscheint (Wunsch des Nutzers, 2026-11-03: nicht dauerhaft). Sichtbar, wenn
 * - die Stempelkarte nicht neutral ist (sie hat dich an der FH erkannt und erinnert, oder du bist eingeloggt),
 * - heute Termine in den Arbeitskalendern liegen und es zwischen 1 h vor dem ersten und 1 h nach dem letzten ist.
 * Arbeitskalender = Name enthält „Stundenplan“ oder „Arbeit“. Rein (bis auf [today]), Tests `StempelVisibilityTest`.
 */
object StempelVisibility {
    const val MARGIN_MS = 60 * 60_000L
    private val NAME_KEYS = listOf("stundenplan", "arbeit")

    data class Window(val start: Long, val end: Long)

    fun isWorkCalendar(name: String): Boolean = NAME_KEYS.any { name.contains(it, ignoreCase = true) }

    fun workCalendars(calendars: List<CalendarInfo>): List<CalendarInfo> = calendars.filter { isWorkCalendar(it.name) }

    /** Zeitraum heute: 1 h vor dem ersten bis 1 h nach dem letzten Termin (ohne ganztägige); null = keine. */
    fun window(events: List<EventInstance>, workCalendarIds: Set<Long>, dayStart: Long, dayEnd: Long): Window? {
        val today = events.filter { !it.allDay && it.calendarId in workCalendarIds && it.begin < dayEnd && it.end > dayStart }
        if (today.isEmpty()) return null
        return Window(today.minOf { it.begin } - MARGIN_MS, today.maxOf { it.end } + MARGIN_MS)
    }

    fun visible(status: StempelStatus, window: Window?, now: Long): Boolean =
        status.state != "NEUTRAL" ||
            (window != null && now >= window.start && now < window.end)

    /** Nächster Zeitpunkt, an dem sich die Sichtbarkeit durch das Fenster ändert (zum Neuzeichnen). */
    fun nextChange(window: Window?, now: Long): Long? =
        window?.let { listOf(it.start, it.end).filter { t -> t > now }.minOrNull() }

    /** Fenster für heute aus dem Kalender (alle Kalender, auch in Chip ausgeblendete). */
    fun today(context: Context, repo: CalendarRepository = CalendarRepository(context)): Window? {
        val ids = workCalendars(repo.calendars()).map { it.id }.toSet()
        if (ids.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return window(repo.instances(day, day), ids, start, end)
    }

    /** Für die Einstellungen: welche Kalender zählen und welches Fenster heute gilt. */
    fun describe(context: Context): String = runCatching {
        val repo = CalendarRepository(context)
        val names = workCalendars(repo.calendars()).map { it.name }
        if (names.isEmpty()) return@runCatching "Arbeitskalender: keiner (Name mit „Stundenplan“ oder „Arbeit“)"
        val w = today(context, repo)
        "Arbeitskalender: ${names.joinToString()}; heute " +
            (w?.let { "sichtbar ${Formats.time(it.start)}–${Formats.time(it.end)}" } ?: "keine Termine")
    }.getOrElse { "Arbeitskalender: nicht lesbar" }
}
