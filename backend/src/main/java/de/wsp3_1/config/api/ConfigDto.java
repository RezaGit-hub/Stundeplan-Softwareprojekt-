package de.wsp3_1.config.api;

import de.wsp3_1.config.domain.SystemConfigData;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Systemkonfiguration im Vertrag (Schema {@code ConfigDto} in {@code api-contract/openapi.yaml}).
 * Dieselbe Form fuer {@code GET} und {@code PUT}: {@code PUT} ersetzt alle vier Werte.
 *
 * <p>Die Meldungen der Bean-Validation erscheinen unveraendert in {@code errors[].message} der
 * 400-Antwort und stehen deshalb in Schulsprache.</p>
 */
public record ConfigDto(

        // Die Laengengrenzen sind die Spaltenbreiten aus V1 (system_config.school_name varchar(120),
        // school_type varchar(40)). Ohne sie scheitert ein zu langer Wert erst an der Datenbank, und
        // der Client bekaeme 500 statt 400 mit Feldliste.
        @NotBlank(message = "darf nicht leer sein")
        @Size(max = 120, message = "hoechstens 120 Zeichen")
        String schoolName,

        // Die 16 Kuerzel stehen genauso im Vertrag (enum von ConfigDto.state) und in rule_preset.state.
        @NotNull(message = "darf nicht leer sein")
        @Pattern(regexp = "BW|BY|BE|BB|HB|HH|HE|MV|NI|NW|RP|SL|SN|ST|SH|TH",
                message = "muss eines der 16 Bundesland-Kuerzel sein")
        String state,

        @NotBlank(message = "darf nicht leer sein")
        @Size(max = 40, message = "hoechstens 40 Zeichen")
        String schoolType,

        // Obergrenze, weil das Override des Solvers das Basis-Limit aus application.properties ERSETZT
        // und nicht von ihm gedeckelt wird (SV-4, gemessen). Mit parallel-solver-count=1 belegte ein
        // Wert wie 86400 den einen Solver-Thread einen Tag lang. Zehn Minuten sind mehr, als eine
        // Schulplanung je braucht — der Beispielbestand ist nach zehn Sekunden fertig.
        @NotNull(message = "darf nicht leer sein")
        @Min(value = 1, message = "muss mindestens 1 sein")
        @Max(value = 600, message = "höchstens 600")
        Integer maxSolveSeconds) {

    static ConfigDto von(SystemConfigData daten) {
        return new ConfigDto(daten.schoolName(), daten.state(), daten.schoolType(), daten.maxSolveSeconds());
    }

    SystemConfigData zuDaten() {
        return new SystemConfigData(schoolName, state, schoolType, maxSolveSeconds);
    }
}
