package de.wsp3_1.masterdata.api;

import de.wsp3_1.masterdata.MasterdataDtos.ClassDto;
import de.wsp3_1.masterdata.MasterdataDtos.ClassInput;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectInput;
import de.wsp3_1.masterdata.domain.SchoolClassService;
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
 * {@code /api/classes} — Klassen und ihre Faecherlisten (Vertrag v1, Rolle PLANNING).
 * Die Faecherliste kommt als eigener Aufruf {@code PUT /api/classes/&#123;id&#125;/subjects} und
 * ersetzt die bisherige vollstaendig.
 */
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final SchoolClassService service;

    public ClassController(SchoolClassService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClassDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ClassDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassDto create(@Valid @RequestBody ClassInput input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public ClassDto update(@PathVariable Long id, @Valid @RequestBody ClassInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PutMapping("/{id}/subjects")
    public ClassDto replaceSubjects(@PathVariable Long id, @RequestBody List<ClassSubjectInput> rows) {
        return service.replaceSubjects(id, rows);
    }
}
