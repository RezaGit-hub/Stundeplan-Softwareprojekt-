package de.wsp3_1.rules.persistence;

import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Eine Zeile des Regelwerks der Schule (Tabelle {@code rule}, Migration V1, Seed V2).
 *
 * <p>Das Regelwerk ist Daten, kein Code: der Katalog der Typen steht als Enum, jede Zahl, jede Haerte und
 * jedes Gewicht steht in der Datenbank und ist in der Oberflaeche aenderbar (E-48).</p>
 *
 * <ul>
 *   <li>{@code grade} leer = schulweite Zeile, gesetzt = nur dieser Jahrgang (Soll, E-47). Die
 *       Jahrgangszeile schlaegt die schulweite Zeile — aufgeloest im
 *       {@code de.wsp3_1.rules.domain.RuleResolver}.</li>
 *   <li>{@code scope} leer nur bei {@code NO_LESSONS_IN_BREAKS}: die Pausenregel betrifft weder Klasse
 *       noch Lehrkraft (CHECK {@code rule_scope_required_for_max} in V1).</li>
 *   <li>{@code (type, scope, grade)} ist eindeutig — mit {@code NULLS NOT DISTINCT}, damit auch zwei
 *       Zeilen mit leerem {@code scope} oder leerem {@code grade} als doppelt erkannt werden.</li>
 * </ul>
 */
@Entity
@Table(name = "rule")
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private RuleType type;

    /** leer bei der Pausenregel */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 10)
    private RuleScope scope;

    /** leer = schulweit */
    @Column(name = "grade")
    private Integer grade;

    @Column(name = "value", nullable = false)
    private int value;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** fuer JPA */
    protected Rule() {
    }

    public Rule(RuleType type, RuleScope scope, Integer grade, int value, Severity severity, int weight,
                boolean active) {
        this.type = type;
        this.scope = scope;
        this.grade = grade;
        this.value = value;
        this.severity = severity;
        this.weight = weight;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public RuleType getType() {
        return type;
    }

    public RuleScope getScope() {
        return scope;
    }

    public Integer getGrade() {
        return grade;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
