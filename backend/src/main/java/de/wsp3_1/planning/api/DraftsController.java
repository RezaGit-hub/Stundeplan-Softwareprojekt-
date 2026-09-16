package de.wsp3_1.planning.api;

import de.wsp3_1.planning.api.PlanningDtos.AcceptDraftInput;
import de.wsp3_1.planning.api.PlanningDtos.AcceptDraftResponse;
import de.wsp3_1.planning.api.PlanningDtos.AddLessonInput;
import de.wsp3_1.planning.api.PlanningDtos.CreateDraft;
import de.wsp3_1.planning.api.PlanningDtos.DraftDto;
import de.wsp3_1.planning.api.PlanningDtos.DraftPatchInput;
import de.wsp3_1.planning.api.PlanningDtos.DraftSummaryDto;
import de.wsp3_1.planning.api.PlanningDtos.PatchLessonInput;
import de.wsp3_1.planning.api.PlanningDtos.RepairInput;
import de.wsp3_1.planning.api.PlanningDtos.RepairResponse;
import de.wsp3_1.planning.api.PlanningDtos.StartSolveInput;
import de.wsp3_1.planning.domain.DraftService;
import de.wsp3_1.planning.domain.RepairService;
import de.wsp3_1.planning.domain.SolveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/drafts**} — mehrere Entwuerfe nebeneinander (#103, Vertrag 1.3.0, E-52, Rolle PLANNING).
 *
 * <p>Jeder Pfad nennt den Entwurf ueber seine Kennung; die Fachlogik ist dieselbe wie hinter den
 * Singular-Pfaden in {@link DraftController} — dort steht {@code currentDraftId()}, hier {@code id}.
 * Antwort jeder Aenderung ist der ganze Entwurf; waehrend einer Berechnung wird nichts geaendert
 * (409 {@code solving}); Annehmen mit harten Konflikten ist 409 {@code draft-has-conflicts}.</p>
 */
@RestController
@RequestMapping("/api/drafts")
public class DraftsController {

    private final DraftService draftService;
    private final SolveService solveService;
    private final RepairService repairService;

    public DraftsController(DraftService draftService, SolveService solveService, RepairService repairService) {
        this.draftService = draftService;
        this.solveService = solveService;
        this.repairService = repairService;
    }

    // ------------------------------------------------------------------ Liste, Anlegen

    /** Alle Entwuerfe, juengster zuerst — Kennzahlen ohne Stunden. */
    @GetMapping
    public List<DraftSummaryDto> list() {
        return draftService.list().stream().map(DraftSummaryDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftDto create(@Valid @RequestBody CreateDraft input) {
        return DraftDto.of(draftService.create(input.name(), input.source()));
    }

    // ------------------------------------------------------------------ Ein Entwurf

    @GetMapping("/{id}")
    public DraftDto get(@PathVariable Long id) {
        return DraftDto.of(draftService.get(id));
    }

    /** Umbenennen und/oder Ablage (#116). Nur gesendete Felder wirken; leerer Rumpf ist 400. */
    @PatchMapping("/{id}")
    public DraftDto patch(@PathVariable Long id, @RequestBody(required = false) DraftPatchInput input) {
        DraftPatchInput body = input == null ? new DraftPatchInput(null, null) : input;
        return DraftDto.of(draftService.patch(id, body.name(), body.finished()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable Long id) {
        draftService.discard(id);
    }

    // ------------------------------------------------------------------ Stunden (W-3, W-9)

    @PostMapping("/{id}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftDto addLesson(@PathVariable Long id, @Valid @RequestBody AddLessonInput input) {
        return DraftDto.of(draftService.addLesson(id, input.classId(), input.subjectId(), input.timeslotId(),
                input.roomId()));
    }

    @PatchMapping("/{id}/lessons/{lessonId}")
    public DraftDto patchLesson(@PathVariable Long id, @PathVariable Long lessonId,
                                @RequestBody PatchLessonInput input) {
        return DraftDto.of(draftService.patchLesson(id, lessonId, input.timeslotId(), input.roomId(), input.pinned()));
    }

    @DeleteMapping("/{id}/lessons/{lessonId}")
    public DraftDto removeLesson(@PathVariable Long id, @PathVariable Long lessonId) {
        return DraftDto.of(draftService.removeLesson(id, lessonId));
    }

    // ------------------------------------------------------------------ Berechnung (W-8)

    /** 202 und sofort zurueck; den Stand liefert {@code GET /api/drafts/{id}} (Web fragt alle 2 s). */
    @PostMapping("/{id}/solve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void startSolve(@PathVariable Long id, @RequestBody(required = false) StartSolveInput input) {
        solveService.start(id, input == null ? null : input.classId());
    }

    /** 202 auch dann, wenn gerade nichts laeuft — Abbrechen ist nie ein Fehler. */
    @DeleteMapping("/{id}/solve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancelSolve(@PathVariable Long id) {
        solveService.cancel(id);
    }

    // ------------------------------------------------------------------ Konflikt loesen (#110)

    @PostMapping("/{id}/repair")
    public RepairResponse repair(@PathVariable Long id, @Valid @RequestBody RepairInput input) {
        return RepairResponse.of(repairService.propose(id, input.lessonId()));
    }

    // ------------------------------------------------------------------ Annehmen (W-8)

    /**
     * Andere Entwuerfe bleiben stehen; harte Konflikte sind 409 {@code draft-has-conflicts}, ein
     * kuenftiger Plan im Weg 409 {@code plan-overlap} (#115). Ein Entwurf aus der Ablage darf
     * angenommen werden.
     */
    @PostMapping("/{id}/accept")
    public AcceptDraftResponse accept(@PathVariable Long id, @RequestBody(required = false) AcceptDraftInput input) {
        return AcceptDraftResponse.of(draftService.accept(id,
                input == null ? null : input.validFrom(),
                input == null ? null : input.validUntil()));
    }
}
