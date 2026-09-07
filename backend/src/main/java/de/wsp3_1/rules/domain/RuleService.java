package de.wsp3_1.rules.domain;

import de.wsp3_1.config.domain.SystemConfigService;
import de.wsp3_1.rules.persistence.Rule;
import de.wsp3_1.rules.persistence.RulePreset;
import de.wsp3_1.rules.persistence.RulePresetRepository;
import de.wsp3_1.rules.persistence.RuleRepository;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Regelwerk pflegen: lesen, eine Zeile aendern, den Beispielsatz des Bundeslands laden,
 * Jahrgangszeilen anlegen und loeschen (W-2, Testfaelle R-1 bis R-3 der Abnahme-Checkliste).
 */
@Service
public class RuleService {

    private final RuleRepository rules;
    private final RulePresetRepository presets;
    private final SystemConfigService systemConfig;

    public RuleService(RuleRepository rules, RulePresetRepository presets, SystemConfigService systemConfig) {
        this.rules = rules;
        this.presets = presets;
        this.systemConfig = systemConfig;
    }

    /**
     * Das ganze Regelwerk in der Reihenfolge der Regeltabelle: erst die schulweiten Zeilen, dann die
     * Jahrgangszeilen aufsteigend; innerhalb dessen Klasse vor Lehrkraft vor Pausenregel.
     * Sortiert wird im Programm und nicht in der Datenbank, weil leere Spalten je nach Datenbank vorne
     * oder hinten einsortiert werden — so steht die Reihenfolge sichtbar an einer Stelle.
     */
    @Transactional(readOnly = true)
    public List<RuleView> list() {
        return rules.findAll().stream()
                .sorted(REIHENFOLGE)
                .map(RuleView::of)
                .toList();
    }

    private static final Comparator<Rule> REIHENFOLGE = Comparator
            // schulweit (grade leer) zuerst, danach die Jahrgangszeilen aufsteigend
            .comparingInt((Rule r) -> r.getGrade() == null ? 0 : 1)
            .thenComparingInt(r -> r.getGrade() == null ? 0 : r.getGrade())
            // CLASS, TEACHER, dann die Pausenregel ohne Zaehleinheit
            .thenComparingInt(r -> r.getScope() == null ? 2 : r.getScope().ordinal())
            .thenComparing(Rule::getType)
            .thenComparingLong(Rule::getId);

    /**
     * Wert, Haerte, Gewicht und Ein/Aus einer Zeile aendern ({@code PUT /api/rules/{id}}). Typ,
     * Zaehleinheit und Jahrgang bleiben, wie sie sind: das waere eine andere Regel.
     *
     * <p>Die Pausenregel hat keinen Zahlenwert — was auch immer gesendet wird, gespeichert wird 1
     * (die Oberflaeche sperrt das Feld, siehe {@code RulesTable.tsx}).</p>
     */
    @Transactional
    public RuleView update(long id, int value, Severity severity, int weight, boolean active) {
        Rule rule = laden(id);
        rule.setValue(rule.getType() == RuleType.NO_LESSONS_IN_BREAKS ? 1 : value);
        rule.setSeverity(severity);
        rule.setWeight(weight);
        rule.setActive(active);
        return RuleView.of(rules.save(rule));
    }

    /**
     * Den Beispielsatz des eingestellten Bundeslands laden ({@code POST /api/rules/reset}, E-48).
     * Kein Bundesland steht im Code: welches gilt, sagt die Systemkonfiguration.
     *
     * <p>Danach entspricht das Regelwerk genau der Voreinstellung — eigene Jahrgangswerte, die der
     * Beispielsatz nicht kennt, verschwinden (die Oberflaeche fragt vorher nach). Zeilen, die es schon
     * gibt, behalten ihre Kennung, damit ein Lesezeichen und die Regel-Kennung in einer Konfliktmeldung
     * weiter stimmen.</p>
     *
     * <p>Die Voreinstellung sagt nichts darueber, ob eine Regel eingeschaltet ist ({@code rule_preset}
     * hat keine solche Spalte). Es gilt dieselbe Setzung wie im Seed V2: alles an, nur die schulweiten
     * Lehrkraft-Regeln aus — sonst meldet das Testszenario des Pflichtenhefts sofort Verstoesse gegen
     * das Deputat, obwohl niemand eine Regel eingeschaltet hat.</p>
     */
    @Transactional
    public List<RuleView> reset() {
        String state = systemConfig.get().state();
        List<RulePreset> voreinstellung = presets.findByStateOrderByIdAsc(state);
        if (voreinstellung.isEmpty()) {
            throw new NotFoundException("Fuer das Bundesland " + state + " gibt es keine Voreinstellung.");
        }

        Map<String, Rule> vorhanden = new LinkedHashMap<>();
        for (Rule rule : rules.findAll()) {
            vorhanden.put(schluessel(rule.getType(), rule.getScope(), rule.getGrade()), rule);
        }

        List<Rule> neu = new ArrayList<>();
        for (RulePreset preset : voreinstellung) {
            Rule rule = vorhanden.remove(schluessel(preset.getType(), preset.getScope(), preset.getGrade()));
            if (rule == null) {
                rule = new Rule(preset.getType(), preset.getScope(), preset.getGrade(), preset.getValue(),
                        preset.getSeverity(), preset.getWeight(), true);
            }
            rule.setValue(preset.getValue());
            rule.setSeverity(preset.getSeverity());
            rule.setWeight(preset.getWeight());
            rule.setActive(eingeschaltetNachVoreinstellung(preset));
            neu.add(rule);
        }

        // Was der Beispielsatz nicht kennt, faellt weg. Loeschen und Anlegen treffen nie dieselbe
        // Kombination aus Typ, Zaehleinheit und Jahrgang — die Eindeutigkeit kann dabei nicht brechen.
        rules.deleteAll(vorhanden.values());
        rules.saveAll(neu);
        return list();
    }

    /**
     * Eine Jahrgangszeile anlegen ({@code POST /api/rules}, Soll E-47). Schulweite Zeilen kommen aus dem
     * Beispielsatz und werden nicht von Hand angelegt; Jahrgaenge gibt es nur je Klasse.
     */
    @Transactional
    public RuleView create(RuleType type, RuleScope scope, Integer grade, int value, Severity severity,
                           int weight, boolean active) {
        List<ValidationException.FieldError> fehler = new ArrayList<>();
        if (type == RuleType.NO_LESSONS_IN_BREAKS) {
            fehler.add(new ValidationException.FieldError("type",
                    "Einen Jahrgangswert gibt es nur für die Regeln zu maximalen Stunden."));
        }
        if (scope != RuleScope.CLASS) {
            fehler.add(new ValidationException.FieldError("scope", "Jahrgangsregeln zählen je Klasse."));
        }
        if (!fehler.isEmpty()) {
            throw new ValidationException(fehler);
        }
        if (rules.existsByTypeAndScopeAndGrade(type, scope, grade)) {
            throw new ConflictException("data-exists",
                    "Für Jahrgang " + grade + " gibt es die Regel „" + RuleLabels.of(type) + "“ bereits.");
        }
        return RuleView.of(rules.save(new Rule(type, scope, grade, value, severity, weight, active)));
    }

    /**
     * Eine Jahrgangszeile loeschen ({@code DELETE /api/rules/{id}}). Danach gilt fuer diesen Jahrgang
     * wieder der schulweite Wert. Schulweite Zeilen bleiben: sie gehoeren zum Beispielsatz und werden
     * ausgeschaltet, nicht geloescht.
     */
    @Transactional
    public void delete(long id) {
        Rule rule = laden(id);
        if (rule.getGrade() == null) {
            throw new ConflictException("in-use",
                    "Schulweite Regeln werden ausgeschaltet, nicht gelöscht.");
        }
        rules.delete(rule);
    }

    private Rule laden(long id) {
        return rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Die Regel " + id + " gibt es nicht."));
    }

    /** Schulweite Lehrkraft-Regeln starten ausgeschaltet — wie im Seed V2. */
    private static boolean eingeschaltetNachVoreinstellung(RulePreset preset) {
        return !(preset.getScope() == RuleScope.TEACHER && preset.getGrade() == null);
    }

    /** Die Kombination, die eine Regelzeile eindeutig macht ({@code rule_unique} in V1). */
    private static String schluessel(RuleType type, RuleScope scope, Integer grade) {
        return type + "|" + scope + "|" + grade;
    }
}
