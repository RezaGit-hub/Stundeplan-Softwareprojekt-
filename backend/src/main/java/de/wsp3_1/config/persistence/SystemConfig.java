package de.wsp3_1.config.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Systemkonfiguration der Schule — genau eine Zeile mit {@code id = 1} (Seed in V2).
 * {@code state} und {@code schoolType} sind Daten, kein Bundesland und keine Schulform steht im Code (E-37).
 * Die Endpunkte {@code GET/PUT /api/config} baut Rolle 1 (T-105); hier steht nur die Entity.
 */
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "school_type", nullable = false)
    private String schoolType;

    @Column(name = "max_solve_seconds", nullable = false)
    private int maxSolveSeconds;

    protected SystemConfig() {
    }

    public Long getId() {
        return id;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSchoolType() {
        return schoolType;
    }

    public void setSchoolType(String schoolType) {
        this.schoolType = schoolType;
    }

    public int getMaxSolveSeconds() {
        return maxSolveSeconds;
    }

    public void setMaxSolveSeconds(int maxSolveSeconds) {
        this.maxSolveSeconds = maxSolveSeconds;
    }
}
