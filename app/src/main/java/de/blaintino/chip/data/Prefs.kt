package de.blaintino.chip.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Welche Kalender in Chip (App und Widget) ausgeblendet sind. Ändert nichts am Konto selbst. */
object HiddenCalendars {
    private const val PREFS = "chip"
    private const val KEY = "hidden_calendars"

    fun get(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun set(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, ids.map { it.toString() }.toSet()).commit()
    }
}

/** Ein ICS-Abo. [calendarId] ist der lokale Kalender in CalendarContract, der die Termine hält. */
data class Subscription(
    val calendarId: Long,
    val url: String,
    val name: String,
    val color: Int,
    val lastSync: Long = 0,
    val lastError: String? = null,
    val eventCount: Int = 0,
    val etag: String? = null,
    val lastModified: String? = null,
)

object SubscriptionStore {
    private const val PREFS = "subscriptions"
    private const val KEY = "list"

    @Synchronized
    fun all(context: Context): List<Subscription> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Subscription(
                calendarId = o.optLong("calendarId", -1),
                url = o.optString("url"),
                name = o.optString("name"),
                color = o.optInt("color"),
                lastSync = o.optLong("lastSync", 0),
                lastError = o.optStringOrNull("lastError"),
                eventCount = o.optInt("eventCount", 0),
                etag = o.optStringOrNull("etag"),
                lastModified = o.optStringOrNull("lastModified"),
            )
        }.filter { it.calendarId >= 0 && it.url.isNotEmpty() }
    }

    @Synchronized
    fun save(context: Context, list: List<Subscription>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().apply {
                put("calendarId", s.calendarId)
                put("url", s.url)
                put("name", s.name)
                put("color", s.color)
                put("lastSync", s.lastSync)
                s.lastError?.let { put("lastError", it) }
                put("eventCount", s.eventCount)
                s.etag?.let { put("etag", it) }
                s.lastModified?.let { put("lastModified", it) }
            })
        }
        // commit(): der Hintergrund-Prozess kann sofort beendet werden.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
    }

    @Synchronized
    fun upsert(context: Context, sub: Subscription) {
        save(context, all(context).filter { it.calendarId != sub.calendarId } + sub)
    }

    @Synchronized
    fun remove(context: Context, calendarId: Long) {
        save(context, all(context).filter { it.calendarId != calendarId })
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
}

/**
 * Kalender der Plus-Knöpfe (Schnellauswahl, Wunsch des Nutzers: „Langners“ und „Arbeit“).
 * Gespeichert als Kalender-IDs; beim ersten Start nach Namen vorbelegt, danach im Tab „Kalender“
 * änderbar. Leere Liste = ein einfacher Plus-Knopf mit dem Standardkalender.
 */
object QuickAddCalendars {
    private const val PREFS = "chip"
    private const val KEY = "quick_add_calendars"
    const val SLOTS = 2
    val DEFAULT_NAMES = listOf("Langners", "Arbeit")

    /** IDs je Platz (null = leer), oder null, wenn noch nie festgelegt. */
    fun get(context: Context): List<Long?>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return raw.split(',').map { it.toLongOrNull() }.let { it + List(maxOf(0, SLOTS - it.size)) { null } }.take(SLOTS)
    }

    fun set(context: Context, ids: List<Long?>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, ids.joinToString(",") { it?.toString() ?: "" }).commit()
    }

    /** Vorbelegung nach Namen (ohne Groß-/Kleinschreibung) unter den beschreibbaren Kalendern. */
    fun defaults(calendars: List<CalendarInfo>): List<Long?> =
        DEFAULT_NAMES.map { name -> calendars.firstOrNull { it.writable && it.name.equals(name, ignoreCase = true) }?.id }
}
