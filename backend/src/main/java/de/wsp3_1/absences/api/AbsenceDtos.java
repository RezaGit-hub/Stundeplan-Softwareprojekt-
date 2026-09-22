package de.wsp3_1.absences.api;

import de.wsp3_1.absences.domain.AbsenceView;
import de.wsp3_1.absences.domain.ConvertedAbsenceView;
import de.wsp3_1.substitution.domain.SubstitutionViews.EventView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AbsenceDtos {

    private AbsenceDtos() {
    }

    public record AbsenceDto(
            Long id, Long teacherId, LocalDate dateFrom, LocalDate dateTo,
            String reason, boolean active, boolean seen, Long eventId) {

        public static AbsenceDto of(AbsenceView v) {
            return new AbsenceDto(v.id(), v.teacherId(), v.dateFrom(), v.dateTo(),
                    v.reason(), v.active(), v.seen(), v.eventId());
        }
    }

    /**
     * Antwort von POST /api/absences/{id}/convert: aktualisierte Meldung und neues Ereignis.
     * Enthaelt {@code EventView} direkt — falls ihr ein {@code SubstitutionDtos.EventDto} habt,
     * koennt ihr das hier stattdessen nehmen (dann in {@code ConvertedAbsenceDto.of} umbauen).
     */
    public record ConvertedAbsenceDto(AbsenceDto absence, EventView event) {

        public static ConvertedAbsenceDto of(ConvertedAbsenceView v) {
            return new ConvertedAbsenceDto(AbsenceDto.of(v.absence()), v.event());
        }
    }

    public record CreateAbsenceDto(
            @NotNull(message = "Bitte ein Von-Datum angeben.") LocalDate dateFrom,
            @NotNull(message = "Bitte ein Bis-Datum angeben.") LocalDate dateTo,
            @NotBlank(message = "Bitte einen Grund angeben.")
            @Size(max = 255, message = "höchstens 255 Zeichen") String reason,
            Boolean active) {
    }
}