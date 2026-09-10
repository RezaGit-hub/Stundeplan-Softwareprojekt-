package de.wsp3_1.substitution.persistence;

import de.wsp3_1.substitution.domain.HolidayKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Eine Zeile der mitgelieferten Vorlage „Ferien und Feiertage je Bundesland" (E-51; Tabelle
 * {@code holiday_preset} aus V8).
 *
 * <p>Die Vorlage ist <b>Daten, kein Dienst</b>: nichts wird zur Laufzeit aus dem Internet geholt.
 * Sie wird nur gelesen — geschrieben wird sie ausschliesslich von der Migration, die
 * {@code scripts/gen-holiday-presets.mjs} aus der Recherche erzeugt. {@code POST /api/holidays/preset}
 * kopiert die Zeilen des eingestellten Bundeslands in {@code holiday}; dort ist danach jeder Eintrag
 * einzeln aenderbar und loeschbar.</p>
 *
 * <p>{@code source} ist die amtliche Fundstelle der Zeile. Sie bleibt in der Vorlage und wandert
 * <b>nicht</b> mit: {@code holiday} hat kein solches Feld, und ein von Hand geaenderter Zeitraum
 * traegt ohnehin keine Quelle mehr.</p>
 */
@Entity
@Table(name = "holiday_preset")
public class HolidayPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Kuerzel des Bundeslands, wie es in {@code system_config.state} steht („SH"). */
    @Column(name = "state", nullable = false, length = 2)
    private String state;

    /** Schuljahr in der Schreibweise der KMK („2026/27"). */
    @Column(name = "school_year", nullable = false, length = 7)
    private String schoolYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private HolidayKind kind;

    /** Bezeichnung; wird beim Uebernehmen zu {@code holiday.name}. */
    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(name = "source", nullable = false, length = 300)
    private String source;

    protected HolidayPreset() {
    }

    public Long getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    public String getSchoolYear() {
        return schoolYear;
    }

    public HolidayKind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getSource() {
        return source;
    }
}
