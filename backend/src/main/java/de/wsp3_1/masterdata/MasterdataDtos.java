package de.wsp3_1.masterdata;

import de.wsp3_1.shared.Weekday;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Alle Datensaetze, die das Modul {@code masterdata} nach aussen gibt oder entgegennimmt.
 * Feldnamen und Typen 1:1 wie im Vertrag {@code api-contract/openapi.yaml} und in
 * {@code frontend/src/api/types.ts}.
 *
 * <p><b>Warum liegen die Records hier und nicht in {@code masterdata/api}?</b> Die
 * ArchUnit-Regeln lassen zwei Wege nicht zu: {@code api} darf keine Entity anfassen (Regel 2) und
 * {@code domain} darf nichts aus {@code api} benutzen (Regel 3). Damit es nur eine Satzart statt
 * zweier fast gleicher Schichten gibt, stehen die Records im Modulwurzel-Paket: der Dienst in
 * {@code domain} baut sie aus den Entities, der Controller in {@code api} reicht sie durch.
 * Dieselbe Stelle nutzt {@code de.wsp3_1.shared.Weekday}. Abweichung MD-1 in docs/VERTRAG.md.</p>
 *
 * <p><b>Die {@code @Size}-Grenzen sind die Spaltenbreiten aus V1</b> ({@code teacher.name} und
 * {@code subject.name} varchar(120), {@code room.name} und {@code room_type} varchar(60),
 * {@code school_class.name} varchar(20), Kuerzel varchar(20) — dort gilt bewusst die engere Grenze 10).
 * Fehlt die Grenze, kommt ein zu langer Wert durch die Pruefung und scheitert erst an der Datenbank:
 * der Client bekaeme 500 statt 400 mit Feldliste. Wer eine Spalte in einer spaeteren Migration
 * verbreitert, zieht die Zahl hier mit.</p>
 */
public final class MasterdataDtos {

    private MasterdataDtos() {
    }

    /** Warnung in einer 200-Antwort — nie ein Fehler (T-W1.4, T-W1.5). */
    public enum WarningCode {
        /** Die Lehrkraft hat das Fach nicht in ihrer Faecherliste. */
        SUBJECT_NOT_TAUGHT,
        /** Die Summe der Wochenstunden liegt ueber dem Deputat. */
        QUOTA_EXCEEDED
    }

    public record WarningDto(WarningCode code, String message, Long teacherId) {
    }

    // ------------------------------------------------------------------ Lehrkraefte

    public record TeacherDto(Long id, String name, String shortName, int weeklyHoursQuota,
                             List<Long> subjectIds, List<Long> preferredTimeslotIds, List<Long> blockedTimeslotIds,
                             boolean active, int plannedHours, List<WarningDto> warnings) {
    }

    public record TeacherInput(
            @NotBlank(message = "darf nicht leer sein") @Size(max = 120, message = "hoechstens 120 Zeichen") String name,
            @NotBlank(message = "darf nicht leer sein") @Size(max = 10, message = "hoechstens 10 Zeichen") String shortName,
            @NotNull(message = "darf nicht leer sein") @Min(value = 0, message = "muss mindestens 0 sein") Integer weeklyHoursQuota,
            @NotNull(message = "darf nicht leer sein") List<Long> subjectIds,
            @NotNull(message = "darf nicht leer sein") List<Long> preferredTimeslotIds,
            @NotNull(message = "darf nicht leer sein") List<Long> blockedTimeslotIds,
            @NotNull(message = "darf nicht leer sein") Boolean active) {
    }

    // ------------------------------------------------------------------ Faecher

    public record SubjectDto(Long id, String name, String shortName, String requiredRoomType, String color) {
    }

    public record SubjectInput(
            @NotBlank(message = "darf nicht leer sein") @Size(max = 120, message = "hoechstens 120 Zeichen") String name,
            @NotBlank(message = "darf nicht leer sein") @Size(max = 10, message = "hoechstens 10 Zeichen") String shortName,
            @Size(max = 60, message = "hoechstens 60 Zeichen") String requiredRoomType,
            @NotBlank(message = "darf nicht leer sein")
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Farbe im Format #RRGGBB") String color) {
    }

    // ------------------------------------------------------------------ Raeume

    public record RoomDto(Long id, String name, String roomType, boolean active) {
    }

    public record RoomInput(
            @NotBlank(message = "darf nicht leer sein") @Size(max = 60, message = "hoechstens 60 Zeichen") String name,
            @NotBlank(message = "darf nicht leer sein") @Size(max = 60, message = "hoechstens 60 Zeichen") String roomType,
            @NotNull(message = "darf nicht leer sein") Boolean active) {
    }

    // ------------------------------------------------------------------ Klassen

    public record ClassSubjectDto(Long subjectId, String subjectShort, int weeklyHours,
                                  Long teacherId, String teacherShort) {
    }

    /** {@code weeklyHours} und {@code teacherId} duerfen leer ankommen — dann 400 mit Feldliste (G-8). */
    public record ClassSubjectInput(Long subjectId, Integer weeklyHours, Long teacherId) {
    }

    public record ClassDto(Long id, String name, int grade, boolean active,
                           List<ClassSubjectDto> subjects, List<WarningDto> warnings) {
    }

    public record ClassInput(
            @NotBlank(message = "darf nicht leer sein") @Size(max = 20, message = "hoechstens 20 Zeichen") String name,
            @NotNull(message = "darf nicht leer sein") @Min(value = 1, message = "muss mindestens 1 sein") Integer grade,
            @NotNull(message = "darf nicht leer sein") Boolean active) {
    }

    // ------------------------------------------------------------------ Stundenraster

    public record TimeslotDto(Long id, Weekday day, int position, String startTime, String endTime, boolean isBreak) {
    }

    /** {@code id} gesetzt = aendern, fehlend = neu anlegen; der Abgleich laeuft aber ueber (day, position). */
    public record TimeslotInput(Long id, Weekday day, Integer position,
                                String startTime, String endTime, Boolean isBreak) {
    }

    public record TimegridDto(List<TimeslotDto> slots) {
    }

    public record TimegridInput(@NotNull(message = "darf nicht leer sein") @Valid List<TimeslotInput> slots) {
    }
}
