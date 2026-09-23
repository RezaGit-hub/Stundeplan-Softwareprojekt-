package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.TimegridDto
import de.wsp3_1.stundenplan.data.TimeslotDto
import de.wsp3_1.stundenplan.ui.substitutionLessonText
import org.junit.Assert.assertEquals
import org.junit.Test

class SubstitutionLessonTest {
    private fun slot(day: String, position: Int, pause: Boolean = false) =
        TimeslotDto(position.toLong(), day, position, "08:00", "08:45", pause)

    private val grid = TimegridDto(listOf(
        slot("MO", 4), slot("MO", 2), slot("MO", 3, true), slot("MO", 1),
        slot("MO", 5, true), slot("MO", 6),
        slot("TU", 1), slot("TU", 2), slot("TU", 3), slot("TU", 4),
    ))

    @Test fun `pauses do not increase lesson number even with unsorted slots`() {
        assertEquals("1. Stunde", substitutionLessonText(grid, "2026-09-21", 1))
        assertEquals("3. Stunde", substitutionLessonText(grid, "2026-09-21", 4))
        assertEquals("4. Stunde", substitutionLessonText(grid, "2026-09-21", 6))
    }

    @Test fun `uses weekday of requested date`() {
        assertEquals("4. Stunde", substitutionLessonText(grid, "2026-09-22", 4))
    }

    @Test fun `breaks and missing information are never shown as lesson numbers`() {
        assertEquals("Pause", substitutionLessonText(grid, "2026-09-21", 3))
        assertEquals("Stunde unbekannt", substitutionLessonText(null, "2026-09-21", 4))
        assertEquals("Stunde unbekannt", substitutionLessonText(grid, "2026-09-23", 4))
        assertEquals("Stunde unbekannt", substitutionLessonText(grid, "2026-09-21", 99))
        assertEquals("Stunde unbekannt", substitutionLessonText(grid, "invalid", 4))
    }
}
