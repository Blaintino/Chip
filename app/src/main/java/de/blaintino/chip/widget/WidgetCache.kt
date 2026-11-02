package de.blaintino.chip.widget

import android.content.Context
import de.blaintino.chip.data.EventInstance
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Letzter Widget-Inhalt, damit ein neu startendes Widget sofort damit erscheint statt mit einem
 * Zwischenzustand – vorher flackerte es (große schwarze Fläche, Knöpfe springen), vom Nutzer gemeldet.
 * Im Speicher und zusätzlich in SharedPreferences (überlebt das Beenden des Prozesses).
 */
internal object WidgetCache {
    private const val PREFS = "widget_cache"

    @Volatile
    private var memory: WidgetData.Loaded? = null

    fun save(context: Context, data: WidgetData.Loaded) {
        memory = data
        val json = JSONObject().apply {
            put("weather", data.weather ?: JSONObject.NULL)
            put("quick", JSONArray().apply {
                data.quick.forEach { put(JSONObject().put("id", it.calendarId).put("name", it.name).put("color", it.color)) }
            })
            put("events", JSONArray().apply {
                data.events.forEach { e ->
                    put(
                        JSONObject().put("eventId", e.eventId).put("calendarId", e.calendarId).put("title", e.title)
                            .put("location", e.location).put("begin", e.begin).put("end", e.end).put("allDay", e.allDay)
                            .put("color", e.color).put("recurring", e.recurring)
                            .put("startDay", e.startDay.toString()).put("endDay", e.endDay.toString()),
                    )
                }
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json.toString()).apply()
    }

    /** Letzter Inhalt, vergangene Termine schon herausgefiltert; null, wenn es keinen gibt. */
    fun load(context: Context, now: Long = System.currentTimeMillis()): WidgetData.Loaded? {
        val data = memory ?: runCatching { read(context) }.getOrNull() ?: return null
        return data.copy(events = WidgetFilter.upcoming(data.events, now))
    }

    private fun read(context: Context): WidgetData.Loaded? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", null) ?: return null
        val json = JSONObject(raw)
        val quick = json.optJSONArray("quick") ?: JSONArray()
        val events = json.optJSONArray("events") ?: JSONArray()
        return WidgetData.Loaded(
            events = (0 until events.length()).map { i ->
                val e = events.getJSONObject(i)
                EventInstance(
                    eventId = e.getLong("eventId"),
                    calendarId = e.getLong("calendarId"),
                    title = e.getString("title"),
                    location = e.optString("location"),
                    begin = e.getLong("begin"),
                    end = e.getLong("end"),
                    allDay = e.getBoolean("allDay"),
                    color = e.getInt("color"),
                    recurring = e.optBoolean("recurring"),
                    startDay = LocalDate.parse(e.getString("startDay")),
                    endDay = LocalDate.parse(e.getString("endDay")),
                )
            }.filter { it.endDay >= LocalDate.now() },
            quick = (0 until quick.length()).map { i ->
                val q = quick.getJSONObject(i)
                QuickButton(q.getLong("id"), q.getString("name"), q.getInt("color"))
            },
            weather = if (json.isNull("weather")) null else json.optString("weather"),
        )
    }
}
