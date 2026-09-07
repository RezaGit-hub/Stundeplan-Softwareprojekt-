package de.wsp3_1.substitution.persistence;

import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.substitution.domain.ExceptionKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Eine datierte Ausnahme zu einer Stunde (W-6, W-7): Vertretung, Verlegung, Ausfall oder eine
 * zusaetzlich angesetzte Stunde. Tabelle {@code lesson_exception} aus Migration V4.
 *
 * <p><b>Sie aendert den Regelplan nicht.</b> Sie gilt fuer genau einen Kalendertag — am naechsten
 * Montag steht wieder, was im Plan steht. Je Stunde und Tag gibt es hoechstens eine Ausnahme
 * (UNIQUE {@code (lesson_id, date)}); eine zweite ist 409 {@code exception-exists}.</p>
 *
 * <p>Bei {@code ADDED} gibt es keine Regelplanstunde: {@code lessonId} ist leer, Klasse und Fach
 * stehen in {@code addedClassId}/{@code addedSubjectId}, Lehrkraft und Raum in den
 * {@code substitute…}-Spalten, Tag und Zeitfenster in {@code newDate}/{@code newTimeslotId}.</p>
 *
 * <p>Die Anlaesse stehen als reine Kennungsmenge ({@code @ElementCollection} auf der Verbindungstabelle
 * {@code exception_event}) — dieselbe Form wie die Faecher einer Lehrkraft in {@code masterdata}. Ein
 * geloeschtes Ereignis nimmt seine Zeile mit (ON DELETE CASCADE in V4), die Ausnahme bleibt bestehen
 * und verliert nur diesen Anlass.</p>
 */
@Entity
@Table(name = "lesson_exception")
public class LessonException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Die Stunde des Regelplans; leer nur bei {@code ADDED}. */
    @Column(name = "lesson_id")
    private Long lessonId;

    /** Der Kalendertag, fuer den die Ausnahme gilt. */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ExceptionKind kind;

    @Column(name = "substitute_teacher_id")
    private Long substituteTeacherId;

    @Column(name = "substitute_room_id")
    private Long substituteRoomId;

    /** Bei {@code MOVE} das Zieldatum, bei {@code ADDED} der Tag der Zusatzstunde. */
    @Column(name = "new_date")
    private LocalDate newDate;

    @Column(name = "new_timeslot_id")
    private Long newTimeslotId;

    @Column(name = "added_class_id")
    private Long addedClassId;

    @Column(name = "added_subject_id")
    private Long addedSubjectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    // SUBSELECT statt eines Selects je Zeile: die Wochenaufloesung liest die Ausnahmen eines Zeitraums
    // in einem Zug, und ohne diese Angabe holte Hibernate die Anlaesse jeder einzelnen Ausnahme mit
    // einer eigenen Abfrage nach — die Wochensicht wurde mit jeder je eingetragenen Vertretung eine
    // Abfrage langsamer (Befund 05.09.).
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "exception_event", joinColumns = @JoinColumn(name = "exception_id"))
    @Column(name = "event_id", nullable = false)
    private Set<Long> eventIds = new LinkedHashSet<>();

    protected LessonException() {
    }

    /** Vertretung, Verlegung oder Ausfall einer Regelplanstunde. */
    public static LessonException forLesson(Long lessonId, LocalDate date, ExceptionKind kind,
                                            Long substituteTeacherId, Long substituteRoomId,
                                            LocalDate newDate, Long newTimeslotId,
                                            Instant createdAt, UserAccount createdBy) {
        LessonException exception = new LessonException();
        exception.lessonId = lessonId;
        exception.date = date;
        exception.kind = kind;
        exception.substituteTeacherId = substituteTeacherId;
        exception.substituteRoomId = substituteRoomId;
        exception.newDate = newDate;
        exception.newTimeslotId = newTimeslotId;
        exception.createdAt = createdAt;
        exception.createdBy = createdBy;
        return exception;
    }

    /** Die Zusatzstunde eines Ereignisses {@code EXTRA_LESSON} — ohne Regelplanstunde. */
    public static LessonException added(LocalDate date, Long timeslotId, Long classId, Long subjectId,
                                        Long teacherId, Long roomId, Instant createdAt,
                                        UserAccount createdBy) {
        LessonException exception = new LessonException();
        exception.date = date;
        exception.kind = ExceptionKind.ADDED;
        exception.newDate = date;
        exception.newTimeslotId = timeslotId;
        exception.addedClassId = classId;
        exception.addedSubjectId = subjectId;
        exception.substituteTeacherId = teacherId;
        exception.substituteRoomId = roomId;
        exception.createdAt = createdAt;
        exception.createdBy = createdBy;
        return exception;
    }

    public Long getId() {
        return id;
    }

    public Long getLessonId() {
        return lessonId;
    }

    /**
     * Setzt die Ausnahme auf die entsprechende Stunde des neuen Plans um (Befund PFLICHTENHEFT-9).
     *
     * <p>Der einzige Aufrufer ist {@code ExpiringExceptionsCounter.carryOver} beim Annehmen eines
     * Entwurfs. Sonst bleibt die Zuordnung fest: eine Ausnahme gehoert zu genau der Stunde, fuer die
     * sie eingetragen wurde. Die Kennung darf nur auf eine Stunde zeigen, die dieselbe Fachzuordnung,
     * dasselbe Zeitfenster und denselben Raum hat — sonst stimmen die Angaben der Vertretung nicht mehr.</p>
     */
    public void moveToLesson(Long newLessonId) {
        if (this.lessonId == null) {
            throw new IllegalStateException("Eine Zusatzstunde haengt an keiner Stunde und wird nicht umgehaengt.");
        }
        this.lessonId = newLessonId;
    }

    public LocalDate getDate() {
        return date;
    }

    public ExceptionKind getKind() {
        return kind;
    }

    public Long getSubstituteTeacherId() {
        return substituteTeacherId;
    }

    public Long getSubstituteRoomId() {
        return substituteRoomId;
    }

    public LocalDate getNewDate() {
        return newDate;
    }

    public Long getNewTimeslotId() {
        return newTimeslotId;
    }

    public Long getAddedClassId() {
        return addedClassId;
    }

    public Long getAddedSubjectId() {
        return addedSubjectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public Set<Long> getEventIds() {
        return eventIds;
    }
}
