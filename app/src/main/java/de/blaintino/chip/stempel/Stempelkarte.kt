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
    private val STATUS_URI: Uri = Uri.parse("content://$PACKAGE.status/status")
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
            )
        }
    } catch (e: SecurityException) {
        Log.i(TAG, "Keine Berechtigung für die Stempelkarte", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Stempelkarte nicht lesbar", e)
        null
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
) {
    val loggedIn get() = state == "EINGELOGGT"

    /** Arbeitszeit läuft und ist bekannt – dann zeigt der Knopf sie statt „Eingeloggt“. */
    val timeKnown get() = loggedIn && workRunningSince > 0

    /** Heutige Arbeitszeit bis [now] (ms). */
    fun workedMs(now: Long): Long =
        workClosedMs + if (workRunningSince > 0) (now - workRunningSince).coerceAtLeast(0) else 0

    /** Was ein Tipp auf den Knopf auslöst: eingeloggt → ausloggen, sonst einloggen. */
    val tapAction get() = if (loggedIn) Stempelkarte.ACTION_LOGOUT else Stempelkarte.ACTION_LOGIN
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
