package de.wsp3_1.substitution.domain;

import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.api.SubstitutionDtos.CreateSubstitutionRequestDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.SubstitutionRequestDto;
import de.wsp3_1.substitution.domain.SubstitutionViews.PendingRowView;
import de.wsp3_1.substitution.domain.SubstitutionViews.SubstitutionRequestView;
import de.wsp3_1.substitution.persistence.SubstitutionRequest;
import de.wsp3_1.substitution.persistence.SubstitutionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vertretungsanfragen (V14/V15). Eine Anfrage haengt an einer konkreten ausfallenden Stunde
 * (lesson_id, date) und wird von der Planung an eine Lehrkraft gestellt; die Lehrkraft antwortet
 * mit ACCEPTED oder DECLINED. Das Annehmen loest <b>keine</b> Vertretung aus — die traegt die
 * Planung wie bisher ueber POST /api/exceptions ein.
 *
 * <p>Anzeigefelder (Klasse, Fach, Position, Raum, ausfallende Lehrkraft) stehen NICHT in der
 * Datenbank; sie kommen beim Lesen aus der pending-Sicht des Tages ({@link WeekResolver}). Liegt
 * die Stunde nach einem Planwechsel nicht mehr in der pending-Sicht (Orphan), wird die Anfrage
 * ausgeblendet — sie bleibt in der Datenbank stehen, taucht aber in keiner Antwort mehr auf.</p>
 *
 * <p>Der pending-Lookup wird pro Tag genau einmal gemacht und in einer Map gehalten; sonst
 * baute jede Anfrage ihre eigene Woche auf (N+1 auf einer teuren Zeile).</p>
 */
@Service
public class SubstitutionRequestService {

    private final SubstitutionRequestRepository requests;
    private final TeacherRepository teachers;
    private final WeekResolver weekResolver;

    public SubstitutionRequestService(SubstitutionRequestRepository requests,
                                      TeacherRepository teachers,
                                      WeekResolver weekResolver) {
        this.requests = requests;
        this.teachers = teachers;
        this.weekResolver = weekResolver;
    }

    // ------------------------------------------------------------------ Planung

    /** Planung: alle Anfragen, neueste zuerst. Optional auf eine Lehrkraft eingeschraenkt. */
    @Transactional(readOnly = true)
    public List<SubstitutionRequestDto> list(Long teacherId) {
        List<SubstitutionRequest> rows = teacherId == null
                ? requests.findAllByOrderByIdDesc()
                : requests.findByTeacherIdOrderByIdDesc(teacherId);
        return toDtos(rows, new HashMap<>());
    }

    /**
     * Planung: mehrere Anfragen in einem Zug. Die ganze Liste laeuft in einer Transaktion —
     * ist eine Zeile ungueltig, wird keine angelegt. Fehler nennen den Index ({@code [2].lessonId}),
     * damit die Oberflaeche die Zeile markieren kann.
     *
     * <p>Leere Eingabe ist kein Fehler: die Antwort ist ein leeres Array (201).</p>
     */
    @Transactional
    public List<SubstitutionRequestDto> create(List<CreateSubstitutionRequestDto> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, Map<Long, PendingRowView>> pendingCache = new HashMap<>();
        List<SubstitutionRequest> neu = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            CreateSubstitutionRequestDto row = input.get(i);
            validateRow(row, i, pendingCache);
            neu.add(new SubstitutionRequest(row.teacherId(), row.lessonId(), row.date()));
        }
        List<SubstitutionRequest> saved = requests.saveAll(neu);
        return toDtos(saved, pendingCache);
    }

    /** Planung: eine Anfrage zurueckziehen — auch nach einer Antwort. */
    @Transactional
    public void withdraw(Long id) {
        SubstitutionRequest request = requests.findById(id)
                .orElseThrow(() -> new NotFoundException("Diese Anfrage gibt es nicht."));
        requests.delete(request);
    }

    // ------------------------------------------------------------------ Lehrkraft

    /**
     * Lehrkraft: eigene Anfragen. Mit {@code open=true} nur die unbeantworteten.
     *
     * <p>Datumsfilter: nur heute oder spaeter. Vergangene unbeantwortete Anfragen erscheinen
     * nicht mehr — sie sind fuer die Lehrkraft gegenstandslos, bleiben aber in der Datenbank
     * stehen (kein Aufraeumen jetzt).</p>
     *
     * <p>Sortierung: nach Datum aufsteigend, dann nach Stundennummer (Position) — was zuerst
     * kommt, steht oben. Die Position kommt aus der pending-Sicht; Orphans fallen vorher raus.</p>
     */
    @Transactional(readOnly = true)
    public List<SubstitutionRequestDto> listForTeacher(Long teacherId, boolean open) {
        List<SubstitutionRequest> rows = open
                ? requests.findByTeacherIdAndStatusOrderByIdDesc(teacherId, SubstitutionRequestStatus.UNKNOWN)
                : requests.findByTeacherIdOrderByIdDesc(teacherId);
        LocalDate heute = LocalDate.now();
        List<SubstitutionRequest> sichtbar = rows.stream()
                .filter(r -> !r.getDate().isBefore(heute))
                .toList();
        List<SubstitutionRequestDto> out = new ArrayList<>(toDtos(sichtbar, new HashMap<>()));
        out.sort(Comparator
                .comparing(SubstitutionRequestDto::date)
                .thenComparing(dto -> dto.position() == null ? Integer.MAX_VALUE : dto.position())
                .thenComparing(SubstitutionRequestDto::id));
        return out;
    }

    /**
     * Lehrkraft: eigene Anfrage beantworten.
     *
     * @throws ValidationException {@code status} fehlt oder ist {@code UNKNOWN}
     * @throws NotFoundException   die Anfrage gibt es nicht oder sie gehoert einer anderen Lehrkraft
     * @throws ConflictException   die Anfrage ist schon beantwortet
     */
    @Transactional
    public SubstitutionRequestDto answer(Long id, Long teacherId, SubstitutionRequestStatus status) {
        if (status == null || status == SubstitutionRequestStatus.UNKNOWN) {
            throw new ValidationException("status", "Bitte mit ACCEPTED oder DECLINED antworten.");
        }
        SubstitutionRequest request = requests.findById(id)
                .orElseThrow(() -> new NotFoundException("Diese Anfrage gibt es nicht."));
        if (!request.getTeacherId().equals(teacherId)) {
            // Fremde Anfrage — nicht verraten, dass es sie gibt.
            throw new NotFoundException("Diese Anfrage gibt es nicht.");
        }
        request.setStatus(status);
        PendingRowView pending = pendingRow(request.getDate(), request.getLessonId(), new HashMap<>());
        if (pending == null) {
            // Die Stunde ist inzwischen weg (Planwechsel): trotzdem antworten, aber keine
            // Anzeigefelder mehr liefern — die Antwort bleibt ein schlankes Echo.
            return new SubstitutionRequestDto(request.getId(), request.getTeacherId(),
                    request.getLessonId(), request.getDate(),
                    null, null, null, null, null, request.getStatus());
        }
        return SubstitutionRequestDto.of(toView(request), pending);
    }

    // ------------------------------------------------------------------ Helpers

    private void validateRow(CreateSubstitutionRequestDto row, int index,
                             Map<LocalDate, Map<Long, PendingRowView>> cache) {
        String prefix = "[" + index + "].";
        if (row == null) {
            throw new ValidationException("[" + index + "]", "darf nicht leer sein");
        }
        if (row.teacherId() == null) {
            throw new ValidationException(prefix + "teacherId", "Bitte die Lehrkraft angeben.");
        }
        if (row.lessonId() == null) {
            throw new ValidationException(prefix + "lessonId", "Bitte die Stunde angeben.");
        }
        if (row.date() == null) {
            throw new ValidationException(prefix + "date", "Bitte den Tag angeben.");
        }
        if (!teachers.existsById(row.teacherId())) {
            throw new ValidationException(prefix + "teacherId", "Diese Lehrkraft gibt es nicht.");
        }
        if (pendingRow(row.date(), row.lessonId(), cache) == null) {
            throw new ValidationException(prefix + "lessonId", "Diese Stunde ist an diesem Tag nicht offen.");
        }
        if (requests.existsByTeacherIdAndLessonIdAndDate(row.teacherId(), row.lessonId(), row.date())) {
            throw new ConflictException("in-use", "Diese Lehrkraft wurde zu dieser Stunde schon gefragt.");
        }
    }

    /**
     * Die pending-Zeile einer Stunde an einem Tag; {@code null}, wenn es sie nicht (mehr) gibt.
     * Der Tag wird nur beim ersten Zugriff aufgeloest.
     */
    private PendingRowView pendingRow(LocalDate date, Long lessonId,
                                      Map<LocalDate, Map<Long, PendingRowView>> cache) {
        if (date == null || lessonId == null) {
            return null;
        }
        Map<Long, PendingRowView> proTag = cache.computeIfAbsent(date, tag ->
                weekResolver.pending(tag, tag).stream()
                        .collect(Collectors.toMap(PendingRowView::lessonId, Function.identity(),
                                (a, b) -> a)));
        return proTag.get(lessonId);
    }

    /**
     * Die Anfragen als DTOs. Anfragen, deren pending-Zeile fehlt (Planwechsel), werden
     * ausgeblendet — der {@code pendingCache} wird zwischen Aufrufen nicht mitgeschleppt.
     */
    private List<SubstitutionRequestDto> toDtos(List<SubstitutionRequest> rows,
                                                Map<LocalDate, Map<Long, PendingRowView>> cache) {
        List<SubstitutionRequestDto> out = new ArrayList<>(rows.size());
        for (SubstitutionRequest r : rows) {
            PendingRowView pending = pendingRow(r.getDate(), r.getLessonId(), cache);
            if (pending == null) {
                continue;
            }
            out.add(SubstitutionRequestDto.of(toView(r), pending));
        }
        return out;
    }

    private static SubstitutionRequestView toView(SubstitutionRequest r) {
        return new SubstitutionRequestView(r.getId(), r.getTeacherId(), r.getLessonId(), r.getDate(),
                r.getStatus());
    }
}