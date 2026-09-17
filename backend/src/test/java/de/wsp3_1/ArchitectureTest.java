package de.wsp3_1;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Die Architekturregeln aus {@code backbone/architektur.md}. Sie laufen in {@code ./gradlew test}
 * bei jedem Push und halten die Modulgrenzen ohne Absprache ein.
 *
 * <p>Das Team-Beispiel unter {@code de.wsp3_1.example} ist bewusst ausgenommen: es ist Wegwerfcode
 * aus dem Vite-Geruest und verschwindet vor der Liveschaltung.</p>
 */
class ArchitectureTest {

    private static final String ROOT = "de.wsp3_1";

    /**
     * Welches Modul welches benutzen darf (Tabelle in architektur.md). Module ohne Eintrag rechts
     * duerfen kein anderes Modul benutzen. Die Tabelle steht auch fuer die heute noch leeren Module
     * hier, damit die Grenze schon gilt, bevor die erste Klasse darin liegt.
     */
    // Map.ofEntries statt Map.of: Map.of fasst hoechstens zehn Paare, und die Tabelle waechst noch
    // (ergaenzt 05.09. beim Modul sampledata, 15.09. beim Modul transfer).
    private static final Map<String, Set<String>> ALLOWED = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("masterdata", Set.of("shared")),
            // rules liest das eingestellte Bundesland aus config (POST /api/rules/reset, E-48) —
            // VERTRAG Abschnitt 4, Tabelle der erlaubten Modulabhaengigkeiten. Ergaenzt 05.09. (Modul rules).
            Map.entry("rules", Set.of("config", "shared")),
            // planning liest die Rechenzeit und das Bundesland aus config (SolveService → SystemConfigService)
            // — VERTRAG Abschnitt 4, Tabelle. Ergaenzt 05.09. (Pruefung backend1).
            Map.entry("planning", Set.of("masterdata", "rules", "config", "solver", "changelog", "shared")),
            Map.entry("solver", Set.of("shared")),
            // substitution und changelog verweisen per JPA auf UserAccount (change_event.created_by_id,
            // changelog_entry.user_id aus V4) und brauchen dafuer die Entity aus auth —
            // VERTRAG Abschnitt 4, Tabelle (D-3). Ergaenzt 05.09. (Pruefung backend1).
            // substitution liest zusaetzlich das eingestellte Bundesland aus config: POST /api/holidays/preset
            // uebernimmt die mitgelieferte Ferien- und Feiertagsvorlage dieses Landes (E-51) — dieselbe
            // Leserichtung wie rules -> config. VERTRAG Abschnitt 4, Tabelle. Ergaenzt 05.09. (E-51).
            Map.entry("substitution", Set.of("masterdata", "planning", "changelog", "auth", "config", "shared")),
            Map.entry("changelog", Set.of("auth", "shared")),
            Map.entry("auth", Set.of("masterdata", "shared")),
            Map.entry("config", Set.of("shared")),
            // sampledata fuellt fremde Tabellen und darf dafuer masterdata und planning benutzen
            // (VERTRAG Abschnitt 4). Die Grunddaten schreibt es mit JdbcTemplate; fuer den gefuellten,
            // angenommenen Beispielplan (E-46) ruft SampleDataLoader.loadFull() seit der Nacharbeit
            // backend2 planning.DraftService und planning.SolveService.
            Map.entry("sampledata", Set.of("masterdata", "planning", "shared")),
            // Neu am 15.09.: das Modul transfer liest fuer den Export die bestehenden Lese-Dienste der
            // Module masterdata, rules und substitution und ruft beim Import deren Anlege-Methoden
            // auf. config wird nicht gebraucht (die Systemkonfiguration ist bewusst nicht Teil des
            // Dokuments); auth, planning, changelog, solver und sampledata benutzt es nicht.
            Map.entry("transfer", Set.of("masterdata", "rules", "substitution", "shared")),
            Map.entry("shared", Set.of())));

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            // Team-Beispiel (Client, ClientController) gehoert keinem Modul und wird nicht geprueft.
            .withImportOption(location -> !location.contains("/de/wsp3_1/example/"))
            .importPackages(ROOT);

    @Test
    @DisplayName("Regel 1: das Solver-Modell kennt die Datenbank nicht")
    void solverHasNoPersistenceDependency() {
        noClasses().that().resideInAPackage("..solver..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "..persistence..", "org.springframework.data..")
                .because("das Solver-Modell arbeitet nur auf Records; der Uebersetzer zwischen "
                        + "Datenbank und Solver liegt im Modul planning")
                .check(classes);
    }

    @Test
    @DisplayName("Regel 2: Schnittstellenklassen geben keine Entities nach aussen")
    void apiDoesNotDependOnEntities() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("die REST-Schnittstelle spricht in DTOs; sonst wandert jede Schemaaenderung "
                        + "ungewollt in den Vertrag")
                .check(classes);
    }

    @Test
    @DisplayName("Regel 3: Fachlogik und Persistenz kennen weder Web noch Schnittstelle")
    void domainAndPersistenceDoNotDependOnApi() {
        // Ausnahme transfer: `ExportService` und `ImportService` setzen das Sicherungsdokument aus
        // den DTO-Records anderer Module zusammen (TransferDtos.MasterDataExport fuehrt
        // List<RuleDto> und List<HolidayDto>, MasterDataImport/MasterDataImportResult sind die
        // Ein-/Ausgabetypen des ImportService selbst). Der Vertrag sieht das Modul `transfer`
        // genau dafuer vor. Die Alternative — RuleDtos, SubstitutionDtos und TransferDtos in
        // ihre Modulwurzelpakete verschieben wie MasterdataDtos (Abweichung MD-1) — beruehrt
        // jeden Controller und jeden Test der drei Module und wird als Nacharbeit gefuehrt.
        // Die Regel bleibt inhaltlich gewahrt: beide Dienste brauchen keinen Webserver.
        noClasses().that().resideInAnyPackage("..domain..", "..persistence..")
                .and().resideOutsideOfPackage("..transfer.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "org.springframework.web..")
                .because("Fachlogik muss ohne laufenden Webserver testbar bleiben; das Modul transfer "
                        + "ist ausgenommen, weil es als Sicherungs-Modul die DTO-Formen der anderen "
                        + "Module buendelt")
                .check(classes);
    }

    @Test
    @DisplayName("Regel 4a: die Module sind zyklenfrei")
    void modulesAreFreeOfCycles() {
        slices().matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    @DisplayName("Regel 4b: jedes Modul benutzt nur die Module aus der Tabelle")
    void modulesOnlyUseAllowedModules() {
        for (Map.Entry<String, Set<String>> entry : ALLOWED.entrySet()) {
            String module = entry.getKey();
            Set<String> forbidden = new TreeSet<>(ALLOWED.keySet());
            forbidden.removeAll(entry.getValue());
            forbidden.remove(module);
            if (forbidden.isEmpty()) {
                continue;
            }
            String[] forbiddenPackages = forbidden.stream()
                    .map(other -> ROOT + "." + other + "..")
                    .toArray(String[]::new);
            ArchRule rule = noClasses().that().resideInAPackage(ROOT + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                    .because("laut Tabelle in architektur.md darf " + module + " nur "
                            + (entry.getValue().isEmpty() ? "kein anderes Modul" : entry.getValue()) + " benutzen")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    /**
     * Rohes SQL sieht ArchUnit nicht: {@code JdbcTemplate} und {@code nativeQuery = true} greifen an
     * jeder Modulgrenze vorbei, weil eine Tabelle keine Java-Abhaengigkeit ist. Regel 4b bliebe gruen,
     * obwohl ein Modul die Tabellen eines anderen liest. Diese Regel haelt die Liste der Stellen
     * fest, an denen das im Vertrag vereinbart ist (VERTRAG Abschnitt 4, „Wer fremde Tabellen liest") —
     * jede weitere faellt beim naechsten Lauf auf.
     */
    private static final Set<String> ALLOWED_RAW_SQL = Set.of(
            // sampledata leert und fuellt die Tabellen aller Module (D-2) — dafuer ist es da.
            ROOT + ".sampledata.domain.SampleDataLoader",
            // changelog joint user_account (auth) und school_class (masterdata) fuer die Liste (D-3).
            ROOT + ".changelog.domain.ChangelogService",
            // ImportService leert vor dem Einspielen die Grunddatentabellen (dieselbe Liste wie
            // SampleDataLoader). Reines JdbcTemplate; die Modultabelle sieht SQL nicht (Regel 5).
            ROOT + ".transfer.domain.ImportService",
            ROOT + ".shared.domain.DatabaseCleaner");

    @Test
    @DisplayName("Regel 5: rohes SQL nur an den im Vertrag genannten Stellen")
    void rawSqlOnlyWhereAgreed() {
        noClasses().that().resideInAPackage(ROOT + "..")
                .and(new DescribedPredicate<>("nicht im Vertrag als SQL-Stelle genannt") {
                    @Override
                    public boolean test(JavaClass candidate) {
                        return !ALLOWED_RAW_SQL.contains(candidate.getName());
                    }
                })
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "org.springframework.jdbc.core.JdbcTemplate")
                .because("wer fremde Tabellen liest, umgeht die Modultabelle; die vereinbarten "
                        + "Ausnahmen stehen in VERTRAG Abschnitt 4 und in ALLOWED_RAW_SQL")
                .check(classes);
    }
}