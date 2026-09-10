package de.wsp3_1.rules;

import de.wsp3_1.rules.domain.RuleService;
import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Das Regelwerk ueber die Schnittstelle gegen echtes PostgreSQL — die Schritte R-1 bis R-3 der
 * Abnahme-Checkliste und die Randfaelle des Vertrags.
 *
 * <p>Alle Integrationstests teilen sich einen Spring-Kontext und eine Datenbank. Damit die Reihenfolge
 * der Testklassen egal bleibt, wird vor und nach jedem Test der Beispielsatz hergestellt — das ist
 * derselbe Stand, den der Seed V2 anlegt.</p>
 */
class RuleControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RuleService ruleService;

    private String token;

    @BeforeEach
    void beispielsatzHerstellen() {
        ruleService.reset();
    }

    @AfterEach
    void beispielsatzWiederherstellen() {
        ruleService.reset();
    }

    @BeforeEach
    void anmelden() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(antwort).get("token").asText();
    }

    private JsonNode regeln() throws Exception {
        String antwort = mvc.perform(get("/api/rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort);
    }

    /** Die Kennung der Regelzeile mit diesem Typ und dieser Zaehleinheit, schulweit. */
    private long id(String type, String scope) throws Exception {
        JsonNode regeln = regeln();
        for (int i = 0; i < regeln.size(); i++) {
            JsonNode regel = regeln.get(i);
            if (regel.get("type").asText().equals(type)
                    && regel.get("grade").isNull()
                    && (scope == null ? regel.get("scope").isNull() : scope.equals(regel.get("scope").asText()))) {
                return regel.get("id").asLong();
            }
        }
        throw new AssertionError("Keine schulweite Regel " + type + " / " + scope);
    }

    /** Abnahme R-1: die beiden Regeln des Pflichtenhefts stehen mit ihren Werten im Regelwerk. */
    @Test
    @DisplayName("R-1: das Regelwerk traegt die fuenf Zeilen des Beispielsatzes SH")
    void listRules_afterSeed_hasTheSchleswigHolsteinPreset() throws Exception {
        mvc.perform(get("/api/rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                // Reihenfolge: schulweit, Klasse vor Lehrkraft vor Pausenregel, je Tag vor Woche
                .andExpect(jsonPath("$[0].type").value("MAX_LESSONS_PER_DAY"))
                .andExpect(jsonPath("$[0].scope").value("CLASS"))
                .andExpect(jsonPath("$[0].grade").doesNotExist())
                .andExpect(jsonPath("$[0].value").value(6))
                .andExpect(jsonPath("$[0].severity").value("SOFT"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].label").value("Maximale Stunden pro Tag"))
                .andExpect(jsonPath("$[1].type").value("MAX_LESSONS_PER_WEEK"))
                .andExpect(jsonPath("$[1].scope").value("CLASS"))
                .andExpect(jsonPath("$[1].value").value(30))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[1].label").value("Maximale Stunden pro Woche"))
                // Lehrkraft-Regeln sind vorhanden, aber ausgeschaltet
                .andExpect(jsonPath("$[2].scope").value("TEACHER"))
                .andExpect(jsonPath("$[2].value").value(6))
                .andExpect(jsonPath("$[2].active").value(false))
                .andExpect(jsonPath("$[3].scope").value("TEACHER"))
                .andExpect(jsonPath("$[3].value").value(26))
                .andExpect(jsonPath("$[3].active").value(false))
                // Die Pausenregel ist hart und hat kein Objekt
                .andExpect(jsonPath("$[4].type").value("NO_LESSONS_IN_BREAKS"))
                .andExpect(jsonPath("$[4].scope").doesNotExist())
                .andExpect(jsonPath("$[4].severity").value("HARD"))
                .andExpect(jsonPath("$[4].active").value(true))
                .andExpect(jsonPath("$[4].label").value("Keine Stunden in Pausen"));
    }

    /** Abnahme R-2: ein Wert kleiner als 1 wird abgelehnt und der gespeicherte Wert bleibt stehen. */
    @Test
    @DisplayName("R-2: Wert 0 ergibt 400 mit Feldfehler, die 6 bleibt gespeichert")
    void updateRule_valueZero_isRejectedAndKeepsOldValue() throws Exception {
        long id = id("MAX_LESSONS_PER_DAY", "CLASS");

        mvc.perform(put("/api/rules/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":0,"severity":"SOFT","weight":1,"active":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("value"))
                .andExpect(jsonPath("$.errors[0].message").value("muss mindestens 1 sein"));

        assertThat(regeln().get(0).get("value").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("Ein gueltiger Wert wird gespeichert und ueberlebt das naechste Lesen (T-W2.1, Schritt 1)")
    void updateRule_validValue_isStored() throws Exception {
        long id = id("MAX_LESSONS_PER_DAY", "CLASS");

        mvc.perform(put("/api/rules/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":3,"severity":"SOFT","weight":1,"active":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.value").value(3))
                .andExpect(jsonPath("$.label").value("Maximale Stunden pro Tag"));

        assertThat(regeln().get(0).get("value").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("Die Pausenregel behaelt den Wert 1, was auch gesendet wird")
    void updateRule_breakRule_keepsValueOne() throws Exception {
        long id = id("NO_LESSONS_IN_BREAKS", null);

        mvc.perform(put("/api/rules/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":9,"severity":"HARD","weight":1,"active":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(1));
    }

    @Test
    @DisplayName("Eine unbekannte Regel ergibt 404")
    void updateRule_unknownId_isNotFound() throws Exception {
        mvc.perform(put("/api/rules/9999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":5,"severity":"SOFT","weight":1,"active":true}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }

    /**
     * Abnahme R-3: der Beispielsatz des eingestellten Bundeslands ersetzt das Regelwerk. Verglichen wird
     * gegen die Tabelle {@code rule_preset} — nicht gegen Zahlen im Test, damit eine Wertkorrektur in
     * einer spaeteren Migration hier nichts zerbricht.
     */
    @Test
    @DisplayName("R-3: Beispielsatz laden stellt genau die Werte der Voreinstellung her")
    void resetRules_restoresThePresetOfTheConfiguredState() throws Exception {
        long id = id("MAX_LESSONS_PER_DAY", "CLASS");
        mvc.perform(put("/api/rules/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":3,"severity":"HARD","weight":4,"active":false}"""))
                .andExpect(status().isOk());

        mvc.perform(post("/api/rules/reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(id))          // Kennung bleibt
                .andExpect(jsonPath("$[0].value").value(6))
                .andExpect(jsonPath("$[0].severity").value("SOFT"))
                .andExpect(jsonPath("$[0].weight").value(1))
                .andExpect(jsonPath("$[0].active").value(true));

        List<Map<String, Object>> voreinstellung = jdbc.queryForList("""
                SELECT p.type, p.scope, p.value, p.severity, p.weight
                  FROM rule_preset p JOIN system_config c ON c.id = 1 AND c.state = p.state
                 WHERE p.grade IS NULL
                 ORDER BY p.id
                """);
        assertThat(voreinstellung).hasSize(5);
        Integer abweichungen = jdbc.queryForObject("""
                SELECT count(*) FROM rule r
                  JOIN system_config c ON c.id = 1
                  LEFT JOIN rule_preset p ON p.state = c.state AND p.type = r.type
                       AND p.scope IS NOT DISTINCT FROM r.scope
                       AND p.grade IS NOT DISTINCT FROM r.grade
                 WHERE p.id IS NULL OR p.value <> r.value OR p.severity <> r.severity OR p.weight <> r.weight
                """, Integer.class);
        assertThat(abweichungen).isZero();
    }

    @Test
    @DisplayName("Beispielsatz laden entfernt eigene Jahrgangswerte")
    void resetRules_dropsOwnGradeRules() throws Exception {
        mvc.perform(post("/api/rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","grade":5,"value":5,"severity":"SOFT","weight":2,"active":true}"""))
                .andExpect(status().isCreated());
        assertThat(regeln().size()).isEqualTo(6);

        mvc.perform(post("/api/rules/reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("Ein Jahrgangswert entsteht, steht hinter den schulweiten Zeilen und laesst sich loeschen")
    void createAndDeleteGradeRule() throws Exception {
        String antwort = mvc.perform(post("/api/rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","grade":5,"value":5,"severity":"SOFT","weight":2,"active":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grade").value(5))
                .andExpect(jsonPath("$.value").value(5))
                .andExpect(jsonPath("$.weight").value(2))
                .andExpect(jsonPath("$.label").value("Maximale Stunden pro Tag"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(antwort).get("id").asLong();

        JsonNode nachAnlegen = regeln();
        assertThat(nachAnlegen.size()).isEqualTo(6);
        assertThat(nachAnlegen.get(5).get("grade").asInt()).isEqualTo(5);

        mvc.perform(delete("/api/rules/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(regeln().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Derselbe Jahrgangswert ein zweites Mal ergibt 409")
    void createRule_duplicate_isConflict() throws Exception {
        String koerper = """
                {"type":"MAX_LESSONS_PER_WEEK","scope":"CLASS","grade":7,"value":33,"severity":"SOFT","weight":1,"active":true}""";
        mvc.perform(post("/api/rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(koerper))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(koerper))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:data-exists"));
    }

    @Test
    @DisplayName("Ohne Jahrgang gibt es keinen Jahrgangswert: 400 mit Feldfehler")
    void createRule_withoutGrade_isValidationError() throws Exception {
        mvc.perform(post("/api/rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","value":5,"severity":"SOFT","weight":1,"active":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("grade"));
    }

    @Test
    @DisplayName("Die Pausenregel gibt es nicht je Jahrgang: 400 mit zwei Feldfehlern")
    void createRule_breakRulePerGrade_isValidationError() throws Exception {
        mvc.perform(post("/api/rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"NO_LESSONS_IN_BREAKS","scope":"TEACHER","grade":5,"value":1,"severity":"HARD","weight":1,"active":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[1].field").value("scope"));
    }

    @Test
    @DisplayName("Eine schulweite Regel wird ausgeschaltet, nicht geloescht: 409")
    void deleteRule_schoolWide_isConflict() throws Exception {
        long id = id("MAX_LESSONS_PER_WEEK", "CLASS");

        mvc.perform(delete("/api/rules/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:in-use"));

        assertThat(regeln().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Ohne Token bleibt das Regelwerk verschlossen")
    void listRules_withoutToken_isUnauthenticated() throws Exception {
        mvc.perform(get("/api/rules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }
}
