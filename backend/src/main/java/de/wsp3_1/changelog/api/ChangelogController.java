package de.wsp3_1.changelog.api;

import de.wsp3_1.changelog.api.ChangelogDtos.ChangelogPage;
import de.wsp3_1.changelog.domain.ChangelogService;
import de.wsp3_1.changelog.domain.ChangelogViews.Query;
import de.wsp3_1.shared.domain.ValidationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * {@code GET /api/changelog} — der Aenderungsverlauf, nur lesend (W-7, Rolle PLANNING).
 *
 * <p><b>Diese Klasse hat genau eine Methode, und das ist der Punkt.</b> T-W7.1 verlangt, dass es keinen
 * Weg gibt, einen Eintrag zu aendern oder zu loeschen: kein {@code PUT}, kein {@code PATCH}, kein
 * {@code DELETE}, kein {@code POST}. Ein {@code DELETE /api/changelog/1} findet deshalb keine Route und
 * endet mit 404. {@code ChangelogControllerTest} haelt das fest.</p>
 */
@RestController
@RequestMapping("/api/changelog")
public class ChangelogController {

    private final ChangelogService changelog;

    public ChangelogController(ChangelogService changelog) {
        this.changelog = changelog;
    }

    @GetMapping
    public ChangelogPage list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long lessonId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + ChangelogService.DEFAULT_SIZE) int size) {

        // Von Hand geprueft statt mit @Validated: an Abfrageparametern wirft Spring sonst eine
        // ConstraintViolationException, fuer die es im ApiExceptionHandler keinen Fall gibt — der
        // Client bekaeme 500 statt 400 mit Feldliste.
        if (page < 0) {
            throw new ValidationException("page", "darf nicht negativ sein");
        }
        if (size < 1 || size > ChangelogService.MAX_SIZE) {
            throw new ValidationException("size", "muss zwischen 1 und " + ChangelogService.MAX_SIZE + " liegen");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("to", "darf nicht vor „from“ liegen");
        }

        return ChangelogPage.of(changelog.find(new Query(from, to, classId, teacherId, lessonId, page, size)));
    }
}
