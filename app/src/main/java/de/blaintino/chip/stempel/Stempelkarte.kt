package de.blaintino.chip.stempel

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import de.blaintino.chip.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Anbindung an die eigene App FH Stempelkarte (Repo `Blaintino/FH-Stempelkarte`, dort `INTEROP.md`):
 * Status lesen, bei Änderungen neu zeichnen, ein-/ausloggen. Alles hinter der `signature`-Berechtigung
 * der Stempelkarte – nur Release-Builds mit demselben Schlüssel kommen heran (Debug-Chip nicht).
 */
object Stempelkarte {
    const val PACKAGE = "de.fhstempelkarte"
    const val PERMISSION = "$PACKAGE.permission.INTEROP"
    val STATUS_URI: Uri = Uri.parse("content://$PACKAGE.status/status")
    const val ACTION_STATE_CHANGED = "$PACKAGE.action.ZUSTAND_GEAENDERT"
    const val ACTION_LOGIN = "$PACKAGE.action.EINLOGGEN"
    const val ACTION_LOGOUT = "$PACKAGE.action.AUSLOGGEN"
    private const val TAG = "ChipStempel"

    /** Aktueller Status; null = Stempelkarte fehlt, zu alt oder keine Berechtigung (dann kein Knopf). */
    fun read(context: Context): StempelStatus? = try {
        context.contentResolver.query(STATUS_URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else StempelStatus(
                state = c.getString(c.getColumnIndexOrThrow("zustand")),
                color = c.intOrNull("farbe"),
                workClosedMs = c.longOrZero("arbeit_heute_ms"),
                workRunningSince = c.longOrZero("arbeit_laeuft_seit"),
                workBreakMs = c.longOrZero("arbeit_pausen_ms"),
            )
        }
    } catch (e: SecurityException) {
        Log.i(TAG, "Keine Berechtigung für die Stempelkarte", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Stempelkarte nicht lesbar", e)
        null
    }

    /** Zur Fehlersuche in den Einstellungen: wann und wodurch zuletzt ein Wechsel gemeldet wurde. */
    fun recordSignal(context: Context, source: String) {
        context.getSharedPreferences("stempel", Context.MODE_PRIVATE).edit()
            .putLong("signalTime", System.currentTimeMillis()).putString("signalSource", source).apply()
    }

    fun lastSignal(context: Context): Pair<Long, String>? {
        val p = context.getSharedPreferences("stempel", Context.MODE_PRIVATE)
        val source = p.getString("signalSource", null) ?: return null
        return p.getLong("signalTime", 0) to source
    }

    /** Ein Satz für die Einstellungen (Fehlersuche ohne adb). */
    fun describe(context: Context, now: Long = System.currentTimeMillis()): String {
        val s = read(context)
            ?: return "Nicht lesbar – FH Stempelkarte fehlt, ist älter als v17, oder Chip hat die Berechtigung nicht " +
                "(Debug-Build, oder Chip wurde vor der Stempelkarte installiert: dann Chip neu installieren)."
        val time = when {
            s.timeKnown -> ", heute netto ${formatDuration(s.workedMs(now))} (brutto ${formatDuration(s.grossMs(now))}, " +
                "Pausen genommen ${formatDuration(s.workBreakMs)}, läuft seit ${de.blaintino.chip.data.Formats.time(s.workRunningSince)}" +
                (if (s.paused(now)) ", steht gerade: fehlende Pause" else "") + ")"
            s.loggedIn -> ", Beginn unbekannt (Stempelkarte ab v19 und einmal neu einloggen)"
            s.workClosedMs > 0 -> ", heute netto ${formatDuration(s.workedMs(now))}"
            else -> ""
        }
        return "Zustand ${s.state}$time"
    }

    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        return "%d:%02d h".format(minutes / 60, minutes % 60)
    }

    private fun Cursor.intOrNull(name: String): Int? =
        getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let(::getInt)

    // Ältere Stempelkarte ohne Arbeitszeit-Spalten: 0 = unbekannt.
    private fun Cursor.longOrZero(name: String): Long =
        getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let(::getLong) ?: 0
}

/** Stand der Stempelkarte, wie das Widget ihn braucht. Rein (Test `StempelStatusTest`). */
data class StempelStatus(
    /** `NEUTRAL`, `NICHT_EINGELOGGT` oder `EINGELOGGT`. */
    val state: String,
    /** Farbe der Stempelkarte (Rot/Grün), null bei Neutral. */
    val color: Int?,
    val workClosedMs: Long,
    /** Beginn der laufenden Sitzung (ms), 0 = keine bzw. unbekannt. */
    val workRunningSince: Long,
    /** Angerechnete Pausen heute (ausgeloggt ≥ 15 min), ältere Stempelkarte: 0. */
    val workBreakMs: Long = 0,
) {
    val loggedIn get() = state == "EINGELOGGT"

    /** Arbeitszeit läuft und ist bekannt – dann zeigt der Knopf sie statt „Eingeloggt“. */
    val timeKnown get() = loggedIn && workRunningSince > 0

    /** Brutto-Anwesenheit heute bis [now] (ms). */
    fun grossMs(now: Long): Long =
        workClosedMs + if (workRunningSince > 0) (now - workRunningSince).coerceAtLeast(0) else 0

    /** Fehlende Pause bis 30 min (ab 6 h) und die weiteren bis 45 min (ab 9 h). */
    private val missing1 get() = (30 * MIN - workBreakMs).coerceAtLeast(0)
    private val missing2 get() = (45 * MIN - workBreakMs).coerceAtLeast(0) - missing1

    /**
     * Heutige Nettoarbeitszeit bis [now] (ms), Pausen nach § 4 ArbZG wie in der Stempelkarte (`WorkTime.net`,
     * Wunsch des Nutzers): mehr als 6 h → 30 min, mehr als 9 h → 45 min insgesamt; nur die noch fehlende Pause
     * wird gleitend abgezogen – die Zeit steht bei 6:00 bzw. 9:00, bis sie verstrichen ist.
     */
    fun workedMs(now: Long): Long {
        var n = grossMs(now)
        if (n > 6 * HOUR) n = maxOf(6 * HOUR, n - missing1)
        if (n > 9 * HOUR) n = maxOf(9 * HOUR, n - missing2)
        return n
    }

    /** Brutto-Zeitpunkte, an denen der Timer anhält bzw. weiterläuft (nur Stufen mit fehlender Pause). */
    private fun breakPoints(): List<Pair<Long, Long>> = listOfNotNull(
        (6 * HOUR to 6 * HOUR + missing1).takeIf { missing1 > 0 },
        (9 * HOUR + missing1 to 9 * HOUR + missing1 + missing2).takeIf { missing2 > 0 },
    )

    /** Steht der Timer gerade (fehlende Pause wird abgezogen)? */
    fun paused(now: Long): Boolean {
        if (!timeKnown) return false
        val g = grossMs(now)
        return breakPoints().any { (from, to) -> g >= from && g < to }
    }

    /** Nächster Zeitpunkt (ms), an dem der Timer anhält oder weiterläuft – dann neu zeichnen; null = keiner. */
    fun nextChange(now: Long): Long? {
        if (!timeKnown) return null
        val g = grossMs(now)
        return breakPoints().flatMap { listOf(it.first, it.second) }.filter { it > g }.minOrNull()?.let { now + (it - g) }
    }

    /** Was ein Tipp auf den Knopf auslöst: eingeloggt → ausloggen, sonst einloggen. */
    val tapAction get() = if (loggedIn) Stempelkarte.ACTION_LOGOUT else Stempelkarte.ACTION_LOGIN

    private companion object {
        const val MIN = 60_000L
        const val HOUR = 60 * MIN
    }
}

/**
 * Unsichtbares Sprungbrett für den Stempel-Knopf im Widget (wie `QuaelgeistActivity`): startet die
 * Aktion der Stempelkarte (danach öffnet sie ZEUS), sonst ein Hinweis.
 */
class StempelkarteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action?.takeIf { it == Stempelkarte.ACTION_LOGIN || it == Stempelkarte.ACTION_LOGOUT }
        val message = when {
            action == null -> null
            else -> try {
                startActivity(Intent(action).setPackage(Stempelkarte.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                null
            } catch (_: ActivityNotFoundException) {
                "FH Stempelkarte ist nicht installiert oder zu alt"
            } catch (_: SecurityException) {
                "Keine Berechtigung für die FH Stempelkarte"
            }
        }
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        /** Eigene Action je Knopf, damit Android die PendingIntents nicht zusammenlegt. */
        fun intent(context: Context, action: String): Intent =
            Intent(context, StempelkarteActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Zustandswechsel der Stempelkarte (expliziter Broadcast, nur mit Berechtigung): Widget neu zeichnen. */
class StempelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Stempelkarte.ACTION_STATE_CHANGED) return
        val app = context.applicationContext
        Stempelkarte.recordSignal(app, "Meldung der Stempelkarte (${intent.getStringExtra("zustand") ?: "?"})")
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                TodayWidget.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
