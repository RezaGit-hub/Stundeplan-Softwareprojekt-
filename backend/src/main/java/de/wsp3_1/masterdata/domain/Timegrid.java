package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.persistence.Timeslot;
import de.wsp3_1.shared.Weekday;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die zwei Rechnungen, die jedes Modul am Stundenraster braucht: die Reihenfolge und die angezeigte
 * Stundennummer. Beide standen bis zum 05.09. mehrfach im Code — die Reihenfolge viermal
 * ({@code TimegridService}, {@code planning.ConflictService}, {@code planning.SolveService},
 * {@code substitution.WeekResolver}), die Nummerierung zweimal wortgleich. Wer die Nummerierung
 * aendert (zum Beispiel fuer Doppelstunden), aendert sie hier einmal; sonst zeigen Planung und
 * Vertretung verschiedene Stundennummern fuer dieselbe Stunde.
 *
 * <p>Das Modul {@code masterdata} ist der richtige Ort: das Raster gehoert ihm, und {@code planning}
 * wie {@code substitution} duerfen es benutzen (VERTRAG Abschnitt 4, Tabelle der Modulabhaengigkeiten).
 * Die Records bleiben getrennt — {@code planning.CheckContext.SlotInfo} fuehrt zusaetzlich die
 * Uhrzeitspanne fuer den Text der Pausenregel, {@code substitution.DayFacts.SlotFact} nicht.</p>
 */
public final class Timegrid {

    private Timegrid() {
    }

    /**
     * Raster-Reihenfolge: Montag zuerst, innerhalb des Tages nach Position. Bewusst in Java und nicht
     * per {@code ORDER BY day}: der Wochentag steht als Text in der Datenbank, und alphabetisch kaeme
     * FR vor MO. Sortiert wird nach der Reihenfolge des Enums {@link Weekday}.
     */
    public static final Comparator<Timeslot> GRID_ORDER =
            Comparator.<Timeslot>comparingInt(slot -> slot.getDay().ordinal())
                    .thenComparingInt(Timeslot::getPosition);

    /** Dieselben Zeitfenster in Raster-Reihenfolge. */
    public static List<Timeslot> sortedByGrid(List<Timeslot> slots) {
        return slots.stream().sorted(GRID_ORDER).toList();
    }

    /**
     * Die angezeigte Stundennummer je Zeitfenster. Sie wird gezaehlt und nie gespeichert: sie ist die
     * laufende Nummer der Nicht-Pausen des Tages (wie {@code lessonNumber} in
     * {@code frontend/src/lib/timegrid.ts}). Bei der grossen Pause an Position 3 ist Position 4 die
     * 3. Stunde.
     *
     * <p>Pausen stehen nicht in der Karte — {@code get} liefert fuer sie {@code null}, genau wie fuer
     * ein unbekanntes Zeitfenster.</p>
     */
    public static Map<Long, Integer> lessonNumbers(List<Timeslot> slots) {
        Map<Long, Integer> numbers = new LinkedHashMap<>();
        Weekday currentDay = null;
        int lessonNumber = 0;
        for (Timeslot slot : sortedByGrid(slots)) {
            if (slot.getDay() != currentDay) {
                currentDay = slot.getDay();
                lessonNumber = 0;
            }
            if (!slot.isBreak()) {
                lessonNumber++;
                numbers.put(slot.getId(), lessonNumber);
            }
        }
        return numbers;
    }
}
