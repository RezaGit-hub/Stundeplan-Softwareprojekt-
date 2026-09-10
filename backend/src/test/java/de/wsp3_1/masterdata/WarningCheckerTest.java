package de.wsp3_1.masterdata;

import de.wsp3_1.masterdata.MasterdataDtos.WarningCode;
import de.wsp3_1.masterdata.MasterdataDtos.WarningDto;
import de.wsp3_1.masterdata.domain.WarningChecker;
import de.wsp3_1.masterdata.domain.WarningChecker.TeachingAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-W1.4 und T-W1.5 auf der Rechenebene: ohne Spring, ohne Datenbank (Teststrategie Ebene 1).
 * Geprueft werden die Anzahl der Warnungen und die Meldungstexte <b>woertlich</b> — sie stehen so
 * in der Abnahme-Checkliste und in der Attrappe des Frontends.
 *
 * <p>Ausgangslage ist der Testdatenbestand des Pflichtenhefts: 7a Ma 2 h bei Meyer, 7a De 1 h bei
 * Weber, 7a Ph 1 h bei Meyer, 8b Ma 2 h bei Meyer, 8b De 1 h bei Weber, 9c Ma 1 h bei Schulz.
 * Meyer (Deputat 25) hat 5 Wochenstunden, Weber (25) hat 2, Schulz (2) hat 1.</p>
 */
class WarningCheckerTest {

    private static final long MATHE = 1;
    private static final long DEUTSCH = 2;
    private static final long PHYSIK = 3;
    private static final long MEYER = 10;
    private static final long SCHULZ = 11;
    private static final long WEBER = 12;
    private static final long K7A = 100;
    private static final long K8B = 101;
    private static final long K9C = 102;

    @Test
    @DisplayName("Ohne Abweichung meldet der Testdatenbestand nichts")
    void testdatenbestandIstWarnungsfrei() {
        List<TeachingAssignment> alle = testdaten();

        assertThat(WarningChecker.forClass(K7A, alle)).isEmpty();
        assertThat(WarningChecker.forClass(K8B, alle)).isEmpty();
        assertThat(WarningChecker.forClass(K9C, alle)).isEmpty();
        assertThat(WarningChecker.plannedHours(MEYER, alle)).isEqualTo(5);
        assertThat(WarningChecker.plannedHours(SCHULZ, alle)).isEqualTo(1);
        assertThat(WarningChecker.forTeacher(SCHULZ, "Schulz", 2, alle)).isEmpty();
    }

    /** T-W1.4: 9c Mathematik von Schulz auf Weber — Weber unterrichtet nur Deutsch. */
    @Test
    @DisplayName("T-W1.4: Fach ohne Lehrbefaehigung ergibt genau eine Warnung, Zuruecksetzen loescht sie")
    void tW1_4_subjectNotTaught_exactlyOneWarning_resetRemovesIt() {
        List<TeachingAssignment> alle = testdaten();
        ersetze(alle, K9C, MATHE, new TeachingAssignment(K9C, MATHE, "Mathematik", WEBER, "Weber", 25, false, 1));

        List<WarningDto> warnungen = WarningChecker.forClass(K9C, alle);

        assertThat(warnungen).hasSize(1);
        assertThat(warnungen.get(0).code()).isEqualTo(WarningCode.SUBJECT_NOT_TAUGHT);
        assertThat(warnungen.get(0).teacherId()).isEqualTo(WEBER);
        assertThat(warnungen.get(0).message()).isEqualTo("Weber unterrichtet das Fach Mathematik nicht.");

        // Zuruecksetzen auf Schulz: keine Warnung mehr.
        assertThat(WarningChecker.forClass(K9C, testdaten())).isEmpty();
    }

    /** T-W1.5: 7a Mathematik (2 h) von Meyer auf Schulz — Schulz kaeme auf 3 Stunden bei Deputat 2. */
    @Test
    @DisplayName("T-W1.5: 3 Wochenstunden bei Deputat 2 ergeben genau eine Warnung")
    void tW1_5_quotaExceeded_3of2_exactlyOneWarning() {
        List<TeachingAssignment> alle = testdaten();
        ersetze(alle, K7A, MATHE, new TeachingAssignment(K7A, MATHE, "Mathematik", SCHULZ, "Schulz", 2, true, 2));

        List<WarningDto> warnungen = WarningChecker.forClass(K7A, alle);

        assertThat(WarningChecker.plannedHours(SCHULZ, alle)).isEqualTo(3);
        assertThat(warnungen).hasSize(1);
        assertThat(warnungen.get(0).code()).isEqualTo(WarningCode.QUOTA_EXCEEDED);
        assertThat(warnungen.get(0).teacherId()).isEqualTo(SCHULZ);
        assertThat(warnungen.get(0).message())
                .isEqualTo("Schulz ist mit 3 Wochenstunden über dem Deputat von 2.");

        // Dieselbe Warnung sieht auch die Lehrkraft-Liste; nach dem Zuruecksetzen ist sie weg.
        assertThat(WarningChecker.forTeacher(SCHULZ, "Schulz", 2, alle)).hasSize(1);
        assertThat(WarningChecker.forTeacher(SCHULZ, "Schulz", 2, testdaten())).isEmpty();
    }

    @Test
    @DisplayName("Eine Lehrkraft mit zwei Zeilen ueber dem Deputat wird nur einmal gemeldet")
    void quotaWarningOncePerTeacher() {
        List<TeachingAssignment> alle = new ArrayList<>(List.of(
                new TeachingAssignment(K7A, MATHE, "Mathematik", SCHULZ, "Schulz", 2, true, 2),
                new TeachingAssignment(K7A, PHYSIK, "Physik", SCHULZ, "Schulz", 2, false, 2)));

        List<WarningDto> warnungen = WarningChecker.forClass(K7A, alle);

        // Zeile 1 (Mathematik, darf sie) loest nur das Deputat aus, Zeile 2 (Physik) die Lehrbefaehigung.
        assertThat(warnungen).extracting(WarningDto::code)
                .containsExactly(WarningCode.QUOTA_EXCEEDED, WarningCode.SUBJECT_NOT_TAUGHT);
    }

    @Test
    @DisplayName("Das Deputat zaehlt ueber alle Klassen, nicht nur ueber die gerade betrachtete")
    void quotaCountsAcrossAllClasses() {
        List<TeachingAssignment> alle = new ArrayList<>(List.of(
                new TeachingAssignment(K7A, MATHE, "Mathematik", SCHULZ, "Schulz", 2, true, 2),
                new TeachingAssignment(K9C, MATHE, "Mathematik", SCHULZ, "Schulz", 2, true, 1)));

        assertThat(WarningChecker.plannedHours(SCHULZ, alle)).isEqualTo(3);
        assertThat(WarningChecker.forClass(K9C, alle)).hasSize(1);
    }

    private static List<TeachingAssignment> testdaten() {
        return new ArrayList<>(List.of(
                new TeachingAssignment(K7A, MATHE, "Mathematik", MEYER, "Meyer", 25, true, 2),
                new TeachingAssignment(K7A, DEUTSCH, "Deutsch", WEBER, "Weber", 25, true, 1),
                new TeachingAssignment(K7A, PHYSIK, "Physik", MEYER, "Meyer", 25, true, 1),
                new TeachingAssignment(K8B, MATHE, "Mathematik", MEYER, "Meyer", 25, true, 2),
                new TeachingAssignment(K8B, DEUTSCH, "Deutsch", WEBER, "Weber", 25, true, 1),
                new TeachingAssignment(K9C, MATHE, "Mathematik", SCHULZ, "Schulz", 2, true, 1)));
    }

    private static void ersetze(List<TeachingAssignment> alle, long classId, long subjectId,
                                TeachingAssignment neu) {
        for (int i = 0; i < alle.size(); i++) {
            if (alle.get(i).classId() == classId && alle.get(i).subjectId() == subjectId) {
                alle.set(i, neu);
                return;
            }
        }
        throw new IllegalArgumentException("Zeile nicht gefunden");
    }
}
