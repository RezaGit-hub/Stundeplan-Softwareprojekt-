package de.wsp3_1.stundenplan.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Datumshelfer ohne Zeitzonen: die App rechnet nur mit Kalendertagen (yyyy-MM-dd). */
object PlanDates {

    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun parse(iso: String): LocalDate = LocalDate.parse(iso)

    /** Wochentag-Code des Vertrags (Weekday: MO..SA; SU nur intern fuer Sonntage). */
    fun weekdayCode(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }

    /** Deutsche Kurzform zum Wochentag-Code, z. B. fuer "verlegt -> Mi 1.". */
    fun shortGerman(code: String): String = when (code) {
        "MO" -> "Mo"
        "TU" -> "Di"
        "WE" -> "Mi"
        "TH" -> "Do"
        "FR" -> "Fr"
        "SA" -> "Sa"
        "SU" -> "So"
        else -> code
    }

    fun shortGerman(date: LocalDate): String = shortGerman(weekdayCode(date))
}
