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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.calculateCentroid
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

/** Ab dieser Fingerbewegung (Faktor) ist eine Zwei-Finger-Geste als Aufziehen bzw. Zusammenschieben erkannt. */
const val PINCH_START_IN = 1.03f
const val PINCH_START_OUT = 0.97f

/**
 * Gesten der Kalenderansichten mit laufender Rückmeldung (Wunsch des Nutzers, Mockup „Chip Gesten-Mockup“):
 * - ein Finger waagerecht: [onDrag] mit der Verschiebung je Bewegung, am Ende [onDragEnd] mit der
 *   Geschwindigkeit (px/s, negativ = nach links); die Ansicht folgt also dem Finger,
 * - zwei Finger: [onPinch] mit dem Gesamtfaktor seit Beginn und dem Mittelpunkt, am Ende [onPinchEnd].
 *
 * Läuft im Initial-Durchgang. Ereignisse werden erst verbraucht, sobald die Geste erkannt ist (zwei Finger
 * bzw. klar waagerechte Bewegung über die Berührungsschwelle) – senkrechtes Scrollen und Tippen kommen sonst
 * unverändert bei den Ansichten an; ein erkanntes Wischen bricht das Tippen darunter ab. Nach langem
 * Stillhalten gehört die Geste dem Zeitwähler der Ansicht.
 */
fun Modifier.calendarGestures(
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onPinch: (Float, Offset) -> Unit,
    onPinchEnd: () -> Unit,
): Modifier = composed {
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val pinch by rememberUpdatedState(onPinch)
    val pinchEnd by rememberUpdatedState(onPinchEnd)
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var scale = 1f
            var multiTouch = false
            var swiping = false
            var longPressed = false
            var dx = 0f
            var dy = 0f
            var ended = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 && !multiTouch) {
                    multiTouch = true
                    // Ein angefangenes Wischen zurückfedern lassen.
                    if (swiping) dragEnd(0f)
                    swiping = false
                }
                if (multiTouch) {
                    if (pressed >= 2) {
                        scale *= event.calculateZoom()
                        pinch(scale, event.calculateCentroid(useCurrent = true))
                    }
                    event.changes.forEach { it.consume() }
                } else {
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null) {
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (!swiping && !longPressed && change.uptimeMillis - down.uptimeMillis > longPressMs) longPressed = true
                        if (!swiping && !longPressed && abs(dx) > slop && abs(dx) > 2 * abs(dy)) {
                            swiping = true
                            drag(dx) // bisherige Strecke nachholen, damit die Ansicht unter dem Finger bleibt
                        } else if (swiping) {
                            drag(delta.x)
                        }
                        if (swiping) change.consume()
                        if (!change.pressed && swiping) {
                            dragEnd(tracker.calculateVelocity().x)
                            ended = true
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (multiTouch) pinchEnd()
            else if (swiping && !ended) dragEnd(0f)
        }
    }
}
