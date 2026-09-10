package de.wsp3_1.solver;

import java.util.List;

/**
 * Das Ergebnis eines Solver-Laufs: je Stunde die neue Lage und der Timefold-Score als Text
 * (z. B. {@code "0hard/0medium/-2soft"}). {@code planning.DraftService.storeResult} schreibt daraus
 * nur die Stunden mit {@code pinned = false} zurueck.
 */
public record SolveResult(List<Placement> placements, String score) {

    /**
     * Lage einer Stunde nach dem Lauf. Entweder sind {@code timeslotId} und {@code roomId} beide gesetzt
     * oder beide {@code null} (offen) — der Uebersetzer stellt das sicher, nicht erst die Datenbank.
     */
    public record Placement(Long lessonId, Long timeslotId, Long roomId) {
    }
}
