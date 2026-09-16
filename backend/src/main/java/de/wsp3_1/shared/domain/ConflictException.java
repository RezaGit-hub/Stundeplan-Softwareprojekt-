package de.wsp3_1.shared.domain;

import java.util.Map;

/**
 * Fachlicher Konflikt, der das Speichern verhindert (409).
 * Der {@code slug} landet im {@code type} des ProblemDetail, die {@code extras} als zusaetzliche Felder
 * (etwa {@code missing[]} bei {@code master-data-incomplete} oder {@code conflicts[]} bei {@code pinned-contradiction}).
 * Erlaubte Slugs laut api.md: in-use, solving, master-data-incomplete, pinned-contradiction, data-exists,
 * exception-exists, protected-account; dazu preset-missing (POST /api/holidays/preset ohne Vorlage zu Land und Schuljahr, E-51) und
 * draft-has-conflicts (Annehmen eines Entwurfs mit harten Konflikten, #103, Vertrag 1.3.0). draft-exists gab es bis
 * Vertrag 1.2.0 (ein Entwurf); seit V11 duerfen mehrere nebeneinander stehen.
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
