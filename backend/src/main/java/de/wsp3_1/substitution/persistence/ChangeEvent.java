package de.wsp3_1.substitution.persistence;

import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.substitution.domain.EventKind;
import jakarta.persistence.Column;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ein Aenderungsereignis (W-5): Ausfall einer Lehrkraft, Sperrung eines Raums oder eine angesetzte
 * Zusatzstunde. Tabelle {@code change_event} aus Migration V4.
 *
 * <p><b>Das Ereignis aendert den Regelplan nicht.</b> Es haengt am Kalendertag und sagt nur, was an
 * diesem Tag anders ist; welche Stunden es trifft, rechnet der {@code WeekResolver} bei jedem Lesen
 * neu aus (E-31). Deshalb steht hier kein Verweis auf eine Stunde.</p>
 *
 * <p>Die Felder einer Zusatzstunde ({@code EXTRA_LESSON}) stehen nicht hier, sondern in der Ausnahme
 * der Art {@code ADDED}, die {@code ChangeEventService} zusammen mit dem Ereignis anlegt: eine
 * Zusatzstunde ist eine Stunde, und Stunden eines Kalendertags stehen in {@code lesson_exception}.</p>
 */
@Entity
@Table(name = "change_event")
public class ChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private EventKind kind;

    /** Nur bei {@code TEACHER_ABSENCE} gesetzt (CHECK in V4). */
    @Column(name = "teacher_id")
    private Long teacherId;

    /** Nur bei {@code ROOM_BLOCKED} gesetzt (CHECK in V4). */
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Wer das Ereignis erfasst hat. Als Beziehung, weil die Antwort den Benutzernamen zeigt —
     * die Kante {@code substitution → auth} steht dafuer in der Modultabelle (VERTRAG Abschnitt 4, D-3).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    protected ChangeEvent() {
    }

    public ChangeEvent(EventKind kind, Long teacherId, Long roomId, LocalDate dateFrom, LocalDate dateTo,
                       String reason, Instant createdAt, UserAccount createdBy) {
        this.kind = kind;
        this.teacherId = teacherId;
        this.roomId = roomId;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.reason = reason;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public EventKind getKind() {
        return kind;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }
}
