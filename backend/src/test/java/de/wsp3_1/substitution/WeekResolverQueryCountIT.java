package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Die Messgrundlage zu Befund BACKEND-5: <b>wie viele</b> Abfragen eine Auflösung kostet.
 *
 * <p>Die Zahl selbst ist nicht die Zusicherung — sie haengt an der Fixture. Zugesichert wird der
 * Verlauf: die Kosten duerfen <b>nicht mit der Zahl der Tage</b> und <b>nicht mit der Zahl der
 * Ereignisse</b> wachsen. Genau das taten sie bis zum 05.09.: jeder Tag fragte alle jemals
 * angenommenen Plaene und alle Stunden des geltenden neu, und {@code GET /api/events} loeste den
 * Zeitraum jedes Ereignisses einzeln auf (Ereignisse x Tage vollstaendige Neuladungen).</p>
 *
 * <p>Gemessen wird mit Hibernates eigener Statistik. Sie wird hier zur Laufzeit eingeschaltet und
 * nicht ueber eine Eigenschaft: eine zusaetzliche {@code @TestPropertySource} erzeugte einen zweiten
 * Spring-Kontext und verdoppelte die Laufzeit aller Integrationstests.</p>
 */
class WeekResolverQueryCountIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);

    @Autowired
    private EntityManagerFactory emf;

    @Test
    @DisplayName("BACKEND-5: vier Wochen aufloesen kostet nicht mehr Abfragen als eine")
    void resolvingMoreDaysDoesNotCostMoreQueries() throws Exception {
        planDerWoche();

        long eineWoche = queriesOf("/api/substitutions/pending?from=" + MONTAG
                + "&to=" + MONTAG.plusDays(6));
        long vierWochen = queriesOf("/api/substitutions/pending?from=" + MONTAG
                + "&to=" + MONTAG.plusDays(27));

        // Vier Wochen laden dieselben Grunddaten, dieselbe Zeitleiste und denselben Plan wie eine.
        // Vor der Korrektur stiegen die Abfragen mit jedem Tag; die Grenze faengt das ab, ohne sich an
        // eine feste Zahl zu binden.
        assertThat(vierWochen)
                .as("vier Wochen (%d Abfragen) gegen eine Woche (%d)", vierWochen, eineWoche)
                .isLessThanOrEqualTo(eineWoche + 3);
    }

    @Test
    @DisplayName("BACKEND-5: acht Ereignisse aufzulisten kostet nicht das Achtfache von einem")
    void listingMoreEventsDoesNotCostMoreQueries() throws Exception {
        Testdaten daten = planDerWoche();
        absence(daten.meyer, MONTAG);
        long eines = queriesOf("/api/events");

        for (int tag = 1; tag < 8; tag++) {
            absence(daten.meyer, MONTAG.plusDays(tag));
        }
        long achte = queriesOf("/api/events");

        assertThat(achte)
                .as("acht Ereignisse (%d Abfragen) gegen eines (%d)", achte, eines)
                .isLessThanOrEqualTo(eines + 3);
    }

    /** Die Zahl der Abfragen, die dieser eine Aufruf ausloest. */
    private long queriesOf(String path) throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        getJson(path);
        long count = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);
        // Die Zahl gehoert in den Testbericht: sie ist die Messgrundlage, auf die sich die Befunde
        // BACKEND-4 und BACKEND-5 berufen, und ohne Ausgabe stuende sie nur im Fehlerfall da.
        System.out.printf("[Abfragen] %s -> %d%n", path, count);
        return count;
    }

    private void absence(long teacherId, LocalDate date) throws Exception {
        call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(teacherId, date, date)), 201);
    }

    private Testdaten planDerWoche() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        return daten;
    }
}
