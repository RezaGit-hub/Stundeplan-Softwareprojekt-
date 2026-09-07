package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.shared.domain.NotFoundException;
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
 * {@code GET /api/me/timetable/week} — die eigene Woche der angemeldeten Lehrkraft (A-1, A-2,
 * Web-Seite „Mein Plan"). Einziger Fachendpunkt der Android-App neben {@code GET /api/timegrid}.
 *
 * <p><b>Die Lehrkraft kommt aus dem Token, nie aus einem Parameter</b> (Musskriterium Zugriffsschutz):
 * niemand kann durch Raten einer Kennung fremde Plaene lesen. Ein Konto ohne Lehrkraft — etwa die
 * Planung selbst — bekommt 404, so steht es im Vertrag. Der Pfad liegt unter {@code /api/me/**} und ist
 * deshalb fuer <b>beide</b> Rollen offen ({@code SecurityConfig}); {@code GET /api/timetable} bleibt der
 * Planung vorbehalten.</p>
 */
@RestController
@RequestMapping("/api/me/timetable/week")
public class MyWeekController {

    private final WeekResolver weekResolver;

    public MyWeekController(WeekResolver weekResolver) {
        this.weekResolver = weekResolver;
    }

    @GetMapping
    public WeekDto myWeek(@RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CurrentUser user = CurrentUser.require();
        if (user.teacherId() == null) {
            throw new NotFoundException("Dieses Konto gehört zu keiner Lehrkraft.");
        }
        return WeekDto.of(weekResolver.week(date == null ? LocalDate.now() : date,
                View.TEACHER, user.teacherId()));
    }
}
