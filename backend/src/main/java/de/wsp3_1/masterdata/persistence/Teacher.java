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
 * Lehrkraft. {@code weeklyHoursQuota} ist das Deputat in Wochenstunden.
 *
 * <p>Faecher, Wunschzeiten und Sperrzeiten stehen bewusst als reine ID-Mengen
 * ({@code @ElementCollection}) und nicht als {@code @ManyToMany} auf Entities: die API spricht
 * ohnehin nur in IDs, die Fremdschluessel stehen in V1, und das Modul kommt ohne Nachladen
 * ganzer Fach- und Zeitfenster-Objekte aus.</p>
 *
 * <p>Geloescht wird nur ohne Verweise; sonst {@code active = false} (T-W1.3).</p>
 */
@Entity
@Table(name = "teacher")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false, unique = true)
    private String shortName;

    @Column(name = "weekly_hours_quota", nullable = false)
    private int weeklyHoursQuota;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "teacher_subject", joinColumns = @JoinColumn(name = "teacher_id"))
    @Column(name = "subject_id", nullable = false)
    private Set<Long> subjectIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "teacher_preferred_timeslot", joinColumns = @JoinColumn(name = "teacher_id"))
    @Column(name = "timeslot_id", nullable = false)
    private Set<Long> preferredTimeslotIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "teacher_blocked_timeslot", joinColumns = @JoinColumn(name = "teacher_id"))
    @Column(name = "timeslot_id", nullable = false)
    private Set<Long> blockedTimeslotIds = new LinkedHashSet<>();

    protected Teacher() {
    }

    public Teacher(String name, String shortName, int weeklyHoursQuota, boolean active) {
        this.name = name;
        this.shortName = shortName;
        this.weeklyHoursQuota = weeklyHoursQuota;
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

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public int getWeeklyHoursQuota() {
        return weeklyHoursQuota;
    }

    public void setWeeklyHoursQuota(int weeklyHoursQuota) {
        this.weeklyHoursQuota = weeklyHoursQuota;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Long> getSubjectIds() {
        return subjectIds;
    }

    public Set<Long> getPreferredTimeslotIds() {
        return preferredTimeslotIds;
    }

    public Set<Long> getBlockedTimeslotIds() {
        return blockedTimeslotIds;
    }
}
