package de.wsp3_1.solver;

/**
 * Ein Raum als unveraenderliche Tatsache fuer den Solver. {@code roomType} ist Freitext, Abgleich exakt.
 *
 * <p>{@code active} ergaenzt in Bauphase backend2: {@code SolveInput} traegt alle Raeume, der Uebersetzer
 * nimmt nur aktive in den Wertebereich. Ein inaktiver Raum kann trotzdem an einer gespeicherten
 * (z. B. festgelegten) Stunde haengen — dann braucht der Solver seine Raumart fuer H5.</p>
 */
public record RoomFact(Long id, String roomType, boolean active) {
}
