package de.blaintino.chip

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Unsichtbares Sprungbrett für den Widget-Knopf „+ Quälgeist“: öffnet die Schnelleingabe von
 * Quälgeist (Aktion [ACTION_QUICK_ADD], braucht Quälgeist mit freigegebener Schnelleingabe), sonst
 * Quälgeist selbst, sonst einen Hinweis. Stabile App vor der Beta.
 */
class QuaelgeistActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val started = PACKAGES.any { pkg -> tryStart(Intent(ACTION_QUICK_ADD).setPackage(pkg)) } ||
            PACKAGES.any { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let(::tryStart) == true }
        if (!started) Toast.makeText(this, "Quälgeist ist nicht installiert", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val ACTION_QUICK_ADD = "de.quaelgeist.action.QUICK_ADD"
        private val PACKAGES = listOf("de.quaelgeist", "de.quaelgeist.beta")
    }
}

/** Intents, mit denen das Widget in Chip einen neuen Termin in einem bestimmten Kalender öffnet. */
object NewEventIntents {
    const val ACTION_NEW_EVENT = "de.blaintino.chip.action.NEW_EVENT"
    const val EXTRA_CALENDAR_ID = "calendar_id"

    fun forCalendar(context: android.content.Context, calendarId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_NEW_EVENT)
            // Eigene Daten-URI je Kalender, damit Android die PendingIntents nicht zusammenlegt.
            .setData(android.net.Uri.parse("chip://new-event/$calendarId"))
            .putExtra(EXTRA_CALENDAR_ID, calendarId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun calendarIdFrom(intent: Intent?): Long? =
        intent?.takeIf { it.action == ACTION_NEW_EVENT }?.getLongExtra(EXTRA_CALENDAR_ID, -1L)?.takeIf { it >= 0 }
}
