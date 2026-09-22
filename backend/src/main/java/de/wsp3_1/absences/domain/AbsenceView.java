package de.wsp3_1.absences.domain;

import java.time.LocalDate;

/** Leseform einer Abwesenheitsmeldung fuer die API-Schicht (kein Entity, kein DTO). */
public record AbsenceView(
        Long id,
        Long teacherId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String reason,
        boolean active,
        boolean seen,
        Long eventId) {
}