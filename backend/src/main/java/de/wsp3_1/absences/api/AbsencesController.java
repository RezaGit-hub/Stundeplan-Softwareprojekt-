package de.wsp3_1.absences.api;

import de.wsp3_1.absences.api.AbsenceDtos.AbsenceDto;
import de.wsp3_1.absences.api.AbsenceDtos.CreateAbsenceDto;
import de.wsp3_1.absences.domain.AbsencesService;
import de.wsp3_1.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vertrag {@code /api/absences}: Sicht der Planung (alle Meldungen, Wandlung in ein Ereignis)
 * und Sicht der Lehrkraft (nur eigene). Rollen macht die Pfadregel in {@code SecurityConfig} —
 * hier bewusst kein {@code @PreAuthorize}, damit es bei einem Mechanismus bleibt.
 *
 * <p>Eigentum: die {@code teacherId} kommt immer aus dem Principal, nie aus dem Body oder Pfad
 * (dieselbe Regel wie in {@code AuthController}).</p>
 */
@RestController
@RequestMapping("/api/absences")
public class AbsencesController {

    private final AbsencesService service;

    public AbsencesController(AbsencesService service) {
        this.service = service;
    }

    /** Planung: alle Meldungen. */
    @GetMapping
    public List<AbsenceDto> list() {
        return service.listAll();
    }

    /** Planung: Meldung annehmen und zu einem Ereignis machen (#116 — Ereignis folgt). */
    @PostMapping("/{id}/convert")
    public AbsenceDto convert(@PathVariable Long id) {
        return service.convert(id);
    }

    /** Lehrkraft: nur die eigenen Meldungen. */
    @GetMapping("/me")
    public List<AbsenceDto> listMe(@AuthenticationPrincipal CurrentUser user) {
        return service.listForTeacher(user.teacherId());
    }

    /** Lehrkraft: neue eigene Meldung. */
    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceDto create(@AuthenticationPrincipal CurrentUser user,
                             @Valid @RequestBody CreateAbsenceDto input) {
        return service.create(user.teacherId(), input);
    }

    /** Lehrkraft: eigene Meldung bearbeiten. */
    @PutMapping("/me/{id}")
    public AbsenceDto update(@AuthenticationPrincipal CurrentUser user,
                             @PathVariable Long id,
                             @Valid @RequestBody CreateAbsenceDto input) {
        return service.update(user.teacherId(), id, input);
    }
}