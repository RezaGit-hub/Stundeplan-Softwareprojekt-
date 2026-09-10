package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.api.SubstitutionDtos.EventDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.EventInput;
import de.wsp3_1.substitution.domain.ChangeEventService;
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
 * {@code /api/events**} — Ausfaelle, Raumsperrungen und Zusatzstunden erfassen (W-5, Rolle PLANNING).
 *
 * <p>Die Antwort nennt in {@code affected} die betroffenen Stunden und in {@code hint} den Satz fuer
 * den Fall, dass keine Stunde betroffen ist. Beides wird bei jedem Lesen neu berechnet, nichts davon
 * steht in der Datenbank (E-31); der Regelplan bleibt unveraendert (T-W5.1).</p>
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final ChangeEventService events;

    public EventController(ChangeEventService events) {
        this.events = events;
    }

    @GetMapping
    public List<EventDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long roomId) {
        // Wie in SubstitutionController.requireRange (SU-16): ein verdrehter Bereich ist ein
        // Eingabefehler und keine leere Liste — sonst sieht niemand den vertauschten Wert.
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("to", "darf nicht vor „from“ liegen");
        }
        return events.list(from, to, teacherId, roomId).stream().map(EventDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto create(@Valid @RequestBody EventInput input) {
        return EventDto.of(events.create(input.toView()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        events.delete(id);
    }
}
