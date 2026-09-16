package de.wsp3_1.planning.domain;

import de.wsp3_1.shared.Weekday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Die Ergebnisse, die die Dienste dieses Moduls herausgeben. Reine Records ohne JPA — der Controller
 * bildet sie in {@code planning/api/PlanningDtos} auf die Vertragsform ab (VERTRAG Abschnitt 6,
 * „DTO-Regeln"): {@code api} darf keine Entity sehen, {@code domain} kein {@code api}.
 */
public final class PlanViews {

    private PlanViews() {
    }

    /**
     * Eine Stunde des Wochenmusters. {@code timeslotId/day/position} und {@code roomId/roomShort} sind
     * gesetzt („belegt") oder alle leer („offen") — einen dritten Zustand gibt es nicht (T-W8.1).
     */
    public record LessonView(Long id, Long classId, String classShort, Long subjectId, String subjectShort,
                             String subjectColor, Long teacherId, String teacherShort,
                             Long roomId, String roomShort, Long timeslotId, Weekday day, Integer position,
                             boolean pinned) {

        /** Die gespeicherte Stunde, fertig fuer die Antwort. */
        public static LessonView of(de.wsp3_1.planning.persistence.Lesson lesson) {
            var classSubject = lesson.getClassSubject();
            var room = lesson.getRoom();
            var slot = lesson.getTimeslot();
            return new LessonView(lesson.getId(),
                    classSubject.getSchoolClass().getId(), classSubject.getSchoolClass().getName(),
                    classSubject.getSubject().getId(), classSubject.getSubject().getShortName(),
                    classSubject.getSubject().getColor(),
                    classSubject.getTeacher().getId(), classSubject.getTeacher().getShortName(),
                    room == null ? null : room.getId(), room == null ? null : room.getName(),
                    slot == null ? null : slot.getId(), slot == null ? null : slot.getDay(),
                    slot == null ? null : slot.getPosition(),
                    lesson.isPinned());
        }
    }

    /** Fehlende Wochenstunden je Klasse und Fach — zugleich die Pflichtfaecher-Pruefung. */
    public record OpenLessonView(Long classId, String classShort, String subjectShort, int missing) {
    }

    /** Daten des letzten bzw. laufenden Solver-Laufs fuer die Fortschrittsanzeige. */
    public record SolveInfoView(Instant startedAt, Integer limitSeconds, String lastScore, String error) {
    }

    /**
      * Der aktive Regelplan. Vor der ersten Annahme: {@code id null} und leere Listen.
      *
      * <p>{@code validFrom} ist der Stichtag „Gueltig ab" (E-40); ohne gesetzten Stichtag {@code null} —
      * dann gilt der Plan ab dem Tag seiner Annahme. Ergaenzt 05.09. (Befund PFLICHTENHEFT-6): vorher
      * musste die Web-App den Beginn aus {@code acceptedAt} raten und zeigte bei einem Plan mit Stichtag
      * das falsche Datum. {@code validUntil} ist der letzte Tag einschliesslich (#115), {@code null} =
      * unbegrenzt.</p>
      */
    public record PlanView(Long id, Instant acceptedAt, LocalDate validFrom, LocalDate validUntil,
                           List<LessonView> lessons, List<ConflictView> conflicts) {

        public static PlanView empty() {
            return new PlanView(null, null, null, null, List.of(), List.of());
        }
    }

    /**
     * Eine Zeile von {@code GET /api/timetables} (#115): ein angenommener Plan mit seinem Zeitraum,
     * ohne Stunden.
     *
     * @param current gilt dieser Plan heute? Traegt hoechstens einer — in einer Luecke zwischen zwei
     *                Zeitraeumen keiner.
     */
    public record PlanSummaryView(Long id, Instant acceptedAt, LocalDate validFrom, LocalDate validUntil,
                                  boolean current) {
    }

    /**
     * Ein Entwurf mit allem, was die Oberflaeche zum Bauen braucht.
     *
     * @param name               der Name des Entwurfs (#103, V11) — unterscheidet mehrere Entwuerfe
     * @param finished           liegt der Entwurf in der Ablage (#116, V12)? Dann ist er eingefroren:
     *                           Stunden aendern, Berechnen und Reparieren sind 409 {@code draft-finished},
     *                           Annehmen, Umbenennen und Loeschen bleiben erlaubt
     * @param expiringExceptions wie viele datierte Vertretungen das Annehmen dieses Entwurfs verfallen
     *                           liesse — nur die, deren Stunde er veraendert oder streicht. Der
     *                           Annehmen-Dialog nennt damit dieselbe Zahl, die der Server hinterher
     *                           meldet, und verlangt die Bestaetigung nur, wenn wirklich etwas verloren
     *                           geht (Befund PFLICHTENHEFT-9, 05.09.).
     */
    public record DraftView(Long id, String name, boolean finished, Instant createdAt, DraftStatus status,
                            SolveInfoView solve, List<LessonView> lessons, List<ConflictView> conflicts,
                            List<OpenLessonView> openLessons, List<String> warnings,
                            long expiringExceptions) {
    }

    /**
     * Eine Zeile der Entwurfsliste ({@code GET /api/drafts}, #103): nur die Kennzahlen, keine Stunden.
     *
     * @param source        {@code COPY_ACTIVE}, wenn der Entwurf aus einem aktiven Plan kopiert wurde
     *                      ({@code based_on_id} gesetzt), sonst {@code EMPTY}
     * @param hardConflicts Zahl der harten Konflikte, beim Lesen berechnet (E-31)
     */
    public record DraftSummaryView(Long id, String name, boolean finished, Instant createdAt, DraftSource source,
                                   DraftStatus status, int hardConflicts) {
    }

    /** Antwort auf {@code POST /api/draft/accept}: der neue aktive Plan plus deutsche Hinweise. */
    public record AcceptResultView(PlanView plan, List<String> warnings) {
    }
}
