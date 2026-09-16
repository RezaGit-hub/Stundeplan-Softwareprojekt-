package de.wsp3_1.planning.api;

import de.wsp3_1.planning.domain.ConflictType;
import de.wsp3_1.planning.domain.ConflictView;
import de.wsp3_1.planning.domain.DraftSource;
import de.wsp3_1.planning.domain.DraftStatus;
import de.wsp3_1.planning.domain.ObjectKind;
import de.wsp3_1.planning.domain.PlanViews.AcceptResultView;
import de.wsp3_1.planning.domain.PlanViews.DraftSummaryView;
import de.wsp3_1.planning.domain.PlanViews.DraftView;
import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.planning.domain.PlanViews.OpenLessonView;
import de.wsp3_1.planning.domain.PlanViews.PlanView;
import de.wsp3_1.planning.domain.PlanViews.SolveInfoView;
import de.wsp3_1.planning.domain.RepairProposal;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Die Datensaetze der Schnittstelle {@code /api/timetable}, {@code /api/draft} und {@code /api/drafts} — Feldnamen,
 * Reihenfolge und Typen 1:1 wie im Vertrag {@code api-contract/openapi.yaml} und in
 * {@code frontend/src/api/types.ts}.
 *
 * <p>Warum eine zweite Satzart neben {@code planning.domain.PlanViews}? Weil die ArchUnit-Regeln beides
 * verlangen: {@code api} darf keine Entity sehen (Regel 2), {@code domain} nichts aus {@code api}
 * (Regel 3). Der Dienst gibt deshalb einen Record aus {@code domain} zurueck, und die Abbildung darauf
 * steht hier — genau ein {@code of(...)} je Satzart, kein MapStruct (VERTRAG Abschnitt 6, DTO-Regeln).</p>
 */
public final class PlanningDtos {

    private PlanningDtos() {
    }

    // ------------------------------------------------------------------ Antworten

    /**
     * Eine Stunde des Wochenmusters. Zeitfenster und Raum sind beide gesetzt („belegt") oder beide
     * {@code null} („offen") — einen dritten Zustand gibt es nicht (T-W8.1).
     */
    public record LessonDto(Long id, Long classId, String classShort, Long subjectId, String subjectShort,
                            String subjectColor, Long teacherId, String teacherShort,
                            Long roomId, String roomShort, Long timeslotId, Weekday day, Integer position,
                            boolean pinned) {

        public static LessonDto of(LessonView view) {
            return new LessonDto(view.id(), view.classId(), view.classShort(), view.subjectId(),
                    view.subjectShort(), view.subjectColor(), view.teacherId(), view.teacherShort(),
                    view.roomId(), view.roomShort(), view.timeslotId(), view.day(), view.position(),
                    view.pinned());
        }
    }

    public record ConflictObjectDto(ObjectKind kind, Long id, String label) {
    }

    /** Ein Konflikt ist ein Datum, kein Fehler: er steht neben den Stunden in einer 200-Antwort. */
    public record ConflictDto(ConflictType type, Severity severity, String message, List<Long> lessonIds,
                              Long ruleId, List<ConflictObjectDto> objects) {

        public static ConflictDto of(ConflictView view) {
            return new ConflictDto(view.type(), view.severity(), view.message(), view.lessonIds(),
                    view.ruleId(),
                    view.objects().stream()
                            .map(object -> new ConflictObjectDto(object.kind(), object.id(), object.label()))
                            .toList());
        }
    }

    public record OpenLessonDto(Long classId, String classShort, String subjectShort, int missing) {

        public static OpenLessonDto of(OpenLessonView view) {
            return new OpenLessonDto(view.classId(), view.classShort(), view.subjectShort(), view.missing());
        }
    }

    public record SolveInfoDto(Instant startedAt, Integer limitSeconds, String lastScore, String error) {

        public static SolveInfoDto of(SolveInfoView view) {
            return new SolveInfoDto(view.startedAt(), view.limitSeconds(), view.lastScore(), view.error());
        }
    }

    /**
     * Der aktive Regelplan. Vor der ersten Annahme: {@code id: null} und leere Listen.
     *
     * <p>{@code validFrom}: ab welchem Kalendertag dieser Plan gilt (E-40), {@code null} wenn ohne
     * Stichtag angenommen. Additiv ergaenzt 05.09. (Befund PFLICHTENHEFT-6) — die Web-App zeigte
     * „gilt ab" vorher aus {@code acceptedAt} geraten.</p>
     */
    public record TimetableDto(Long id, Instant acceptedAt, LocalDate validFrom, List<LessonDto> lessons,
                              List<ConflictDto> conflicts) {

        public static TimetableDto of(PlanView view) {
            return new TimetableDto(view.id(), view.acceptedAt(), view.validFrom(),
                    view.lessons().stream().map(LessonDto::of).toList(),
                    view.conflicts().stream().map(ConflictDto::of).toList());
        }
    }

    /** Wie {@link TimetableDto}, zusaetzlich deutsche Hinweise (z. B. verfallende Vertretungen). */
    public record AcceptDraftResponse(Long id, Instant acceptedAt, LocalDate validFrom, List<LessonDto> lessons,
                                      List<ConflictDto> conflicts, List<String> warnings) {

        public static AcceptDraftResponse of(AcceptResultView view) {
            TimetableDto plan = TimetableDto.of(view.plan());
            return new AcceptDraftResponse(plan.id(), plan.acceptedAt(), plan.validFrom(), plan.lessons(),
                    plan.conflicts(), view.warnings());
        }
    }

    /** Ein Entwurf; {@code name} kam mit #103 (Vertrag 1.4.0) additiv dazu. */
    public record DraftDto(Long id, String name, Instant createdAt, DraftStatus status, SolveInfoDto solve,
                           List<LessonDto> lessons, List<ConflictDto> conflicts,
                           List<OpenLessonDto> openLessons, List<String> warnings,
                           long expiringExceptions) {

        public static DraftDto of(DraftView view) {
            return new DraftDto(view.id(), view.name(), view.createdAt(), view.status(), SolveInfoDto.of(view.solve()),
                    view.lessons().stream().map(LessonDto::of).toList(),
                    view.conflicts().stream().map(ConflictDto::of).toList(),
                    view.openLessons().stream().map(OpenLessonDto::of).toList(),
                    view.warnings(), view.expiringExceptions());
        }
    }

    /** Eine Zeile von {@code GET /api/drafts} (#103): Kennzahlen ohne Stunden. */
    public record DraftSummaryDto(Long id, String name, Instant createdAt, DraftSource source, DraftStatus status,
                                  int hardConflicts) {

        public static DraftSummaryDto of(DraftSummaryView view) {
            return new DraftSummaryDto(view.id(), view.name(), view.createdAt(), view.source(), view.status(),
                    view.hardConflicts());
        }
    }

    // ------------------------------------------------------------------ Konflikt loesen (#110)

    public record RepairMoveDto(Long lessonId, Long timeslotId, Long roomId) {
    }

    /** Ein Vorschlag — eine Vorschau, nichts davon ist gespeichert (E-53). */
    public record RepairProposalDto(int level, String label, List<RepairMoveDto> moves, int hardConflictsAfter,
                                    String score) {

        public static RepairProposalDto of(RepairProposal proposal) {
            return new RepairProposalDto(proposal.level(), proposal.label(),
                    proposal.moves().stream()
                            .map(move -> new RepairMoveDto(move.lessonId(), move.timeslotId(), move.roomId()))
                            .toList(),
                    proposal.hardConflictsAfter(), proposal.score());
        }
    }

    /** Antwort auf {@code POST /api/draft/repair}; leer, wenn kein Lauf eine Stunde bewegt hat. */
    public record RepairResponse(List<RepairProposalDto> proposals) {

        public static RepairResponse of(List<RepairProposal> proposals) {
            return new RepairResponse(proposals.stream().map(RepairProposalDto::of).toList());
        }
    }

    // ------------------------------------------------------------------ Eingaben

    /** {@code name} ist Kann (#103): fehlt er, heisst der Entwurf „Entwurf n". */
    public record CreateDraft(String name, @NotNull(message = "darf nicht leer sein") DraftSource source) {
    }

    /** Rumpf von {@code PATCH /api/drafts/{id}} (#103): der neue Name, geprueft im Dienst. */
    public record DraftNameInput(String name) {
    }

    /** Die Konfliktstunde, fuer die Vorschlaege gerechnet werden (#110). */
    public record RepairInput(@NotNull(message = "darf nicht leer sein") Long lessonId) {
    }

    /** Handaufbau (W-3) — keine Lehrkraft im Rumpf, sie folgt aus der Fachzuordnung der Klasse (E-18). */
    public record AddLessonInput(@NotNull(message = "darf nicht leer sein") Long classId,
                                 @NotNull(message = "darf nicht leer sein") Long subjectId,
                                 @NotNull(message = "darf nicht leer sein") Long timeslotId,
                                 @NotNull(message = "darf nicht leer sein") Long roomId) {
    }

    /**
     * Nur gesendete Felder aendern sich. Ein fehlendes Feld ist {@code null} — deshalb laesst sich mit
     * diesem Rumpf ein Zeitfenster setzen, aber nicht leeren; zum Ausplanen dient
     * {@code DELETE /api/draft/lessons/&#123;id&#125;}.
     */
    public record PatchLessonInput(Long timeslotId, Long roomId, Boolean pinned) {
    }

    /** Kann (E-40): {@code classId} begrenzt die Neuberechnung auf eine Klasse. */
    public record StartSolveInput(Long classId) {
    }

    /** Kann (E-40): {@code validFrom} laesst den neuen Plan erst ab diesem Tag gelten. */
    public record AcceptDraftInput(LocalDate validFrom) {
    }
}
