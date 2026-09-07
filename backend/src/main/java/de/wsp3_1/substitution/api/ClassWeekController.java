package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.substitution.api.SubstitutionDtos.WeekDto;
import de.wsp3_1.substitution.domain.StudentView;
import de.wsp3_1.substitution.domain.View;
import de.wsp3_1.substitution.domain.WeekResolver;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * {@code GET /api/me/class-week} — die Woche der eigenen Klasse fuer ein Klassenkonto (E-50, ST-3).
 *
 * <p><b>Die Klasse kommt aus dem Token, nie aus einem Parameter</b> — genauso wie die Lehrkraft in
 * {@link MyWeekController}. Es gibt keinen Weg, eine fremde Klasse zu adressieren: der einzige
 * Parameter ist {@code date}. Ein Konto ohne Klasse bekommt 404.</p>
 *
 * <p>Die Woche entsteht im gemeinsamen {@link WeekResolver} (Sicht {@code CLASS}) und geht danach
 * durch {@link StudentView} — Anlaesse raus, Kennungen im Vorher-Wert raus. Was uebrig bleibt, ist
 * genau das Bild aus dem Vorbild: Fach, Raum, Lehrkraft-Kuerzel je Kachel und die Art der Aenderung.
 * Ferien und unterrichtsfreie Tage stehen wie in allen anderen Sichten als {@code schoolDay: false}.</p>
 *
 * <p>Der Pfad steht in {@code SecurityConfig} auf „angemeldet". Das ist keine Luecke: Planung und
 * Lehrkraft haben keine Klasse und bekommen hier 404. Beide nutzen ihre eigenen Sichten
 * ({@code GET /api/timetable/week?view=CLASS} bzw. {@code /api/me/timetable/week}), die ein
 * Klassenkonto seinerseits nicht lesen darf.</p>
 */
@RestController
@RequestMapping("/api/me/class-week")
public class ClassWeekController {

    private final WeekResolver weekResolver;

    public ClassWeekController(WeekResolver weekResolver) {
        this.weekResolver = weekResolver;
    }

    @GetMapping
    public WeekDto classWeek(@RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CurrentUser user = CurrentUser.require();
        if (user.classId() == null) {
            throw new NotFoundException("Dieses Konto gehört zu keiner Klasse.");
        }
        return WeekDto.of(StudentView.filter(
                weekResolver.week(date == null ? LocalDate.now() : date, View.CLASS, user.classId())));
    }
}
