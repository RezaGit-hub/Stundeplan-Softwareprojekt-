package de.wsp3_1.solver;

/**
 * Gesperrte Zeit eines Raums (Tabelle {@code room_blocked_timeslot}, V9, #133). Immer hart: eine Stunde
 * in diesem Raum in diesem Zeitfenster ist Constraint H8 {@code roomBlockedTime}. Eigener Record neben
 * {@link BlockedTimeFact}, damit H4 und der Lehrkraft-Fact unveraendert bleiben (E-54).
 */
public record RoomBlockedTimeFact(Long roomId, Long timeslotId) {
}
