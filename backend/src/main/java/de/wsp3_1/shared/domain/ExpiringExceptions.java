package de.wsp3_1.shared.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Was ein Planwechsel mit den datierten Ausnahmen macht — die eine Frage, die {@code planning} dem
 * Modul {@code substitution} stellen muss.
 *
 * <p>Die Modultabelle (VERTRAG Abschnitt 4) erlaubt {@code planning → substitution} nicht: die
 * Vertretung baut auf der Planung auf, nicht umgekehrt. Bis 05.09. las {@code DraftService} die fremde
 * Tabelle {@code lesson_exception} deshalb mit {@link org.springframework.jdbc.core.JdbcTemplate} —
 * ArchUnit sah die Grenzueberschreitung nicht, weil rohes SQL keine Java-Abhaengigkeit ist.</p>
 *
 * <p>Jetzt zeigen beide Module auf {@code shared}: {@code planning} kennt nur diese Schnittstelle,
 * {@code substitution} liefert die Umsetzung. Damit bleibt die Richtung der Modultabelle gewahrt, die
 * Zaehlung steht dort, wo die Tabelle hingehoert, und sie ist eine gewoehnliche Repository-Abfrage
 * statt handgeschriebenem SQL.</p>
 */
public interface ExpiringExceptions {

    /**
     * Wie viele Ausnahmen ab {@code from} ein Planwechsel verfallen liesse — <b>ohne</b> etwas zu aendern.
     *
     * <p>Das ist die Vorschau, die der Entwurf mitliefert: der Annehmen-Dialog nennt dieselbe Zahl, die
     * der Server hinterher meldet, und verlangt die Bestaetigung nur, wenn wirklich etwas verloren geht.
     * Zusatzstunden zaehlen nicht mit: sie haengen an keiner Stunde ({@code lesson_id} ist leer, V4) und
     * ueberleben jeden Planwechsel.</p>
     *
     * @param affectedDay wie bei {@link #carryOver}
     * @param sameLesson  wie bei {@link #carryOver}: alte Stunden-Kennung → neue, fuer unveraenderte Stunden
     */
    long countExpiring(LocalDate from, Predicate<LocalDate> affectedDay, Map<Long, Long> sameLesson);

    /**
     * Haengt die datierten Ausnahmen ab {@code from} vom alten Plan auf den neuen um und liefert die
     * Zahl derer, die dabei keine Entsprechung gefunden haben — genau die verfallen wirklich.
     *
     * <p><b>Warum es das braucht</b> (Befund PFLICHTENHEFT-9, 05.09.): Eine Ausnahme zeigt mit
     * {@code lesson_id} auf <i>eine</i> Zeile der Tabelle {@code lesson}, und {@code COPY_ACTIVE} legt
     * beim Anlegen eines Entwurfs neue Zeilen mit neuen Kennungen an. Nach dem Annehmen zeigte jede
     * Vertretung damit auf eine Stunde, die zu keinem geltenden Plan mehr gehoert; die Wochensicht
     * ordnet ausschliesslich ueber diese Kennung zu und liess sie verschwinden. Eine Grunddatenkorrektur
     * — der Muss-Ablauf des Pflichtenhefts — loeschte so den gesamten Vertretungsplan der Folgewochen.</p>
     *
     * <p>Umgehaengt wird nur, was inhaltlich <b>dieselbe</b> Stunde ist: gleiche Fachzuordnung, gleiches
     * Zeitfenster, gleicher Raum. Verschiebt der neue Plan die Stunde oder faellt sie weg, bleibt es beim
     * Verfall — die Angabe „statt Frau Meyer, Raum R204" waere sonst falsch. Die Zuordnung stellt
     * {@code planning} auf; dieses Modul kennt die Stunden nicht, sondern nur ihre Kennungen.</p>
     *
     * @param from        fruehester Tag, ab dem der neue Plan gilt (Gueltigkeitsbeginn, fruehestens heute).
     *                    Fruehere Ausnahmen bleiben unangetastet: fuer diese Tage gilt der bisherige Plan weiter.
     * @param affectedDay ob der getauschte Plan an diesem Tag ueberhaupt gilt. „Ab {@code from}" allein reicht
     *                    nicht: ein <b>archivierter</b> Plan mit spaeterem Gueltigkeitsbeginn gilt weiter
     *                    (E-40, {@code PlanReader.timetableIdAt} — „gilt A heute, B ab 21.09., C ab 28.09."),
     *                    und an dessen Tagen haengt die Ausnahme schon am richtigen Plan. Sie bleibt dann
     *                    unangetastet und zaehlt auch nicht als verfallend.
     * @param sameLesson  alte Stunden-Kennung → neue Stunden-Kennung, nur fuer unveraenderte Stunden
     * @return wie viele Ausnahmen ab {@code from} verfallen, weil ihre Stunde im neuen Plan fehlt
     */
    long carryOver(LocalDate from, Predicate<LocalDate> affectedDay, Map<Long, Long> sameLesson);
}
