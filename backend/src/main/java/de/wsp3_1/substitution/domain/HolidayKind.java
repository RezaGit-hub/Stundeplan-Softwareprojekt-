package de.wsp3_1.substitution.domain;

/**
 * Ferien oder gesetzlicher Feiertag (Kann, E-13). Wortgleich mit dem Enum {@code HolidayKind} im
 * Vertrag und mit der CHECK-Liste der Spalte {@code holiday.kind} (Migration V5).
 *
 * <p>Fuer die Wochensicht macht der Unterschied nichts: an beiden ist {@code schoolDay false}. Er
 * steht nur in der Liste, damit die Oberflaeche Ferien und Feiertage getrennt beschriften kann.</p>
 */
public enum HolidayKind {
    VACATION, PUBLIC_HOLIDAY
}
