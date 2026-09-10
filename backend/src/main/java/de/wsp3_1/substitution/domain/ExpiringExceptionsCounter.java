package de.wsp3_1.substitution.domain;

import de.wsp3_1.shared.domain.ExpiringExceptions;
import de.wsp3_1.substitution.persistence.LessonException;
import de.wsp3_1.substitution.persistence.LessonExceptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Die Umsetzung von {@link ExpiringExceptions} — sie steht im Modul, dem die Tabelle gehoert. */
@Component
public class ExpiringExceptionsCounter implements ExpiringExceptions {

    private final LessonExceptionRepository exceptions;

    public ExpiringExceptionsCounter(LessonExceptionRepository exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    @Transactional(readOnly = true)
    public long countExpiring(LocalDate from, Predicate<LocalDate> affectedDay, Map<Long, Long> sameLesson) {
        return zaehle(from, affectedDay, sameLesson, false);
    }

    /**
     * Haengt um, was der neue Plan unveraendert weiterfuehrt, und zaehlt den Rest.
     *
     * <p>Laeuft in der Transaktion des Annehmens ({@code DraftService.accept} ist {@code @Transactional}):
     * entweder wird der Plan getauscht <b>und</b> die Vertretungen wandern mit, oder es bleibt alles beim
     * Alten. Ein halber Wechsel — neuer Plan, verwaiste Vertretungen — ist damit ausgeschlossen.</p>
     */
    @Override
    @Transactional
    public long carryOver(LocalDate from, Predicate<LocalDate> affectedDay, Map<Long, Long> sameLesson) {
        return zaehle(from, affectedDay, sameLesson, true);
    }

    /**
     * Die eine Rechnung hinter beiden Methoden: was keine Entsprechung hat, verfaellt. Beim Annehmen
     * ({@code umhaengen}) wandert der Rest mit, in der Vorschau bleibt die Tabelle unberuehrt — sonst
     * koennten Dialog und Antwort verschiedene Zahlen nennen.
     *
     * <p>Tage, an denen der getauschte Plan gar nicht gilt, bleiben aussen vor: dort haengt die Ausnahme
     * an einem anderen, weiterhin geltenden Plan (siehe {@code affectedDay}).</p>
     */
    private long zaehle(LocalDate from, Predicate<LocalDate> affectedDay, Map<Long, Long> sameLesson,
                        boolean umhaengen) {
        List<LessonException> rows =
                exceptions.findByDateGreaterThanEqualAndLessonIdIsNotNullOrderByIdAsc(from);
        long verfallen = 0;
        for (LessonException row : rows) {
            if (!affectedDay.test(row.getDate())) {
                continue;
            }
            Long neu = sameLesson.get(row.getLessonId());
            if (neu == null) {
                verfallen++;
            } else if (umhaengen) {
                row.moveToLesson(neu);
            }
        }
        return verfallen;
    }
}
