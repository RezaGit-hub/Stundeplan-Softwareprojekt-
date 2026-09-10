package de.wsp3_1.config.api;

import de.wsp3_1.config.domain.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET/PUT /api/config} — Schulname, Bundesland, Schulform und Rechenzeit (W-1, E-37).
 * Nur die Rolle Planung; die Pfadregel dafuer steht in {@code auth/api/SecurityConfig}
 * (alles unter {@code /api/**} ausser den dort genannten Ausnahmen).
 *
 * <p>Der Schulname fuer die Anmeldeseite und die App kommt aus {@code GET /api/health},
 * nicht von hier: {@code /api/health} ist ohne Anmeldung erreichbar.</p>
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final SystemConfigService service;

    public ConfigController(SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ConfigDto get() {
        return ConfigDto.von(service.get());
    }

    @PutMapping
    public ConfigDto put(@Valid @RequestBody ConfigDto body) {
        return ConfigDto.von(service.update(body.zuDaten()));
    }
}
