package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.MasterdataDtos.TimegridDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimegridInput;
import de.wsp3_1.masterdata.MasterdataDtos.TimeslotDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimeslotInput;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.Timeslot;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Das Stundenraster: Mo–Fr (oder Sa), je Tag Zeitfenster mit laufender Nummer, Pausen zaehlen mit
 * (G-3). {@code PUT /api/timegrid} ersetzt das ganze Raster in einem Zug.
 *
 * <p><b>Upsert nach (Tag, Position)</b>, nicht nach {@code id}: die Oberflaeche schickt eine Tabelle,
 * kein Aenderungsprotokoll. Ein Zeitfenster, das in der neuen Tabelle fehlt, wird geloescht — es sei
 * denn, eine Stunde des aktiven Plans oder des Entwurfs liegt darin (oder eine Verlegung zeigt\n * darauf); dann 409 {@code in-use} mit den betroffenen Zeitfenstern, damit kein Plan zerbricht.\n * Stunden archivierter Plaene halten nichts fest und werden vor dem Loeschen entfernt.</p>
 *
 * <p>Verschwindet ein Zeitfenster, verschwinden mit ihm die Wunsch- und Sperrzeiten der Lehrkraefte
 * und die Sperrzeiten der Raeume, die darauf zeigen. Das ist kein Datenverlust nebenbei, sondern die
 * einzige Moeglichkeit: die Fremdschluessel in {@code teacher_preferred_timeslot},
 * {@code teacher_blocked_timeslot} und {@code room_blocked_timeslot} (V9) muessen vorher weg.</p>
 */
@Service
public class TimegridService {

    private static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    private final TimeslotRepository timeslots;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final ClassSubjectRepository classSubjects;

    public TimegridService(TimeslotRepository timeslots, TeacherRepository teachers, RoomRepository rooms,
                           ClassSubjectRepository classSubjects) {
        this.timeslots = timeslots;
        this.teachers = teachers;
        this.rooms = rooms;
        this.classSubjects = classSubjects;
    }

    @Transactional(readOnly = true)
    public TimegridDto get() {
        return new TimegridDto(Timegrid.sortedByGrid(timeslots.findAll()).stream()
                .map(TimegridService::toDto)
                .toList());
    }

    @Transactional
    public TimegridDto replace(TimegridInput input) {
        List<TimeslotInput> wanted = input.slots() == null ? List.of() : input.slots();
        validate(wanted);

        List<Timeslot> existing = new ArrayList<>(timeslots.findAll());
        Set<Long> keep = new HashSet<>();
        for (TimeslotInput row : wanted) {
            Optional<Timeslot> match = existing.stream()
                    .filter(slot -> slot.getDay() == row.day() && slot.getPosition() == row.position())
                    .findFirst();
            if (match.isPresent()) {
                Timeslot slot = match.get();
                slot.setStartTime(LocalTime.parse(row.startTime()));
                slot.setEndTime(LocalTime.parse(row.endTime()));
                slot.setBreak(Boolean.TRUE.equals(row.isBreak()));
                keep.add(slot.getId());
            } else {
                Timeslot slot = timeslots.save(new Timeslot(row.day(), row.position(),
                        LocalTime.parse(row.startTime()), LocalTime.parse(row.endTime()),
                        Boolean.TRUE.equals(row.isBreak())));
                keep.add(slot.getId());
            }
        }

        List<Timeslot> removed = existing.stream().filter(slot -> !keep.contains(slot.getId())).toList();
        List<String> belegt = removed.stream()
                .filter(slot -> classSubjects.countLessonsByTimeslot(slot.getId()) > 0
                        || classSubjects.countExceptionsByNewTimeslot(slot.getId()) > 0)
                .map(TimegridService::label)
                .toList();
        if (!belegt.isEmpty()) {
            throw new ConflictException("in-use", belegt.size() + " Zeitfenster sind im aktiven Plan, im Entwurf "
                    + "oder in einer Verlegung belegt und können nicht entfernt werden: "
                    + String.join(", ", belegt) + ". Erst die Stunden verschieben oder den Plan leeren.");
        }
        if (!removed.isEmpty()) {
            Set<Long> removedIds = removed.stream().map(Timeslot::getId).collect(Collectors.toSet());
            for (Teacher teacher : teachers.findAllByOrderByIdAsc()) {
                teacher.getPreferredTimeslotIds().removeAll(removedIds);
                teacher.getBlockedTimeslotIds().removeAll(removedIds);
            }
            teachers.flush();
            for (Room room : rooms.findAllByOrderByIdAsc()) {
                room.getBlockedTimeslotIds().removeAll(removedIds);
            }
            rooms.flush();
            // Archivierte Plaene zaehlen oben nicht als Belegung, ihre Stunden zeigen aber noch per
            // Fremdschluessel auf das Zeitfenster; ohne diesen Schritt bricht das DELETE mit 23503 ab.
            for (Timeslot slot : removed) {
                classSubjects.deleteArchivedLessonsByTimeslot(slot.getId());
            }
            timeslots.deleteAll(removed);
        }
        timeslots.flush();
        return get();
    }

    /** Zeitfenster fuer Meldungen: Wochentag und Beginn, z. B. "Mo 12:40". */
    private static String label(Timeslot slot) {
        String tag = switch (slot.getDay()) {
            case MO -> "Mo";
            case TU -> "Di";
            case WE -> "Mi";
            case TH -> "Do";
            case FR -> "Fr";
            case SA -> "Sa";
        };
        return tag + " " + slot.getStartTime();
    }

    private static void validate(List<TimeslotInput> rows) {
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            TimeslotInput row = rows.get(i);
            String prefix = "slots[" + i + "].";
            // Eine leere Zeile im Array ({"slots":[null]}) kommt durch die Bean-Validation durch.
            // Ohne diesen Fall liefe der erste Zugriff darauf in eine NullPointerException und der
            // Client bekaeme 500 statt 400.
            if (row == null) {
                errors.add(new FieldError("slots[" + i + "]", "darf nicht leer sein"));
                continue;
            }
            if (row.day() == null) {
                errors.add(new FieldError(prefix + "day", "darf nicht leer sein"));
            }
            if (row.position() == null || row.position() < 1) {
                errors.add(new FieldError(prefix + "position", "muss mindestens 1 sein"));
            }
            // isBreak steht im Vertrag unter `required` (Schema TimeslotInput). Fehlt es, ist das ein
            // Eingabefehler — stillschweigend „keine Pause" anzunehmen macht aus einer vergessenen
            // Pausenzeile eine Unterrichtsstunde.
            if (row.isBreak() == null) {
                errors.add(new FieldError(prefix + "isBreak", "darf nicht leer sein"));
            }
            boolean startOk = row.startTime() != null && TIME.matcher(row.startTime()).matches();
            boolean endOk = row.endTime() != null && TIME.matcher(row.endTime()).matches();
            if (!startOk) {
                errors.add(new FieldError(prefix + "startTime", "Uhrzeit im Format HH:mm"));
            }
            if (!endOk) {
                errors.add(new FieldError(prefix + "endTime", "Uhrzeit im Format HH:mm"));
            } else if (startOk && row.endTime().compareTo(row.startTime()) <= 0) {
                errors.add(new FieldError(prefix + "endTime", "muss nach dem Beginn liegen"));
            }
            if (row.day() != null && row.position() != null && indexOfSlot(rows, i) != i) {
                errors.add(new FieldError(prefix + "position", "Position ist doppelt"));
            }
            if (startOk && endOk && row.day() != null && overlapsEarlierSlot(rows, i)) {
                errors.add(new FieldError(prefix + "startTime", "überschneidet sich mit einem anderen Zeitfenster"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Ueberschneidet sich die Zeile mit einer frueheren Zeile desselben Tages? Uhrzeiten im Format
     * HH:mm lassen sich als Text vergleichen — „08:50" &lt; „09:35" gilt zeichenweise genauso wie als
     * Uhrzeit, deshalb braucht es hier kein {@code LocalTime}.
     *
     * <p>Ohne diese Pruefung liessen sich zwei Montagsstunden mit denselben Uhrzeiten anlegen: die
     * Positionen waeren verschieden, das Raster saehe im Browser aber doppelt belegt aus. Gemeldet wird
     * nur bei der spaeteren Zeile, damit dieselbe Ueberschneidung nicht zweimal in der Feldliste steht.</p>
     */
    private static boolean overlapsEarlierSlot(List<TimeslotInput> rows, int index) {
        TimeslotInput row = rows.get(index);
        for (int i = 0; i < index; i++) {
            TimeslotInput other = rows.get(i);
            if (other == null || other.day() != row.day()
                    || other.startTime() == null || other.endTime() == null) {
                continue;
            }
            // Zwei Zeitraeume ueberschneiden sich, wenn jeder vor dem Ende des anderen beginnt.
            if (row.startTime().compareTo(other.endTime()) < 0
                    && other.startTime().compareTo(row.endTime()) < 0) {
                return true;
            }
        }
        return false;
    }

    /** Erster Index mit demselben (Tag, Position) — steht er nicht bei sich selbst, ist die Zeile doppelt. */
    private static int indexOfSlot(List<TimeslotInput> rows, int index) {
        TimeslotInput row = rows.get(index);
        for (int i = 0; i < rows.size(); i++) {
            TimeslotInput other = rows.get(i);
            // Leere Zeilen ueberspringen: sie sind schon als eigener Fehler gemeldet.
            if (other != null && row.day() == other.day() && row.position().equals(other.position())) {
                return i;
            }
        }
        return index;
    }

    private static TimeslotDto toDto(Timeslot slot) {
        return new TimeslotDto(slot.getId(), slot.getDay(), slot.getPosition(),
                slot.getStartTime().toString(), slot.getEndTime().toString(), slot.isBreak());
    }
}
