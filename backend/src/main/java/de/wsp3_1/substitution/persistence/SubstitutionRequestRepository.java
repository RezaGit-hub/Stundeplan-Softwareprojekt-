package de.wsp3_1.substitution.persistence;

import de.wsp3_1.substitution.domain.SubstitutionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubstitutionRequestRepository extends JpaRepository<SubstitutionRequest, Long> {

    /** Alle Anfragen, neueste zuerst — ohne Filter (Planung) oder auf eine Lehrkraft eingeschraenkt (App). */
    List<SubstitutionRequest> findAllByOrderByIdDesc();
    List<SubstitutionRequest> findByTeacherIdOrderByIdDesc(Long teacherId);

    /** Nur die offenen Anfragen einer Lehrkraft — die App fragt genau das (Teilindex). */
    List<SubstitutionRequest> findByTeacherIdAndStatusOrderByIdDesc(Long teacherId, SubstitutionRequestStatus status);

    /** Wen habe ich zu dieser konkreten Stunde an diesem Tag schon gefragt? */
    List<SubstitutionRequest> findByLessonIdAndDateOrderByIdAsc(Long lessonId, LocalDate date);

    boolean existsByTeacherIdAndLessonIdAndDate(Long teacherId, Long lessonId, LocalDate date);
    Optional<SubstitutionRequest> findByTeacherIdAndLessonIdAndDate(Long teacherId, Long lessonId, LocalDate date);

    /**
     * Löscht alle Anfragen zu einer konkreten Stunde an einem Tag. Wird von
     * {@code ExceptionService.create} aufgerufen, sobald eine datierte Ausnahme
     * (SUBSTITUTION, CANCEL, MOVE) zu dieser Stunde entsteht — dann ist die Anfrage
     * gegenstandslos und die Lehrkraft soll sie nicht mehr sehen.
     */
    void deleteByLessonIdAndDate(Long lessonId, java.time.LocalDate date);
}