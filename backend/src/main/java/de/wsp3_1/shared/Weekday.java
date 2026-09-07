package de.wsp3_1.shared;

/**
 * Wochentag eines Zeitfensters. Reihenfolge = Reihenfolge im Stundenraster.
 * In der Datenbank als {@code varchar} mit CHECK-Liste, im Code als Enum.
 *
 * <p>Die Kuerzel sind englisch wie jeder Bezeichner in Code, API und Datenbank
 * (api-contract/openapi.yaml, Schema {@code Weekday}). Deutsche Beschriftungen macht in der Regel
 * die Oberflaeche (frontend/src/lib/timegrid.ts) — der Server braucht sie nur fuer Meldungstexte,
 * die er selbst formuliert: Konfliktmeldungen der Planung („Montag") und Verlaufstexte der
 * Vertretung („Mo"). Beide standen bis zum 05.09. als eigene {@code switch}-Bloecke in
 * {@code planning.ConflictChecker} und {@code substitution.WeekResolver}; hier stehen sie einmal.</p>
 */
public enum Weekday {
    MO, TU, WE, TH, FR, SA;

    /** Ausgeschriebener deutscher Wochentag fuer Meldungstexte (wie {@code DAY_LABEL} in der Oberflaeche). */
    public String label() {
        return switch (this) {
            case MO -> "Montag";
            case TU -> "Dienstag";
            case WE -> "Mittwoch";
            case TH -> "Donnerstag";
            case FR -> "Freitag";
            case SA -> "Samstag";
        };
    }

    /** Kurzform wie in der Oberflaeche ({@code DAY_SHORT} in {@code lib/timegrid.ts}). */
    public String shortLabel() {
        return switch (this) {
            case MO -> "Mo";
            case TU -> "Di";
            case WE -> "Mi";
            case TH -> "Do";
            case FR -> "Fr";
            case SA -> "Sa";
        };
    }
}
