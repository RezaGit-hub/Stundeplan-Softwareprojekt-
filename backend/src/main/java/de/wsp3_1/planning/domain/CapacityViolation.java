package de.wsp3_1.planning.domain;

import de.wsp3_1.shared.Weekday;

import java.util.Map;

/**
 * Eine notwendige, aber nicht ausreichende Kapazitaetsbedingung fuer einen vollstaendigen Plan.
 *
 * <p>{@code demand}, {@code capacity} und {@code shortfall} stehen in jeder Meldung zur Verfuegung.
 * Die restlichen Felder machen die Rechnung fuer Klassen und Lehrkraefte beziehungsweise Raeume
 * nachvollziehbar und werden nur gesetzt, wenn sie fuer die jeweilige {@link #type()} gelten.</p>
 */
public record CapacityViolation(CapacityViolationType type, Long ownerId, String label, String roomType,
                                int demand, int capacity, int shortfall,
                                Map<Weekday, Integer> dailyCapacities, Integer capacityFromDays,
                                Integer dailyRuleLimit, Integer weeklyRuleLimit,
                                boolean dailyRuleLimitsCapacity, boolean weeklyRuleLimitsCapacity,
                                Integer activeRoomCount, Integer planningTimeslotCount) {
}
