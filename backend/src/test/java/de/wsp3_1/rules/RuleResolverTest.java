package de.wsp3_1.rules;

import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.rules.domain.RuleResolver;
import de.wsp3_1.rules.domain.RuleResolver.ClassRef;
import de.wsp3_1.rules.persistence.Rule;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Die Regelaufloesung ohne Datenbank und ohne Spring ({@code docs/SOLVER-PLAN.md} Abschnitt 9).
 * Genau diese Liste sehen spaeter der Solver und die Konfliktpruefung — was hier gilt, gilt dort.
 */
class RuleResolverTest {

    private static final ClassRef KLASSE_7A = new ClassRef(1L, 7);
    private static final ClassRef KLASSE_8B = new ClassRef(2L, 8);
    private static final long MEYER = 100L;
    private static final long SCHULZ = 101L;

    /**
     * Baut eine Regelzeile mit Kennung. Die Kennung setzt sonst die Datenbank; hier reicht der direkte
     * Weg ueber das Feld, damit der Test ohne Container laeuft.
     */
    private static Rule regel(long id, RuleType type, RuleScope scope, Integer grade, int value,
                             Severity severity, int weight, boolean active) {
        Rule rule = new Rule(type, scope, grade, value, severity, weight, active);
        ReflectionTestUtils.setField(rule, "id", id);
        return rule;
    }

    @Test
    @DisplayName("Die Jahrgangszeile schlaegt die schulweite Zeile — nur fuer ihren Jahrgang")
    void gradeRuleBeatsSchoolWideRule() {
        List<Rule> regeln = List.of(
                regel(1, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, null, 30, Severity.SOFT, 1, true),
                regel(2, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, 7, 33, Severity.SOFT, 2, true));

        List<EffectiveRule> aufgeloest = RuleResolver.resolve(regeln, List.of(KLASSE_7A, KLASSE_8B), List.of());

        assertThat(aufgeloest)
                .extracting(EffectiveRule::ownerId, EffectiveRule::value, EffectiveRule::weight,
                        EffectiveRule::ruleId)
                .containsExactly(
                        tuple(1L, 33, 2, 2L),   // 7a nimmt die Jahrgangszeile
                        tuple(2L, 30, 1, 1L));  // 8b hat keine, also die schulweite
    }

    @Test
    @DisplayName("Eine ausgeschaltete Regel kommt in der Aufloesung nicht vor")
    void inactiveRuleIsIgnored() {
        List<Rule> regeln = List.of(
                regel(1, RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, null, 6, Severity.SOFT, 1, false));

        assertThat(RuleResolver.resolve(regeln, List.of(KLASSE_7A), List.of())).isEmpty();
    }

    @Test
    @DisplayName("Gibt es weder Jahrgangs- noch schulweite Zeile, gilt fuer die Klasse nichts")
    void classWithoutMatchingRuleGetsNothing() {
        List<Rule> regeln = List.of(
                regel(1, RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, 9, 5, Severity.SOFT, 1, true));

        assertThat(RuleResolver.resolve(regeln, List.of(KLASSE_7A), List.of())).isEmpty();
    }

    @Test
    @DisplayName("Eine Lehrkraft-Regel gilt fuer jede Lehrkraft einzeln")
    void teacherRuleAppliesToEveryTeacher() {
        List<Rule> regeln = List.of(
                regel(4, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, null, 26, Severity.SOFT, 1, true));

        List<EffectiveRule> aufgeloest = RuleResolver.resolve(regeln, List.of(), List.of(MEYER, SCHULZ));

        assertThat(aufgeloest)
                .extracting(EffectiveRule::scope, EffectiveRule::ownerId, EffectiveRule::value)
                .containsExactly(
                        tuple(RuleScope.TEACHER, MEYER, 26),
                        tuple(RuleScope.TEACHER, SCHULZ, 26));
    }

    @Test
    @DisplayName("Die Pausenregel gilt einmal und ohne Objekt")
    void breakRuleHasNoOwner() {
        List<Rule> regeln = List.of(
                regel(5, RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD, 1, true));

        List<EffectiveRule> aufgeloest = RuleResolver.resolve(regeln, List.of(KLASSE_7A, KLASSE_8B),
                List.of(MEYER));

        assertThat(aufgeloest).singleElement().satisfies(regel -> {
            assertThat(regel.scope()).isNull();
            assertThat(regel.ownerId()).isNull();
            assertThat(regel.severity()).isEqualTo(Severity.HARD);
            assertThat(regel.label()).isEqualTo("Keine Stunden in Pausen");
        });
    }

    @Test
    @DisplayName("Das Regelwerk der Schule ergibt fuer drei Klassen und zwei Lehrkraefte acht Regeln")
    void seedRuleSetResolvesForTheSampleSchool() {
        // Genau die fuenf Zeilen aus dem Seed V2 (Bundesland SH): Lehrkraft-Regeln sind ausgeschaltet.
        List<Rule> regeln = List.of(
                regel(1, RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, null, 6, Severity.SOFT, 1, true),
                regel(2, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, null, 30, Severity.SOFT, 1, true),
                regel(3, RuleType.MAX_LESSONS_PER_DAY, RuleScope.TEACHER, null, 6, Severity.SOFT, 1, false),
                regel(4, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, null, 26, Severity.SOFT, 1, false),
                regel(5, RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD, 1, true));

        List<EffectiveRule> aufgeloest = RuleResolver.resolve(regeln,
                List.of(KLASSE_7A, KLASSE_8B, new ClassRef(3L, 9)), List.of(MEYER, SCHULZ));

        // drei Klassen mal zwei Klassenregeln, dazu die Pausenregel; die Lehrkraft-Regeln sind aus
        assertThat(aufgeloest).hasSize(7);
        assertThat(aufgeloest).noneMatch(regel -> regel.scope() == RuleScope.TEACHER);
    }
}
