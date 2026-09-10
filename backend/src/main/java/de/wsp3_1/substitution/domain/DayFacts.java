package de.wsp3_1.substitution.domain;

import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.shared.Weekday;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Alles, was das Aufloesen <b>eines</b> Kalendertags braucht — reine Records, keine Datenbank.
 *
 * <p>Damit ist {@link WeekResolver#resolveDay(DayFacts)} eine Funktion: Tatsachen hinein, Eintraege
 * heraus. Genau wie beim {@code ConflictChecker} in {@code planning} laesst sich die Fachlogik so ohne
 * Spring und ohne PostgreSQL pruefen (Teststrategie Ebene 1, {@code WeekResolverTest}); der
 * {@link WeekResolver} als Dienst tut nichts weiter, als diese Tatsachen zusammenzutragen.</p>
 *
 * @param date          der Kalendertag
 * @param weekday       sein Wochentag — der Tag des Regelplans, der auf ihn faellt
 * @param lessons       die Stunden des Plans, der an diesem Tag gilt ({@code PlanReader.plannedAt})
 * @param events        die Ereignisse, deren Zeitraum diesen Tag enthaelt, ohne die Zusatzstunden
 * @param exceptions    die Ausnahmen dieses Tages <b>und</b> die Verlegungen, deren Ziel er ist
 * @param eventReasons  der Anlass je Ereignis, ueber alle Ereignisse — eine Ausnahme darf einen Anlass
 *                      nennen, dessen Ereignis an diesem Tag gar nicht wirkt (Zusatzstunde, Verlegung)
 * @param movedOrigins  Ursprungsstunden verlegter Stunden, nach Stundenkennung — eine verlegte Stunde
 *                      steht im Plan ihres <i>urspruenglichen</i> Tages, nicht in dem des Zieltages
 * @param slots         alle Zeitfenster des Rasters
 * @param teacherShorts Kuerzel je Lehrkraft (fuer Vertretungen und Zusatzstunden)
 * @param roomNames     Bezeichnung je Raum
 * @param classNames    Bezeichnung je Klasse (fuer Zusatzstunden)
 * @param subjects      Kuerzel und Farbe je Fach (fuer Zusatzstunden)
 */
public record DayFacts(LocalDate date,
                       Weekday weekday,
                       List<LessonView> lessons,
                       List<EventFact> events,
                       List<ExceptionFact> exceptions,
                       Map<Long, String> eventReasons,
                       Map<Long, LessonView> movedOrigins,
                       Map<Long, SlotFact> slots,
                       Map<Long, String> teacherShorts,
                       Map<Long, String> roomNames,
                       Map<Long, String> classNames,
                       Map<Long, SubjectFact> subjects) {

    /** Ein Aenderungsereignis, so weit die Aufloesung es braucht. */
    public record EventFact(Long id, EventKind kind, Long teacherId, Long roomId, String reason,
                            LocalDate dateFrom, LocalDate dateTo) {
    }

    /** Eine datierte Ausnahme, so weit die Aufloesung sie braucht. */
    public record ExceptionFact(Long id, Long lessonId, LocalDate date, ExceptionKind kind,
                                Long substituteTeacherId, Long substituteRoomId,
                                LocalDate newDate, Long newTimeslotId,
                                Long addedClassId, Long addedSubjectId, List<Long> eventIds) {
    }

    /**
     * Ein Zeitfenster.
     *
     * @param lessonNumber die angezeigte Stundennummer ohne Pausen (1..n je Tag), {@code null} in einer
     *                     Pause — sie wird gezaehlt und nie gespeichert (wie in
     *                     {@code frontend/src/lib/timegrid.ts})
     */
    public record SlotFact(Long id, Weekday day, int position, boolean isBreak, Integer lessonNumber) {
    }

    public record SubjectFact(Long id, String shortName, String color) {
    }
}
