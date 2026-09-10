package de.wsp3_1.changelog.persistence;

import de.wsp3_1.changelog.domain.ChangelogAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ein Eintrag im Aenderungsverlauf (W-7, Tabelle {@code changelog_entry} aus V4).
 *
 * <p><b>{@link Immutable}</b>: ein Eintrag wird geschrieben und gelesen, nie geaendert. Es gibt weder
 * {@code PUT} noch {@code DELETE} auf {@code /api/changelog} (T-W7.1); die Annotation haelt die Regel
 * auch dann, wenn jemand versehentlich einen Setter baut — Hibernate schreibt dann einfach nichts.</p>
 *
 * <p>Die Belegungen stehen als <b>Text-Schnappschuss</b> ({@code oldAssignment}, {@code newAssignment}),
 * damit der Eintrag lesbar bleibt, wenn eine Lehrkraft spaeter deaktiviert oder ein Raum umbenannt wird.
 * {@code lessonId}, {@code classId}, {@code teacherId} und {@code userId} sind bewusst nur Spalten und
 * keine JPA-Beziehungen: die Fremdschluessel stehen in V4, aber die Entities gehoeren anderen Modulen,
 * die {@code changelog} laut Modultabelle nicht benutzen darf.</p>
 */
@Entity
@Immutable
@Table(name = "changelog_entry")
public class ChangelogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private ChangelogAction action;

    /** Datum der betroffenen Stunde; bei {@code TIMETABLE_ACCEPTED} der Gueltigkeitsbeginn oder leer. */
    @Column(name = "date")
    private LocalDate date;

    @Column(name = "position")
    private Integer position;

    @Column(name = "old_assignment", length = 255)
    private String oldAssignment;

    @Column(name = "new_assignment", length = 255)
    private String newAssignment;

    /**
     * Alle Anlaesse, mit „; " getrennt; nie {@code null}, sondern leer. Ohne Laengengrenze
     * ({@code text}, Migration V6): ein Anlass ist Freitext bis 255 Zeichen, und eine Stunde darf
     * mehrere tragen — bei fuenf waere varchar(1000) zu schmal, und der Verlauf verlore ganze Anlaesse.
     */
    @Column(name = "reasons", columnDefinition = "text")
    private String reasons;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "teacher_id")
    private Long teacherId;

    protected ChangelogEntry() {
    }

    public ChangelogEntry(Instant timestamp, ChangelogAction action, LocalDate date, Integer position,
                          String oldAssignment, String newAssignment, String reasons,
                          Long userId, Long lessonId, Long classId, Long teacherId) {
        this.timestamp = timestamp;
        this.action = action;
        this.date = date;
        this.position = position;
        this.oldAssignment = oldAssignment;
        this.newAssignment = newAssignment;
        this.reasons = reasons;
        this.userId = userId;
        this.lessonId = lessonId;
        this.classId = classId;
        this.teacherId = teacherId;
    }

    public Long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ChangelogAction getAction() {
        return action;
    }

    public LocalDate getDate() {
        return date;
    }

    public Integer getPosition() {
        return position;
    }

    public String getOldAssignment() {
        return oldAssignment;
    }

    public String getNewAssignment() {
        return newAssignment;
    }

    public String getReasons() {
        return reasons;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Long getClassId() {
        return classId;
    }

    public Long getTeacherId() {
        return teacherId;
    }
}
