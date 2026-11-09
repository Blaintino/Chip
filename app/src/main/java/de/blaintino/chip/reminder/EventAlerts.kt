package de.blaintino.chip.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.blaintino.chip.MainActivity
import de.blaintino.chip.R
import de.blaintino.chip.data.Formats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Erinnerungen an Termine (Wunsch des Nutzers: Chip soll den Google Kalender ersetzen). Die Zeitpunkte
 * plant der Kalender-Provider von Android selbst aus der Tabelle `Reminders`; zur Zeit schickt er
 * `EVENT_REMINDER`, Chip liest die fälligen Einträge aus `CalendarAlerts` und zeigt je einen Hinweis.
 */
object EventAlerts {
    const val CHANNEL = "termine"
    const val EXTRA_DAY = "de.blaintino.chip.extra.DAY"
    private const val PREFS = "event_alerts"
    private const val KEY_SHOWN = "shown"
    private const val KEY_LAST = "last"

    /** Verspätete Meldungen (Doze) noch zeigen, ältere nicht mehr. */
    private const val LATE_MS = 30 * 60_000L

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Letzte gezeigte Erinnerung (für die Einstellungen, Fehlersuche ohne adb). */
    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Termin-Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Erinnerungen vor Terminen"
            },
        )
    }

    /** Fällige Erinnerungen zeigen; jede nur einmal (der Provider kann nach Zeitänderungen erneut melden). */
    fun showDue(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Gezeigte Erinnerungen: Schlüssel → Zeitpunkt; nach zwei Tagen vergessen.
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet())!!.mapNotNull {
            val i = it.lastIndexOf('@')
            if (i < 0) null else it.substring(0, i) to (it.substring(i + 1).toLongOrNull() ?: 0L)
        }.filter { now - it.second < 2 * 86_400_000L }.toMap().toMutableMap()

        val projection = arrayOf(
            CalendarContract.CalendarAlerts._ID,
            CalendarContract.CalendarAlerts.EVENT_ID,
            CalendarContract.CalendarAlerts.BEGIN,
            CalendarContract.CalendarAlerts.END,
            CalendarContract.CalendarAlerts.ALARM_TIME,
            CalendarContract.CalendarAlerts.STATE,
            CalendarContract.CalendarAlerts.TITLE,
            CalendarContract.CalendarAlerts.EVENT_LOCATION,
            CalendarContract.CalendarAlerts.ALL_DAY,
        )
        val toMark = mutableListOf<Long>()
        context.contentResolver.query(
            CalendarContract.CalendarAlerts.CONTENT_URI, projection,
            "${CalendarContract.CalendarAlerts.ALARM_TIME} <= ? AND ${CalendarContract.CalendarAlerts.ALARM_TIME} >= ?",
            arrayOf((now + 60_000L).toString(), (now - LATE_MS).toString()),
            "${CalendarContract.CalendarAlerts.ALARM_TIME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val alertId = c.getLong(0)
                val eventId = c.getLong(1)
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val alarmTime = c.getLong(4)
                val state = c.getInt(5)
                if (state == CalendarContract.CalendarAlerts.STATE_DISMISSED) continue
                val key = "$eventId:$begin:$alarmTime"
                if (key in shown) continue
                val title = c.getString(6)?.takeIf { it.isNotBlank() } ?: "(ohne Titel)"
                val location = c.getString(7).orEmpty()
                val allDay = c.getInt(8) == 1
                notify(context, key.hashCode(), title, describe(begin, end, allDay, location), begin, allDay)
                shown[key] = now
                if (state == CalendarContract.CalendarAlerts.STATE_SCHEDULED) toMark += alertId
                prefs.edit().putString(KEY_LAST, "${Formats.dateTime(now)} – $title").commit()
            }
        }
        // Als gezeigt markieren, wie es Kalender-Apps tun, damit der Provider sie nicht erneut meldet.
        for (id in toMark) {
            runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.CalendarAlerts.CONTENT_URI, id),
                    ContentValues().apply { put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_FIRED) },
                    null, null,
                )
            }
        }
        prefs.edit().putStringSet(KEY_SHOWN, shown.map { (k, t) -> "$k@$t" }.toSet()).commit()
    }

    private fun day(begin: Long, allDay: Boolean): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()

    private fun describe(begin: Long, end: Long, allDay: Boolean, location: String): String {
        val today = LocalDate.now()
        val d = day(begin, allDay)
        val dayText = when (d) {
            today -> "Heute"
            today.plusDays(1) -> "Morgen"
            else -> Formats.dayHeader(d, today)
        }
        val time = if (allDay) "ganztägig" else "${Formats.time(begin)} – ${Formats.time(end)}"
        return listOf("$dayText, $time", location).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun notify(context: Context, id: Int, title: String, text: String, begin: Long, allDay: Boolean) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DAY, day(begin, allDay).toEpochDay())
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(begin)
            .setShowWhen(!allDay)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify("termin", id, notification)
        } catch (_: SecurityException) {
        }
    }

    /** Tag aus einer angetippten Erinnerung, sonst null. */
    fun dayFrom(intent: Intent?): LocalDate? =
        intent?.takeIf { it.hasExtra(EXTRA_DAY) }?.getLongExtra(EXTRA_DAY, 0)?.let { LocalDate.ofEpochDay(it) }
}

/** Empfängt `EVENT_REMINDER` vom Kalender-Provider. */
class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EventAlerts.showDue(app)
            } finally {
                pending.finish()
            }
        }
    }
}
