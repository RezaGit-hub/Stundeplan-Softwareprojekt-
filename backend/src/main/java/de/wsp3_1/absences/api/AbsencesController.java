package de.wsp3_1.absences.api;

import de.wsp3_1.absences.api.AbsenceDtos.AbsenceDto;
import de.wsp3_1.absences.api.AbsenceDtos.ConvertedAbsenceDto;
import de.wsp3_1.absences.api.AbsenceDtos.CreateAbsenceDto;
import de.wsp3_1.absences.domain.AbsenceInput;
import de.wsp3_1.absences.domain.AbsencesService;
import de.wsp3_1.shared.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/absences")
public class AbsencesController {

    private final AbsencesService service;

    public AbsencesController(AbsencesService service) {
        this.service = service;
    }

    @GetMapping
    public List<AbsenceDto> list() {
        return service.listAll().stream().map(AbsenceDto::of).toList();
    }

    @PostMapping("/{id}/convert")
    public ConvertedAbsenceDto convert(@PathVariable Long id) {
        return ConvertedAbsenceDto.of(service.convert(id));
    }

    @GetMapping("/me")
    public List<AbsenceDto> listMe(@AuthenticationPrincipal CurrentUser user) {
        return service.listForTeacher(user.teacherId()).stream().map(AbsenceDto::of).toList();
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceDto create(@AuthenticationPrincipal CurrentUser user,
                             @Valid @RequestBody CreateAbsenceDto input) {
        AbsenceInput domainInput = new AbsenceInput(input.dateFrom(), input.dateTo(),
                input.reason(), input.active());
        return AbsenceDto.of(service.create(user.teacherId(), domainInput));
    }

    @PutMapping("/me/{id}")
    public AbsenceDto update(@AuthenticationPrincipal CurrentUser user,
                             @PathVariable Long id,
                             @Valid @RequestBody CreateAbsenceDto input) {
        AbsenceInput domainInput = new AbsenceInput(input.dateFrom(), input.dateTo(),
                input.reason(), input.active());
        return AbsenceDto.of(service.update(user.teacherId(), id, domainInput));
    }
}