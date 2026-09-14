package de.wsp3_1.shared.domain;

import java.util.Map;

/**
 * Fachlicher Konflikt, der das Speichern verhindert (409).
 * Der {@code slug} landet im {@code type} des ProblemDetail, die {@code extras} als zusaetzliche Felder
 * (etwa {@code missing[]} bei {@code master-data-incomplete} oder {@code conflicts[]} bei {@code pinned-contradiction}).
 * Erlaubte Slugs laut api.md: in-use, draft-exists, solving, master-data-incomplete, pinned-contradiction, data-exists,
 * exception-exists, protected-account; dazu preset-missing (POST /api/holidays/preset ohne Vorlage zu Land und Schuljahr, E-51).
 */
public class ConflictException extends RuntimeException {

    private final String slug;
    private final Map<String, Object> extras;

    public ConflictException(String slug, String detail) {
        this(slug, detail, Map.of());
    }

    public ConflictException(String slug, String detail, Map<String, Object> extras) {
        super(detail);
        this.slug = slug;
        this.extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public String slug() {
        return slug;
    }

    public Map<String, Object> extras() {
        return extras;
    }
}
