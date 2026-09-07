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
 * Eine Zeile der Voreinstellung eines Bundeslands (Tabelle {@code rule_preset}, Migration V1, Werte V2).
 * 138 Zeilen ueber alle 16 Bundeslaender; {@code POST /api/rules/reset} kopiert die Zeilen des in der
 * Systemkonfiguration eingestellten Bundeslands nach {@code rule} (E-48).
 *
 * <p>Die Tabelle wird nur gelesen. Sie hat bewusst keine Spalte {@code active}: ob eine Regel eingeschaltet
 * ist, entscheidet die Schule, nicht die Voreinstellung — siehe
 * {@code de.wsp3_1.rules.domain.RuleService#reset()}.</p>
 *
 * <p><strong>Die Werte sind begruendete Startwerte, kein Rechtsstand.</strong> Herkunft je Zeile steht als
 * Kommentar in V2 (erzeugt aus {@code 07-Konzept/recherche/bundesland-regelwerte.json}); wo dort „Standard"
 * steht, ist der Wert eine Setzung des Projekts und kein Landesrecht. Rueckfrage R2 im Interview 2 am 08.09.
 * kann einzelne Werte aendern — dann eine neue Migration schreiben, V2 bleibt unveraendert.</p>
 */
@Entity
@Table(name = "rule_preset")
public class RulePreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Bundesland-Kuerzel, zum Beispiel {@code SH} */
    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private RuleType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 10)
    private RuleScope scope;

    /** leer = schulweite Zeile; gesetzt = Wert der amtlichen Stundentafel fuer diesen Jahrgang (5..10) */
    @Column(name = "grade")
    private Integer grade;

    @Column(name = "value", nullable = false)
    private int value;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "weight", nullable = false)
    private int weight;

    /** fuer JPA */
    protected RulePreset() {
    }

    public Long getId() {
        return id;
    }

    public String getState() {
        return state;
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

    public Severity getSeverity() {
        return severity;
    }

    public int getWeight() {
        return weight;
    }
}
