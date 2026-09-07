package de.wsp3_1.changelog.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Schreibt Verlaufseintraege. Gelesen wird nicht hier, sondern in
 * {@code ChangelogService.find(...)} — die Antwort braucht die Klassenbezeichnung und den
 * Benutzernamen aus fremden Tabellen (siehe dortiger Kommentar).
 */
public interface ChangelogEntryRepository extends JpaRepository<ChangelogEntry, Long> {
}
