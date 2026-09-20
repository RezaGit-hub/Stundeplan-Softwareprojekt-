package de.wsp3_1.planning.domain;

import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reine Vorpruefung der notwendigen Kapazitaeten eines Solver-Laufs.
 *
 * <p>Sie weist nur Daten zurueck, fuer die wegen einer <em>harten</em> Regel oder wegen fehlender
 * Zeit-/Raumkapazitaet sicher keine vollstaendige konfliktfreie Belegung moeglich ist. Weiche Regeln
 * begrenzen den Solver nicht und duerfen deshalb keinen Lauf verhindern. Die Pruefung ersetzt den
 * Solver nicht: Ueberlagerungen von Lehrkraeften, Klassen und Raumtypen koennen trotz ausreichender
 * Einzelkapazitaeten immer noch eine teilweise offene Loesung ergeben.</p>
 */
public final class CapacityChecker {

    private CapacityChecker() {
    }

    public record LessonDemand(Long classId, Long teacherId, String requiredRoomType) {
    }

    public record ClassInfo(Long id, String name) {
    }

    public record TeacherInfo(Long id, String name, Set<Long> blockedTimeslotIds) {
    }

    public record RoomInfo(String roomType, boolean active, Set<Long> blockedTimeslotIds) {
    }

    /** Ein Nicht-Pausen-Zeitfenster, das der Solver tatsaechlich belegen darf. */
    public record PlanningTimeslot(Long id, Weekday day) {
    }

    public static List<CapacityViolation> check(List<LessonDemand> lessons,
                                                List<ClassInfo> activeClasses,
                                                List<TeacherInfo> teachers,
                                                List<RoomInfo> rooms,
                                                List<PlanningTimeslot> planningTimeslots,
                                                List<EffectiveRule> rules) {
        List<CapacityViolation> violations = new ArrayList<>();
        Map<Weekday, List<PlanningTimeslot>> slotsByDay = planningTimeslots.stream()
                .collect(Collectors.groupingBy(PlanningTimeslot::day,
                        () -> new EnumMap<>(Weekday.class), Collectors.toList()));

        for (ClassInfo schoolClass : activeClasses) {
            int demand = countBy(lessons, LessonDemand::classId, schoolClass.id());
            Integer dailyRule = hardRule(rules, RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, schoolClass.id());
            Integer weeklyRule = hardRule(rules, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, schoolClass.id());
            Capacity capacity = classCapacity(slotsByDay, dailyRule, weeklyRule);
            if (demand > capacity.maximum()) {
                violations.add(new CapacityViolation(CapacityViolationType.CLASS_CAPACITY, schoolClass.id(),
                        schoolClass.name(), null, demand, capacity.maximum(), demand - capacity.maximum(),
                        capacity.dailyCapacities(), capacity.fromDays(), dailyRule, weeklyRule,
                        capacity.dailyRuleLimitsCapacity(), capacity.weeklyRuleLimitsCapacity(), null, null));
            }
        }

        Map<Long, TeacherInfo> teacherById = teachers.stream()
                .collect(Collectors.toMap(TeacherInfo::id, teacher -> teacher));
        for (Long teacherId : lessons.stream().map(LessonDemand::teacherId).distinct().sorted().toList()) {
            TeacherInfo teacher = teacherById.get(teacherId);
            if (teacher == null) {
                continue;
            }
            int demand = countBy(lessons, LessonDemand::teacherId, teacherId);
            Integer dailyRule = hardRule(rules, RuleType.MAX_LESSONS_PER_DAY, RuleScope.TEACHER, teacherId);
            Integer weeklyRule = hardRule(rules, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, teacherId);
            Capacity capacity = teacherCapacity(slotsByDay, teacher.blockedTimeslotIds(), dailyRule, weeklyRule);
            if (demand > capacity.maximum()) {
                violations.add(new CapacityViolation(CapacityViolationType.TEACHER_CAPACITY, teacherId,
                        teacher.name(), null, demand, capacity.maximum(), demand - capacity.maximum(),
                        capacity.dailyCapacities(), capacity.fromDays(), dailyRule, weeklyRule,
                        capacity.dailyRuleLimitsCapacity(), capacity.weeklyRuleLimitsCapacity(), null, null));
            }
        }

        Map<String, Integer> demandByRoomType = new LinkedHashMap<>();
        for (LessonDemand lesson : lessons) {
            if (lesson.requiredRoomType() != null) {
                demandByRoomType.merge(lesson.requiredRoomType(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : demandByRoomType.entrySet()) {
            List<RoomInfo> matchingRooms = rooms.stream()
                    .filter(RoomInfo::active)
                    .filter(room -> entry.getKey().equals(room.roomType()))
                    .toList();
            int activeRoomCount = matchingRooms.size();
            int capacity = roomCapacity(matchingRooms, planningTimeslots);
            if (entry.getValue() > capacity) {
                violations.add(new CapacityViolation(CapacityViolationType.SPECIAL_ROOM_CAPACITY, null,
                        "Raumtyp " + entry.getKey(), entry.getKey(), entry.getValue(), capacity,
                        entry.getValue() - capacity, Map.of(), null, null, null, false, false, activeRoomCount,
                        planningTimeslots.size()));
            }
        }

        List<RoomInfo> activeRooms = rooms.stream().filter(RoomInfo::active).toList();
        int activeRoomCount = activeRooms.size();
        int roomCapacity = roomCapacity(activeRooms, planningTimeslots);
        if (lessons.size() > roomCapacity) {
            violations.add(new CapacityViolation(CapacityViolationType.ROOM_CAPACITY, null, "Alle aktiven Raeume",
                    null, lessons.size(), roomCapacity, lessons.size() - roomCapacity, Map.of(), null,
                    null, null, false, false, activeRoomCount, planningTimeslots.size()));
        }
        return List.copyOf(violations);
    }

    /** Jede Sperrzeit nimmt genau diesem Raum eine planbare Raumstunde. */
    private static int roomCapacity(List<RoomInfo> rooms, List<PlanningTimeslot> planningTimeslots) {
        return rooms.stream()
                .mapToInt(room -> (int) planningTimeslots.stream()
                        .filter(slot -> !room.blockedTimeslotIds().contains(slot.id()))
                        .count())
                .sum();
    }

    private static Capacity classCapacity(Map<Weekday, List<PlanningTimeslot>> slotsByDay, Integer dailyRule,
                                          Integer weeklyRule) {
        Map<Weekday, Integer> dailyCapacities = new EnumMap<>(Weekday.class);
        int unrestrictedFromDays = 0;
        for (Map.Entry<Weekday, List<PlanningTimeslot>> entry : slotsByDay.entrySet()) {
            int gridCapacity = entry.getValue().size();
            unrestrictedFromDays += gridCapacity;
            dailyCapacities.put(entry.getKey(), dailyRule == null ? gridCapacity : Math.min(dailyRule, gridCapacity));
        }
        return totalCapacity(dailyCapacities, unrestrictedFromDays, dailyRule, weeklyRule);
    }

    private static Capacity teacherCapacity(Map<Weekday, List<PlanningTimeslot>> slotsByDay, Set<Long> blocked,
                                            Integer dailyRule, Integer weeklyRule) {
        Map<Weekday, Integer> dailyCapacities = new EnumMap<>(Weekday.class);
        int unrestrictedFromDays = 0;
        for (Map.Entry<Weekday, List<PlanningTimeslot>> entry : slotsByDay.entrySet()) {
            int available = (int) entry.getValue().stream().filter(slot -> !blocked.contains(slot.id())).count();
            unrestrictedFromDays += available;
            dailyCapacities.put(entry.getKey(), dailyRule == null ? available : Math.min(dailyRule, available));
        }
        return totalCapacity(dailyCapacities, unrestrictedFromDays, dailyRule, weeklyRule);
    }

    private static Capacity totalCapacity(Map<Weekday, Integer> dailyCapacities, int unrestrictedFromDays,
                                          Integer dailyRule, Integer weeklyRule) {
        int fromDays = dailyCapacities.values().stream().mapToInt(Integer::intValue).sum();
        int maximum = weeklyRule == null ? fromDays : Math.min(fromDays, weeklyRule);
        return new Capacity(Map.copyOf(dailyCapacities), fromDays, maximum,
                dailyRule != null && fromDays < unrestrictedFromDays
                        && (weeklyRule == null || fromDays < weeklyRule),
                weeklyRule != null && weeklyRule < fromDays);
    }

    private static Integer hardRule(List<EffectiveRule> rules, RuleType type, RuleScope scope, Long ownerId) {
        return rules.stream()
                .filter(rule -> rule.type() == type && rule.scope() == scope && rule.severity() == Severity.HARD)
                .filter(rule -> ownerId.equals(rule.ownerId()))
                .map(EffectiveRule::value)
                .findFirst()
                .orElse(null);
    }

    private static int countBy(List<LessonDemand> lessons,
                               java.util.function.Function<LessonDemand, Long> owner,
                               Long id) {
        return (int) lessons.stream().filter(lesson -> id.equals(owner.apply(lesson))).count();
    }

    private record Capacity(Map<Weekday, Integer> dailyCapacities, int fromDays, int maximum,
                            boolean dailyRuleLimitsCapacity, boolean weeklyRuleLimitsCapacity) {
    }
}
