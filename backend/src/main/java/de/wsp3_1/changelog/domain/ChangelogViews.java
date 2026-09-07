package de.wsp3_1.changelog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Was der Dienst herausgibt und was er hereinbekommt — reine Records ohne JPA. Der Controller bildet
 * sie in {@code changelog/api/ChangelogDtos} auf die Vertragsform ab (VERTRAG Abschnitt 6, DTO-Regeln).
 */
public final class ChangelogViews {

    private ChangelogViews() {
    }

    /**
     * Ein neuer Eintrag. Wer ihn schreibt, sagt damit auch, woran er haengt: {@code lessonId},
     * {@code classId} und {@code teacherId} sind die Filterspalten von {@code GET /api/changelog}.
     */
    public record NewEntry(ChangelogAction action, LocalDate date, Integer position,
                           String oldAssignment, String newAssignment, String reasons,
                           Long userId, Long lessonId, Long classId, Long teacherId) {

        /**
         * Der Eintrag zur Annahme eines Plans (W-8 schreibt ihn, W-7 liest ihn). Er haengt an keiner
         * einzelnen Stunde: {@code position}, {@code lessonId}, {@code classId} und {@code teacherId}
         * bleiben leer, {@code date} traegt den Gueltigkeitsbeginn (oder ist leer = ab sofort).
         */
        public static NewEntry timetableAccepted(LocalDate validFrom, String oldAssignment,
                                                 String newAssignment, Long userId) {
            return new NewEntry(ChangelogAction.TIMETABLE_ACCEPTED, validFrom, null,
                    oldAssignment, newAssignment, "", userId, null, null, null);
        }

        /**
         * Der Eintrag zu einer neuen datierten Ausnahme (W-5/W-6 schreiben ihn, W-7 liest ihn).
         * Aufrufer ist das Modul {@code substitution}; {@code changelog} kennt dessen Klassen nicht.
         *
         * <p>Die beiden Belegungen sind <b>fertige Texte</b>, weil nur der Aufrufer die Namen kennt:
         * {@code oldAssignment} ist die Belegung des Regelplans („Meyer / R101"), {@code newAssignment}
         * die neue Lage — bei einer Vertretung „Schulz / R204", bei einem Ausfall „entfaellt", bei
         * einer Verlegung „Di, 3. Stunde". Die Anlaesse kommen als Liste herein und werden mit „; "
         * verbunden, damit der Verlauf ueberall gleich aussieht (Vertrag, Feld {@code reasons}).</p>
         *
         * <p>Beispiel aus der Abnahme (V-6):<br>
         * {@code NewEntry.exceptionCreated(LocalDate.of(2026, 9, 21), 1, "Meyer / R101", "Schulz / R204",
         * List.of("Krankmeldung", "Wasserschaden"), userId, lessonId, klasse7a, meyer)}</p>
         */
        public static NewEntry exceptionCreated(LocalDate date, Integer position, String oldAssignment,
                                                String newAssignment, List<String> reasons, Long userId,
                                                Long lessonId, Long classId, Long teacherId) {
            return new NewEntry(ChangelogAction.EXCEPTION_CREATED, date, position, oldAssignment,
                    newAssignment, joinReasons(reasons), userId, lessonId, classId, teacherId);
        }

        /**
         * Der Eintrag zur Ruecknahme einer Ausnahme. Alt und neu tauschen die Rollen: {@code oldAssignment}
         * ist die weggefallene Ausnahme („Schulz / R204"), {@code newAssignment} die Belegung, die wieder
         * gilt („Meyer / R101"). Der Eintrag der Anlage bleibt stehen — der Verlauf loescht nichts (T-W7.1).
         */
        public static NewEntry exceptionDeleted(LocalDate date, Integer position, String oldAssignment,
                                                String newAssignment, List<String> reasons, Long userId,
                                                Long lessonId, Long classId, Long teacherId) {
            return new NewEntry(ChangelogAction.EXCEPTION_DELETED, date, position, oldAssignment,
                    newAssignment, joinReasons(reasons), userId, lessonId, classId, teacherId);
        }

        /** Alle Anlaesse in einer Zeile, mit „; " getrennt; ohne Anlass bleibt das Feld leer, nie {@code null}. */
        private static String joinReasons(List<String> reasons) {
            return reasons == null ? "" : String.join("; ", reasons);
        }
    }

    /**
     * Die Filter von {@code GET /api/changelog}. {@code from}/{@code to} wirken auf das Datum der
     * Stunde, nicht auf den Zeitstempel des Eintrags (Vertrag).
     */
    public record Query(LocalDate from, LocalDate to, Long classId, Long teacherId, Long lessonId,
                        int page, int size) {
    }

    /** Ein Eintrag, wie ihn die Antwort zeigt: fertige Texte, keine Kennungen zum Nachschlagen. */
    public record ItemView(Long id, Instant timestamp, ChangelogAction action, LocalDate date,
                           Integer position, String classShort, String oldAssignment, String newAssignment,
                           String reasons, String username) {
    }

    /** Eine Seite des Verlaufs. {@code total} zaehlt alle Treffer des Filters, nicht nur diese Seite. */
    public record PageView(List<ItemView> items, int page, int size, long total) {
    }
}
