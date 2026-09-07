package de.wsp3_1.masterdata.api;

import de.wsp3_1.masterdata.MasterdataDtos.TimegridDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimegridInput;
import de.wsp3_1.masterdata.domain.TimegridService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/timegrid} — das Stundenraster.
 *
 * <p>Lesen darf <b>jedes angemeldete Konto</b> ({@code x-role: ANY}): die App und „Mein Plan"
 * brauchen die Uhrzeiten, und im Raster stehen keine personenbezogenen Daten. Schreiben darf nur die
 * Planung. Beides steht als Pfadregel in {@code auth/api/SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/timegrid")
public class TimegridController {

    private final TimegridService service;

    public TimegridController(TimegridService service) {
        this.service = service;
    }

    @GetMapping
    public TimegridDto get() {
        return service.get();
    }

    @PutMapping
    public TimegridDto replace(@Valid @RequestBody TimegridInput input) {
        return service.replace(input);
    }
}
