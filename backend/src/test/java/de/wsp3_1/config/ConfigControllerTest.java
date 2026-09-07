package de.wsp3_1.config;

import de.wsp3_1.config.api.ConfigController;
import de.wsp3_1.config.api.HealthController;
import de.wsp3_1.config.domain.SystemConfigData;
import de.wsp3_1.config.domain.SystemConfigService;
import de.wsp3_1.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Form der Antworten und Eingabepruefung von {@code /api/config} und {@code /api/health} — ohne
 * Datenbank und ohne Zugriffsschutz ({@code addFilters = false}). Dass beide Pfade der richtigen Rolle
 * offenstehen, prueft {@code auth.SecurityConfigTest} und {@code config.ConfigIT}.
 */
@WebMvcTest(controllers = {ConfigController.class, HealthController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ConfigControllerTest {

    private static final SystemConfigData VORBELEGUNG =
            new SystemConfigData("Gymnasium Musterstadt", "SH", "GYMNASIUM", 60);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SystemConfigService service;

    @Test
    @DisplayName("Lesen liefert alle vier Werte der Systemkonfiguration")
    void getReturnsAllFields() throws Exception {
        given(service.get()).willReturn(VORBELEGUNG);

        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Musterstadt"))
                .andExpect(jsonPath("$.state").value("SH"))
                .andExpect(jsonPath("$.schoolType").value("GYMNASIUM"))
                .andExpect(jsonPath("$.maxSolveSeconds").value(60));
    }

    @Test
    @DisplayName("Speichern gibt den gespeicherten Stand zurueck")
    void putReturnsSavedValues() throws Exception {
        given(service.update(any())).willReturn(
                new SystemConfigData("Gymnasium Musterstadt", "SH", "GYMNASIUM", 30));

        mvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":30}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxSolveSeconds").value(30));
    }

    @Test
    @DisplayName("Ein unbekanntes Bundesland-Kuerzel ergibt 400 und wird nicht gespeichert")
    void unknownStateIsRejected() throws Exception {
        mvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Gymnasium Musterstadt","state":"XX","schoolType":"GYMNASIUM","maxSolveSeconds":60}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("state"))
                .andExpect(jsonPath("$.errors[0].message").value("muss eines der 16 Bundesland-Kuerzel sein"));

        then(service).should(never()).update(any());
    }

    @Test
    @DisplayName("Eine Rechenzeit von 0 Sekunden ergibt 400")
    void zeroSolveSecondsIsRejected() throws Exception {
        mvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxSolveSeconds"))
                .andExpect(jsonPath("$.errors[0].message").value("muss mindestens 1 sein"));

        then(service).should(never()).update(any());
    }

    @Test
    @DisplayName("Eine Rechenzeit ueber 600 Sekunden ergibt 400 — sonst belegt ein Lauf den Solver stundenlang")
    void tooLongSolveSecondsIsRejected() throws Exception {
        mvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":601}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxSolveSeconds"))
                .andExpect(jsonPath("$.errors[0].message").value("höchstens 600"));

        then(service).should(never()).update(any());
    }

    @Test
    @DisplayName("Ein leerer Schulname ergibt 400")
    void emptySchoolNameIsRejected() throws Exception {
        mvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":60}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("schoolName"));
    }

    @Test
    @DisplayName("Die Erreichbarkeitspruefung nennt den Schulnamen")
    void healthReportsSchoolName() throws Exception {
        given(service.schoolName()).willReturn("Gymnasium Musterstadt");

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Musterstadt"));
    }
}
