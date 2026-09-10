package de.wsp3_1.planning.domain;

/**
 * Womit ein Entwurf beginnt ({@code POST /api/draft}).
 *
 * <ul>
 *   <li>{@code EMPTY} — leerer Entwurf. Zusammen mit {@code POST /api/draft/accept} der Weg zum
 *       leeren aktiven Plan, den der Testdurchlauf vor W-8 verlangt (E-46).</li>
 *   <li>{@code COPY_ACTIVE} — Kopie des aktiven Plans: konfliktfreie Stunden kommen festgelegt
 *       herueber, konfliktbehaftete nicht (W-1).</li>
 * </ul>
 */
public enum DraftSource {
    EMPTY,
    COPY_ACTIVE
}
