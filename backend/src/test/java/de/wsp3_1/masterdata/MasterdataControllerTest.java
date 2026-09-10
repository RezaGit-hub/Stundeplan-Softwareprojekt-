package de.wsp3_1.masterdata;

import de.wsp3_1.masterdata.MasterdataDtos.ClassDto;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.RoomDto;
import de.wsp3_1.masterdata.MasterdataDtos.SubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimegridDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimeslotDto;
import de.wsp3_1.masterdata.MasterdataDtos.WarningCode;
import de.wsp3_1.masterdata.MasterdataDtos.WarningDto;
import de.wsp3_1.masterdata.api.ClassController;
import de.wsp3_1.masterdata.api.RoomController;
import de.wsp3_1.masterdata.api.SubjectController;
import de.wsp3_1.masterdata.api.TeacherController;
import de.wsp3_1.masterdata.api.TimegridController;
import de.wsp3_1.masterdata.domain.RoomService;
import de.wsp3_1.masterdata.domain.SchoolClassService;
import de.wsp3_1.masterdata.domain.SubjectService;
import de.wsp3_1.masterdata.domain.TeacherService;
import de.wsp3_1.masterdata.domain.TimegridService;
import de.wsp3_1.shared.Weekday;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Die Form der Antworten (Teststrategie Ebene 2): heissen die Felder so wie im Vertrag
 * {@code api-contract/openapi.yaml} und in {@code frontend/src/api/types.ts}? Ein falsch benanntes
 * Feld faellt hier auf und nicht erst im Browser.
 *
 * <p>Ohne Datenbank und ohne Zugriffsschutz ({@code addFilters = false}): die Pfadregeln prueft
 * {@code auth/SecurityConfigTest}, die Fachlogik pruefen die Integrationstests.</p>
 */
@WebMvcTest(controllers = {TeacherController.class, SubjectController.class, RoomController.class,
        ClassController.class, TimegridController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class MasterdataControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TeacherService teacherService;

    @MockitoBean
    private SubjectService subjectService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private SchoolClassService schoolClassService;

    @MockitoBean
    private TimegridService timegridService;

    @Test
    @DisplayName("Eine Lehrkraft kommt mit allen zehn Feldern des Vertrags zurueck")
    void teacherDtoHasContractFields() throws Exception {
        given(teacherService.list()).willReturn(List.of(new TeacherDto(2L, "Schulz", "SCZ", 2,
                List.of(1L), List.of(), List.of(34L, 35L), true, 1,
                List.of(new WarningDto(WarningCode.QUOTA_EXCEEDED,
                        "Schulz ist mit 3 Wochenstunden über dem Deputat von 2.", 2L)))));

        mvc.perform(get("/api/teachers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Schulz"))
                .andExpect(jsonPath("$[0].shortName").value("SCZ"))
                .andExpect(jsonPath("$[0].weeklyHoursQuota").value(2))
                .andExpect(jsonPath("$[0].subjectIds[0]").value(1))
                .andExpect(jsonPath("$[0].preferredTimeslotIds").isArray())
                .andExpect(jsonPath("$[0].blockedTimeslotIds[1]").value(35))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].plannedHours").value(1))
                .andExpect(jsonPath("$[0].warnings[0].code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$[0].warnings[0].teacherId").value(2))
                .andExpect(jsonPath("$[0].warnings[0].message")
                        .value("Schulz ist mit 3 Wochenstunden über dem Deputat von 2."));
    }

    @Test
    @DisplayName("Eine angelegte Lehrkraft antwortet mit 201")
    void createTeacherAnswers201() throws Exception {
        given(teacherService.create(any())).willReturn(new TeacherDto(4L, "Krause", "KRA", 10,
                List.of(), List.of(), List.of(), true, 0, List.of()));

        mvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Krause","shortName":"KRA","weeklyHoursQuota":10,"subjectIds":[],
                         "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    @DisplayName("Ein fehlender Name kommt gar nicht erst im Dienst an")
    void invalidTeacherIsRejectedBeforeTheService() throws Exception {
        mvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"","shortName":"KRA","weeklyHoursQuota":10,"subjectIds":[],
                         "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("Ein Fach nennt Raumanforderung und Farbe; ohne Anforderung steht dort null")
    void subjectDtoHasContractFields() throws Exception {
        given(subjectService.list()).willReturn(List.of(
                new SubjectDto(1L, "Mathematik", "Ma", null, "#1976D2"),
                new SubjectDto(3L, "Physik", "Ph", "Fachraum Physik", "#F57C00")));

        mvc.perform(get("/api/subjects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requiredRoomType").doesNotExist())
                .andExpect(jsonPath("$[0].color").value("#1976D2"))
                .andExpect(jsonPath("$[1].requiredRoomType").value("Fachraum Physik"))
                .andExpect(jsonPath("$[1].shortName").value("Ph"));
    }

    @Test
    @DisplayName("Ein Raum nennt Name, Raumart und ob er aktiv ist")
    void roomDtoHasContractFields() throws Exception {
        given(roomService.list()).willReturn(List.of(new RoomDto(1L, "R101", "Klassenraum", true)));

        mvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("R101"))
                .andExpect(jsonPath("$[0].roomType").value("Klassenraum"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("Eine Klasse bringt ihre Faecherliste mit Kuerzeln mit")
    void classDtoHasSubjectsWithShortNames() throws Exception {
        given(schoolClassService.get(1L)).willReturn(new ClassDto(1L, "7a", 7, true,
                List.of(new ClassSubjectDto(1L, "Ma", 2, 1L, "MEY")), List.of()));

        mvc.perform(get("/api/classes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(7))
                .andExpect(jsonPath("$.subjects[0].subjectId").value(1))
                .andExpect(jsonPath("$.subjects[0].subjectShort").value("Ma"))
                .andExpect(jsonPath("$.subjects[0].weeklyHours").value(2))
                .andExpect(jsonPath("$.subjects[0].teacherShort").value("MEY"))
                .andExpect(jsonPath("$.warnings").isArray());
    }

    /**
     * Der Feldname heisst {@code isBreak}, nicht {@code break}, und die Uhrzeiten sind {@code "08:00"},
     * nicht {@code "08:00:00"} — beides steht so im Vertrag, und die Web-App liest es so.
     */
    @Test
    @DisplayName("Ein Zeitfenster heisst isBreak und nennt die Uhrzeit als HH:mm")
    void timeslotDtoUsesIsBreakAndShortTimes() throws Exception {
        given(timegridService.get()).willReturn(new TimegridDto(List.of(
                new TimeslotDto(1L, Weekday.MO, 1, "08:00", "08:45", false),
                new TimeslotDto(3L, Weekday.MO, 3, "09:35", "09:55", true))));

        mvc.perform(get("/api/timegrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].day").value("MO"))
                .andExpect(jsonPath("$.slots[0].position").value(1))
                .andExpect(jsonPath("$.slots[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("08:45"))
                .andExpect(jsonPath("$.slots[0].isBreak").value(false))
                .andExpect(jsonPath("$.slots[1].isBreak").value(true))
                .andExpect(jsonPath("$.slots[0].break").doesNotExist());
    }

    // ---------------------------------------------------------------- Fehlerfaelle des Auffanghandlers

    /**
     * Eine Nummer, die keine ist. Das ist ein Fehler der Anfrage — bevor
     * {@code MethodArgumentTypeMismatchException} einen eigenen Fall bekam, lief er in den
     * Auffangzweig und ergab 500.
     */
    @Test
    @DisplayName("Eine Kennung, die keine Zahl ist, ergibt 400 und nicht 500")
    void nonNumericPathIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/teachers/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    /** Falsches Verb auf einem bekannten Pfad: 405 im Problemformat, kein 500. */
    @Test
    @DisplayName("Ein falsches HTTP-Verb ergibt 405 im Problemformat")
    void wrongHttpMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/api/timegrid"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:method-not-allowed"));
    }

    /** Ohne application/json: 415 im Problemformat, kein 500. */
    @Test
    @DisplayName("Ein falscher Inhaltstyp ergibt 415 im Problemformat")
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        mvc.perform(post("/api/teachers").contentType(MediaType.TEXT_PLAIN).content("Meyer"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unsupported-media-type"));
    }

    /**
     * Zu lange Werte muss die Pruefung abfangen: die Spalten in V1 sind begrenzt
     * ({@code school_class.name varchar(20)}), und ohne Grenze im DTO scheiterte erst die Datenbank —
     * mit 500 statt 400 mit Feldliste.
     */
    @Test
    @DisplayName("Ein zu langer Klassenname ergibt 400 mit Feldliste, nicht 500")
    void tooLongClassNameIsValidationError() throws Exception {
        mvc.perform(post("/api/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","grade":7,"active":true}""".formatted("x".repeat(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

}
