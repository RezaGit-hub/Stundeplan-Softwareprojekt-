package de.wsp3_1.solver;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jede Bewertungsregel einzeln, ohne Solver-Lauf und ohne Datenbank (Teststrategie, Ebene 2;
 * Tabelle in docs/SOLVER-PLAN.md Abschnitt 10.1). Je Constraint ein Positiv- und ein Negativfall.
 * Testnamen mit Pflichtenheft-Nummer (tW…) sind die Rueckverfolgung ohne Matrix.
 *
 * <p>Die Testdaten sind die des Pflichtenhefts: Klassen 7a, 8b, 9c; Faecher Mathematik, Deutsch, Physik;
 * Lehrkraefte Meyer, Schulz, Weber; Raeume R101, R204 (Klassenraum), P1 (Fachraum Physik).
 * {@code ConflictCheckerTest} im Modul planning benutzt dieselben Konstanten und Faelle.</p>
 */
class TimetableConstraintProviderTest {

    static final Long CLASS_7A = 1L;
    static final Long CLASS_9C = 2L;
    static final Long CLASS_8B = 3L;
    static final Long MATHEMATIK = 10L;
    static final Long DEUTSCH = 11L;
    static final Long PHYSIK = 12L;
    static final Long MEYER = 100L;
    static final Long SCHULZ = 101L;
    static final Long WEBER = 102L;

    static final TimeslotFact MO_1 = new TimeslotFact(1L, Weekday.MO, 1, false);
    static final TimeslotFact MO_2 = new TimeslotFact(2L, Weekday.MO, 2, false);
    static final TimeslotFact MO_BREAK = new TimeslotFact(3L, Weekday.MO, 3, true);
    static final TimeslotFact MO_4 = new TimeslotFact(4L, Weekday.MO, 4, false);
    static final TimeslotFact MO_5 = new TimeslotFact(5L, Weekday.MO, 5, false);
    static final TimeslotFact TU_1 = new TimeslotFact(8L, Weekday.TU, 1, false);
    static final TimeslotFact TU_2 = new TimeslotFact(9L, Weekday.TU, 2, false);
    /** Freitag 5. Stunde = Position 6, weil Position 3 die Pause ist (Kennung 34 im Beispielbestand). */
    static final TimeslotFact FR_5 = new TimeslotFact(34L, Weekday.FR, 6, false);

    static final RoomFact R101 = new RoomFact(1000L, "Klassenraum", true);
    static final RoomFact R204 = new RoomFact(1001L, "Klassenraum", true);
    static final RoomFact P1 = new RoomFact(1002L, "Fachraum Physik", true);
    /** Derselbe Raum, nachtraeglich abgeschaltet — Wertebereich nur noch fuer festgelegte Stunden (H7). */
    static final RoomFact R101_INAKTIV = new RoomFact(1000L, "Klassenraum", false);

    private final ConstraintVerifier<TimetableConstraintProvider, SolverTimetable> verifier =
            ConstraintVerifier.build(new TimetableConstraintProvider(), SolverTimetable.class, SolverLesson.class);

    // ---------------------------------------------------------------- H1 roomConflict

    @Test
    @DisplayName("T-W3.1: zwei Stunden gleichzeitig in R101 sind genau ein Raumkonflikt")
    void tW3_1_roomDoubleBooked_penalizesExactlyOnce() {
        SolverLesson erste = lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101);
        SolverLesson zweite = lesson(2L, CLASS_9C, MATHEMATIK, SCHULZ, null, MO_1, R101);
        // Dieselbe Klasse im selben Raum, aber an einem anderen Tag: kein Konflikt.
        SolverLesson dritte = lesson(3L, CLASS_7A, MATHEMATIK, MEYER, null, TU_1, R101);

        verifier.verifyThat(TimetableConstraintProvider::roomConflict)
                .given(erste, zweite, dritte)
                .penalizesBy(1);
    }

    @Test
    void roomConflict_differentRooms_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::roomConflict)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_9C, MATHEMATIK, SCHULZ, null, MO_1, R204))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- H2 teacherConflict

    @Test
    void teacherConflict_sameTeacherSameSlot_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::teacherConflict)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_8B, MATHEMATIK, MEYER, null, MO_1, R204))
                .penalizesBy(1);
    }

    @Test
    void teacherConflict_sameTeacherDifferentSlots_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::teacherConflict)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_8B, MATHEMATIK, MEYER, null, MO_2, R204))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- H3 classConflict

    @Test
    void classConflict_sameClassSameSlot_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::classConflict)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_1, R204))
                .penalizesBy(1);
    }

    @Test
    void classConflict_differentClasses_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::classConflict)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_8B, DEUTSCH, WEBER, null, MO_1, R204))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- H4 teacherBlockedTime

    @Test
    @DisplayName("T-W1.2: Schulz hat Freitag 5. Stunde gesperrt — eine Stunde dort ist genau ein Konflikt")
    void tW1_2_teacherBlockedTime_schulzFriday5_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::teacherBlockedTime)
                .given(lesson(1L, CLASS_9C, MATHEMATIK, SCHULZ, null, FR_5, R101),
                        new BlockedTimeFact(SCHULZ, FR_5.id()))
                .penalizesBy(1);
    }

    @Test
    void teacherBlockedTime_otherTeacherBlocked_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::teacherBlockedTime)
                .given(lesson(1L, CLASS_9C, MATHEMATIK, SCHULZ, null, FR_5, R101),
                        new BlockedTimeFact(MEYER, FR_5.id()))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- H5 roomTypeMismatch

    @Test
    void roomTypeMismatch_physicsInClassroom_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::roomTypeMismatch)
                .given(lesson(1L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, R101))
                .penalizesBy(1);
    }

    @Test
    void roomTypeMismatch_physicsInP1_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::roomTypeMismatch)
                .given(lesson(1L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1))
                .penalizesBy(0);
    }

    @Test
    void roomTypeMismatch_noRequirement_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::roomTypeMismatch)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_4, P1))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- H8 roomBlockedTime

    @Test
    @DisplayName("H8 (#133): R101 ist Montag 1. Stunde gesperrt — eine Stunde dort ist genau ein Konflikt")
    void roomBlockedTime_r101Monday1_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::roomBlockedTime)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_8B, DEUTSCH, WEBER, null, MO_2, R101),
                        new RoomBlockedTimeFact(R101.id(), MO_1.id()))
                .penalizesBy(1);
    }

    @Test
    void roomBlockedTime_otherRoomBlocked_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::roomBlockedTime)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        new RoomBlockedTimeFact(R204.id(), MO_1.id()))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- M1 unassignedLesson

    @Test
    @DisplayName("M1: eine Stunde ohne Zeitfenster gilt als offen und wird auf der mittleren Ebene bestraft")
    void unassignedLesson_withoutTimeslot_penalizesOnce() {
        SolverLesson offen = new SolverLesson(9L, CLASS_7A, MATHEMATIK, MEYER, null);

        verifier.verifyThat(TimetableConstraintProvider::unassignedLesson)
                .given(offen)
                .penalizesBy(1);
    }

    @Test
    @DisplayName("M1: eine eingeplante Stunde ist nicht offen")
    void unassignedLesson_withTimeslot_penalizesNothing() {
        verifier.verifyThat(TimetableConstraintProvider::unassignedLesson)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- R1 maxLessonsPerDay (Klasse)

    @Test
    @DisplayName("T-W2.1: hoechstens 3 Stunden pro Tag (weich), 7a hat Montag 4 — Strafe = Ueberschuss 1")
    void tW2_1_maxLessonsPerDayClassSoft_valueThree_fourLessons_penalizesOvershoot() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayClassSoft)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, CLASS_7A, 3, Severity.SOFT),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                        lesson(4L, CLASS_7A, DEUTSCH, WEBER, null, MO_5, R101))
                .penalizesBy(1);
    }

    @Test
    void maxLessonsPerDayClassSoft_valueThree_threeLessons_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayClassSoft)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, CLASS_7A, 3, Severity.SOFT),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1))
                .penalizesBy(0);
    }

    @Test
    void maxLessonsPerDayClassHard_ruleIsSoft_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayClassHard)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, CLASS_7A, 3, Severity.SOFT),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                        lesson(4L, CLASS_7A, DEUTSCH, WEBER, null, MO_5, R101))
                .penalizesBy(0);
    }

    @Test
    void maxLessonsPerDayClassHard_ruleIsHard_penalizesOvershoot() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayClassHard)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, CLASS_7A, 3, Severity.HARD),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                        lesson(4L, CLASS_7A, DEUTSCH, WEBER, null, MO_5, R101))
                .penalizesBy(1);
    }

    @Test
    void maxLessonsPerDayClassSoft_otherClassRule_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayClassSoft)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, CLASS_8B, 3, Severity.SOFT),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                        lesson(4L, CLASS_7A, DEUTSCH, WEBER, null, MO_5, R101))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("R1: das Gewicht der Regel vervielfacht die Strafe (Ueberschuss 1 x Gewicht 3)")
    void maxLessonsPerDayClassSoft_weightThree_penalizesThreefold() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayClassSoft)
                .given(new RuleFact(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, CLASS_7A, 3, Severity.SOFT, 3),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                        lesson(4L, CLASS_7A, DEUTSCH, WEBER, null, MO_5, R101))
                .penalizesBy(3);
    }

    // ---------------------------------------------------------------- R1 maxLessonsPerDay (Lehrkraft)

    @Test
    void maxLessonsPerDayTeacherSoft_valueTwo_threeLessons_penalizesOvershoot() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayTeacherSoft)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.TEACHER, MEYER, 2, Severity.SOFT),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_8B, MATHEMATIK, MEYER, null, MO_2, R204),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1))
                .penalizesBy(1);
    }

    @Test
    void maxLessonsPerDayTeacherHard_ruleIsSoft_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerDayTeacherHard)
                .given(rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.TEACHER, MEYER, 2, Severity.SOFT),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_8B, MATHEMATIK, MEYER, null, MO_2, R204),
                        lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- R2 maxLessonsPerWeek

    @Test
    void maxLessonsPerWeekClassSoft_valueFour_sixLessons_penalizesOvershoot() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerWeekClassSoft)
                .given(with(rule(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, CLASS_7A, 4, Severity.SOFT),
                        sixLessonsOf7a()))
                .penalizesBy(2);
    }

    @Test
    void maxLessonsPerWeekClassHard_ruleIsSoft_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerWeekClassHard)
                .given(with(rule(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, CLASS_7A, 4, Severity.SOFT),
                        sixLessonsOf7a()))
                .penalizesBy(0);
    }

    @Test
    void maxLessonsPerWeekTeacherSoft_valueFour_fiveLessons_penalizesOvershoot() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerWeekTeacherSoft)
                .given(with(rule(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, MEYER, 4, Severity.SOFT),
                        fiveLessonsOfMeyer()))
                .penalizesBy(1);
    }

    @Test
    void maxLessonsPerWeekTeacherHard_ruleIsHard_penalizesOvershoot() {
        verifier.verifyThat(TimetableConstraintProvider::maxLessonsPerWeekTeacherHard)
                .given(with(rule(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, MEYER, 4, Severity.HARD),
                        fiveLessonsOfMeyer()))
                .penalizesBy(1);
    }

    // ---------------------------------------------------------------- R3 noLessonsInBreaks

    @Test
    @DisplayName("R3 hart: eine festgelegte Stunde in der grossen Pause ist ein harter Verstoss")
    void noLessonsInBreaksHard_pinnedLessonInBreak_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::noLessonsInBreaksHard)
                .given(rule(RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD),
                        pinnedLesson(1L, CLASS_7A, MATHEMATIK, MEYER, MO_BREAK, R101))
                .penalizesBy(1);
    }

    @Test
    void noLessonsInBreaksSoft_ruleIsHard_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::noLessonsInBreaksSoft)
                .given(rule(RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD),
                        pinnedLesson(1L, CLASS_7A, MATHEMATIK, MEYER, MO_BREAK, R101))
                .penalizesBy(0);
    }

    @Test
    void noLessonsInBreaksSoft_ruleIsSoft_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::noLessonsInBreaksSoft)
                .given(rule(RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.SOFT),
                        pinnedLesson(1L, CLASS_7A, MATHEMATIK, MEYER, MO_BREAK, R101))
                .penalizesBy(1);
    }

    @Test
    void noLessonsInBreaksHard_lessonOutsideBreak_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::noLessonsInBreaksHard)
                .given(rule(RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD),
                        lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- H6/H7 Grenzen des Wertebereichs

    @Test
    @DisplayName("H6: eine bewegliche Stunde in der Pause ist ein harter Verstoss — auch ohne Pausenregel")
    void movableLessonInBreak_penalizesOnce() {
        // Kein RuleFact: genau darum geht es. Liegt eine festgelegte Stunde in der Pause, nimmt der
        // Uebersetzer die Pause in den Wertebereich auf (SV-2) — dann darf der Solver dort trotzdem
        // keine weitere Stunde hinsetzen, egal wie das Regelwerk eingestellt ist.
        verifier.verifyThat(TimetableConstraintProvider::movableLessonInBreak)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_BREAK, R101))
                .penalizesBy(1);
    }

    @Test
    @DisplayName("H6: die festgelegte Stunde in der Pause bleibt unbestraft — dafuer ist R3 zustaendig")
    void movableLessonInBreak_pinnedLesson_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::movableLessonInBreak)
                .given(pinnedLesson(1L, CLASS_7A, MATHEMATIK, MEYER, MO_BREAK, R101))
                .penalizesBy(0);
    }

    @Test
    @DisplayName("H7: eine bewegliche Stunde in einem deaktivierten Raum ist ein harter Verstoss")
    void movableLessonInInactiveRoom_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::movableLessonInInactiveRoom)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101_INAKTIV))
                .penalizesBy(1);
    }

    @Test
    void movableLessonInInactiveRoom_activeRoomOrPinned_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::movableLessonInInactiveRoom)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        pinnedLesson(2L, CLASS_8B, MATHEMATIK, MEYER, MO_2, R101_INAKTIV))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- S1 teacherPreferredTime

    @Test
    void teacherPreferredTime_lessonInPreferredSlot_rewardsOnce() {
        verifier.verifyThat(TimetableConstraintProvider::teacherPreferredTime)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        new PreferredTimeFact(MEYER, MO_1.id()))
                .rewardsWith(1);
    }

    @Test
    void teacherPreferredTime_otherSlot_noReward() {
        verifier.verifyThat(TimetableConstraintProvider::teacherPreferredTime)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        new PreferredTimeFact(MEYER, MO_2.id()))
                .rewardsWith(0);
    }

    // ---------------------------------------------------------------- S2 subjectSpreadOverWeek

    //@Test
    /*void subjectSpreadOverWeek_sameSubjectTwiceOnMonday_penalizesOnce() {
        verifier.verifyThat(TimetableConstraintProvider::subjectSpreadOverWeek)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, MATHEMATIK, MEYER, null, MO_2, R101))
                .penalizesBy(1);
    }*/

    @Test
    void subjectSpreadOverWeek_sameSubjectDifferentDays_noPenalty() {
        verifier.verifyThat(TimetableConstraintProvider::subjectSpreadOverWeek)
                .given(lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                        lesson(2L, CLASS_7A, MATHEMATIK, MEYER, null, TU_1, R101))
                .penalizesBy(0);
    }

    // ---------------------------------------------------------------- Helfer

    static SolverLesson lesson(Long id, Long classId, Long subjectId, Long teacherId, String requiredRoomType,
                               TimeslotFact timeslot, RoomFact room) {
        return new SolverLesson(id, classId, subjectId, teacherId, requiredRoomType, false, timeslot, room);
    }

    static SolverLesson pinnedLesson(Long id, Long classId, Long subjectId, Long teacherId,
                                     TimeslotFact timeslot, RoomFact room) {
        return new SolverLesson(id, classId, subjectId, teacherId, null, true, timeslot, room);
    }

    static RuleFact rule(RuleType type, RuleScope scope, Long ownerId, int value, Severity severity) {
        return new RuleFact(type, scope, ownerId, value, severity, 1);
    }

    /** Regel plus Stunden als ein flaches Array — {@code given(Object...)} nimmt sonst das Array als eine Tatsache. */
    private static Object[] with(RuleFact rule, Object[] lessons) {
        Object[] facts = new Object[lessons.length + 1];
        facts[0] = rule;
        System.arraycopy(lessons, 0, facts, 1, lessons.length);
        return facts;
    }

    /** Sechs Stunden der 7a ueber Montag und Dienstag, Lehrkraefte und Raeume ohne Doppelbelegung. */
    private static Object[] sixLessonsOf7a() {
        return new Object[] {
                lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                lesson(2L, CLASS_7A, DEUTSCH, WEBER, null, MO_2, R101),
                lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                lesson(4L, CLASS_7A, DEUTSCH, WEBER, null, MO_5, R101),
                lesson(5L, CLASS_7A, MATHEMATIK, MEYER, null, TU_1, R101),
                lesson(6L, CLASS_7A, DEUTSCH, WEBER, null, TU_2, R101)
        };
    }

    /** Fuenf Stunden von Meyer in verschiedenen Zeitfenstern. */
    private static Object[] fiveLessonsOfMeyer() {
        return new Object[] {
                lesson(1L, CLASS_7A, MATHEMATIK, MEYER, null, MO_1, R101),
                lesson(2L, CLASS_8B, MATHEMATIK, MEYER, null, MO_2, R204),
                lesson(3L, CLASS_7A, PHYSIK, MEYER, "Fachraum Physik", MO_4, P1),
                lesson(4L, CLASS_8B, MATHEMATIK, MEYER, null, MO_5, R204),
                lesson(5L, CLASS_7A, MATHEMATIK, MEYER, null, TU_1, R101)
        };
    }
}
