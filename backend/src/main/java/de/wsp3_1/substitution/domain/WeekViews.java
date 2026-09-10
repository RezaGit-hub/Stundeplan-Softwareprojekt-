package de.wsp3_1.substitution.domain;

import de.wsp3_1.shared.Weekday;

import java.time.LocalDate;
import java.util.List;

/**
 * Die aufgeloeste Kalenderwoche, wie der {@link WeekResolver} sie herausgibt — reine Records ohne JPA.
 * Der Controller bildet sie in {@code substitution/api/SubstitutionDtos} auf die Vertragsform ab.
 */
public final class WeekViews {

    private WeekViews() {
    }

    /**
     * Die geaenderten Werte einer Stunde: bei einer Vertretung Lehrkraft und Raum von vorher, bei einer
     * Verlegung das urspruengliche Datum und die urspruengliche Position. In Teil 1 immer {@code null}.
     */
    public record OriginalView(Long teacherId, String teacherShort, Long roomId, String roomShort,
                               LocalDate date, Integer position) {
    }

    /**
     * Eine Stunde an einem Kalendertag, fertig aufgeloest — Web und App faerben und beschriften nur
     * (ui.md). {@code lessonId} ist nur bei einer Zusatzstunde leer.
     *
     * <p>{@code subjectId} steht nicht im Vertrag und geht deshalb nicht in {@code EntryDto}: die
     * Oberflaeche braucht nur das Kuerzel und die Farbe. Die Konfliktpruefung einer Vertretung braucht
     * dagegen die Kennung, um die Raumanforderung des Fachs zu pruefen.</p>
     */
    public record EntryView(LocalDate date, Long timeslotId, Weekday day, Integer position, Long lessonId,
                            Long classId, String classShort, Long subjectId, String subjectShort,
                            String subjectColor, Long teacherId, String teacherShort, Long roomId,
                            String roomShort, EntryKind kind, OriginalView original, List<String> reasons,
                            Long exceptionId) {
    }

    /** Ein Tag der Woche. {@code schoolDay false} heisst unterrichtsfrei: dann ist {@code entries} leer. */
    public record DayView(LocalDate date, boolean schoolDay, List<EntryView> entries) {
    }

    /** Die Woche ab Montag; die Tage sind die Unterrichtstage des Rasters. */
    public record WeekView(LocalDate monday, List<DayView> days) {
    }
}
