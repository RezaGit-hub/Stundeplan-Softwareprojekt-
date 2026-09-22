package de.wsp3_1.substitution.persistence;

import de.wsp3_1.substitution.domain.SubstitutionRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Eine Vertretungsanfrage an eine Lehrkraft (V14). Eine Zeile je Anfrage; {@code status} laeuft
 * {@code UNKNOWN -> ACCEPTED/DECLINED}. Wie {@code change_event.kind} (V4) als {@code varchar} mit
 * CHECK-Liste und {@link EnumType#STRING} — {@code ORDINAL} wuerde 0/1/2 schreiben, und jede
 * Umsortierung des Enums kippte die Daten.
 */
@Entity
@Table(name = "substitution_request")
public class SubstitutionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubstitutionRequestStatus status = SubstitutionRequestStatus.UNKNOWN;

    protected SubstitutionRequest() {
    }

    public SubstitutionRequest(Long teacherId, Long eventId) {
        this.teacherId = teacherId;
        this.eventId = eventId;
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public Long getEventId() { return eventId; }
    public SubstitutionRequestStatus getStatus() { return status; }
    public void setStatus(SubstitutionRequestStatus status) { this.status = status; }
}