package de.wsp3_1.substitution.persistence;

import de.wsp3_1.substitution.domain.SubstitutionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubstitutionRequestRepository extends JpaRepository<SubstitutionRequest, Long> {

    /** Alle Anfragen eines Ereignisses — Sicht der Planung. */
    List<SubstitutionRequest> findByEventIdOrderByIdAsc(Long eventId);

    /** Alle Anfragen an eine Lehrkraft, neueste zuerst. */
    List<SubstitutionRequest> findByTeacherIdOrderByIdDesc(Long teacherId);

    /** Nur die offenen — die App fragt genau das; nutzt den Teilindex {@code substitution_request_open_idx}. */
    List<SubstitutionRequest> findByTeacherIdAndStatusOrderByIdDesc(Long teacherId, SubstitutionRequestStatus status);

    boolean existsByTeacherIdAndEventId(Long teacherId, Long eventId);

    Optional<SubstitutionRequest> findByTeacherIdAndEventId(Long teacherId, Long eventId);
}