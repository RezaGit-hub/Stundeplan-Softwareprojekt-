package de.wsp3_1.transfer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Import der Grunddaten gegen echtes PostgreSQL.
 *
 * <p>Die Faelle folgen der Zusage aus dem Vertrag: alles laeuft in <b>einer</b> Transaktion.
 * Schlaegt irgendetwas fehl — eine unbekannte Kennung, ein doppeltes Kuerzel, eine unbekannte
 * Format-Version — bleibt der alte Bestand stehen. Ohne {@code replace} und bei vorhandenen
 * Grunddaten antwortet der Server mit 409 {@code data-exists}, genau wie
 * {@code POST /api/sample-data}.</p>
 */
class MasterDataImportIT extends TransferITBase {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Roundtrip: exportieren, mit replace einspielen — gleiche Zahlen, Konto admin bleibt")
    void roundtripErhaeltDenBestand() throws Exception {
        JsonNode doc1 = exportiere();
        ObjectNode body = (ObjectNode) doc1.deepCopy();
        body.put("replace", true);

        JsonNode ergebnis = importiere(body, 200);

        // Zaehlung der Antwort stimmt mit dem Dokument ueberein.
        assertThat(ergebnis.get("subjects").asInt()).isEqualTo(doc1.get("subjects").size());
        assertThat(ergebnis.get("teachers").asInt()).isEqualTo(doc1.get("teachers").size());
        assertThat(ergebnis.get("rooms").asInt()).isEqualTo(doc1.get("rooms").size());
        assertThat(ergebnis.get("classes").asInt()).isEqualTo(doc1.get("classes").size());
        assertThat(ergebnis.get("timeslots").asInt()).isEqualTo(doc1.get("timeslots").size());
        assertThat(ergebnis.get("rules").asInt()).isEqualTo(doc1.get("rules").size());
        assertThat(ergebnis.get("holidays").asInt()).isEqualTo(doc1.get("holidays").size());
        assertThat(ergebnis.get("warnings")).isEmpty();

        // Zweiter Export: fachlich derselbe Bestand. Die Kennungen duerfen sich unterscheiden
        // (jede Zeile wurde neu angelegt), die fachlichen Schluessel nicht.
        JsonNode doc2 = exportiere();
        assertThat(feldWerte(doc2.get("subjects"), "shortName"))
                .isEqualTo(feldWerte(doc1.get("subjects"), "shortName"));
        assertThat(feldWerte(doc2.get("teachers"), "shortName"))
                .isEqualTo(feldWerte(doc1.get("teachers"), "shortName"));
        assertThat(feldWerte(doc2.get("rooms"), "name"))
                .isEqualTo(feldWerte(doc1.get("rooms"), "name"));
        assertThat(feldWerte(doc2.get("classes"), "name"))
                .isEqualTo(feldWerte(doc1.get("classes"), "name"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_account WHERE username = 'admin'", Integer.class))
                .as("Konto admin ueberlebt den Import").isEqualTo(1);
    }

    @Test
    @DisplayName("Ohne replace und bei vorhandenen Daten: 409 data-exists, Bestand unverändert")
    void ohneReplaceBeiVorhandenenDaten409() throws Exception {
        JsonNode doc = exportiere();
        ObjectNode body = (ObjectNode) doc.deepCopy();
        body.put("replace", false);

        importiere(body, 409);

        // Der vorhandene Bestand steht unveraendert da.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teacher", Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subject", Integer.class)).isEqualTo(12);
    }

    @Test
    @DisplayName("Unbekannte Format-Version: 400 am Feld version, nichts geändert")
    void unbekannteVersion400() throws Exception {
        ObjectNode doc = leeresDokument();
        doc.put("version", "99");

        JsonNode problem = importiere(doc, 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("version");
        // Nichts angefasst.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teacher", Integer.class)).isEqualTo(24);
    }

    @Test
    @DisplayName("Ein leeres Dokument mit replace leert den Bestand; Konto admin bleibt")
    void leeresDokumentMitReplaceLeertDenBestand() throws Exception {
        importiere(leeresDokument(), 200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM teacher", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subject", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM school_class", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timeslot", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rule", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM holiday", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_account WHERE role = 'PLANNING'", Integer.class))
                .as("das Konto der Planung bleibt").isEqualTo(1);
    }

    @Test
    @DisplayName("Verweis ins Leere: 400, alter Bestand bleibt (Transaktion zurückgerollt)")
    void verweisInsLeere400() throws Exception {
        long vorherLehrer = jdbc.queryForObject("SELECT count(*) FROM teacher", Long.class);
        long vorherFaecher = jdbc.queryForObject("SELECT count(*) FROM subject", Long.class);

        JsonNode doc = exportiere();
        ObjectNode body = (ObjectNode) doc.deepCopy();
        body.put("replace", true);
        ((ObjectNode) body.get("classes").get(0).get("subjects").get(0)).put("teacherId", 999_999L);

        JsonNode problem = importiere(body, 400);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");

        // Beweis, dass die Transaktion wirklich zurueckgerollt wurde: der Clear-Schritt haette
        // alle Zeilen entfernt, aber der alte Bestand steht unveraendert.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teacher", Long.class)).isEqualTo(vorherLehrer);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subject", Long.class)).isEqualTo(vorherFaecher);
    }

    @Test
    @DisplayName("Doppeltes Kürzel im Dokument: 400, alter Bestand bleibt")
    void doppeltesKuerzel400() throws Exception {
        long vorher = jdbc.queryForObject("SELECT count(*) FROM subject", Long.class);

        JsonNode doc = exportiere();
        ObjectNode body = (ObjectNode) doc.deepCopy();
        body.put("replace", true);
        // Das Kuerzel des ersten Fachs auf das zweite kopieren — die Fachpruefung lehnt das ab.
        ((ObjectNode) body.get("subjects").get(1)).put("shortName",
                body.get("subjects").get(0).get("shortName").asText());

        importiere(body, 400);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM subject", Long.class)).isEqualTo(vorher);
    }

    // ------------------------------------------------------------------ Helfer

    /** Ein strukturell vollstaendiges, aber leeres Dokument mit replace=true. */
    private ObjectNode leeresDokument() {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("version", "1");
        doc.put("replace", true);
        doc.putArray("subjects");
        doc.putArray("teachers");
        doc.putArray("rooms");
        doc.putArray("classes");
        doc.putArray("timeslots");
        doc.putArray("rules");
        doc.putArray("holidays");
        return doc;
    }

    private static Set<String> feldWerte(JsonNode array, String feld) {
        Set<String> s = new HashSet<>();
        for (JsonNode n : array) s.add(n.get(feld).asText());
        return s;
    }
}