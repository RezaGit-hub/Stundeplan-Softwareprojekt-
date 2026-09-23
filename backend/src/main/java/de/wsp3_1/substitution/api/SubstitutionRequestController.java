package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.substitution.api.SubstitutionDtos.AnswerSubstitutionRequestDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.CreateSubstitutionRequestDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.SubstitutionRequestDto;
import de.wsp3_1.substitution.domain.SubstitutionRequestService;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewSubstitutionRequest;
import de.wsp3_1.substitution.domain.SubstitutionViews.SubstitutionRequestView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vertrag {@code /api/substitution-requests}: Planung legt Anfragen an, liest alle und zieht
 * zurueck; Lehrkraft sieht ihre eigenen und antwortet.
 *
 * <p>Die Rollen macht die Pfadregel in {@code SecurityConfig}: {@code /api/substitution-requests/me/**}
 * fuer {@code TEACHER}, alles Uebrige unter {@code /api/**} fuer {@code PLANNING}.</p>
 *
 * <p>Der Controller ist die einzige Stelle, an der die DTOs der Schnittstelle und die Views der
 * Fachlogik aufeinandertreffen. Der Dienst sieht keine DTOs (ArchUnit-Regel 3), der Controller
 * macht die Uebersetzung.</p>
 */
@RestController
@RequestMapping("/api/substitution-requests")
public class SubstitutionRequestController {

    private final SubstitutionRequestService service;

    public SubstitutionRequestController(SubstitutionRequestService service) {
        this.service = service;
    }

    /** Planung: alle Anfragen, optional auf eine Lehrkraft eingeschraenkt. */
    @GetMapping
    public List<SubstitutionRequestDto> list(@RequestParam(required = false) Long teacherId) {
        return service.list(teacherId).stream().map(SubstitutionRequestController::toDto).toList();
    }

    /** Planung: mehrere Anfragen in einem Zug (Array). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<SubstitutionRequestDto> create(@RequestBody List<CreateSubstitutionRequestDto> input) {
        List<NewSubstitutionRequest> domain = (input == null ? List.<CreateSubstitutionRequestDto>of() : input)
                .stream()
                .map(row -> new NewSubstitutionRequest(row.teacherId(), row.lessonId(), row.date()))
                .toList();
        return service.create(domain).stream().map(SubstitutionRequestController::toDto).toList();
    }

    /** Planung: eine Anfrage zurueckziehen. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long id) {
        service.withdraw(id);
    }

    /** Lehrkraft: eigene Anfragen; {@code open=true} nur die unbeantworteten. */
    @GetMapping("/me")
    public List<SubstitutionRequestDto> listMe(@AuthenticationPrincipal CurrentUser user,
                                               @RequestParam(defaultValue = "false") boolean open) {
        return service.listForTeacher(user.teacherId(), open).stream()
                .map(SubstitutionRequestController::toDto)
                .toList();
    }

    /** Lehrkraft: eigene Anfrage beantworten (ACCEPTED oder DECLINED). */
    @PatchMapping("/me/{id}")
    public SubstitutionRequestDto answer(@AuthenticationPrincipal CurrentUser user,
                                         @PathVariable Long id,
                                         @Valid @RequestBody AnswerSubstitutionRequestDto input) {
        return toDto(service.answer(id, user.teacherId(), input.status()));
    }

    // ------------------------------------------------------------------ Mapping View → DTO

    private static SubstitutionRequestDto toDto(SubstitutionRequestView view) {
        return new SubstitutionRequestDto(view.id(), view.teacherId(), view.lessonId(), view.date(),
                view.classShort(), view.subjectShort(), view.position(), view.roomShort(),
                view.originalTeacherShort(), view.status());
    }
}