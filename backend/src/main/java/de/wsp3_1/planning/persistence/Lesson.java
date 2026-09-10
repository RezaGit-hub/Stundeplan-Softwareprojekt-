package de.wsp3_1.planning.persistence;

import de.wsp3_1.masterdata.persistence.ClassSubject;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.Timeslot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Eine Unterrichtsstunde im Wochenmuster eines Plans (Tabelle {@code lesson}, Migration V3).
 *
 * <p>Klasse, Fach <b>und</b> Lehrkraft stehen in der Fachzuordnung {@link ClassSubject} (E-18) — die
 * Stunde traegt keine eigene Lehrkraft. Veraenderlich sind nur Zeitfenster, Raum und die Festlegung.</p>
 *
 * <p><b>Invariante „belegt oder offen"</b> (T-W8.1): entweder sind Zeitfenster und Raum beide gesetzt
 * oder beide leer. Die Datenbank erzwingt das (CHECK {@code lesson_assigned_or_open}); die Anwendung
 * pruefen es vorher, damit der Client 400 statt 500 bekommt.</p>
 */
@Entity
@Table(name = "lesson")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_subject_id", nullable = false)
    private ClassSubject classSubject;

    /** festgelegt (W-9) — der Solver laesst Zeit und Raum dieser Stunde stehen. */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id")
    private Timeslot timeslot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    protected Lesson() {
    }

    public Lesson(Timetable timetable, ClassSubject classSubject, boolean pinned, Timeslot timeslot, Room room) {
        this.timetable = timetable;
        this.classSubject = classSubject;
        this.pinned = pinned;
        this.timeslot = timeslot;
        this.room = room;
    }

    public Long getId() {
        return id;
    }

    public Timetable getTimetable() {
        return timetable;
    }

    public ClassSubject getClassSubject() {
        return classSubject;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Timeslot getTimeslot() {
        return timeslot;
    }

    public Room getRoom() {
        return room;
    }

    /** Zeitfenster und Raum aendern sich nur gemeinsam — beide gesetzt („belegt") oder beide leer („offen"). */
    public void assign(Timeslot timeslot, Room room) {
        if ((timeslot == null) != (room == null)) {
            throw new IllegalArgumentException("Zeitfenster und Raum gehoeren zusammen: beide gesetzt oder beide leer.");
        }
        this.timeslot = timeslot;
        this.room = room;
    }
}
