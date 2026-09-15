package de.wsp3_1.transfer;

import de.wsp3_1.rules.domain.RuleService;
import de.wsp3_1.sampledata.domain.SampleDataLoader;
import de.wsp3_1.shared.IntegrationTestBase;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewHoliday;
import de.wsp3_1.substitution.domain.HolidayKind;
import de.wsp3_1.substitution.domain.HolidayService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gemeinsame Vorbereitung der Transfer-Integrationstests: leere Datenbank mit dem vollen
 * Beispielbestand (12 Klassen, 24 Lehrkraefte, 18 Raeume), dem Beispielsatz SH als Regelwerk
 * und einer Ferienzeit — so zeigt der Export alle sieben Bereiche.
 *
 * <p>Der Bestand kommt aus {@link SampleDataLoader#loadFullMasterData()} — das ist der Weg
 * <b>ohne</b> Solver-Lauf (der volle {@code loadFull()} braucht ~10 s und einen angenommenen
 * Plan, den diese Tests nicht brauchen). Konten legt der Loader nicht an; das Konto
 * {@code admin} stammt aus {@code AdminBootstrap} und bleibt unberuehrt.</p>
 *
 * <p>Alle Integrationstests teilen einen Spring-Kontext und eine Datenbank. Deshalb wird vor
 * jedem Test geleert und neu geladen — sonst haengen die Ergebnisse an der Reihenfolge der
 * Testklassen.</p>
 */
abstract class TransferITBase extends IntegrationTestBase {

    @Autowired protected ObjectMapper objectMapper;

    @Autowired private SampleDataLoader sampleData;
    @Autowired private RuleService rules;
    @Autowired private HolidayService holidays;

    protected String token;

    @BeforeEach
    void resetUndAnmelden() throws Exception {
        sampleData.clear();
        sampleData.loadFullMasterData();
        // Der Loader laesst das Regelwerk in Ruhe; hier stellen wir den Beispielsatz SH her
        // (5 Zeilen: 3 aktiv, 2 Lehrkraft-Zeilen aus).
        rules.reset();
                holidays.create(new NewHoliday("Herbstferien",
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 31), HolidayKind.VACATION));
        token = login();
    }

    private String login() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort).get("token").asText();
    }

    protected MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    /** GET /api/masterdata/export — Antwort als Baum. */
    protected JsonNode exportiere() throws Exception {
        String body = mvc.perform(authorized(get("/api/masterdata/export")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** POST /api/masterdata/import — erwartet den uebergebenen Status, liefert die Antwort (leer -> leeres Objekt). */
    protected JsonNode importiere(JsonNode doc, int erwarteterStatus) throws Exception {
        String body = mvc.perform(authorized(post("/api/masterdata/import"))
                        .content(objectMapper.writeValueAsString(doc)))
                .andExpect(status().is(erwarteterStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }
}