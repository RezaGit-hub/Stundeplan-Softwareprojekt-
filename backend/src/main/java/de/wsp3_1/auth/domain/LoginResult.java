package de.wsp3_1.auth.domain;

import de.wsp3_1.shared.CurrentUser;

/**
 * Ergebnis einer erfolgreichen Anmeldung: das Klartext-Token (es entsteht genau hier und verlaesst
 * den Server nur dieses eine Mal), die angemeldete Person und ihr Anzeigename.
 */
public record LoginResult(String token, CurrentUser user, String displayName) {
}
