package de.blaintino.chip.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    /** Angezeigte Farbe (eigene Farbe aus [CalendarColors], sonst die des Kontos). */
    val color: Int,
    val writable: Boolean,
    /** Farbe laut Konto, für „Zurücksetzen“. */
    val accountColor: Int = color,
)

/** Ein Vorkommen eines Termins (bei Serien: ein einzelner Termin der Serie). */
data class EventInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val recurring: Boolean,
    /** Erster und letzter Tag (lokal), an dem der Termin stattfindet. */
    val startDay: LocalDate,
    val endDay: LocalDate,
)

/** Inhalt des Editors. Bei ganztägigen Terminen zählen nur die Datumsangaben. */
data class EventDraft(
    val id: Long?,
    val calendarId: Long,
    val title: String,
    val location: String,
    val description: String,
    val allDay: Boolean,
    val start: Long,
    val end: Long,
    val recurring: Boolean = false,
    /** Wiederholung (Editor-Auswahl; beim Laden aus der Regel der Serie gelesen). */
    val repeat: Repeat = Repeat.NONE,
    /** Bisherige Regel der Serie (roh), bleibt bei [RepeatKind.CUSTOM] erhalten. */
    val rrule: String? = null,
    /** Gesetzt, wenn dieser Termin ein einzeln geänderter Termin einer Serie ist (Ausnahme-Zeile). */
    val originalId: Long? = null,
    /** Vorkommen einer Serie: Instances.BEGIN (roh) bzw. bei Ausnahmen ORIGINAL_INSTANCE_TIME. */
    val instanceBegin: Long? = null,
    /** Vorkommen einer Serie: angezeigter Beginn beim Öffnen (wie [start]), für das Verschieben der ganzen Serie. */
    val instanceStart: Long? = null,
    /** Beginn des ersten Termins der Serie (wie [start] umgerechnet). */
    val seriesStart: Long? = null,
)

/** Welche Termine einer Serie eine Änderung oder das Löschen betrifft. */
enum class SeriesScope { THIS, FOLLOWING, ALL }

/**
 * Zugriff auf die Kalenderdatenbank von Android (CalendarContract). Google-Kalender (auch
 * geteilte) synchronisiert das Google-Konto hinein, ICS-Abos legt [de.blaintino.chip.sync.SubscriptionSync]
 * als lokale Kalender an.
 */
class CalendarRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val result = mutableListOf<CalendarInfo>()
        val overrides = CalendarColors.get(appContext)
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection,
            "${CalendarContract.Calendars.SYNC_EVENTS} = 1", null,
            "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { c ->
            while (c.moveToNext()) {
                result += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "(ohne Namen)",
                    accountName = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "",
                    color = overrides[c.getLong(0)] ?: c.getInt(4),
                    writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    accountColor = c.getInt(4),
                )
            }
        }
        return result
    }

    /** Alle Vorkommen zwischen [from] und [to] (lokale Tage, [to] einschließlich). */
    fun instances(from: LocalDate, to: LocalDate, hiddenCalendars: Set<Long> = emptySet()): List<EventInstance> {
        if (!hasPermission()) return emptyList()
        val zone = ZoneId.systemDefault()
        // Etwas Puffer, damit ganztägige Termine (in UTC gespeichert) sicher dabei sind.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.END_DAY,
            CalendarContract.Instances.EVENT_COLOR,
        )
        val result = mutableListOf<EventInstance>()
        val overrides = CalendarColors.get(appContext)
        resolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val calendarId = c.getLong(1)
                if (calendarId in hiddenCalendars) continue
                // START_DAY/END_DAY sind julianische Tage, bei ganztägigen Terminen schon richtig
                // (ohne Zeitzonenversatz) gerechnet.
                val startDay = LocalDate.ofEpochDay(c.getLong(10) - JULIAN_EPOCH_OFFSET)
                val endDay = LocalDate.ofEpochDay(c.getLong(11) - JULIAN_EPOCH_OFFSET)
                if (endDay < from || startDay > to) continue
                result += EventInstance(
                    eventId = c.getLong(0),
                    calendarId = calendarId,
                    title = c.getString(2)?.takeIf { it.isNotBlank() } ?: "(ohne Titel)",
                    location = c.getString(3) ?: "",
                    begin = c.getLong(4),
                    end = c.getLong(5),
                    allDay = c.getInt(6) == 1,
                    // Eigene Terminfarbe bleibt, sonst eigene Kalenderfarbe, sonst die des Kontos.
                    color = if (!c.isNull(12) && c.getInt(12) != 0) c.getInt(7) else overrides[calendarId] ?: c.getInt(7),
                    recurring = !c.getString(8).isNullOrEmpty() || !c.getString(9).isNullOrEmpty(),
                    startDay = startDay,
                    endDay = endDay,
                )
            }
        }
        return result.sortedWith(compareBy({ it.startDay }, { !it.allDay }, { it.begin }))
    }

    fun event(id: Long): EventDraft? {
        if (!hasPermission()) return null
        val projection = arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
        )
        resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val allDay = c.getInt(4) == 1
            var start = c.getLong(5)
            var end = if (c.isNull(6)) start else c.getLong(6)
            if (allDay) {
                // Gespeichert als Mitternacht UTC; im Editor als lokale Mitternacht desselben Datums.
                start = utcMidnightToLocal(start)
                end = utcMidnightToLocal(end)
            }
            val rrule = c.getString(7)?.takeIf { it.isNotBlank() }
            val rdate = c.getString(8)?.takeIf { it.isNotBlank() }
            val zone = ZoneId.systemDefault()
            val startDate = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val repeat = when {
                rrule != null -> RepeatRules.parse(rrule, startDate, zone)
                rdate != null -> Repeat(RepeatKind.CUSTOM)
                else -> Repeat.NONE
            }
            return EventDraft(
                id = id,
                calendarId = c.getLong(0),
                title = c.getString(1) ?: "",
                location = c.getString(2) ?: "",
                description = c.getString(3) ?: "",
                allDay = allDay,
                start = start,
                end = end,
                recurring = rrule != null || rdate != null,
                repeat = repeat,
                rrule = rrule,
                originalId = if (c.isNull(9)) null else c.getLong(9).takeIf { it > 0 },
                instanceBegin = if (c.isNull(10)) null else c.getLong(10),
            )
        }
        return null
    }

    /**
     * Termin so, wie er im Editor angezeigt wird: bei Serien das angetippte Vorkommen (Beginn/Ende des
     * Vorkommens), die Serie selbst steht in [EventDraft.seriesStart].
     */
    fun eventForInstance(ev: EventInstance): EventDraft? {
        val d = event(ev.eventId) ?: return null
        if (!d.recurring) return d
        val s = if (d.allDay) utcMidnightToLocal(ev.begin) else ev.begin
        val e = if (d.allDay) utcMidnightToLocal(ev.end) else ev.end
        return d.copy(start = s, end = maxOf(e, s), seriesStart = d.start, instanceStart = s, instanceBegin = ev.begin)
    }

    /**
     * Speichert einen neuen Termin, einen Einzeltermin oder einen einzeln geänderten Serientermin
     * (Ausnahme-Zeile). Eine Wiederholung aus dem Editor macht aus einem Einzeltermin eine Serie.
     */
    fun save(draft: EventDraft): Long {
        val rrule = if (draft.originalId != null) null else ruleFor(draft, draft.start)
        return write(draft.id, draft, rrule)
    }

    /** Änderung an einem Vorkommen einer Serie. */
    fun saveSeries(draft: EventDraft, scope: SeriesScope) {
        val masterId = draft.id ?: return
        val instanceBegin = draft.instanceBegin ?: return
        val master = masterRow(masterId) ?: return
        when (scope) {
            SeriesScope.THIS -> {
                // Ausnahme-Zeile wie die Kalender-App: Android verknüpft sie mit dem Vorkommen.
                val values = baseValues(draft).apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                    put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (master.allDay) 1 else 0)
                    put(CalendarContract.Events.DTEND, endMillis(draft))
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                }
                resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, masterId), values)
            }
            SeriesScope.FOLLOWING -> {
                if (instanceBegin <= master.dtStart) {
                    write(masterId, draft, ruleFor(draft, draft.start) ?: if (draft.repeat.kind == RepeatKind.CUSTOM) master.rrule else null)
                } else {
                    endSeriesBefore(masterId, master, instanceBegin)
                    val rule = ruleFor(draft, draft.start) ?: if (draft.repeat.kind == RepeatKind.CUSTOM) master.rrule else null
                    write(null, draft.copy(id = null), rule)
                }
            }
            SeriesScope.ALL -> {
                // Verschiebung des Vorkommens (Wanduhrzeit) auf den ersten Termin der Serie übertragen.
                val zone = ZoneId.systemDefault()
                fun ldt(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
                val oldInst = ldt(draft.instanceStart ?: draft.start)
                val newInst = ldt(draft.start)
                val shift = java.time.Duration.between(oldInst, newInst)
                val length = java.time.Duration.between(newInst, ldt(draft.end))
                val newStart = ldt(draft.seriesStart ?: draft.start).plus(shift)
                val newEnd = newStart.plus(length)
                val moved = draft.copy(
                    start = newStart.atZone(zone).toInstant().toEpochMilli(),
                    end = newEnd.atZone(zone).toInstant().toEpochMilli(),
                )
                val original = RepeatRules.parse(master.rrule, ldt(draft.seriesStart ?: draft.start).toLocalDate(), zone)
                val sameDay = oldInst.toLocalDate() == newInst.toLocalDate()
                val rule = when {
                    draft.repeat == original && sameDay -> master.rrule
                    draft.repeat.kind == RepeatKind.CUSTOM -> master.rrule
                    else -> ruleFor(moved, moved.start)
                }
                write(masterId, moved, rule)
            }
        }
    }

    /** Löscht einen Termin; bei Serien je nach [scope] nur das Vorkommen, ab hier oder alles. */
    fun deleteEvent(draft: EventDraft, scope: SeriesScope) {
        val id = draft.id ?: return
        val masterId = draft.originalId ?: id.takeIf { draft.recurring }
        if (masterId == null) {
            delete(id)
            return
        }
        when (scope) {
            SeriesScope.THIS -> if (draft.originalId != null) {
                // Ausnahme-Zeile absagen: das Vorkommen verschwindet, ohne dass die Serie es zurückbekommt.
                resolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
                    ContentValues().apply { put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED) },
                    null, null,
                )
            } else {
                val begin = draft.instanceBegin ?: return
                val master = masterRow(masterId) ?: return
                resolver.insert(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, masterId),
                    ContentValues().apply {
                        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, begin)
                        put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (master.allDay) 1 else 0)
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                    },
                )
            }
            SeriesScope.FOLLOWING -> {
                val begin = draft.instanceBegin ?: return
                val master = masterRow(masterId) ?: return
                if (begin <= master.dtStart) delete(masterId) else endSeriesBefore(masterId, master, begin)
            }
            SeriesScope.ALL -> delete(masterId)
        }
    }

    fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
    }

    private data class MasterRow(val dtStart: Long, val rrule: String?, val allDay: Boolean)

    private fun masterRow(id: Long): MasterRow? {
        val projection = arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.RRULE, CalendarContract.Events.ALL_DAY)
        resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            return MasterRow(c.getLong(0), c.getString(1)?.takeIf { it.isNotBlank() }, c.getInt(2) == 1)
        }
        return null
    }

    /** Serie endet vor [cutoff]; später einzeln geänderte Termine fallen mit weg. */
    private fun endSeriesBefore(masterId: Long, master: MasterRow, cutoff: Long) {
        val rrule = master.rrule ?: return
        resolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, masterId),
            ContentValues().apply { put(CalendarContract.Events.RRULE, RepeatRules.endBefore(rrule, cutoff, master.allDay)) },
            null, null,
        )
        resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.ORIGINAL_ID} = ? AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} >= ?",
            arrayOf(masterId.toString(), cutoff.toString()),
        )
    }

    /** RRULE aus der Editor-Auswahl, gerechnet ab dem Tag von [start]. */
    private fun ruleFor(draft: EventDraft, start: Long): String? {
        val zone = ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        return RepeatRules.build(draft.repeat, day, draft.allDay, zone)
            ?: if (draft.repeat.kind == RepeatKind.CUSTOM) draft.rrule else null
    }

    /** Gemeinsame Felder ohne Ende und Regel. */
    private fun baseValues(draft: EventDraft) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, draft.calendarId)
        put(CalendarContract.Events.TITLE, draft.title)
        put(CalendarContract.Events.EVENT_LOCATION, draft.location)
        put(CalendarContract.Events.DESCRIPTION, draft.description)
        put(CalendarContract.Events.ALL_DAY, if (draft.allDay) 1 else 0)
        put(CalendarContract.Events.DTSTART, startMillis(draft))
        put(CalendarContract.Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else TimeZone.getDefault().id)
    }

    private fun allDayDates(draft: EventDraft): Pair<LocalDate, LocalDate> {
        val zone = ZoneId.systemDefault()
        val startDate = java.time.Instant.ofEpochMilli(draft.start).atZone(zone).toLocalDate()
        var endDate = java.time.Instant.ofEpochMilli(draft.end).atZone(zone).toLocalDate()
        if (endDate <= startDate) endDate = startDate.plusDays(1)
        return startDate to endDate
    }

    private fun startMillis(draft: EventDraft): Long =
        if (draft.allDay) allDayDates(draft).first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else draft.start

    private fun endMillis(draft: EventDraft): Long =
        if (draft.allDay) allDayDates(draft).second.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else maxOf(draft.end, draft.start)

    /** Schreibt die Termin-Zeile; mit [rrule] als Serie (CalendarContract: DURATION statt DTEND). */
    private fun write(id: Long?, draft: EventDraft, rrule: String?): Long {
        val values = baseValues(draft).apply {
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                val duration = if (draft.allDay) {
                    val (s, e) = allDayDates(draft)
                    "P${e.toEpochDay() - s.toEpochDay()}D"
                } else {
                    "P${(endMillis(draft) - draft.start) / 1000}S"
                }
                put(CalendarContract.Events.DURATION, duration)
                putNull(CalendarContract.Events.DTEND)
            } else {
                put(CalendarContract.Events.DTEND, endMillis(draft))
                putNull(CalendarContract.Events.RRULE)
                putNull(CalendarContract.Events.DURATION)
            }
        }
        return if (id == null) {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) } ?: -1
        } else {
            resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null)
            id
        }
    }

    private fun utcMidnightToLocal(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        /** Julianischer Tag von 1970-01-01. */
        private const val JULIAN_EPOCH_OFFSET = 2_440_588L
    }
}
