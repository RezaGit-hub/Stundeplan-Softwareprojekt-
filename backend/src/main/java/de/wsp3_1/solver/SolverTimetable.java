package de.wsp3_1.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Planungsproblem eines Entwurfs. Drei Bewertungsebenen (solver.md):
 * <b>hard</b> = feste Konflikte und harte Regeln, <b>medium</b> = Stunde ohne Zeitfenster („offen"),
 * <b>soft</b> = weiche Regeln und Wuensche. Der Solver laesst also lieber eine Stunde offen, als einen
 * harten Konflikt zu bauen.
 *
 * <p>Wertebereiche: {@code timeslots} enthaelt nur Zeitfenster ohne Pause, {@code rooms} nur aktive
 * Raeume — dafuer sorgt {@link SolverTranslator#toSolution(SolveInput)}. Regeln, gesperrte und bevorzugte
 * Zeiten sind reine Tatsachen fuer die Constraints (docs/SOLVER-PLAN.md Abschnitt 2).</p>
 */
@PlanningSolution
public class SolverTimetable {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<TimeslotFact> timeslots = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<RoomFact> rooms = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<RuleFact> rules = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<BlockedTimeFact> blockedTimes = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<PreferredTimeFact> preferredTimes = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<RoomBlockedTimeFact> roomBlockedTimes = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<SolverLesson> lessons = new ArrayList<>();

    @PlanningScore
    private HardMediumSoftScore score;

    public SolverTimetable() {
    }

    /** Nur Wertebereiche und Stunden — fuer kleine Tests ohne Regelwerk. */
    public SolverTimetable(List<TimeslotFact> timeslots, List<RoomFact> rooms, List<SolverLesson> lessons) {
        this(timeslots, rooms, List.of(), List.of(), List.of(), lessons);
    }

    /** Ohne Raumsperren — fuer Tests und Aufrufer aus der Zeit vor #133. */
    public SolverTimetable(List<TimeslotFact> timeslots, List<RoomFact> rooms, List<RuleFact> rules,
                           List<BlockedTimeFact> blockedTimes, List<PreferredTimeFact> preferredTimes,
                           List<SolverLesson> lessons) {
        this(timeslots, rooms, rules, blockedTimes, preferredTimes, List.of(), lessons);
    }

    public SolverTimetable(List<TimeslotFact> timeslots, List<RoomFact> rooms, List<RuleFact> rules,
                           List<BlockedTimeFact> blockedTimes, List<PreferredTimeFact> preferredTimes,
                           List<RoomBlockedTimeFact> roomBlockedTimes, List<SolverLesson> lessons) {
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.rules = rules;
        this.blockedTimes = blockedTimes;
        this.preferredTimes = preferredTimes;
        this.roomBlockedTimes = roomBlockedTimes;
        this.lessons = lessons;
    }

    public List<TimeslotFact> getTimeslots() {
        return timeslots;
    }

    public void setTimeslots(List<TimeslotFact> timeslots) {
        this.timeslots = timeslots;
    }

    public List<RoomFact> getRooms() {
        return rooms;
    }

    public void setRooms(List<RoomFact> rooms) {
        this.rooms = rooms;
    }

    public List<RuleFact> getRules() {
        return rules;
    }

    public void setRules(List<RuleFact> rules) {
        this.rules = rules;
    }

    public List<BlockedTimeFact> getBlockedTimes() {
        return blockedTimes;
    }

    public void setBlockedTimes(List<BlockedTimeFact> blockedTimes) {
        this.blockedTimes = blockedTimes;
    }

    public List<PreferredTimeFact> getPreferredTimes() {
        return preferredTimes;
    }

    public void setPreferredTimes(List<PreferredTimeFact> preferredTimes) {
        this.preferredTimes = preferredTimes;
    }

    public List<RoomBlockedTimeFact> getRoomBlockedTimes() {
        return roomBlockedTimes;
    }

    public void setRoomBlockedTimes(List<RoomBlockedTimeFact> roomBlockedTimes) {
        this.roomBlockedTimes = roomBlockedTimes;
    }

    public List<SolverLesson> getLessons() {
        return lessons;
    }

    public void setLessons(List<SolverLesson> lessons) {
        this.lessons = lessons;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
