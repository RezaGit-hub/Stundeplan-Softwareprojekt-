package de.wsp3_1.transfer.api;

import de.wsp3_1.masterdata.MasterdataDtos.ClassDto;
import de.wsp3_1.masterdata.MasterdataDtos.RoomDto;
import de.wsp3_1.masterdata.MasterdataDtos.SubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimeslotDto;
import de.wsp3_1.rules.api.RuleDtos.RuleDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

/**
 * Die beiden Dokumente der Sicherung (Format-Version 1). Feldgleich zu den Schemata
 * {@code MasterDataExport}, {@code MasterDataImport} und {@code MasterDataImportResult} in
 * {@code api-contract/openapi.yaml}.
 *
 * <p>Der Import nimmt denselben Aufbau entgegen wie der Export — ein exportiertes Dokument ist
 * genau das, was der Import erwartet. Die Kennungen sind innerhalb der Datei selbstkonsistent;
 * der Import bildet sie auf die neu vergebenen Kennungen ab ({@code IdMap}).</p>
 */
public final class TransferDtos {

    private TransferDtos() {
    }

    /**
     * Alle Grunddaten der Schule als ein Dokument.
     *
     * <p>{@code plannedHours} und {@code warnings} in den enthaltenen DTOs sind abgeleitet
     * (berechnet aus den Klassenzuordnungen) und dienen nur der Anzeige im exportierenden
     * System; der Import ignoriert sie.</p>
     */
    public record MasterDataExport(
            @NotBlank String version,
            Instant exportedAt,
            @NotNull @Valid List<SubjectDto> subjects,
            @NotNull @Valid List<TeacherDto> teachers,
            @NotNull @Valid List<RoomDto> rooms,
            @NotNull @Valid List<ClassDto> classes,
            @NotNull @Valid List<TimeslotDto> timeslots,
            @NotNull @Valid List<RuleDto> rules,
            @NotNull @Valid List<HolidayDto> holidays) {
    }

    /**
     * Import-Auftrag.
     *
     * <p>{@code version} ist die Format-Version; unbekannte Werte werden mit 400 abgelehnt,
     * statt zu raten, wie die Datei gelesen werden soll.</p>
     *
     * <p>{@code replace} loescht den vorhandenen Bestand vor dem Einspielen; fehlt es und sind
     * schon Grunddaten da, antwortet der Controller 409 {@code data-exists} (genau wie
     * {@code POST /api/sample-data}).</p>
     */
    public record MasterDataImport(
            @NotBlank
            @Pattern(regexp = "1", message = "wird nicht unterstützt (erwartet 1)")
            String version,
            Instant exportedAt,
            @NotNull @Valid List<SubjectDto> subjects,
            @NotNull @Valid List<TeacherDto> teachers,
            @NotNull @Valid List<RoomDto> rooms,
            @NotNull @Valid List<ClassDto> classes,
            @NotNull @Valid List<TimeslotDto> timeslots,
            @NotNull @Valid List<RuleDto> rules,
            @NotNull @Valid List<HolidayDto> holidays,
            @NotNull(message = "Bitte angeben, ob vorhandene Daten ersetzt werden.")
            Boolean replace) {
    }

    /** Zaehlung dessen, was der Import neu angelegt hat. */
    public record MasterDataImportResult(
            int subjects, int teachers, int rooms, int classes,
            int timeslots, int rules, int holidays,
            List<String> warnings) {
    }
}