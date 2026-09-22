package de.wsp3_1.absences.domain;

import java.time.LocalDate;

/** Eingabe fuer Anlegen und Aendern einer Abwesenheitsmeldung. */
public record AbsenceInput(LocalDate dateFrom, LocalDate dateTo, String reason, Boolean active) {
}