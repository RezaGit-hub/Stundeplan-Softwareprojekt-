package de.wsp3_1.masterdata.persistence;

import de.wsp3_1.shared.Weekday;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/**
 * Ein Zeitfenster des Stundenrasters. {@code position} ist die laufende Nummer am Tag und zaehlt
 * Pausen mit; die angezeigte Stundennummer ergibt sich aus {@code isBreak = false} und wird nicht
 * gespeichert. Eindeutig ist das Paar (Tag, Position) — daran haengt auch der Upsert in
 * {@code PUT /api/timegrid}.
 */
@Entity
@Table(name = "timeslot")
public class Timeslot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day", nullable = false)
    private Weekday day;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_break", nullable = false)
    private boolean isBreak;

    protected Timeslot() {
    }

    public Timeslot(Weekday day, int position, LocalTime startTime, LocalTime endTime, boolean isBreak) {
        this.day = day;
        this.position = position;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isBreak = isBreak;
    }

    public Long getId() {
        return id;
    }

    public Weekday getDay() {
        return day;
    }

    public int getPosition() {
        return position;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isBreak() {
        return isBreak;
    }

    public void setBreak(boolean isBreak) {
        this.isBreak = isBreak;
    }
}
