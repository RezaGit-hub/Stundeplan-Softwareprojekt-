package de.wsp3_1.absences.api;

import de.wsp3_1.absences.persistence.Absence;
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

        public static AbsenceDto of(Absence a) {
            return new AbsenceDto(a.getId(), a.getTeacherId(), a.getDateFrom(), a.getDateTo(),
                    a.getReason(), a.isActive(), a.isSeen(), a.getEventId());
        }
    }

    /**
     * Antwort von {@code POST /api/absences/&#123;id&#125;/convert}: die aktualisierte Meldung
     * ({@code seen=true}, {@code eventId} gesetzt) <b>und</b> das neu angelegte Ereignis. Der Client
     * soll beides sehen, ohne zwei Anfragen schicken zu muessen.
     */
    public record ConvertedAbsenceDto(AbsenceDto absence, EventView event) {
    }

    public record CreateAbsenceDto(
            @NotNull(message = "Bitte ein Von-Datum angeben.") LocalDate dateFrom,
            @NotNull(message = "Bitte ein Bis-Datum angeben.") LocalDate dateTo,
            @NotBlank(message = "Bitte einen Grund angeben.")
            @Size(max = 255, message = "höchstens 255 Zeichen") String reason,
            Boolean active) {
    }
}