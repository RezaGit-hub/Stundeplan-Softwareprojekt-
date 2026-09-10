package de.wsp3_1.substitution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ChangeEventRepository extends JpaRepository<ChangeEvent, Long> {

    /**
     * Alle Ereignisse, deren Zeitraum sich mit {@code [von, bis]} ueberschneidet, aelteste zuerst.
     * Gelesen als: das Ereignis beginnt nicht nach {@code bis} und endet nicht vor {@code von}.
     *
     * <p>Fuer einen einzelnen Tag steht derselbe Tag in beiden Werten — so fragt der
     * {@code WeekResolver} Tag fuer Tag.</p>
     */
    List<ChangeEvent> findByDateFromLessThanEqualAndDateToGreaterThanEqualOrderByIdAsc(LocalDate bis,
                                                                                       LocalDate von);

    List<ChangeEvent> findAllByOrderByIdAsc();

    /**
     * Wie {@link #findAllByOrderByIdAsc()}, aber mit dem Verursacher in derselben Abfrage:
     * {@code GET /api/events} nennt je Ereignis den Benutzernamen, und {@code created_by} ist
     * {@code LAZY} — ohne das {@code join fetch} kostet die Liste eine Abfrage je Ereignis.
     */
    @Query("select e from ChangeEvent e join fetch e.createdBy order by e.id asc")
    List<ChangeEvent> findAllWithCreator();
}
