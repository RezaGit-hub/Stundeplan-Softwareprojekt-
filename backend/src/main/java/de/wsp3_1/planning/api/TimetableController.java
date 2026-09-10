package de.wsp3_1.planning.api;

import de.wsp3_1.planning.api.PlanningDtos.TimetableDto;
import de.wsp3_1.planning.domain.PlanReader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/timetable} — der aktive Regelplan (Rolle PLANNING).
 *
 * <p>Die Konflikte werden bei jedem Lesen neu berechnet, damit eine Aenderung an den Grunddaten sofort
 * sichtbar wird (E-31). Vor der ersten Annahme antwortet der Endpunkt mit {@code id: null} und leeren
 * Listen — das ist kein Fehler, sondern der Anfangszustand.</p>
 */
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final PlanReader planReader;

    public TimetableController(PlanReader planReader) {
        this.planReader = planReader;
    }

    @GetMapping
    public TimetableDto get() {
        return TimetableDto.of(planReader.activePlan());
    }
}
