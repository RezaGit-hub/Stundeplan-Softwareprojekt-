package de.wsp3_1.rules.persistence;

import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, Long> {

    /** Nur eingeschaltete Regeln — die ausgeschalteten sieht weder der Solver noch die Konfliktpruefung. */
    List<Rule> findByActiveTrue();

    /**
     * Gibt es die Jahrgangszeile schon? Nur fuer {@code POST /api/rules} gedacht: dort sind {@code scope}
     * und {@code grade} immer gesetzt. Mit {@code null} wuerde die abgeleitete Abfrage {@code = null}
     * erzeugen und niemals treffen — die Eindeutigkeit ueber leere Spalten sichert die Datenbank
     * ({@code rule_unique … NULLS NOT DISTINCT} in V1).
     */
    boolean existsByTypeAndScopeAndGrade(RuleType type, RuleScope scope, Integer grade);
}
