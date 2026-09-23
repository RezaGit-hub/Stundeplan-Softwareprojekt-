package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.substitution.api.SubstitutionDtos.AnswerSubstitutionRequestDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.CreateSubstitutionRequestDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.SubstitutionRequestDto;
import de.wsp3_1.substitution.domain.SubstitutionRequestService;
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
 * <p>Die Anzeigefelder in den Antworten kommen aus der pending-Sicht des Tages und werden im
 * Service gesetzt — hier gibt es keine Fachlogik.</p>
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
        return service.list(teacherId);
    }

    /** Planung: mehrere Anfragen in einem Zug (Array). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<SubstitutionRequestDto> create(@RequestBody List<CreateSubstitutionRequestDto> input) {
        return service.create(input);
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
        return service.listForTeacher(user.teacherId(), open);
    }

    /** Lehrkraft: eigene Anfrage beantworten (ACCEPTED oder DECLINED). */
    @PatchMapping("/me/{id}")
    public SubstitutionRequestDto answer(@AuthenticationPrincipal CurrentUser user,
                                         @PathVariable Long id,
                                         @Valid @RequestBody AnswerSubstitutionRequestDto input) {
        return service.answer(id, user.teacherId(), input.status());
    }
}