package de.wsp3_1.planning.domain;

import java.util.List;

/**
 * Ein Vorschlag, wie sich ein Konflikt im Entwurf aufloesen laesst (#110, E-53). Der Vorschlag ist eine
 * Vorschau: nichts davon ist gespeichert. Uebernommen wird er Stunde fuer Stunde ueber
 * {@code PATCH /api/draft/lessons/{id}} — genau die {@code moves}.
 *
 * @param level              Stufe des Freiheitsgrads: 1 = nur die Konfliktstunde, 2 = dazu die Stunden
 *                           derselben Klasse am selben Tag, 3 = dazu alle Stunden der beteiligten
 *                           Lehrkraft und des Raums an diesem Tag
 * @param label              deutscher Text der Stufe fuer die Oberflaeche
 * @param moves              die Stunden, deren Lage sich gegenueber dem Entwurf aendert (nie leer)
 * @param hardConflictsAfter harte Konflikte im ganzen Entwurf, wenn der Vorschlag uebernommen ist
 * @param score              Timefold-Score des Laufs als Text, z. B. {@code "0hard/0medium/-2soft"}
 */
public record RepairProposal(int level, String label, List<RepairMove> moves, int hardConflictsAfter,
                             String score) {

    /** Eine Stunde wandert in dieses Zeitfenster und diesen Raum. */
    public record RepairMove(Long lessonId, Long timeslotId, Long roomId) {
    }
}
