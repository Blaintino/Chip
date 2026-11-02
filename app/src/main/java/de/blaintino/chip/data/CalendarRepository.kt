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
)

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
            return EventDraft(
                id = id,
                calendarId = c.getLong(0),
                title = c.getString(1) ?: "",
                location = c.getString(2) ?: "",
                description = c.getString(3) ?: "",
                allDay = allDay,
                start = start,
                end = end,
                recurring = !c.getString(7).isNullOrEmpty() || !c.getString(8).isNullOrEmpty(),
            )
        }
        return null
    }

    /** Speichert einen neuen oder geänderten Einzeltermin. Serien werden hier nicht bearbeitet. */
    fun save(draft: EventDraft): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, draft.calendarId)
            put(CalendarContract.Events.TITLE, draft.title)
            put(CalendarContract.Events.EVENT_LOCATION, draft.location)
            put(CalendarContract.Events.DESCRIPTION, draft.description)
            put(CalendarContract.Events.ALL_DAY, if (draft.allDay) 1 else 0)
            if (draft.allDay) {
                val zone = ZoneId.systemDefault()
                val startDate = java.time.Instant.ofEpochMilli(draft.start).atZone(zone).toLocalDate()
                var endDate = java.time.Instant.ofEpochMilli(draft.end).atZone(zone).toLocalDate()
                if (endDate <= startDate) endDate = startDate.plusDays(1)
                put(CalendarContract.Events.DTSTART, startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, draft.start)
                put(CalendarContract.Events.DTEND, maxOf(draft.end, draft.start))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        }
        return if (draft.id == null) {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) } ?: -1
        } else {
            resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, draft.id), values, null, null)
            draft.id
        }
    }

    fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
    }

    private fun utcMidnightToLocal(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        /** Julianischer Tag von 1970-01-01. */
        private const val JULIAN_EPOCH_OFFSET = 2_440_588L
    }
}
