package de.wsp3_1.transfer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Export der Grunddaten (Format-Version 1) gegen echtes PostgreSQL.
 *
 * <p>Geprueft wird die Zusage, auf der der Import aufbaut: das Dokument ist in sich stimmig —
 * jede Kennung ist eindeutig, und jeder Verweis zeigt auf einen Eintrag derselben Datei.</p>
 */
class MasterDataExportIT extends TransferITBase {

    @Test
    @DisplayName("Der Export enthält alle Grunddaten des vollen Beispielbestands")
    void exportEnthaeltAlleGrunddaten() throws Exception {
        JsonNode doc = exportiere();

        assertThat(doc.get("version").asText()).isEqualTo("1");
        assertThat(doc.get("exportedAt").isNull()).isFalse();
        assertThat(doc.get("subjects")).hasSize(12);
        assertThat(doc.get("teachers")).hasSize(24);
        assertThat(doc.get("rooms")).hasSize(18);
        assertThat(doc.get("classes")).hasSize(12);
        assertThat(doc.get("timeslots")).hasSize(35);
        // Beispielsatz SH: fuenf schulweite Zeilen.
        assertThat(doc.get("rules")).hasSize(5);
        assertThat(doc.get("holidays")).hasSize(1);
    }

    @Test
    @DisplayName("Kennungen sind eindeutig, jeder Verweis zeigt auf einen Eintrag derselben Datei")
    void kennungenUndVerweiseSindKonsistent() throws Exception {
        JsonNode doc = exportiere();

        Set<Long> subjectIds = ids(doc.get("subjects"));
        Set<Long> teacherIds = ids(doc.get("teachers"));
        Set<Long> slotIds = ids(doc.get("timeslots"));
        assertThat(subjectIds).hasSize(12);
        assertThat(teacherIds).hasSize(24);
        assertThat(slotIds).hasSize(35);

        for (JsonNode t : doc.get("teachers")) {
            for (JsonNode id : t.get("subjectIds")) assertThat(subjectIds).contains(id.asLong());
            for (JsonNode id : t.get("preferredTimeslotIds")) assertThat(slotIds).contains(id.asLong());
            for (JsonNode id : t.get("blockedTimeslotIds")) assertThat(slotIds).contains(id.asLong());
        }
        for (JsonNode klasse : doc.get("classes")) {
            for (JsonNode zeile : klasse.get("subjects")) {
                assertThat(subjectIds).contains(zeile.get("subjectId").asLong());
                assertThat(teacherIds).contains(zeile.get("teacherId").asLong());
            }
        }
    }

    private static Set<Long> ids(JsonNode array) {
        Set<Long> s = new HashSet<>();
        for (JsonNode n : array) s.add(n.get("id").asLong());
        return s;
    }
}