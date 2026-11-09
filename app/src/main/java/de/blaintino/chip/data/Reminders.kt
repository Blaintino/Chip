package de.blaintino.chip.data

import android.content.Context

/**
 * Erinnerungen an Termine (Wunsch des Nutzers): bis zu [MAX] je Termin, Minuten vor Beginn.
 * Reines Kotlin bis auf [DefaultReminders].
 */
object ReminderRules {
    const val MAX = 3

    /** Vorgabe ab Werk: 1 Tag, 1 Stunde und 15 Minuten vorher. */
    val FACTORY = listOf(1440, 60, 15)

    /** Auswahl im Editor und in den Einstellungen. */
    val OPTIONS = listOf(0, 5, 10, 15, 30, 60, 120, 180, 1440, 2880, 10080)

    fun label(minutes: Int): String = when {
        minutes <= 0 -> "Zum Beginn"
        minutes % 10080 == 0 -> (minutes / 10080).let { if (it == 1) "1 Woche vorher" else "$it Wochen vorher" }
        minutes % 1440 == 0 -> (minutes / 1440).let { if (it == 1) "1 Tag vorher" else "$it Tage vorher" }
        minutes % 60 == 0 -> "${minutes / 60} Std. vorher"
        minutes > 60 -> "${minutes / 60} Std. ${minutes % 60} Min. vorher"
        else -> "$minutes Min. vorher"
    }

    /** Ohne Doppelte, früheste Erinnerung zuerst. */
    fun normalize(minutes: List<Int>): List<Int> = minutes.filter { it >= 0 }.distinct().sortedDescending()

    fun encode(minutes: List<Int>): String = normalize(minutes).take(MAX).joinToString(",")

    fun decode(raw: String): List<Int> = normalize(raw.split(',').mapNotNull { it.trim().toIntOrNull() }).take(MAX)
}

/** In den Einstellungen festgelegte Erinnerungen, die jeder neue Termin übernimmt. */
object DefaultReminders {
    private const val PREFS = "chip"
    private const val KEY = "default_reminders"

    fun get(context: Context): List<Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return ReminderRules.FACTORY
        return ReminderRules.decode(raw)
    }

    fun set(context: Context, minutes: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, ReminderRules.encode(minutes)).commit()
    }
}
