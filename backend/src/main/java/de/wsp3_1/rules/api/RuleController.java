package de.wsp3_1.rules.api;

import de.wsp3_1.rules.api.RuleDtos.CreateRuleRequest;
import de.wsp3_1.rules.api.RuleDtos.RuleDto;
import de.wsp3_1.rules.api.RuleDtos.UpdateRuleRequest;
import de.wsp3_1.rules.domain.RuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Das Regelwerk ueber die Schnittstelle (W-2). Alle Pfade sind der Planung vorbehalten — das regelt die
 * Pfadregel {@code /api/**} in {@code auth/api/SecurityConfig}, hier steht dazu nichts.
 *
 * <p>Vertrag: {@code api-contract/openapi.yaml}, Abschnitt {@code /api/rules}.</p>
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** Das ganze Regelwerk. */
    @GetMapping
    public List<RuleDto> list() {
        return ruleService.list().stream().map(RuleDto::of).toList();
    }

    /** Eine Zeile aendern; ein Wert kleiner als 1 wird mit 400 abgelehnt und der alte Wert bleibt stehen. */
    @PutMapping("/{id}")
    public RuleDto update(@PathVariable long id, @Valid @RequestBody UpdateRuleRequest request) {
        return RuleDto.of(ruleService.update(id, request.value(), request.severity(), request.weight(),
                request.active()));
    }

    /** Den Beispielsatz des in der Systemkonfiguration eingestellten Bundeslands laden (E-48). */
    @PostMapping("/reset")
    public List<RuleDto> reset() {
        return ruleService.reset().stream().map(RuleDto::of).toList();
    }

    /** Einen Jahrgangswert anlegen (Soll, E-47). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleDto create(@Valid @RequestBody CreateRuleRequest request) {
        return RuleDto.of(ruleService.create(request.type(), request.scope(), request.grade(), request.value(),
                request.severity(), request.weight(), request.active()));
    }

    /** Einen Jahrgangswert loeschen; danach gilt wieder der schulweite Wert. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
