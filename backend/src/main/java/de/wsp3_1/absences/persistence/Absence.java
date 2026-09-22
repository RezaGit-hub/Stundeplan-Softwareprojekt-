package de.wsp3_1.absences.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Eine Abwesenheitsmeldung einer Lehrkraft (V13). Zeitraum [dateFrom, dateTo], beide Grenzen
 * einschliesslich. {@code active=false}: die Lehrkraft hat die Meldung zurueckgenommen.
 * {@code read}: von der Planung noch nicht gesehen — neue und bearbeitete Meldungen sind ungelesen.
 */
@Entity
@Table(name = "absence")
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "seen", nullable = false)
    private boolean seen = false;

    @Column(name = "event_id")
    private Long eventId;

    protected Absence() {
    }

    public Absence(Long teacherId, LocalDate dateFrom, LocalDate dateTo, String reason,
                   boolean active, boolean seen) {
        this.teacherId = teacherId;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.reason = reason;
        this.active = active;
        this.seen = seen;
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
}