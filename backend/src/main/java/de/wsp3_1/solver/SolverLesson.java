package de.wsp3_1.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

/**
 * Eine Unterrichtsstunde, wie der Solver sie sieht. Veraenderlich sind nur Zeitfenster und Raum;
 * Klasse, Fach und Lehrkraft stehen fest, weil zu jedem Paar Klasse+Fach genau eine Lehrkraft gehoert.
 *
 * <p>{@code pinned = true} heisst „festgelegt" (W-9): der Solver laesst die Stunde stehen, bewertet sie
 * aber weiter mit. {@code timeslot} darf leer bleiben — eine offene Stunde ist besser als ein harter Konflikt.</p>
 */
@PlanningEntity
public class SolverLesson {

    @PlanningId
    private Long id;

    private Long classId;
    private Long subjectId;
    private Long teacherId;
    private String requiredRoomType;

    @PlanningPin
    private boolean pinned;

    @PlanningVariable(allowsUnassigned = true)
    private TimeslotFact timeslot;

    @PlanningVariable
    private RoomFact room;

    /** Timefold braucht einen parameterlosen Konstruktor. */
    public SolverLesson() {
    }

    public SolverLesson(Long id, Long classId, Long subjectId, Long teacherId, String requiredRoomType) {
        this(id, classId, subjectId, teacherId, requiredRoomType, false, null, null);
    }

    public SolverLesson(Long id, Long classId, Long subjectId, Long teacherId, String requiredRoomType,
                        boolean pinned, TimeslotFact timeslot, RoomFact room) {
        this.id = id;
        this.classId = classId;
        this.subjectId = subjectId;
        this.teacherId = teacherId;
        this.requiredRoomType = requiredRoomType;
        this.pinned = pinned;
        this.timeslot = timeslot;
        this.room = room;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getRequiredRoomType() {
        return requiredRoomType;
    }

    public void setRequiredRoomType(String requiredRoomType) {
        this.requiredRoomType = requiredRoomType;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public TimeslotFact getTimeslot() {
        return timeslot;
    }

    public void setTimeslot(TimeslotFact timeslot) {
        this.timeslot = timeslot;
    }

    public RoomFact getRoom() {
        return room;
    }

    public void setRoom(RoomFact room) {
        this.room = room;
    }
}
