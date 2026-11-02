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
}

private const val ZOOM_IN_THRESHOLD = 1.25f
private const val ZOOM_OUT_THRESHOLD = 0.8f

/**
 * Zwei-Finger-Geste: Aufziehen ruft [onZoomIn], Zusammenschieben [onZoomOut] – höchstens einmal je
 * Geste. Läuft im Initial-Durchgang und verbraucht nur Ereignisse mit zwei oder mehr Fingern, damit
 * Scrollen und Tippen mit einem Finger unverändert bei den Ansichten ankommen.
 */
fun Modifier.pinchToZoom(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier = composed {
    val zoomIn by rememberUpdatedState(onZoomIn)
    val zoomOut by rememberUpdatedState(onZoomOut)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var scale = 1f
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    scale *= event.calculateZoom()
                    if (!fired && scale > ZOOM_IN_THRESHOLD) {
                        fired = true
                        zoomIn()
                    } else if (!fired && scale < ZOOM_OUT_THRESHOLD) {
                        fired = true
                        zoomOut()
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
