package de.wsp3_1.planning.api;

import de.wsp3_1.planning.api.PlanningDtos.TimetableDto;
import de.wsp3_1.planning.api.PlanningDtos.TimetableSummaryDto;
import de.wsp3_1.planning.domain.PlanReader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Die angenommenen Plaene (Rolle PLANNING).
 *
 * <ul>
 *   <li>{@code GET /api/timetable} — „der aktive Plan": der, der <b>heute</b> gilt; sonst der mit dem
 *       naechsten kuenftigen Beginn; sonst leer ({@code id: null}). Vor der ersten Annahme ist das kein
 *       Fehler, sondern der Anfangszustand.</li>
 *   <li>{@code GET /api/timetables} — alle aktiven Plaene mit ihrem Zeitraum, frueheste zuerst
 *       (#115). Damit waehlt die Oberflaeche zwischen „gilt bis Januar" und „ab Februar".</li>
 *   <li>{@code GET /api/timetables/&#123;id&#125;} — ein bestimmter angenommener Plan, aktiv oder
 *       archiviert; ein Entwurf ist 404.</li>
 * </ul>
 *
 * <p>Die Konflikte werden bei jedem Lesen neu berechnet, damit eine Aenderung an den Grunddaten sofort
 * sichtbar wird (E-31).</p>
 */
@RestController
public class TimetableController {

    private final PlanReader planReader;

    public TimetableController(PlanReader planReader) {
        this.planReader = planReader;
    }

    @GetMapping("/api/timetable")
    public TimetableDto get() {
        return TimetableDto.of(planReader.activePlan());
    }

    @GetMapping("/api/timetables")
    public List<TimetableSummaryDto> list() {
        return planReader.activePlans().stream().map(TimetableSummaryDto::of).toList();
    }

    @GetMapping("/api/timetables/{id}")
    public TimetableDto byId(@PathVariable Long id) {
        return TimetableDto.of(planReader.planById(id));
    }
}
