package de.wsp3_1.masterdata.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fach. {@code requiredRoomType} ist Freitext und wird spaeter exakt mit {@code Room.roomType}
 * verglichen (Raumanforderung des Fachs, harte Bedingung im Solver) — {@code null} heisst
 * „jeder Raum passt". {@code color} ist die Kachelfarbe im Plan (Hex, #RRGGBB).
 */
@Entity
@Table(name = "subject")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false, unique = true)
    private String shortName;

    @Column(name = "required_room_type")
    private String requiredRoomType;

    @Column(name = "color", nullable = false)
    private String color;

    protected Subject() {
    }

    public Subject(String name, String shortName, String requiredRoomType, String color) {
        this.name = name;
        this.shortName = shortName;
        this.requiredRoomType = requiredRoomType;
        this.color = color;
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

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getRequiredRoomType() {
        return requiredRoomType;
    }

    public void setRequiredRoomType(String requiredRoomType) {
        this.requiredRoomType = requiredRoomType;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
