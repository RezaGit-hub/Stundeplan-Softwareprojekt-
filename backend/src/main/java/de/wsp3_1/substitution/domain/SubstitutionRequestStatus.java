package de.wsp3_1.substitution.domain;

/**
 * Antwort der Lehrkraft auf eine Vertretungsanfrage (V14). Wortgleich mit der CHECK-Liste
 * {@code substitution_request.status} und mit dem Enum {@code SubstitutionRequestStatus} im
 * Vertrag. {@code UNKNOWN} = noch nicht geantwortet.
 */
public enum SubstitutionRequestStatus {
    UNKNOWN, ACCEPTED, DECLINED
}