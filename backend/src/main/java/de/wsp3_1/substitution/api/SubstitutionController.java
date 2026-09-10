package de.wsp3_1.substitution.api;

import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.api.SubstitutionDtos.OptionsDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.PendingRow;
import de.wsp3_1.substitution.domain.SubstitutionOptionsService;
import de.wsp3_1.substitution.domain.WeekResolver;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code /api/substitutions/**} — die vertretungsbeduerftigen Stunden und die Vorschlaege dazu
 * (W-6, Rolle PLANNING).
 *
 * <p>Beide Antworten sind berechnet und nirgends gespeichert (E-31): {@code pending} ist die Liste der
 * Eintraege mit {@code kind = NEEDS_SUBSTITUTION}, {@code options} das Ergebnis von Filtern und
 * Sortieren ueber die Grunddaten. Eingetragen wird eine Vertretung erst mit
 * {@code POST /api/exceptions}.</p>
 */
@RestController
@RequestMapping("/api/substitutions")
public class SubstitutionController {

    private final WeekResolver weekResolver;
    private final SubstitutionOptionsService optionsService;

    public SubstitutionController(WeekResolver weekResolver,
                                  SubstitutionOptionsService optionsService) {
        this.weekResolver = weekResolver;
        this.optionsService = optionsService;
    }

    @GetMapping("/pending")
    public List<PendingRow> pending(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireRange(from, to);
        return weekResolver.pending(from, to).stream().map(PendingRow::of).toList();
    }

    @GetMapping("/options")
    public OptionsDto options(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam Long lessonId) {
        return OptionsDto.of(optionsService.options(date, lessonId));
    }

    /**
     * Von Hand geprueft statt mit {@code @Validated}: an Abfrageparametern wirft Spring sonst eine
     * {@code ConstraintViolationException}, fuer die es im {@code ApiExceptionHandler} keinen Fall
     * gibt — der Client bekaeme 500 statt 400 mit Feldliste (wie im {@code ChangelogController}).
     */
    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("to", "darf nicht vor „from“ liegen");
        }
        // MAX_DAYS zaehlt beide Enden mit: from + 365 Tage sind 366 Kalendertage. Mit `plusDays(MAX_DAYS)`
        // waere ein Tag mehr erlaubt, als der Resolver rechnet — die Antwort verlore still den letzten Tag.
        if (from.plusDays(WeekResolver.MAX_DAYS - 1L).isBefore(to)) {
            throw new ValidationException("to",
                    "höchstens " + WeekResolver.MAX_DAYS + " Tage auf einmal");
        }
    }
}
