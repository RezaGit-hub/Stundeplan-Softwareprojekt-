package de.wsp3_1.sampledata;

import de.wsp3_1.sampledata.domain.SampleDataLoader;
import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der kleine Bestand des Pflichtenhefts als Testfixture: Meyer/Schulz/Weber, 7a/8b/9c, R101/R204/P1,
 * 35 Zeitfenster und der angenommene Plan aus dem Testdurchlauf (sieben Stunden Montag und Dienstag).
 * Die anderen Module bauen ihre Integrationstests darauf auf.
 *
 * <p>Braucht eine Datenbank und traegt deshalb {@code IT} im Namen (docs/VERTRAG.md nennt die Klasse
 * {@code SmallFixtureTest}; ohne Datenbank laesst sich der Bestand nicht anlegen).</p>
 */
class SmallFixtureIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SampleDataLoader loader;

    @BeforeEach
    void vorherLeeren() {
        loader.clear();
    }

    @AfterEach
    void nachherLeeren() {
        loader.clear();
    }

    @Test
    @DisplayName("Der kleine Bestand hat drei Lehrkraefte, drei Klassen, drei Raeume und 35 Zeitfenster")
    void smallFixtureMatchesPflichtenheft() {
        SampleDataLoader.Counts counts = loader.loadSmall();

        assertThat(counts).isEqualTo(new SampleDataLoader.Counts(3, 3, 3));
        assertThat(jdbc.queryForList("SELECT short_name FROM teacher ORDER BY id", String.class))
                .containsExactly("MEY", "SCZ", "WEB");
        assertThat(jdbc.queryForList("SELECT name FROM school_class ORDER BY id", String.class))
                .containsExactly("7a", "8b", "9c");
        assertThat(jdbc.queryForList("SELECT name FROM room ORDER BY id", String.class))
                .containsExactly("R101", "R204", "P1");
        assertThat(jdbc.queryForList("SELECT short_name FROM subject ORDER BY id", String.class))
                .containsExactly("Ma", "De", "Ph");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timeslot", Integer.class)).isEqualTo(35);

        // Deputate: Meyer 25, Schulz 2, Weber 25 — Schulz ist mit einer Wochenstunde noch darunter.
        assertThat(jdbc.queryForObject(
                "SELECT weekly_hours_quota FROM teacher WHERE short_name = 'SCZ'", Integer.class)).isEqualTo(2);

        // Physik verlangt den Fachraum, den es genau einmal gibt.
        assertThat(jdbc.queryForObject(
                "SELECT required_room_type FROM subject WHERE short_name = 'Ph'", String.class))
                .isEqualTo("Fachraum Physik");
    }

    @Test
    @DisplayName("Schulz ist freitags in der 5. und 6. Stunde gesperrt (Position 6 und 7)")
    void schulzIsBlockedOnFridayAfternoon() {
        loader.loadSmall();

        List<Map<String, Object>> gesperrt = jdbc.queryForList("""
                SELECT ts.day, ts.position FROM teacher_blocked_timeslot b
                JOIN teacher t ON t.id = b.teacher_id
                JOIN timeslot ts ON ts.id = b.timeslot_id
                WHERE t.short_name = 'SCZ' ORDER BY ts.position
                """);

        assertThat(gesperrt).hasSize(2);
        assertThat(gesperrt).allSatisfy(zeile -> assertThat(zeile.get("day")).isEqualTo("FR"));
        assertThat(gesperrt.stream().map(zeile -> zeile.get("position"))).containsExactly(6, 7);
    }

    @Test
    @DisplayName("Der Plan des Testdurchlaufs hat sieben Stunden; Meyer hat montags vier und dienstags eine")
    void activePlanHasSevenLessons() {
        loader.loadSmallWithActivePlan();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson", Integer.class)).isEqualTo(7);
        // Invariante „belegt oder offen": im angenommenen Plan ist jede Stunde belegt.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM lesson WHERE timeslot_id IS NULL OR room_id IS NULL", Integer.class)).isZero();

        List<Map<String, Object>> proTag = jdbc.queryForList("""
                SELECT ts.day, count(*) AS stunden FROM lesson l
                JOIN class_subject cs ON cs.id = l.class_subject_id
                JOIN teacher t ON t.id = cs.teacher_id
                JOIN timeslot ts ON ts.id = l.timeslot_id
                WHERE t.short_name = 'MEY' GROUP BY ts.day ORDER BY ts.day DESC
                """);

        assertThat(proTag).hasSize(2);
        assertThat(proTag.get(0)).containsEntry("day", "TU");
        assertThat(((Number) proTag.get(0).get("stunden")).intValue()).isEqualTo(1);
        assertThat(proTag.get(1)).containsEntry("day", "MO");
        assertThat(((Number) proTag.get(1).get("stunden")).intValue()).isEqualTo(4);

        // Die Mathematikstunde der 9c ist im Testdurchlauf noch nicht eingeplant (offene Stunde).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM lesson l
                JOIN class_subject cs ON cs.id = l.class_subject_id
                JOIN school_class c ON c.id = cs.class_id
                WHERE c.name = '9c'
                """, Integer.class)).isZero();
    }
}
