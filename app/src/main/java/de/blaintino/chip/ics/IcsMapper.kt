package de.blaintino.chip.ics

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Ein Termin so, wie er in CalendarContract.Events geschrieben wird (reine Daten, ohne Android).
 *
 * Regeln von CalendarContract, die hier schon eingehalten werden:
 * - Ganztägig: Beginn/Ende auf Mitternacht UTC, Zeitzone "UTC".
 * - Wiederkehrend (RRULE/RDATE): kein [dtEnd], stattdessen [duration] (RFC-5545-Dauer).
 * - Ausnahmen (EXDATE) als kommagetrennte UTC-Zeiten `yyyyMMdd'T'HHmmss'Z'`.
 */
data class EventRow(
    val uid: String,
    val title: String,
    val location: String?,
    val description: String?,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Boolean,
    val timeZone: String,
    val rrule: String?,
    val exdate: String?,
    val rdate: String?,
)

object IcsMapper {

    private const val DAY_MS = 86_400_000L
    private val UTC_LIST_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_TIME_NO_SECONDS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
    private val DATE = DateTimeFormatter.BASIC_ISO_DATE

    /**
     * @param deviceZone Zeitzone für Zeiten ohne Zone („floating“), falls der Kalender keine
     *   X-WR-TIMEZONE angibt.
     * @param windowsZone Übersetzt Windows-Zonennamen („W. Europe Standard Time“) in IANA-IDs;
     *   auf Android über ICU, im Test ein Stub.
     */
    fun map(
        calendar: IcsCalendar,
        deviceZone: ZoneId,
        windowsZone: (String) -> String? = { null },
    ): List<EventRow> {
        val resolve: (String) -> ZoneId? = { resolveZone(it, windowsZone) }
        val defaultZone = calendar.timeZone?.let(resolve) ?: deviceZone

        val masters = calendar.events.filter { it.first("RECURRENCE-ID") == null }
        val overrides = calendar.events.filter { it.first("RECURRENCE-ID") != null }

        // Geänderte oder abgesagte Einzeltermine einer Serie: Am Serientermin als Ausnahme
        // austragen und (falls nicht abgesagt) als eigenständigen Termin anlegen. Einfacher und
        // robuster als CalendarContracts Ausnahme-Verknüpfung über ORIGINAL_SYNC_ID.
        val extraExdates = mutableMapOf<String, MutableList<Long>>()
        val rows = mutableListOf<EventRow>()
        for (ov in overrides) {
            val uid = ov.text("UID") ?: continue
            val recId = parseTime(ov.first("RECURRENCE-ID")!!, ov.first("RECURRENCE-ID")!!.value, defaultZone, resolve) ?: continue
            extraExdates.getOrPut(uid) { mutableListOf() } += recId.millis
            if (!isCancelled(ov)) {
                toRow(ov, "$uid#${recId.millis}", emptyList(), defaultZone, resolve, allowRecurrence = false)?.let { rows += it }
            }
        }
        for (ev in masters) {
            if (isCancelled(ev)) continue
            val uid = ev.text("UID") ?: "ohne-uid-${rows.size}"
            toRow(ev, uid, extraExdates[uid].orEmpty(), defaultZone, resolve, allowRecurrence = true)?.let { rows += it }
        }
        return rows
    }

    private fun isCancelled(ev: IcsEvent) = ev.first("STATUS")?.value?.trim().equals("CANCELLED", ignoreCase = true)

    private fun toRow(
        ev: IcsEvent,
        uid: String,
        extraExdates: List<Long>,
        defaultZone: ZoneId,
        resolve: (String) -> ZoneId?,
        allowRecurrence: Boolean,
    ): EventRow? {
        val startProp = ev.first("DTSTART") ?: return null
        val start = parseTime(startProp, startProp.value, defaultZone, resolve) ?: return null

        var end: Long = ev.first("DTEND")?.let { parseTime(it, it.value, defaultZone, resolve)?.millis }
            ?: ev.first("DURATION")?.let { d -> parseDurationSeconds(d.value)?.let { start.millis + it * 1000 } }
            ?: if (start.allDay) start.millis + DAY_MS else start.millis
        if (end < start.millis) end = start.millis
        if (start.allDay && end < start.millis + DAY_MS) end = start.millis + DAY_MS

        val rrule = if (allowRecurrence) ev.first("RRULE")?.value?.trim()?.takeIf { it.isNotEmpty() } else null
        val rdates = if (allowRecurrence) timeList(ev.all("RDATE"), defaultZone, resolve) else emptyList()
        val exdates = if (allowRecurrence) timeList(ev.all("EXDATE"), defaultZone, resolve) + extraExdates else emptyList()
        val recurring = rrule != null || rdates.isNotEmpty()

        val duration = if (recurring) {
            if (start.allDay) "P${maxOf(1, (end - start.millis + DAY_MS / 2) / DAY_MS)}D"
            else "P${(end - start.millis) / 1000}S"
        } else null

        return EventRow(
            uid = uid,
            title = ev.text("SUMMARY")?.takeIf { it.isNotEmpty() } ?: "(ohne Titel)",
            location = ev.text("LOCATION")?.takeIf { it.isNotEmpty() },
            description = ev.text("DESCRIPTION")?.takeIf { it.isNotEmpty() },
            dtStart = start.millis,
            dtEnd = if (recurring) null else end,
            duration = duration,
            allDay = start.allDay,
            timeZone = start.zone,
            rrule = rrule,
            exdate = if (recurring) formatUtcList(exdates) else null,
            rdate = formatUtcList(rdates),
        )
    }

    private fun timeList(props: List<IcsProperty>, defaultZone: ZoneId, resolve: (String) -> ZoneId?): List<Long> =
        props.filter { !it.params["VALUE"].equals("PERIOD", ignoreCase = true) }
            .flatMap { p -> p.value.split(',').mapNotNull { parseTime(p, it, defaultZone, resolve)?.millis } }
            .distinct()
            .sorted()

    private fun formatUtcList(times: List<Long>): String? =
        times.distinct().sorted().takeIf { it.isNotEmpty() }
            ?.joinToString(",") { UTC_LIST_FORMAT.format(java.time.Instant.ofEpochMilli(it)) }

    data class IcsTime(val millis: Long, val allDay: Boolean, val zone: String)

    /** Liest DATE, DATE-TIME in UTC (`…Z`), mit TZID oder ohne Zone (dann [defaultZone]). */
    fun parseTime(prop: IcsProperty, rawValue: String, defaultZone: ZoneId, resolve: (String) -> ZoneId?): IcsTime? {
        val value = rawValue.trim()
        if (value.isEmpty()) return null
        return try {
            if (prop.params["VALUE"].equals("DATE", ignoreCase = true) || (value.length == 8 && value.all(Char::isDigit))) {
                val date = LocalDate.parse(value.take(8), DATE)
                IcsTime(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay = true, zone = "UTC")
            } else if (value.endsWith("Z", ignoreCase = true)) {
                val ldt = parseLocalDateTime(value.dropLast(1)) ?: return null
                IcsTime(ldt.toInstant(ZoneOffset.UTC).toEpochMilli(), allDay = false, zone = "UTC")
            } else {
                val ldt = parseLocalDateTime(value) ?: return null
                val zone = prop.params["TZID"]?.let(resolve) ?: defaultZone
                IcsTime(ldt.atZone(zone).toInstant().toEpochMilli(), allDay = false, zone = zone.id)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLocalDateTime(v: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(v, DATE_TIME) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(v, DATE_TIME_NO_SECONDS) }.getOrNull()

    /** TZID → ZoneId: IANA-Name, Mozilla-Präfix (`/mozilla.org/…/Europe/Berlin`) oder Windows-Name. */
    fun resolveZone(tzid: String, windowsZone: (String) -> String?): ZoneId? {
        val clean = tzid.trim().removeSurrounding("\"")
        runCatching { return ZoneId.of(clean) }
        val segments = clean.trim('/').split('/')
        for (n in 3 downTo 1) {
            if (segments.size >= n) runCatching { return ZoneId.of(segments.takeLast(n).joinToString("/")) }
        }
        windowsZone(clean)?.let { id -> runCatching { return ZoneId.of(id) } }
        return null
    }

    /** RFC-5545-Dauer (`P1D`, `PT1H30M`, `P2W`, `-PT15M`) in Sekunden. */
    fun parseDurationSeconds(value: String): Long? {
        val m = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")
            .matchEntire(value.trim().uppercase()) ?: return null
        val (sign, w, d, h, min, s) = m.destructured
        val total = (w.toLongOrNull() ?: 0) * 7 * 86400 + (d.toLongOrNull() ?: 0) * 86400 +
            (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
        return if (sign == "-") -total else total
    }
}
