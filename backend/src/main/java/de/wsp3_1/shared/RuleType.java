package de.wsp3_1.shared;

/**
 * Katalog der Regeltypen des Regelwerks. Neu am 05.09. durch das Modul {@code rules} ergaenzt —
 * additive Ergaenzung in {@code shared}, weil ausser {@code rules} auch {@code planning} (ConflictChecker)
 * und {@code solver} (RuleFact) den Typ brauchen und {@code solver} nur {@code shared} benutzen darf
 * (VERTRAG Abschnitt 4 und Abweichung D-1).
 *
 * <p>In der Datenbank steht der Typ als {@code varchar} mit CHECK-Liste (Tabellen {@code rule} und
 * {@code rule_preset}, Migration V1); die deutschen Beschriftungen macht
 * {@code de.wsp3_1.rules.domain.RuleLabels}.</p>
 *
 * <ul>
 *   <li>{@code MAX_LESSONS_PER_DAY} — hoechstens n Stunden an einem Tag (je Klasse oder je Lehrkraft)</li>
 *   <li>{@code MAX_LESSONS_PER_WEEK} — hoechstens n Stunden in der Woche (je Klasse oder je Lehrkraft)</li>
 *   <li>{@code NO_LESSONS_IN_BREAKS} — in Pausen wird nicht geplant; ohne Objekt, der Wert ist bedeutungslos</li>
 * </ul>
 */
public enum RuleType {
    MAX_LESSONS_PER_DAY,
    MAX_LESSONS_PER_WEEK,
    NO_LESSONS_IN_BREAKS
}
