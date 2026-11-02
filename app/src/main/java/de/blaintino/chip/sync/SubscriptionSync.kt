package de.blaintino.chip.sync

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import de.blaintino.chip.data.CalendarRepository
import de.blaintino.chip.data.Subscription
import de.blaintino.chip.data.SubscriptionStore
import de.blaintino.chip.ics.EventRow
import de.blaintino.chip.ics.IcsMapper
import de.blaintino.chip.ics.IcsParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * ICS-Abos: Jedes Abo ist ein eigener, nur lesbarer lokaler Kalender (Kontotyp LOCAL) in
 * CalendarContract. Beim Aktualisieren wird die Datei geladen, vollständig gelesen und erst dann
 * der Kalenderinhalt ersetzt – ein Fehler beim Laden lässt die alten Termine stehen.
 */
object SubscriptionSync {

    const val ACCOUNT_NAME = "Chip-Abos"
    private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    private const val MAX_BYTES = 20 * 1024 * 1024
    private const val BATCH = 200
    /** Byte-Order-Mark, die manche Server vor die Datei setzen. */
    private const val BOM = '\uFEFF'

    sealed interface Outcome {
        data class Updated(val count: Int, val calendarName: String?) : Outcome
        data object NotModified : Outcome
        data class Failed(val message: String) : Outcome
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()

    /** `webcal://` und `webcals://` sind https (bzw. http) mit anderem Namen. */
    fun normalizeUrl(input: String): String {
        val url = input.trim()
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("webcals://") -> "https://" + url.substring(10)
            lower.startsWith("webcal://") -> "https://" + url.substring(9)
            lower.startsWith("http://") || lower.startsWith("https://") -> url
            else -> "https://$url"
        }
    }

    fun createCalendar(context: Context, name: String, color: Int): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
            ?: throw IOException("Kalender konnte nicht angelegt werden")
        return ContentUris.parseId(uri)
    }

    fun updateCalendar(context: Context, calendarId: Long, name: String, color: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
        }
        context.contentResolver.update(
            ContentUris.withAppendedId(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), calendarId), values, null, null,
        )
    }

    fun deleteCalendar(context: Context, calendarId: Long) {
        context.contentResolver.delete(
            ContentUris.withAppendedId(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), calendarId), null, null,
        )
    }

    /** Neues Abo: Kalender anlegen, sofort laden, Name aus der Datei übernehmen, falls keiner angegeben. */
    fun add(context: Context, rawUrl: String, name: String, color: Int): Pair<Subscription, Outcome> {
        val url = normalizeUrl(rawUrl)
        val initialName = name.ifBlank { "Abo" }
        val calendarId = createCalendar(context, initialName, color)
        var sub = Subscription(calendarId = calendarId, url = url, name = initialName, color = color)
        SubscriptionStore.upsert(context, sub)
        val outcome = refresh(context, sub)
        sub = SubscriptionStore.all(context).firstOrNull { it.calendarId == calendarId } ?: sub
        if (name.isBlank() && outcome is Outcome.Updated && outcome.calendarName != null) {
            sub = sub.copy(name = outcome.calendarName)
            updateCalendar(context, calendarId, sub.name, color)
            SubscriptionStore.upsert(context, sub)
        }
        return sub to outcome
    }

    fun remove(context: Context, sub: Subscription) {
        deleteCalendar(context, sub.calendarId)
        SubscriptionStore.remove(context, sub.calendarId)
    }

    fun refreshAll(context: Context): List<Pair<Subscription, Outcome>> =
        SubscriptionStore.all(context).map { it to refresh(context, it) }

    fun refresh(context: Context, sub: Subscription): Outcome {
        if (!CalendarRepository(context).hasPermission()) return Outcome.Failed("Kein Kalenderzugriff")
        val outcome = try {
            val download = download(sub.url, sub.etag, sub.lastModified)
            if (download == null) {
                Outcome.NotModified
            } else {
                val calendar = IcsParser.parse(download.body)
                val rows = IcsMapper.map(calendar, ZoneId.systemDefault(), ::windowsZoneToIana)
                if (calendar.events.isEmpty() && !download.body.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                    throw IOException("Keine Kalenderdatei (kein BEGIN:VCALENDAR)")
                }
                replaceEvents(context, sub.calendarId, rows)
                SubscriptionStore.upsert(
                    context,
                    sub.copy(etag = download.etag, lastModified = download.lastModified, eventCount = rows.size),
                )
                Outcome.Updated(rows.size, calendar.name)
            }
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
        val latest = SubscriptionStore.all(context).firstOrNull { it.calendarId == sub.calendarId } ?: return outcome
        SubscriptionStore.upsert(
            context,
            when (outcome) {
                is Outcome.Failed -> latest.copy(lastError = outcome.message)
                else -> latest.copy(lastError = null, lastSync = System.currentTimeMillis())
            },
        )
        return outcome
    }

    private class Download(val body: String, val etag: String?, val lastModified: String?)

    /** Lädt die Datei; `null` bei HTTP 304 (unverändert). Folgt Weiterleitungen auch http→https. */
    private fun download(url: String, etag: String?, lastModified: String?): Download? {
        var current = URL(url)
        repeat(6) {
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("User-Agent", "Chip-Kalender (Android)")
                conn.setRequestProperty("Accept", "text/calendar, */*")
                etag?.let { conn.setRequestProperty("If-None-Match", it) }
                lastModified?.let { conn.setRequestProperty("If-Modified-Since", it) }
                val code = conn.responseCode
                when {
                    code == HttpURLConnection.HTTP_NOT_MODIFIED -> return null
                    code in 300..399 -> {
                        val location = conn.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel")
                        current = URL(current, location)
                    }
                    code !in 200..299 -> throw IOException("HTTP $code ${conn.responseMessage ?: ""}".trim())
                    else -> {
                        val bytes = conn.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                if (out.size() > MAX_BYTES) throw IOException("Datei größer als 20 MB")
                            }
                            out.toByteArray()
                        }
                        return Download(
                            body = String(bytes, Charsets.UTF_8).trimStart(BOM),
                            etag = conn.getHeaderField("ETag"),
                            lastModified = conn.getHeaderField("Last-Modified"),
                        )
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    private fun replaceEvents(context: Context, calendarId: Long, rows: List<EventRow>) {
        val resolver = context.contentResolver
        val eventsUri = asSyncAdapter(CalendarContract.Events.CONTENT_URI)
        resolver.delete(eventsUri, "${CalendarContract.Events.CALENDAR_ID} = ?", arrayOf(calendarId.toString()))
        rows.chunked(BATCH).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>(chunk.size)
            for (row in chunk) {
                ops += ContentProviderOperation.newInsert(eventsUri).withValues(toValues(calendarId, row)).build()
            }
            resolver.applyBatch(CalendarContract.AUTHORITY, ops)
        }
    }

    private fun toValues(calendarId: Long, row: EventRow) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events._SYNC_ID, row.uid)
        put(CalendarContract.Events.TITLE, row.title)
        put(CalendarContract.Events.EVENT_LOCATION, row.location)
        put(CalendarContract.Events.DESCRIPTION, row.description)
        put(CalendarContract.Events.DTSTART, row.dtStart)
        if (row.dtEnd != null) put(CalendarContract.Events.DTEND, row.dtEnd)
        if (row.duration != null) put(CalendarContract.Events.DURATION, row.duration)
        put(CalendarContract.Events.ALL_DAY, if (row.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, row.timeZone)
        if (row.rrule != null) put(CalendarContract.Events.RRULE, row.rrule)
        if (row.rdate != null) put(CalendarContract.Events.RDATE, row.rdate)
        if (row.exdate != null) put(CalendarContract.Events.EXDATE, row.exdate)
        put(CalendarContract.Events.HAS_ALARM, 0)
    }

    private fun windowsZoneToIana(name: String): String? =
        runCatching { android.icu.util.TimeZone.getIDForWindowsID(name, "DE") }.getOrNull()
            ?: runCatching { android.icu.util.TimeZone.getIDForWindowsID(name, null) }.getOrNull()
}
