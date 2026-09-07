package de.wsp3_1.solver;

/**
 * Gesperrte Zeit einer Lehrkraft (Tabelle {@code teacher_blocked_timeslot}). Immer hart: eine Stunde
 * dieser Lehrkraft in diesem Zeitfenster ist Constraint H4 {@code teacherBlockedTime} (T-W1.2).
 */
public record BlockedTimeFact(Long teacherId, Long timeslotId) {
}
