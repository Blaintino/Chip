package de.blaintino.chip

import org.junit.Assert.assertEquals
import org.junit.Test

class ZaehlerTest {
    @Test
    fun zaehltHoch() {
        assertEquals(1, naechsterStand(0))
        assertEquals(42, naechsterStand(41))
    }

    @Test
    fun beschriftungIstGrammatischKorrekt() {
        assertEquals("Noch nicht getippt", beschriftung(0))
        assertEquals("1 Mal getippt", beschriftung(1))
        assertEquals("3 Mal getippt", beschriftung(3))
    }
}
