package de.wsp3_1.changelog;

import de.wsp3_1.changelog.api.ChangelogController;
import de.wsp3_1.changelog.domain.ChangelogAction;
import de.wsp3_1.changelog.domain.ChangelogService;
import de.wsp3_1.changelog.domain.ChangelogViews.ItemView;
import de.wsp3_1.changelog.domain.ChangelogViews.PageView;
import de.wsp3_1.changelog.domain.ChangelogViews.Query;
import de.wsp3_1.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Form und Grenzen von {@code GET /api/changelog} — ohne Datenbank und ohne Zugriffsschutz.
 * Der Kern ist {@link #noDeleteOrPutMapping()}: T-W7.1 verlangt, dass es keinen Weg gibt, einen
 * Verlaufseintrag zu aendern oder zu loeschen.
 */
@WebMvcTest(controllers = ChangelogController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class ChangelogControllerTest {

    private static final ItemView EINTRAG = new ItemView(2L, Instant.parse("2026-09-18T09:20:00Z"),
            ChangelogAction.EXCEPTION_CREATED, LocalDate.parse("2026-09-21"), 1, "7a",
            "Meyer / R101", "Schulz / R204", "Krankmeldung; Wasserschaden", "admin");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChangelogService service;

    @Test
    @DisplayName("T-W7.1: der Verlauf kennt kein PUT, PATCH, POST oder DELETE")
    void noDeleteOrPutMapping() {
        for (Method method : ChangelogController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PutMapping.class))
                    .as("%s darf kein PUT sein", method.getName()).isNull();
            assertThat(method.getAnnotation(PatchMapping.class))
                    .as("%s darf kein PATCH sein", method.getName()).isNull();
            assertThat(method.getAnnotation(PostMapping.class))
                    .as("%s darf kein POST sein", method.getName()).isNull();
            assertThat(method.getAnnotation(DeleteMapping.class))
                    .as("%s darf kein DELETE sein", method.getName()).isNull();
        }
    }

    @Test
    @DisplayName("V-6: ein Eintrag kommt mit allen zehn Feldern des Vertrags")
    void itemHasAllContractFields() throws Exception {
        given(service.find(any())).willReturn(new PageView(List.of(EINTRAG), 0, 50, 1));

        mvc.perform(get("/api/changelog").param("classId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].action").value("EXCEPTION_CREATED"))
                .andExpect(jsonPath("$.items[0].date").value("2026-09-21"))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].classShort").value("7a"))
                .andExpect(jsonPath("$.items[0].oldAssignment").value("Meyer / R101"))
                .andExpect(jsonPath("$.items[0].newAssignment").value("Schulz / R204"))
                .andExpect(jsonPath("$.items[0].reasons").value("Krankmeldung; Wasserschaden"))
                .andExpect(jsonPath("$.items[0].username").value("admin"))
                .andExpect(jsonPath("$.items[0].timestamp").exists());

        verify(service).find(new Query(null, null, 1L, null, null, 0, 50));
    }

    @Test
    @DisplayName("Eine Seitengroesse ueber 500 ergibt 400 statt einer riesigen Antwort")
    void tooLargePageSizeIsRejected() throws Exception {
        mvc.perform(get("/api/changelog").param("size", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("Eine negative Seitennummer ergibt 400")
    void negativePageIsRejected() throws Exception {
        mvc.perform(get("/api/changelog").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }
}
