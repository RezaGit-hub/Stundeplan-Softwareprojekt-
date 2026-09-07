package de.wsp3_1.planning.domain;

/**
 * Was der Entwurf gerade tut. Wird bei jedem Lesen bestimmt, nicht gespeichert
 * ({@code docs/SOLVER-PLAN.md} Abschnitt 8):
 * {@code SOLVING}, solange der Solver an diesem Entwurf rechnet; sonst {@code FAILED}, wenn der letzte
 * Lauf mit einem Fehler endete; sonst {@code EDITING}.
 */
public enum DraftStatus {
    EDITING,
    SOLVING,
    FAILED
}
