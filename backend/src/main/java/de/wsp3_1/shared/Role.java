package de.wsp3_1.shared;

/**
 * Rolle eines Kontos. {@code PLANNING} ist die Planung (bei adesso „Admin" genannt, in der Oberflaeche
 * „Planung"), {@code TEACHER} eine Lehrkraft, die nur ihren eigenen Plan lesen darf (E-38),
 * {@code STUDENT} ein Klassenkonto, das nur den Plan seiner eigenen Klasse lesen darf (E-50).
 *
 * <p>Liegt in {@code shared} und nicht in {@code auth.persistence} (VERTRAG Abschnitt 4): {@code changelog}
 * und {@code substitution} brauchen die Rolle, sollen dafuer aber nicht das Modul {@code auth} importieren.
 * Additiv ergaenzt vom Modul auth am 05.09.</p>
 *
 * <p><b>Warum {@code STUDENT} und nicht ein Konto je Person?</b> Ein Konto je Klasse ist die von den
 * Aufsichtsbehoerden tolerierte Minimalloesung, solange es keine personalisierten Zugaenge (SSO) gibt
 * — Recherche {@code 07-Konzept/recherche/sammelzugang-schueler.md}, Abschnitt „Bewertung". Die Sicht
 * zeigt deshalb nur die eigene Klasse, Lehrkraefte nur als Kuerzel und nie einen Abwesenheitsgrund.</p>
 */
public enum Role {

    PLANNING,
    TEACHER,
    STUDENT;

    /** Spring Security erwartet das Praefix {@code ROLE_} in den Berechtigungen. */
    public String authority() {
        return "ROLE_" + name();
    }
}
