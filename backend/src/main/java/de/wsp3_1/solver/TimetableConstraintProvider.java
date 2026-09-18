package de.wsp3_1.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import ai.timefold.solver.core.api.score.stream.tri.TriConstraintStream;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;

import java.util.List;
import java.util.function.Function;

import java.util.EnumMap;
import java.util.Map;
/**
 * Die Bewertungsregeln des Solvers (docs/SOLVER-PLAN.md Abschnitt 4). Die Methodennamen sind stabil:
 * sie stehen so in der Entwurfsdokumentation, in {@code TimetableConstraintProviderTest} und in der
 * Abnahme-Checkliste.
 *
 * <ul>
 *   <li><b>hard</b>: H1–H5 feste Konflikte (Strafe 1), H6/H7 die Grenzen des Wertebereichs, H8 die
 *       Sperrzeit eines Raums (#133) und die harten Zwillinge R1–R3 des Regelwerks</li>
 *   <li><b>medium</b>: M1 offene Stunde (Strafe 1)</li>
 *   <li><b>soft</b>: die weichen Zwillinge R1–R3, S1 Wunschzeit (Belohnung 1), S2 Faecherverteilung</li>
 * </ul>
 *
 * <p>Zur Erinnerung: {@code forEach(SolverLesson.class)} liefert nur Stunden, deren Zeitfenster gesetzt
 * ist ({@code allowsUnassigned = true}); {@code getTimeslot()} ist in allen Stroemen ausser M1 deshalb
 * nie {@code null}. Pausen liegen nicht im Wertebereich — R3 trifft nur Stunden, die von Hand in eine
 * Pause gesetzt wurden.</p>
 */
public class TimetableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[] {
        // ============================================================
        // HARTE CONSTRAINTS (müssen 0 sein)
        // ============================================================
        roomConflict(f),
        teacherConflict(f),
        classConflict(f),
        teacherBlockedTime(f),
        roomTypeMismatch(f),
        movableLessonInBreak(f),
        movableLessonInInactiveRoom(f),
        roomBlockedTime(f),
        doubleLessonSameRoom(f),
        
        // ============================================================
        // REGELWERK – HARTE ZWILLINGE
        // ============================================================
        maxLessonsPerDayClassHard(f),
        maxLessonsPerDayTeacherHard(f),
        maxLessonsPerWeekClassHard(f),
        maxLessonsPerWeekTeacherHard(f),
        noLessonsInBreaksHard(f),
        
        // ============================================================
        // MEDIUM CONSTRAINT
        // ============================================================
        unassignedLesson(f),
        
        // ============================================================
        // REGELWERK – WEICHE ZWILLINGE
        // ============================================================
        maxLessonsPerDayClassSoft(f),
        maxLessonsPerDayTeacherSoft(f),
        maxLessonsPerWeekClassSoft(f),
        maxLessonsPerWeekTeacherSoft(f),
        noLessonsInBreaksSoft(f),
        
        // ============================================================
        // WEICHE WÜNSCHE
        // ============================================================
        teacherPreferredTime(f),
        subjectSpreadOverWeek(f),
        doubleLesson(f),
        avoidFreePeriods(f), 
        avoidTripleLessons(f),
        balancedClassWeek(f)
        };
    }

    // ---------------------------------------------------------------- feste harte Konflikte (E-07, E-20)

    /** H1: zwei Stunden im selben Zeitfenster im selben Raum (T-W3.1). */
    public Constraint roomConflict(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getTimeslot), Joiners.equal(SolverLesson::getRoom))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("roomConflict");
    }

    /** H2: dieselbe Lehrkraft zweimal im selben Zeitfenster. */
    public Constraint teacherConflict(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getTimeslot), Joiners.equal(SolverLesson::getTeacherId))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("teacherConflict");
    }

    /** H3: dieselbe Klasse zweimal im selben Zeitfenster. */
    public Constraint classConflict(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getTimeslot), Joiners.equal(SolverLesson::getClassId))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("classConflict");
    }

    /** H4: Stunde in einem gesperrten Zeitfenster der Lehrkraft (T-W1.2). */
    public Constraint teacherBlockedTime(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .join(BlockedTimeFact.class,
                        Joiners.equal(SolverLesson::getTeacherId, BlockedTimeFact::teacherId),
                        Joiners.equal(lesson -> lesson.getTimeslot().id(), BlockedTimeFact::timeslotId))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("teacherBlockedTime");
    }

    /** H5: Raumart passt nicht zur Raumanforderung des Fachs (exakter Textvergleich). */
    public Constraint roomTypeMismatch(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .filter(lesson -> lesson.getRequiredRoomType() != null
                        && !lesson.getRequiredRoomType().equals(lesson.getRoom().roomType()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("roomTypeMismatch");
    }

    // ---------------------------------------------------------------- Grenzen des Wertebereichs (SV-2)

    /**
     * H6: eine <b>bewegliche</b> Stunde liegt in einer Pause.
     *
     * <p>Normalerweise unmoeglich — Pausen stehen nicht im Wertebereich. Liegt aber eine
     * <b>festgelegte</b> Stunde in einer Pause (Handaufbau), muss der Uebersetzer genau dieses
     * Zeitfenster in den Wertebereich aufnehmen, sonst lehnt Timefold den Startwert ab (SV-2 in
     * {@code docs/VERTRAG.md}). Ab da duerfte der Solver dort auch <i>andere</i> Stunden hinsetzen.
     * Diese Regel verbietet ihm das — unabhaengig davon, ob die Pausenregel des Regelwerks hart oder
     * weich steht: der Wertebereich ist eine bauliche Grenze, keine Geschmacksfrage.</p>
     */
    public Constraint movableLessonInBreak(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .filter(lesson -> !lesson.isPinned() && lesson.getTimeslot().isBreak())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("movableLessonInBreak");
    }

    /**
     * H7: dasselbe fuer einen deaktivierten Raum. H5 prueft nur die Raumart — ohne diese Regel duerfte
     * der Solver in einen abgeschalteten Raum planen, sobald dort eine festgelegte Stunde liegt.
     */
    public Constraint movableLessonInInactiveRoom(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .filter(lesson -> !lesson.isPinned() && !lesson.getRoom().active())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("movableLessonInInactiveRoom");
    }

    /** H8: Stunde in einem gesperrten Zeitfenster des Raums (#133) — wie H4, nur ueber den Raum. */
    public Constraint roomBlockedTime(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .join(RoomBlockedTimeFact.class,
                        Joiners.equal(lesson -> lesson.getRoom().id(), RoomBlockedTimeFact::roomId),
                        Joiners.equal(lesson -> lesson.getTimeslot().id(), RoomBlockedTimeFact::timeslotId))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("roomBlockedTime");
    }

    // ---------------------------------------------------------------- offene Stunde

    /** M1: eine Stunde ohne Zeitfenster ist „offen" — erlaubt, aber schlechter als eingeplant. */
    public Constraint unassignedLesson(ConstraintFactory f) {
        return f.forEachIncludingUnassigned(SolverLesson.class)
                .filter(lesson -> lesson.getTimeslot() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("unassignedLesson");
    }

    // ---------------------------------------------------------------- Regelwerk: Zwillinge hart/weich (E-21)

    /** R1 Klasse, hart: mehr Stunden je Tag als die Regel erlaubt; Strafe = Ueberschuss x Gewicht (T-W2.1). */
    public Constraint maxLessonsPerDayClassHard(ConstraintFactory f) {
        return perDay(f, RuleScope.CLASS, Severity.HARD, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, day, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayClassHard");
    }

    public Constraint maxLessonsPerDayClassSoft(ConstraintFactory f) {
        return perDay(f, RuleScope.CLASS, Severity.SOFT, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, day, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayClassSoft");
    }

    public Constraint maxLessonsPerDayTeacherHard(ConstraintFactory f) {
        return perDay(f, RuleScope.TEACHER, Severity.HARD, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, day, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayTeacherHard");
    }

    public Constraint maxLessonsPerDayTeacherSoft(ConstraintFactory f) {
        return perDay(f, RuleScope.TEACHER, Severity.SOFT, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, day, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerDayTeacherSoft");
    }

    /** R2 wie R1, nur ueber die ganze Woche. */
    public Constraint maxLessonsPerWeekClassHard(ConstraintFactory f) {
        return perWeek(f, RuleScope.CLASS, Severity.HARD, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekClassHard");
    }

    public Constraint maxLessonsPerWeekClassSoft(ConstraintFactory f) {
        return perWeek(f, RuleScope.CLASS, Severity.SOFT, SolverLesson::getClassId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekClassSoft");
    }

    public Constraint maxLessonsPerWeekTeacherHard(ConstraintFactory f) {
        return perWeek(f, RuleScope.TEACHER, Severity.HARD, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekTeacherHard");
    }

    public Constraint maxLessonsPerWeekTeacherSoft(ConstraintFactory f) {
        return perWeek(f, RuleScope.TEACHER, Severity.SOFT, SolverLesson::getTeacherId)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, count) -> (count - rule.value()) * rule.weight())
                .asConstraint("maxLessonsPerWeekTeacherSoft");
    }

    /** R3: Stunde in einer Pause — trifft im Solver nur festgelegte Stunden (Pausen sind kein Wertebereich). */
    public Constraint noLessonsInBreaksHard(ConstraintFactory f) {
        return inBreaks(f, Severity.HARD)
                .penalize(HardMediumSoftScore.ONE_HARD, (rule, lesson) -> rule.weight())
                .asConstraint("noLessonsInBreaksHard");
    }

    public Constraint noLessonsInBreaksSoft(ConstraintFactory f) {
        return inBreaks(f, Severity.SOFT)
                .penalize(HardMediumSoftScore.ONE_SOFT, (rule, lesson) -> rule.weight())
                .asConstraint("noLessonsInBreaksSoft");
    }

    // ---------------------------------------------------------------- weiche Wuensche (Soll: Guete des Vorschlags)

    /** S1: Belohnung fuer eine Stunde in einem bevorzugten Zeitfenster der Lehrkraft. */
    public Constraint teacherPreferredTime(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .join(PreferredTimeFact.class,
                        Joiners.equal(SolverLesson::getTeacherId, PreferredTimeFact::teacherId),
                        Joiners.equal(lesson -> lesson.getTimeslot().id(), PreferredTimeFact::timeslotId))
                .reward(HardMediumSoftScore.ONE_SOFT)
                .asConstraint("teacherPreferredTime");
    }

    /** S2: dasselbe Fach einer Klasse zweimal am selben Tag (Faecherverteilung ueber die Woche). 
     * Ausgenommen sind Doppelstunden
    */
    public Constraint subjectSpreadOverWeek(ConstraintFactory f) {
    return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getClassId),
                        Joiners.equal(SolverLesson::getSubjectId),
                        Joiners.equal(lesson -> lesson.getTimeslot().day()))
                .filter((a, b) -> {
                        int diff = Math.abs(a.getTimeslot().position() - b.getTimeslot().position());
                        // NUR bestrafen, wenn die Stunden NICHT aufeinanderfolgen
                        return diff > 1;
                 })
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint("subjectSpreadOverWeek");
        }

        public Constraint doubleLessonSameRoom(ConstraintFactory f) {
            return f.forEachUniquePair(SolverLesson.class,
                            Joiners.equal(SolverLesson::getClassId),
                            Joiners.equal(SolverLesson::getSubjectId),
                            Joiners.equal(SolverLesson::getTeacherId),
                            Joiners.equal(lesson -> lesson.getTimeslot().day()))
                    .filter((a, b) -> {
                        int diff = Math.abs(a.getTimeslot().position() - b.getTimeslot().position());
                        // Nur echte Nachbarstunden sind eine Doppelstunde. Pausen liegen nicht im
                        // Wertebereich, deshalb bleibt `diff == 1` innerhalb eines Tages auch dann
                        // korrekt, wenn zwischen den beiden Positionen im Raster eine Pause steht
                        // (dann ist `diff` naemlich > 1 und die Regel greift nicht).
                        return diff == 1
                                && !a.getRoom().id().equals(b.getRoom().id());
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD)
                    .asConstraint("doubleLessonSameRoom");
        }

        /* S3: Belohnung für Doppelstunden */
        public Constraint doubleLesson(ConstraintFactory f) {
        return f.forEachUniquePair(SolverLesson.class,
                        Joiners.equal(SolverLesson::getClassId),
                        Joiners.equal(SolverLesson::getSubjectId),
                        Joiners.equal(SolverLesson::getTeacherId),
                        Joiners.equal(lesson -> lesson.getTimeslot().day()))
                .filter((a, b) -> {
                        int diff = Math.abs(a.getTimeslot().position() - b.getTimeslot().position());
                        // NUR aufeinanderfolgende Stunden = Doppelstunde
                        return diff == 1;
                })
                .reward(HardMediumSoftScore.ofSoft(5))
                .asConstraint("doubleLesson");
        }

        /* S4: Bestrafung für mehr als 2 gleiche Fachstunden hintereinander */
        // ================================================================
        // NEU 3: 3+ Stunden hintereinander vermeiden (Blockunterricht)
        // ================================================================

    public Constraint avoidTripleLessons(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .groupBy(
                        SolverLesson::getClassId,
                        SolverLesson::getSubjectId,
                        lesson -> lesson.getTimeslot().day(),
                        ConstraintCollectors.count()
                )
                .filter((classId, subjectId, day, count) -> count > 2)
                .penalize(HardMediumSoftScore.ONE_SOFT, (classId, subjectId, day, count) -> count - 2)
                .asConstraint("avoidTripleLessons");
    }

    public Constraint balancedClassWeek(ConstraintFactory f) {
        return f.forEach(SolverLesson.class)
                .groupBy(SolverLesson::getClassId, ConstraintCollectors.toList())
                .penalize(HardMediumSoftScore.ONE_SOFT, (classId, lessons) -> balancePenalty(lessons))
                .asConstraint("balancedClassWeek");
    }

    /** Tage der Schulwoche für die Mittelwertrechnung — Mo–Fr; bei Samstagsunterricht auf 6 setzen. */
    private static final int SCHOOL_DAYS = 5;

    /**
     * Summe der quadrierten Abweichungen vom Mittelwert; {@code 0} heißt „alle Tage gleich belegt".
     * Reine Funktion, damit sie sich im Test einzeln nachrechnen lässt.
     */
    private static int balancePenalty(List<SolverLesson> lessons) {
        if (lessons.size() < 2) {
            return 0;
        }
        Map<Weekday, Integer> perDay = new EnumMap<>(Weekday.class);
        for (SolverLesson lesson : lessons) {
            perDay.merge(lesson.getTimeslot().day(), 1, Integer::sum);
        }
        double average = (double) lessons.size() / SCHOOL_DAYS;
        double sumOfSquares = 0;
        for (int day = 0; day < SCHOOL_DAYS; day++) {
            int count = perDay.getOrDefault(Weekday.values()[day], 0);
            double difference = count - average;
            sumOfSquares += difference * difference;
        }
        return (int) Math.round(sumOfSquares);
    }

        public Constraint avoidFreePeriods(ConstraintFactory f) {
                return f.forEach(SolverLesson.class)
                        .groupBy(
                                SolverLesson::getClassId,
                                lesson -> lesson.getTimeslot().day(),
                                ConstraintCollectors.toList()
                        )
                        .penalize(HardMediumSoftScore.ONE_SOFT, (classId, day, lessons) -> gapPenalty(lessons))
                        .asConstraint("avoidFreePeriods");
                }

                /**
                 * Bestrafung eines Schultages: jede Luecke zwischen erster und letzter Stunde einer Klasse zaehlt
                 * dreifach, jede Luecke vor der ersten Stunde einfach. Nachlaufende Luecken (die Klasse endet frueher
                 * als der Tag) zaehlen nicht — der Solver kann sie nicht sinnvoll bestrafen, ohne die Gesamtzahl der
                 * Stundennummern des Tages zu kennen, und sie sind der harmloseste Fall.
                 */
                private static int gapPenalty(List<SolverLesson> lessons) {
                if (lessons.size() < 2) {
                        return 0;
                }
                List<Integer> numbers = lessons.stream()
                        .map(lesson -> lesson.getTimeslot().lessonNumber())
                        .filter(java.util.Objects::nonNull)
                        .sorted()
                        .distinct()
                        .toList();
                if (numbers.size() < 2) {
                        return 0;
                }
                int first = numbers.getFirst();
                int last = numbers.getLast();
                int span = last - first + 1;
                int internalFree = span - numbers.size();   // Luecken zwischen erster und letzter Stunde
                int leadingFree = first - 1;                // Luecken vor der ersten Stunde
                return internalFree * 3 + leadingFree;
        }

    // ---------------------------------------------------------------- gemeinsame Stroeme der Regel-Zwillinge

    /**
     * (Regel, Tag, Anzahl) je Objekt und Tag — nur Gruppen ueber dem erlaubten Wert.
     * {@code owner} sagt, ob die Regel an der Klasse oder an der Lehrkraft der Stunde haengt.
     */
    private static TriConstraintStream<RuleFact, Weekday, Long> perDay(
            ConstraintFactory f, RuleScope scope, Severity severity, Function<SolverLesson, Long> owner) {
        return f.forEach(RuleFact.class)
                .filter(rule -> rule.type() == RuleType.MAX_LESSONS_PER_DAY
                        && rule.scope() == scope && rule.severity() == severity)
                .join(SolverLesson.class, Joiners.equal(RuleFact::ownerId, owner))
                .groupBy((rule, lesson) -> rule, (rule, lesson) -> lesson.getTimeslot().day(),
                        ConstraintCollectors.countBi())
                .filter((rule, day, count) -> count > rule.value());
    }

    /** (Regel, Anzahl) je Objekt ueber die Woche — nur ueber dem erlaubten Wert. */
    private static BiConstraintStream<RuleFact, Long> perWeek(
            ConstraintFactory f, RuleScope scope, Severity severity, Function<SolverLesson, Long> owner) {
        return f.forEach(RuleFact.class)
                .filter(rule -> rule.type() == RuleType.MAX_LESSONS_PER_WEEK
                        && rule.scope() == scope && rule.severity() == severity)
                .join(SolverLesson.class, Joiners.equal(RuleFact::ownerId, owner))
                .groupBy((rule, lesson) -> rule, ConstraintCollectors.countBi())
                .filter((rule, count) -> count > rule.value());
    }

    /** (Pausenregel, Stunde in einer Pause). */
    private static BiConstraintStream<RuleFact, SolverLesson> inBreaks(ConstraintFactory f, Severity severity) {
        return f.forEach(RuleFact.class)
                .filter(rule -> rule.type() == RuleType.NO_LESSONS_IN_BREAKS && rule.severity() == severity)
                .join(SolverLesson.class, Joiners.filtering((rule, lesson) -> lesson.getTimeslot().isBreak()));
    }
}
