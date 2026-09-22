package de.wsp3_1.absences.api;

import de.wsp3_1.absences.persistence.Absence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Feldnamen 1:1 wie im Vertrag ({@code AbsencesDto}, {@code CreateAbsenceDto}).
 * {@code read} heisst so (nicht {@code isRead}) — so steht es in {@code openapi.yaml}.
 */
public final class AbsenceDtos {

    private AbsenceDtos() {
    }

    public record AbsenceDto(
            Long id,
            Long teacherId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String reason,
            boolean active,
            boolean seen,
            Long eventId) {

        public static AbsenceDto of(Absence a) {
            return new AbsenceDto(a.getId(), a.getTeacherId(), a.getDateFrom(), a.getDateTo(),
                    a.getReason(), a.isActive(), a.isSeen(), a.getEventId());
        }
    }

    /**
     * {@code active} ist optional: fehlt es, gilt die Meldung als aktiv. Beide Daten sind Pflicht —
     * ohne Zeitraum ergibt eine Abwesenheit keinen Sinn (kleine Verschaerfung gegenueber dem leeren
     * {@code required: []} in openapi.yaml).
     */
    public record CreateAbsenceDto(
            @NotNull(message = "Bitte ein Von-Datum angeben.") LocalDate dateFrom,
            @NotNull(message = "Bitte ein Bis-Datum angeben.") LocalDate dateTo,
            @NotBlank(message = "Bitte einen Grund angeben.") String reason,
            Boolean active) {
    }
}