package de.blaintino.chip.weather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Vom Nutzer gewählter Ort (Suche nach Namen, keine Standort-Berechtigung). */
data class WeatherPlace(val name: String, val latitude: Double, val longitude: Double)

/** Wetter eines Tages: Höchst-/Tiefstwert und Regenwahrscheinlichkeit je Stunde (0–100). */
data class WeatherDay(
    val date: LocalDate,
    val max: Double?,
    val min: Double?,
    val hourlyRain: List<Pair<LocalDateTime, Int>>,
)

/**
 * Wetter von Open-Meteo (open-meteo.com, frei, ohne Schlüssel, Daten CC BY 4.0; für Deutschland u. a.
 * DWD-Modelle). Ort per Namenssuche (Geocoding-API), Antwort zwischengespeichert, höchstens stündlich neu.
 */
object Weather {
    private const val PREFS = "weather"
    private const val MAX_AGE_MS = 60 * 60 * 1000L

    fun place(context: Context): WeatherPlace? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString("name", null) ?: return null
        return WeatherPlace(name, p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble())
    }

    /** [place] = null schaltet das Wetter aus. Löscht den Zwischenspeicher. */
    fun setPlace(context: Context, place: WeatherPlace?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply {
            if (place != null) {
                putString("name", place.name)
                putFloat("lat", place.latitude.toFloat())
                putFloat("lon", place.longitude.toFloat())
            }
        }.commit()
    }

    /** Ortssuche nach Namen (Netzwerk, nicht im Hauptthread aufrufen). */
    fun search(query: String): List<WeatherPlace> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=de&format=json&name=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val parts = listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() }.distinct()
            WeatherPlace(parts.joinToString(", "), o.optDouble("latitude"), o.optDouble("longitude"))
        }
    }

    /**
     * Wetter für heute. Lädt neu, wenn der Zwischenspeicher älter als eine Stunde oder von gestern ist
     * ([allowFetch]); bei einem Netzfehler bleibt der letzte Stand (falls von heute). null = aus/keine Daten.
     */
    fun today(context: Context, allowFetch: Boolean, now: LocalDateTime = LocalDateTime.now()): WeatherDay? {
        val place = place(context) ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var body = prefs.getString("body", null)
        val fetchedAt = prefs.getLong("fetchedAt", 0)
        val stale = body == null || System.currentTimeMillis() - fetchedAt > MAX_AGE_MS ||
            parseDay(body, now.toLocalDate()) == null
        if (stale && allowFetch) {
            try {
                body = get(
                    "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
                        "&daily=temperature_2m_max,temperature_2m_min&hourly=precipitation_probability" +
                        "&timezone=auto&forecast_days=2",
                )
                prefs.edit().putString("body", body).putLong("fetchedAt", System.currentTimeMillis()).commit()
            } catch (_: Exception) {
                // Letzten Stand behalten.
            }
        }
        return body?.let { parseDay(it, now.toLocalDate()) }
    }

    private fun parseDay(body: String, date: LocalDate): WeatherDay? = try {
        val json = JSONObject(body)
        val daily = json.getJSONObject("daily")
        val days = daily.getJSONArray("time")
        val idx = (0 until days.length()).firstOrNull { days.getString(it) == date.toString() }
        if (idx == null) {
            null
        } else {
            val hourly = json.optJSONObject("hourly")
            val hours = hourly?.optJSONArray("time") ?: JSONArray()
            val probs = hourly?.optJSONArray("precipitation_probability") ?: JSONArray()
            val rain = (0 until hours.length()).mapNotNull { i ->
                val t = runCatching { LocalDateTime.parse(hours.getString(i)) }.getOrNull() ?: return@mapNotNull null
                if (t.toLocalDate() != date || probs.isNull(i)) null else t to probs.optInt(i)
            }
            WeatherDay(
                date = date,
                max = daily.optJSONArray("temperature_2m_max")?.optDoubleOrNull(idx),
                min = daily.optJSONArray("temperature_2m_min")?.optDoubleOrNull(idx),
                hourlyRain = rain,
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun JSONArray.optDoubleOrNull(i: Int): Double? = if (isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Chip-Kalender (Android)")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }
}

/** Texte fürs Widget (reines Kotlin, testbar). */
object WeatherText {
    /** Ab dieser Regenwahrscheinlichkeit (Prozent) gibt es einen Hinweis. */
    const val RAIN_THRESHOLD = 40

    fun temperatures(day: WeatherDay): String? {
        val max = day.max?.roundToInt() ?: return null
        val min = day.min?.roundToInt() ?: return null
        return "↑ $max°  ↓ $min°"
    }

    /**
     * Regenhinweis für den Rest des Tages ab der laufenden Stunde: „☂ ab 15 Uhr (70 %)“, bzw.
     * „☂ jetzt (70 %)“, wenn es schon in der laufenden Stunde so weit ist; null ohne Regenrisiko.
     * Die Prozentzahl ist der höchste Wert der restlichen Stunden.
     */
    fun rainHint(day: WeatherDay, now: LocalDateTime): String? {
        val rest = day.hourlyRain.filter { (t, _) -> t.toLocalDate() == now.toLocalDate() && t.hour >= now.hour }
        val first = rest.firstOrNull { it.second >= RAIN_THRESHOLD } ?: return null
        val peak = rest.filter { !it.first.isBefore(first.first) }.maxOf { it.second }
        return if (first.first.hour <= now.hour) "☂ jetzt ($peak %)" else "☂ ab ${first.first.hour} Uhr ($peak %)"
    }
}
