package de.wsp3_1.substitution;

import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.shared.Weekday;
import de.wsp3_1.substitution.domain.EntryKind;
import de.wsp3_1.substitution.domain.SubstitutionOptionsService;
import de.wsp3_1.substitution.domain.SubstitutionOptionsService.OptionsInput;
import de.wsp3_1.substitution.domain.SubstitutionOptionsService.RoomFact;
import de.wsp3_1.substitution.domain.SubstitutionOptionsService.TeacherFact;
import de.wsp3_1.substitution.domain.SubstitutionViews.OptionsView;
import de.wsp3_1.substitution.domain.SubstitutionViews.RoomOptionView;
import de.wsp3_1.substitution.domain.SubstitutionViews.TeacherOptionView;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Vorschlagslisten als Funktion (Teststrategie Ebene 1): filtern und sortieren, sonst nichts.
 * Grundlage ist Schritt V-3 der Abnahme-Checkliste und Testfall T-W6.1 — die 1. Stunde der 7a am
 * 21.09.2026, nachdem Meyer ausgefallen und R101 gesperrt ist.
 */
class SubstitutionOptionsTest {

    private static final long MEYER = 1;
    private static final long SCHULZ = 2;
    private static final long WEBER = 3;
    private static final long R101 = 1;
    private static final long R204 = 2;
    private static final long P1 = 3;
    private static final long MATHE = 1;
    private static final long PHYSIK = 3;

    private static final LocalDate MONTAG = LocalDate.of(2026, 9, 21);
    private static final long MO_1 = 1;
    private static final long MO_2 = 2;
    private static final long MO_4 = 4;
    private static final long FR_6 = 34;

    // ------------------------------------------------------------------ T-W6.1

    @Test
    @DisplayName("T-W6.1: Schulz ja, Meyer nein; R204 ja, R101 nein")
    void tW6_1_listsSchulzNotMeyer_R204NotR101() {
        OptionsView options = SubstitutionOptionsService.choose(new OptionsInput(
                stunde7aMathe(), montag(), lehrkraefte(), raeume(),
                null, Set.of(MEYER), Set.of(R101)));

        assertThat(options.teachers().stream().map(TeacherOptionView::name))
                .containsExactly("Schulz", "Weber");
        assertThat(options.teachers().getFirst().teachesSubject())
                .as("fachgleiche Lehrkraft steht oben").isTrue();
        assertThat(options.teachers().stream().map(TeacherOptionView::id)).doesNotContain(MEYER);

        assertThat(options.rooms().stream().map(RoomOptionView::name)).containsExactly("P1", "R204");
        assertThat(options.rooms().stream().map(RoomOptionView::id)).doesNotContain(R101);
        assertThat(options.rooms()).allSatisfy(room ->
                assertThat(room.matchesRequirement())
                        .as("Mathematik stellt keine Raumanforderung").isTrue());
    }

    @Test
    @DisplayName("Eine deaktivierte Lehrkraft steht nicht in den Vorschlaegen (T-W1.3, Schritt 5b)")
    void inactiveTeacherIsNotOffered() {
        List<TeacherFact> lehrkraefte = List.of(
                teacher(MEYER, "MEY", "Meyer", true, Set.of(MATHE, PHYSIK), Set.of()),
                teacher(SCHULZ, "SCZ", "Schulz", false, Set.of(MATHE), Set.of(FR_6)),
                teacher(WEBER, "WEB", "Weber", true, Set.of(2L), Set.of()));

        OptionsView options = SubstitutionOptionsService.choose(new OptionsInput(
                stunde7aMathe(), montag(), lehrkraefte, raeume(), null, Set.of(MEYER), Set.of(R101)));

        assertThat(options.teachers().stream().map(TeacherOptionView::name)).containsExactly("Weber");
    }

    @Test
    @DisplayName("Eine Lehrkraft mit gesperrter Zeit oder eigener Stunde im Zeitfenster fehlt")
    void blockedOrBusyTeachersAreNotOffered() {
        // Weber unterrichtet montags in der 2. Stunde; Schulz hat die 2. Stunde gesperrt.
        LessonView stunde = lesson(4, 2, "8b", MATHE, "Ma", MEYER, "MEY", R204, "R204", Weekday.MO, 2, MO_2);
        List<TeacherFact> lehrkraefte = List.of(
                teacher(SCHULZ, "SCZ", "Schulz", true, Set.of(MATHE), Set.of(MO_2)),
                teacher(WEBER, "WEB", "Weber", true, Set.of(2L), Set.of()));

        OptionsView options = SubstitutionOptionsService.choose(new OptionsInput(
                stunde, montag(), lehrkraefte, raeume(), null, Set.of(MEYER), Set.of()));

        assertThat(options.teachers()).isEmpty();
    }

    @Test
    @DisplayName("Passende Raumart zuerst: Physik schlaegt den Fachraum vor dem Klassenraum vor")
    void matchingRoomTypeComesFirst() {
        LessonView physik = lesson(3, 1, "7a", PHYSIK, "Ph", MEYER, "MEY", P1, "P1", Weekday.MO, 4, MO_4);

        OptionsView options = SubstitutionOptionsService.choose(new OptionsInput(
                physik, montag(), lehrkraefte(), raeume(), "Fachraum Physik", Set.of(MEYER), Set.of()));

        // R204 ist in der 4. Stunde von der 8b belegt und faellt heraus; P1 gehoert zur Stunde selbst
        // und ist deshalb frei.
        assertThat(options.rooms().stream().map(RoomOptionView::name)).containsExactly("P1", "R101");
        assertThat(options.rooms().getFirst().matchesRequirement()).isTrue();
        assertThat(options.rooms().getLast().matchesRequirement()).isFalse();
    }

    // ------------------------------------------------------------------ Testdaten

    /** Die 1. Stunde der 7a am Montag: Mathematik bei Meyer in R101. */
    private static LessonView stunde7aMathe() {
        return lesson(1, 1, "7a", MATHE, "Ma", MEYER, "MEY", R101, "R101", Weekday.MO, 1, MO_1);
    }

    /** Der aufgeloeste Montag des Leitszenarios — die sechs Stunden des Regelplans. */
    private static List<EntryView> montag() {
        return List.of(
                entry(1, 1, "7a", MATHE, "Ma", MEYER, "MEY", R101, "R101", MO_1, 1),
                entry(2, 1, "7a", 2, "De", WEBER, "WEB", R101, "R101", MO_2, 2),
                entry(4, 2, "8b", MATHE, "Ma", MEYER, "MEY", R204, "R204", MO_2, 2),
                entry(3, 1, "7a", PHYSIK, "Ph", MEYER, "MEY", P1, "P1", MO_4, 4),
                entry(5, 2, "8b", 2, "De", WEBER, "WEB", R204, "R204", MO_4, 4),
                entry(6, 2, "8b", MATHE, "Ma", MEYER, "MEY", R204, "R204", 5, 5));
    }

    private static List<TeacherFact> lehrkraefte() {
        return List.of(
                teacher(MEYER, "MEY", "Meyer", true, Set.of(MATHE, PHYSIK), Set.of()),
                teacher(SCHULZ, "SCZ", "Schulz", true, Set.of(MATHE), Set.of(FR_6)),
                teacher(WEBER, "WEB", "Weber", true, Set.of(2L), Set.of()));
    }

    private static List<RoomFact> raeume() {
        return List.of(new RoomFact(R101, "R101", "Klassenraum", true),
                new RoomFact(R204, "R204", "Klassenraum", true),
                new RoomFact(P1, "P1", "Fachraum Physik", true));
    }

    private static TeacherFact teacher(long id, String shortName, String name, boolean active,
                                       Set<Long> subjectIds, Set<Long> blockedTimeslotIds) {
        return new TeacherFact(id, shortName, name, active, subjectIds, blockedTimeslotIds);
    }

    private static LessonView lesson(long id, long classId, String classShort, long subjectId,
                                     String subjectShort, long teacherId, String teacherShort,
                                     long roomId, String roomShort, Weekday day, int position,
                                     long timeslotId) {
        return new LessonView(id, classId, classShort, subjectId, subjectShort, "#1976D2", teacherId,
                teacherShort, roomId, roomShort, timeslotId, day, position, true);
    }

    private static EntryView entry(long lessonId, long classId, String classShort, long subjectId,
                                   String subjectShort, long teacherId, String teacherShort,
                                   long roomId, String roomShort, long timeslotId, int position) {
        return new EntryView(MONTAG, timeslotId, Weekday.MO, position, lessonId, classId, classShort,
                subjectId, subjectShort, "#1976D2", teacherId, teacherShort, roomId, roomShort,
                EntryKind.REGULAR, null, List.of(), null);
    }
}
