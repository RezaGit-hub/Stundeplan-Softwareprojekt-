package de.wsp3_1.shared.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/**
 * Baut die ProblemDetail-Antworten nach RFC 9457. Ein Ort fuer {@code type}-Praefix und Titel,
 * damit Advice, Einstiegspunkt (401) und Zugriffsverweigerung (403) dasselbe Format liefern.
 */
public final class ApiProblems {

    /** Praefix aller Fehler-Typen; der Slug dahinter steht in api.md. */
    public static final String TYPE_PREFIX = "urn:pushnpull:problem:";

    private ApiProblems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }

    /** Token fehlt, ist unbekannt oder abgelaufen — der Client schickt den Nutzer zur Anmeldung. */
    public static ProblemDetail unauthenticated() {
        return of(HttpStatus.UNAUTHORIZED, "unauthenticated", "Nicht angemeldet",
                "Bitte erneut anmelden: das Token fehlt oder ist abgelaufen.");
    }

    /**
     * Anmeldung mit falschem Benutzernamen oder Passwort. Gleicher Slug und Status wie
     * {@link #unauthenticated()} (der Vertrag kennt nur eine 401-Antwort), aber ein Text, der im
     * Anmeldedialog stimmt: „Token abgelaufen" waere dort irrefuehrend. Kein Hinweis darauf, welcher
     * der beiden Werte falsch war.
     */
    public static ProblemDetail badCredentials() {
        return of(HttpStatus.UNAUTHORIZED, "unauthenticated", "Nicht angemeldet",
                "Benutzername oder Passwort ist falsch.");
    }

    public static ProblemDetail forbidden() {
        return of(HttpStatus.FORBIDDEN, "forbidden", "Kein Zugriff",
                "Diese Funktion ist der Planung vorbehalten.");
    }
}
