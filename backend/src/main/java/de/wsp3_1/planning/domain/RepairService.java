package de.wsp3_1.planning.domain;

import de.wsp3_1.planning.domain.RepairProposal.RepairMove;
import de.wsp3_1.planning.persistence.Lesson;
import de.wsp3_1.planning.persistence.LessonRepository;
import de.wsp3_1.planning.persistence.Timetable;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.solver.SolveInput;
import de.wsp3_1.solver.SolveInput.LessonInput;
import de.wsp3_1.solver.SolveResult;
import de.wsp3_1.solver.SolverRunner;
import de.wsp3_1.solver.TimeslotFact;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loest einen Konflikt des Entwurfs per Knopf (#110, E-53): drei kurze Solver-Laeufe mit wachsendem
 * Freiheitsgrad, jeder liefert einen Vorschlag. <b>Gespeichert wird nichts</b> — die Planung waehlt einen
 * Vorschlag, und die Oberflaeche uebernimmt ihn Stunde fuer Stunde ueber {@code PATCH /api/draft/lessons}.
 *
 * <p>Die drei Stufen: (1) nur die Konfliktstunde ist frei, jede andere belegte Stunde gilt fuer den Lauf
 * als festgelegt; (2) dazu die Stunden derselben Klasse am selben Tag; (3) dazu alle Stunden der
 * beteiligten Lehrkraft und des beteiligten Raums an diesem Tag. Eine Stufe „weiche Regel lockern" gibt es
 * bewusst nicht (E-53).</p>
 *
 * <p>Ablauf im Request: den Entwurf beim {@link SolverRunner} vormerken (409 {@code solving}, wenn schon
 * ein Lauf unterwegs ist — und solange die Serie laeuft, bekommt jeder andere Lauf und jede Aenderung
 * dieselbe Antwort), dann die Eingabe in einer kurzen lesenden Transaktion laden, die Laeufe ausserhalb
 * jeder Transaktion rechnen, im {@code finally} freigeben. Drei mal fuenf Sekunden sind die Obergrenze;
 * die Oberflaeche sagt „rechnet bis zu 15 Sekunden". Ein „Berechnung abbrechen" erreicht die Serie
 * nicht — sie ist auf 15 s begrenzt, das ist gewollt einfach gehalten.</p>
 */
@Service
public class RepairService {

    /** Rechenzeit je Stufe. Drei Stufen, also hoechstens 15 s je Anfrage. */
    static final int SECONDS_PER_LEVEL = 5;

    private static final List<String> LABELS = List.of(
            "Nur diese Stunde verschieben",
            "Stunden der Klasse am selben Tag umstellen",
            "Auch Stunden der Lehrkraft und des Raums am selben Tag umstellen");

    private final DraftService draftService;
    private final SolveService solveService;
    private final ConflictService conflicts;
    private final LessonRepository lessons;
    private final SolverRunner runner;
    private final TransactionTemplate readOnly;

    public RepairService(DraftService draftService, SolveService solveService, ConflictService conflicts,
                         LessonRepository lessons, SolverRunner runner,
                         PlatformTransactionManager transactionManager) {
        this.draftService = draftService;
        this.solveService = solveService;
        this.conflicts = conflicts;
        this.lessons = lessons;
        this.runner = runner;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * Die Vorschlaege fuer die Konfliktstunde {@code lessonId} im Entwurf {@code draftId}. 404, wenn es
     * den Entwurf nicht gibt oder die Stunde nicht in ihm steht; 400 {@code validation}, wenn sie in
     * keinem Konflikt steht; 409 {@code solving}, wenn der Entwurf gerade berechnet wird. Nur Vorschlaege
     * mit mindestens einer bewegten Stunde kommen zurueck, gleiche Vorschlaege zweier Stufen nur einmal.
     */
    public List<RepairProposal> propose(Long draftId, Long lessonId) {
        // Erst vormerken, dann laden. Umgekehrt koennte zwischen Laden und Vormerken noch ein PATCH
        // committen, und die Serie rechnete auf einem veralteten Stand (Zweitpruefung 11.09.).
        readOnly.execute(status -> draftService.requireDraft(draftId));
        if (!runner.reserve(draftId)) {
            throw new ConflictException("solving",
                    "Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen.");
        }
        try {
            Loaded loaded = readOnly.execute(status -> load(draftId, lessonId));
            return proposeLevels(loaded);
        } finally {
            runner.release(draftId);
        }
    }

    // ------------------------------------------------------------------ Laden (Transaktion)

    /** Alles, was die Laeufe brauchen — nur Records, damit der Solver keine Datenbank mehr sieht. */
    private record Loaded(Long draftId, LessonInput target, List<LessonInput> placed, SolveInput input,
                          CheckContext context) {
    }

    private Loaded load(Long draftId, Long lessonId) {
        Timetable draft = draftService.requireDraft(draftId);
        Lesson lesson = lessons.findInTimetable(lessonId, draft.getId())
                .orElseThrow(() -> new NotFoundException("Stunde " + lessonId + " nicht im Entwurf"));

        List<Assignment> assignments = lessons.findAllOfTimetable(draft.getId()).stream()
                .map(Assignment::of)
                .toList();
        CheckContext context = conflicts.context(assignments);
        boolean inConflict = ConflictChecker.check(assignments, context).stream()
                .anyMatch(conflict -> conflict.lessonIds().contains(lesson.getId()));
        if (!inConflict) {
            throw new ValidationException("lessonId", "Die Stunde steht in keinem Konflikt");
        }

        // Offene Stunden bleiben draussen: sie erzeugen keinen Konflikt, und ein Vorschlag soll nur
        // umstellen, nicht nebenbei einplanen. buildInput laesst festgelegte offene Stunden ohnehin weg
        // (Timefold lehnt sie ab); hier fallen auch die nicht festgelegten heraus.
        SolveInput input = solveService.buildInput(draft, null);
        List<LessonInput> placed = input.lessons().stream()
                .filter(row -> row.timeslotId() != null)
                .toList();
        LessonInput target = placed.stream()
                .filter(row -> row.id().equals(lesson.getId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("lessonId", "Die Stunde steht in keinem Konflikt"));
        return new Loaded(draft.getId(), target, placed, input, context);
    }

    // ------------------------------------------------------------------ Die drei Stufen (ohne Transaktion)

    private List<RepairProposal> proposeLevels(Loaded loaded) {
        Map<Long, Weekday> dayOfSlot = new HashMap<>();
        for (TimeslotFact slot : loaded.input().timeslots()) {
            dayOfSlot.put(slot.id(), slot.day());
        }
        LessonInput target = loaded.target();
        Weekday day = dayOfSlot.get(target.timeslotId());

        Set<Long> free = new HashSet<>();
        free.add(target.id());
        List<RepairProposal> proposals = new ArrayList<>();
        List<Set<RepairMove>> seen = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            for (LessonInput row : loaded.placed()) {
                boolean sameDay = day != null && day.equals(dayOfSlot.get(row.timeslotId()));
                if (level == 2 && sameDay && row.classId().equals(target.classId())) {
                    free.add(row.id());
                }
                if (level == 3 && sameDay
                        && (row.teacherId().equals(target.teacherId()) || row.roomId().equals(target.roomId()))) {
                    free.add(row.id());
                }
            }
            RepairProposal proposal = solveLevel(loaded, level, Set.copyOf(free));
            if (proposal == null) {
                continue;
            }
            Set<RepairMove> moves = Set.copyOf(proposal.moves());
            if (seen.contains(moves)) {
                continue;
            }
            seen.add(moves);
            proposals.add(proposal);
        }
        return proposals;
    }

    /** Ein Lauf mit dieser Pin-Maske; {@code null}, wenn sich keine Stunde bewegt hat. */
    private RepairProposal solveLevel(Loaded loaded, int level, Set<Long> free) {
        List<LessonInput> rows = loaded.placed().stream()
                .map(row -> new LessonInput(row.id(), row.classId(), row.subjectId(), row.teacherId(),
                        row.requiredRoomType(), !free.contains(row.id()), row.timeslotId(), row.roomId()))
                .toList();
        SolveInput base = loaded.input();
        SolveInput input = new SolveInput(rows, base.timeslots(), base.rooms(), base.rules(),
                base.blockedTimes(), base.preferredTimes(), base.roomBlockedTimes());

        // Eigene Problem-Kennung je Stufe: die Kennung des Entwurfs gehoert dem richtigen Lauf, und
        // Timefold meldet sie erst nach dem Rueckruf wieder frei — drei Laeufe hintereinander auf derselben
        // Kennung liefen Gefahr, sich mit „already solving" zu treffen.
        Long problemId = -(loaded.draftId() * 10 + level);
        SolveResult result = runner.solveBlocking(problemId, input, SECONDS_PER_LEVEL);

        Map<Long, SolveResult.Placement> placements = new HashMap<>();
        for (SolveResult.Placement placement : result.placements()) {
            placements.put(placement.lessonId(), placement);
        }
        List<RepairMove> moves = new ArrayList<>();
        List<Assignment> after = new ArrayList<>();
        for (LessonInput row : rows) {
            SolveResult.Placement placement = placements.get(row.id());
            // Timefold darf eine freie Stunde auch offen lassen (allowsUnassigned). Das ist kein Zug, den
            // die Oberflaeche uebernehmen koennte: die Stunde bleibt dann, wo sie war — und genau so
            // zaehlt sie auch in hardConflictsAfter, damit Vorschau und Uebernahme dasselbe zeigen.
            boolean moved = placement != null && placement.timeslotId() != null && placement.roomId() != null
                    && !(placement.timeslotId().equals(row.timeslotId()) && placement.roomId().equals(row.roomId()));
            Long slotId = moved ? placement.timeslotId() : row.timeslotId();
            Long roomId = moved ? placement.roomId() : row.roomId();
            after.add(new Assignment(row.id(), row.classId(), row.subjectId(), row.teacherId(),
                    roomId, slotId, row.pinned()));
            if (moved) {
                moves.add(new RepairMove(row.id(), slotId, roomId));
            }
        }
        if (moves.isEmpty()) {
            return null;
        }
        int hard = (int) ConflictChecker.check(after, loaded.context()).stream()
                .filter(conflict -> conflict.severity() == Severity.HARD)
                .count();
        return new RepairProposal(level, LABELS.get(level - 1), moves, hard, result.score());
    }
}
