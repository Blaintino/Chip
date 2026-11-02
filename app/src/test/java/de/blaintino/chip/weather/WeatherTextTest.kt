package de.blaintino.chip.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherTextTest {
    private val date = LocalDate.of(2026, 11, 2)

    private fun day(vararg rain: Int, max: Double? = 13.6, min: Double? = 4.4) =
        WeatherDay(date, max, min, rain.mapIndexed { h, p -> date.atTime(h, 0) to p })

    @Test
    fun `Temperaturen gerundet, ohne Werte keine Zeile`() {
        assertEquals("↑ 14°  ↓ 4°", WeatherText.temperatures(day()))
        assertNull(WeatherText.temperatures(day(max = null)))
    }

    @Test
    fun `aktuelle Temperatur aus der laufenden Stunde`() {
        val d = day().copy(hourlyTemp = listOf(date.atTime(10, 0) to 9.4, date.atTime(11, 0) to 10.6))
        assertEquals("11°", WeatherText.current(d, LocalDateTime.of(2026, 11, 2, 11, 40)))
        assertNull(WeatherText.current(d, LocalDateTime.of(2026, 11, 2, 12, 5)))
    }

    @Test
    fun `Regen ab der ersten Stunde über der Schwelle, Prozent = Höchstwert danach`() {
        val rain = IntArray(24) { 10 }.also { it[15] = 45; it[17] = 80; it[9] = 90 }
        // 9 Uhr ist schon vorbei (jetzt 11:20), zählt nicht mehr.
        assertEquals("☂ ab 15 Uhr (80 %)", WeatherText.rainHint(day(*rain), LocalDateTime.of(2026, 11, 2, 11, 20)))
    }

    @Test
    fun `Regen in der laufenden Stunde heißt jetzt`() {
        val rain = IntArray(24) { 0 }.also { it[11] = 60 }
        assertEquals("☂ jetzt (60 %)", WeatherText.rainHint(day(*rain), LocalDateTime.of(2026, 11, 2, 11, 50)))
    }

    @Test
    fun `kein Hinweis unter der Schwelle oder nur in vergangenen Stunden`() {
        assertNull(WeatherText.rainHint(day(*IntArray(24) { 39 }), LocalDateTime.of(2026, 11, 2, 8, 0)))
        val rain = IntArray(24) { 0 }.also { it[7] = 90 }
        assertNull(WeatherText.rainHint(day(*rain), LocalDateTime.of(2026, 11, 2, 8, 0)))
    }
}
