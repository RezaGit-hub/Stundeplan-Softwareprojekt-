package de.wsp3_1.solver;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der volle Beispielbestand (360 Stunden) mit der Rechenzeit, die {@code sampledata} beim Laden benutzt
 * (10 s). Kein Spring, keine Datenbank — aber 10 s Laufzeit, deshalb als Integrationstest markiert und
 * nicht bei jedem Push. Misst, wie viele Stunden nach 10 s offen bleiben (steht im Testbericht).
 */
@Tag("integration")
class FullDatasetSolveIT {

    @Test
    @DisplayName("voller Bestand, 10 s: kein harter Verstoss, hoechstens wenige offene Stunden")
    void fullDataset_tenSeconds_noHardConflict() {
        SolveInput input = SampleSolveInputs.full();
        SolverFactory<SolverTimetable> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(SolverTimetable.class)
                .withEntityClasses(SolverLesson.class)
                .withConstraintProviderClass(TimetableConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(10)));

        long start = System.currentTimeMillis();
        SolverTimetable solution = factory.buildSolver().solve(SolverTranslator.toSolution(input));
        long millis = System.currentTimeMillis() - start;
        SolveResult result = SolverTranslator.toResult(solution);

        System.out.println("FullDatasetSolveIT: " + millis + " ms, Score " + result.score()
                + ", offen " + HardConflicts.open(result) + " von " + result.placements().size());

        assertEquals(360, result.placements().size());
        assertEquals(List.of(), HardConflicts.of(input, result));
        assertEquals(0, solution.getScore().hardScore(), result.score());
        assertTrue(millis < 20_000, "Lauf laenger als Limit + 10 s: " + millis + " ms");
        assertTrue(HardConflicts.open(result) <= 36, "mehr als 10 % offen: " + HardConflicts.open(result));
    }
}
