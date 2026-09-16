package de.wsp3_1.planning.domain;

import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.planning.domain.PlanViews.PlanSummaryView;
import de.wsp3_1.planning.domain.PlanViews.PlanView;
import de.wsp3_1.planning.persistence.Lesson;
import de.wsp3_1.planning.persistence.LessonRepository;
import de.wsp3_1.planning.persistence.Timetable;
import de.wsp3_1.planning.persistence.TimetableRepository;
import de.wsp3_1.planning.persistence.TimetableStatus;
import de.wsp3_1.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Liest den Regelplan — der einzige Weg fuer andere Module an die Stunden eines Plans.
 *
 * <p>{@code substitution} loest damit seine Wochensichten auf: es fragt nicht „was ist der aktive
 * Plan", sondern „welcher Plan gilt an diesem Tag" ({@link #plannedAt(LocalDate)}). Das ist der
 * Unterschied, den der Gueltigkeitszeitraum macht (E-40, #115): vor dem Beginn gilt weiter der
 * vorherige Plan, nach dem Ende gar keiner.</p>
 *
 * <p>Seit V12 (#115) darf es <b>mehrere aktive Plaene</b> geben — je einen je Zeitraum. „Der aktive
 * Plan" ({@link #activePlan()}) heisst seitdem: der, der heute gilt; gibt es keinen, der mit dem
 * naechsten kuenftigen Beginn. Die ganze Liste liefert {@link #activePlans()}.</p>
 */
@Service
public class PlanReader {

    private final TimetableRepository timetables;
    private final LessonRepository lessons;
    private final ConflictService conflicts;

    public PlanReader(TimetableRepository timetables, LessonRepository lessons, ConflictService conflicts) {
        this.timetables = timetables;
        this.lessons = lessons;
        this.conflicts = conflicts;
    }

    /**
     * Der aktive Plan mit frisch berechneten Konflikten ({@code GET /api/timetable}).
     *
     * <p>„Der aktive": derjenige, der <b>heute</b> gilt. Gibt es keinen — weil der laufende Plan
     * abgelaufen ist oder der naechste erst beginnt —, dann der mit dem naechsten kuenftigen Beginn;
     * so zeigt die Oberflaeche nach dem Ende eines Halbjahres den kommenden Plan statt einer leeren
     * Seite. Gibt es auch den nicht, ist die Antwort leer ({@code id: null}).</p>
     */
    @Transactional(readOnly = true)
    public PlanView activePlan() {
        return currentOrNextActive().map(this::planOf).orElseGet(PlanView::empty);
    }

    /**
     * Alle aktiven Plaene mit ihren Zeitraeumen, frueheste zuerst ({@code GET /api/timetables}).
     *
     * <p>{@code current} traegt genau einer — der, der heute gilt. Am Ende eines Zeitraums und vor
     * dem Beginn des naechsten traegt ihn keiner; das ist ein Zustand, kein Fehler.</p>
     */
    @Transactional(readOnly = true)
    public List<PlanSummaryView> activePlans() {
        LocalDate heute = LocalDate.now();
        return activePlansByStart().stream()
                .map(plan -> new PlanSummaryView(plan.getId(), plan.getAcceptedAt(), plan.getValidFrom(),
                        plan.getValidUntil(), appliesOn(plan, heute)))
                .toList();
    }

    /**
     * Ein angenommener Plan ueber seine Kennung ({@code GET /api/timetables/{id}}) — aktiv oder
     * archiviert. Ein Entwurf ist keiner: dafuer gibt es {@code /api/drafts/{id}}.
     */
    @Transactional(readOnly = true)
    public PlanView planById(Long id) {
        Timetable plan = timetables.findById(id)
                .filter(candidate -> candidate.getStatus() != TimetableStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Plan " + id + " nicht gefunden."));
        return planOf(plan);
    }

    /** Ein Plan als Antwort: Stunden in Reihenfolge der Kennung, Konflikte neu berechnet (E-31). */
    @Transactional(readOnly = true)
    public PlanView planOf(Timetable timetable) {
        List<Lesson> rows = lessons.findAllOfTimetable(timetable.getId());
        return new PlanView(timetable.getId(), timetable.getAcceptedAt(), timetable.getValidFrom(),
                timetable.getValidUntil(),
                rows.stream().map(LessonView::of).toList(),
                conflicts.check(rows.stream().map(Assignment::of).toList()));
    }

    /**
     * Der Plan, der an diesem Kalendertag gilt — samt seinen Stunden, ohne Konflikte.
     *
     * <p>Regel: von allen jemals angenommenen Plaenen (den aktiven und allen archivierten) derjenige
     * mit dem <b>spaetesten Beginn, der nicht nach diesem Tag liegt</b> und dessen Ende nicht vor ihm
     * liegt. Der Beginn ist {@code validFrom} (E-40) oder, wo keines gesetzt wurde, der Tag der
     * Annahme; das Ende ist {@code validUntil} oder unbegrenzt. Gibt es keinen, ist die Liste leer —
     * das ist seit #115 ein echter Fall: nach dem Ende eines Zeitraums kann eine Luecke stehen.</p>
     *
     * <p>Die frueheren Zeilen nahmen bei einem noch nicht gueltigen aktiven Plan schlicht den zuletzt
     * archivierten. Bei mehr als einem Wechsel war das falsch: gilt A heute, B ab 21.09. und C ab 28.09.,
     * dann lieferte eine Frage nach dem 14.09. den Plan B statt A (Befund 05.09.).</p>
     */
    @Transactional(readOnly = true)
    public PlanSnapshot plannedAt(LocalDate date) {
        Long id = timetableIdAt(acceptedTimeline(), date);
        return id == null ? new PlanSnapshot(null, List.of()) : new PlanSnapshot(id, lessonsOf(id));
    }

    /**
     * Wann welcher angenommene Plan gilt — der juengste Beginn zuerst.
     *
     * <p>Fuer eine einzelne Frage ist das dasselbe wie {@link #plannedAt(LocalDate)}. Wer viele Tage
     * aufloest (die Wochensicht der Vertretung fragt fuer jeden Tag), holt die Zeitleiste einmal und
     * ordnet jeden Tag daraus zu, statt je Tag alle angenommenen Plaene <b>und</b> deren Stunden neu zu
     * lesen: eine Woche lud denselben Plan bis 05.09. fuenf- bis sechsmal, ein Jahr bis zu 366-mal.</p>
     */
    @Transactional(readOnly = true)
    public List<PlanPeriod> acceptedTimeline() {
        return acceptedPlans().stream()
                .map(plan -> new PlanPeriod(plan.getId(), startOf(plan), plan.getValidUntil()))
                .sorted(Comparator.comparing(PlanPeriod::from)
                        .thenComparing(PlanPeriod::timetableId)
                        .reversed())
                .toList();
    }

    /**
     * Der Plan, der an diesem Tag gilt: der erste der Zeitleiste, der nicht spaeter beginnt und nicht
     * frueher endet. Dieselbe Regel wie in {@link #plannedAt(LocalDate)} — nur ohne Datenbank, weil die
     * Zeitleiste schon da ist.
     *
     * <p>Ein Plan mit leerem Zeitraum ({@code until} vor {@code from}) faellt durch beide Bedingungen
     * und gilt damit an keinem Tag. Genau so legt {@code DraftService.accept} einen Vorgaenger still,
     * der noch gar nicht begonnen hatte.</p>
     */
    public static Long timetableIdAt(List<PlanPeriod> timeline, LocalDate date) {
        return timeline.stream()
                .filter(period -> !date.isBefore(period.from()))
                .filter(period -> period.until() == null || !date.isAfter(period.until()))
                .map(PlanPeriod::timetableId)
                .findFirst()
                .orElse(null);
    }

    /** Die Stunden eines Plans, ohne Konflikte. */
    @Transactional(readOnly = true)
    public List<LessonView> lessonsOf(Long timetableId) {
        return lessons.findAllOfTimetable(timetableId).stream().map(LessonView::of).toList();
    }

    /**
     * Der Plan, den die Oberflaeche „den aktiven" nennt: der heute geltende, sonst der naechste
     * kuenftige. Auch {@code COPY_ACTIVE} kopiert genau diesen — was sonst kopiert wuerde, waere
     * nicht der Plan, den die Planung gerade vor sich sieht.
     */
    @Transactional(readOnly = true)
    public Optional<Timetable> currentOrNextActive() {
        LocalDate heute = LocalDate.now();
        List<Timetable> plans = activePlansByStart();
        return plans.stream()
                .filter(plan -> appliesOn(plan, heute))
                .findFirst()
                .or(() -> plans.stream().filter(plan -> startOf(plan).isAfter(heute)).findFirst());
    }

    /** Alle aktiven Plaene, frueheste zuerst; bei gleichem Beginn entscheidet die Kennung. */
    @Transactional(readOnly = true)
    public List<Timetable> activePlansByStart() {
        return timetables.findAllByStatusOrderByIdAsc(TimetableStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(PlanReader::startOf).thenComparing(Timetable::getId))
                .toList();
    }

    /** Gilt dieser Plan an diesem Tag? Beginn einschliesslich, Ende einschliesslich, leer = unbegrenzt. */
    public static boolean appliesOn(Timetable plan, LocalDate date) {
        return !date.isBefore(startOf(plan))
                && (plan.getValidUntil() == null || !date.isAfter(plan.getValidUntil()));
    }

    /** Alles, was einmal angenommen wurde: die aktiven Plaene und alle archivierten. */
    private List<Timetable> acceptedPlans() {
        List<Timetable> plans =
                new ArrayList<>(timetables.findByStatusOrderByAcceptedAtDesc(TimetableStatus.ARCHIVED));
        plans.addAll(timetables.findAllByStatusOrderByIdAsc(TimetableStatus.ACTIVE));
        return plans;
    }

    /**
     * Ab welchem Tag ein Plan gilt: sein Stichtag „Gueltig ab", sonst der Tag seiner Annahme. Ein Plan
     * ohne beides (den gibt es nur, wenn jemand von Hand in der Datenbank war) gilt seit jeher.
     */
    public static LocalDate startOf(Timetable plan) {
        if (plan.getValidFrom() != null) {
            return plan.getValidFrom();
        }
        return plan.getAcceptedAt() == null
                ? LocalDate.MIN
                : plan.getAcceptedAt().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Welcher Plan gilt und welche Stunden er hat. {@code timetableId} ist leer, wenn es keinen gibt. */
    public record PlanSnapshot(Long timetableId, List<LessonView> lessons) {
    }

    /**
     * Ein angenommener Plan und sein Zeitraum (E-40, #115). {@code until} leer = unbegrenzt; liegt
     * {@code until} vor {@code from}, gilt der Plan an keinem Tag.
     */
    public record PlanPeriod(Long timetableId, LocalDate from, LocalDate until) {
    }
}
