package de.wsp3_1.planning.domain;

import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.shared.Weekday;

import java.util.List;
import java.util.Map;

/**
 * Alles, was der {@link ConflictChecker} ausser den Belegungen braucht: Namen fuer die Meldungstexte,
 * Sperrzeiten, Raumarten und die schon aufgeloesten Regeln.
 *
 * <p>Bewusst reine Karten und Records statt Repositories: so ist die Pruefung eine Funktion ohne
 * Datenbank (Teststrategie Ebene 1). Zusammengebaut wird der Kontext einmal je Anfrage im
 * {@link ConflictService}.</p>
 *
 * <p>Die Regeln kommen aus {@code rules.domain.RuleResolver} — derselben Quelle, aus der auch der
 * Solver seine {@code RuleFact}s bekommt. Damit koennen Solver und Pruefung nicht auseinanderlaufen
 * ({@code docs/SOLVER-PLAN.md} Abschnitt 9).</p>
 */
public record CheckContext(Map<Long, SlotInfo> timeslots,
                           Map<Long, ClassInfo> classes,
                           Map<Long, TeacherInfo> teachers,
                           Map<Long, SubjectInfo> subjects,
                           Map<Long, RoomInfo> rooms,
                           List<EffectiveRule> rules) {

    /**
     * Ein Zeitfenster.
     *
     * @param lessonNumber die angezeigte Stundennummer ohne Pausen (1..n je Tag); {@code null} in einer
     *                     Pause. Sie wird gezaehlt, nie gespeichert (wie in {@code frontend/src/lib/timegrid.ts}).
     * @param timeRange    „09:35–09:55" fuer den Text der Pausenregel
     */
    public record SlotInfo(Long id, Weekday day, int position, boolean isBreak, Integer lessonNumber,
                           String timeRange) {
    }

    public record ClassInfo(Long id, String name, int grade) {
    }

    public record TeacherInfo(Long id, String name, String shortName, java.util.Set<Long> blockedTimeslotIds) {
    }

    public record SubjectInfo(Long id, String name, String shortName, String requiredRoomType) {
    }

    /** {@code blockedTimeslotIds}: woechentliche Sperrzeiten des Raums (#133), wie bei der Lehrkraft. */
    public record RoomInfo(Long id, String name, String roomType, java.util.Set<Long> blockedTimeslotIds) {
    }
}
