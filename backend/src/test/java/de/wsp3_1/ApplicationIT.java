package de.wsp3_1;

import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Der Grundtest: Startet die Anwendung gegen ein echtes PostgreSQL, laufen alle Migrationen durch
 * und passen die Entities zum Schema? {@code spring.jpa.hibernate.ddl-auto=validate} laesst den Kontext
 * gar nicht erst hochkommen, wenn eine Spalte fehlt — dieser Test schlaegt dann fehl.
 */
class ApplicationIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Flyway hat V1 bis V13 angewendet")
    void allMigrationsApplied() {
        List<String> versionen = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        // V10 = Rundgang (#139), V11 = mehrere Entwuerfe (#103), V12 = Gueltigkeitszeitraum und
        // Ablage (#115, #116). Die drei kamen aus verschiedenen Zweigen und stehen erst hier zusammen.
        assertThat(versionen).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    }

    /**
     * V2 wird aus 07-Konzept/recherche/bundesland-regelwerte.json erzeugt (scripts/gen-rule-presets.mjs):
     * je Bundesland fuenf schulweite Zeilen und bei zehn Laendern zusaetzlich Jahrgangszeilen — 138 gesamt.
     */
    @Test
    @DisplayName("Die Voreinstellungen umfassen 138 Zeilen ueber 16 Bundeslaender")
    void rulePresetsSeeded() {
        Integer zeilen = jdbc.queryForObject("SELECT count(*) FROM rule_preset", Integer.class);
        Integer laender = jdbc.queryForObject("SELECT count(DISTINCT state) FROM rule_preset", Integer.class);
        Integer schulweit = jdbc.queryForObject("SELECT count(*) FROM rule_preset WHERE grade IS NULL", Integer.class);
        Integer laenderMitJahrgang = jdbc.queryForObject(
                "SELECT count(DISTINCT state) FROM rule_preset WHERE grade IS NOT NULL", Integer.class);

        assertThat(laender).isEqualTo(16);
        assertThat(zeilen).isEqualTo(138);
        assertThat(schulweit).isEqualTo(80);
        assertThat(laenderMitJahrgang).isEqualTo(10);
    }

    /** Der Beispielsatz des vorbelegten Landes SH steht so im aktiven Regelwerk (V2, gleiche Quelle). */
    @Test
    @DisplayName("Das aktive Regelwerk traegt die SH-Werte der Voreinstellung")
    void ruleMatchesPresetOfConfiguredState() {
        Integer abweichungen = jdbc.queryForObject("""
                SELECT count(*) FROM rule r
                JOIN system_config c ON c.id = 1
                LEFT JOIN rule_preset p
                       ON p.state = c.state AND p.type = r.type
                      AND p.scope IS NOT DISTINCT FROM r.scope
                      AND p.grade IS NOT DISTINCT FROM r.grade
                WHERE p.id IS NULL OR p.value <> r.value OR p.severity <> r.severity OR p.weight <> r.weight
                """, Integer.class);

        assertThat(abweichungen).isZero();
    }

    @Test
    @DisplayName("Das aktive Regelwerk ist mit dem Beispielsatz vorbelegt")
    void ruleSeeded() {
        Integer aktiv = jdbc.queryForObject("SELECT count(*) FROM rule WHERE active", Integer.class);

        assertThat(aktiv).isEqualTo(3);
    }

    @Test
    @DisplayName("Die Erreichbarkeitspruefung nennt den Schulnamen aus der Systemkonfiguration")
    void healthReportsSchoolName() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Musterstadt"));
    }

    /**
     * Das Team-Beispiel bleibt bis zur Liveschaltung liegen, ist seit dem 05.09. aber nicht mehr
     * ohne Anmeldung erreichbar (VERTRAG, Phase backend1): {@code /api/clients/**} faellt jetzt
     * unter die Regel „alles Uebrige ist Planungssache".
     */
    @Test
    @DisplayName("Das Team-Beispiel antwortet weiterhin — nur nicht mehr ohne Anmeldung")
    void teamExampleStillAnswers() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(antwort).get("token").asText();

        mvc.perform(get("/api/clients"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
