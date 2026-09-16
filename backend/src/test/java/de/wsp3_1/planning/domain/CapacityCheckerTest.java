package de.wsp3_1.planning.domain;

import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityCheckerTest {

    @Test
    void detectsClassTeacherSpecialRoomAndOverallRoomCapacity() {
        List<CapacityChecker.LessonDemand> lessons = List.of(
                new CapacityChecker.LessonDemand(1L, 10L, "Labor"),
                new CapacityChecker.LessonDemand(1L, 10L, "Labor"),
                new CapacityChecker.LessonDemand(1L, 10L, "Labor"),
                new CapacityChecker.LessonDemand(1L, 10L, "Labor"));
        List<CapacityChecker.PlanningTimeslot> slots = List.of(
                new CapacityChecker.PlanningTimeslot(1L, Weekday.MO),
                new CapacityChecker.PlanningTimeslot(2L, Weekday.MO),
                new CapacityChecker.PlanningTimeslot(3L, Weekday.TU));
        List<EffectiveRule> rules = List.of(
                rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, 1L, 1),
                rule(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, 1L, 5),
                rule(RuleType.MAX_LESSONS_PER_DAY, RuleScope.TEACHER, 10L, 5),
                rule(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, 10L, 5));

        List<CapacityViolation> violations = CapacityChecker.check(lessons,
                List.of(new CapacityChecker.ClassInfo(1L, "7a")),
                List.of(new CapacityChecker.TeacherInfo(10L, "Meyer", Set.of(1L, 2L))),
                List.of(new CapacityChecker.RoomInfo("Labor", true)), slots, rules);

        assertThat(violations).extracting(CapacityViolation::type).containsExactly(
                CapacityViolationType.CLASS_CAPACITY,
                CapacityViolationType.TEACHER_CAPACITY,
                CapacityViolationType.SPECIAL_ROOM_CAPACITY,
                CapacityViolationType.ROOM_CAPACITY);
        assertThat(violations.get(0)).extracting(CapacityViolation::demand, CapacityViolation::capacity,
                CapacityViolation::capacityFromDays, CapacityViolation::shortfall)
                .containsExactly(4, 2, 2, 2);
        assertThat(violations.get(1)).extracting(CapacityViolation::demand, CapacityViolation::capacity,
                CapacityViolation::capacityFromDays, CapacityViolation::shortfall)
                .containsExactly(4, 1, 1, 3);
        assertThat(violations.get(2)).extracting(CapacityViolation::demand, CapacityViolation::capacity,
                CapacityViolation::activeRoomCount, CapacityViolation::planningTimeslotCount)
                .containsExactly(4, 3, 1, 3);
    }

    @Test
    void ignoresSoftRulesBecauseTheyDoNotMakeASolutionInvalid() {
        List<CapacityViolation> violations = CapacityChecker.check(
                List.of(new CapacityChecker.LessonDemand(1L, 10L, null),
                        new CapacityChecker.LessonDemand(1L, 10L, null)),
                List.of(new CapacityChecker.ClassInfo(1L, "7a")),
                List.of(new CapacityChecker.TeacherInfo(10L, "Meyer", Set.of())),
                List.of(new CapacityChecker.RoomInfo("Klassenraum", true)),
                List.of(new CapacityChecker.PlanningTimeslot(1L, Weekday.MO),
                        new CapacityChecker.PlanningTimeslot(2L, Weekday.MO)),
                List.of(new EffectiveRule(1L, RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, 1L, 1,
                        Severity.SOFT, 1, "weich")));

        assertThat(violations).isEmpty();
    }

    private static EffectiveRule rule(RuleType type, RuleScope scope, Long ownerId, int value) {
        return new EffectiveRule(1L, type, scope, ownerId, value, Severity.HARD, 1, "hart");
    }
}
