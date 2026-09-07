package de.wsp3_1.masterdata.api;

import de.wsp3_1.masterdata.MasterdataDtos.TeacherDto;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherInput;
import de.wsp3_1.masterdata.domain.TeacherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/teachers} — Lehrkraefte (Vertrag v1, Rolle PLANNING; die Pfadregel steht in
 * {@code auth/api/SecurityConfig}). Der Controller nimmt entgegen und gibt zurueck; alles Fachliche
 * steht in {@link TeacherService}.
 */
@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private final TeacherService service;

    public TeacherController(TeacherService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeacherDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TeacherDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherDto create(@Valid @RequestBody TeacherInput input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public TeacherDto update(@PathVariable Long id, @Valid @RequestBody TeacherInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
