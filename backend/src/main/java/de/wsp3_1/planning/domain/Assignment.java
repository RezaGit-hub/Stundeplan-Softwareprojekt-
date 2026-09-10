package de.wsp3_1.planning.domain;

/**
 * Eine Belegung, wie die Konfliktpruefung sie sieht: nur Kennungen, keine Entities. Damit laesst sich
 * der {@link ConflictChecker} ohne Datenbank und ohne Spring pruefen (Teststrategie Ebene 1) — und das
 * Modul {@code substitution} kann spaeter dieselbe Pruefung auf eine gedachte Vertretung anwenden,
 * ohne sie vorher zu speichern.
 *
 * <p>{@code timeslotId} und {@code roomId} sind beide gesetzt („belegt") oder beide leer („offen").
 * Offene Stunden erzeugen nie einen Konflikt.</p>
 */
public record Assignment(Long lessonId, Long classId, Long subjectId, Long teacherId,
                         Long roomId, Long timeslotId, boolean pinned) {

    public boolean isPlaced() {
        return timeslotId != null;
    }

    /** Die gespeicherte Stunde als Belegung. Klasse, Fach und Lehrkraft kommen aus der Fachzuordnung (E-18). */
    public static Assignment of(de.wsp3_1.planning.persistence.Lesson lesson) {
        return new Assignment(lesson.getId(),
                lesson.getClassSubject().getSchoolClass().getId(),
                lesson.getClassSubject().getSubject().getId(),
                lesson.getClassSubject().getTeacher().getId(),
                lesson.getRoom() == null ? null : lesson.getRoom().getId(),
                lesson.getTimeslot() == null ? null : lesson.getTimeslot().getId(),
                lesson.isPinned());
    }
}
