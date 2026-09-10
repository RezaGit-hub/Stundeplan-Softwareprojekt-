package de.wsp3_1.stundenplan.ui

import de.wsp3_1.stundenplan.data.DayDto
import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.EntryKind
import de.wsp3_1.stundenplan.data.PlanDates
import de.wsp3_1.stundenplan.data.TimegridDto
import de.wsp3_1.stundenplan.data.TimeslotDto
import de.wsp3_1.stundenplan.data.WeekDto
import java.time.LocalDate

/**
 * Abbildungen von Woche und Stundenraster auf das, was die Screens zeigen:
 * Woche -> Tag, (Tag, Position) -> Uhrzeit, Positionen der Wochenmatrix, Zaehler "Heute geaendert".
 * Reines Kotlin, ohne Compose - deshalb auf der JVM testbar.
 */
object WeekView {

    fun dayFor(week: WeekDto, date: LocalDate): DayDto? {
        val iso = date.toString()
        return week.days.firstOrNull { it.date == iso }
    }

    fun slotFor(timegrid: TimegridDto?, dayCode: String, position: Int): TimeslotDto? =
        timegrid?.slots?.firstOrNull { it.day == dayCode && it.position == position }

    /** "08:00–08:45" oder null, wenn das Raster (noch) nicht vorliegt. */
    fun timeText(timegrid: TimegridDto?, entry: EntryDto): String? =
        slotFor(timegrid, entry.day, entry.position)?.let { timeText(it) }

    fun timeText(slot: TimeslotDto): String = "${slot.startTime}–${slot.endTime}"

    /** Zeilen der Wochenmatrix: alle Positionen des Rasters an den Tagen der Woche, plus Positionen von Eintraegen. */
    fun positions(week: WeekDto, timegrid: TimegridDto?): List<Int> {
        val dayCodes = week.days.map { PlanDates.weekdayCode(PlanDates.parse(it.date)) }.toSet()
        val fromGrid = timegrid?.slots.orEmpty().filter { it.day in dayCodes }.map { it.position }
        val fromEntries = week.days.flatMap { day -> day.entries.map { it.position } }
        return (fromGrid + fromEntries).distinct().sorted()
    }

    /** true, wenn das Raster diese Position an allen Tagen als Pause fuehrt. */
    fun isBreak(timegrid: TimegridDto?, position: Int): Boolean {
        val slots = timegrid?.slots.orEmpty().filter { it.position == position }
        return slots.isNotEmpty() && slots.all { it.isBreak }
    }

    /**
     * true, wenn das Raster **an diesem Tag** eine Pause fuehrt. Der Vertrag erlaubt je Wochentag
     * eigene Zeitfenster (`PUT /api/timegrid` legt sie nach Tag und Position ab), also entscheidet
     * die Zelle und nicht die Zeile. Kennt das Raster den Tag nicht, gilt die Zeile (alte Regel).
     */
    fun isBreak(timegrid: TimegridDto?, dayCode: String, position: Int): Boolean {
        val slot = slotFor(timegrid, dayCode, position)
        return if (slot != null) slot.isBreak else isBreak(timegrid, position)
    }

    /**
     * Uhrzeit fuer eine Zeile der Wochenmatrix (Montag, sonst erster Tag mit dieser Position).
     * Weichen die Zeiten je Tag ab, steht in der Zeile die des Montags - die Tagesansicht und das
     * Stundendetail zeigen immer die Zeit des jeweiligen Tages.
     */
    fun rowTime(timegrid: TimegridDto?, position: Int): String? {
        val slots = timegrid?.slots.orEmpty().filter { it.position == position }
        val slot = slots.firstOrNull { it.day == "MO" } ?: slots.firstOrNull() ?: return null
        return timeText(slot)
    }

    fun entriesAt(day: DayDto, position: Int): List<EntryDto> =
        day.entries.filter { it.position == position }

    /** Eintraege mit date == heute und kind != REGULAR (Soll-Kriterium "Heute geaendert"). */
    fun changedToday(week: WeekDto, today: LocalDate): Int {
        val day = dayFor(week, today) ?: return 0
        return day.entries.count { it.kind != EntryKind.REGULAR }
    }

    /** Eintraege eines Tages in Rasterreihenfolge. */
    fun sortedEntries(day: DayDto): List<EntryDto> = day.entries.sortedBy { it.position }
}
