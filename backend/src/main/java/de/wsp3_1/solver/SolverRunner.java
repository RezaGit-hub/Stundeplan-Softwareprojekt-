package de.wsp3_1.solver;

import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Duenne Huelle um den {@link SolverManager} des Timefold-Spring-Boot-Starters. Das Modul {@code planning}
 * ({@code SolveService}) ruft nur diese vier Methoden; alles mit Datenbank bleibt dort
 * (docs/SOLVER-PLAN.md Abschnitt 8).
 *
 * <p>Problem-Kennung = Entwurfs-Kennung. Die Rechenzeit je Lauf kommt vom Aufrufer
 * ({@code SystemConfig.maxSolveSeconds}); {@code timefold.solver.termination.spent-limit} in
 * {@code application.properties} gilt nur fuer Laeufe ohne eigene Angabe — hier bekommt jeder Lauf eine.</p>
 *
 * <p>Zusicherung: {@link #isSolving(Long)} bleibt {@code true}, bis {@code onFinished} bzw. {@code onError}
 * <b>fertig gelaufen</b> ist. Wer also {@code EDITING} sieht, sieht auch das gespeicherte Ergebnis. Timefold
 * allein gibt das nur beim Ablauf der Rechenzeit her; nach {@link #cancel(Long)} meldet
 * {@code SolverManager.getSolverStatus} schon {@code NOT_SOLVING}, waehrend der Final-Consumer noch laeuft
 * (gemessen 05.09., {@code SolverRunnerIT}). Deshalb fuehrt der Runner die Kennungen laufender Auftraege
 * selbst in {@link #pending}.</p>
 */
@Service
public class SolverRunner {

    private final SolverManager<SolverTimetable> solverManager;

    /** Entwuerfe, deren Lauf vorgemerkt oder gestartet und deren Rueckruf noch nicht zu Ende ist. */
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    public SolverRunner(SolverManager<SolverTimetable> solverManager) {
        this.solverManager = solverManager;
    }

    /**
     * Merkt einen Entwurf fuer einen Lauf vor. {@code false} heisst: fuer diesen Entwurf ist schon einer
     * vorgemerkt oder unterwegs.
     *
     * <p>Das ist der einzige Punkt, an dem sich zwei gleichzeitige Starts sehen. {@link #isSolving(Long)}
     * allein reicht nicht: zwischen dieser Pruefung und {@link #start} liegt die Transaktion des
     * Aufrufers, und beide Anfragen kommen durch, solange keine committet hat. Gemessen 05.09.: zwei
     * Threads auf {@code POST /api/draft/solve}, der zweite bekam 500
     * ({@code IllegalStateException: The problemId (2) is already solving}) statt 409 {@code solving}.</p>
     *
     * <p>Wer vormerkt, muss auch wieder freigeben: {@link #start} raeumt die Vormerkung nach dem
     * Rueckruf weg, ein Aufrufer, dessen Lauf nicht zustande kommt, ruft {@link #release(Long)}.</p>
     */
    public boolean reserve(Long draftId) {
        return pending.add(draftId);
    }

    /** Gibt eine Vormerkung frei, deren Lauf nicht zustande kommt (zurueckgerollte Transaktion). */
    public void release(Long draftId) {
        pending.remove(draftId);
    }

    /**
     * Startet asynchron und kehrt sofort zurueck (HTTP 202). Das Problem wird hier, im Thread des
     * Aufrufers, aus dem {@code SolveInput} gebaut; der Solver-Thread bekommt fertige Records.
     *
     * @param onFinished bekommt das bis dahin beste Ergebnis — nach Ablauf der Rechenzeit oder nach
     *                   {@link #cancel(Long)}
     * @param onError    bekommt die Ausnahme, wenn der Lauf scheitert (dann wird {@code onFinished}
     *                   nicht gerufen)
     */
    public void start(Long draftId, SolveInput input, int seconds,
                      Consumer<SolveResult> onFinished, Consumer<Throwable> onError) {
        SolverTimetable problem = SolverTranslator.toSolution(input);
        // Wer schon vorgemerkt hat (SolveService), aendert damit nichts; wer direkt startet, merkt hier vor.
        pending.add(draftId);
        try {
            solverManager.solveBuilder()
                    .withProblemId(draftId)
                    .withProblem(problem)
                    .withFinalBestSolutionEventConsumer(event -> {
                        try {
                            onFinished.accept(SolverTranslator.toResult(event.solution()));
                        } finally {
                            pending.remove(draftId);
                        }
                    })
                    .withExceptionHandler((id, ex) -> {
                        try {
                            onError.accept(ex);
                        } finally {
                            pending.remove(draftId);
                        }
                    })
                    .withConfigOverride(new SolverConfigOverride().withTerminationSpentLimit(Duration.ofSeconds(seconds)))
                    .run();
        } catch (RuntimeException ex) {
            pending.remove(draftId);
            throw ex;
        }
    }

    /**
     * {@code true}, solange der Lauf wartet, rechnet ({@code SOLVING_SCHEDULED}, {@code SOLVING_ACTIVE}) oder
     * sein Ergebnis noch nicht fertig uebergeben ist (siehe Klassenkommentar).
     */
    public boolean isSolving(Long draftId) {
        return pending.contains(draftId) || solverManager.getSolverStatus(draftId) != SolverStatus.NOT_SOLVING;
    }

    /**
     * Beendet den Lauf vorzeitig; Timefold liefert danach das bisher beste Ergebnis an {@code onFinished}
     * (T-W8.1, {@code DELETE /api/draft/solve}). Kehrt sofort zurueck; {@link #isSolving(Long)} bleibt
     * {@code true}, bis das Ergebnis uebergeben ist. Laeuft nichts, passiert nichts.
     */
    public void cancel(Long draftId) {
        solverManager.terminateEarly(draftId);
    }

    /**
     * Blockierend — nur fuer den Beispieldatenbestand ({@code sampledata}, wenige Sekunden), nie aus einem
     * Controller mit wartendem Nutzer. Laeuft ueber denselben {@link SolverManager}; mit
     * {@code parallel-solver-count=1} wartet der Aufruf, bis ein laufender Entwurfs-Lauf fertig ist.
     */
    public SolveResult solveBlocking(Long problemId, SolveInput input, int seconds) {
        SolverJob<SolverTimetable> job = solverManager.solveBuilder()
                .withProblemId(problemId)
                .withProblem(SolverTranslator.toSolution(input))
                .withConfigOverride(new SolverConfigOverride().withTerminationSpentLimit(Duration.ofSeconds(seconds)))
                .run();
        try {
            return SolverTranslator.toResult(job.getFinalBestSolution());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Die Berechnung wurde unterbrochen.", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Die Berechnung ist gescheitert: " + ex.getCause().getMessage(), ex.getCause());
        }
    }
}
