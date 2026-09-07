package de.wsp3_1.masterdata.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Klasse im Klassenverband. Keine Klassengroesse (Abgrenzung im Pflichtenheft). {@code grade} ist
 * eine freie Zahl, damit jede Schulform passt — es steht kein Jahrgangsbereich im Code.
 *
 * <p>Die Faecherliste haengt an der Klasse ({@code cascade ALL}, {@code orphanRemoval}); sie wird
 * ueber {@code PUT /api/classes/&#123;id&#125;/subjects} als Ganzes ersetzt.</p>
 */
@Entity
@Table(name = "school_class")
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "grade", nullable = false)
    private int grade;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "schoolClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClassSubject> subjects = new ArrayList<>();

    protected SchoolClass() {
    }

    public SchoolClass(String name, int grade, boolean active) {
        this.name = name;
        this.grade = grade;
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

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<ClassSubject> getSubjects() {
        return subjects;
    }
}
