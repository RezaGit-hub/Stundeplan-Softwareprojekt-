> **Hinweis 05.09.:** Diese Datei ist die Kopie, die beim Anlegen des Vollausbaus mitgewandert ist. Sie beschreibt die
> **Startschablone**, nicht den Vollausbau, und ist an mehreren Stellen älter als das Original. Verbindlich ist
> `08-Build/starter/STARTSTAND.md` (dort steht auch der Nachtrag 0a mit den Audit-Korrekturen vom 05.09.); für den
> Vollausbau selbst gelten `ANLEITUNG.md` und `docs/VERTRAG.md`.

# STARTSTAND — Startschablone `web-app`, Branch `vorlage/start`

Stand: 04.09.2026 abends, Architekt (Fable). Dieses Dokument ist der Einstieg für alle sieben am Montag 08.09. Die
Bauaufträge B1–B5 (Abschnitt 9) sind gebaut, geprüft und die Prüfbefunde eingearbeitet. **Abschnitt 10 ist die
Beleg-Tabelle**: jeder Startbefehl aus Abschnitt 3 ist dort mit Befehl und gesehener Ausgabe belegt, und daneben steht,
was weiterhin ungeprüft ist (Runner, VM, Android). Was in Abschnitt 10 keinen Beleg hat, gilt als unbelegt.

Ort: `08-Build/starter/` = lokaler Klon von `04-Repo/web-app` (Commit `1552b63`, main), Branch `vorlage/start`,
**nichts committet, nichts gepusht**. Philipp prüft, committet und stellt den Merge Request; das Team entscheidet am Kickoff.

## 0. In drei Sätzen

1. Das Team-Repo `web-app` (Spring Boot 4.1.1 + Vite/React/Tailwind) bleibt die Basis; das Client-Beispiel wandert nach
   `backend/src/main/java/de/wsp3_1/example/` und geht nicht verloren.
2. Die Schablone ergänzt, was sonst jede Rolle einzeln bauen müsste: Datenbank-Schema (Flyway V1–V4; V5 kam am 05.09.
   in der Phase backend1 dazu), Anmeldung mit
   Rollen, Fehlerformat, ArchUnit, Timefold-Anschluss, API-Vertrag v0 mit Mock, das fertige Frontend gegen Attrappe,
   Compose-Stack, CI.
3. Fachlogik (Grunddaten-CRUD, Regelwerk, Planung, Solver-Lauf, Vertretung, Verlauf) ist **nicht** drin — das sind die
   Karten der Rollen 1–7. Der Vollausbau als Referenz entsteht danach unter `08-Build/plattform` (Abschnitt 4).

## 1. Was ist da — Ordnerbaum mit Zweck

```
web-app/  (Branch vorlage/start)
├── STARTSTAND.md                    dieses Dokument (wird Abschnitt 00 des Handbuchs)
├── README.md                        Kurzfassung: drei Befehle + Verweis hierher                       [B4]
├── .gitattributes                   * text=auto eol=lf (CI läuft auf Linux, Windows-Checkouts bleiben sauber)  [B4]
├── .gitignore                       .env, demo/, .agent-out/, *.local                                  [B4]
├── .dockerignore                    Build-Kontext des Web-Abbilds ist die Wurzel; hält .git, node_modules und *.local draußen  [B4]
├── .gitlab-ci.yml                   contract:lint · backend:test · backend:integration · frontend:build · image (main, manual) · deploy:vm (manual)  [B4]
├── scripts/                         jeder CI-Job auch lokal ohne Runner (Docker + Node)
│   ├── ci-contract-lint.sh          redocly lint                                                       [B2]
│   ├── ci-backend-test.sh           Wegwerf-Postgres + gradle test integrationTest im Container        [B1]
│   ├── ci-frontend-build.sh         npm ci, typecheck, lint, test, build                               [B3]
│   ├── ci-image.sh                  docker compose build (backend + web)                               [B4]
│   ├── ci-all.sh                    die vier oben nacheinander                                         [B4]
│   └── dev-backend.sh               gradle bootRun im Container gegen die Compose-DB (kein JDK nötig)  [B1]
├── api-contract/                    die eine Wahrheit der REST-API (Rolle 4)                           [B2]
│   ├── openapi.yaml                 v0: health, auth, me, users, teachers, subjects, rooms, classes, timegrid, rules, config, sample-data
│   ├── package.json                 npm run mock (Prism auf 1501) · npm run lint (Redocly)
│   └── README.md                    wie ändern, wie mocken, Freeze-Regel
├── backend/                         Spring Boot 4.1.1, Java 21, Gradle 9.7.1                          [B1]
│   ├── build.gradle · settings.gradle · gradlew · gradle/wrapper/
│   ├── Dockerfile                   multi-stage: gradle:9.7.1-jdk21 → eclipse-temurin:21-jre
│   └── src/main/java/de/wsp3_1/
│       ├── BackendApplication.java  (Team, unverändert)
│       ├── example/                 Client + ClientController (Team-Beispiel, nur Paket verschoben)
│       ├── shared/                  Weekday, ApiExceptionHandler + ApiProblems (RFC 9457 ProblemDetail), Ausnahmen NotFound/Conflict
│       ├── config/                  SystemConfig-Entity + SystemConfigService (domain), GET /api/health
│       ├── auth/                    UserAccount, AuthToken, Role; SecurityConfig, TokenAuthenticationFilter;
│       │                            POST /api/auth/login · DELETE /api/auth/token · GET /api/me; AdminBootstrap
│       └── solver/                  Minimalmodell SolverLesson/SolverTimetable, Constraints H1 roomConflict + M1 unassignedLesson
│       src/main/resources/
│       ├── application.properties · application-dev.properties · application-ci.properties · application-prod.properties
│       └── db/migration/V1__master_data_config_auth.sql · V2__rule_presets.sql · V3__planning.sql · V4__substitution_changelog.sql
│       src/test/java/de/wsp3_1/
│       ├── ArchitectureTest         die vier ArchUnit-Regeln in fünf Tests (Regel 4 zerfällt in Zyklen + Modultabelle)
│       ├── solver/TimetableConstraintProviderTest   ConstraintVerifier (unit)
│       ├── auth/SecurityConfigTest  @WebMvcTest: ohne Token 401, Lehrkraft auf Planungspfad 403, health ohne Token 200 (unit)
│       ├── shared/IntegrationTestBase + TestcontainersConfig   (Tag "integration", Postgres per Testcontainers, Profil ci: feste URL)
│       ├── ApplicationIT            Kontext startet, Flyway V1–V6 gelaufen (V6 seit 05.09.), 138 rule_preset-Zeilen
│       └── auth/AuthFlowIT          login → token → /api/me → logout → 401
├── frontend/                        Web-App aus 08-Build/web (React 19, Vite 8, Tailwind 4, shadcn/ui, TanStack Query, TypeScript)  [B3]
│   ├── package.json                 dev · dev:mock · dev:prism · typecheck · lint · test · build · build:mock · build:demo
│   ├── vite.config.ts               Proxy /api → VITE_PROXY_TARGET (Standard http://localhost:8080)
│   ├── .env.example · .env.mock · .env.prism · .env.demo
│   ├── README.md                    Konventionen, „Wie man eine Seite hinzufügt", Grenzen der Attrappe
│   ├── AGENT-NOTIZEN.md             Bauprotokoll der Attrappe (Belege, Abweichungen) — Lesestoff, keine Pflicht
│   ├── Dockerfile                   node:22-alpine build → nginx:alpine + deployment/nginx.conf (Kontext = Repo-Wurzel)  [B4]
│   └── src/                         api/ (types.ts = DTOs, client.ts, hooks.ts) · mock/ · app/ · routes/ · pages/ · components/ · lib/ · example/ (Team-JSX, nicht geroutet)
├── deployment/                                                                                        [B4]
│   ├── docker-compose.yml           name: stundenplan — db · backend · web (1500); mock seit 05.09. im dev-Zusatz
│   ├── docker-compose.dev.yml       Override: db auf 15432, backend auf 8080 veröffentlicht
│   ├── .env.example                 ADMIN_INITIAL_PASSWORD, DB_*, WEB_PORT, MOCK_PORT
│   ├── nginx.conf                   statisches Frontend + location /api/ → backend:8080
│   ├── backup.sh                    pg_dump aus dem db-Container nach ./backups/
│   └── README-VM.md                 Schritte für VM Gruppe 5 (SSH 1522, Ports 1500/1501) — beschrieben, nicht ausgeführt
└── android-starter/                 optional (B5): Compose-Gerüst, hier nicht kompilierbar → später Repo android-app
```

## 2. Versionen (Quelle: Build-Dateien; „verifiziert" = Ausgabe gesehen)

| Baustein | Version | Quelle / Beleg |
|---|---|---|
| Spring Boot | **4.1.1** | `backend/build.gradle` (Team); identisch mit Timefold-Quickstart `getting-started/spring-boot-integration` (pom.xml, Branch stable, per WebFetch verifiziert) |
| Spring Dependency Management | 1.1.7 | `backend/build.gradle` (Team) |
| Gradle | **9.7.1** | `backend/gradle/wrapper/gradle-wrapper.properties` (Team); Container-Image `gradle:9.7.1-jdk21` existiert (docker manifest, verifiziert) |
| Java | **21** (Toolchain) | E-44: Team-Datei sagt 25; VM hat OpenJDK 21, CI-Image temurin:21, Quickstart `release 21`. **B1 ändert 25 → 21.** |
| Timefold Solver | **2.6.0** | Quickstart pom: `version.ai.timefold.solver=2.6.0`, Artefakt `ai.timefold.solver:timefold-solver-spring-boot-starter` (B1 verifiziert). **Es gibt kein Artefakt `timefold-solver-test` mehr**: `ConstraintVerifier` liegt in 2.6.0 im Kern unter `ai.timefold.solver.core.api.score.stream.test` (B1 gemessen) |
| Boot-4-Starter-Namen | `spring-boot-starter-webmvc`, `-data-jpa`, `-security`, `-validation`, `-flyway`, Tests `-webmvc-test`, `-security-test`; Testcontainers `spring-boot-testcontainers` | Spring-Boot-4.0-Migrationsleitfaden + Referenz 4.1.1 (WebFetch, verifiziert). Boot 4 braucht `spring-boot-starter-flyway` ausdrücklich. |
| Flyway | **12.4.0** (`flyway-core` + `org.flywaydb:flyway-database-postgresql`) | Version aus der Boot-4.1.1-BOM, von B1 aufgelöst (`gradle dependencies --configuration runtimeClasspath`). Flyway ≥ 10 trennt Datenbankmodule — ohne die zweite Zeile: „Unsupported Database: PostgreSQL" |
| ArchUnit | **1.4.1** (`com.tngtech.archunit:archunit-junit5`, in `build.gradle` gepinnt) | von B1 aufgelöst und grün gelaufen (5 Regeltests) |
| Testcontainers | **2.0.5** — Artefakte heißen jetzt `org.testcontainers:testcontainers-junit-jupiter` und `…:testcontainers-postgresql` (nicht mehr `junit-jupiter`/`postgresql`) | `spring-boot-testcontainers` 4.1.1 zieht `testcontainers:2.0.5`; die alten 1.x-Namen lösen mit Boot 4 nicht mehr auf (B1 gemessen) |
| Jackson | **3.1.5**, Paket `tools.jackson.databind` (nicht `com.fasterxml.jackson`) | Spring Boot 4 liefert Jackson 3; der Import `com.fasterxml.jackson.databind` compiliert nicht mehr (B1 gemessen) |
| PostgreSQL | 16 (`postgres:16-alpine`) | Image lokal vorhanden (`docker images`, verifiziert) |
| Prism | **`@stoplight/prism-cli` 5.14.2** — sowohl `npm run mock` als auch der Compose-Dienst (`stoplight/prism:5.14.2`) | B2 pinnt 5.14.2, weil ab 5.15.7 `engines.node >= 24` steht und CI auf `node:22-alpine` läuft (npm-Warnung EBADENGINE gesehen). **Korrektur 04.09.:** Der bewegliche Tag `stoplight/prism:5` (= 5.15.10) ist im Container **nicht** gleichwertig — er startet dort in Endlosschleife neu (`TypeError: Cannot read properties of undefined (reading 'isPrimary')`, Port 1501 antwortet nie; gemessen). Die frühere Aussage „verhält sich gleich" stammte von `npx`, nicht aus dem Container. Der Dienst `mock` fährt darum 5.14.2 mit `-m false` (ein Prozess) und `platform: linux/amd64` — das Bild gibt es nur für amd64, auf Apple Silicon läuft es unter Rosetta |
| Redocly CLI | **`@redocly/cli` 2.51.1** | von B2 gepinnt; `npm run lint` meldet 0 Fehler und 1 Warnung (`operation-4xx-response` an `GET /api/health`) |
| Node / npm | Node ≥ 20.19 oder ≥ 22.12; gemessen 20.20.1 / 10.8.2; CI `node:22-alpine` | `frontend/README.md`; Image-Tag verifiziert |
| Vite / React / Tailwind / react-router | 8.2.x / 19.2.x / 4.3.x / 7.18.x | `frontend/package.json` (gleiche Major wie Team-Gerüst) |
| TanStack Query / shadcn / TypeScript / Vitest | 5.102.x / 4.20.1 / 6.0.x / 5.0.0 | `frontend/package.json` |
| nginx | `nginx:alpine` | Image-Tag verifiziert |

Lokal auf diesem Mac gibt es **kein JDK** (`java -version` → „Unable to locate a Java Runtime"), kein Gradle, kein Android-SDK.
Alles Java läuft im Container; Node 20.20.1 ist da; Docker 29.1.2 mit Compose v2.40 läuft.

## 3. Starten — drei Befehle (und ein vierter für „alles in einem Vorgang")

Vorbedingung einmalig: Docker läuft; Node ≥ 20.19. Alle Befehle aus der Repo-Wurzel.

### 3.1 Backend mit Postgres (Container, kein JDK nötig)

```bash
(cd deployment && cp -n .env.example .env && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db)
scripts/dev-backend.sh          # = gradle bootRun in gradle:9.7.1-jdk21, Port 8080, DB db:5432 im Compose-Netz
```
Läuft, wenn: Log zeigt `Flyway ... Successfully applied 6 migrations` (V6 seit 05.09.) und `Started BackendApplication`;
`curl -s localhost:8080/api/health` → `{"status":"UP","schoolName":"Gymnasium Musterstadt"}`;
`curl -s -X POST localhost:8080/api/auth/login -H 'content-type: application/json' -d '{"username":"admin","password":"admin"}'` → `{"token":"…","role":"PLANNING",…}`
(Profil `dev`: Admin-Passwort `admin`; Profil `prod` verlangt `ADMIN_INITIAL_PASSWORD`).
Mit eigenem JDK 21: `cd backend && ./gradlew bootRun` — Standard-URL `jdbc:postgresql://localhost:15432/timetable` zeigt auf die Compose-DB.

**Wenn 8080 schon belegt ist.** Auf macOS hört dort oft der mitgelieferte Apache: `curl -s -i localhost:8080/` zeigt dann
`Server: Apache/2.4.66`, und `docker run -p 8080:8080` meldet `bind: address already in use`. Zwei Wege, beide am 04.09.
gemessen: `sudo apachectl stop` — oder auf einen freien Port ausweichen, dann muss der Vite-Proxy mitziehen:
```bash
BACKEND_PORT=8089 scripts/dev-backend.sh
(cd frontend && VITE_PROXY_TARGET=http://localhost:8089 npm run dev)
```
Ohne das eine oder das andere antwortet der Vite-Proxy mit Apaches HTML-404 statt mit dem Backend — ohne jeden Hinweis.
`BACKEND_PORT` versteht auch der dev-Override (`docker-compose.dev.yml`). Wer in `deployment/.env` andere Datenbankwerte
setzt, muss nichts weiter tun: `scripts/dev-backend.sh` liest dieselbe Datei.

### 3.2 Mock-API (Prism aus dem Vertrag)

```bash
(cd api-contract && npm ci && npm run mock)     # Prism auf http://localhost:1501
```
Läuft, wenn: `curl -s localhost:1501/api/health` liefert das `example` aus `openapi.yaml`; Prism-Log zeigt je Anfrage eine Zeile.
Alternative ohne Node: `cd deployment && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mock`
(gleicher Port 1501 — **nie beides gleichzeitig**). Seit dem 05.09. steht der Dienst im dev-Zusatz, nicht mehr im
Produktions-Compose; ohne die zweite `-f`-Datei meldet Docker `no such service: mock`.

**Bearer-Header nicht vergessen.** Prism setzt das `security` des Vertrags durch, prüft aber nur, *ob* ein Bearer-Header da
ist — jedes Wort genügt, geprüft wird nichts. Ohne Header antwortet jeder geschützte Pfad mit 401:
```bash
curl -s localhost:1501/api/teachers
# {"type":"urn:pushnpull:problem:unauthenticated",…,"status":401}
curl -s -H 'Authorization: Bearer x' localhost:1501/api/teachers | head -c 120
# [{"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,…
```
`/api/health` und `POST /api/auth/login` gehen ohne Header. Mehr dazu in `api-contract/README.md`.

### 3.3 Frontend

```bash
cd frontend && npm ci
npm run dev:mock     # eingebaute Attrappe, kein Backend nötig — http://localhost:5173, Konto admin/admin
npm run dev          # echter Client: /api → Vite-Proxy → http://localhost:8080 (Backend aus 3.1 muss laufen)
npm run dev:prism    # echter Client: /api → Vite-Proxy → http://localhost:1501 (Prism aus 3.2)
```
Läuft, wenn: `http://localhost:5173/login` zeigt die Anmeldeseite mit Schulname (aus `/api/health`); Anmeldung als `admin`
führt zur Übersicht. Gegen das echte Backend (3.1) funktionieren **Anmeldung, Abmelden und `/api/me`** — mehr Endpunkte gibt
es dort noch nicht. Alles andere zeigt den `ErrorState` mit dem 404 des Backends, und dazu gehört auch der Passwort-Dialog:
`PUT /api/me/password` steht im Vertrag und im Client, der Controller dazu ist Karte **T-405** (Rolle 4). Das ist erwartet;
die Seiten füllen sich, sobald die Rollen ihre Controller liefern. Gegen die Attrappe (`dev:mock`) ist alles vollständig.

### 3.4 Alles in einem Vorgang (so läuft es auch auf der VM)

```bash
cd deployment && cp -n .env.example .env && docker compose up -d --build && docker compose ps
```
Läuft, wenn: `http://localhost:1500` zeigt die Anmeldeseite (nginx), `curl -s localhost:1500/api/health` antwortet über
den Proxy aus dem Backend-Container, `curl -s localhost:1501/api/health` aus Prism. Erster Build dauert Minuten (Gradle-
und npm-Download im Container).

**Das Konto `admin` entsteht genau einmal.** `AdminBootstrap` legt es nur an, solange die Tabelle `user_account` leer ist.
Wer vorher 3.1 gefahren ist (Profil `dev`, Passwort `admin`), meldet sich auch hier weiter mit **`admin`/`admin`** an —
`ADMIN_INITIAL_PASSWORD=bitte-aendern` aus `.env` bleibt wirkungslos und ergibt 401 (gemessen). Wer den Wert wirklich
prüfen will, fängt mit leerer Datenbank an:
```bash
cd deployment && docker compose down -v && docker compose up -d --build
```
`down -v` löscht das Volume `stundenplan_pgdata` samt aller Testdaten. Dasselbe braucht, wer eine Datenbank aus einem
älteren Stand der Schablone hat: `V1` hat sich am 04.09. geändert (Wochentage, siehe Abschnitt 8), und Flyway lehnt eine
bereits migrierte Datenbank mit geänderter Prüfsumme ab.

### 3.5 Prüfen wie die Pipeline (ohne Runner)

```bash
scripts/ci-all.sh    # = ci-contract-lint.sh, ci-backend-test.sh, ci-frontend-build.sh, ci-image.sh
```
Jedes Skript führt dieselben Befehle aus wie der gleichnamige Job in `.gitlab-ci.yml` (Tabelle in Abschnitt 8) — die Java-
Teile im Container statt im Runner-Image. Grün hier heißt deshalb: die Befehle stimmen und der Code ist grün. Ob der
CAU-Runner sie auch grün bekommt, ist damit **nicht** gezeigt: Runner-Image, Netzzugang und Docker-in-Docker sind ungeprüft
(Tag-1-Prüfpunkt T-703, Rolle 7). Erste Pipeline am Montag ist der Beweis, nicht dieses Skript.

## 4. Was bewusst fehlt — und wo die Referenz liegt

Nicht in der Schablone (das sind die Karten, Abschnitt 5):

| Bereich | fehlt | Karte |
|---|---|---|
| Grunddaten | Entities/Repositories/Controller für Teacher, Subject, Room, SchoolClass, ClassSubject, Timeslot; Warnungen; Löschen-vs-Deaktivieren | T-305, T-306, T-404, T-207 |
| Regelwerk | `Rule`-Entity, `GET /api/rules`, `PUT`, `POST /api/rules/reset` (kopiert `rule_preset` des eingestellten Bundeslands), Jahrgangszeilen | T-309 (Rest), T-204 |
| Konfiguration | `GET/PUT /api/config`, `SampleDataLoader` SMALL/FULL, `POST /api/sample-data` | T-105, T-106 |
| Konten | `GET/POST /api/users`, Passwort setzen/ändern, Token-Aufräumen (`@Scheduled`) | T-405 |
| Planung | Timetable/Lesson-Entities, Entwurf-Endpunkte, `ConflictChecker`, `SolverTranslator`, `SolveService`, Annehmen | T-203, T-205, T-209, T-307, T-406 |
| Solver | H2–H5, R1–R3 (Zwillinge hart/weich), S1–S2, `RuleFact`, `BlockedTimeFact`, Betrieb über `SolverManager` | T-202, T-204, T-206 |
| Vertretung/Verlauf | ChangeEvent, LessonException, ChangelogEntry (Tabellen sind da, Java fehlt), `WeekResolver`, Endpunkte | T-208, T-307, T-308, T-407 |
| Vertrag | v1 (timetable, week, me/timetable/week, draft, events, substitutions, exceptions, changelog, holidays) | T-403 |
| Ferien | `V5__holiday.sql`, `/api/holidays` (Kann) | T-110 |
| Frontend | nichts — alle 19 Routen sind gegen die Attrappe fertig. Offen: Abgleich mit dem echten Backend, Druckansicht (Soll), Komponententests | T-502, T-509, T-510 |
| Android | eigenes Repo `android-app`; Gerüst optional unter `android-starter/` — `TimetableApi` kennt bewusst nur `GET api/health`, die in `SPEZIFIKATION.md` 9.3 aufgeführten `POST api/auth/login`, `DELETE api/auth/token`, `GET api/me`, `GET api/me/timetable/week`, `GET api/timegrid` und die zugehörigen DTOs fehlen (namentlich im Javadoc von `TimetableApi.kt` und `Dtos.kt`, Abschnitt 9) | T-601 ff. |
| Betrieb | VM einrichten, Deploy-Key, Board, Wiki | T-701, T-702, T-705, T-708 |

**Referenz „Vollausbau":** entsteht als Klon dieser Schablone unter `08-Build/plattform` (Branch `vollausbau`) mit allen Modulen,
Timefold-Solver, echter Frontend-Anbindung, CI mit Deploy-Stufe und Abnahmedurchlauf. Er ist Nachschlagewerk und Sicherheitsnetz,
nicht die Abgabe: Das Team baut auf der Schablone, schaut in den Vollausbau, wenn es hakt, und übernimmt bewusst (mit Autorenangabe
im Einzelvortrag). Pfad und Stand werden hier eingetragen, sobald er existiert.

## 5. Erste Karte je Rolle (Mo 08.09., nach dem Kickoff 10:30)

Rollen laut `backbone/rollen_aufgaben.md` (Nachtrag 04.09.). Jede Karte: eine Person, höchstens ein Tag, Anforderungs-ID auf der Karte.

| Rolle | Erste Karte | Wo anfangen (Datei/Ordner) | Fertig, wenn |
|---|---|---|---|
| 1 Regelwerk & Konfliktprüfung (Johannes) | **T-105** `GET/PUT /api/config` | Muster: `backend/src/main/java/de/wsp3_1/config/api/HealthController.java` + `config/persistence/SystemConfig.java`; neu `config/api/ConfigController.java`, `config/api/ConfigDto.java`; DTO-Form: `frontend/src/api/types.ts` → `ConfigDto`; Vertrag: `api-contract/openapi.yaml` `/config` | Integrationstest `ConfigControllerIT`: PUT `maxSolveSeconds=30` überlebt Kontext-Neustart; `scripts/ci-backend-test.sh` grün. Danach T-203 `ConflictChecker` in `planning/domain/` (reine Records, kein Spring) |
| 2 Planungslogik & Solver (Basam) | **T-202** H2–H5 + Tests | `backend/src/main/java/de/wsp3_1/solver/TimetableConstraintProvider.java` (H1, M1 sind da), `src/test/…/solver/TimetableConstraintProviderTest.java` (ConstraintVerifier-Muster ist da); Skizzen: `backbone/solver.md` | `gradle test` grün mit Positiv-/Negativfall je Constraint; Lernnotiz zu `SolverConfigOverride`/`withConfigOverride` in Timefold 2.6.0 (T-201) |
| 3 Datenmodell & Persistenz (Philipp) | **T-305** Entities + Repositories Grunddaten | `backend/src/main/resources/db/migration/V1__master_data_config_auth.sql` ist die Vorgabe; neu `masterdata/persistence/{Teacher,Subject,Room,SchoolClass,ClassSubject,Timeslot}.java` + Repositories. `spring.jpa.hibernate.ddl-auto=validate` beweist, dass Entity und Tabelle zusammenpassen | `ApplicationIT` startet weiter (validate ohne Fehler); T-W1.1-Repository-Test grün; ER-Diagramm (T-301) aus denselben Tabellen |
| 4 API & Sicherheit (Anthony) | **T-403** Vertrag v1 | `api-contract/openapi.yaml` (v0 mit Muster je Ressource); DTO-Form 1:1 aus `frontend/src/api/types.ts`, Beispielwerte aus `frontend/src/mock/data-small.ts` (Leitszenario 21.09.); `npm run lint`, `npm run mock` | Prism liefert `/timetable/week?date=2026-09-21&view=CLASS&id=1` mit allen sieben `kind`-Werten; Lint 0 Fehler; Tag `contract-v1` am Fr 12.09. Danach T-404 Controller Grunddaten (Muster: `auth/api/AuthController.java`, `shared/api/ApiExceptionHandler.java`) |
| 5 Web-Frontend (Reza) | **T-502** echter Client gegen das Backend | `frontend/src/api/client.ts`, `frontend/src/api/types.ts`; `npm run dev` gegen 3.1: Login, `/api/me`, 401-Redirect, ProblemDetail-Toast am echten Server prüfen; Abweichungen Backend↔Attrappe in `src/mock/**` nachziehen (Backend gewinnt) | Login gegen Backend, Abmelden, Sitzung abgelaufen → `/login`; Vitest weiter 27/27; Zeile in `frontend/README.md` „Stand gegen Backend". Danach T-509 Komponententests, T-510 Druckansicht; Pairing mit Rolle 6 (Di/Do 14–16) |
| 6 Android-App (Lasse) | **T-601** Projekt + Einstellungen + Health | Repo `android-app` anlegen (Betreuende/Maintainer); Ausgangspunkt `android-starter/` (falls gebaut) oder `08-Build/SPEZIFIKATION.md` Abschnitte 5 und 9 + `backbone/android.md`; Ziel des Verbindungstests: Prism `http://<Mac-IP>:1501` bzw. VM `http://134.245.1.240:1500` | T-A3.1 von Hand: falsche Adresse → Warnung, „trotzdem speichern", Neustart, Adresse noch da |
| 7 Betrieb, CI/CD & Qualität (Thomas) | **T-701/T-703** Repo-Regeln + Pipeline | `.gitlab-ci.yml`, `.gitattributes`, `scripts/ci-*.sh`; GitLab: `main`-Schutz (MR-Pflicht, Pipeline grün, 1 Approval), Board-Spalten (T-708); `docker info` im Runner als Lernnotiz | Pipeline grün auf dem ersten MR; direkter Push auf `main` abgelehnt. Danach T-702 VM nach `deployment/README-VM.md`, T-704 Compose auf der VM, Walking Skeleton bis Mi 10.09. (`backbone/pipeline.md`) |

Sofort startbar ohne Vorbedingung bleiben außerdem: T-103 (Glossar, Entscheidungsdateien), T-107 (Entwurfsdoku-Skelett), T-301, T-401 (v0 ist da → nur noch Codegen-Test), T-402 (Spike ist da → nur noch T-409 vorbereiten), T-408 (Advice ist da → Validierungsfälle ergänzen).

## 6. Regeln (Team-Beschluss O-3 am Kickoff; das hier ist der Vorschlag)

**Branches.** `feature/<issue-nr>-kurz`, Lebensdauer unter 24 h, `git pull --rebase` vor jedem Push, kein direkter Push auf `main`.
Wer nicht mergen kann, hat zu groß geschnitten: Karte teilen.

**Merge Requests.** Ein Reviewer (nicht der Pairing-Partner allein), Review-SLA ein Arbeitstag, Zielgröße 200–400 Zeilen, MR nennt
Anforderungs-ID und `Closes #n`. Pipeline muss grün sein („Pipelines must succeed"); rote Pipeline repariert oder revertet der
Verursacher innerhalb einer Stunde, niemand merged auf Rot. API-Änderung = Label `api-change`, Reviewer Rolle 4 + betroffene
Frontend-Rolle, Post im Mattermost.

**Vertrag zuerst.** `api-contract/openapi.yaml` ist die einzige Wahrheit der REST-API. Endpunkt bauen = (1) Vertrag im selben MR,
(2) `npm run lint` grün, (3) Prism liefert das Beispiel, (4) dann Controller. DTO-Form für Web und App stammt aus dem Vertrag;
`frontend/src/api/types.ts` ist seine TypeScript-Abschrift und wird bei jeder Änderung nachgezogen (Rolle 5 mit Rolle 4).
Ab **Freeze Fr 12.09.** nur additiv (neue Felder/Endpunkte; nie umbenennen oder entfernen) — das federt die adesso-Änderung
21.–23.09. ab. Fehler immer als RFC-9457-ProblemDetail (`shared/api/ApiExceptionHandler`); Konflikte und Warnungen sind Daten
in 200-Antworten, nie Fehler.

**Definition of Done (Vorschlag, Team formuliert selbst).**
1. Code in `main`, Pipeline grün (lokal vorher `scripts/ci-all.sh`).
2. MR von einer zweiten Person geprüft.
3. Tests für das, was fachlich zählt; Testname trägt die Testfall-ID (`tW3_1_roomDoubleBooked_…`).
4. Anforderungs-ID am Issue.
5. Entscheidung getroffen → `doku/entscheidungen/E-xx.md` im Gruppenprojekt am selben Tag („steht sie nur im Chat, existiert sie nicht").
6. Deployment betroffen → `deployment/README-VM.md` nachgezogen.
7. API betroffen → `openapi.yaml` im selben MR, Prism liefert das Beispiel.
8. Nichts behaupten, was nicht gemessen ist: „läuft" heißt „Ausgabe gesehen".

**Werkzeuge.** Backend ohne JDK: `scripts/dev-backend.sh`, `scripts/ci-backend-test.sh`. Löschen im Repo: `git rm`; auf diesem Mac sind
`rm`/`rmdir` per Hook gesperrt (weg = `mv` nach `~/.Trash`). UI-Texte deutsch in Schulsprache (Lehrkraft, Klasse, Raum, Stundenraster,
Regelwerk, Entwurf, aktiver Plan, festgelegt, Vertretung, Änderungsverlauf; „Planung", nicht „Admin"); Bezeichner in Code, API, DB
englisch wie im Vertrag. Keine externen Laufzeitdienste, keine URL im Build (`/api` relativ; Umzug = `.env` + `docker compose up -d`).

## 7. Abweichungen von Plan und Rückgrat — und warum

| # | Plan/Rückgrat sagt | Schablone macht | Grund |
|---|---|---|---|
| A1 | Paket `de.pushnpull.timetable.<modul>` | **`de.wsp3_1.<modul>`** (`shared`, `config`, `auth`, `masterdata`, `rules`, `planning`, `solver`, `substitution`, `changelog`, `example`) | Team-Gruppe in `build.gradle` ist `de.wsp3_1`; nichts umbenennen, was das Team angelegt hat. ArchUnit-Slices entsprechend `de.wsp3_1.(*)..`, `example` ausgenommen |
| A2 | Startschablone: nur `V1` (+ `V2` Presets) | **V1–V4** (alle Tabellen aus `datenmodell.md` außer `holiday`) | E-36 verlangt in W1 den durchgehenden Pfad bis zur Erzeugung → Rolle 2 braucht `timetable`/`lesson` ab Montag; Rolle 3 (Philipp) beginnt bei Entities statt DDL. `V5__holiday_valid_from_solve_state.sql` ist am 05.09. in der Phase backend1 nachgezogen (VERTRAG Abschnitt 6) |
| A3 | „Sicherheitsfilter-Gerüst mit einem Negativtest" | **Anmeldung komplett**: login, logout, `/api/me`, `AdminBootstrap`, Token-Persistenz | Ohne Token kann kein Client am Montag mit dem Backend sprechen; Walking-Skeleton-Punkt 3 (`backbone/pipeline.md`) wäre erst in W2 (T-405). `/api/users` und Passwortwechsel bleiben T-405 |
| A4 | E-45: `TokenAuthenticationFilter` in `auth.domain` | Filter in **`auth.api`** neben `SecurityConfig`; `AuthService` in `auth.domain` | Der Filter erbt von `org.springframework.web.filter.OncePerRequestFilter` — ArchUnit-Regel 3 verbietet `org.springframework.web..` in `domain`. E-45 zielt auf das Modul (kein Zyklus `shared → auth`), nicht auf die Schicht; das bleibt erfüllt |
| A5 | Timefold nur als Abhängigkeit | **Minimalmodell** `SolverLesson`/`SolverTimetable` + H1 `roomConflict` + M1 `unassignedLesson` + ConstraintVerifier-Test | Belegt, dass Boot 4.1.1 + Timefold 2.6.0 im Container kompiliert und startet (T-201-Spike vorweggenommen); ohne `@PlanningSolution`-Klasse wäre der Starter im Projekt unbewiesen. Kein `SolveService`, kein Endpunkt |
| A6 | Team-Gerüst „JSX ohne TypeScript"; E-43 „das JSX-Gerüst bleibt" | Frontend aus `08-Build/web` ist **TypeScript**; Team-JSX-Seiten liegen unverändert unter `frontend/src/example/` (nicht geroutet) | Home/ClientList/ClientEdit sind Wegwerfcode des Vite-Templates; Vite, Tailwind, react-router, ESLint-Basis und `src/index.css`-Ansatz bleiben. `allowJs` ist an: JSX-Dateien dürfen weiter existieren, neue Dateien sind `.tsx` (E-43) |
| A7 | Backbone `web.md`: Prism-Adresse über `VITE_API_BASE` | **keine `VITE_API_BASE`**; `/api` bleibt relativ, Ziel wählt der Vite-Proxy (`VITE_PROXY_TARGET`, `npm run dev:prism`) | Keine URL im Build (Pflichtenheft „Ortsunabhängigkeit"); ein Client-Code für Mock, Prism, Backend, VM |
| A8 | Rückgrat `NO_LESSONS_IN_BREAKS` hart im Seed; E-47 (04.09. mittags) nannte den Seed weich | Seed wieder **hart** (V2, 04.09. abends) | Der Recherchesatz vom 04.09. setzt die Regel in allen 16 Ländern hart: Unterricht in einer Pause ist ein Fehler, keine Unschönheit — eine überschrittene Wochenstundenzahl ist dagegen verhandelbar (`bundesland-regelwerte.md`, Abschnitt „Abbildung auf das Regelwerk“). Die Attrappe stand ohnehin schon auf hart. **Offen für R2 im Interview 2:** will adesso weich, ist das ein Wert, kein Code — eine Zeile in `rule` |
| A9 | Backend-Test „Testcontainers oder Compose-DB" | **beides, ein Basisklassen-Muster**: Profil ≠ `ci` → Testcontainers (`@ServiceConnection`); Profil `ci` → feste URL aus `SPRING_DATASOURCE_URL` | Studierende mit JDK+Docker: `./gradlew test integrationTest` ohne Vorbereitung; Runner ohne Docker-in-Docker: Postgres-Service; `scripts/ci-backend-test.sh` fährt lokal den CI-Weg — gleiche SQL, gleiche Migrationen, kein H2 |
| A10 | Plan: `image` nur auf `main` | `image:build` auf `main`, **manuell, `allow_failure: true`** | Ob der CAU-Runner Docker-in-Docker kann, ist offen (O-6/T-703); Deploy baut ohnehin auf der VM (`docker compose up --build`). Blockiert nie |
| A11 | Rückgrat `web/` + `android/` im Monorepo | `frontend/` (Team-Name) und getrennte Repos (E-42) | Ist-Stand der GitLab-Gruppe |

## 8. Berührungspunkte — Ports, Pfade, Env-Namen (verbindlich für B1–B4)

| Was | Wert | setzt | liest |
|---|---|---|---|
| Backend HTTP | `8080` (`server.port`), im Compose nicht veröffentlicht; dev-Override und `scripts/dev-backend.sh` veröffentlichen `${BACKEND_PORT:-8080}:8080` — im Container bleibt es immer 8080 | B1 | B3 (Proxy-Standard, mit `VITE_PROXY_TARGET` umstellbar), B4 (nginx `backend:8080`, dev-Override) |
| Postgres | Container `db`, intern `5432`; dev-Override `15432:5432`; DB `timetable`, User `tt`, Passwort `tt` | B4 (`.env.example`: `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PORT`) | B1 (Standard-URL `jdbc:postgresql://localhost:15432/timetable`) |
| Env-Namen Backend | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_PROFILES_ACTIVE` (`dev` Standard / `ci` / `prod`), `ADMIN_INITIAL_PASSWORD` (Pflicht bei `prod`), `JAVA_TOOL_OPTIONS` | B4 (Compose, CI) | B1 (`application*.properties`) |
| Compose-Projekt | `name: stundenplan` → Netz **`stundenplan_default`**, Volume `stundenplan_pgdata`; Compose-Dateien in `deployment/`, Befehle mit `cd deployment` | B4 | B1 (`scripts/dev-backend.sh`: `--network stundenplan_default`, Host `db`) |
| Gradle-Cache | Docker-Volume `stundenplan-gradle` an `/home/gradle/.gradle`; Image `gradle:9.7.1-jdk21` | B1 | B4 (`ci-image.sh` nutzt den Dockerfile-Build, nicht das Volume) |
| CI-Postgres | Service `postgres:16-alpine`, Host `postgres`, `POSTGRES_DB=timetable`, `POSTGRES_USER=tt`, `POSTGRES_PASSWORD=tt`, `SPRING_PROFILES_ACTIVE=ci`, `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/timetable` | B4 | B1 (Profil `ci`) |
| Prism | lokal `npm run mock` → **1501**; Compose `mock` (seit 05.09. in `docker-compose.dev.yml`) → `${MOCK_PORT:-1501}:4010`, Bild `stoplight/prism:5.14.2`, `platform: linux/amd64`, Befehl `mock -m false …`; Vertrag `api-contract/openapi.yaml`, Basis `/api` (Prism muss `/api/health` beantworten); geschützte Pfade brauchen irgendeinen `Authorization: Bearer`-Header | B2 | B3 (`.env.prism`: `VITE_PROXY_TARGET=http://localhost:1501`), B4 (Volume `../api-contract/openapi.yaml:/contract/openapi.yaml:ro`) |
| Web (nginx) | `${WEB_PORT:-1500}:8080`; `frontend/dist` → `/usr/share/nginx/html`; `deployment/nginx.conf` → `/etc/nginx/conf.d/default.conf`; `frontend/Dockerfile` mit Kontext **Repo-Wurzel** | B4 | B3 (liefert `npm run build` → `dist/`) |
| Vite | `5173`; Proxy `/api` → `VITE_PROXY_TARGET` (Standard `http://localhost:8080`); `.env.prism`; Basis im Client immer `/api` | B3 | — |
| Fehlerformat | `application/problem+json`, `type = urn:pushnpull:problem:<slug>` mit Slugs `validation` (+`errors[]`), `unauthenticated`, `forbidden`, `not-found`, `in-use`, `draft-exists`, `solving`, `master-data-incomplete`, `pinned-contradiction`, `data-exists` | B1 (Advice), B2 (Schema `ProblemDetail`) | B3 (`src/api/errors.ts`, unverändert) |
| Wochentage | **`MO TU WE TH FR SA`** — englisch wie jeder Bezeichner in Code, API und Datenbank. Deutsche Beschriftungen macht allein die Oberfläche (`frontend/src/lib/timegrid.ts`: `DAY_LABEL`, `DAY_SHORT`) | B2 (Schema `Weekday`) | B1 (`shared/Weekday`, CHECK-Liste in `V1`), B3 (`types.ts`), Rolle 6 (App) |
| DTO-Form v0 | **`08-Build/web/src/api/types.ts`** (= `frontend/src/api/types.ts` nach B3) ist die Abschrift von `backbone/api.md` und die Referenz für Feldnamen und -typen | — | B1 (`LoginResponse{token, role, displayName, teacherId}`, `MeDto{id, username, role, displayName, teacherId}`, `HealthDto{status, schoolName}`), B2 (alle Schemas) |
| Seed-Werte | `system_config`: `id=1, school_name='Gymnasium Musterstadt', state='SH', school_type='GYMNASIUM', max_solve_seconds=60`; Bundesland-Kürzel `BW BY BE BB HB HH HE MV NI NW RP SL SN ST SH TH` | B1 (V2) | B2 (Beispiele), B3 (`STATE_LABEL` in `components/masterdata/helpers.ts`) |
| Root-Dateien | `README.md`, `.gitattributes`, `.gitignore` (enthält `/demo/`, `deployment/.env`, `.agent-out/`, `*.local`), `.dockerignore` (dieselben Muster, **plus `**/.env.local` und `**/*.local`** — der Web-Build kopiert die Wurzel und liest `.gitignore` nicht), `.gitlab-ci.yml`, `scripts/ci-image.sh`, `scripts/ci-all.sh` | B4 | alle |
| Skript-Namen | `scripts/ci-contract-lint.sh` (B2), `scripts/ci-backend-test.sh` + `scripts/dev-backend.sh` (B1), `scripts/ci-frontend-build.sh` (B3), `scripts/ci-image.sh` + `scripts/ci-all.sh` (B4); alle `#!/usr/bin/env bash`, `set -euo pipefail`, `cd "$(dirname "$0")/.."`, ausführbar (`chmod +x`) | wie genannt | B4 (`ci-all.sh` ruft die vier), `.gitlab-ci.yml` spiegelt die Befehle |
| Verboten für alle | Dateien eines anderen Auftrags anlegen oder ändern; `git add`/`commit`/`push`; Schreiben unter `04-Repo/**`; `rm`/`rmdir` (Hook) — weg = `mv ~/.Trash`; externe Laufzeit-URLs | — | — |

CI-Job ↔ lokales Skript (B4 schreibt die Jobs genau so; die Skripte führen dieselben Befehle aus, Java-Teile im Container):

| Job | Image | Befehle | Skript |
|---|---|---|---|
| `contract:lint` | `node:22-alpine` | `cd api-contract && npm ci && npm run lint` | `ci-contract-lint.sh` (lokales Node) |
| `backend:test` | `eclipse-temurin:21-jdk` | `cd backend && ./gradlew test bootJar --no-daemon` | Teil von `ci-backend-test.sh` |
| `backend:integration` | `eclipse-temurin:21-jdk` + Service `postgres:16-alpine` | `cd backend && ./gradlew integrationTest --no-daemon` (Profil `ci`) | `ci-backend-test.sh` (Wegwerf-Postgres im Docker-Netz `stundenplan-ci`, Image `gradle:9.7.1-jdk21` = Wrapper-Version) |
| `frontend:build` | `node:22-alpine` | `cd frontend && npm ci && npm run typecheck && npm run lint && npm test && npm run build`; Artefakt `frontend/dist` | `ci-frontend-build.sh` |
| `image:build` (main, manual, allow_failure) | `docker:27` + `docker:27-dind` | `cp deployment/.env.example deployment/.env`, dann `cd deployment && docker compose build backend web` | `ci-image.sh` |
| `deploy:vm` (main, manual) | `alpine:3.20` | rsync `./` → `deploy@134.245.1.240:/opt/timetable/` über SSH 1522, dann `docker compose up -d --build` | kein Skript (nur VM); Schritte in `deployment/README-VM.md` |

## 9. Bauaufträge B1–B4 (parallel, getrennte Dateien)

Für alle vier gilt: Lies dieses Dokument ganz, dann deine Quellen. Ändere nur Dateien deines Auftrags. Jede Fertig-Zeile ist mit
einem Befehl belegt, dessen Ausgabe du gesehen hast; was du nicht prüfen konntest (weil ein anderer Teil noch fehlt), meldest du
als „ungeprüft" mit dem Befehl, den die Prüfung später ausführen soll. Weicht dein Ergebnis von Abschnitt 1–3 oder 8 ab, korrigiere
den Satz dort. Nicht committen. Lernnotizen: `07-Konzept/workflows/lernnotizen.md` (vorher lesen, eine Erkenntnis je Zeile).

### B1 — Backend-Gerüst

**Ziel.** `backend/` kompiliert und startet im Container mit Java 21, Spring Boot 4.1.1, Timefold 2.6.0, Flyway V1–V4 auf PostgreSQL;
Anmeldung mit Rollen funktioniert; ArchUnit, ein ConstraintVerifier-Test und zwei Integrationstests sind grün; das Team-Beispiel bleibt.

**Quellen.** `backbone/datenmodell.md` (Tabellen, Constraints, Flyway-Reihenfolge), `backbone/api.md` (Auth-Fluss, Fehlerformat, Pfadregeln),
`backbone/architektur.md` (Module, Schichten, ArchUnit 1–4), `backbone/solver.md` (Modell, H1, M1), `backbone/testen.md`,
`backbone/entscheidungen.md` E-44–E-48, `08-Build/web/src/api/types.ts` (DTO-Form), Timefold-Quickstart `getting-started/spring-boot-integration`
(github.com/TimefoldAI/timefold-quickstarts, Branch stable; `application.properties` dort als Muster für `timefold.*`).

**Dateien (nur diese).**
- `backend/build.gradle` (Toolchain 21; Abhängigkeiten laut Abschnitt 2; `bootJar { archiveFileName = 'app.jar' }`; `test` schließt Tag
  `integration` aus, neue Task `integrationTest` (Typ `Test`, gleicher Classpath) schließt ihn ein; beide `useJUnitPlatform`).
- `backend/Dockerfile` (multi-stage: `gradle:9.7.1-jdk21` → `gradle bootJar -x test --no-daemon` → `eclipse-temurin:21-jre`, `EXPOSE 8080`,
  `ENTRYPOINT ["java","-jar","/app/app.jar"]`). `backend/.dockerignore` (`build/`, `.gradle/`).
- `backend/src/main/java/de/wsp3_1/example/{Client,ClientController}.java` — **verschoben aus `controller/` und `model/`**, Inhalt unverändert
  bis auf `package`/`import`; die alten Ordner `controller/`, `model/` verschwinden (Dateien per `git mv`, nicht löschen).
- `backend/src/main/java/de/wsp3_1/shared/`: `Weekday` (`MO TU WE TH FR SA`, englisch wie im Vertrag — Abschnitt 8), `api/ApiExceptionHandler` (`@RestControllerAdvice`, `ProblemDetail`,
  Tabelle aus `api.md`: Bean-Validation → 400 `validation` + `errors[{field,message}]`; unlesbares JSON → 400; `NotFoundException` → 404
  `not-found`; `ConflictException(slug, detail, Map extras)` → 409 mit Slug; `AccessDeniedException` → 403 `forbidden`; Rest → 500
  `internal` mit deutschem `detail`), `domain/NotFoundException`, `domain/ConflictException`. `type` immer `urn:pushnpull:problem:<slug>`,
  Texte deutsch.
- `backend/src/main/java/de/wsp3_1/config/`: `persistence/SystemConfig` (+Repository), `api/HealthController` (`GET /api/health` →
  `{status:"UP", schoolName}` aus Zeile `id=1`). **Kein** `/api/config` (T-105, Rolle 1).
- `backend/src/main/java/de/wsp3_1/auth/`: `persistence/{UserAccount, AuthToken, Role, UserAccountRepository, AuthTokenRepository}`
  (`UserAccount.teacherId Long` als Spalte, kein Entity-Link — `Teacher` gibt es noch nicht); `domain/AuthService` (login: BCrypt-Prüfung,
  32 Bytes `SecureRandom` Base64url, DB speichert SHA-256-Hex + `expires_at = now + 12h`; `authenticate(rawToken)` → `Optional<Principal>`
  Record `{id, username, role, teacherId}`; `logout(rawToken)`), `domain/AdminBootstrap` (`ApplicationRunner`: `user_account` leer →
  `admin`/PLANNING mit `app.admin-initial-password`; leer/blank → `IllegalStateException` mit deutschem Text; Passwort nie loggen),
  `api/TokenAuthenticationFilter` (`OncePerRequestFilter`, `Authorization: Bearer` → `AuthService.authenticate` →
  `UsernamePasswordAuthenticationToken` mit `ROLE_PLANNING`/`ROLE_TEACHER`), `api/SecurityConfig` (`@EnableMethodSecurity`; CSRF aus;
  `STATELESS`; `HttpStatusEntryPoint(401)` **und** `AccessDeniedHandler`, beide schreiben ProblemDetail-JSON; Pfadregeln E-45:
  `permitAll` `GET /api/health`, `POST /api/auth/login`, `/api/clients/**` (Team-Beispiel, Kommentar „vor Liveschaltung entfernen");
  `authenticated` `DELETE /api/auth/token`, `/api/me/**`, `GET /api/timegrid`; **alles andere `/api/**` → `hasRole("PLANNING")`**),
  `api/AuthController` (`POST /api/auth/login {username,password}` → 200 `LoginResponse{token, role, displayName, teacherId}` / 401
  `unauthenticated`; `DELETE /api/auth/token` → 204; `GET /api/me` → `MeDto{id, username, role, displayName, teacherId}`;
  `displayName` = `username`, bis `Teacher` existiert), DTOs als Records mit `@Valid`.
- `backend/src/main/java/de/wsp3_1/solver/`: `TimeslotFact(id, day, position, isBreak)`, `RoomFact(id, roomType)` (Records),
  `SolverLesson` (`@PlanningEntity`, `@PlanningId id`, `classId`, `subjectId`, `teacherId`, `requiredRoomType`, `@PlanningPin pinned`,
  `@PlanningVariable(allowsUnassigned = true) TimeslotFact timeslot`, `@PlanningVariable RoomFact room`), `SolverTimetable`
  (`@PlanningSolution`, `@ProblemFactCollectionProperty @ValueRangeProvider` timeslots/rooms, `@PlanningEntityCollectionProperty` lessons,
  `@PlanningScore HardMediumSoftScore`), `TimetableConstraintProvider` mit **nur** `roomConflict` (H1) und `unassignedLesson` (M1),
  Constraint-Namen wie in `solver.md`. Kein DB-Import in diesem Paket.
- `backend/src/main/resources/application.properties` (`spring.application.name=stundenplanung`, `spring.profiles.default=dev`,
  `spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:15432/timetable}`, `…username=${SPRING_DATASOURCE_USERNAME:tt}`,
  `…password=${SPRING_DATASOURCE_PASSWORD:tt}`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`,
  `app.admin-initial-password=${ADMIN_INITIAL_PASSWORD:}`, `timefold.solver.termination.spent-limit=60s`,
  `timefold.solver-manager.parallel-solver-count=1`, `server.port=8080`), `application-dev.properties` (`app.admin-initial-password=admin`,
  `logging.level.org.flywaydb=INFO`), `application-ci.properties` (`spring.datasource.url=${SPRING_DATASOURCE_URL}` ohne Standard),
  `application-prod.properties` (leer bis auf Kommentar: Passwort kommt aus der Umgebung).
- `backend/src/main/resources/db/migration/V1__master_data_config_auth.sql` (teacher, subject, room, school_class, class_subject, timeslot,
  teacher_subject, teacher_preferred_timeslot, teacher_blocked_timeslot, rule mit `grade int NULL` und `UNIQUE(type, scope, grade)` (E-47),
  rule_preset mit `grade int NULL CHECK (grade BETWEEN 5 AND 10)`, `UNIQUE NULLS NOT DISTINCT (state, type, scope, grade)` und derselben
  Scope-Pflicht-CHECK wie `rule`, system_config, user_account, auth_token — alle Unique/Check/FK aus `datenmodell.md`, snake_case,
  `bigserial`/`bigint`, Enums als `varchar` + CHECK-Liste, Zeiten `time`, Zeitstempel `timestamptz`), `V2__rule_presets.sql` (**erzeugt**, siehe
  Abschnitt „Regelwerte je Bundesland" weiter unten: 138 Zeilen über 16 Länder, je Land fünf schulweite und bei zehn Ländern zusätzlich
  Jahrgangszeilen; `system_config`-Zeile laut Abschnitt 8; `rule`-Zeilen für SH aus denselben Preset-Werten: CLASS-Tag 6 soft aktiv,
  CLASS-Woche 30 soft aktiv, TEACHER-Tag 6 soft inaktiv, TEACHER-Woche 26 soft inaktiv, NO_LESSONS_IN_BREAKS 1 **hart** aktiv — E-47),
  `V3__planning.sql` (timetable, lesson, Partial-Unique-Indizes
  `…one_draft`/`…one_active`, `CHECK ((timeslot_id IS NULL) = (room_id IS NULL))`), `V4__substitution_changelog.sql` (change_event, lesson_exception
  `UNIQUE(lesson_id, date)`, exception_event, changelog_entry mit Filterspalten). Jede Datei mit Kopfkommentar, welche Tabelle welchem Modul gehört.
- Tests unter `backend/src/test/java/de/wsp3_1/`: `ArchitectureTest` (Regeln 1–4 aus `architektur.md`, Slices `de.wsp3_1.(*)..` ohne `example`;
  erlaubte Modulabhängigkeiten als Tabelle im Test, auch für noch leere Module: masterdata→shared; rules→shared; planning→masterdata,rules,solver,
  changelog,shared; solver→shared; substitution→masterdata,planning,changelog,shared; changelog→shared; auth→masterdata,shared; config→shared),
  `solver/TimetableConstraintProviderTest` (`ConstraintVerifier`: T-W3.1-Tripel → `roomConflict` bestraft 1; Stunde ohne Zeitfenster →
  `unassignedLesson` bestraft 1), `auth/SecurityConfigTest` (`@WebMvcTest` mit `SecurityConfig`+Filter, `@MockitoBean AuthService`:
  `GET /api/health` ohne Token 200; `GET /api/me` ohne Token 401 `application/problem+json` mit `type …:unauthenticated`; `GET /api/teachers`
  mit TEACHER-Principal 403 `…:forbidden`), `shared/TestcontainersConfig` (`@TestConfiguration(proxyBeanMethods=false) @Profile("!ci")`,
  `@Bean @ServiceConnection PostgreSQLContainer<?>("postgres:16-alpine")`), `shared/IntegrationTestBase` (`@SpringBootTest` +
  `@AutoConfigureMockMvc` + `@Import(TestcontainersConfig.class)` + `@Tag("integration")` + Property `app.admin-initial-password=admin`),
  `ApplicationIT` (ersetzt `backend/BackendApplicationTests` — Kontext startet; `flyway_schema_history` enthält V1–V4;
  `SELECT count(*) FROM rule_preset` = 138 bei 16 `state` und 80 schulweiten Zeilen; das aktive Regelwerk stimmt Zeile für Zeile mit dem
  Preset des in `system_config` eingestellten Landes überein; `GET /api/health` → `schoolName` „Gymnasium Musterstadt"), `auth/AuthFlowIT`
  (login admin/admin → token; `GET /api/me` mit Token → 200 `username=admin`, `role=PLANNING`; falsches Passwort → 401; `DELETE /api/auth/token`
  → 204; danach `GET /api/me` → 401).
- `scripts/dev-backend.sh` und `scripts/ci-backend-test.sh` (Inhalt: Abschnitt 8 und 3; `ci-backend-test.sh` legt Netz `stundenplan-ci` an,
  startet `postgres:16-alpine` als `stundenplan-ci-db` mit `--rm`, wartet auf `pg_isready`, führt `gradle test integrationTest --no-daemon` im
  Image `gradle:9.7.1-jdk21` mit Volume `stundenplan-gradle` aus, stoppt die DB am Ende — auch bei Fehler (`trap`). Weitere Argumente gehen
  an Gradle durch, deshalb `scripts/ci-backend-test.sh --rerun` für einen echten Zweitlauf statt `UP-TO-DATE`. Hinweis: der PreToolUse-Hook
  dieses Macs kann das Wort `rm` auch in `docker rm` blockieren — `docker stop` + `--rm` benutzen; `-it` nur, wenn `[ -t 0 ]`).

### Regelwerte je Bundesland (`V2__rule_presets.sql` — erzeugt, nicht von Hand geschrieben)

Stand 04.09.2026 abends. Die Platzhalter „nach SH-Muster“ sind weg. Die Werte stammen aus einer Recherche der
Stundentafel- und Arbeitszeitverordnungen der Länder sowie der KMK-Übersichten; Quellen je Land und die Ableitung
stehen in `07-Konzept/recherche/bundesland-regelwerte.md` (Abschnitt „Abbildung auf das Regelwerk“), die Zahlen in
`bundesland-regelwerte.json`.

**Generator.** `node scripts/gen-rule-presets.mjs` liest die JSON und schreibt drei Dateien:
`backend/src/main/resources/db/migration/V2__rule_presets.sql`, `../web/src/mock/rule-presets.ts` und
`frontend/src/mock/rule-presets.ts`. Damit zeigen Backend und Attrappe dieselben Zahlen. `--check` schreibt nichts,
sondern meldet nur Abweichungen (Rückgabewert 2). Die JSON liegt **außerhalb** des Repos im Konzeptordner — mit
`--input <datei>` lässt sich ein anderer Pfad angeben. Jede SQL-Zeile trägt am Zeilenende einen Kommentar mit der
Herkunft des Werts (`basis`), jedes Land einen Kommentarblock mit der Quellenlage (`source_note`).

**Umfang.** 138 Zeilen über 16 Länder: je Land fünf schulweite Zeilen (`grade IS NULL`) und bei zehn Ländern
zusätzlich Jahrgangszeilen `MAX_LESSONS_PER_WEEK`/`CLASS` mit `grade` 5..10 (Kann-Kriterium E-47). `rule_preset`
hat dafür seit 04.09. eine Spalte `grade` (CHECK 5..10) und die Eindeutigkeit `(state, type, scope, grade)`.

**Was gesetzt und nicht belegt ist — Rückfrage R2 im Interview 2 (08.09.) klärt es:**

| Punkt | Stand im Seed | Warum unsicher |
|---|---|---|
| `MAX_LESSONS_PER_DAY`, Klasse und Lehrkraft | 6 in allen Ländern außer Brandenburg (dort 8) | Nur Brandenburg regelt eine Tageshöchstzahl für Schülerinnen und Schüler (VV-Schulbetrieb); für Lehrkräfte regelt sie **kein** Land. Die 6 ist eine fachliche Setzung des Projekts |
| BW `MAX_LESSONS_PER_WEEK`/`CLASS` = 34 | Ersatzwert, nicht amtlich | Baden-Württemberg hat keine belastbare Wochenstundenzahl (Kontingentstundentafel, Verordnungstexte nicht auslesbar). 34 ist der Median der belegten Länder |
| SN Jahrgangswerte 29/31/32/32/34/35 | übernommen | Stammen aus einer Schulwebsite, nicht aus der amtlichen Anlage 4a — der wackeligste Einzelpunkt des Satzes |
| HH `MAX_LESSONS_PER_WEEK`/`TEACHER` = 25 | gerundeter Modellwert | Hamburg hat kein Regelstundenmaß, sondern ein Faktorisierungsmodell; 25,1 ist ein Durchschnitt daraus |
| `NO_LESSONS_IN_BREAKS` hart, Wert 1 | hart | siehe Abweichung A8 |
| Rundungen | kaufmännisch, Originalwert steht in `basis` | HH 25,1→25, NI 23,5→24, HE/NW/SH 25,5→26; halbe Klassenstunden (BY Jg. 7, NW Jg. 7–10 je 31,5) werden **auf**gerundet, weil die Regel eine Obergrenze ist |

**Wenn R2 Werte ändert:** V2 ist ab dem ersten Commit unveränderlich. Dann die JSON pflegen, das Skript laufen
lassen, die erzeugte SQL gegen die vorige diffen und nur die Unterschiede als `V<n>__rule_presets_korrektur.sql`
nachschieben; die beiden TS-Dateien dürfen dagegen direkt ersetzt werden.

**Fertig, wenn (Befehl → Ausgabe).**
1. `scripts/ci-backend-test.sh` → `BUILD SUCCESSFUL`; Testbericht `backend/build/test-results/` zeigt `ArchitectureTest`,
   `TimetableConstraintProviderTest`, `SecurityConfigTest` (Task `test`) und `ApplicationIT`, `AuthFlowIT` (Task `integrationTest`) ohne Fehler.
2. `docker build -t stundenplan-backend backend/` → Image gebaut; `docker run --rm --network stundenplan-ci -e SPRING_PROFILES_ACTIVE=prod
   -e SPRING_DATASOURCE_URL=jdbc:postgresql://stundenplan-ci-db:5432/timetable -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt
   -e ADMIN_INITIAL_PASSWORD=geheim -p 8080:8080 stundenplan-backend` (DB aus Schritt 1 dafür laufen lassen) → `Started BackendApplication`;
   `curl -s localhost:8080/api/health` → `{"status":"UP","schoolName":"Gymnasium Musterstadt"}`; Login mit `geheim` → Token;
   `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/teachers` → `401`; mit Token → `404` (kein Controller, aber autorisiert);
   `curl -s localhost:8080/api/clients` → Team-Beispiel antwortet mit zwei Clients. Start **ohne** `ADMIN_INITIAL_PASSWORD` im Profil `prod` bricht
   mit deutscher Meldung ab (Ausgabe zitieren).
3. `grep -n 'languageVersion' backend/build.gradle` → `21`; `git status --short backend/` zeigt `controller/`→`example/` als Umbenennung (`R`), kein `D` ohne `R`.
4. `spring.jpa.hibernate.ddl-auto=validate` hat bei Schritt 1 keinen Fehler geworfen (Entities `SystemConfig`, `UserAccount`, `AuthToken` passen zu V1).
5. Abschnitt 2: ArchUnit-Version und tatsächlich aufgelöste Flyway-Version eintragen (`gradle dependencies --configuration runtimeClasspath | grep -i flyway`).

**Stand nach B1 (04.09., gemessen).** `backend/` ist gebaut und grün: 12 Tests in `test` (ArchitectureTest 5, SecurityConfigTest 4,
TimetableConstraintProviderTest 3) und 11 in `integrationTest` (ApplicationIT 5, AuthFlowIT 6); das Abbild `stundenplan-backend`
startet im Profil `prod` gegen PostgreSQL 16, wendet V1–V4 an und beantwortet health, login, `/api/me`, `/api/clients`.
Vier Abweichungen von der Vorgabe oben: (1) `HealthController` liest über den neuen `config/domain/SystemConfigService`, damit
`..api..` keine Entity und kein Repository berührt (ArchUnit-Regeln 2 und 3); (2) neu `shared/api/ApiProblems` als gemeinsame
Quelle für 401/403, weil Advice, Einstiegspunkt und Zugriffsverweigerung dasselbe Format schreiben müssen; (3) neu ein Handler für
`NoResourceFoundException` → 404 `not-found`, sonst antwortet ein noch nicht gebauter Pfad mit 500 (gemessen); (4) `rule` und
`rule_preset` benutzen `UNIQUE NULLS NOT DISTINCT` — das setzt PostgreSQL ≥ 15 voraus, sonst greift die Eindeutigkeit bei leerem
`scope`/`grade` nicht. **Offen:** 405 und 415 laufen weiter in den 500-Auffangzweig; api.md kennt dafür keinen Slug (Karte T-408).

**Korrektur B1 (04.09. abends, nach der Prüfung).** Zwei Punkte im Backend geändert, beide gemessen:
1. **Wochentage jetzt `MO TU WE TH FR SA`.** `shared/Weekday` und die CHECK-Liste in `V1` hatten deutsche Kürzel
   (`MO DI MI DO FR SA`), Vertrag und `types.ts` englische. Ein Zeitfenster aus der Oberfläche wäre an der CHECK-Liste
   gescheitert. Der Vertrag gewinnt (Abschnitt 6 „Vertrag zuerst", Abschnitt 8 Zeile „Wochentage"); geändert sind genau
   `shared/Weekday.java`, die CHECK-Liste in `V1` und eine Testkonstante. **Wer schon eine Datenbank hat: `docker compose
   down -v`** — sonst weist Flyway die geänderte Prüfsumme von `V1` zurück.
2. **Eigener Text für die fehlgeschlagene Anmeldung.** Falsches Passwort ergab 401 mit „das Token fehlt oder ist
   abgelaufen." — im Anmeldedialog irreführend, weil das Frontend `detail` anzeigt. Neu: `ApiProblems.badCredentials()`
   („Benutzername oder Passwort ist falsch.") und ein `@ExceptionHandler(BadCredentialsException.class)` vor dem
   allgemeinen. Slug und Status bleiben `unauthenticated`/401, der Vertrag ändert sich nicht. `AuthFlowIT` prüft jetzt
   beide Texte (daher 6 statt 5 Fälle).

**Werkzeuglage 04.09.:** Am Nachmittag startete der Docker-Daemon dieses Macs zeitweise keine Container und hielt
Bind-Mounts außerhalb von `/private/tmp` an (Abhilfe war `docker desktop stop && docker desktop start`). Das war ein
lokales Problem des Tages, kein Mangel der Schablone: am Abend liefen `scripts/ci-backend-test.sh`, `scripts/dev-backend.sh`,
`scripts/ci-all.sh` und der ganze Compose-Stack auf demselben Rechner durch — Belege in Abschnitt 10.

**Berührungspunkte.** Port 8080, Env-Namen, Profile, Netz `stundenplan_default`/`stundenplan-ci`, Volume `stundenplan-gradle`, DTO-Form aus
`types.ts`, Seed-Werte, Fehler-Slugs — alles Abschnitt 8. `scripts/dev-backend.sh` setzt voraus, dass B4 die Compose-DB `db` im Projekt
`stundenplan` anlegt; prüfen kannst du es erst, wenn `deployment/docker-compose.yml` existiert — sonst „ungeprüft" mit Befehl.

### B2 — API-Vertrag v0 + Prism-Mock + contract-lint

**Ziel.** `api-contract/openapi.yaml` (OpenAPI 3.1) beschreibt v0 vollständig mit `example` je Antwort; Prism liefert jede Antwort auf 1501;
Redocly-Lint ist ohne Fehler; das Team kann v1 (T-403) nach demselben Muster ergänzen.

**Quellen.** `backbone/api.md` (Endpunkte v0: health, auth, me, users, teachers, subjects, rooms, classes(+subjects), timegrid, rules(+reset,
POST/DELETE Jahrgangszeilen), config, sample-data; Fehlerformat; Rollen), `08-Build/web/src/api/types.ts` (**Feldnamen und Typen 1:1**),
`08-Build/web/src/mock/data-small.ts` (Beispielwerte: Meyer/Schulz/Weber, 7a/8b/9c, R101/R204/P1, Raster Mo–Fr 6 Stunden, Pause nach der 2.,
Regeln), `backbone/entscheidungen.md` E-45–E-48.

**Dateien (nur diese).** `api-contract/openapi.yaml`, `api-contract/package.json` (+ `package-lock.json`; devDependencies `@stoplight/prism-cli`
und `@redocly/cli` mit exakter Version; Scripts `"mock": "prism mock -p 1501 -h 0.0.0.0 openapi.yaml"`, `"lint": "redocly lint openapi.yaml"`),
`api-contract/.gitignore` (`node_modules`), `api-contract/README.md` (wie ändern; `api-change`-Regel; Freeze 12.09. additiv; wie v1 ergänzen; Prism-
Grenzen: statische Beispiele, kein Zustand, 4xx nur per `Prefer: code=409`), `scripts/ci-contract-lint.sh`.

**Inhalt des Vertrags.** `info` (Titel „Stundenplanung — REST-API", Version `0.1.0`, Beschreibung mit Konventionen aus `api.md`: camelCase,
`yyyy-MM-dd`, `HH:mm`, Wochentag `MO TU WE TH FR SA`, IDs int64, Listen unpaginiert, Texte deutsch); `servers` so, dass Prism `/api/…` beantwortet; `securitySchemes`
`bearerToken` (http/bearer); `tags` je Ressource; `components/schemas` für **jeden** Typ aus `types.ts` (HealthDto, LoginRequest, LoginResponse,
MeDto, PasswordChange, UserDto/UserInput, TeacherDto/TeacherInput inkl. `warnings[]`, `plannedHours`, `preferredTimeslotIds[]`,
`blockedTimeslotIds[]`, SubjectDto/Input, RoomDto/Input, ClassDto/Input inkl. `subjects[]` und `warnings[]`, ClassSubjectInput,
TimegridDto/TimegridInput/TimeslotDto/TimeslotInput, RuleDto/RuleInput (mit `grade`), ConfigDto, SampleDataRequest/Response, ProblemDetail mit
`errors[]`, `missing[]`, `conflicts[]`); `components/responses` `Validation400`, `Unauthenticated401`, `Forbidden403`, `NotFound404`,
`Conflict409` mit Beispielen aus `api.md`; jede Operation mit `operationId` (camelCase wie in `client.ts`, z. B. `listTeachers`), `security`
(leer bei health/login), `x-role: PLANNING|TEACHER|ANY` als Lesehilfe, und **`example` je 2xx-Antwort** (Pflichtenheft-Testdaten; `GET /teachers`
liefert genau Meyer, Schulz, Weber mit den Werten aus dem Pflichtenheft: Schulz Deputat 2, Mathematik, Fr 5./6. gesperrt). Nur die Endpunkte
aus v0 — **keine** timetable/draft/events/… (T-403).

**Korrektur B2 (04.09., geprüft).** Die Basis `/api` steht in jedem Pfadschlüssel (`/api/teachers`), `servers` bleibt `/` —
Prism blendet einen Server-Pfad aus, mit `servers: /api` beantwortete der Mock `/health` statt `/api/health`. Schemanamen folgen
`types.ts` statt der Sammelnamen oben: `CreateUser` (nicht `UserInput`), `SampleDataResult` (nicht `SampleDataResponse`), dazu
`PasswordSet` für den Rumpf von `PUT /api/users/{id}/password`. Der Vertrag hat 39 Operationen; jede `operationId` kommt als
Methodenname in `client.ts` vor.

**Fertig, wenn.**
1. `scripts/ci-contract-lint.sh` → Redocly meldet `0 errors` (Warnungen erlaubt, aufgelistet).
2. `(cd api-contract && npm run mock)` läuft; in zweiter Shell: `curl -s localhost:1501/api/health` → Health-Beispiel;
   `curl -s -H 'Authorization: Bearer x' localhost:1501/api/teachers | head -c 300` → Meyer zuerst; `curl -s -X POST localhost:1501/api/auth/login
   -H 'content-type: application/json' -d '{"username":"admin","password":"admin"}'` → LoginResponse-Beispiel;
   `curl -s -H 'Authorization: Bearer x' -H 'Prefer: code=409' -X DELETE localhost:1501/api/teachers/1` → ProblemDetail mit `in-use`.
   **Der Bearer-Header gehört an jeden geschützten Pfad**: Prism setzt das `security` des Vertrags durch und prüft nur, ob ein
   Header da ist — ohne ihn kommt überall 401 `unauthenticated` statt des Beispiels. Ohne Header gehen nur `/api/health` und
   `POST /api/auth/login`. Für **jeden** Pfad der Tabelle in `api.md` (v0-Teil) einmal `curl` mit Statuscode ≠ 404/500 — Liste in der Meldung.
3. `node -e` oder `grep -c 'operationId' api-contract/openapi.yaml` ≥ Anzahl der v0-Operationen (nennen); jede `operationId` kommt in
   `08-Build/web/src/api/client.ts` als Methodenname vor (Skript-Zeile zeigen; Abweichungen begründen).
4. `docker run --rm --platform linux/amd64 -v "$PWD/api-contract/openapi.yaml":/contract/openapi.yaml:ro -p 1502:4010
   stoplight/prism:5.14.2 mock -m false -h 0.0.0.0 -p 4010 /contract/openapi.yaml` (Port 1502 nur für diese Probe) →
   `curl localhost:1502/api/health` antwortet — belegt, dass B4s Compose-Mock mit derselben Datei läuft. **Version und `-m false`
   gehören dazu**: mit `stoplight/prism:5` (5.15.10) und ohne `-m false` startet der Container in Endlosschleife neu
   (`TypeError: … reading 'isPrimary'`), und der Port antwortet nie (gemessen 04.09.).

**Berührungspunkte.** Port 1501; Datei-Pfad `api-contract/openapi.yaml` (B4 mountet ihn); Slugs und DTO-Form (Abschnitt 8); B3 spricht Prism nur
über den Vite-Proxy. Keine Datei außerhalb `api-contract/` und `scripts/ci-contract-lint.sh`.

### B3 — Frontend-Einzug aus `08-Build/web`

**Ziel.** `frontend/` ist der Stand aus `08-Build/web` (alle 19 Routen gegen Attrappe, 27 Vitest-Fälle), baut im Klon grün, kann gegen Attrappe,
Prism und Backend laufen; das Team-Gerüst ist verstanden ersetzt, seine Beispieldateien bleiben lesbar erhalten.

**Quellen.** `08-Build/web/README.md` (Abschnitt „Übernahme nach web-app/frontend/" — Schritt 1–3 und 6 ausführen, 4/5 macht B4),
`08-Build/web/AGENT-NOTIZEN.md`, `08-Build/SPEZIFIKATION.md` Abschnitte 1, 7, 8, 11.

**Dateien (nur diese).** Alles unter `frontend/` und `scripts/ci-frontend-build.sh`.
- Entfernen aus dem Team-Stand (per `git mv` nach `frontend/src/example/`, Inhalt unverändert): `src/App.jsx`, `src/App.css`, `src/pages/*.jsx`,
  `src/components/Navbar.jsx`, `src/main.jsx`, `src/assets/*` (die zwei SVGs und `hero.png`). Ersetzt werden `vite.config.js` → `vite.config.ts`,
  `package.json`, `package-lock.json`, `eslint.config.js`, `index.html`, `README.md`, `.gitignore`, `src/index.css`.
- Kopieren aus `08-Build/web/` (ohne `node_modules/`, `dist/`, `.demo-dist/`, `.agent-out/`): `package.json`, `package-lock.json`, `index.html`,
  `tsconfig*.json`, `vite.config.ts`, `vite.demo.config.ts`, `vitest.config.ts`, `eslint.config.js`, `components.json`, `.env.example`, `.env.mock`,
  `.env.demo`, `.gitignore`, `README.md`, `AGENT-NOTIZEN.md`, `scripts/`, `public/`, `src/`.
- Ändern: `vite.config.ts` — Proxy-Ziel aus `loadEnv(mode, process.cwd(), '')`: `VITE_PROXY_TARGET ?? 'http://localhost:8080'`; neu `.env.prism`
  (`VITE_PROXY_TARGET=http://localhost:1501`); `package.json` Script `"dev:prism": "vite --mode prism"`; `.env.example` um `VITE_PROXY_TARGET`-Zeile
  ergänzen; `eslint.config.js`: `src/example/**` in `globalIgnores` (Team-JSX bleibt ungelintet); `README.md`: Abschnitt „Übernahme" entfernen,
  Pfade auf den neuen Ort (`../demo` liegt außerhalb des Repos und ist per Root-`.gitignore` `/demo/` ausgeschlossen — Satz dazu), Abschnitt
  „Modi" um `dev:prism` ergänzen, Absatz „Was vom Team-Gerüst blieb" (Vite, Tailwind, react-router, ESLint-Basis; Beispielseiten unter
  `src/example/`, nicht geroutet); `src/example/README.md` (drei Zeilen: Herkunft, nicht geroutet, `/api/clients` im Backend unter `example/`).
- `scripts/ci-frontend-build.sh`: `cd frontend && npm ci && npm run typecheck && npm run lint && npm test && npm run build`.

**Ergebnis B3 (04.09., gebaut und geprüft).** Zwei Abweichungen von der Vorgabe oben: (1) `vitest.config.ts` und `vite.demo.config.ts` mussten mitgeändert werden — `vite.config.ts` exportiert wegen `loadEnv` jetzt eine Callback-Config, und `mergeConfig` kann keine Callback-Config mischen; beide holen sich das Basisobjekt darum über die neue benannte Ausfuhr `baseConfig(mode)` aus `vite.config.ts`. (2) Die Beispieldateien des Teams liegen mit ihrer Ordnerstruktur unter `src/example/` (`pages/`, `components/`, `assets/`), weil ihr Inhalt unverändert bleiben soll; `public/icons.svg` ist dabei nach `src/example/assets/` gewandert, damit `public/` genau der Stand aus `08-Build/web` ist.

**Fertig, wenn.**
1. `scripts/ci-frontend-build.sh` → exit 0; Ausgabe zeigt `tsc` ohne Fehler, ESLint `0 problems`, Vitest `27 passed` (oder die Zahl, die du siehst),
   `vite build ✓ built` mit `dist/index.html`. Grep `dist/assets/*.js` auf `http(s)://` trifft keine Lade-URL (nur Namespaces/Doku-Links).
2. `npm run dev:mock -- --port 5176` → `curl -s -o /dev/null -w '%{http_code}' localhost:5176/login` → `200`; Titel `Stundenplanung`.
3. `npm run dev:prism -- --port 5177` mit laufendem Prism (B2) → `curl -s localhost:5177/api/health` liefert das Prism-Beispiel; wenn B2 noch nicht
   fertig ist: Stub testen — eigener Prism-Aufruf auf 1501 mit einer Minimal-Spec im Scratchpad — und als „geprüft gegen Stub" melden.
4. `npm run dev -- --port 5178` → `curl -s localhost:5178/api/health` → Proxy-Fehler 5xx, solange kein Backend läuft (belegt, dass das Ziel 8080 ist);
   mit B1-Backend: Health-Antwort und Login im Browser (Playwright-MCP oder Chrome headless, Screenshot der Übersicht nach Login) — sonst „ungeprüft".
5. `git status --short frontend/` zeigt die Team-Dateien als `R` nach `src/example/`; `find frontend/src/example -type f` listet 11 Team-Dateien + README (die Unterordner `pages/`, `components/`, `assets/` bleiben erhalten, sonst brächen die unveränderten Importe; `public/icons.svg` ist als Team-Datei mitgewandert).
6. `du -sh frontend/node_modules` wird **nicht** ins Repo aufgenommen (`git status` zeigt es nicht — `.gitignore` greift).

**Berührungspunkte.** Ports 5173/8080/1501 und `VITE_PROXY_TARGET` (Abschnitt 8); `frontend/Dockerfile` und `deployment/nginx.conf` gehören B4 —
nicht anlegen; `npm run build` muss ohne Env nach `dist/` bauen (B4s Dockerfile ruft genau das). Keine Paketänderung außer den genannten Scripts.

### B4 — deployment/, .gitlab-ci.yml, Root-Dateien, lokale CI-Skripte

**Ziel.** Ein Compose-Stack, der mit einem Befehl startet (db, backend, web, mock), ein dev-Override, nginx-Konfiguration, VM-Anleitung, Backup-Skript,
eine Pipeline, deren Jobs lokal als Skripte laufen, und die Root-Dateien des Repos.

**Quellen.** `backbone/pipeline.md` (CI-Skizze, Deploy-Weg, Walking Skeleton), `backbone/architektur.md` (Laufzeit-Verteilung, Speicher-Startwerte,
URLs ohne Neukompilieren), `07-Konzept/WISSENSSTAND.md` Abschnitt 6 (VM Gruppe 5: SSH 1522, HTTP 8080–8083 → 1500–1503), `08-Build/web/README.md`
Schritte 4/5, Abschnitt 8 dieses Dokuments.

**Dateien (nur diese).**
- `deployment/docker-compose.yml` (`name: stundenplan`; `db`: `postgres:16-alpine`, `POSTGRES_DB/USER/PASSWORD` aus `DB_*`, Volume `pgdata`,
  `healthcheck: pg_isready -U ${DB_USER} -d ${DB_NAME}`, `mem_limit: 512m`, `command: postgres -c shared_buffers=128MB`; `backend`: `build: ../backend`,
  `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${DB_NAME}`, `…USERNAME/PASSWORD`, `ADMIN_INITIAL_PASSWORD=${ADMIN_INITIAL_PASSWORD:?ADMIN_INITIAL_PASSWORD fehlt in deployment/.env}`,
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70`, `depends_on: db: condition: service_healthy`, `mem_limit: 1536m`, kein `ports`; `web`:
  `build: {context: .., dockerfile: frontend/Dockerfile}`, `ports: ["${WEB_PORT:-1500}:8080"]`, `depends_on: [backend]`, `mem_limit: 64m`; `mock`:
  `image: stoplight/prism:5.14.2`, `platform: linux/amd64`, `command: mock -m false -p 4010 -h 0.0.0.0 /contract/openapi.yaml` (Korrektur 04.09., Begründung unten), Volume `../api-contract/openapi.yaml:/contract/openapi.yaml:ro`,
  `ports: ["${MOCK_PORT:-1501}:4010"]`, `mem_limit: 256m`; `volumes: pgdata`).
- `deployment/docker-compose.dev.yml` (`db.ports: ["${DB_PORT:-15432}:5432"]`, `backend.ports: ["${BACKEND_PORT:-8080}:8080"]`, `backend.environment.SPRING_PROFILES_ACTIVE=dev`).
- `deployment/.env.example` (`ADMIN_INITIAL_PASSWORD=bitte-aendern`, `DB_NAME=timetable`, `DB_USER=tt`, `DB_PASSWORD=tt`, `DB_PORT=15432`,
  `WEB_PORT=1500`, `MOCK_PORT=1501`, Kommentar: auf der VM `WEB_PORT=8080`, `MOCK_PORT=8081` — die VM-NAT macht daraus 1500/1501).
- `deployment/nginx.conf` (`listen 8080`, `root /usr/share/nginx/html`, `location / { try_files $uri /index.html; }`, `location /api/ { proxy_pass
  http://backend:8080/api/; proxy_set_header Host $host; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_read_timeout 120s; }`,
  gzip für text/css/js/json, `client_max_body_size 2m`).
- `frontend/Dockerfile` (Kontext Repo-Wurzel: `FROM node:22-alpine AS build; WORKDIR /w; COPY frontend/package*.json ./; RUN npm ci; COPY frontend/ ./;
  RUN npm run build; FROM nginx:alpine; COPY --from=build /w/dist /usr/share/nginx/html; COPY deployment/nginx.conf /etc/nginx/conf.d/default.conf;
  EXPOSE 8080`). Dazu `/.dockerignore` in der Repo-Wurzel (der Build-Kontext ist die Wurzel): `**/node_modules`, `**/dist`, `**/build`,
  `**/.gradle`, `.git`.
- `deployment/backup.sh` (`docker compose exec -T db pg_dump -U "$DB_USER" "$DB_NAME" | gzip > backups/timetable-$(date +%F-%H%M).sql.gz`, liest `.env`).
- `deployment/README-VM.md` (Schritte, **nicht ausgeführt**: SSH `ssh -p 1522 deploy@134.245.1.240`; Docker + User `deploy` in Gruppe `docker`;
  `/opt/timetable`; `.env` mit `WEB_PORT=8080`, `MOCK_PORT=8081`, echtem Passwort; `docker compose up -d --build`; Prüfung `curl localhost:8080/api/health`
  und von außen `http://134.245.1.240:1500`; Speicherbudget 4 GB, Messung `free -m`/`docker stats` als Lernnotiz (T-704); Deploy-Key + `SSH_KNOWN_HOSTS`
  aus `ssh-keyscan -p 1522 134.245.1.240` in GitLab-CI-Variablen; Umzug = `.env` ändern; Backup vor jedem Deploy ab 20.09.; Rückfall CI-Artefakte +
  COPY-Dockerfiles, falls der Build auf der VM kippt).
- `.gitlab-ci.yml` (Stages `build`, `test`, `image`, `deploy`; Jobs exakt wie Tabelle in Abschnitt 8; Gradle-Cache über `GRADLE_USER_HOME=$CI_PROJECT_DIR/.gradle`,
  `GRADLE_OPTS=-Dorg.gradle.daemon=false`; npm-Cache `frontend/.npm` und `api-contract/.npm` mit `npm ci --cache .npm --prefer-offline`; JUnit-Reports
  als `artifacts.reports.junit`; `frontend/dist` und `backend/build/libs/*.jar` als Artefakte 1 Woche; `image:build` `rules: main` + `when: manual` +
  `allow_failure: true` mit dind-Service; `deploy:vm` `rules: main` + `when: manual`, `before_script` mit `ssh-agent`, `rsync -az --delete -e "ssh -p 1522"
  --exclude .git --exclude node_modules ./ deploy@134.245.1.240:/opt/timetable/`, dann `ssh … "cd /opt/timetable/deployment && docker compose up -d --build && docker compose ps"`).
- Root: `.gitattributes` (`* text=auto eol=lf`, `*.bat text eol=crlf`, `*.jar binary`, `*.png binary`, `*.woff2 binary`, `*.svg text`), `.gitignore`
  (`deployment/.env`, `/demo/`, `.agent-out/`, `*.local`, `.DS_Store`, `**/node_modules/`), `README.md` (Titel, drei Befehle aus Abschnitt 3,
  „Ausführlich: STARTSTAND.md", Team-Hinweise angepasst: JDK 21 statt 25, Node ≥ 20.19, `npm run dev:mock`), `scripts/ci-image.sh`
  (`cd deployment && docker compose build`), `scripts/ci-all.sh` (ruft die vier Skripte nacheinander, bricht beim ersten Fehler ab, druckt am Ende
  eine Zeile je Skript mit ok/fehlgeschlagen).

**Fertig, wenn.**
1. `(cd deployment && cp .env.example .env && docker compose -f docker-compose.yml -f docker-compose.dev.yml config)` → gültiges Compose ohne Warnung;
   Ausgabe zeigt Netz `stundenplan_default`, Ports `1500`, `1501`, `15432`, `8080` und `ADMIN_INITIAL_PASSWORD` aus `.env`. Ohne `.env`: `docker compose config`
   bricht mit „ADMIN_INITIAL_PASSWORD fehlt" ab (Ausgabe zitieren). `deployment/.env` danach nach `~/.Trash` bewegen (nicht im Repo lassen).
2. `docker run --rm -v "$PWD/deployment/nginx.conf":/etc/nginx/conf.d/default.conf:ro nginx:alpine nginx -t` → `syntax is ok` (Auflösung von `backend` schlägt
   dabei fehl? Dann `proxy_pass` mit Variable + `resolver` oder Test mit `--add-host backend:127.0.0.1` — Weg nennen).
3. `(cd deployment && docker compose up -d db mock)` → `docker compose ps` zeigt beide `running`; `curl -s localhost:1501/api/health` antwortet, sobald B2s
   `openapi.yaml` existiert (sonst mit einer Minimal-Spec im Scratchpad als Stand-in prüfen und so melden); `docker compose down` am Ende.
4. `docker build -f frontend/Dockerfile -t stundenplan-web .` → Image gebaut, `docker run --rm -p 1509:8080 stundenplan-web` → `curl -s -o /dev/null -w '%{http_code}'
   localhost:1509/login` → `200`. Wenn B3 noch nicht fertig ist: denselben Build gegen einen Scratch-Ordner mit `08-Build/web` als `frontend/` und deiner
   `nginx.conf` als `deployment/nginx.conf` — als „gegen Stand-in geprüft" melden.
5. `bash -n` auf alle `scripts/*.sh` und `deployment/backup.sh`; `scripts/ci-all.sh` ohne die noch fehlenden Skripte → meldet fehlende Skripte
   namentlich statt still durchzulaufen.
6. `.gitlab-ci.yml`: YAML-Syntax per `npx --yes js-yaml .gitlab-ci.yml > /dev/null` (oder `python3 -c 'import yaml; yaml.safe_load(open(".gitlab-ci.yml"))'`),
   dazu ein Abgleich: je Job die Befehlszeile neben der Skriptzeile (Tabelle in der Meldung). Der Runner selbst ist nicht erreichbar → „ungeprüft im Runner".
7. `git check-attr eol backend/gradlew frontend/index.html` → `lf`; `git status --short` zeigt keine `.env`, kein `node_modules`.

**Berührungspunkte.** Alles in Abschnitt 8. Du liest `backend/Dockerfile` (B1) und `frontend/package.json` (B3) nur; du schreibst `frontend/Dockerfile`
und `/.dockerignore`. `scripts/ci-all.sh` ruft die Skripte der anderen beim Namen — ihre Namen sind fest.

**Stand nach B4 (04.09.).** Gebaut wie beschrieben, mit drei Abweichungen: `backend:test` ruft `./gradlew test bootJar --no-daemon`,
damit `backend/build/libs/*.jar` als Artefakt entsteht (der Rückfall in `README-VM.md` Abschnitt 9 braucht es); `image:build` und
`scripts/ci-image.sh` legen vorher `deployment/.env` aus der Vorlage an, weil `${ADMIN_INITIAL_PASSWORD:?…}` schon beim Einlesen der
Compose-Datei geprüft wird; `nginx -t` läuft nur mit `--add-host backend:127.0.0.1` durch, weil nginx den `proxy_pass`-Host beim
Prüfen auflöst. Container **starten** ging am Nachmittag des 04.09. auf diesem Mac zeitweise nicht (`docker run` blieb in
`Created` stehen); am Abend liefen `docker compose up -d --build` und die Einzelbefehle durch — Belege in Abschnitt 10.

**Korrektur B4 (04.09. abends, nach der Prüfung).** Drei Änderungen, alle gemessen:
1. **`mock` startete nie.** `stoplight/prism:5` (5.15.10) brach beim Mehrprozess-Start ab und lief in Endlosschleife.
   Jetzt `image: stoplight/prism:5.14.2` (dieselbe Version wie `api-contract/package.json`), `command: mock -m false …`
   und `platform: linux/amd64`.
2. **`deploy:vm` hätte die `.env` der VM gelöscht.** `rsync --delete` räumt weg, was nicht im Checkout liegt — und
   `deployment/.env` (Passwort) und `deployment/backups/` stehen im `.gitignore`. Der nächste `docker compose up` wäre mit
   „ADMIN_INITIAL_PASSWORD fehlt in deployment/.env" gescheitert. Beide Pfade sind jetzt `--exclude`; `README-VM.md` sagt es
   auch in Worten. Auf der VM selbst nicht ausgeführt (kein Zugang) — mit zwei lokalen Ordnern nachgestellt.
3. **`.dockerignore` ergänzt** um `**/.env.local`, `**/.env.*.local`, `**/*.local`. Der Web-Build kopiert die Repo-Wurzel
   und liest `.gitignore` nicht; Vite lädt `.env.local` in jedem Modus. Wer `VITE_MOCK=true` lokal gesetzt hatte, hätte ein
   Web-Abbild mit Attrappe statt echtem Client gebaut.
4. **`scripts/dev-backend.sh`** liest jetzt `deployment/.env` (DB-Name, Benutzer, Passwort) und kennt `BACKEND_PORT`; die
   fest verdrahteten Werte widersprachen `.env.example`. Dazu die bash-3.2-Korrektur aus Abschnitt 10.

### B5 (optional, nur mit `args.android`) — Android-Gerüst

`android-starter/`: Kotlin, Compose, Material 3, Retrofit, DataStore nach `08-Build/SPEZIFIKATION.md` Abschnitte 5 und 9 und `backbone/android.md`;
nur Projektdateien, Manifest, Application, SetupScreen mit Serveradresse + Verbindungstest gegen `/api/health`, README. Hier nicht kompilierbar —
statisch prüfen (`08-Build/tools/android-precheck.sh`), „nicht kompiliert" kennzeichnen. Berührt keine andere Datei.

**Umfang mit Absicht klein.** `TimetableApi` kennt nur `GET api/health`. Login, `/api/me`, die eigene Woche und das
Zeitraster stehen in `SPEZIFIKATION.md` und sind Karten der Rolle 6 (T-601 ff.), nicht Teil des Gerüsts; die fehlenden
Signaturen sind im Javadoc der Schnittstelle namentlich aufgezählt, damit niemand sie suchen muss.

## 10. Belege (Stand 04.09. abends, alles auf diesem Mac gemessen)

**Zuerst das Wichtigste: noch ist nichts committet.** Der Branch `vorlage/start` zeigt auf den Team-Commit `1552b63`; die
ganze Arbeit von B1–B5 liegt als 72 geänderte und 47 neue Pfade nur im Arbeitsbaum. Ein `git clone … -b vorlage/start`
liefert deshalb **nur** das Team-Gerüst (`backend/`, `frontend/`, `LICENSE`, `README.md`) — kein STARTSTAND.md, kein
`api-contract/`, kein `deployment/`, keine `scripts/` (nachgestellt, Ausgabe gesehen). **Solange nichts committet ist:
den Arbeitsbaum kopieren, nicht klonen** (`cp -a 08-Build/starter <ziel>`). Klont man trotzdem und legt die Dateien
darüber, liegen die per `git mv` verschobenen Altpfade doppelt: zwei `clientController`-Beans (`ConflictingBeanDefinitionException`)
und Vite nimmt `vite.config.js` statt `vite.config.ts`.

Nächster Schritt (Philipp): Arbeitsbaum prüfen, `git add -A`, auf `vorlage/start` committen — die Umbenennungen sind
gestaged, die 47 neuen Pfade noch nicht. Erst danach ist Klonen der richtige Weg für das Team.

### 10.1 Beleg-Tabelle

| # | Befehl | Ausgabe (gekürzt) | Datum |
|---|---|---|---|
| 1 | `scripts/ci-all.sh` | `ok scripts/ci-contract-lint.sh` · `ok scripts/ci-backend-test.sh` · `ok scripts/ci-frontend-build.sh` · `ok scripts/ci-image.sh` · `ci-all: alles gruen.` | 04.09. |
| 2 | `scripts/ci-contract-lint.sh` | `Woohoo! Your API description is valid.` — 0 Fehler, 1 Warnung (`operation-4xx-response` an `GET /api/health`) | 04.09. |
| 3 | `scripts/ci-backend-test.sh` | `BUILD SUCCESSFUL in 25s`; `test` 12 Fälle, `integrationTest` 11 Fälle, 0 Fehler (XML-Berichte gezählt). Zweitlauf 21:16 am selben Pfad: der Bind-Mount trägt auch den **Leerzeichen-Pfad** `…/Software Projekt/…`; ohne `--rerun` meldet Gradle nur `UP-TO-DATE`, mit `--rerun` lief `integrationTest` echt durch (`BUILD SUCCESSFUL in 22s`) | 04.09. |
| 4 | `scripts/ci-frontend-build.sh` | `tsc` ohne Fehler, ESLint ohne Fehler, Vitest `Test Files 3 passed`/`Tests 27 passed`, `vite build ✓ built in 916ms` | 04.09. |
| 5 | `scripts/ci-image.sh` | `stundenplan-backend Built`, `stundenplan-web Built` | 04.09. |
| 6 | `gradle integrationTest --rerun` im Container mit gemountetem Docker-Socket (ohne Profil `ci`) | `Testcontainers version: 2.0.5`, `Creating container for image: postgres:16-alpine`, `BUILD SUCCESSFUL`, 11 Fälle grün | 04.09. |
| 7 | `BACKEND_PORT=8089 scripts/dev-backend.sh` ohne Terminal (`< /dev/null`) | `Successfully applied 4 migrations`, `Started BackendApplication in 4.987 seconds`; kein `unbound variable` | 04.09. |
| 8 | `curl -s localhost:8089/api/health` | `{"status":"UP","schoolName":"Gymnasium Musterstadt"}` | 04.09. |
| 9 | `curl -X POST …/api/auth/login -d '{"username":"admin","password":"falsch"}'` | 401 `{"detail":"Benutzername oder Passwort ist falsch.",…,"type":"urn:pushnpull:problem:unauthenticated"}` | 04.09. |
| 10 | `curl -H 'Authorization: Bearer quatsch' localhost:8089/api/me` | 401 `{"detail":"Bitte erneut anmelden: das Token fehlt oder ist abgelaufen.",…}` | 04.09. |
| 11 | `VITE_PROXY_TARGET=http://localhost:8089 npm run dev -- --port 5178`, dann `curl localhost:5178/api/health` | `{"status":"UP","schoolName":"Gymnasium Musterstadt"}`; `/login` → 200 | 04.09. |
| 12 | `cd deployment && docker compose up -d mock` (Bild 5.14.2, `-m false`) | `Prism is listening on http://0.0.0.0:4010`, Dienst bleibt `Up` | 04.09. |
| 13 | `curl -s localhost:1501/api/teachers` ohne Header | 401 `{"type":"urn:pushnpull:problem:unauthenticated",…}` | 04.09. |
| 14 | dieselbe Anfrage mit `-H 'Authorization: Bearer x'` | `[{"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,…` | 04.09. |
| 15 | `cd deployment && docker compose up -d --build` | vier Dienste `Up`; `localhost:1500/login` → 200; `localhost:1500/api/health` über nginx aus dem Backend; `localhost:1501/api/health` aus Prism | 04.09. — **seit 05.09. drei Dienste**, die Attrappe steht im dev-Zusatz |
| 16 | Volume aus 3.1 (Profil `dev`), danach 3.4 (Profil `prod`, `ADMIN_INITIAL_PASSWORD=bitte-aendern`) | Login `bitte-aendern` → **401**, Login `admin` → **200** | 04.09. |
| 17 | dasselbe nach `docker compose down -v` | Login `bitte-aendern` → **200**, Login `admin` → **401** | 04.09. |
| 18 | `rsync -az --delete … --exclude deployment/.env --exclude deployment/backups` zwischen zwei Ordnern | `.env` und `backups/t.sql.gz` überleben; ohne die zwei `--exclude` sind beide weg | 04.09. |
| 19 | `docker build` mit `COPY frontend/ ./` und `ls -a` im Bild | `.env.local` fehlt im Kontext, `.env.mock`/`.env.prism`/`.env.demo` sind da; ohne `.dockerignore` ist `.env.local` dabei | 04.09. |
| 20 | `js-yaml .gitlab-ci.yml` | lädt; Jobs `contract:lint, frontend:build, backend:test, backend:integration, image:build, deploy:vm`; die rsync-Zeile faltet sich zu einer Zeile mit beiden neuen `--exclude` | 04.09. |
| 21 | `/bin/bash -c 'set -u; A=(); echo "${A[@]}"'` (macOS-Systembash 3.2.57) | `unbound variable`, Exit 127 — mit `${A[@]+"${A[@]}"}` Exit 0. So steht es jetzt in `dev-backend.sh` | 04.09. |
| 22 | `curl -s -i localhost:8080/` | `Server: Apache/2.4.66 (Unix)` — deshalb `BACKEND_PORT` | 04.09. |

### 10.2 Was weiterhin ungeprüft ist

| Was | Warum | Wer prüft es wann |
|---|---|---|
| GitLab-Pipeline im CAU-Runner | kein Runner erreichbar; nur die Befehle sind geprüft, nicht das Runner-Image, der Netzzugang und Docker-in-Docker | Rolle 7, T-703, Mo 08.09. |
| `deploy:vm` auf der VM | kein Zugang; die rsync-Regel ist lokal nachgestellt, der Deploy selbst nicht gelaufen | Rolle 7, T-702/T-704 |
| Prism-Bild auf x86 | der Mehrprozess-Fehler ist nur auf Apple Silicon (unter Rosetta) gemessen; ob er auf der VM auch aufträte, ist offen — mit 5.14.2 und `-m false` stellt sich die Frage nicht | Rolle 7 beim ersten VM-Start |
| `android-starter/` | kein JDK, kein Android-SDK auf diesem Rechner: kein Gradle-Sync, kein Compile, kein Test | Rolle 6, T-601, Mo 08.09. |
| Frontend gegen das echte Backend über die Anmeldung hinaus | es gibt erst health, login, logout, `/api/me` | Rolle 5, T-502 |

### 10.3 Zwei Fallen fürs Protokoll

- **Gradle hält `integrationTest` für `UP-TO-DATE`**, wenn sich nur Umgebungsvariablen ändern (z. B. Profil `ci` → Testcontainers).
  Dann `--rerun` benutzen, sonst prüft man den vorigen Lauf.
- **`docker stop` kehrt zurück, bevor `--rm` aufgeräumt hat.** Wer sofort einen Container gleichen Namens startet, trifft den
  halb abgeräumten Vorgänger; `scripts/ci-backend-test.sh` wartet deshalb jetzt, bis der Name frei ist (einmal als
  „rejecting connections" aufgetreten).
