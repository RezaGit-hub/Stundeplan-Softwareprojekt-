package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Die mitgelieferte Vorlage „Ferien und Feiertage je Bundesland" (E-51):
 * {@code GET /api/holidays/preset-years} und {@code POST /api/holidays/preset}.
 *
 * <p>Gemessen wird beides — die Zeilen der Migration V8 (kommen sie vollstaendig und mit den Regeln
 * der Ableitung an?) und der Weg darueber (uebernimmt der Endpunkt sie, und tut ein zweiter Aufruf
 * nichts mehr?). Die Zahlen stehen so im Bericht {@code 07-Konzept/recherche/} und im Kopf von
 * {@code V8__holiday_presets.sql}; weicht die Recherche ab, faellt es hier auf.</p>
 */
class HolidayPresetIT extends PlanningITBase {

    /** Wie in V8: 16 Bundeslaender, zwei Schuljahre, 523 Zeilen. */
    private static final int ZEILEN_GESAMT = 523;
    private static final List<String> SCHULJAHRE = List.of("2026/27", "2027/28");

    /** Schleswig-Holstein ist das vorbelegte Bundesland (E-37): vier Ferienbloecke, zehn Feiertage. */
    private static final int SH_2026_27 = 14;

    @AfterEach
    void bundeslandZurueckstellen() {
        // Alle Integrationstests teilen eine Datenbank; ConfigIT prueft die Vorbelegung SH.
        jdbc.update("UPDATE system_config SET state = 'SH' WHERE id = 1");
    }

    // ------------------------------------------------------------------ die Zeilen der Migration

    @Test
    @DisplayName("V8 liefert 523 Zeilen: 16 Bundeslaender x zwei Schuljahre, jedes mit Ferien und Feiertagen")
    void theSeedCoversEveryStateAndSchoolYear() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM holiday_preset", Integer.class))
                .isEqualTo(ZEILEN_GESAMT);
        assertThat(jdbc.queryForList("SELECT DISTINCT state FROM holiday_preset", String.class))
                .hasSize(16);
        assertThat(jdbc.queryForList("SELECT DISTINCT school_year FROM holiday_preset ORDER BY 1", String.class))
                .containsExactlyElementsOf(SCHULJAHRE);

        // Jede der 32 Kombinationen hat mindestens drei Ferienbloecke und neun Feiertage — die neun
        // bundesweiten Feiertage stehen laut Recherche in jedem Land.
        for (String schuljahr : SCHULJAHRE) {
            // Das Schuljahr kommt aus der Konstante oben, nicht aus einer Eingabe — hier reicht der Literaltext
            // (die Ueberladung von queryForList mit Platzhalter-Argumenten ist in Spring 7 abgekuendigt).
            List<String> zuDuenn = jdbc.queryForList("""
                    SELECT state FROM holiday_preset WHERE school_year = '%s'
                    GROUP BY state
                    HAVING count(*) FILTER (WHERE kind = 'VACATION') < 3
                        OR count(*) FILTER (WHERE kind = 'PUBLIC_HOLIDAY') < 9""".formatted(schuljahr),
                    String.class);
            assertThat(zuDuenn).as("Bundeslaender ohne vollstaendige Vorlage in " + schuljahr).isEmpty();
        }

        // Der Schluessel, ueber den das Uebernehmen Duplikate erkennt, ist in der Vorlage eindeutig.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT state, school_year, date_from, date_to, label FROM holiday_preset
                    GROUP BY 1, 2, 3, 4, 5 HAVING count(*) > 1) doppelt""", Integer.class))
                .isZero();

        // Jede Zeile nennt ihre Fundstelle (R1..R5 im Kopf der Migration).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM holiday_preset WHERE source NOT LIKE 'http%'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("R1: Feiertage, die nur in einem Teilgebiet gelten, stehen nicht in der Vorlage")
    void regionalHolidaysAreNotPartOfTheStatewidePreset() {
        // Die drei Teilgebiets-Faelle der Recherche: Augsburger Friedensfest (nur Stadt Augsburg),
        // Mariae Himmelfahrt (katholische Gemeinden Bayerns), Fronleichnam in Teilen von SN und TH.
        assertThat(jdbc.queryForList(
                "SELECT label FROM holiday_preset WHERE label LIKE '%Friedensfest%' OR label LIKE '%Himmelfahrt (%'",
                String.class)).isEmpty();
        assertThat(jdbc.queryForList(
                "SELECT state FROM holiday_preset WHERE label = 'Fronleichnam'", String.class))
                .doesNotContain("SN", "TH")
                // landesweit gilt Fronleichnam laut Recherche in sechs Laendern
                .contains("BW", "BY", "HE", "NW", "RP", "SL");
    }

    @Test
    @DisplayName("R4: die Sommerferien stehen im Schuljahr, in dem sie beginnen — auch ueber den Wechsel hinweg")
    void summerVacationBelongsToTheSchoolYearItStartsIn() {
        // BW 2026/27 beginnt am 29.07.2027, also zwei Tage VOR dem Schuljahreswechsel, und laeuft
        // bis in den September hinein: der Block bleibt beim beginnenden Schuljahr.
        assertThat(jdbc.queryForList("""
                SELECT date_from || '..' || date_to FROM holiday_preset
                WHERE state = 'BW' AND school_year = '2026/27' AND label = 'Sommerferien'""", String.class))
                .containsExactly("2027-07-29..2027-09-11");
        // Ausnahme Bayern: die Sommerferien 2026/27 beginnen erst am 02.08.2027 und bleiben trotzdem
        // bei 2026/27, wie die KMK sie fuehrt — sonst haette Bayern dort keine Sommerferien.
        assertThat(jdbc.queryForList("""
                SELECT date_from || '..' || date_to FROM holiday_preset
                WHERE state = 'BY' AND school_year = '2026/27' AND label = 'Sommerferien'""", String.class))
                .containsExactly("2027-08-02..2027-09-13");
    }

    // ------------------------------------------------------------------ die Endpunkte

    @Test
    @DisplayName("Die Auswahlliste nennt die Schuljahre des eingestellten Bundeslands")
    void thePresetYearsFollowTheConfiguredState() throws Exception {
        JsonNode jahre = getJson("/api/holidays/preset-years");
        assertThat(jahre.get("state").asText()).isEqualTo("SH");
        assertThat(jahre.get("schoolYears")).hasSize(2);
        assertThat(jahre.get("schoolYears").get(0).asText()).isEqualTo("2026/27");

        setState("BY");
        assertThat(getJson("/api/holidays/preset-years").get("state").asText()).isEqualTo("BY");
    }

    @Test
    @DisplayName("SH 2026/27: 14 Zeitraeume, darunter die Sommerferien; ein zweiter Aufruf legt nichts an")
    void loadingThePresetIsIdempotent() throws Exception {
        JsonNode ergebnis = call(post("/api/holidays/preset").content("""
                {"schoolYear":"2026/27"}"""), 200);
        assertThat(ergebnis.get("state").asText()).isEqualTo("SH");
        assertThat(ergebnis.get("schoolYear").asText()).isEqualTo("2026/27");
        assertThat(ergebnis.get("added").asInt()).isEqualTo(SH_2026_27);
        assertThat(ergebnis.get("skipped").asInt()).isZero();

        JsonNode liste = getJson("/api/holidays");
        assertThat(liste).hasSize(SH_2026_27);
        // Die Sommerferien laut Recherche (KMK-Ferienkalender 2026/27): 03.07.–14.08.2027.
        JsonNode sommer = null;
        for (JsonNode zeitraum : liste) {
            if ("Sommerferien".equals(zeitraum.get("name").asText())) {
                sommer = zeitraum;
            }
        }
        assertThat(sommer).as("Sommerferien in der Vorlage SH 2026/27").isNotNull();
        assertThat(sommer.get("dateFrom").asText()).isEqualTo("2027-07-03");
        assertThat(sommer.get("dateTo").asText()).isEqualTo("2027-08-14");
        assertThat(sommer.get("kind").asText()).isEqualTo("VACATION");
        // Ein Feiertag kommt als eintaegiger Zeitraum mit der anderen Art.
        assertThat(liste.toString()).contains("\"Tag der Deutschen Einheit\"").contains("PUBLIC_HOLIDAY");

        JsonNode zweiterLauf = call(post("/api/holidays/preset").content("""
                {"schoolYear":"2026/27"}"""), 200);
        assertThat(zweiterLauf.get("added").asInt()).isZero();
        assertThat(zweiterLauf.get("skipped").asInt()).isEqualTo(SH_2026_27);
        assertThat(getJson("/api/holidays")).hasSize(SH_2026_27);
    }

    @Test
    @DisplayName("Das zweite Schuljahr kommt dazu, ein von Hand geloeschter Eintrag bleibt weg")
    void thePresetAddsToWhatIsAlreadyThereAndStaysEditable() throws Exception {
        call(post("/api/holidays/preset").content("""
                {"schoolYear":"2026/27"}"""), 200);
        JsonNode zweitesJahr = call(post("/api/holidays/preset").content("""
                {"schoolYear":"2027/28"}"""), 200);
        assertThat(zweitesJahr.get("added").asInt()).isEqualTo(14);
        assertThat(getJson("/api/holidays")).hasSize(2 * SH_2026_27);

        // Was uebernommen ist, ist ein Zeitraum wie jeder andere: einzeln loeschbar.
        long id = getJson("/api/holidays").get(0).get("id").asLong();
        call(delete("/api/holidays/" + id), 204);
        assertThat(getJson("/api/holidays")).hasSize(2 * SH_2026_27 - 1);
    }

    @Test
    @DisplayName("Ein Bundesland mit eigener Vorlage bringt seine eigenen Zeitraeume mit")
    void anotherStateBringsItsOwnPeriods() throws Exception {
        setState("BY");
        JsonNode ergebnis = call(post("/api/holidays/preset").content("""
                {"schoolYear":"2026/27"}"""), 200);
        assertThat(ergebnis.get("state").asText()).isEqualTo("BY");
        assertThat(ergebnis.get("added").asInt()).isEqualTo(18);
        // Bayern hat als einziges Land Winterferien um Aschermittwoch — und kein Augsburger Friedensfest.
        String liste = getJson("/api/holidays").toString();
        assertThat(liste).contains("Winterferien");
        assertThat(liste).doesNotContain("Friedensfest");
    }

    @Test
    @DisplayName("Ohne Vorlage zu Land und Schuljahr: 409 preset-missing, und nichts wird angelegt")
    void anUnknownSchoolYearIsAConflict() throws Exception {
        JsonNode problem = call(post("/api/holidays/preset").content("""
                {"schoolYear":"2030/31"}"""), 409);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:preset-missing");
        assertThat(problem.get("detail").asText()).contains("2026/27");
        assertThat(getJson("/api/holidays")).isEmpty();

        // Eine Schreibweise, die kein Schuljahr ist, faengt schon die Bean-Validation ab.
        JsonNode ungueltig = call(post("/api/holidays/preset").content("""
                {"schoolYear":"2026"}"""), 400);
        assertThat(ungueltig.get("errors").get(0).get("field").asText()).isEqualTo("schoolYear");
    }

    /** Stellt das Bundesland der Systemkonfiguration um — die Vorlage folgt ihm. */
    private void setState(String state) throws Exception {
        JsonNode config = getJson("/api/config");
        call(put("/api/config").content("""
                {"schoolName":"%s","state":"%s","schoolType":"%s","maxSolveSeconds":%d}"""
                .formatted(config.get("schoolName").asText(), state,
                        config.get("schoolType").asText(), config.get("maxSolveSeconds").asInt())), 200);
    }
}
