package de.blaintino.chip.sync

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.blaintino.chip.widget.TodayWidget
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Hintergrundarbeit über WorkManager:
 * - tägliches Aktualisieren aller ICS-Abos (nur mit Netz),
 * - Widget neu zeichnen, sobald sich irgendetwas in der Kalenderdatenbank ändert
 *   (Google-Sync, Abo, eigene Änderung) – über einen Content-URI-Auslöser,
 * - Widget kurz nach Mitternacht neu zeichnen (neuer Tag).
 */
object Background {
    private const val SYNC_WORK = "ics-sync-daily"
    private const val SYNC_NOW_WORK = "ics-sync-now"
    private const val CHANGE_WORK = "calendar-change-watch"
    private const val MIDNIGHT_WORK = "widget-midnight"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SubscriptionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        watchCalendarChanges(context, ExistingWorkPolicy.KEEP)
        scheduleMidnight(context, ExistingWorkPolicy.KEEP)
    }

    /** Alle Abos sofort aktualisieren (z. B. nach dem App-Start, falls lange nicht geschehen). */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    internal fun watchCalendarChanges(context: Context, policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHANGE_WORK,
            policy,
            OneTimeWorkRequestBuilder<CalendarChangeWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                        .setTriggerContentUpdateDelay(Duration.ofSeconds(2))
                        .setTriggerContentMaxDelay(Duration.ofSeconds(30))
                        .build(),
                )
                .build(),
        )
    }

    internal fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy) {
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1)
        val delay = Duration.between(ZonedDateTime.now(zone), next)
        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK,
            policy,
            OneTimeWorkRequestBuilder<MidnightWorker>().setInitialDelay(delay).build(),
        )
    }
}

class SubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SubscriptionSync.refreshAll(applicationContext)
        TodayWidget.refresh(applicationContext)
        // Fehler einzelner Abos stehen am Abo selbst (Kalender-Tab); kein Retry-Sturm.
        return Result.success()
    }
}

class CalendarChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget.refresh(applicationContext)
        // Content-Auslöser gelten nur einmal: gleich wieder anmelden (nach diesem Lauf).
        Background.watchCalendarChanges(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget.refresh(applicationContext)
        Background.scheduleMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
