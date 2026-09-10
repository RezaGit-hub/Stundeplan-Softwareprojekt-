package de.wsp3_1.solver;

import de.wsp3_1.config.domain.SystemConfigData;
import de.wsp3_1.config.domain.SystemConfigService;
import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der {@link SolverRunner} als Spring-Bean mit dem {@code SolverManager} des Starters: asynchroner Start,
 * Rechenzeit aus {@code SystemConfig.maxSolveSeconds}, Abbruch mit bisher bestem Ergebnis, blockierender
 * Lauf. Der Spring-Kontext braucht die Datenbank (Testcontainers bzw. Profil ci), der Solver selbst nicht.
 */
class SolverRunnerIT extends IntegrationTestBase {

    @Autowired
    private SolverRunner runner;

    @Autowired
    private SystemConfigService config;

    @Test
    @DisplayName("Start ist asynchron, endet mit der Rechenzeit aus SystemConfig, Ergebnis liegt vor dem Statuswechsel vor")
    void start_isAsync_endsByConfiguredLimit_resultBeforeNotSolving() throws InterruptedException {
        SystemConfigData before = config.get();
        config.update(new SystemConfigData(before.schoolName(), before.state(), before.schoolType(), 3));
        int seconds = config.maxSolveSeconds();
        assertEquals(3, seconds);

        AtomicReference<SolveResult> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SolveInput input = SampleSolveInputs.small();

        long start = System.currentTimeMillis();
        runner.start(901L, input, seconds, result::set, error::set);
        assertTrue(runner.isSolving(901L), "direkt nach start() laeuft oder wartet der Lauf");
        assertNull(result.get(), "start() kehrt zurueck, bevor das Ergebnis da ist");

        long millis = waitUntilNotSolving(901L, seconds * 1000L + 10_000);
        // Sofort nach dem Statuswechsel — kein weiteres Warten: das Ergebnis muss schon da sein.
        SolveResult delivered = result.get();
        assertNull(error.get(), "kein Fehler: " + error.get());
        assertNotNull(delivered, "Final-Consumer laeuft vor NOT_SOLVING");
        assertEquals(List.of(), HardConflicts.of(input, delivered));
        assertEquals(0, HardConflicts.open(delivered));
        assertTrue(millis >= 2_000 && millis <= seconds * 1000L + 10_000,
                "Laufzeit " + millis + " ms bei Limit " + seconds + " s (Start " + start + ")");

        config.update(before);
    }

    @Test
    @DisplayName("Abbruch: der Lauf endet innerhalb weniger Sekunden mit dem bisher besten Ergebnis")
    void cancel_deliversBestSoFar() throws InterruptedException {
        AtomicReference<SolveResult> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SolveInput input = SampleSolveInputs.small();

        runner.start(902L, input, 30, result::set, error::set);
        Thread.sleep(1_000);
        assertTrue(runner.isSolving(902L));
        runner.cancel(902L);

        long millis = waitUntilNotSolving(902L, 5_000);
        assertNull(error.get());
        assertNotNull(result.get(), "auch nach Abbruch kommt ein Ergebnis");
        assertTrue(millis < 5_000, "Abbruch dauerte " + millis + " ms");
        assertTrue(result.get().placements().stream().anyMatch(p -> p.timeslotId() != null),
                "mindestens eine Stunde belegt");
        assertNotNull(result.get().score());
    }

    @Test
    void cancel_withoutRun_doesNothing() {
        runner.cancel(903L);
        assertFalse(runner.isSolving(903L));
    }

    @Test
    @DisplayName("blockierender Lauf (Beispieldaten): Ergebnis ohne harten Verstoss nach der Rechenzeit")
    void solveBlocking_returnsResult() {
        SolveInput input = SampleSolveInputs.small();

        SolveResult result = runner.solveBlocking(904L, input, 2);

        assertEquals(8, result.placements().size());
        assertEquals(List.of(), HardConflicts.of(input, result));
        assertTrue(result.score().startsWith("0hard/"), result.score());
        assertFalse(runner.isSolving(904L));
    }

    private long waitUntilNotSolving(Long problemId, long maxMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (runner.isSolving(problemId)) {
            if (System.currentTimeMillis() - start > maxMillis) {
                throw new AssertionError("Lauf " + problemId + " lief nach " + maxMillis + " ms noch");
            }
            Thread.sleep(50);
        }
        return System.currentTimeMillis() - start;
    }
}
