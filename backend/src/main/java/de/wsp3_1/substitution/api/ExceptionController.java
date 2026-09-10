package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.api.SubstitutionDtos.ExceptionDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.ExceptionInput;
import de.wsp3_1.substitution.domain.ExceptionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code /api/exceptions**} — datierte Ausnahmen: Vertretung, Raumwechsel, Verlegung, Entfall
 * (W-6, W-7, Rolle PLANNING).
 *
 * <p>Die Antwort auf {@code POST} traegt in {@code conflicts} die Folgekonflikte des Tages. Das sind
 * <b>Daten, kein Fehler</b>: gespeichert wird immer, die Planung entscheidet, ob sie damit leben will.
 * Eine zweite Ausnahme fuer dieselbe Stunde am selben Tag ist dagegen 409 {@code exception-exists} —
 * erst aufheben, dann neu anlegen.</p>
 */
@RestController
@RequestMapping("/api/exceptions")
public class ExceptionController {

    private final ExceptionService exceptions;

    public ExceptionController(ExceptionService exceptions) {
        this.exceptions = exceptions;
    }

    @GetMapping
    public List<ExceptionDto> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("to", "darf nicht vor „from“ liegen");
        }
        return exceptions.list(from, to).stream().map(ExceptionDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExceptionDto create(@Valid @RequestBody ExceptionInput input) {
        return ExceptionDto.of(exceptions.create(input.toView()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        exceptions.delete(id);
    }
}
