package de.wsp3_1.shared.domain;

/** Unbekannte ID oder fehlende Ressource. Wird vom {@code ApiExceptionHandler} zu 404 {@code not-found}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
