package de.wsp3_1.shared.api;

import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Ein Ort fuer alle Fehlerantworten (api.md, Tabelle „Fehlerformat"). Alles kommt als
 * {@code application/problem+json} mit {@code type = urn:pushnpull:problem:<slug>} zurueck; Texte deutsch.
 * Konflikte und Warnungen der Fachlogik sind Daten in 200-Antworten und laufen NICHT hier durch.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Die Bedingung aus V4, die je Stunde und Kalendertag hoechstens eine Ausnahme zulaesst. Sie steht
     * hier als Zeichenkette und nicht als Verweis auf {@code substitution}: {@code shared} kennt kein
     * Modul (Modultabelle im VERTRAG). Den Teilindex {@code timetable_one_draft} gab es bis V10 auf
     * dieselbe Weise (409 {@code draft-exists}); seit #103 (V11) duerfen mehrere Entwuerfe nebeneinander
     * stehen, und der Fall ist weg.
     */
    private static final String ONE_EXCEPTION_INDEX = "lesson_exception_unique";

    /** Bean-Validation an einem Request-Body: 400 mit Feldliste. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "ist ungueltig" : error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, "validation", "Eingabe unvollstaendig",
                "Bitte die markierten Felder pruefen.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Eingabefehler, den die Bean-Validation nicht sehen kann (vergebenes Kuerzel, doppelte Zeile,
     * Endzeit vor Beginn, „nur Maximalstunden-Regeln gibt es je Jahrgang"). Gleiche Antwortform wie
     * {@link #onValidation}, damit die Oberflaeche nur einen Fall kennt.
     *
     * <p>Additiv ergaenzt in Bauphase backend1. Die Module {@code masterdata}, {@code auth} und
     * {@code rules} haben am 05.09. jeweils einen eigenen Handler dafuer angelegt; drei Methoden fuer
     * dieselbe Ausnahmeklasse laesst Spring nicht zu („Ambiguous @ExceptionHandler"), deshalb steht
     * hier genau eine.</p>
     */
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail onCustomValidation(ValidationException ex) {
        List<Map<String, String>> errors = ex.errors().stream()
                .map(error -> Map.of("field", error.field(), "message", error.message()))
                .toList();
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, "validation", "Eingabe unvollstaendig",
                "Bitte die markierten Felder pruefen.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Unlesbares oder fehlendes JSON: ebenfalls 400 validation, aber ohne Feldliste. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadableBody(HttpMessageNotReadableException ex) {
        return ApiProblems.of(HttpStatus.BAD_REQUEST, "validation", "Anfrage nicht lesbar",
                "Der Inhalt der Anfrage konnte nicht gelesen werden.");
    }

    /**
     * Ein Pfad- oder Abfragewert im falschen Format: {@code /api/teachers/abc}, wo eine Nummer stehen
     * muss. Das ist ein Fehler der Anfrage — ohne diesen Fall liefe er in den Auffangzweig und ergaebe
     * 500 statt 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, "validation", "Eingabe unvollstaendig",
                "Bitte die markierten Felder pruefen.");
        problem.setProperty("errors",
                List.of(Map.of("field", ex.getName(), "message", "muss eine Zahl sein")));
        return problem;
    }

    /**
     * Ein Pflicht-Abfrageparameter fehlt: {@code GET /api/substitutions/options?lessonId=1} ohne
     * {@code date}. Ohne diesen Fall liefe er in den Auffangzweig und ergaebe 500 statt 400 — mit einer
     * Meldung, die den fehlenden Namen gar nicht nennt.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail onMissingParameter(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ApiProblems.of(HttpStatus.BAD_REQUEST, "validation", "Eingabe unvollstaendig",
                "Bitte die markierten Felder pruefen.");
        problem.setProperty("errors",
                List.of(Map.of("field", ex.getParameterName(), "message", "darf nicht leer sein")));
        return problem;
    }

    /** Falsches HTTP-Verb auf einem bekannten Pfad: 405 im selben Antwortformat, nicht 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail onMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ApiProblems.of(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Nicht erlaubt",
                "Diese Adresse beantwortet " + ex.getMethod() + " nicht.");
    }

    /** Falscher oder fehlender Inhaltstyp (kein {@code application/json}): 415 statt 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail onMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ApiProblems.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Falsches Format",
                "Diese Adresse erwartet application/json.");
    }

    /**
     * Eine Bedingung der Datenbank ist verletzt — der Fall, den die Pruefung im Dienst nicht abfangen
     * konnte. Zwei Gruende kommen vor:
     *
     * <ul>
     *   <li><b>Doppelter Schluessel</b> (SQLState 23505): zwei gleichzeitige Anfragen haben beide die
     *       Existenzpruefung bestanden und legen dieselbe Zeile an. Die Pruefung im Dienst bleibt —
     *       sie liefert die bessere Meldung; dieser Fall faengt nur das Wettrennen ab.</li>
     *   <li><b>Fremdschluessel</b> (23503): etwas zeigt noch auf die Zeile, die geloescht werden soll.
     *       Beispiel: ein Raum, der nur noch in einem archivierten Plan vorkommt — die Zaehlabfragen in
     *       {@code ClassSubjectRepository} lassen archivierte Plaene bewusst aus, die Fremdschluessel
     *       bleiben aber bestehen.</li>
     * </ul>
     *
     * <p>Beides ist ein Konflikt (409), kein Serverfehler. Ohne diesen Fall antwortete beides mit 500.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onDataIntegrity(DataIntegrityViolationException ex) {
        if ("23505".equals(sqlState(ex))) {
            // Ein Wettrennen bei zwei gleichzeitigen POST /api/exceptions auf dieselbe Stunde und
            // denselben Tag: der Vertrag nennt dafuer `exception-exists`, nicht `data-exists`.
            if (mentions(ex, ONE_EXCEPTION_INDEX)) {
                return ApiProblems.of(HttpStatus.CONFLICT, "exception-exists", "Konflikt",
                        "Für diese Stunde gibt es an diesem Tag schon eine Ausnahme."
                                + " Erst aufheben, dann neu anlegen.");
            }
            return ApiProblems.of(HttpStatus.CONFLICT, "data-exists", "Konflikt",
                    "Diesen Eintrag gibt es bereits.");
        }
        return ApiProblems.of(HttpStatus.CONFLICT, "in-use", "Konflikt",
                "Dieser Eintrag wird noch verwendet und kann nicht entfernt werden.");
    }

    /** Der Fehlerschluessel der Datenbank aus der Ursachenkette; {@code null}, wenn keiner dabei ist. */
    private static String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /** Steht der Name des Index irgendwo in der Ursachenkette? PostgreSQL nennt ihn in der Meldung. */
    private static boolean mentions(Throwable ex, String indexName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(indexName)) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex) {
        return ApiProblems.of(HttpStatus.NOT_FOUND, "not-found", "Nicht gefunden", ex.getMessage());
    }

    /**
     * Ein Pfad, den es (noch) nicht gibt. Ohne diesen Fall liefe eine unbekannte Adresse in den
     * Auffangzweig und ergaebe 500 statt 404 — waehrend des Projekts ist das der Normalfall, solange
     * eine Rolle ihren Controller noch nicht gebaut hat.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail onUnknownPath(NoResourceFoundException ex) {
        return ApiProblems.of(HttpStatus.NOT_FOUND, "not-found", "Nicht gefunden",
                "Diesen Pfad gibt es nicht: " + ex.getResourcePath());
    }

    /** Fachlicher Konflikt: der Slug steht in der Ausnahme, Zusatzfelder kommen unveraendert mit. */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail onConflict(ConflictException ex) {
        ProblemDetail problem = ApiProblems.of(HttpStatus.CONFLICT, ex.slug(), "Konflikt", ex.getMessage());
        ex.extras().forEach(problem::setProperty);
        return problem;
    }

    /** Falsche Rolle an einer mit {@code @PreAuthorize} geschuetzten Methode. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return ApiProblems.forbidden();
    }

    /**
     * Anmeldung mit falschem Benutzernamen oder Passwort ({@code AuthService.login}). Eigener Fall,
     * weil das Frontend {@code detail} im Anmeldedialog anzeigt und „das Token fehlt oder ist
     * abgelaufen" dort falsch waere. Steht vor {@link #onAuthentication}: Spring waehlt den Handler
     * mit der genaueren Ausnahmeklasse.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail onBadCredentials(BadCredentialsException ex) {
        return ApiProblems.badCredentials();
    }

    /** Jede andere fehlgeschlagene Authentifizierung: fehlendes, unbekanntes oder abgelaufenes Token. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail onAuthentication(AuthenticationException ex) {
        return ApiProblems.unauthenticated();
    }

    /** Alles Uebrige: 500 mit deutschem Text; die Ursache steht nur im Log, nie in der Antwort. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unerwarteter Fehler", ex);
        return ApiProblems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Unerwarteter Fehler",
                "Es ist ein unerwarteter Fehler aufgetreten. Bitte die Serverprotokolle pruefen.");
    }
}
