package de.blaintino.chip.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Detailstufen der Kalenderansicht, von fein nach grob. */
enum class CalendarZoom(val label: String) {
    DAY("Tag"),
    WEEK("Woche"),
    MONTH("Monat"),
    YEAR("Jahr"),
    ;

    /** Aufziehen: eine Stufe feiner (Jahr → Monat → Woche → Tag). */
    fun zoomIn(): CalendarZoom = entries[maxOf(ordinal - 1, 0)]

    /** Zusammenschieben: eine Stufe gröber (Tag → Woche → Monat → Jahr). */
    fun zoomOut(): CalendarZoom = entries[minOf(ordinal + 1, entries.lastIndex)]

    /** Gewählten Tag um [steps] Zeiträume dieser Stufe verschieben (Wischen, Pfeile). */
    fun shift(day: java.time.LocalDate, steps: Int): java.time.LocalDate = when (this) {
        DAY -> day.plusDays(steps.toLong())
        WEEK -> day.plusWeeks(steps.toLong())
        MONTH -> day.plusMonths(steps.toLong())
        YEAR -> day.plusYears(steps.toLong())
    }
}

private const val ZOOM_IN_THRESHOLD = 1.25f
private const val ZOOM_OUT_THRESHOLD = 0.8f
private val SWIPE_DISTANCE = 72.dp

/**
 * Gesten der Kalenderansichten, in einem Durchgang ausgewertet:
 * - zwei Finger aufziehen → [onZoomIn], zusammenschieben → [onZoomOut] (höchstens einmal je Geste),
 * - ein Finger waagerecht wischen → [onSwipe] mit +1 (nach links = nächster Zeitraum) bzw. −1.
 *
 * Läuft im Initial-Durchgang. Ereignisse werden erst verbraucht, sobald die Geste erkannt ist
 * (zwei Finger bzw. klar waagerechte Bewegung über die Berührungsschwelle) – senkrechtes Scrollen
 * und Tippen kommen sonst unverändert bei den Ansichten an; ein erkanntes Wischen bricht das Tippen
 * darunter ab, damit kein Termin angelegt wird.
 */
fun Modifier.calendarGestures(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onSwipe: (Int) -> Unit,
): Modifier = composed {
    val zoomIn by rememberUpdatedState(onZoomIn)
    val zoomOut by rememberUpdatedState(onZoomOut)
    val swipe by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val swipePx = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var scale = 1f
            var zoomFired = false
            var multiTouch = false
            var swiping = false
            var dx = 0f
            var dy = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) multiTouch = true
                if (multiTouch) {
                    if (pressed >= 2) {
                        scale *= event.calculateZoom()
                        if (!zoomFired && scale > ZOOM_IN_THRESHOLD) {
                            zoomFired = true
                            zoomIn()
                        } else if (!zoomFired && scale < ZOOM_OUT_THRESHOLD) {
                            zoomFired = true
                            zoomOut()
                        }
                    }
                    event.changes.forEach { it.consume() }
                } else {
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) {
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (!swiping && abs(dx) > slop && abs(dx) > 2 * abs(dy)) swiping = true
                        if (swiping) change.consume()
                        if (!change.pressed && swiping && abs(dx) > swipePx) swipe(if (dx < 0) 1 else -1)
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
