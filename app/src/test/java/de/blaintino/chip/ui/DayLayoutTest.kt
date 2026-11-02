package de.blaintino.chip.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DayLayoutTest {

    private fun cols(vararg seg: Pair<Int, Int>) = DayLayout.layout(seg.toList()).map { it.column to it.columns }

    @Test
    fun `einzelne und aufeinanderfolgende Termine nutzen die volle Breite`() {
        assertEquals(listOf(0 to 1, 0 to 1), cols(540 to 600, 600 to 660))
    }

    @Test
    fun `zwei überschneidende Termine teilen sich die Breite`() {
        assertEquals(listOf(0 to 2, 1 to 2), cols(540 to 660, 600 to 720))
    }

    @Test
    fun `freie Spalte wird wiederverwendet, Gruppe über Kette verbunden`() {
        // A 9–11, B 10–12, C 11–13: C passt in A's Spalte, alle drei bilden eine Gruppe mit 2 Spalten.
        assertEquals(listOf(0 to 2, 1 to 2, 0 to 2), cols(540 to 660, 600 to 720, 660 to 780))
    }

    @Test
    fun `nach einer Lücke beginnt eine neue Gruppe`() {
        assertEquals(listOf(0 to 2, 1 to 2, 0 to 1), cols(540 to 600, 540 to 600, 720 to 780))
    }

    @Test
    fun `kurze Termine bekommen Mindesthöhe und überschneiden sich dadurch`() {
        val slots = DayLayout.layout(listOf(600 to 600, 610 to 615))
        assertEquals(620, slots[0].endMin)
        assertEquals(2, slots[0].columns)
    }

    @Test
    fun `Reihenfolge der Eingabe bleibt erhalten`() {
        assertEquals(listOf(1, 0), DayLayout.layout(listOf(700 to 760, 540 to 600)).map { if (it.startMin == 540) 0 else 1 })
    }
}
