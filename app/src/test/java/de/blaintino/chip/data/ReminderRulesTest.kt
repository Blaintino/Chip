package de.blaintino.chip.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderRulesTest {
    @Test
    fun `Vorgabe ab Werk ist 1 Tag, 1 Stunde und 15 Minuten`() {
        assertEquals(listOf("1 Tag vorher", "1 Std. vorher", "15 Min. vorher"), ReminderRules.FACTORY.map(ReminderRules::label))
    }

    @Test
    fun `Beschriftungen`() {
        assertEquals("Zum Beginn", ReminderRules.label(0))
        assertEquals("2 Tage vorher", ReminderRules.label(2880))
        assertEquals("1 Woche vorher", ReminderRules.label(10080))
        assertEquals("3 Std. vorher", ReminderRules.label(180))
        assertEquals("1 Std. 30 Min. vorher", ReminderRules.label(90))
    }

    @Test
    fun `gespeichert ohne Doppelte, früheste zuerst, höchstens drei`() {
        assertEquals("1440,60,15", ReminderRules.encode(listOf(15, 1440, 60, 60)))
        assertEquals(listOf(1440, 60, 15), ReminderRules.decode("15,60,1440,5"))
        assertEquals(emptyList<Int>(), ReminderRules.decode(""))
    }
}
