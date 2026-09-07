package de.wsp3_1.solver;

import de.wsp3_1.shared.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Der Uebersetzer ohne Solver und ohne Spring (Teststrategie, Ebene 1; docs/SOLVER-PLAN.md 10.2). */
class SolverTranslatorTest {

    private static final TimeslotFact MO_1 = new TimeslotFact(1L, Weekday.MO, 1, false);
    private static final TimeslotFact MO_2 = new TimeslotFact(2L, Weekday.MO, 2, false);
    private static final TimeslotFact MO_BREAK = new TimeslotFact(3L, Weekday.MO, 3, true);
    private static final RoomFact R101 = new RoomFact(1L, "Klassenraum", true);
    private static final RoomFact R204 = new RoomFact(2L, "Klassenraum", true);
    private static final RoomFact ALT = new RoomFact(3L, "Klassenraum", false);

    @Test
    @DisplayName("je Zeitfenster und Raum eine Instanz: zwei Stunden auf MO_1 teilen dasselbe Objekt")
    void toSolution_buildsOneFactInstancePerId() {
        SolveInput input = input(List.of(
                new SolveInput.LessonInput(10L, 1L, 1L, 1L, null, false, 1L, 1L),
                new SolveInput.LessonInput(11L, 2L, 1L, 2L, null, false, 1L, 2L)));

        SolverTimetable solution = SolverTranslator.toSolution(input);

        assertSame(solution.getLessons().get(0).getTimeslot(), solution.getLessons().get(1).getTimeslot());
        assertEquals(List.of(MO_1, MO_2), solution.getTimeslots(), "Pausen gehoeren nicht in den Wertebereich");
        assertEquals(List.of(R101, R204), solution.getRooms(), "inaktive Raeume gehoeren nicht in den Wertebereich");
    }

    @Test
    void toSolution_openLessonHasNullTimeslotAndRoom() {
        SolveInput input = input(List.of(new SolveInput.LessonInput(10L, 1L, 1L, 1L, null, false, null, null)));

        SolverLesson lesson = SolverTranslator.toSolution(input).getLessons().get(0);

        assertNull(lesson.getTimeslot());
        assertNull(lesson.getRoom());
    }

    @Test
    @DisplayName("festgelegte Stunde in der Pause / im inaktiven Raum: bleibt stehen, Werte kommen in den Wertebereich")
    void toSolution_pinnedLessonOutsideValueRange_keepsItsFactsAndWidensValueRange() {
        SolveInput input = input(List.of(new SolveInput.LessonInput(10L, 1L, 1L, 1L, null, true, 3L, 3L)));

        SolverTimetable solution = SolverTranslator.toSolution(input);
        SolverLesson lesson = solution.getLessons().get(0);

        assertEquals(MO_BREAK, lesson.getTimeslot());
        assertEquals(ALT, lesson.getRoom());
        assertEquals(List.of(MO_1, MO_2, MO_BREAK), solution.getTimeslots());
        assertEquals(List.of(R101, R204, ALT), solution.getRooms());
    }

    @Test
    @DisplayName("nicht festgelegte Stunde in der Pause / im inaktiven Raum: wird offen uebergeben und neu geplant")
    void toSolution_unpinnedLessonOutsideValueRange_becomesOpen() {
        SolveInput input = input(List.of(new SolveInput.LessonInput(10L, 1L, 1L, 1L, null, false, 3L, 1L)));

        SolverTimetable solution = SolverTranslator.toSolution(input);
        SolverLesson lesson = solution.getLessons().get(0);

        assertNull(lesson.getTimeslot());
        assertNull(lesson.getRoom());
        assertEquals(List.of(MO_1, MO_2), solution.getTimeslots(), "der Wertebereich bleibt ohne Pause");
    }

    @Test
    void toSolution_unknownTimeslot_throws() {
        SolveInput input = input(List.of(new SolveInput.LessonInput(10L, 1L, 1L, 1L, null, false, 99L, 1L)));

        assertThrows(IllegalArgumentException.class, () -> SolverTranslator.toSolution(input));
    }

    @Test
    void toResult_timeslotNullMeansRoomNull() {
        SolverLesson lesson = new SolverLesson(10L, 1L, 1L, 1L, null, false, null, R101);
        SolverTimetable solution = new SolverTimetable(List.of(MO_1), List.of(R101), List.of(lesson));

        SolveResult result = SolverTranslator.toResult(solution);

        assertEquals(new SolveResult.Placement(10L, null, null), result.placements().get(0));
        assertNull(result.score(), "ohne Lauf gibt es keinen Score");
    }

    @Test
    void toResult_keepsIds() {
        SolverLesson lesson = new SolverLesson(10L, 1L, 1L, 1L, null, false, MO_1, R204);
        SolverTimetable solution = new SolverTimetable(List.of(MO_1), List.of(R204), List.of(lesson));

        SolveResult result = SolverTranslator.toResult(solution);

        assertEquals(new SolveResult.Placement(10L, 1L, 2L), result.placements().get(0));
    }

    private static SolveInput input(List<SolveInput.LessonInput> lessons) {
        return new SolveInput(lessons, List.of(MO_1, MO_2, MO_BREAK), List.of(R101, R204, ALT),
                List.of(), List.of(), List.of());
    }
}
