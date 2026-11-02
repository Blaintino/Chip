package de.blaintino.chip.widget

import de.blaintino.chip.data.EventInstance

/**
 * Was das Widget zeigt (Wunsch des Nutzers): vergangene Termine ausblenden, damit oben immer der
 * laufende oder nächste Termin steht. Ganztägige Termine bleiben den ganzen Tag. Reines Kotlin (testbar).
 */
object WidgetFilter {

    /** Termine, die noch laufen oder kommen: Ende nach [now]; Termine ohne Dauer bis zu ihrem Beginn. */
    fun upcoming(events: List<EventInstance>, now: Long): List<EventInstance> =
        events.filter { it.allDay || it.end > now || (it.end == it.begin && it.begin >= now) }

    /** Nächster Zeitpunkt nach [now], zu dem ein angezeigter Termin endet (dann neu zeichnen), sonst null. */
    fun nextChange(events: List<EventInstance>, now: Long): Long? =
        events.filter { !it.allDay }
            .map { maxOf(it.end, it.begin) }
            .filter { it > now }
            .minOrNull()
}
