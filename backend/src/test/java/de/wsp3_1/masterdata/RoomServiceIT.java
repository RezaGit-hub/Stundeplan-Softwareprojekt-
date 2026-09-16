package de.wsp3_1.masterdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Sperrzeiten eines Raums (#133, V9) gegen echtes PostgreSQL: anlegen, lesen, aendern, loeschen —
 * der Roundtrip ueber die Schnittstelle, wie die Oberflaeche ihn faehrt.
 */
class RoomServiceIT extends MasterdataITBase {

    @Test
    @DisplayName("#133: Raum mit Sperrzeiten anlegen, lesen, aendern — die Liste kommt so zurueck, wie sie ging")
    void roomBlockedTimeslots_roundtrip() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        // Anlegen mit zwei Sperrzeiten.
        JsonNode angelegt = call(post("/api/rooms").content("""
                {"name":"Aula","roomType":"Aula","active":true,"blockedTimeslotIds":[%d,%d]}"""
                .formatted(daten.slot("MO", 1), daten.slot("FR", 7))), 201);
        long aula = angelegt.get("id").asLong();
        assertThat(zahlen(angelegt.get("blockedTimeslotIds")))
                .containsExactlyInAnyOrder(daten.slot("MO", 1), daten.slot("FR", 7));

        // Lesen: einzeln und in der Liste.
        assertThat(zahlen(getJson("/api/rooms/" + aula).get("blockedTimeslotIds")))
                .containsExactlyInAnyOrder(daten.slot("MO", 1), daten.slot("FR", 7));
        JsonNode alle = getJson("/api/rooms");
        assertThat(alle).hasSize(4);
        for (JsonNode room : alle) {
            if (room.get("id").asLong() == aula) {
                assertThat(room.get("blockedTimeslotIds")).hasSize(2);
            } else {
                // Raeume ohne das Feld im Request haben eine leere Liste, kein null (Vertrag 1.1.0).
                assertThat(room.get("blockedTimeslotIds").isArray()).isTrue();
                assertThat(room.get("blockedTimeslotIds")).isEmpty();
            }
        }

        // Aendern ersetzt die ganze Liste.
        JsonNode geaendert = call(put("/api/rooms/" + aula).content("""
                {"name":"Aula","roomType":"Aula","active":true,"blockedTimeslotIds":[%d]}"""
                .formatted(daten.slot("TU", 2))), 200);
        assertThat(zahlen(geaendert.get("blockedTimeslotIds"))).containsExactly(daten.slot("TU", 2));
        assertThat(zahlen(getJson("/api/rooms/" + aula).get("blockedTimeslotIds")))
                .containsExactly(daten.slot("TU", 2));
        Long zeilen = jdbc.queryForObject("SELECT count(*) FROM room_blocked_timeslot WHERE room_id = ?",
                Long.class, aula);
        assertThat(zeilen).isEqualTo(1);

        // Ohne das Feld (alter Client) werden die Sperrzeiten geleert — Standard ist leer.
        JsonNode ohneFeld = call(put("/api/rooms/" + aula).content("""
                {"name":"Aula","roomType":"Aula","active":true}"""), 200);
        assertThat(ohneFeld.get("blockedTimeslotIds")).isEmpty();

        // Loeschen raeumt die Sperrzeiten mit (ON DELETE CASCADE).
        call(put("/api/rooms/" + aula).content("""
                {"name":"Aula","roomType":"Aula","active":true,"blockedTimeslotIds":[%d]}"""
                .formatted(daten.slot("TU", 2))), 200);
        call(delete("/api/rooms/" + aula), 204);
        Long nachLoeschen = jdbc.queryForObject("SELECT count(*) FROM room_blocked_timeslot WHERE room_id = ?",
                Long.class, aula);
        assertThat(nachLoeschen).isZero();
    }

    @Test
    @DisplayName("Ein unbekanntes oder leeres Zeitfenster in blockedTimeslotIds ergibt 400 mit Feldliste")
    void unknownOrNullBlockedTimeslotIsValidationError() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode unbekannt = call(post("/api/rooms").content("""
                {"name":"Aula","roomType":"Aula","active":true,"blockedTimeslotIds":[999999]}"""), 400);
        assertThat(unbekannt.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(unbekannt.get("errors").get(0).get("field").asText()).isEqualTo("blockedTimeslotIds");

        JsonNode leer = call(post("/api/rooms").content("""
                {"name":"Aula","roomType":"Aula","active":true,"blockedTimeslotIds":[null]}"""), 400);
        assertThat(leer.get("errors").get(0).get("field").asText()).isEqualTo("blockedTimeslotIds");
    }

    @Test
    @DisplayName("Faellt ein Zeitfenster aus dem Raster, faellt auch die Raumsperre darauf weg")
    void removingTimeslotDropsRoomBlockedTime() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        call(put("/api/rooms/" + daten.r101).content("""
                {"name":"R101","roomType":"Klassenraum","active":true,"blockedTimeslotIds":[%d]}"""
                .formatted(daten.slot("FR", 7))), 200);

        // Raster ohne Freitag 7. Position speichern — keine Stunde liegt dort, also erlaubt.
        JsonNode raster = getJson("/api/timegrid");
        List<String> rows = new ArrayList<>();
        for (JsonNode slot : raster.get("slots")) {
            if (slot.get("id").asLong() == daten.slot("FR", 7)) {
                continue;
            }
            rows.add(slot.toString());
        }
        call(put("/api/timegrid").content("{\"slots\":[" + String.join(",", rows) + "]}"), 200);

        assertThat(getJson("/api/rooms/" + daten.r101).get("blockedTimeslotIds")).isEmpty();
    }

    private static List<Long> zahlen(JsonNode array) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            out.add(array.get(i).asLong());
        }
        return out;
    }
}
