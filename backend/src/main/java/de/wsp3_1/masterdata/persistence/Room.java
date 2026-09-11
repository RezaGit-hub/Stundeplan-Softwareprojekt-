package de.wsp3_1.masterdata.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Raum. Keine Kapazitaet (Abgrenzung im Pflichtenheft). Datierte Sperrzeiten eines Raums
 * (z. B. Wasserschaden) stehen als {@code change_event} im Modul {@code substitution}, nicht hier.
 *
 * <p>{@code blockedTimeslotIds} sind dagegen die <b>woechentlich wiederkehrenden</b> Sperrzeiten
 * (#133, V9): wie bei der Lehrkraft eine reine ID-Menge ({@code @ElementCollection}), immer hart —
 * Solver H8 {@code roomBlockedTime} und Konfliktart {@code ROOM_BLOCKED_TIME}.</p>
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

    @ElementCollection
    @CollectionTable(name = "room_blocked_timeslot", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "timeslot_id", nullable = false)
    private Set<Long> blockedTimeslotIds = new LinkedHashSet<>();

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

    public Set<Long> getBlockedTimeslotIds() {
        return blockedTimeslotIds;
    }
}
