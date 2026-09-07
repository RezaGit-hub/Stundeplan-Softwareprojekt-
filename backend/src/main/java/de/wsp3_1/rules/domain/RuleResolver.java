package de.wsp3_1.rules.domain;

import de.wsp3_1.rules.persistence.Rule;
import de.wsp3_1.rules.persistence.RuleRepository;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Loest das Regelwerk zu Regeln je Objekt auf: aus „hoechstens 6 Stunden pro Tag, je Klasse" und den
 * Klassen 7a, 8b, 9c werden drei {@link EffectiveRule}. Der Solver und die Konfliktpruefung im Modul
 * {@code planning} arbeiten beide auf dieser Liste; damit koennen sie nicht auseinanderlaufen
 * ({@code docs/SOLVER-PLAN.md} Abschnitt 9).
 *
 * <p>Die Regeln der Aufloesung:</p>
 * <ul>
 *   <li>Nur eingeschaltete Regeln zaehlen.</li>
 *   <li>{@code scope = CLASS}: je Klasse und Typ gilt die Zeile mit dem Jahrgang der Klasse; gibt es die
 *       nicht, die schulweite Zeile; gibt es auch die nicht, gilt fuer diese Klasse nichts (E-47:
 *       „Jahrgang vor schulweit").</li>
 *   <li>{@code scope = TEACHER}: je Lehrkraft die schulweite Zeile — bei Lehrkraeften gibt es keinen
 *       Jahrgang.</li>
 *   <li>{@code NO_LESSONS_IN_BREAKS}: eine Zeile ohne Objekt.</li>
 * </ul>
 */
@Service
public class RuleResolver {

    private final RuleRepository repository;

    public RuleResolver(RuleRepository repository) {
        this.repository = repository;
    }

    /** Eine Klasse, so wie die Regelaufloesung sie braucht: Kennung und Jahrgang. */
    public record ClassRef(Long id, int grade) {
    }

    /**
     * Die aufgeloesten Regeln fuer die uebergebenen Klassen und Lehrkraefte.
     *
     * @param classes    alle aktiven Klassen mit ihrem Jahrgang
     * @param teacherIds alle Lehrkraefte, die im Plan vorkommen
     */
    @Transactional(readOnly = true)
    public List<EffectiveRule> resolve(List<ClassRef> classes, List<Long> teacherIds) {
        return resolve(repository.findByActiveTrue(), classes, teacherIds);
    }

    /**
     * Dieselbe Aufloesung ohne Datenbank — hier steht die Logik, damit sie ohne Spring und ohne Container
     * geprueft werden kann ({@code RuleResolverTest}).
     *
     * @param activeRules nur eingeschaltete Regeln; ausgeschaltete werden hier zur Sicherheit noch einmal
     *                    aussortiert
     */
    public static List<EffectiveRule> resolve(List<Rule> activeRules, List<ClassRef> classes,
                                              List<Long> teacherIds) {
        List<Rule> rules = activeRules.stream().filter(Rule::isActive).toList();
        List<EffectiveRule> resolved = new ArrayList<>();

        for (RuleType type : List.of(RuleType.MAX_LESSONS_PER_DAY, RuleType.MAX_LESSONS_PER_WEEK)) {
            for (ClassRef schoolClass : classes) {
                Rule rule = pick(rules, type, RuleScope.CLASS, schoolClass.grade());
                if (rule != null) {
                    resolved.add(effective(rule, schoolClass.id()));
                }
            }
            for (Long teacherId : teacherIds) {
                Rule rule = pick(rules, type, RuleScope.TEACHER, null);
                if (rule != null) {
                    resolved.add(effective(rule, teacherId));
                }
            }
        }

        for (Rule rule : rules) {
            if (rule.getType() == RuleType.NO_LESSONS_IN_BREAKS) {
                resolved.add(effective(rule, null));
            }
        }
        return resolved;
    }

    /** Die Jahrgangszeile, sonst die schulweite Zeile, sonst nichts. */
    private static Rule pick(List<Rule> rules, RuleType type, RuleScope scope, Integer grade) {
        Rule schoolWide = null;
        for (Rule rule : rules) {
            if (rule.getType() != type || rule.getScope() != scope) {
                continue;
            }
            if (grade != null && grade.equals(rule.getGrade())) {
                return rule;
            }
            if (rule.getGrade() == null) {
                schoolWide = rule;
            }
        }
        return schoolWide;
    }

    private static EffectiveRule effective(Rule rule, Long ownerId) {
        return new EffectiveRule(rule.getId(), rule.getType(), rule.getScope(), ownerId, rule.getValue(),
                rule.getSeverity(), rule.getWeight(), RuleLabels.of(rule.getType()));
    }
}
