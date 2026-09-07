package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.PlanDates
import de.wsp3_1.stundenplan.data.TimegridDto
import de.wsp3_1.stundenplan.ui.WeekView
import de.wsp3_1.stundenplan.ui.entryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Woche -> Tag, Zeitraster -> Uhrzeit, Positionen der Matrix, Zaehler "Heute geaendert". */
class WeekViewTest {

    @Test
    fun `Tag der Woche wird ueber das Datum gefunden`() {
        val day = WeekView.dayFor(SampleWeek.week, LocalDate.of(2026, 9, 21))
        assertEquals("2026-09-21", day?.date)
        assertEquals(5, day?.entries?.size)
        assertNull(WeekView.dayFor(SampleWeek.week, LocalDate.of(2026, 9, 26)))
    }

    @Test
    fun `Uhrzeit kommt aus dem Raster ueber Tag und Position`() {
        assertEquals("08:00–08:45", WeekView.timeText(SampleWeek.timegrid, SampleWeek.givenAway))
        assertEquals("09:55–10:40", WeekView.timeText(SampleWeek.timegrid, SampleWeek.regular))
        assertNull(WeekView.timeText(null, SampleWeek.regular))
    }

    @Test
    fun `Positionen der Matrix sind alle Rasterpositionen der Wochentage, Pause erkannt`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), WeekView.positions(SampleWeek.week, SampleWeek.timegrid))
        assertTrue(WeekView.isBreak(SampleWeek.timegrid, 3))
        assertFalse(WeekView.isBreak(SampleWeek.timegrid, 4))
        assertEquals("09:35–09:55", WeekView.rowTime(SampleWeek.timegrid, 3))
    }

    @Test
    fun `ohne Raster kommen die Positionen aus den Eintraegen`() {
        assertEquals(listOf(1, 2, 4, 5, 6), WeekView.positions(SampleWeek.week, null))
    }

    @Test
    fun `die Pause gilt je Tag, nicht je Zeile`() {
        // Mittwoch hat an Position 3 Unterricht, die uebrigen Tage Pause (der Vertrag erlaubt das).
        val gemischt = TimegridDto(
            slots = SampleWeek.timegrid.slots.map {
                if (it.day == "WE" && it.position == 3) it.copy(isBreak = false) else it
            }
        )
        assertTrue(WeekView.isBreak(gemischt, "MO", 3))
        assertFalse(WeekView.isBreak(gemischt, "WE", 3))
        assertFalse(WeekView.isBreak(gemischt, "MO", 4))
        // Zeile insgesamt: nicht mehr Pause, weil ein Tag abweicht
        assertFalse(WeekView.isBreak(gemischt, 3))
        // Tag, den das Raster nicht kennt -> Regel der Zeile
        assertTrue(WeekView.isBreak(SampleWeek.timegrid, "SA", 3))
    }

    @Test
    fun `zwei Zusatzstunden im selben Zeitfenster bekommen verschiedene Listenschluessel`() {
        val a = SampleWeek.added.copy(exceptionId = 11L)
        val b = SampleWeek.added.copy(exceptionId = 12L)
        assertNotEquals(entryKey(a), entryKey(b))
        assertEquals(entryKey(a), entryKey(a.copy()))
    }

    @Test
    fun `Heute geaendert zaehlt nur den heutigen Tag und nur Aenderungen`() {
        assertEquals(4, WeekView.changedToday(SampleWeek.week, LocalDate.of(2026, 9, 21)))
        assertEquals(2, WeekView.changedToday(SampleWeek.week, LocalDate.of(2026, 9, 23)))
        assertEquals(0, WeekView.changedToday(SampleWeek.week, LocalDate.of(2026, 9, 28)))
        assertEquals(0, WeekView.changedToday(SampleWeek.nextWeek, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `Eintraege eines Tages sind nach Position sortiert`() {
        val day = WeekView.dayFor(SampleWeek.week, LocalDate.of(2026, 9, 25))!!
        assertEquals(listOf(2, 6), WeekView.sortedEntries(day).map { it.position })
    }

    @Test
    fun `Montag der Woche und Wochentag-Codes`() {
        assertEquals(LocalDate.of(2026, 9, 21), PlanDates.mondayOf(LocalDate.of(2026, 9, 24)))
        assertEquals(LocalDate.of(2026, 9, 21), PlanDates.mondayOf(LocalDate.of(2026, 9, 21)))
        assertEquals(LocalDate.of(2026, 9, 21), PlanDates.mondayOf(LocalDate.of(2026, 9, 27)))
        assertEquals("WE", PlanDates.weekdayCode(LocalDate.of(2026, 9, 23)))
        assertEquals("Mi", PlanDates.shortGerman(LocalDate.of(2026, 9, 23)))
    }
}
