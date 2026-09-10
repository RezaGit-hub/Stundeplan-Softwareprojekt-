package de.wsp3_1.substitution.api;

import de.wsp3_1.substitution.api.SubstitutionDtos.WeekDto;
import de.wsp3_1.substitution.domain.View;
import de.wsp3_1.substitution.domain.WeekResolver;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * {@code GET /api/timetable/week} — die aufgeloeste Kalenderwoche in vier Sichten (W-4, Rolle PLANNING).
 *
 * <p>{@code date} darf ein beliebiger Tag der Woche sein; die Antwort beginnt immer am Montag. Die Sicht
 * {@code ROOM} ist Musskriterium E-39 aus Interview 1. Fehlt {@code id} bei einer Sicht, die eine
 * braucht, ist das 400; zeigt sie ins Leere, 404 — beides entscheidet der {@link WeekResolver}.</p>
 */
@RestController
@RequestMapping("/api/timetable/week")
public class WeekController {

    private final WeekResolver weekResolver;

    public WeekController(WeekResolver weekResolver) {
        this.weekResolver = weekResolver;
    }

    @GetMapping
    public WeekDto week(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        @RequestParam View view,
                        @RequestParam(required = false) Long id) {
        return WeekDto.of(weekResolver.week(date, view, id));
    }
}
