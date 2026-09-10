package de.wsp3_1.changelog;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Architekturregel des Moduls {@code changelog}: <b>der Verlauf loescht nichts</b> (T-W7.1,
 * VERTRAG Abschnitt 4, ArchUnit-Zusatz).
 *
 * <p>Warum eine eigene Regel? {@code ChangelogEntryRepository} erbt von {@code JpaRepository} und hat
 * damit {@code deleteById}, {@code deleteAll} und {@code delete} — die Methoden sind da, ob wir sie
 * wollen oder nicht. Diese Regel haelt fest, dass niemand im Modul sie aufruft. Die vier allgemeinen
 * Regeln (Modulgrenzen, Zyklen, Schichten) stehen in {@code de.wsp3_1.ArchitectureTest}; diese hier
 * gilt nur fuer dieses Modul und liegt deshalb daneben.</p>
 */
class ChangelogArchitectureTest {

    private static final String MODULE = "de.wsp3_1.changelog";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(MODULE);

    @Test
    @DisplayName("T-W7.1: keine Klasse des Moduls ruft eine loeschende Methode auf")
    void noOneInChangelogCallsDelete() {
        noClasses().that().resideInAPackage(MODULE + "..")
                .should().callMethodWhere(describe("Name faengt mit „delete\" an",
                        (JavaMethodCall call) -> call.getTarget().getName().startsWith("delete")))
                .because("ein Verlaufseintrag wird geschrieben und gelesen, nie geloescht (T-W7.1); "
                        + "die Loeschmethoden erbt das Repository nur von JpaRepository")
                .check(classes);
    }

    @Test
    @DisplayName("T-W7.1: das Repository erklaert selbst keine loeschende oder aendernde Abfrage")
    void repositoryDeclaresNothingThatChangesAnEntry() {
        for (Method method : de.wsp3_1.changelog.persistence.ChangelogEntryRepository.class
                .getDeclaredMethods()) {
            assertThat(method.getName())
                    .as("%s darf im Verlaufs-Repository nicht stehen", method.getName())
                    .doesNotStartWith("delete")
                    .doesNotStartWith("remove")
                    .doesNotStartWith("update");
        }
    }
}
