package de.wsp3_1.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import de.wsp3_1.shared.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein echter Solver-Lauf auf den Pflichtenheft-Testdaten, ohne Spring und ohne Datenbank
 * (Teststrategie, Ebene 2; T-W8.1 auf Solver-Seite). Rechenzeit 2 s — bei acht Stunden reicht das
 * fuer den Konstruktionsheuristik-Schritt und etwas lokale Suche.
 */
class SmallDatasetSolveTest {

    private static SolverFactory<SolverTimetable> factory(int seconds) {
        return SolverFactory.create(new SolverConfig()
                .withSolutionClass(SolverTimetable.class)
                .withEntityClasses(SolverLesson.class)
                .withConstraintProviderClass(TimetableConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(seconds)));
    }

    @Test
    @DisplayName("T-W8.1 (Solver): kleiner Bestand — jede Stunde belegt, kein harter Verstoss")
    void tW8_1_smallDataset_noHardConflict_everyLessonAssigned() {
        SolveInput input = SampleSolveInputs.small();

        SolverTimetable solution = factory(2).buildSolver().solve(SolverTranslator.toSolution(input));
        SolveResult result = SolverTranslator.toResult(solution);

        assertEquals(0, solution.getScore().hardScore(), "harter Score: " + solution.getScore());
        assertEquals(List.of(), HardConflicts.of(input, result));
        assertEquals(0, HardConflicts.open(result), "bei 30 Zeitfenstern und 3 Raeumen bleibt keine Stunde offen");
        assertEquals(8, result.placements().size());
        assertTrue(result.score().startsWith("0hard/0medium/"), result.score());
    }

    @Test
    @DisplayName("T-W9.1 (Solver): festgelegte Stunden bleiben, wo sie sind; offene werden eingeplant")
    void tW9_1_pinnedLessonsStay_othersGetAssigned() {
        SolveInput base = SampleSolveInputs.small();
        List<SolveInput.LessonInput> lessons = new ArrayList<>(base.lessons());
        // 7a Mathematik (Stunde 1) festgelegt auf Mo 1. Stunde in R101, 8b Mathematik (Stunde 5) auf Mo 2. in R204.
        lessons.set(0, pinned(lessons.get(0), SampleSolveInputs.timeslotId(Weekday.MO, 1), SampleSolveInputs.R101));
        lessons.set(4, pinned(lessons.get(4), SampleSolveInputs.timeslotId(Weekday.MO, 2), SampleSolveInputs.R204));
        SolveInput input = new SolveInput(lessons, base.timeslots(), base.rooms(), base.rules(),
                base.blockedTimes(), base.preferredTimes());

        SolveResult result = SolverTranslator.toResult(factory(2).buildSolver().solve(SolverTranslator.toSolution(input)));

        assertEquals(new SolveResult.Placement(1L, 1L, SampleSolveInputs.R101), result.placements().get(0));
        assertEquals(new SolveResult.Placement(5L, 2L, SampleSolveInputs.R204), result.placements().get(4));
        assertEquals(List.of(), HardConflicts.of(input, result));
        assertEquals(0, HardConflicts.open(result));
    }

    @Test
    @DisplayName("Handaufbau in die Pause: eine festgelegte Stunde ausserhalb des Wertebereichs stoert den Lauf nicht")
    void pinnedLessonInBreak_isKeptAndCountsAsHardViolation() {
        SolveInput base = SampleSolveInputs.small();
        List<SolveInput.LessonInput> lessons = new ArrayList<>(base.lessons());
        Long pause = SampleSolveInputs.timeslotId(Weekday.MO, SampleSolveInputs.PAUSE_POSITION);
        lessons.set(0, pinned(lessons.get(0), pause, SampleSolveInputs.R101));
        SolveInput input = new SolveInput(lessons, base.timeslots(), base.rooms(), base.rules(),
                base.blockedTimes(), base.preferredTimes());

        SolverTimetable solution = factory(1).buildSolver().solve(SolverTranslator.toSolution(input));
        SolveResult result = SolverTranslator.toResult(solution);

        assertEquals(new SolveResult.Placement(1L, pause, SampleSolveInputs.R101), result.placements().get(0),
                "die festgelegte Stunde bleibt in der Pause");
        assertEquals(-1, solution.getScore().hardScore(), "genau der eine Verstoss gegen die harte Pausenregel");
        assertEquals(0, HardConflicts.open(result), "die uebrigen sieben Stunden sind trotzdem belegt");
    }

    private static SolveInput.LessonInput pinned(SolveInput.LessonInput lesson, Long timeslotId, Long roomId) {
        return new SolveInput.LessonInput(lesson.id(), lesson.classId(), lesson.subjectId(), lesson.teacherId(),
                lesson.requiredRoomType(), true, timeslotId, roomId);
    }
}
