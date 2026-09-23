package de.wsp3_1.substitution.api;

import de.wsp3_1.planning.api.PlanningDtos.ConflictDto;
import de.wsp3_1.shared.Weekday;
import de.wsp3_1.substitution.domain.EntryKind;
import de.wsp3_1.substitution.domain.EventKind;
import de.wsp3_1.substitution.domain.ExceptionKind;
import de.wsp3_1.substitution.domain.HolidayKind;
import de.wsp3_1.substitution.domain.SubstitutionViews.AffectedView;
import de.wsp3_1.substitution.domain.SubstitutionViews.EventView;
import de.wsp3_1.substitution.domain.SubstitutionViews.ExceptionView;
import de.wsp3_1.substitution.domain.SubstitutionViews.ExtraLessonView;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayPresetResultView;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayPresetYearsView;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayView;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewEvent;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewException;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewHoliday;
import de.wsp3_1.substitution.domain.SubstitutionViews.OptionsView;
import de.wsp3_1.substitution.domain.SubstitutionViews.PendingRowView;
import de.wsp3_1.substitution.domain.SubstitutionViews.RoomOptionView;
import de.wsp3_1.substitution.domain.SubstitutionViews.TeacherOptionView;
import de.wsp3_1.substitution.domain.WeekViews.DayView;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import de.wsp3_1.substitution.domain.WeekViews.OriginalView;
import de.wsp3_1.substitution.domain.WeekViews.WeekView;
import de.wsp3_1.substitution.domain.SubstitutionRequestStatus;
import de.wsp3_1.substitution.domain.SubstitutionViews.SubstitutionRequestView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Die Datensaetze dieses Moduls — Feldnamen, Reihenfolge und Typen 1:1 wie die gleichnamigen Schemata
 * in {@code api-contract/openapi.yaml} und in {@code frontend/src/api/types.ts}.
 *
 * <p>Die Dienste geben Records aus {@code substitution/domain} zurueck; abgebildet wird hier, neben
 * dem Controller (VERTRAG Abschnitt 6, „DTO-Regeln"). Kein MapStruct.</p>
 */
public final class SubstitutionDtos {

    private SubstitutionDtos() {
    }

    // ------------------------------------------------------------------ Wochensichten (W-4)

    public record EntryOriginal(Long teacherId, String teacherShort, Long roomId, String roomShort,
                                LocalDate date, Integer position) {

        static EntryOriginal of(OriginalView view) {
            return view == null ? null : new EntryOriginal(view.teacherId(), view.teacherShort(),
                    view.roomId(), view.roomShort(), view.date(), view.position());
        }
    }

    public record EntryDto(LocalDate date, Long timeslotId, Weekday day, Integer position, Long lessonId,
                           Long classId, String classShort, String subjectShort, String subjectColor,
                           Long teacherId, String teacherShort, Long roomId, String roomShort,
                           EntryKind kind, EntryOriginal original, List<String> reasons, Long exceptionId) {

        static EntryDto of(EntryView view) {
            // subjectId aus EntryView steht bewusst nicht im DTO: der Vertrag kennt am Wocheneintrag
            // nur Kuerzel und Farbe des Fachs.
            return new EntryDto(view.date(), view.timeslotId(), view.day(), view.position(), view.lessonId(),
                    view.classId(), view.classShort(), view.subjectShort(), view.subjectColor(),
                    view.teacherId(), view.teacherShort(), view.roomId(), view.roomShort(),
                    view.kind(), EntryOriginal.of(view.original()), view.reasons(), view.exceptionId());
        }
    }

    public record DayDto(LocalDate date, boolean schoolDay, List<EntryDto> entries) {

        static DayDto of(DayView view) {
            return new DayDto(view.date(), view.schoolDay(),
                    view.entries().stream().map(EntryDto::of).toList());
        }
    }

    public record WeekDto(LocalDate monday, List<DayDto> days) {

        public static WeekDto of(WeekView view) {
            return new WeekDto(view.monday(), view.days().stream().map(DayDto::of).toList());
        }
    }

    // ------------------------------------------------------------------ Aenderungsereignisse (W-5)

    public record AffectedDto(LocalDate date, Long lessonId, String classShort, Integer position) {

        static AffectedDto of(AffectedView view) {
            return new AffectedDto(view.date(), view.lessonId(), view.classShort(), view.position());
        }
    }

    /** Angaben einer Zusatzstunde — dasselbe Schema in Eingabe und Antwort ({@code ExtraLessonInput}). */
    public record ExtraLessonDto(LocalDate date, Long timeslotId, Long classId, Long subjectId,
                                 Long teacherId, Long roomId) {

        static ExtraLessonDto of(ExtraLessonView view) {
            return view == null ? null : new ExtraLessonDto(view.date(), view.timeslotId(),
                    view.classId(), view.subjectId(), view.teacherId(), view.roomId());
        }

        ExtraLessonView toView() {
            return new ExtraLessonView(date, timeslotId, classId, subjectId, teacherId, roomId);
        }
    }

    public record EventDto(Long id, EventKind kind, Long teacherId, Long roomId, LocalDate dateFrom,
                           LocalDate dateTo, String reason, Instant createdAt, String createdBy,
                           List<AffectedDto> affected, String hint, ExtraLessonDto extra) {

        public static EventDto of(EventView view) {
            return new EventDto(view.id(), view.kind(), view.teacherId(), view.roomId(),
                    view.dateFrom(), view.dateTo(), view.reason(), view.createdAt(), view.createdBy(),
                    view.affected().stream().map(AffectedDto::of).toList(), view.hint(),
                    ExtraLessonDto.of(view.extra()));
        }
    }

    /**
     * Eingabe von {@code POST /api/events}. Was je Art zusaetzlich Pflicht ist — {@code teacherId},
     * {@code roomId} oder {@code extra} —, prueft der Dienst: die Bean-Validation kennt die Art nicht.
     */
    public record EventInput(@NotNull(message = "darf nicht leer sein") EventKind kind,
                             Long teacherId,
                             Long roomId,
                             @NotNull(message = "darf nicht leer sein") LocalDate dateFrom,
                             @NotNull(message = "darf nicht leer sein") LocalDate dateTo,
                             @NotBlank(message = "darf nicht leer sein")
                             @Size(max = 255, message = "höchstens 255 Zeichen") String reason,
                             ExtraLessonDto extra) {

        NewEvent toView() {
            return new NewEvent(kind, teacherId, roomId, dateFrom, dateTo, reason,
                    extra == null ? null : extra.toView());
        }
    }

    // ------------------------------------------------------------------ Vertretung (W-6)

    public record PendingRow(LocalDate date, Long lessonId, String classShort, String subjectShort,
                             Integer position, String teacherShort, String roomShort,
                             List<String> reasons, List<Long> eventIds) {

        public static PendingRow of(PendingRowView view) {
            return new PendingRow(view.date(), view.lessonId(), view.classShort(), view.subjectShort(),
                    view.position(), view.teacherShort(), view.roomShort(), view.reasons(),
                    view.eventIds());
        }
    }

    public record TeacherOption(Long id, String shortName, String name, boolean teachesSubject) {

        static TeacherOption of(TeacherOptionView view) {
            return new TeacherOption(view.id(), view.shortName(), view.name(), view.teachesSubject());
        }
    }

    public record RoomOption(Long id, String name, String roomType, boolean matchesRequirement) {

        static RoomOption of(RoomOptionView view) {
            return new RoomOption(view.id(), view.name(), view.roomType(), view.matchesRequirement());
        }
    }

    public record OptionsDto(List<TeacherOption> teachers, List<RoomOption> rooms) {

        public static OptionsDto of(OptionsView view) {
            return new OptionsDto(view.teachers().stream().map(TeacherOption::of).toList(),
                    view.rooms().stream().map(RoomOption::of).toList());
        }
    }

    public record ExceptionDto(Long id, LocalDate date, Long lessonId, ExceptionKind kind,
                               Long substituteTeacherId, Long substituteRoomId, LocalDate newDate,
                               Long newTimeslotId, List<Long> eventIds, List<ConflictDto> conflicts) {

        public static ExceptionDto of(ExceptionView view) {
            return new ExceptionDto(view.id(), view.date(), view.lessonId(), view.kind(),
                    view.substituteTeacherId(), view.substituteRoomId(), view.newDate(),
                    view.newTimeslotId(), view.eventIds(),
                    view.conflicts().stream().map(ConflictDto::of).toList());
        }
    }

    /**
     * Eingabe von {@code POST /api/exceptions}. Was je Art zusaetzlich Pflicht ist — Lehrkraft oder
     * Raum bei {@code SUBSTITUTION}, Ziel bei {@code MOVE} —, prueft der Dienst.
     */
    public record ExceptionInput(@NotNull(message = "darf nicht leer sein") LocalDate date,
                                 @NotNull(message = "darf nicht leer sein") Long lessonId,
                                 @NotNull(message = "darf nicht leer sein") ExceptionKind kind,
                                 Long substituteTeacherId,
                                 Long substituteRoomId,
                                 LocalDate newDate,
                                 Long newTimeslotId,
                                 List<Long> eventIds) {

        NewException toView() {
            return new NewException(date, lessonId, kind, substituteTeacherId, substituteRoomId,
                    newDate, newTimeslotId, eventIds);
        }
    }

    // ------------------------------------------------------------------ Ferien (Kann, E-13)

    public record HolidayDto(Long id, String name, LocalDate dateFrom, LocalDate dateTo, HolidayKind kind) {

        public static HolidayDto of(HolidayView view) {
            return new HolidayDto(view.id(), view.name(), view.dateFrom(), view.dateTo(), view.kind());
        }
    }

    public record HolidayInput(@NotBlank(message = "darf nicht leer sein")
                               @Size(max = 120, message = "höchstens 120 Zeichen") String name,
                               @NotNull(message = "darf nicht leer sein") LocalDate dateFrom,
                               @NotNull(message = "darf nicht leer sein") LocalDate dateTo,
                               @NotNull(message = "darf nicht leer sein") HolidayKind kind) {

        NewHoliday toView() {
            return new NewHoliday(name, dateFrom, dateTo, kind);
        }
    }

    // ------------------------------------------------------------------ Vorlage je Bundesland (E-51)

    /** Antwort von {@code GET /api/holidays/preset-years}. */
    public record HolidayPresetYearsDto(String state, List<String> schoolYears) {

        public static HolidayPresetYearsDto of(HolidayPresetYearsView view) {
            return new HolidayPresetYearsDto(view.state(), view.schoolYears());
        }
    }

    /** Antwort von {@code POST /api/holidays/preset}. */
    public record HolidayPresetResultDto(int added, int skipped, String state, String schoolYear) {

        public static HolidayPresetResultDto of(HolidayPresetResultView view) {
            return new HolidayPresetResultDto(view.added(), view.skipped(), view.state(), view.schoolYear());
        }
    }

    /** Eingabe von {@code POST /api/holidays/preset}: das Schuljahr in der Schreibweise „2026/27". */
    public record HolidayPresetInput(@NotBlank(message = "darf nicht leer sein")
                                     @Pattern(regexp = "\\d{4}/\\d{2}", message = "Schreibweise 2026/27")
                                     String schoolYear) {
    }

    // ------------------------------------------------------------------ Vertretungsanfragen (V14/V15)

    /**
     * Antwort: die Zeile aus der Datenbank plus die Anzeigefelder, die der Dienst beim Lesen
     * aus der pending-Sicht des Tages setzt. Diese Felder stehen NICHT in der Datenbank — der
     * Vertrag traegt sie trotzdem, damit die App nichts nachladen muss.
     */
    public record SubstitutionRequestDto(
            Long id,
            Long teacherId,
            Long lessonId,
            LocalDate date,
            String classShort,
            String subjectShort,
            Integer position,
            String roomShort,
            String originalTeacherShort,
            SubstitutionRequestStatus status) {

        public static SubstitutionRequestDto of(SubstitutionRequestView view, PendingRowView pending) {
            return new SubstitutionRequestDto(view.id(), view.teacherId(),
                    view.lessonId(), view.date(),
                    pending.classShort(), pending.subjectShort(), pending.position(),
                    pending.roomShort(), pending.teacherShort(),
                    view.status());
        }
    }

    /**
     * Eine Zeile des POST-Arrays: welche Lehrkraft zu welcher Stunde an welchem Tag gefragt
     * wird. Der Dienst loest daraus die pending-Zeile auf (WeekResolver) und fuellt die
     * Anzeigefelder in der Antwort.
     */
    public record CreateSubstitutionRequestDto(
            @NotNull(message = "Bitte die Lehrkraft angeben.") Long teacherId,
            @NotNull(message = "Bitte die Stunde angeben.") Long lessonId,
            @NotNull(message = "Bitte den Tag angeben.") LocalDate date) {
    }

    /**
     * Eingabe der Lehrkraft-Antwort: nur {@code status} ({@code ACCEPTED} oder {@code DECLINED});
     * {@code UNKNOWN} weist der Dienst als 400 zurueck.
     */
    public record AnswerSubstitutionRequestDto(
            @NotNull(message = "Bitte die Antwort angeben.") SubstitutionRequestStatus status) {
    }
}
