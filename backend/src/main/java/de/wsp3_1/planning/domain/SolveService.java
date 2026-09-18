package de.wsp3_1.planning.domain;

import de.wsp3_1.config.domain.SystemConfigService;
import de.wsp3_1.masterdata.domain.Timegrid;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.SchoolClass;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.planning.persistence.Lesson;
import de.wsp3_1.planning.persistence.LessonRepository;
import de.wsp3_1.planning.persistence.Timetable;
import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.rules.domain.RuleResolver;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.solver.BlockedTimeFact;
import de.wsp3_1.solver.PreferredTimeFact;
import de.wsp3_1.solver.RoomBlockedTimeFact;
import de.wsp3_1.solver.RoomFact;
import de.wsp3_1.solver.RuleFact;
import de.wsp3_1.solver.SolveInput;
import de.wsp3_1.solver.SolveResult;
import de.wsp3_1.solver.SolverRunner;
import de.wsp3_1.solver.TimeslotFact;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Startet und beendet die Berechnung eines Entwurfs (W-8, W-9) und uebersetzt die Datenbank in die
 * Eingabe des Solvers. Der Solver selbst (Timefold) liegt im Modul {@code solver} und kennt keine
 * Datenbank; hier liegt alles, was eine braucht (Abweichung D-4, {@code docs/SOLVER-PLAN.md}).
 *
 * <p>Die Vorbedingungen aus {@code docs/SOLVER-PLAN.md} Abschnitt 7 werden in dieser Reihenfolge
 * geprueft: kein Entwurf, Lauf laeuft schon, Grunddaten unvollstaendig, unbekannte Klasse,
 * Widerspruch zwischen festgelegten Stunden. Erst danach wird ergaenzt und gestartet.</p>
 */
@Service
public class SolveService {

    /**
     * Obergrenze fuer einen blockierenden Lauf ({@link #solveBlocking(int)}). Die Rechenzeit kommt sonst
     * aus {@code SystemConfig}; dort steht sie als Daten und darf gross sein. Ein blockierender Lauf
     * haelt aber Transaktion und Datenbankverbindung offen — eine Minute ist die Grenze, ab der das
     * niemandem mehr nutzt.
     */
    private static final int MAX_BLOCKING_SECONDS = 60;

    private final LessonRepository lessons;
    private final SchoolClassRepository classes;
    private final TimeslotRepository timeslots;
    private final RoomRepository rooms;
    private final TeacherRepository teachers;
    private final RuleResolver ruleResolver;
    private final ConflictService conflicts;
    private final DraftService draftService;
    private final SystemConfigService config;
    private final SolverRunner runner;

    public SolveService(LessonRepository lessons,
                        SchoolClassRepository classes, TimeslotRepository timeslots, RoomRepository rooms,
                        TeacherRepository teachers, RuleResolver ruleResolver, ConflictService conflicts,
                        DraftService draftService, SystemConfigService config, SolverRunner runner) {
        this.lessons = lessons;
        this.classes = classes;
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.teachers = teachers;
        this.ruleResolver = ruleResolver;
        this.conflicts = conflicts;
        this.draftService = draftService;
        this.config = config;
        this.runner = runner;
    }

    /**
     * Prueft die Vorbedingungen, ergaenzt die fehlenden Stunden und startet den Lauf. Kehrt sofort
     * zurueck (HTTP 202); den Stand liefert {@code GET /api/draft}.
     *
     * @param classId Kann-Option (E-40): nur diese Klasse neu berechnen. Alle Stunden anderer Klassen
     *                gelten fuer diesen Lauf als festgelegt — nur im Eingaberecord, nicht in der
     *                Datenbank. Leer = ganzer Plan.
     */
    @Transactional
    public void start(Long classId) {
        Timetable draft = draftService.requireDraft();
        draftService.requireNotSolving(draft);
        requireCompleteMasterData();
        if (classId != null) {
            classes.findById(classId)
                    .filter(SchoolClass::isActive)
                    .orElseThrow(() -> new NotFoundException("Klasse " + classId + " gibt es nicht oder sie ist inaktiv."));
        }

        draftService.syncRequiredLessons(draft);
        requireNoPinnedContradiction(draft, classId);
        requireSufficientCapacity(draft);

        int seconds = config.maxSolveSeconds();
        draft.setSolveStartedAt(Instant.now());
        draft.setSolveLimitSeconds(seconds);
        draft.setSolveError(null);

        Long draftId = draft.getId();
        SolveInput input = buildInput(draft, classId);

        // Vormerken, solange die Transaktion noch offen ist. requireNotSolving oben allein reicht nicht:
        // zwei gleichzeitige Anfragen bestehen beide die Pruefung, weil noch keine committet hat — der
        // zweite runner.start flog dann mit „already solving" als 500 heraus (gemessen 05.09.).
        requireReservation(draftId);

        // Der Lauf startet erst, wenn diese Transaktion geschrieben ist. Sonst koennte der Solver-Thread
        // sein Ergebnis in einer eigenen Transaktion speichern, bevor hier committet wird — und der
        // Commit von hier wuerde es gleich wieder ueberschreiben. Wird die Transaktion zurueckgerollt,
        // faellt auch die Vormerkung wieder weg.
        startAfterCommit(draftId, () -> runner.start(draftId, input, seconds,
                result -> draftService.storeResult(draftId, result),
                error -> draftService.markFailed(draftId, error)));
    }

    /**
     * Beendet einen laufenden Lauf vorzeitig; das bis dahin beste Ergebnis wird uebernommen (T-W8.1).
     * Laeuft nichts, aendert sich nichts — die Antwort ist trotzdem 202.
     */
    @Transactional(readOnly = true)
    public void cancel() {
        runner.cancel(draftService.requireDraft().getId());
    }

    /**
     * Rechnet blockierend und speichert das Ergebnis — der Einstieg, den {@code sampledata} fuer den
     * gefuellten Beispielplan braucht (E-46). Nie aus einem Controller mit wartendem Nutzer aufrufen:
     * die Transaktion (und damit eine Datenbankverbindung) bleibt fuer die ganze Rechenzeit offen. Bei
     * den zehn Sekunden des Beispielbestands ist das vertretbar und spart einen zweiten Weg durch die
     * Vorbedingungen.
     */
    @Transactional
    public void solveBlocking(int seconds) {
        Timetable draft = draftService.requireDraft();
        draftService.requireNotSolving(draft);
        requireCompleteMasterData();
        draftService.syncRequiredLessons(draft);
        requireSufficientCapacity(draft);

        int limit = Math.min(seconds, MAX_BLOCKING_SECONDS);
        Long draftId = draft.getId();
        requireReservation(draftId);
        try {
            SolveResult result = runner.solveBlocking(draftId, buildInput(draft, null), limit);
            draftService.storeResult(draftId, result);
        } finally {
            runner.release(draftId);
        }
        draft.setSolveLimitSeconds(limit);
        draft.setSolveStartedAt(Instant.now());
    }

    // ------------------------------------------------------------------ Vorbedingungen

    /** (a) aktive Klasse ohne Fach, (b) kein Zeitfenster ausser Pausen, (c) kein aktiver Raum. */
    private void requireCompleteMasterData() {
        List<String> missing = new ArrayList<>();
        Set<Long> classesWithSubjects = draftService.activeClassSubjects().stream()
                .map(row -> row.getSchoolClass().getId())
                .collect(Collectors.toSet());
        for (SchoolClass schoolClass : classes.findAllByOrderByIdAsc()) {
            if (schoolClass.isActive() && !classesWithSubjects.contains(schoolClass.getId())) {
                missing.add("Klasse " + schoolClass.getName() + " hat kein Fach");
            }
        }
        if (timeslots.findAll().stream().noneMatch(slot -> !slot.isBreak())) {
            missing.add("Kein Stundenraster angelegt");
        }
        if (rooms.findAllByOrderByIdAsc().stream().noneMatch(Room::isActive)) {
            missing.add("Kein aktiver Raum");
        }
        if (!missing.isEmpty()) {
            throw new ConflictException("master-data-incomplete", "Vor der Berechnung fehlen Angaben.",
                    Map.of("missing", missing));
        }
    }

    /**
     * Widersprechen sich die festgelegten Stunden, kann der Solver das nie aufloesen — dann startet
     * kein Lauf (409 {@code pinned-contradiction}). Bei der Kann-Option {@code classId} gelten auch die
     * Stunden der anderen Klassen als festgelegt und zaehlen mit.
     */
    private void requireNoPinnedContradiction(Timetable draft, Long classId) {
        List<Assignment> pinned = lessons.findAllOfTimetable(draft.getId()).stream()
                .filter(lesson -> isPinnedForRun(lesson, classId))
                .map(Assignment::of)
                .toList();
        List<ConflictView> hard = conflicts.check(pinned).stream()
                .filter(conflict -> conflict.severity() == Severity.HARD)
                .toList();
        if (!hard.isEmpty()) {
            throw new ConflictException("pinned-contradiction",
                    "Festgelegte Stunden stehen im Konflikt. Bitte auflösen oder Festlegung aufheben.",
                    Map.of("conflicts", hard));
        }
    }

    /**
     * Verhindert einen Lauf, wenn die Daten schon eine notwendige Kapazitaetsbedingung verletzen.
     *
     * <p>Die Pruefung passiert nach {@link DraftService#syncRequiredLessons(Timetable)}, damit der
     * Bedarf der gerade angelegten Soll-Stunden zaehlt. Sie verwendet nur harte Regeln: Eine weiche
     * Regel darf die Berechnung nicht blockieren, weil der Solver eine weiche Abweichung bewusst als
     * gueltiges, nur schlechteres Ergebnis behandeln darf.</p>
     */
    private void requireSufficientCapacity(Timetable draft) {
        List<Lesson> rows = lessons.findAllOfTimetable(draft.getId());
        List<CapacityChecker.LessonDemand> lessonDemand = rows.stream()
                .map(lesson -> new CapacityChecker.LessonDemand(
                        lesson.getClassSubject().getSchoolClass().getId(),
                        lesson.getClassSubject().getTeacher().getId(),
                        lesson.getClassSubject().getSubject().getRequiredRoomType()))
                .toList();
        List<CapacityChecker.ClassInfo> activeClasses = classes.findAllByOrderByIdAsc().stream()
                .filter(SchoolClass::isActive)
                .map(schoolClass -> new CapacityChecker.ClassInfo(schoolClass.getId(), schoolClass.getName()))
                .toList();
        List<Long> teacherIds = lessonDemand.stream()
                .map(CapacityChecker.LessonDemand::teacherId)
                .distinct()
                .sorted()
                .toList();
        List<EffectiveRule> resolvedRules = ruleResolver.resolve(activeClassRefs(), teacherIds);
        List<CapacityChecker.TeacherInfo> teacherInfo = teachers.findAllByOrderByIdAsc().stream()
                .map(teacher -> new CapacityChecker.TeacherInfo(teacher.getId(), teacher.getName(),
                        Set.copyOf(teacher.getBlockedTimeslotIds())))
                .toList();
        List<CapacityChecker.RoomInfo> roomInfo = rooms.findAllByOrderByIdAsc().stream()
                .map(room -> new CapacityChecker.RoomInfo(room.getRoomType(), room.isActive()))
                .toList();
        List<CapacityChecker.PlanningTimeslot> planningTimeslots = timeslots.findAll().stream()
                .filter(slot -> !slot.isBreak())
                .map(slot -> new CapacityChecker.PlanningTimeslot(slot.getId(), slot.getDay()))
                .toList();

        List<CapacityViolation> violations = CapacityChecker.check(lessonDemand, activeClasses, teacherInfo,
                roomInfo, planningTimeslots, resolvedRules);
        if (!violations.isEmpty()) {
            throw new ConflictException("solve-capacity-exceeded",
                    "Die verfügbaren Kapazitäten reichen für einen vollständigen Plan nicht aus.",
                    Map.of("capacityViolations", violations));
        }
    }

    // ------------------------------------------------------------------ Uebersetzung in die Solver-Eingabe

    /**
     * Baut die Eingabe eines Laufs — in der Transaktion des Aufrufers, damit der Solver-Thread nur noch
     * Records sieht ({@code docs/SOLVER-PLAN.md} Abschnitt 6).
     *
     * <p>Stunden, die dieser Lauf nicht anfassen darf und die zugleich offen sind, kommen gar nicht mit
     * (siehe {@link #isFixedAndOpen}). Sie bleiben unveraendert stehen.</p>
     */
    SolveInput buildInput(Timetable draft, Long classId) {
        List<Lesson> rows = lessons.findAllOfTimetable(draft.getId()).stream()
                .filter(lesson -> !isFixedAndOpen(lesson, classId))
                .toList();

        List<SolveInput.LessonInput> lessonInputs = rows.stream()
                .map(lesson -> new SolveInput.LessonInput(lesson.getId(),
                        lesson.getClassSubject().getSchoolClass().getId(),
                        lesson.getClassSubject().getSubject().getId(),
                        lesson.getClassSubject().getTeacher().getId(),
                        lesson.getClassSubject().getSubject().getRequiredRoomType(),
                        isPinnedForRun(lesson, classId),
                        lesson.getTimeslot() == null ? null : lesson.getTimeslot().getId(),
                        lesson.getRoom() == null ? null : lesson.getRoom().getId()))
                .toList();

        List<de.wsp3_1.masterdata.persistence.Timeslot> alleSlots = timeslots.findAll();
        Map<Long, Integer> lessonNumbers = Timegrid.lessonNumbers(alleSlots);
        List<TimeslotFact> slotFacts = Timegrid.sortedByGrid(alleSlots).stream()
                .map(slot -> new TimeslotFact(slot.getId(), slot.getDay(), slot.getPosition(), slot.isBreak(),
                        lessonNumbers.get(slot.getId())))
                .toList();

        List<RoomFact> roomFacts = new ArrayList<>();
        List<RoomBlockedTimeFact> roomBlocked = new ArrayList<>();
        for (Room room : rooms.findAllByOrderByIdAsc()) {
            roomFacts.add(new RoomFact(room.getId(), room.getRoomType(), room.isActive()));
            room.getBlockedTimeslotIds()
                    .forEach(slotId -> roomBlocked.add(new RoomBlockedTimeFact(room.getId(), slotId)));
        }

        List<Long> teacherIds = rows.stream()
                .map(lesson -> lesson.getClassSubject().getTeacher().getId())
                .distinct()
                .sorted()
                .toList();
        List<RuleFact> ruleFacts = ruleResolver.resolve(activeClassRefs(), teacherIds).stream()
                .map(SolveService::toFact)
                .toList();

        List<BlockedTimeFact> blocked = new ArrayList<>();
        List<PreferredTimeFact> preferred = new ArrayList<>();
        for (Teacher teacher : teachers.findAllByOrderByIdAsc()) {
            if (!teacherIds.contains(teacher.getId())) {
                continue;
            }
            teacher.getBlockedTimeslotIds()
                    .forEach(slotId -> blocked.add(new BlockedTimeFact(teacher.getId(), slotId)));
            teacher.getPreferredTimeslotIds()
                    .forEach(slotId -> preferred.add(new PreferredTimeFact(teacher.getId(), slotId)));
        }

        return new SolveInput(lessonInputs, slotFacts, roomFacts, ruleFacts, blocked, preferred, roomBlocked);
    }

    /**
     * Festgelegt fuer diesen Lauf: die Stunde ist festgelegt (W-9) — oder sie gehoert einer anderen
     * Klasse als der, die neu berechnet werden soll (Kann-Option {@code classId}).
     */
    private static boolean isPinnedForRun(Lesson lesson, Long classId) {
        return lesson.isPinned()
                || (classId != null && !lesson.getClassSubject().getSchoolClass().getId().equals(classId));
    }

    /**
     * Eine Stunde, die dieser Lauf nicht bewegen darf und die zugleich offen ist (kein Zeitfenster,
     * kein Raum). Sie geht nicht in die Eingabe: Timefold lehnt eine festgelegte Stunde ohne Raum ab
     * („has a variable pinned to null, even though unassigned values are not allowed"). Da der Solver
     * sie ohnehin nicht bewegen duerfte, verliert der Lauf dadurch nichts — die Stunde bleibt offen.
     *
     * <p>Zwei Wege fuehren hierher: eine von Hand festgelegte offene Stunde, und bei der Kann-Option
     * {@code classId} jede offene Stunde einer anderen Klasse.</p>
     */
    private static boolean isFixedAndOpen(Lesson lesson, Long classId) {
        return isPinnedForRun(lesson, classId) && lesson.getTimeslot() == null;
    }

    private List<RuleResolver.ClassRef> activeClassRefs() {
        return classes.findAllByOrderByIdAsc().stream()
                .filter(SchoolClass::isActive)
                .map(schoolClass -> new RuleResolver.ClassRef(schoolClass.getId(), schoolClass.getGrade()))
                .toList();
    }

    private static RuleFact toFact(EffectiveRule rule) {
        return new RuleFact(rule.type(), rule.scope(), rule.ownerId(), rule.value(), rule.severity(),
                rule.weight());
    }

    /** Merkt den Lauf vor oder bricht mit 409 {@code solving} ab — der Fall „zwei Klicks auf Berechnen". */
    private void requireReservation(Long draftId) {
        if (!runner.reserve(draftId)) {
            throw new ConflictException("solving",
                    "Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen.");
        }
    }

    /**
     * Startet den Lauf, sobald die laufende Transaktion geschrieben ist — und gibt die Vormerkung wieder
     * frei, wenn sie zurueckgerollt wird. Ohne den zweiten Teil bliebe der Entwurf nach einem
     * fehlgeschlagenen Commit fuer immer „in Berechnung".
     */
    private void startAfterCommit(Long draftId, Runnable start) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            start.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                start.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    runner.release(draftId);
                }
            }
        });
    }
}
