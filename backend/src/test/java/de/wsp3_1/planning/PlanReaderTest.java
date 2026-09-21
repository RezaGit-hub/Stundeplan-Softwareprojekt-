package de.wsp3_1.planning;

import de.wsp3_1.planning.domain.PlanReader;
import de.wsp3_1.planning.domain.PlanReader.PlanPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Zuordnung „welcher Plan gilt an diesem Tag" als Funktion (Teststrategie Ebene 1): eine
 * Zeitleiste hinein, eine Kennung heraus — ohne Datenbank, ohne Spring.
 *
 * <p>Die Regel steht in {@link PlanReader#timetableIdAt(List, LocalDate)}: der erste Plan der
 * Zeitleiste (juengster Beginn zuerst), der nicht spaeter beginnt und nicht frueher endet. Gegen die
 * echte Datenbank prueft {@code WeekIT.threeValidFromDatesResolvePerDay} und
 * {@code WeekIT.aPlanWithAnEndDateStopsResolvingAfterIt} dasselbe.</p>
 */
class PlanReaderTest {

    private static final LocalDate MO = LocalDate.of(2026, 9, 21);

    /** Wie {@code PlanReader.acceptedTimeline()} sortiert: spaetester Beginn zuerst, dann Kennung. */
    private static List<PlanPeriod> timeline(PlanPeriod... periods) {
        return List.of(periods).stream()
                .sorted(Comparator.comparing(PlanPeriod::from)
                        .thenComparing(PlanPeriod::timetableId)
                        .reversed())
                .toList();
    }

    @Test
    @DisplayName("Ohne Ende gilt ein Plan ab seinem Beginn fuer immer")
    void anOpenEndedPlanAppliesFromItsStartOnwards() {
        List<PlanPeriod> zeitleiste = timeline(new PlanPeriod(1L, MO, null));

        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.minusDays(1))).isNull();
        assertThat(PlanReader.timetableIdAt(zeitleiste, MO)).isEqualTo(1L);
        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusYears(5))).isEqualTo(1L);
    }

    @Test
    @DisplayName("Das Ende gilt einschliesslich; der Tag danach gehoert keinem Plan mehr")
    void theEndDateIsInclusiveAndTheDayAfterBelongsToNobody() {
        List<PlanPeriod> zeitleiste = timeline(new PlanPeriod(1L, MO, MO.plusDays(4)));

        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(4))).isEqualTo(1L);
        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(5))).isNull();
    }

    @Test
    @DisplayName("Zwei anschliessende Zeitraeume: jeder Tag bekommt seinen Plan, auch an der Grenze")
    void adjoiningRangesHandOverOnTheRightDay() {
        List<PlanPeriod> zeitleiste = timeline(
                new PlanPeriod(1L, MO, MO.plusDays(2)),
                new PlanPeriod(2L, MO.plusDays(3), null));

        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(2))).isEqualTo(1L);
        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(3))).isEqualTo(2L);
    }

    /**
     * Die Luecke ist der Fall, den die fruehere Regel nicht kannte: sie nahm einfach den letzten Plan
     * mit passendem Beginn. Endet er, darf sie nicht auf ihn zurueckfallen.
     */
    @Test
    @DisplayName("In einer Luecke zwischen zwei Zeitraeumen gilt kein Plan")
    void aGapBetweenTwoRangesHasNoPlan() {
        List<PlanPeriod> zeitleiste = timeline(
                new PlanPeriod(1L, MO, MO.plusDays(2)),
                new PlanPeriod(2L, MO.plusDays(10), null));

        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(5))).isNull();
    }

    /**
     * Ein leerer Zeitraum entsteht beim Annehmen „ab sofort", wenn ein noch gar nicht begonnener Plan
     * im Weg stand: er bekommt das Ende des Vortags und damit ein Ende vor seinem Beginn.
     */
    @Test
    @DisplayName("Ein leerer Zeitraum gilt an keinem Tag")
    void anEmptyRangeNeverApplies() {
        List<PlanPeriod> zeitleiste = timeline(
                new PlanPeriod(1L, MO.plusDays(10), MO.minusDays(1)),
                new PlanPeriod(2L, MO, null));

        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(10))).isEqualTo(2L);
        assertThat(PlanReader.timetableIdAt(zeitleiste, MO.plusDays(20))).isEqualTo(2L);
    }

    @Test
    @DisplayName("Bei gleichem Beginn gewinnt die hoehere Kennung — der zuletzt angenommene Plan")
    void theLaterAcceptedPlanWinsOnAnEqualStart() {
        List<PlanPeriod> zeitleiste = timeline(
                new PlanPeriod(1L, MO, null),
                new PlanPeriod(2L, MO, null));

        assertThat(PlanReader.timetableIdAt(zeitleiste, MO)).isEqualTo(2L);
    }
}
