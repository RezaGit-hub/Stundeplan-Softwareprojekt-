package de.wsp3_1.substitution.persistence;

import de.wsp3_1.substitution.domain.ExceptionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LessonExceptionRepository extends JpaRepository<LessonException, Long> {

    /** Ausnahmen mit {@code date} im Bereich — {@code GET /api/exceptions}. */
    List<LessonException> findByDateBetweenOrderByIdAsc(LocalDate von, LocalDate bis);

    /**
     * Ausnahmen, die den Bereich beruehren: nach ihrem Tag <b>oder</b> ihrem Zieltag. Eine Verlegung
     * wirkt an beiden Tagen, und die Wochensicht braucht sie an beiden — deshalb reicht
     * {@link #findByDateBetweenOrderByIdAsc} fuer die Aufloesung nicht.
     */
    @Query("select e from LessonException e "
            + "where (e.date between :von and :bis) or (e.newDate between :von and :bis) "
            + "order by e.id asc")
    List<LessonException> findTouching(LocalDate von, LocalDate bis);

    /** Die eine Ausnahme dieser Stunde an diesem Tag (UNIQUE in V4) — fuer 409 {@code exception-exists}. */
    Optional<LessonException> findByLessonIdAndDate(Long lessonId, LocalDate date);

    /**
     * Ausnahmen an einer Stunde des Plans ab diesem Tag — hinter dem Hinweis „N Vertretungen verfallen"
     * beim Annehmen eines Entwurfs (ueber {@code shared.domain.ExpiringExceptions}). Beim Annehmen werden
     * sie auf die Stunden des neuen Plans umgehaengt, soweit es sie dort unveraendert gibt (Befund
     * PFLICHTENHEFT-9); Zusatzstunden haengen an keiner Stunde und bleiben draussen.
     */
    List<LessonException> findByDateGreaterThanEqualAndLessonIdIsNotNullOrderByIdAsc(LocalDate von);

    /** Die Zusatzstunden, die zu einem Ereignis gehoeren; sie werden mit ihm geloescht. */
    @Query("select e from LessonException e join e.eventIds v where e.kind = :kind and v = :eventId")
    List<LessonException> findByKindAndEventId(ExceptionKind kind, Long eventId);
}
