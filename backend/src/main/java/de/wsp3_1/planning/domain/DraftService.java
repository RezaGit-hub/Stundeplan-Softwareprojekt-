package de.wsp3_1.planning.domain;

import de.wsp3_1.changelog.domain.ChangelogService;
import de.wsp3_1.changelog.domain.ChangelogViews.NewEntry;
import de.wsp3_1.masterdata.persistence.ClassSubject;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.Timeslot;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.planning.domain.PlanViews.DraftSummaryView;
import de.wsp3_1.planning.domain.PlanViews.DraftView;
import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.planning.domain.PlanViews.OpenLessonView;
import de.wsp3_1.planning.domain.PlanViews.SolveInfoView;
import de.wsp3_1.planning.persistence.Lesson;
import de.wsp3_1.planning.persistence.LessonRepository;
import de.wsp3_1.planning.persistence.Timetable;
import de.wsp3_1.planning.persistence.TimetableRepository;
import de.wsp3_1.planning.persistence.TimetableStatus;
import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.domain.ExpiringExceptions;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import de.wsp3_1.solver.SolveResult;
import de.wsp3_1.solver.SolverRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Der Lebenslauf des Plans: Entwurf anlegen, Stunden von Hand bauen, festlegen, verwerfen, annehmen
 * (W-1, W-3, W-8, W-9). Seit #103 (V11, E-52) gibt es mehrere Entwuerfe nebeneinander, jeder mit Namen
 * und eigener Kennung; jede Methode nennt deshalb den Entwurf, den sie meint. Die Singular-Pfade
 * {@code /api/draft} zeigen auf den juengsten ({@link #currentDraftId()}).
 *
 * <p><b>Waehrend einer Berechnung wird nichts geaendert.</b> Jede schreibende Methode fragt vorher den
 * {@link SolverRunner}; laeuft ein Lauf, kommt 409 {@code solving}. Sonst schriebe der Solver-Thread
 * gleichzeitig in dieselben Stunden.</p>
 *
 * <p><b>Konflikte werden nie gespeichert</b> (E-31): jede Antwort dieses Dienstes berechnet sie neu,
 * damit eine Aenderung an den Grunddaten sofort sichtbar ist.</p>
 */
@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    /** So lang ist die Spalte {@code timetable.solve_error} (V5). */
    private static final int MAX_ERROR_LENGTH = 500;

    /** So lang ist die Spalte {@code timetable.name} (V11). */
    private static final int MAX_NAME_LENGTH = 80;

    private final TimetableRepository timetables;
    private final LessonRepository lessons;
    private final ClassSubjectRepository classSubjects;
    private final SchoolClassRepository classes;
    private final TimeslotRepository timeslots;
    private final RoomRepository rooms;
    private final ConflictService conflicts;
    private final PlanReader planReader;
    private final SolverRunner solverRunner;
    private final ChangelogService changelog;
    private final ExpiringExceptions expiringExceptions;

    public DraftService(TimetableRepository timetables, LessonRepository lessons,
                        ClassSubjectRepository classSubjects, SchoolClassRepository classes,
                        TimeslotRepository timeslots, RoomRepository rooms, ConflictService conflicts,
                        PlanReader planReader, SolverRunner solverRunner, ChangelogService changelog,
                        ExpiringExceptions expiringExceptions) {
        this.timetables = timetables;
        this.lessons = lessons;
        this.classSubjects = classSubjects;
        this.classes = classes;
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.conflicts = conflicts;
        this.planReader = planReader;
        this.solverRunner = solverRunner;
        this.changelog = changelog;
        this.expiringExceptions = expiringExceptions;
    }

    // ------------------------------------------------------------------ Lesen

    @Transactional(readOnly = true)
    public DraftView get(Long draftId) {
        return view(requireDraft(draftId));
    }

    /** Alle Entwuerfe, juengster zuerst — die Uebersicht ({@code GET /api/drafts}). */
    @Transactional(readOnly = true)
    public List<DraftSummaryView> list() {
        return timetables.findAllByStatusOrderByIdDesc(TimetableStatus.DRAFT).stream()
                .map(this::summary)
                .toList();
    }

    /**
     * Der juengste Entwurf (hoechste Kennung) — das Ziel der Singular-Pfade {@code /api/draft} (E-52);
     * 404, wenn es keinen gibt. Fuer das Web ist „kein Entwurf" ein Zustand, kein Fehler.
     */
    @Transactional(readOnly = true)
    public Long currentDraftId() {
        return timetables.findFirstByStatusOrderByIdDesc(TimetableStatus.DRAFT)
                .map(Timetable::getId)
                .orElseThrow(() -> new NotFoundException("Kein Entwurf vorhanden."));
    }

    // ------------------------------------------------------------------ Anlegen und verwerfen

    /**
     * Legt einen Entwurf an. {@code COPY_ACTIVE} uebernimmt die Stunden des aktiven Plans und legt
     * dabei genau die fest, die <b>belegt</b> und konfliktfrei sind (W-1): was schon im aktiven Plan
     * kollidiert, soll der Solver neu anfassen duerfen.
     *
     * <p>Andere Entwuerfe bleiben unberuehrt — seit #103 gibt es beliebig viele nebeneinander, ein
     * 409 {@code draft-exists} gibt es nicht mehr. Ohne {@code name} heisst der Entwurf „Entwurf n".</p>
     *
     * <p>Eine <b>offene</b> Stunde des aktiven Plans hat keinen Konflikt — festlegen hiesse aber „bleib
     * fuer immer offen", und Timefold lehnt eine festgelegte Stunde ohne Raum ohnehin ab. Sie kommt
     * deshalb unfestgelegt in den Entwurf, damit der naechste Lauf sie einplanen kann (Befund 05.09.).</p>
     */
    @Transactional
    public DraftView create(String name, DraftSource source) {
        Optional<Timetable> active = planReader.currentOrNextActive();
        Timetable draft = new Timetable(TimetableStatus.DRAFT, Instant.now(),
                source == DraftSource.COPY_ACTIVE ? active.orElse(null) : null);
        draft.setName(name == null || name.isBlank() ? defaultName() : validName(name));
        draft = timetables.save(draft);

        if (source == DraftSource.COPY_ACTIVE && active.isPresent()) {
            List<Lesson> originals = lessons.findAllOfTimetable(active.get().getId());
            Set<Long> conflicted = new HashSet<>();
            for (ConflictView conflict : conflicts.check(originals.stream().map(Assignment::of).toList())) {
                conflicted.addAll(conflict.lessonIds());
            }
            for (Lesson original : originals) {
                boolean placed = original.getTimeslot() != null;
                lessons.save(new Lesson(draft, original.getClassSubject(),
                        placed && !conflicted.contains(original.getId()),
                        original.getTimeslot(), original.getRoom()));
            }
        }
        return view(draft);
    }

    /**
     * Aendert Name und/oder Ablage eines Entwurfs ({@code PATCH /api/drafts/{id}}). Nur gesendete
     * Felder wirken; ein leerer Rumpf ist 400 {@code validation}. Leerer oder zu langer Name ist
     * ebenfalls 400.
     *
     * <p>Beides ist auch waehrend einer Berechnung und in der Ablage erlaubt: der Name geht den Solver
     * nichts an, und {@code finished: false} ist gerade der Weg aus der Ablage heraus (#116).</p>
     */
    @Transactional
    public DraftView patch(Long draftId, String name, Boolean finished) {
        Timetable draft = requireDraft(draftId);
        if (name == null && finished == null) {
            throw new ValidationException("name", "Name oder Ablage angeben");
        }
        if (name != null) {
            draft.setName(validName(name));
        }
        if (finished != null) {
            draft.setFinished(finished);
        }
        return view(draft);
    }

    /** Verwirft den Entwurf mit allen Stunden; aktiver Plan und andere Entwuerfe bleiben unberuehrt. */
    @Transactional
    public void discard(Long draftId) {
        Timetable draft = requireDraft(draftId);
        requireNotSolving(draft);
        lessons.deleteAll(lessons.findAllOfTimetable(draft.getId()));
        timetables.delete(draft);
    }

    // ------------------------------------------------------------------ Handaufbau (W-3, W-9)

    /**
     * Plant eine Stunde von Hand ein. Die Lehrkraft wird nicht gewaehlt: sie folgt aus der Fachzuordnung
     * der Klasse (E-18). Konflikte blockieren nicht — sie stehen in der Antwort (T-W3.1).
     */
    @Transactional
    public DraftView addLesson(Long draftId, Long classId, Long subjectId, Long timeslotId, Long roomId) {
        Timetable draft = requireDraft(draftId);
        requireNotSolving(draft);
        requireNotFinished(draft);

        List<FieldError> errors = new ArrayList<>();
        boolean classExists = classes.findById(classId).isPresent();
        if (!classExists) {
            errors.add(new FieldError("classId", "Klasse fehlt"));
        }
        ClassSubject row = classSubjects.findBySchoolClassIdOrderBySubjectIdAsc(classId).stream()
                .filter(candidate -> candidate.getSubject().getId().equals(subjectId))
                .findFirst()
                .orElse(null);
        if (row == null) {
            errors.add(new FieldError("subjectId", "Die Klasse hat dieses Fach nicht"));
        }
        Timeslot timeslot = timeslots.findById(timeslotId).orElse(null);
        if (timeslot == null) {
            errors.add(new FieldError("timeslotId", "Zeitfenster fehlt"));
        }
        Room room = rooms.findById(roomId).orElse(null);
        if (room == null) {
            errors.add(new FieldError("roomId", "Raum fehlt"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        lessons.save(new Lesson(draft, row, true, timeslot, room));
        return view(draft);
    }

    /**
     * Verschieben, Raum wechseln, Festlegung setzen oder aufheben. Nur gesendete Felder aendern sich;
     * wer Zeitfenster oder Raum setzt, legt die Stunde damit fest — es sei denn, {@code pinned} steht
     * ausdruecklich im Rumpf (T-W9.1).
     *
     * <p>Eine offene Stunde braucht beim Einplanen <b>beides</b>, Zeitfenster und Raum; sonst 400. Die
     * Attrappe im Web setzt hier noch den ersten freien Raum ein — der Server tut das bewusst nicht
     * (Abweichung D-9: er raet keinen Raum).</p>
     */
    @Transactional
    public DraftView patchLesson(Long draftId, Long lessonId, Long timeslotId, Long roomId, Boolean pinned) {
        Timetable draft = requireDraft(draftId);
        requireNotSolving(draft);
        requireNotFinished(draft);
        Lesson lesson = lessons.findInTimetable(lessonId, draft.getId())
                .orElseThrow(() -> new NotFoundException("Stunde " + lessonId + " nicht im Entwurf"));

        Timeslot timeslot = lesson.getTimeslot();
        if (timeslotId != null) {
            timeslot = timeslots.findById(timeslotId)
                    .orElseThrow(() -> new ValidationException("timeslotId", "Zeitfenster fehlt"));
        }
        Room room = lesson.getRoom();
        if (roomId != null) {
            room = rooms.findById(roomId)
                    .orElseThrow(() -> new ValidationException("roomId", "Raum fehlt"));
        }
        if (timeslot != null && room == null) {
            throw new ValidationException("roomId", "Eine eingeplante Stunde braucht Zeitfenster und Raum");
        }
        if (timeslot == null && room != null) {
            throw new ValidationException("timeslotId", "Eine eingeplante Stunde braucht Zeitfenster und Raum");
        }
        lesson.assign(timeslot, room);

        if (pinned != null) {
            lesson.setPinned(pinned);
        } else if (timeslotId != null || roomId != null) {
            lesson.setPinned(true);
        }
        return view(draft);
    }

    /** Entfernt die Stunde aus dem Wochenmuster; das Zeitfenster ist danach wieder frei. */
    @Transactional
    public DraftView removeLesson(Long draftId, Long lessonId) {
        Timetable draft = requireDraft(draftId);
        requireNotSolving(draft);
        requireNotFinished(draft);
        Lesson lesson = lessons.findInTimetable(lessonId, draft.getId())
                .orElseThrow(() -> new NotFoundException("Stunde " + lessonId + " nicht im Entwurf"));
        lessons.delete(lesson);
        return view(draft);
    }

    // ------------------------------------------------------------------ Berechnung: Vor- und Nacharbeit

    /**
     * Ergaenzt vor jedem Lauf die fehlenden Stunden: je Klasse und Fach so viele offene, nicht
     * festgelegte Zeilen, wie an den Wochenstunden fehlen. Ueberzaehlige Zeilen bleiben stehen (sie
     * stehen als Hinweis in {@code warnings}); geloescht wird hier nichts.
     */
    @Transactional
    public void syncRequiredLessons(Timetable draft) {
        Map<Long, Integer> countByClassSubject = new HashMap<>();
        for (Lesson lesson : lessons.findAllOfTimetable(draft.getId())) {
            countByClassSubject.merge(lesson.getClassSubject().getId(), 1, Integer::sum);
        }
        for (ClassSubject row : activeClassSubjects()) {
            int have = countByClassSubject.getOrDefault(row.getId(), 0);
            for (int i = have; i < row.getWeeklyHours(); i++) {
                lessons.save(new Lesson(draft, row, false, null, null));
            }
        }
    }

    /**
     * Schreibt das Ergebnis eines Laufs. Laeuft im Solver-Thread und deshalb in einer eigenen
     * Transaktion. Festgelegte Stunden werden uebersprungen — das ist W-9 und der Kern von T-W9.1.
     */
    @Transactional
    public void storeResult(Long draftId, SolveResult result) {
        Optional<Timetable> found = timetables.findById(draftId);
        if (found.isEmpty() || found.get().getStatus() != TimetableStatus.DRAFT) {
            log.warn("Ergebnis fuer Entwurf {} verworfen: der Entwurf existiert nicht mehr.", draftId);
            return;
        }
        Timetable draft = found.get();
        Map<Long, Lesson> byId = new HashMap<>();
        for (Lesson lesson : lessons.findAllOfTimetable(draftId)) {
            byId.put(lesson.getId(), lesson);
        }
        Map<Long, Timeslot> slotById = new HashMap<>();
        timeslots.findAll().forEach(slot -> slotById.put(slot.getId(), slot));
        Map<Long, Room> roomById = new HashMap<>();
        rooms.findAllByOrderByIdAsc().forEach(room -> roomById.put(room.getId(), room));

        for (SolveResult.Placement placement : result.placements()) {
            Lesson lesson = byId.get(placement.lessonId());
            if (lesson == null || lesson.isPinned()) {
                continue;
            }
            Timeslot slot = placement.timeslotId() == null ? null : slotById.get(placement.timeslotId());
            Room room = placement.roomId() == null ? null : roomById.get(placement.roomId());
            // Waehrend des Laufs kann jemand das Zeitfenster oder den Raum geloescht haben — die Eingabe
            // des Laufs ist dann veraltet. Die Stunde bleibt in dem Fall offen; ohne diese Zeile
            // scheiterte das Speichern am CHECK „belegt oder offen" der Datenbank.
            if (slot == null || room == null) {
                lesson.assign(null, null);
                continue;
            }
            lesson.assign(slot, room);
        }
        draft.setSolveLastScore(result.score());
        draft.setSolveError(null);
    }

    /** Ein gescheiterter Lauf: die Stunden bleiben, der Entwurf meldet danach {@code FAILED}. */
    @Transactional
    public void markFailed(Long draftId, Throwable cause) {
        Optional<Timetable> found = timetables.findById(draftId);
        if (found.isEmpty()) {
            log.warn("Fehler eines Laufs fuer Entwurf {} verworfen: den Entwurf gibt es nicht mehr.", draftId);
            return;
        }
        String message = "Berechnung abgebrochen: " + cause.getMessage();
        found.get().setSolveError(message.length() > MAX_ERROR_LENGTH
                ? message.substring(0, MAX_ERROR_LENGTH) : message);
        log.warn("Berechnung fuer Entwurf {} gescheitert", draftId, cause);
    }

    // ------------------------------------------------------------------ Annehmen (W-8)

    /**
     * Nimmt den Entwurf an — eine Transaktion: die ueberlappenden Vorgaenger werden verkuerzt oder
     * archiviert, der Entwurf wird ein aktiver Plan mit seinem Zeitraum, ein Verlaufseintrag
     * {@code TIMETABLE_ACCEPTED} entsteht. Andere Entwuerfe bleiben stehen (#103) — die Planung kann
     * sie spaeter annehmen oder verwerfen. Ein Entwurf aus der Ablage darf angenommen werden (#116).
     *
     * <p><b>Mehrere aktive Plaene</b> (#115, V12): {@code from} ist {@code validFrom}, sonst heute;
     * {@code until} ist {@code validUntil}, sonst unbegrenzt. Beruehrt wird nur, was sich mit
     * {@code [from, until]} ueberschneidet — drei Faelle, in dieser Reihenfolge:</p>
     * <ol>
     *   <li>Der Vorgaenger hat frueher begonnen: er endet am Tag vor {@code from}. Liegt er damit ganz
     *       in der Vergangenheit, wird er archiviert; sonst bleibt er aktiv — „der alte Plan gilt bis
     *       zu seinem Ende weiter".</li>
     *   <li>Der Vorgaenger beginnt am selben Tag oder spaeter, hat aber schon begonnen (Beginn bis
     *       heute — jemand hat ihn eben erst angenommen und ersetzt ihn sofort): er wird archiviert und
     *       bekommt einen leeren Zeitraum.</li>
     *   <li>Der Vorgaenger beginnt erst kuenftig: 409 {@code plan-overlap}, egal ab wann der neue Plan
     *       gelten soll. Nichts wird geaendert — ein bewusst fuer spaeter angenommener Plan darf nicht
     *       still verschwinden; die Planung waehlt ein Enddatum vor seinem Beginn oder aendert ihn.</li>
     * </ol>
     *
     * <p><b>Harte Konflikte blockieren</b> (409 {@code draft-has-conflicts}, seit #103): ein Plan mit
     * Doppelbelegung oder Sperrzeitverstoss darf nicht der gueltige werden — vorher loeschte das Web
     * nur die Warnung weg. Weiche Konflikte blockieren nicht; die Planung entscheidet.</p>
     *
     * <p><b>Offene Stunden wandern mit</b> (ohne Zeitfenster und Raum) und tauchen danach in keiner
     * Wochensicht auf — sie waeren sonst still verloren. Damit das niemand uebersieht, nennt
     * {@code warnings} ihre Anzahl; der Annehmen-Dialog zeigt dieselbe Zahl schon vorher aus
     * {@code openLessons} des Entwurfs (Befund 05.09.).</p>
     *
     * @param validFrom  Kann-Option (E-40): der neue Plan gilt erst ab diesem Tag. Leer = ab sofort.
     * @param validUntil Kann-Option (#115): letzter Tag einschliesslich. Leer = unbegrenzt.
     */
    @Transactional
    public PlanViews.AcceptResultView accept(Long draftId, LocalDate validFrom, LocalDate validUntil) {
        Timetable draft = requireDraft(draftId);
        requireNotSolving(draft);
        List<Lesson> rows = lessons.findAllOfTimetable(draft.getId());
        requireNoHardConflicts(rows);

        LocalDate heute = LocalDate.now();
        LocalDate from = validFrom == null ? heute : validFrom;
        if (validUntil != null && validUntil.isBefore(from)) {
            throw new ValidationException("validUntil",
                    "Das Ende darf nicht vor dem Beginn liegen");
        }

        // Erst pruefen, dann schreiben: bei 409 plan-overlap soll kein Vorgaenger halb verkuerzt sein.
        // Die Transaktion raeumte das zwar ohnehin weg, aber die Absicht steht so in den Zeilen.
        List<Timetable> betroffen = planReader.activePlansByStart().stream()
                .filter(plan -> overlaps(PlanReader.startOf(plan), plan.getValidUntil(), from, validUntil))
                .toList();
        for (Timetable plan : betroffen) {
            if (!PlanReader.startOf(plan).isBefore(from) && PlanReader.startOf(plan).isAfter(heute)) {
                throw new ConflictException("plan-overlap", overlapMessage(plan));
            }
        }

        // „Der bisherige Plan" fuer Verlaufseintrag und Vertretungen: der, der am Tag des Beginns galt.
        // Bei „jetzt aktivieren" ueber einen erst kuenftig beginnenden Plan hinweg gibt es den nicht —
        // dann steht der frueheste beruehrte Plan dort, sonst „kein Plan".
        Timetable previous = betroffen.stream()
                .filter(plan -> !PlanReader.startOf(plan).isAfter(from))
                .reduce((erster, zweiter) -> zweiter)
                .or(() -> betroffen.stream().findFirst())
                .orElse(null);
        for (Timetable plan : betroffen) {
            // Der Tag vor dem Beginn des neuen Plans: das Ende des alten. Hat der alte am selben Tag
            // begonnen (heute angenommen, sofort ersetzt), liegt das Ende vor seinem Beginn — ein leerer
            // Zeitraum, der an keinem Tag mehr aufloest (PlanReader).
            plan.setValidUntil(from.minusDays(1));
            if (PlanReader.startOf(plan).isBefore(from)) {
                if (plan.getValidUntil().isBefore(heute)) {
                    plan.setStatus(TimetableStatus.ARCHIVED);
                }
            } else {
                plan.setStatus(TimetableStatus.ARCHIVED);
            }
            // Erst schreiben, dann den Entwurf umstellen: die Ausschlussbedingung
            // timetable_active_no_overlap ist nicht aufschiebbar und laesst keinen Augenblick mit zwei
            // ueberlappenden aktiven Plaenen zu.
            timetables.saveAndFlush(plan);
        }

        draft.setStatus(TimetableStatus.ACTIVE);
        draft.setAcceptedAt(Instant.now());
        draft.setValidFrom(validFrom);
        draft.setValidUntil(validUntil);
        draft.setFinished(false);
        timetables.saveAndFlush(draft);

        long placed = rows.stream().filter(lesson -> lesson.getTimeslot() != null).count();
        writeChangelogEntry(previous, validFrom, placed);

        List<String> warnings = new ArrayList<>();
        long open = rows.size() - placed;
        if (open > 0) {
            warnings.add(open == 1
                    ? "Eine Stunde bleibt unverplant: sie steht im aktiven Plan, aber in keiner Woche."
                    : open + " Stunden bleiben unverplant: sie stehen im aktiven Plan, aber in keiner Woche.");
        }
        warnings.addAll(expiringSubstitutions(validFrom, previous, draft, rows));
        return new PlanViews.AcceptResultView(planReader.planOf(draft), warnings);
    }

    /**
     * Teilen sich zwei Zeitraeume mindestens einen Tag? Beide Grenzen einschliesslich, {@code null} am
     * Ende heisst unbegrenzt. Dieselbe Frage stellt die Datenbank mit {@code daterange(…, '[]') &amp;&amp;}.
     */
    private static boolean overlaps(LocalDate vonA, LocalDate bisA, LocalDate vonB, LocalDate bisB) {
        boolean aEndetVorB = bisA != null && bisA.isBefore(vonB);
        boolean bEndetVorA = bisB != null && bisB.isBefore(vonA);
        return !aEndetVorB && !bEndetVorA;
    }

    /** Der Text zu 409 {@code plan-overlap} — er nennt den Zeitraum, der im Weg steht. */
    private static String overlapMessage(Timetable plan) {
        LocalDate start = PlanReader.startOf(plan);
        String bis = plan.getValidUntil() == null ? "" : " bis " + plan.getValidUntil();
        return "Ein angenommener Plan gilt bereits ab " + start + bis
                + ". Enddatum vor " + start + " wählen oder den Zeitraum ändern.";
    }

    /**
     * Der Verlaufseintrag zur Annahme (W-7). Geschrieben ueber {@code changelog.ChangelogService} —
     * seit dem Bau des Moduls {@code changelog} steht der Eintrag dort, wo ihn {@code GET /api/changelog}
     * auch wieder liest (VERTRAG Abschnitt 4; loest die Zwischenloesung PL-1 mit {@code JdbcTemplate} ab).
     */
    private void writeChangelogEntry(Timetable previous, LocalDate validFrom, long placedLessons) {
        String oldAssignment = previous == null || previous.getAcceptedAt() == null
                ? "kein Plan"
                : "Plan vom " + previous.getAcceptedAt().atZone(ZoneId.systemDefault()).toLocalDate();
        String newAssignment = "Plan angenommen (" + placedLessons + " Stunden)";
        changelog.record(NewEntry.timetableAccepted(validFrom, oldAssignment, newAssignment,
                CurrentUser.require().id()));
    }

    /**
      * Rettet die datierten Vertretungen ueber den Planwechsel und meldet, was dabei uebrig bleibt.
      *
      * <p>Bis 05.09. verfielen hier <b>alle</b> Vertretungen ab heute, auch bei einer unveraenderten
      * Kopie des aktiven Plans: {@code COPY_ACTIVE} legt neue Stunden-Zeilen an, und die Ausnahmen
      * zeigten weiter auf die alten Kennungen (Befund PFLICHTENHEFT-9). Eine Grunddatenkorrektur —
      * im Pflichtenheft der Muss-Ablauf (Zielbestimmungen Z. 19-23) — loeschte damit den ganzen
      * Vertretungsplan der Folgewochen. Jetzt wandert jede Vertretung mit, deren Stunde im neuen Plan
      * unveraendert steht; nur der Rest verfaellt, und nur der steht in der Warnung.</p>
      *
      * <p>Umgehaengt wird im Modul {@code substitution}, dem die Tabelle {@code lesson_exception}
      * gehoert; {@code planning} kennt davon nur die Schnittstelle {@link ExpiringExceptions} in
      * {@code shared} (Befund BACKEND-6, 05.09. — vorher las diese Stelle die fremde Tabelle mit
      * {@code JdbcTemplate}, an der Modultabelle und an ArchUnit vorbei).</p>
      *
      * <p>Untergrenze ist der Gueltigkeitsbeginn, fruehestens heute: bis dahin gilt der bisherige Plan
      * weiter, seine Ausnahmen haengen an dessen Stunden und bleiben unangetastet (Befund
      * PFLICHTENHEFT-6).</p>
      */
    private List<String> expiringSubstitutions(LocalDate validFrom, Timetable previous, Timetable neuerPlan,
                                               List<Lesson> neu) {
        LocalDate heute = LocalDate.now();
        LocalDate ab = validFrom != null && validFrom.isAfter(heute) ? validFrom : heute;
        long count = expiringExceptions.carryOver(ab, giltAn(neuerPlan.getId()), sameLessons(previous, neu));
        if (count == 0) {
            return List.of();
        }
        String wann = ab.equals(heute) ? "ab heute" : "ab dem " + ab;
        return List.of(count == 1
                ? "Eine Vertretung " + wann + " verfaellt: ihre Stunde gibt es im neuen Plan nicht mehr."
                : count + " Vertretungen " + wann + " verfallen: ihre Stunden gibt es im neuen Plan nicht mehr.");
    }

    /**
     * Welche Stunde des alten Plans im neuen unveraendert wiederkehrt — alte Kennung auf neue.
     *
     * <p>„Unveraendert" heisst: gleiche Fachzuordnung (und damit Klasse, Fach und Lehrkraft, E-18),
     * gleiches Zeitfenster, gleicher Raum. Genau dann bleibt der Satz einer Vertretung wahr („statt
     * Frau Meyer, Raum R204"). Verschiebt der neue Plan die Stunde, wechselt den Raum oder streicht
     * sie, ist die Vertretung gegenstandslos und verfaellt.</p>
     *
     * <p>Offene Stunden (ohne Zeitfenster und Raum) bleiben draussen: an ihnen kann keine Vertretung
     * haengen. Kaeme ein Schluessel in einem der beiden Plaene mehrfach vor, waere die Zuordnung nicht
     * eindeutig — dann wird lieber nichts umgehaengt als das Falsche.</p>
     */
    private Map<Long, Long> sameLessons(Timetable previous, List<Lesson> neu) {
        if (previous == null) {
            return Map.of();
        }
        List<Lesson> alteStunden = lessons.findAllOfTimetable(previous.getId());
        Map<String, Long> neueJeSchluessel = eindeutigeStunden(neu);
        Map<String, Long> alteJeSchluessel = eindeutigeStunden(alteStunden);

        Map<Long, Long> zuordnung = new HashMap<>();
        alteJeSchluessel.forEach((schluessel, alteId) -> {
            Long neueId = neueJeSchluessel.get(schluessel);
            if (neueId != null) {
                zuordnung.put(alteId, neueId);
            }
        });
        return zuordnung;
    }

    /**
     * Belegte Stunden nach ihrem fachlichen Schluessel — mehrfach belegte Schluessel fallen heraus.
     *
     * <p>Zwei Stunden mit derselben Fachzuordnung im selben Zeitfenster und Raum waeren ein Konflikt und
     * kommen im Betrieb nicht vor. Traeten sie doch auf, waere nicht zu entscheiden, welche Vertretung zu
     * welcher Stunde gehoert; dann bleibt die Vertretung lieber am alten Plan und verfaellt sichtbar.</p>
     */
    private static Map<String, Long> eindeutigeStunden(List<Lesson> stunden) {
        Map<String, Long> jeSchluessel = new HashMap<>();
        Set<String> mehrdeutig = new HashSet<>();
        for (Lesson lesson : stunden) {
            String schluessel = lessonKey(lesson);
            if (schluessel == null) {
                continue;
            }
            if (jeSchluessel.put(schluessel, lesson.getId()) != null) {
                mehrdeutig.add(schluessel);
            }
        }
        mehrdeutig.forEach(jeSchluessel::remove);
        return jeSchluessel;
    }

    /**
     * An welchen Tagen dieser Plan der geltende ist.
     *
     * <p>„Ab dem Gueltigkeitsbeginn" allein reicht nicht: die Plaene bilden eine Zeitleiste, und ein
     * <b>archivierter</b> Plan mit spaeterem Beginn gilt an seinen Tagen weiter (E-40,
     * {@link PlanReader#timetableIdAt} — „gilt A heute, B ab 21.09. und C ab 28.09."). An solchen Tagen
     * haengen die Ausnahmen schon am richtigen Plan und werden nicht angefasst.</p>
     */
    private Predicate<LocalDate> giltAn(Long timetableId) {
        List<PlanReader.PlanPeriod> zeitleiste = planReader.acceptedTimeline();
        return tag -> timetableId.equals(PlanReader.timetableIdAt(zeitleiste, tag));
    }

    /** Der fachliche Schluessel einer belegten Stunde; {@code null} fuer eine offene. */
    private static String lessonKey(Lesson lesson) {
        if (lesson.getTimeslot() == null || lesson.getRoom() == null) {
            return null;
        }
        return lesson.getClassSubject().getId() + "/" + lesson.getTimeslot().getId()
                + "/" + lesson.getRoom().getId();
    }

    // ------------------------------------------------------------------ Antwort bauen

    /** Der ganze Entwurf, wie ihn jede Mutation zurueckgibt (Vertrag: „Antwort ist der ganze Entwurf"). */
    @Transactional(readOnly = true)
    public DraftView view(Timetable draft) {
        // Erst der Zustand, dann die Stunden — nie umgekehrt. Der Solver-Thread speichert sein Ergebnis
        // in einer eigenen Transaktion; laege das Lesen der Stunden davor und der Zustand danach, meldete
        // die Antwort EDITING und zeigte die alten Lagen — das Web hoerte auf zu fragen und zeigte den
        // Stand von vor dem Lauf. In dieser Reihenfolge ist EDITING immer die Zusicherung „storeResult
        // ist durch" (SolverRunner, Zusicherung zu isSolving).
        DraftStatus status = statusOf(draft);
        List<Lesson> rows = lessons.findAllOfTimetable(draft.getId());
        List<Assignment> assignments = rows.stream().map(Assignment::of).toList();

        List<OpenLessonView> openLessons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<Long, Integer> allByClassSubject = new HashMap<>();
        Map<Long, Integer> placedByClassSubject = new HashMap<>();
        for (Lesson lesson : rows) {
            Long key = lesson.getClassSubject().getId();
            allByClassSubject.merge(key, 1, Integer::sum);
            if (lesson.getTimeslot() != null) {
                placedByClassSubject.merge(key, 1, Integer::sum);
            }
        }
        for (ClassSubject row : activeClassSubjects()) {
            int all = allByClassSubject.getOrDefault(row.getId(), 0);
            int placed = placedByClassSubject.getOrDefault(row.getId(), 0);
            String className = row.getSchoolClass().getName();
            String subjectShort = row.getSubject().getShortName();
            int missing = Math.max(0, row.getWeeklyHours() - placed);
            if (missing > 0) {
                openLessons.add(new OpenLessonView(row.getSchoolClass().getId(), className, subjectShort, missing));
            }
            if (all > row.getWeeklyHours()) {
                warnings.add(className + " " + subjectShort + ": " + all + " Stunden geplant, "
                        + row.getWeeklyHours() + " vorgesehen");
            }
        }

        return new DraftView(draft.getId(), draft.getName(), draft.isFinished(), draft.getCreatedAt(), status,
                new SolveInfoView(draft.getSolveStartedAt(), draft.getSolveLimitSeconds(),
                        draft.getSolveLastScore(), draft.getSolveError()),
                rows.stream().map(LessonView::of).toList(),
                conflicts.check(assignments),
                openLessons, warnings,
                expiringPreview(rows));
    }

    /**
     * Vorschau auf den Verlust: wie viele Vertretungen das Annehmen dieses Entwurfs verfallen liesse.
     *
     * <p>Gerechnet wird ab heute — den Gueltigkeitsbeginn waehlt die Planung erst im Dialog, und ein
     * spaeterer Beginn kann die Zahl nur kleiner machen. Ohne aktiven Plan gibt es nichts zu verlieren.</p>
     *
     * <p>Gefragt wird nach den Tagen des <b>aktiven</b> Plans: genau ihn ersetzt der Entwurf, und nur an
     * seinen Tagen haengen Ausnahmen, die der Wechsel betrifft.</p>
     */
    private long expiringPreview(List<Lesson> draftLessons) {
        Timetable active = planReader.currentOrNextActive().orElse(null);
        if (active == null) {
            return 0;
        }
        return expiringExceptions.countExpiring(LocalDate.now(), giltAn(active.getId()),
                sameLessons(active, draftLessons));
    }

    /**
     * Der Zustand wird gelesen, nicht gespeichert: rechnet der Solver gerade an diesem Entwurf, ist er
     * {@code SOLVING}; steht ein Fehler des letzten Laufs, {@code FAILED}; sonst {@code EDITING}.
     */
    private DraftStatus statusOf(Timetable draft) {
        if (solverRunner.isSolving(draft.getId())) {
            return DraftStatus.SOLVING;
        }
        return draft.getSolveError() == null ? DraftStatus.EDITING : DraftStatus.FAILED;
    }

    // ------------------------------------------------------------------ kleine Helfer

    /** Der Entwurf mit dieser Kennung oder 404 — ein angenommener oder verworfener ist keiner mehr. */
    public Timetable requireDraft(Long draftId) {
        return timetables.findByIdAndStatus(draftId, TimetableStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Entwurf " + draftId + " nicht gefunden."));
    }

    /** Harte Konflikte sperren das Annehmen (409 {@code draft-has-conflicts}); die Liste steht in der Antwort. */
    private void requireNoHardConflicts(List<Lesson> rows) {
        List<ConflictView> hard = conflicts.check(rows.stream().map(Assignment::of).toList()).stream()
                .filter(conflict -> conflict.severity() == Severity.HARD)
                .toList();
        if (!hard.isEmpty()) {
            throw new ConflictException("draft-has-conflicts",
                    hard.size() == 1
                            ? "Der Entwurf hat einen harten Konflikt. Erst lösen, dann annehmen."
                            : "Der Entwurf hat " + hard.size() + " harte Konflikte. Erst lösen, dann annehmen.",
                    Map.of("conflicts", hard));
        }
    }

    /** Eine Zeile der Entwurfsliste; die Konflikte werden wie ueberall beim Lesen gerechnet (E-31). */
    private DraftSummaryView summary(Timetable draft) {
        List<Assignment> assignments = lessons.findAllOfTimetable(draft.getId()).stream()
                .map(Assignment::of)
                .toList();
        int hard = (int) conflicts.check(assignments).stream()
                .filter(conflict -> conflict.severity() == Severity.HARD)
                .count();
        DraftSource source = draft.getBasedOn() == null ? DraftSource.EMPTY : DraftSource.COPY_ACTIVE;
        return new DraftSummaryView(draft.getId(), draft.getName(), draft.isFinished(), draft.getCreatedAt(),
                source, statusOf(draft), hard);
    }

    /** „Entwurf n" mit der ersten freien Nummer — zwei gleichnamige Entwuerfe waeren nicht zu unterscheiden. */
    private String defaultName() {
        Set<String> taken = timetables.findAllByStatusOrderByIdDesc(TimetableStatus.DRAFT).stream()
                .map(Timetable::getName)
                .collect(Collectors.toSet());
        int n = taken.size() + 1;
        while (taken.contains("Entwurf " + n)) {
            n++;
        }
        return "Entwurf " + n;
    }

    /** Der Name, wie er gespeichert wird: ohne Rand, nicht leer, hoechstens 80 Zeichen (Spalte V11). */
    private static String validName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            throw new ValidationException("name", "darf nicht leer sein");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name", "hoechstens " + MAX_NAME_LENGTH + " Zeichen");
        }
        return trimmed;
    }

    /**
     * Ein Entwurf in der Ablage wird nicht mehr bearbeitet (409 {@code draft-finished}, #116).
     *
     * <p>Gesperrt sind Stunden anlegen, aendern und loeschen, Berechnen und Reparieren. Erlaubt bleiben
     * Annehmen, Umbenennen, Loeschen und das Wiederoeffnen selbst ({@code PATCH {finished: false}}) —
     * sonst liesse sich ein abgelegter Entwurf nie mehr anfassen. Abbrechen einer Berechnung bleibt
     * ebenfalls erlaubt: es raeumt nur auf.</p>
     */
    public void requireNotFinished(Timetable draft) {
        if (draft.isFinished()) {
            throw new ConflictException("draft-finished",
                    "Der Entwurf liegt in der Ablage. Erst wieder öffnen.");
        }
    }

    /** Waehrend eines Laufs wird am Entwurf nichts geaendert (409 {@code solving}). */
    public void requireNotSolving(Timetable draft) {
        if (solverRunner.isSolving(draft.getId())) {
            throw new ConflictException("solving",
                    "Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen.");
        }
    }

    /** Die Faecherlisten der aktiven Klassen, nach Klasse und Fach sortiert — eine Abfrage. */
    List<ClassSubject> activeClassSubjects() {
        return classSubjects.findAllWithReferences().stream()
                .filter(row -> row.getSchoolClass().isActive())
                .toList();
    }
}
