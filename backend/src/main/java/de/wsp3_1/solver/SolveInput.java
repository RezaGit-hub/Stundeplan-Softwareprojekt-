package de.wsp3_1.solver;

import java.util.List;

/**
 * Die Eingabe eines Solver-Laufs — nur Records, kein JPA (ArchUnit-Regel 1). Gebaut vom Modul
 * {@code planning} ({@code SolveService.buildInput}) in der Transaktion des Requests; der Solver-Thread
 * sieht keine Datenbank mehr (docs/SOLVER-PLAN.md Abschnitt 6).
 *
 * @param lessons        alle Stunden des Entwurfs, auch die inaktiver Klassen (sie belegen Zeit und Raum)
 * @param timeslots      <b>alle</b> Zeitfenster des Rasters, Pausen eingeschlossen — der Uebersetzer nimmt
 *                       nur {@code isBreak == false} in den Wertebereich, braucht die Pausen aber, um eine
 *                       von Hand in die Pause gesetzte Stunde ueberhaupt darstellen zu koennen
 * @param rooms          <b>alle</b> Raeume; nur {@code active == true} kommen in den Wertebereich
 * @param rules          aufgeloeste, aktive Regeln je Objekt (RuleResolver)
 * @param blockedTimes   gesperrte Zeiten aller Lehrkraefte, die in {@code lessons} vorkommen
 * @param preferredTimes bevorzugte Zeiten, analog
 */
public record SolveInput(List<LessonInput> lessons,
                         List<TimeslotFact> timeslots,
                         List<RoomFact> rooms,
                         List<RuleFact> rules,
                         List<BlockedTimeFact> blockedTimes,
                         List<PreferredTimeFact> preferredTimes) {

    /**
     * Eine Stunde, wie sie gespeichert ist. {@code timeslotId} und {@code roomId} sind beide gesetzt
     * oder beide {@code null} („belegt oder offen"). {@code pinned = true} laesst der Solver stehen —
     * das gilt fuer festgelegte Stunden (W-9) und, bei der Kann-Option {@code classId}, fuer alle
     * Stunden anderer Klassen (nur im Eingaberecord, nicht in der Datenbank).
     */
    public record LessonInput(Long id, Long classId, Long subjectId, Long teacherId, String requiredRoomType,
                              boolean pinned, Long timeslotId, Long roomId) {
    }
}
