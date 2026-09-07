package de.wsp3_1.shared.domain;

import java.util.List;

/**
 * Eingabefehler, den die Bean-Validation nicht sehen kann: ein bereits vergebenes Kuerzel, ein
 * doppeltes Fach in einer Liste, eine Uhrzeit, die vor dem Beginn liegt. Der
 * {@code ApiExceptionHandler} macht daraus dieselbe 400-Antwort wie bei
 * {@code MethodArgumentNotValidException} — {@code type urn:pushnpull:problem:validation} mit
 * {@code errors[{field, message}]}.
 *
 * <p>{@code field} ist der Pfad, den die Oberflaeche am Formular markiert, genau wie im Vertrag:
 * {@code shortName}, {@code subjects[1].weeklyHours}, {@code slots[3].endTime}.</p>
 *
 * <p>Additiv ergaenzt von der Bauphase backend1 (Modul masterdata); die Module {@code rules},
 * {@code planning} und {@code substitution} brauchen dieselbe Antwortform.</p>
 */
public class ValidationException extends RuntimeException {

    /** Ein Feld des Formulars und der deutsche Text daneben. */
    public record FieldError(String field, String message) {
    }

    private final List<FieldError> errors;

    public ValidationException(List<FieldError> errors) {
        super("Eingabe unvollstaendig");
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public List<FieldError> errors() {
        return errors;
    }
}
