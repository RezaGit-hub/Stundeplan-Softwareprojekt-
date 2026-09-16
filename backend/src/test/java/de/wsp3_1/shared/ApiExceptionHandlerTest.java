package de.wsp3_1.shared;

import de.wsp3_1.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der Zweig fuer verletzte Datenbankbedingungen. Er greift nur, wenn zwei Anfragen gleichzeitig
 * dieselbe Zeile anlegen oder eine Zeile loeschen wollen, auf die noch etwas zeigt — ein Wettrennen,
 * das sich mit {@code curl} nicht verlaesslich ausloesen laesst. Deshalb hier ohne Spring und ohne
 * Datenbank: geprueft wird, welchen Status und welchen Slug der Handler aus dem Fehlerschluessel macht.
 *
 * <p>Die Schluessel sind die von PostgreSQL: {@code 23505} doppelter Schluessel, {@code 23503}
 * verletzter Fremdschluessel. Vor diesem Handler wurden beide zu 500.</p>
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    /** Wie Spring die Ausnahme reicht: der SQLState steckt tief in der Ursachenkette. */
    private static DataIntegrityViolationException mitSqlState(String sqlState) {
        SQLException datenbank = new SQLException("Bedingung verletzt", sqlState);
        return new DataIntegrityViolationException("Schreiben fehlgeschlagen",
                new RuntimeException("Hibernate-Huelle", datenbank));
    }

    @Test
    @DisplayName("Ein doppelter Schluessel (23505) ergibt 409 data-exists")
    void duplicateKeyIsConflictDataExists() {
        ProblemDetail problem = handler.onDataIntegrity(mitSqlState("23505"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).hasToString("urn:pushnpull:problem:data-exists");
    }

    /**
     * Die Ausnahme eines Kalendertags hat einen eigenen Slug: zwei gleichzeitige {@code POST /api/exceptions}
     * auf dieselbe Stunde und denselben Tag laufen in {@code lesson_exception_unique}. Der Vertrag nennt
     * dafuer {@code exception-exists}; ohne diesen Fall bekaeme das Web {@code data-exists}. (Den
     * gleichen Fall fuer den einen Entwurf, {@code timetable_one_draft} → {@code draft-exists}, gab es
     * bis V10; seit #103 duerfen mehrere Entwuerfe nebeneinander stehen.)
     */
    @Test
    @DisplayName("Die Bedingung der Ausnahme ergibt 409 exception-exists, nicht data-exists")
    void duplicateExceptionIsConflictExceptionExists() {
        SQLException datenbank = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"lesson_exception_unique\"", "23505");
        ProblemDetail problem = handler.onDataIntegrity(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("Hibernate-Huelle", datenbank)));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).hasToString("urn:pushnpull:problem:exception-exists");
        assertThat(problem.getDetail()).contains("Erst aufheben, dann neu anlegen.");
    }

    @Test
    @DisplayName("Ein verletzter Fremdschluessel (23503) ergibt 409 in-use")
    void foreignKeyIsConflictInUse() {
        ProblemDetail problem = handler.onDataIntegrity(mitSqlState("23503"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).hasToString("urn:pushnpull:problem:in-use");
    }

    /**
     * Ohne erkennbaren Schluessel bleibt es beim Konflikt und wird nicht zum Serverfehler: der
     * Vorgang ist an einer Bedingung der Daten gescheitert, nicht an einem Fehler des Servers.
     */
    @Test
    @DisplayName("Ohne SQLState bleibt es bei 409 in-use")
    void unknownSqlStateStaysConflict() {
        ProblemDetail problem = handler.onDataIntegrity(
                new DataIntegrityViolationException("ohne Ursache"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).hasToString("urn:pushnpull:problem:in-use");
    }
}
