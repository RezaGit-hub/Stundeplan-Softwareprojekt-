package de.wsp3_1.substitution.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import de.wsp3_1.substitution.domain.HolidayKind;

import java.time.LocalDate;

/**
 * Ein unterrichtsfreier Zeitraum: Ferien oder Feiertag (Kann, E-13; Tabelle {@code holiday} aus V5).
 *
 * <p>Er haengt am Kalendertag, nie am Plan — wie alles Datierte. Der {@code WeekResolver} blendet die
 * Stunden dieser Tage aus ({@code schoolDay: false}, keine Eintraege); der Regelplan bleibt unberuehrt.
 * Zeitraeume anlegen und loeschen kann man erst ueber {@code /api/holidays}.</p>
 */
@Entity
@Table(name = "holiday")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private HolidayKind kind;

    protected Holiday() {
    }

    public Holiday(String name, LocalDate dateFrom, LocalDate dateTo, HolidayKind kind) {
        this.name = name;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public HolidayKind getKind() {
        return kind;
    }
}
