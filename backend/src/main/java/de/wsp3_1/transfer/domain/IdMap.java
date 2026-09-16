package de.wsp3_1.transfer.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Alte Kennung (im Dokument) → neue Kennung (in der Datenbank). Waehrend des Imports wird jeder
 * Verweis durch diese Zuordnung gezogen, weil jede Zeile neu angelegt wird und damit eine neue
 * Kennung bekommt.
 *
 * <p>Eine nicht eingetragene Kennung ist ein Programmfehler: {@code validateReferences} hat
 * vorher geprueft, dass jede Kennung im Dokument eine Entsprechung hat. Deshalb
 * {@link IllegalStateException} und keine 400.</p>
 */
final class IdMap {

    private final Map<Long, Long> subjects = new HashMap<>();
    private final Map<Long, Long> teachers = new HashMap<>();
    private final Map<Long, Long> rooms = new HashMap<>();
    private final Map<Long, Long> classes = new HashMap<>();
    private final Map<Long, Long> timeslots = new HashMap<>();

    void subject(Long alt, Long neu) { subjects.put(alt, neu); }
    void teacher(Long alt, Long neu) { teachers.put(alt, neu); }
    void room(Long alt, Long neu) { rooms.put(alt, neu); }
    void klass(Long alt, Long neu) { classes.put(alt, neu); }
    void timeslot(Long alt, Long neu) { timeslots.put(alt, neu); }

    Long subject(Long alt) { return require(subjects, alt, "Fach"); }
    Long teacher(Long alt) { return require(teachers, alt, "Lehrkraft"); }
    Long room(Long alt) { return require(rooms, alt, "Raum"); }
    Long klass(Long alt) { return require(classes, alt, "Klasse"); }
    Long timeslot(Long alt) { return require(timeslots, alt, "Zeitfenster"); }

    private static Long require(Map<Long, Long> map, Long alt, String was) {
        Long neu = map.get(alt);
        if (neu == null) {
            throw new IllegalStateException(was + " mit Kennung " + alt + " wurde nicht angelegt.");
        }
        return neu;
    }
}