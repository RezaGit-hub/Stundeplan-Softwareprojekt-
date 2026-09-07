package de.wsp3_1.substitution.domain;

import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.auth.persistence.UserAccountRepository;
import de.wsp3_1.shared.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Der Verursacher einer Aenderung — aus dem Token, nie aus einem Parameter (VERTRAG Abschnitt 6,
 * „Aktor"). Stand bis zum 05.09. wortgleich in {@code ExceptionService} und {@code ChangeEventService};
 * beide Dienste hielten dafuer ein eigenes {@code UserAccountRepository}, obwohl sie es fuer nichts
 * anderes brauchten.
 */
@Component
public class Actors {

    private final UserAccountRepository accounts;

    public Actors(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Das angemeldete Konto. Fehlt es, ist die Sitzung ungueltig geworden — kein Fachfehler. */
    public UserAccount current() {
        CurrentUser user = CurrentUser.require();
        return accounts.findById(user.id())
                .orElseThrow(() -> new IllegalStateException("Das angemeldete Konto gibt es nicht mehr."));
    }
}
