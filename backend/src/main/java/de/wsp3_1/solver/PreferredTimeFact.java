package de.wsp3_1.solver;

/**
 * Bevorzugte Zeit einer Lehrkraft (Tabelle {@code teacher_preferred_timeslot}). Ein Wunsch, keine Pflicht:
 * eine Stunde dieser Lehrkraft in diesem Zeitfenster wird von S1 {@code teacherPreferredTime} belohnt.
 */
public record PreferredTimeFact(Long teacherId, Long timeslotId) {
}
