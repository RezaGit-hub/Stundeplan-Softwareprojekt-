package de.wsp3_1.substitution.domain;

import de.wsp3_1.planning.domain.ConflictView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Was die Dienste dieses Moduls herausgeben und hereinbekommen — reine Records ohne JPA. Der
 * Controller bildet sie in {@code substitution/api/SubstitutionDtos} auf die Vertragsform ab
 * (VERTRAG Abschnitt 6, „DTO-Regeln"): {@code api} darf keine Entity sehen, {@code domain} kein
 * {@code api}.
 */
public final class SubstitutionViews {

    private SubstitutionViews() {
    }

    // ------------------------------------------------------------------ Ereignisse (W-5)

    /** Eine Stunde, die ein Ereignis trifft. Berechnet bei jedem Lesen, nie gespeichert (E-31). */
    public record AffectedView(LocalDate date, Long lessonId, String classShort, Integer position) {
    }

    /** Die Angaben einer Zusatzstunde — Eingabe beim Ereignis {@code EXTRA_LESSON} und Teil der Antwort. */
    public record ExtraLessonView(LocalDate date, Long timeslotId, Long classId, Long subjectId,
                                  Long teacherId, Long roomId) {
    }

    /**
     * Ein Aenderungsereignis mit seinen Auswirkungen.
     *
     * @param hint deutscher Hinweis, wenn das Ereignis keine Stunde trifft oder eine Zusatzstunde anlegt
     */
    public record EventView(Long id, EventKind kind, Long teacherId, Long roomId, LocalDate dateFrom,
                            LocalDate dateTo, String reason, Instant createdAt, String createdBy,
                            List<AffectedView> affected, String hint, ExtraLessonView extra) {
    }

    /** Ein neues Ereignis, wie der Controller es dem Dienst uebergibt. */
    public record NewEvent(EventKind kind, Long teacherId, Long roomId, LocalDate dateFrom,
                           LocalDate dateTo, String reason, ExtraLessonView extra) {
    }

    // ------------------------------------------------------------------ Vertretung (W-6)

    /** Eine vertretungsbeduerftige Stunde an einem Datum („naechste offene Stunde"). */
    public record PendingRowView(LocalDate date, Long lessonId, String classShort, String subjectShort,
                                 Integer position, String teacherShort, String roomShort,
                                 List<String> reasons, List<Long> eventIds) {
    }

    /** Eine Lehrkraft, die diese Stunde uebernehmen koennte; fachgleiche stehen zuerst. */
    public record TeacherOptionView(Long id, String shortName, String name, boolean teachesSubject) {
    }

    /** Ein freier Raum; passende Raumarten stehen zuerst. */
    public record RoomOptionView(Long id, String name, String roomType, boolean matchesRequirement) {
    }

    public record OptionsView(List<TeacherOptionView> teachers, List<RoomOptionView> rooms) {
    }

    // ------------------------------------------------------------------ Ausnahmen (W-6, W-7)

    /**
     * Eine datierte Ausnahme.
     *
     * @param conflicts Folgekonflikte des Tages — Daten, kein Fehler; nur die Antwort auf {@code POST}
     *                  fuellt sie
     */
    public record ExceptionView(Long id, LocalDate date, Long lessonId, ExceptionKind kind,
                                Long substituteTeacherId, Long substituteRoomId, LocalDate newDate,
                                Long newTimeslotId, List<Long> eventIds, List<ConflictView> conflicts) {
    }

    /** Eine neue Ausnahme, wie der Controller sie dem Dienst uebergibt. */
    public record NewException(LocalDate date, Long lessonId, ExceptionKind kind,
                               Long substituteTeacherId, Long substituteRoomId, LocalDate newDate,
                               Long newTimeslotId, List<Long> eventIds) {
    }

    // ------------------------------------------------------------------ Ferien (Kann, E-13)

    public record HolidayView(Long id, String name, LocalDate dateFrom, LocalDate dateTo, HolidayKind kind) {
    }

    public record NewHoliday(String name, LocalDate dateFrom, LocalDate dateTo, HolidayKind kind) {
    }

    // ------------------------------------------------------------------ Vorlage je Bundesland (E-51)

    /**
     * Die Schuljahre, fuer die es zum eingestellten Bundesland eine Vorlage gibt
     * ({@code GET /api/holidays/preset-years}).
     */
    public record HolidayPresetYearsView(String state, List<String> schoolYears) {
    }

    /**
     * Ergebnis des Uebernehmens ({@code POST /api/holidays/preset}): {@code added} neu angelegt,
     * {@code skipped} uebersprungen, weil derselbe Zeitraum mit derselben Bezeichnung schon stand.
     */
    public record HolidayPresetResultView(int added, int skipped, String state, String schoolYear) {
    }

    // ------------------------------------------------------------------ Vertretungsanfragen (V14-15)

    /**
     * Leseform einer Vertretungsanfrage. Sie traegt die Zeile aus der Datenbank und die
     * Anzeigefelder, die der Dienst beim Lesen aus der pending-Sicht des Tages setzt. Die
     * Anzeigefelder stehen NICHT in der Datenbank; bei einem Planwechsel koennen sie fehlen —
     * dann blendet der Dienst die Anfrage aus.
     */
    public record SubstitutionRequestView(
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
    }

    /** Eine neue Anfrage, wie der Controller sie dem Dienst uebergibt. */
    public record NewSubstitutionRequest(Long teacherId, Long lessonId, LocalDate date) {
    }
}