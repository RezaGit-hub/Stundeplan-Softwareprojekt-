package de.wsp3_1.masterdata.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Raum. Keine Kapazitaet (Abgrenzung im Pflichtenheft). Sperrzeiten eines Raums sind datiert und
 * stehen als {@code change_event} im Modul {@code substitution}, nicht hier.
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "room_type", nullable = false)
    private String roomType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Room() {
    }

    public Room(String name, String roomType, boolean active) {
        this.name = name;
        this.roomType = roomType;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
