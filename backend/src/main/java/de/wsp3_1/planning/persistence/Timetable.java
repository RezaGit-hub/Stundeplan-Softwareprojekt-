package de.wsp3_1.planning.persistence;

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
 * Ein Plan — Entwurf, aktiver Plan oder Archiv (Tabelle {@code timetable}, Migration V3, V5, V11, V12).
 *
 * <p>Seit V11 (#103) darf es mehrere Entwuerfe nebeneinander geben; {@code name} unterscheidet sie in der
 * Oberflaeche. Aktive und archivierte Plaene tragen keinen Namen.</p>
 *
 * <p>Seit V12 (#115) darf es auch mehrere <b>aktive</b> Plaene geben — mit getrennten Zeitraeumen
 * ({@code validFrom}/{@code validUntil}). {@code ARCHIVED} heisst seitdem „ersetzt oder abgelaufen".</p>
 *
 * <p>Konflikte stehen bewusst nicht hier: sie werden bei jedem Lesen neu berechnet (E-31). Die vier
 * {@code solve*}-Spalten sind nur Spur des letzten Laufs; ob gerade gerechnet wird, weiss allein der
 * {@code SolverRunner} — es gibt keinen Zustandsautomaten in der Datenbank
 * ({@code docs/SOLVER-PLAN.md} Abschnitt 8).</p>
 */
@Entity
@Table(name = "timetable")
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TimetableStatus status;

    /** Name eines Entwurfs („Entwurf 1", „Variante B"); bei aktiven und archivierten Plaenen leer (V11). */
    @Column(name = "name", length = 80)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Zeitpunkt der Annahme; bei einem Entwurf immer leer (CHECK in V3). */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** Der Plan, aus dem dieser Entwurf kopiert wurde ({@code COPY_ACTIVE}); sonst leer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "based_on_id")
    private Timetable basedOn;

    @Column(name = "solve_started_at")
    private Instant solveStartedAt;

    @Column(name = "solve_limit_seconds")
    private Integer solveLimitSeconds;

    /** Kann-Option „Gueltig ab" beim Annehmen (E-40); leer = gilt sofort. */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /**
     * Letzter Tag, an dem dieser Plan gilt — einschliesslich; leer = unbegrenzt (V12, #115).
     *
     * <p>Zusammen mit {@link #validFrom} der Gueltigkeitszeitraum. Zwei aktive Plaene duerfen sich
     * keinen Tag teilen; das sichert die Ausschlussbedingung {@code timetable_active_no_overlap}
     * in der Datenbank, nicht die Anwendung.</p>
     */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /**
     * Die Ablage (#116): ein Entwurf ist eingefroren und wird nicht mehr bearbeitet, bleibt aber
     * annehmbar. Nur bei {@code DRAFT} bedeutsam (V12).
     */
    @Column(name = "finished", nullable = false)
    private boolean finished;

    /** Bewertung des letzten Laufs als Text, z. B. „0hard/0medium/-2soft". */
    @Column(name = "solve_last_score", length = 60)
    private String solveLastScore;

    /** Deutscher Fehlertext eines abgebrochenen Laufs; leer, solange nichts schiefging. */
    @Column(name = "solve_error", length = 500)
    private String solveError;

    protected Timetable() {
    }

    public Timetable(TimetableStatus status, Instant createdAt, Timetable basedOn) {
        this.status = status;
        this.createdAt = createdAt;
        this.basedOn = basedOn;
    }

    public Long getId() {
        return id;
    }

    public TimetableStatus getStatus() {
        return status;
    }

    public void setStatus(TimetableStatus status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Timetable getBasedOn() {
        return basedOn;
    }

    public Instant getSolveStartedAt() {
        return solveStartedAt;
    }

    public void setSolveStartedAt(Instant solveStartedAt) {
        this.solveStartedAt = solveStartedAt;
    }

    public Integer getSolveLimitSeconds() {
        return solveLimitSeconds;
    }

    public void setSolveLimitSeconds(Integer solveLimitSeconds) {
        this.solveLimitSeconds = solveLimitSeconds;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public String getSolveLastScore() {
        return solveLastScore;
    }

    public void setSolveLastScore(String solveLastScore) {
        this.solveLastScore = solveLastScore;
    }

    public String getSolveError() {
        return solveError;
    }

    public void setSolveError(String solveError) {
        this.solveError = solveError;
    }
}
