package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.MasterdataDtos.RoomDto;
import de.wsp3_1.masterdata.MasterdataDtos.RoomInput;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Raeume anlegen, lesen, aendern, deaktivieren, loeschen. {@code roomType} ist Freitext und wird
 * spaeter exakt mit der Raumanforderung des Fachs verglichen. Ein belegter Raum wird nicht geloescht,
 * sondern deaktiviert (409 {@code in-use}).
 */
@Service
public class RoomService {

    private final RoomRepository rooms;
    private final ClassSubjectRepository classSubjects;

    public RoomService(RoomRepository rooms, ClassSubjectRepository classSubjects) {
        this.rooms = rooms;
        this.classSubjects = classSubjects;
    }

    @Transactional(readOnly = true)
    public List<RoomDto> list() {
        return rooms.findAllByOrderByIdAsc().stream().map(RoomService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RoomDto get(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public RoomDto create(RoomInput input) {
        validate(input, null);
        return toDto(rooms.save(new Room(input.name().trim(), input.roomType().trim(), input.active())));
    }

    @Transactional
    public RoomDto update(Long id, RoomInput input) {
        Room room = require(id);
        validate(input, id);
        room.setName(input.name().trim());
        room.setRoomType(input.roomType().trim());
        room.setActive(input.active());
        return toDto(rooms.save(room));
    }

    @Transactional
    public void delete(Long id) {
        Room room = require(id);
        long lessons = classSubjects.countLessonsByRoom(id);
        if (lessons > 0) {
            throw new ConflictException("in-use",
                    room.getName() + " ist in " + lessons + " Stunden eingeplant. Deaktivieren statt löschen.");
        }
        rooms.delete(room);
    }

    private Room require(Long id) {
        return rooms.findById(id)
                .orElseThrow(() -> new NotFoundException("Es gibt keinen Raum mit der Nummer " + id + "."));
    }

    private void validate(RoomInput input, Long selfId) {
        rooms.findByNameIgnoreCase(input.name().trim())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new ValidationException("name", "Name ist bereits vergeben");
                });
    }

    private static RoomDto toDto(Room room) {
        return new RoomDto(room.getId(), room.getName(), room.getRoomType(), room.isActive());
    }
}
