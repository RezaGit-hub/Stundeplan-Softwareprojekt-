package de.wsp3_1.planning.domain;

import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.planning.domain.PlanViews.PlanView;
import de.wsp3_1.planning.persistence.Lesson;
import de.wsp3_1.planning.persistence.LessonRepository;
import de.wsp3_1.planning.persistence.Timetable;
import de.wsp3_1.planning.persistence.TimetableRepository;
import de.wsp3_1.planning.persistence.TimetableStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Liest den Regelplan — der einzige Weg fuer andere Module an die Stunden eines Plans.
 *
 * <p>{@code substitution} loest damit seine Wochensichten auf: es fragt nicht „was ist der aktive
 * Plan", sondern „welcher Plan gilt an diesem Tag" ({@link #plannedAt(LocalDate)}). Das ist der
 * Unterschied, den die Kann-Option „Gueltig ab" macht (E-40): bis zum Stichtag gilt weiter der
 * vorherige Plan.</p>
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

    /** Der aktive Plan mit frisch berechneten Konflikten ({@code GET /api/timetable}). */
    @Transactional(readOnly = true)
    public PlanView activePlan() {
        return timetables.findByStatus(TimetableStatus.ACTIVE)
                .map(this::planOf)
                .orElseGet(PlanView::empty);
    }

    /** Ein Plan als Antwort: Stunden in Reihenfolge der Kennung, Konflikte neu berechnet (E-31). */
    @Transactional(readOnly = true)
    public PlanView planOf(Timetable timetable) {
        List<Lesson> rows = lessons.findAllOfTimetable(timetable.getId());
        return new PlanView(timetable.getId(), timetable.getAcceptedAt(), timetable.getValidFrom(),
                rows.stream().map(LessonView::of).toList(),
                conflicts.check(rows.stream().map(Assignment::of).toList()));
    }

    /**
     * Der Plan, der an diesem Kalendertag gilt — samt seinen Stunden, ohne Konflikte.
     *
     * <p>Regel: von allen jemals angenommenen Plaenen (dem aktiven und allen archivierten) derjenige mit
     * dem <b>spaetesten Beginn, der nicht nach diesem Tag liegt</b>. Der Beginn ist {@code validFrom}
     * (E-40) oder, wo keines gesetzt wurde, der Tag der Annahme. Gibt es keinen, ist die Liste leer.</p>
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
     * Wann welcher angenommene Plan zu gelten beginnt — der juengste Beginn zuerst.
     *
     * <p>Fuer eine einzelne Frage ist das dasselbe wie {@link #plannedAt(LocalDate)}. Wer viele Tage
     * aufloest (die Wochensicht der Vertretung fragt fuer jeden Tag), holt die Zeitleiste einmal und
     * ordnet jeden Tag daraus zu, statt je Tag alle angenommenen Plaene <b>und</b> deren Stunden neu zu
     * lesen: eine Woche lud denselben Plan bis 05.09. fuenf- bis sechsmal, ein Jahr bis zu 366-mal.</p>
     */
    @Transactional(readOnly = true)
    public List<PlanPeriod> acceptedTimeline() {
        return acceptedPlans().stream()
                .map(plan -> new PlanPeriod(plan.getId(), startOf(plan)))
                .sorted(Comparator.comparing(PlanPeriod::from)
                        .thenComparing(PlanPeriod::timetableId)
                        .reversed())
                .toList();
    }

    /**
     * Der Plan, der an diesem Tag gilt: der erste der Zeitleiste, der nicht spaeter beginnt. Dieselbe
     * Regel wie in {@link #plannedAt(LocalDate)} — nur ohne Datenbank, weil die Zeitleiste schon da ist.
     */
    public static Long timetableIdAt(List<PlanPeriod> timeline, LocalDate date) {
        return timeline.stream()
                .filter(period -> !date.isBefore(period.from()))
                .map(PlanPeriod::timetableId)
                .findFirst()
                .orElse(null);
    }

    /** Die Stunden eines Plans, ohne Konflikte. */
    @Transactional(readOnly = true)
    public List<LessonView> lessonsOf(Long timetableId) {
        return lessons.findAllOfTimetable(timetableId).stream().map(LessonView::of).toList();
    }

    /** Alles, was einmal angenommen wurde: der aktive Plan und alle archivierten. */
    private List<Timetable> acceptedPlans() {
        List<Timetable> plans =
                new ArrayList<>(timetables.findByStatusOrderByAcceptedAtDesc(TimetableStatus.ARCHIVED));
        timetables.findByStatus(TimetableStatus.ACTIVE).ifPresent(plans::add);
        return plans;
    }

    /**
     * Ab welchem Tag ein Plan gilt: sein Stichtag „Gueltig ab", sonst der Tag seiner Annahme. Ein Plan
     * ohne beides (den gibt es nur, wenn jemand von Hand in der Datenbank war) gilt seit jeher.
     */
    private static LocalDate startOf(Timetable plan) {
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

    /** Ein angenommener Plan und der Tag, ab dem er gilt (E-40). */
    public record PlanPeriod(Long timetableId, LocalDate from) {
    }
}
