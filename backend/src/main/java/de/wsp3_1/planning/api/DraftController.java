package de.wsp3_1.planning.api;

import de.wsp3_1.planning.api.PlanningDtos.AcceptDraftInput;
import de.wsp3_1.planning.api.PlanningDtos.AcceptDraftResponse;
import de.wsp3_1.planning.api.PlanningDtos.AddLessonInput;
import de.wsp3_1.planning.api.PlanningDtos.CreateDraft;
import de.wsp3_1.planning.api.PlanningDtos.DraftDto;
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

/**
 * {@code /api/draft**} — der <b>juengste</b> Entwurf (Vertrag v1, Rolle PLANNING).
 *
 * <p>Bis Vertrag 1.2.0 gab es genau einen Entwurf, und diese Pfade waren der einzige Weg zu ihm. Seit
 * #103 (1.3.0, E-52) stehen mehrere Entwuerfe nebeneinander unter {@code /api/drafts/{id}}
 * ({@link DraftsController}); die Singular-Pfade bleiben fuer alte Clients (App) stehen und meinen den
 * Entwurf mit der hoechsten Kennung ({@link DraftService#currentDraftId()}). Nur {@code POST} legt
 * weiter einen neuen Entwurf an — jetzt ohne 409 {@code draft-exists}.</p>
 *
 * <p>Zwei Dinge gelten fuer alle Aenderungen: die Antwort ist <b>immer der ganze Entwurf</b> (die
 * Oberflaeche muss nichts zusammensetzen), und waehrend einer Berechnung wird nichts geaendert
 * (409 {@code solving}).</p>
 */
@RestController
@RequestMapping("/api/draft")
public class DraftController {

    private final DraftService draftService;
    private final SolveService solveService;
    private final RepairService repairService;

    public DraftController(DraftService draftService, SolveService solveService, RepairService repairService) {
        this.draftService = draftService;
        this.solveService = solveService;
        this.repairService = repairService;
    }

    // ------------------------------------------------------------------ Entwurf

    @GetMapping
    public DraftDto get() {
        return DraftDto.of(draftService.get(draftService.currentDraftId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftDto create(@Valid @RequestBody CreateDraft input) {
        return DraftDto.of(draftService.create(input.name(), input.source()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard() {
        draftService.discard(draftService.currentDraftId());
    }

    // ------------------------------------------------------------------ Stunden (W-3, W-9)

    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftDto addLesson(@Valid @RequestBody AddLessonInput input) {
        return DraftDto.of(draftService.addLesson(draftService.currentDraftId(), input.classId(), input.subjectId(),
                input.timeslotId(), input.roomId()));
    }

    @PatchMapping("/lessons/{id}")
    public DraftDto patchLesson(@PathVariable Long id, @RequestBody PatchLessonInput input) {
        return DraftDto.of(draftService.patchLesson(draftService.currentDraftId(), id, input.timeslotId(),
                input.roomId(), input.pinned()));
    }

    @DeleteMapping("/lessons/{id}")
    public DraftDto removeLesson(@PathVariable Long id) {
        return DraftDto.of(draftService.removeLesson(draftService.currentDraftId(), id));
    }

    // ------------------------------------------------------------------ Berechnung (W-8)

    /** 202 und sofort zurueck; den Stand liefert {@code GET /api/draft} (Web fragt alle 2 s). */
    @PostMapping("/solve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void startSolve(@RequestBody(required = false) StartSolveInput input) {
        solveService.start(draftService.currentDraftId(), input == null ? null : input.classId(),
                input != null && Boolean.TRUE.equals(input.force()));
    }

    /** 202 auch dann, wenn gerade nichts laeuft — Abbrechen ist nie ein Fehler. */
    @DeleteMapping("/solve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancelSolve() {
        solveService.cancel(draftService.currentDraftId());
    }

    // ------------------------------------------------------------------ Konflikt loesen (#110)

    /**
     * Synchron, bis zu 15 s (drei Stufen je 5 s). Die Antwort ist eine Vorschau: gespeichert wird nichts,
     * uebernommen wird ein Vorschlag ueber {@code PATCH /lessons/{id}} je bewegter Stunde.
     */
    @PostMapping("/repair")
    public RepairResponse repair(@Valid @RequestBody RepairInput input) {
        return RepairResponse.of(repairService.propose(draftService.currentDraftId(), input.lessonId()));
    }

    // ------------------------------------------------------------------ Annehmen (W-8)

    @PostMapping("/accept")
    public AcceptDraftResponse accept(@RequestBody(required = false) AcceptDraftInput input) {
        return AcceptDraftResponse.of(draftService.accept(draftService.currentDraftId(),
                input == null ? null : input.validFrom(),
                input == null ? null : input.validUntil()));
    }
}
