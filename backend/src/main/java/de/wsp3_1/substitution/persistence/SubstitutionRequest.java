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

import java.time.LocalDate;

/**
 * Eine Vertretungsanfrage an eine Lehrkraft (V14/V15). Sie haengt an einer konkreten
 * ausfallenden Stunde — (lesson_id, date) — und nicht an einem Ereignis: ein change_event
 * betrifft mehrere Stunden, mehrere Ereignisse koennen dieselbe Stunde offen machen, und
 * pending-Rows sind berechnet (E-31), haben also keine Kennung.
 *
 * <p>{@code status} laeuft {@code UNKNOWN -> ACCEPTED/DECLINED} und bleibt danach stehen,
 * damit die Planung sieht, wen sie gefragt hat und wer nicht geantwortet hat.</p>
 *
 * <p>Anzeigefelder (Klasse, Fach, Position, Raum, ausfallende Lehrkraft) stehen NICHT in der
 * Datenbank. Der Dienst liest sie beim Anzeigen aus dem {@code WeekResolver} (pending-Sicht)
 * und setzt sie im DTO zusammen — dieselbe Bauweise wie bei den berechneten Konflikten.</p>
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

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubstitutionRequestStatus status = SubstitutionRequestStatus.UNKNOWN;

    protected SubstitutionRequest() {
    }

    public SubstitutionRequest(Long teacherId, Long lessonId, LocalDate date) {
        this.teacherId = teacherId;
        this.lessonId = lessonId;
        this.date = date;
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public Long getLessonId() { return lessonId; }
    public LocalDate getDate() { return date; }
    public SubstitutionRequestStatus getStatus() { return status; }
    public void setStatus(SubstitutionRequestStatus status) { this.status = status; }
}