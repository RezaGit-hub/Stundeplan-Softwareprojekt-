package de.wsp3_1.substitution.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Die Bausteine der deutschen Verlaufs- und Hinweistexte der Vertretung. Bisher trugen
 * {@code ExceptionService} und {@code ChangeEventService} je ein eigenes {@code GERMAN_DATE} —
 * zwei Stellen fuer dasselbe Datumsformat.
 */
final class SubstitutionText {

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private SubstitutionText() {
    }

    /** „21.09.2026" — das Datumsformat der Verlaufseintraege und Meldungen. */
    static String date(LocalDate date) {
        return date.format(GERMAN_DATE);
    }
}
