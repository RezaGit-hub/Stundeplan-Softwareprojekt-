# ANLEITUNG — Vollausbau als Referenz benutzen

**Was das hier ist:** eine vollstaendig gebaute Stundenplanung (Backend, Web-App, Vertrag, Pipeline,
Betrieb) auf dem Branch `vollausbau`. Sie ist **Referenz fuer das Team, nicht die Abgabe**. Wer auf der
Startschablone baut, holt sich hier ein fertiges Modul, dessen Tests und dessen Vertragszeile — und
schreibt nicht dieselbe Sache ein zweites Mal.

Drei Dokumente daneben, die diese Anleitung nicht wiederholt:

| Datei | Inhalt |
|---|---|
| [`docs/VERTRAG.md`](docs/VERTRAG.md) | API v1, Modulgrenzen, Regel „wie ein Modul den Vertrag erweitern darf", Aenderungen je Modul |
| [`docs/SOLVER-PLAN.md`](docs/SOLVER-PLAN.md) | Timefold-Modell, alle 20 Constraints im Code, Uebersetzer, Betrieb des Laufs, Tests |
| [`docs/ABNAHME-CHECKLISTE.md`](docs/ABNAHME-CHECKLISTE.md) | Testdurchlauf und die 17 Testfaelle als pruefbare Schritte, dazu das Ergebnis vom 05.09. |

---

## 1. In fuenf Minuten starten

Voraussetzung: Docker laeuft. **Ein JDK braucht man nicht** — Java laeuft im Container. Fuer den
Frontend-Entwicklungsmodus zusaetzlich Node ≥ 20.19 (oder ≥ 22.12).

### Der eine Befehl

```bash
cd deployment
cp -n .env.example .env
# PFLICHT: eigenes Kennwort setzen. Mit dem Platzhalter `bitte-aendern` bricht das Backend
# im Profil prod ab (Befund SICHERHEIT-6, 05.09.) — der Stack startet dann NICHT.
$EDITOR .env            # ADMIN_INITIAL_PASSWORD=<eigener Wert>
docker compose --profile beispieldaten up -d --build
```

Danach:

| Was | Adresse |
|---|---|
| Web-App | <http://localhost:1500> |
| Gesundheitsauskunft | <http://localhost:1500/api/health> → `{"status":"UP","schoolName":"…"}` |
| Konto Planung | `admin` mit dem Wert von `ADMIN_INITIAL_PASSWORD` aus `deployment/.env` |
| Konten Lehrkraft | keine ab Werk — Planung legt sie unter **Einstellungen → Benutzerkonten** an |

`.env.example` liefert den Platzhalter `ADMIN_INITIAL_PASSWORD=bitte-aendern`. **Seit dem 05.09.
startet der Stack damit nicht mehr** (Befund SICHERHEIT-6): `docker-compose.yml` setzt
`SPRING_PROFILES_ACTIVE: prod`, dort ist `app.admin-allow-placeholder-password=false`, und
`AdminBootstrap` bricht mit `IllegalStateException` ab — der Container `backend` endet, `db` und `web`
laufen weiter. Der Grund: der Wert steht im Repository und ist damit oeffentlich bekannt.
Also **vor** dem ersten `up` einen eigenen Wert eintragen. Nur die Profile `dev` und `ci` lassen den
Platzhalter durch, dort mit einer WARN-Zeile im Log.
Wer sich vertan hat: `docker compose down -v`, `.env` korrigieren, neu starten — solange noch kein Konto
in der Datenbank steht, wird `AdminBootstrap` beim naechsten Start erneut ausgefuehrt.

**Gemessen** am 05.09. (Mac, Docker Desktop 29.1.2, Abbilder im Cache): `up` kehrt nach 34 s zurueck, die
Beispieldaten sind rund 25 s spaeter fertig gerechnet — 12 Klassen, 24 Lehrkraefte, 18 Raeume, 360 Stunden
im aktiven Plan. Der **erste Bau ohne Cache** ist ungeprueft und dauert Minuten.

**Ohne `--profile beispieldaten`** startet derselbe Stack mit leerer Datenbank — so faengt eine Schule an.
Es laufen drei Dienste (`db`, `backend`, `web`); nach aussen veroeffentlicht ist nur `web`.

### Aufhoeren, neu anfangen

```bash
cd deployment
docker compose down          # Container weg, Daten bleiben
docker compose down -v       # Daten ebenfalls weg (danach wieder leere Datenbank)
```

### Entwickeln statt nur schauen

```bash
# Backend im Container, Datenbank daneben, Neustart bei jeder Aenderung
(cd deployment && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db)
scripts/dev-backend.sh                       # http://localhost:8080

# Web-App mit Vite
cd frontend && npm ci && npm run dev          # http://localhost:5173, spricht das Backend oben
cd frontend && npm run dev:mock               # dasselbe ohne Backend: Attrappe im Browser, Konto admin/admin
```

Auf einem Mac hoert auf **Port 8080 schon der mitgelieferte Apache** (`curl -s -i localhost:8080/` zeigt
`Server: Apache`). Dann ausweichen: `BACKEND_PORT=8089 scripts/dev-backend.sh` und im Frontend
`VITE_PROXY_TARGET=http://localhost:8089 npm run dev`.

Die Prism-Attrappe aus dem Vertrag ist **kein Teil des Produkts** und steht deshalb in
`docker-compose.dev.yml`: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mock`
(Port 1501) oder `cd api-contract && npm ci && npm run mock`.

**Nach F5 bleibt man angemeldet.** Seit F-5a (05.09.) liegt das Token im `localStorage` und gilt 30 Tage
(einstellbar ueber `app.auth.token-ttl` bzw. `AUTH_TOKEN_TTL`). Abgemeldet wird ueber die Schaltflaeche im
Benutzermenue. Der urspruengliche Auth-Fluss 5 in `backbone/api.md` („Token nur im Speicher") ist damit
ueberholt; der Nachtrag steht dort und in `docs/VERTRAG.md` (F-5a).

---

## 2. Wie die Plattform gebaut ist

### Module

Zehn Backend-Module unter `de.wsp3_1`, je Modul die drei Schichten `api → domain → persistence`. Ein
ArchUnit-Test (`backend/src/test/java/de/wsp3_1/ArchitectureTest.java`) haelt die Richtung fest: `api` sieht
keine Entity, `domain` sieht kein `api`, `solver` sieht keine Datenbank.

| Modul | Verantwortung | Pfade |
|---|---|---|
| `shared` | `Weekday`, `Role`, `RuleType`, `RuleScope`, `Severity`, `CurrentUser`, Fehlerbehandlung (RFC 9457) | — |
| `config` | eine Zeile Systemkonfiguration, Schulname, Rechenzeit | `/api/health`, `/api/config` |
| `auth` | Konten, Token (SHA-256, 30 Tage — `AUTH_TOKEN_TTL`), Pfadregeln, `AdminBootstrap` | `/api/auth/*`, `/api/me*`, `/api/users*` |
| `masterdata` | Lehrkraft, Fach, Raum, Klasse, Klassenfach, Zeitfenster; Warnungen; Loeschen-vs-Deaktivieren | `/api/teachers`, `/api/subjects`, `/api/rooms`, `/api/classes`, `/api/timegrid` |
| `rules` | Regelwerk, Voreinstellung je Bundesland, `RuleResolver` | `/api/rules**` |
| `solver` | Timefold: Modell, 20 Constraints, Uebersetzer, Lauf — **ohne Datenbank** | — |
| `planning` | Entwurf, aktiver Plan, Konfliktpruefung, `SolveService` | `/api/timetable`, `/api/draft**` |
| `substitution` | Ereignisse, Ausnahmen, Wochenaufloesung, Vertretungsoptionen, Schuelersicht, Ferien | `/api/timetable/week`, `/api/me/timetable/week`, `/api/me/class-week`, `/api/events**`, `/api/substitutions/**`, `/api/exceptions**`, `/api/holidays**` |
| `changelog` | Aenderungsverlauf, nur lesbar | `/api/changelog` |
| `sampledata` | Beispielbestand samt gerechnetem Plan | `POST /api/sample-data` |

Die Web-App spiegelt das: `src/api` (ein `client.ts`, `hooks.ts` mit TanStack Query, `types.ts` als
feldgenaue Abschrift des Vertrags), `src/app` (Router, Shell, Anmeldung), `src/pages` je Route,
`src/components/ui` die 29 shadcn-Bausteine, `src/mock` die Attrappe fuer `npm run dev:mock`.
Kein Zustand-Store: was vom Server kommt, haelt TanStack Query; was ein Formular haelt, haelt das Formular.

### Datenfluss

```
Grunddaten (masterdata)  ──►  Entwurf (planning)  ──►  Solver (solver)  ──►  Entwurf mit Ergebnis
                                      │                                              │
                              Konfliktpruefung                                  „Annehmen"
                                (bei jedem GET)                                       ▼
                                                                        aktiver Plan (timetable)
                                                                                      │
Ereignisse + Ausnahmen (substitution) ────────────────────────────────────────────────┤
                                                                                      ▼
                                                                    WeekResolver ► Wochensichten
                                                                    (Klasse · Lehrkraft · Raum ·
                                                                     Schule · /mein-plan)
                                                                                      │
                                                              jede Schreibaktion ─────► Aenderungsverlauf
```

Zwei Saetze dazu, die man kennen muss. Erstens: **Konflikte, Warnungen und Vertretungsbedarfe werden nie
gespeichert** (E-31) — jeder GET rechnet sie neu, und sie blockieren nie eine Eingabe. Zweitens: es gibt
genau einen Entwurf und genau einen aktiven Plan; „Annehmen" schiebt den einen auf den anderen und
archiviert den bisherigen.

### Der Solver in zehn Saetzen

1. Der Solver ist **Timefold 2.6.0** und liegt in `de.wsp3_1.solver`; er kennt weder Spring Data noch JPA.
2. Eingabe ist `SolveInput`: Stunden, **alle** Zeitfenster, **alle** Raeume, Regeln, gesperrte und bevorzugte Zeiten — nur Records.
3. `SolverTranslator.toSolution(...)` baut daraus die Loesung, `toResult(...)` macht Platzierungen daraus; beides statisch und ohne Spring testbar.
4. Geplant werden je Stunde zwei Variablen: **Zeitfenster und Raum**.
5. Eine festgelegte Stunde traegt `@PlanningPin` und bleibt, wo sie steht — das ist Anwendungsfall W-9.
6. Es sind **20 Constraints**: sieben harte (Doppelbelegung Raum, Lehrkraft, Klasse; gesperrte Zeit; falsche Raumart; bewegliche Stunde in einer Pause; bewegliche Stunde in einem inaktiven Raum), eine mittlere (unverplante Stunde) und die Regelwerksregeln als Zwillinge hart/weich, dazu zwei weiche Wuensche (bevorzugte Zeit, Fach ueber die Woche verteilen).
7. Die Regeln stehen **nicht im Code**, sondern kommen als `RuleFact` aus `rules.domain.RuleResolver` — derselben Quelle, aus der auch die Konfliktpruefung liest; sonst wuerden Solver und Oberflaeche verschiedene Dinge sagen.
8. Der Lauf ist **asynchron**: `POST /api/draft/solve` antwortet 202 ohne Rumpf, `SolverRunner` rechnet im Hintergrund, den Stand holt die Oberflaeche alle 2 s ueber `GET /api/draft`.
9. Die Rechenzeit steht in der Konfiguration (`maxSolveSeconds`, 1–600) und wird je Lauf als Override gesetzt; `DELETE /api/draft/solve` bricht ab und **behaelt das beste Ergebnis bis dahin**.
10. Gespeichert wird nicht vom Solver, sondern von `planning.domain.DraftService` in den Entwurf — erst „Annehmen" macht daraus den aktiven Plan.

**Gemessen** (voller Beispielbestand, 360 Stunden, Rechenzeit 10 s): Lauf endet nach 10,7–10,9 s,
357 Stunden belegt, 3 offen, keine Doppelbelegung von Raum, Lehrkraft oder Klasse.

Alles Weitere — Constraint-Code, Gewichte, Vorbedingungen, Abbruch, Tests — steht in
[`docs/SOLVER-PLAN.md`](docs/SOLVER-PLAN.md). Der **Nachtrag ganz oben** in dieser Datei gilt vor dem
uebrigen Text: acht Punkte (SV-1 bis SV-8) haben sich beim Bauen als anders herausgestellt als geplant.

---

## 3. Abnahmestand

**Ergebnis des Testdurchlaufs vom 05.09. (08:00–08:15, frischer Klon, so wie die Betreuenden am 24.09.):
nicht bestanden.** Alles Web- und Server-seitige ist bestanden, jede Zeile mit Beleg; offen ist die
Android-App. Danach lief eine Korrekturphase (08:15–09:00), die acht Befunde bearbeitet hat.

| Bereich | Ergebnis |
|---|---|
| Testdurchlauf Kapitel 1.1–1.10 | bestanden bis auf I-1 (Klon), I-4/I-5 und K-1/K-2 (App) |
| 17 Testfaelle T-W1.1 … T-A3.1 | Server- und Web-Anteil **alle bestanden**; App-Anteil von T-A1.1, T-A2.1, T-A3.1 offen |
| API-Durchlauf des Abnahmelaufs | 43/43 Pruefungen gruen, jede Anfrage mit Antwort protokolliert; wiederholbar mit `scripts/abnahme.sh` (83 Zusicherungen des Leitszenarios) |
| Browser-Durchlauf (`frontend/scripts/abnahme.spec.ts`) | 43/43 gruen in 1,4 min, 0 Seitenfehler, alle 399 Anfragen an `localhost` — **keine externe Adresse** |
| Nichtfunktionale Musskriterien | Zugriffsschutz, Passwort-Hash, Rueckmeldung, Dauerhaftigkeit, Nachvollziehbarkeit, eigenstaendiger Betrieb, Port-Aenderung ohne Neubau: **alle ok** |

**Regressionslauf nach der Korrektur** (jede Zeile gemessen, Ausgaben in `docs/abnahme/korrektur-05-09.md`):

| Lauf | Ergebnis |
|---|---|
| `scripts/ci-backend-test.sh` | BUILD SUCCESSFUL in 3 min 12 s, **135 Unit- + 196 Integrationstests**, 0 Fehler (Lauf 05.09. 22:00) |
| `npm run typecheck` · `npm run lint` · `npm test` (frontend) | gruen · gruen · 89 Tests in 11 Dateien gruen |
| `npm run lint` (api-contract) | „Your API description is valid", 1 vorbestehende Warnung |
| `scripts/ci-android-build.sh` | Geruest 05.09. frueh: BUILD SUCCESSFUL in 19 min 14 s, 3 Unit-Tests. Vollausbau mittags: 41 min 35 s, 48 Tests. **Nach Einarbeitung der Pruefbefunde (05.09. nachmittags):** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` BUILD SUCCESSFUL in 10 min 50 s (warmer Cache, x86-Emulation), **64 Unit-Tests in 6 Klassen gruen** (ApiFactory 6, EntryKindUi 9, WeekView 9, OfflineCache 6, TimetableRepository 17, TimetableViewModel 17), 0 Fehler, **Lint „No issues found"**, Debug-APK 33.380.315 Byte -> `app/stundenplan.apk` (gleiche SHA-256 wie `app-debug.apk`) |
| `docker compose up -d --build` | drei Dienste `Up`, `db`/`backend` healthy, `/login` 200 |

### Offene Punkte, ehrlich

1. **Nichts ist committet.** `git clone` dieses Ordners liefert nur die Startschablone (`backend/`,
   `frontend/`, `LICENSE`, `README.md`) — der ganze Vollausbau steht uncommitted im Arbeitsbaum. Das ist
   Absicht: Philipp prueft und committet. Wer „wie die Betreuenden" pruefen will, kopiert den Arbeitsbaum
   per `rsync` (ohne `.git`, `node_modules`, `build`, `dist`, `deployment/.env`).
2. **Die Android-App ist gebaut, aber nie auf einem Geraet gesehen.** `android-starter/` enthaelt seit dem
   05.09. (mittags) alle vier Screens — Einstellungen mit Verbindungstest und Anmeldung, Tagesansicht,
   Wochenansicht, Stundendetail — plus Offline-Stand, Farblogik je `kind` und den Hinweis „Heute geaendert".
   Belegt ist das auf der JVM (Unit-Tests: Zustandsautomat, Repository gegen MockWebServer mit echten
   Serverantworten, Mapping, Cache) und durch `assembleDebug` im Container. **Ungeprueft:** das Bild auf
   Emulator/Geraet, Pull-to-refresh, Bottom Sheet, **Wischen und Kalender** in der Hand,
   T-A1.1/T-A2.1/T-A3.1 als Klickdurchlauf —
   dafuer fehlt hier Android Studio. `app/stundenplan.apk` traegt die **Debug-Signatur** (installierbar,
   fuer die Abgabe neu signieren). **Abschnitt 3d nennt APK-Pfad, Build-Befehl, Installation und den
   Belegstand je Testfall.** Der Beispielbestand legt keine Lehrkraft-Konten an, nur Klassenkonten; auf dem
   laufenden Stack steht genau eines (`meyer`, id 2), dessen Passwort hier nicht bekannt ist — vor dem
   Klickdurchlauf neu setzen (Passwort mindestens 8 Zeichen). Wichtig fuer Rolle 6: mit `composeBom 2026.08.00`
   **scheitert** der Build (Compose 1.12.0 verlangt AGP ≥ 9.1.0 und `compileSdk 37`); mit `2026.06.01` laeuft er.
   **Nachmittags 05.09. eingearbeitet** (Pruefbefunde Claude + Codex): nur ein Abruf gleichzeitig, damit beim
   Blaettern die zuletzt *angeforderte* Woche gewinnt; Offline-Stand faellt bei 401, Anmeldung und
   Adresswechsel weg (Zugriffsschutz am Geraet); Adresswechsel meldet ab; `/api` am Ende der Serveradresse
   wird entfernt; keine Systemsicherung (`allowBackup="false"` plus Regeln fuer Android 11 und 12+); keine
   HTTP-Weiterleitungen; Banner in Schulsprache („Keine Verbindung — Stand: …"); Kalender und Wischen zum
   Tageswechsel; Stundenraster wird mit jedem Abruf erneuert; Stundendetail rollbar; Zusatzstunden im
   selben Zeitfenster bekommen verschiedene Listenschluessel; die Pause gilt je Tageszelle statt je Zeile.
   Was die App **auf einem bestimmten Tag** oeffnet, steht in `android-starter/README.md` („Auf einen
   bestimmten Tag stellen") — wichtig fuer T-A1.1 und T-A2.1.
3. **`bitte-aendern` steht im Pflichtenheft.** Die Tabelle der Abgabeangaben in `testdurchlauf.tex` nennt
   das Platzhalterkennwort. Die Datei liegt im Repository `SoSe2026_WSP3_1`; der fertige Ersatztext steht
   hier unter `docs/PFLICHTENHEFT-NACHTRAG.md` (P-5).
4. **`testdurchlauf.tex` weicht an sieben Stellen vom Produkt ab** (unter anderem: Handaufbau geht nur im
   Entwurf, danach annehmen; die Auswahlliste der Lehrkraefte beim Einplanen gibt es nicht, weil die
   Lehrkraft aus der Fachzuordnung folgt). Ersatztexte P-1 bis P-7 in `docs/PFLICHTENHEFT-NACHTRAG.md`.
5. **Erledigt am 05.09. (F-5a): die Sitzung ueberlebt das Neuladen.** Das Token liegt im `localStorage`,
   die Laufzeit betraegt 30 Tage (`AUTH_TOKEN_TTL`), abgemeldet wird ueber das Benutzermenue. Der frühere
   Punkt „Sitzung nur im Seitenspeicher (F-5)" ist damit gegenstandslos; wo er noch steht, ist die Doku alt.
6. **Entwurf bearbeiten und Berechnung starten sind nicht gegeneinander serialisiert.** Zwei gleichzeitige
   Berechnungen schliessen sich aus (409 `solving`), eine Aenderung neben einem startenden Lauf nicht — sie
   geht dann verloren. Nichts wird beschaedigt. Der vollstaendige Weg waere ein Zeilenschloss auf
   `timetable` (`SELECT … FOR UPDATE`); fuer eine Anwendung, in der eine Person plant, ist er nicht gebaut.
7. **Offene Stunden im angenommenen Plan.** Ein Entwurf darf Stunden ohne Zeitfenster enthalten; „Annehmen"
   uebernimmt sie und sie stehen in keiner Wochensicht. Seit dem 05.09. nennt der Dialog die Zahl **vor**
   dem Klick und die Antwort nennt sie in `warnings[]` — verworfen werden sie bewusst nicht.
8. **Nie auf der VM gelaufen:** `scripts/deploy-vm.sh` (mit `DRY_RUN=1` zeigt es jeden Befehl), und der
   erste Bau ohne Docker-Build-Cache ist ungemessen. Ob der CAU-Runner Docker-in-Docker kann, ist offen;
   der Job `runner:check` in `.gitlab-ci.yml` beantwortet das in einem manuellen Klick.
9. **Die App zeigt Kuerzel, das Pflichtenheft erwartet Langnamen.** T-A2.1 (`produktfunktionen.tex`)
   verlangt in der Tagesansicht „Mathematik, Klasse 7a, R204"; `EntryDto` traegt laut `openapi.yaml` nur
   `subjectShort`, `roomShort`, `teacherShort` — die App zeigt also „Ma". Zwei Wege, einer muss vor dem
   Testdurchlauf gegangen sein:
   - **Vertrag additiv erweitern** — `EntryDto` um die optionalen `subjectName`, `roomName`, `teacherName`,
     das Backend fuellt sie aus den Stammdaten. **Zustaendig: Rolle 4** (`api-contract/openapi.yaml` +
     `WeekAssembler`), **Termin: bis Fr 11.09.**, damit Rolle 6 es vor dem Klickdurchlauf sieht. Die App ist
     fertig dafuer: `Dtos.kt` kennt die drei Felder als `String? = null`, `EntryDetailSheet` zeigt
     `subjectName ?: subjectShort` — es genuegt, dass der Server sie mitschickt.
   - **Oder Pflichtenheft-Text anpassen** (Ersatztext in `docs/PFLICHTENHEFT-NACHTRAG.md`, Zustaendig:
     Philipp) und im **Testprotokoll vermerken, dass Kuerzel gezeigt werden**.
   Bis zur Entscheidung gilt: Kuerzel, und der Vermerk im Protokoll.
10. **Kein TLS auf dem ganzen Weg.** Der Stack spricht HTTP; Klassenkennwoerter und die 30-Tage-Token
    gehen im Klartext ueber das Netz. Im Rueckgrat ist TLS bewusst abgegrenzt („Aufgabe der
    Schul-Infrastruktur"). Drei Wege stehen in `deployment/README-VM.md` Abschnitt **11a** zur Wahl; ohne
    Entscheidung des Auftraggebers ist nur `AUTH_TOKEN_TTL=PT12H` auf der VM sinnvoll.
    (Befund SICHERHEIT-2, Audit V-4.)
11. **Konten lassen sich nicht sperren oder loeschen.** Es gibt weder `DELETE /api/users/{id}` noch ein
    Merkmal `active` am Konto — wer einmal ein Konto hat, behaelt es. Beides erweitert **Vertrag und
    Datenmodell** (`produktdaten.tex` kennt kein Aktiv-Merkmal), ist also eine Pflichtenheftfrage.
    Fix-Skizze in `docs/VERTRAG.md` §5. (Befund SICHERHEIT-4, Audit V-5.)
12. **Die Rolle `STUDENT` steht nicht im Pflichtenheft.** `zielbestimmungen.tex` Z. 220 sagt „Kein Eltern-
    und kein Schuelerzugang", Z. 140 sagt, Kennwoerter werden „nirgends im Klartext angezeigt" — beides
    widerspricht dem gebauten Klassenkonto und der einmaligen Klartextausgabe der Erstpasswoerter. Zwei
    Wege stehen in `docs/PFLICHTENHEFT-NACHTRAG.md` **P-8**; entschieden wird nach Rueckfrage **R5**
    (Interview 2, Di 08.09.). (Befund PFLICHTENHEFT-2, Audit V-1.)
13. **Blockung/Doppelstunde ist ein Sollkriterium und nicht gebaut.** `zielbestimmungen.tex` Z. 181 fuehrt
    sie als **Soll**, nicht als Kann. Entweder Karte `DOUBLE_LESSON` (Fact + Constraint R4) bauen oder die
    Abwahl im Pflichtenheft begruenden — Nachtragspunkt **P-10**. (Befund PFLICHTENHEFT-5, Audit V-2.)

---

## 3b. Schuelersicht (Klassenkonto)

Seit dem 05.09. (E-50, Migration `V7`) gibt es eine dritte Rolle: **`STUDENT` = Klassenkonto**. Ein Konto je Klasse,
Benutzername = Klassenkuerzel, und es sieht genau eine Seite — den Plan der eigenen Klasse.

**Warum ein Konto je Klasse und nicht je Person?** Weil die Recherche
`07-Konzept/recherche/sammelzugang-schueler.md` (Abschnitt „Bewertung") genau dazwischen landet: ein
schulweites Sammelkonto mit Sicht auf alle Klassen ist nicht zulaessig, personalisierte Zugaenge (SSO)
sind nicht beauftragt, und **klassenweise getrennte Zugangsdaten** sind die von LDI NRW, LfDI RLP und
LfDI Sachsen ausdruecklich tolerierte Minimalloesung. Abwesenheitsgruende sind ueberall unzulaessig.

### Was ein Klassenkonto sieht — und was nicht

| | |
|---|---|
| Route | `/klassenplan` — dasselbe Wochenraster wie `/plan` in der Sicht Klasse, nur lesend, mit Wochenwahl, Legende, Popover und Druckansicht |
| Kachel | Fach, Raum, Lehrkraft-**Kuerzel**; Farbe zeigt die Art der Aenderung |
| Popover | Fach, Klasse, Raum, Kuerzel, Art der Aenderung (`Vertretung offen`, `Vertreten`, `Entfaellt`, `Verlegt`) |
| **nicht** sichtbar | **Anlaesse** („Krankmeldung") — `reasons` ist in dieser Sicht immer leer; Klarnamen von Lehrkraeften; jede fremde Klasse; jede Lehrkraft-Sicht |
| erlaubte Endpunkte | `GET /api/health`, `POST /api/auth/login`, `DELETE /api/auth/token`, `GET /api/me`, `GET /api/timegrid`, `GET /api/me/class-week` — alles andere ist **403**, auch `GET /api/me/timetable/week` und `PUT /api/me/password` |

Gefiltert wird **im Server** (`substitution/domain/StudentView.java`), nicht im Browser: sonst kaemen die
Anlaesse trotzdem ueber die Leitung. Die Klasse kommt aus dem Token — `GET /api/me/class-week` nimmt nur
ein `date`, keine Klassenkennung. Ein Konto ohne Klasse (die Planung selbst) bekommt dort 404.

### Konten anlegen (Planung)

**Einstellungen → Benutzerkonten → „Konten fuer alle Klassen anlegen".** Der Dialog legt fuer jede
**aktive** Klasse **ohne** Konto ein Konto an und zeigt die Zugangsdaten als Tabelle mit „Kopieren" und
„Drucken". Das Passwort steht **genau einmal** dort im Klartext; danach kennt der Server nur den
BCrypt-Hash. Wer eines verliert, setzt in derselben Liste ueber „Passwort setzen" ein neues — das beendet
zugleich alle Sitzungen dieses Kontos. Der Knopf ist idempotent: ein zweiter Klick legt nichts an.

Einzelne Klasse: **„Neues Konto" → Kontotyp „Klasse"**, Klasse waehlen, Benutzername wird vorgeschlagen.

Der Text, der beim Verteilen mitgeht (steht auch im Dialog):

> Ein Konto je Klasse. Passwort in der Klasse weitergeben, halbjaehrlich aendern. Die Sicht zeigt nur die
> eigene Klasse, Lehrkraefte als Kuerzel, keine Abwesenheitsgruende.

Der halbjaehrliche Wechsel ist eine **organisatorische** Zusage — technisch erinnert nichts daran, und
ob adesso das Klassenkonto will oder personalisierte Zugaenge per SSO erwartet, ist Rueckfrage **R5**
fuer Interview 2.

### Von Hand pruefen

```bash
# Konten anlegen (Passwoerter stehen in der Antwort — nicht in ein Protokoll kopieren)
TOKEN=$(curl -s -X POST localhost:1500/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<admin-passwort>"}' | jq -r .token)
curl -s -X POST localhost:1500/api/users/class-accounts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{}' | jq '.[].classShort'

# Als Klasse anmelden und die eigene Woche lesen
S=$(curl -s -X POST localhost:1500/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"5a","password":"<ausgegebenes-passwort>"}' | jq -r .token)
curl -s "localhost:1500/api/me/class-week?date=$(date +%F)" -H "Authorization: Bearer $S" \
  | jq '[.days[].entries[] | {classShort, kind, reasons}] | unique'
curl -s -o /dev/null -w '%{http_code}\n' localhost:1500/api/timetable -H "Authorization: Bearer $S"   # 403
```

## 3c. Ferien und Feiertage je Bundesland

Seit dem 05.09. (E-51, Migration `V8`) liegt eine **Vorlage** bei: fuer jedes der 16 Bundeslaender und die
Schuljahre **2026/27** und **2027/28** stehen die amtlichen Ferien und die landesweit geltenden Feiertage
in der Tabelle `holiday_preset` — **523 Zeilen**, davon 174 Ferienbloecke und 349 Feiertage.

Das ist **Datenhaltung, kein Dienst.** Es wird nichts zur Laufzeit aus dem Internet geholt (das bleibt die
Abgrenzung des Pflichtenhefts). Die Zeilen kommen aus der Recherche in `07-Konzept/recherche/` und werden
mit einem Generator erzeugt — wie die Regelwerte in `V2`.

### Benutzen

**Einstellungen → Unterrichtsfreie Zeitraeume → „Vorlage laden"**, Schuljahr waehlen, „Uebernehmen". Der
Toast meldet `n uebernommen, m schon vorhanden`. Wer in **Einstellungen → Schule** das Bundesland
umstellt und speichert, bekommt die Rueckfrage gleich dort angeboten („Uebernehmen" / „Spaeter").

Uebernommene Eintraege sind danach **Zeitraeume wie jeder andere**: einzeln loeschbar, und ein
abweichender Termin wird als neuer Zeitraum eingetragen. Ein zweiter Aufruf legt nichts doppelt an —
uebersprungen wird, was mit gleicher Bezeichnung und gleichem Anfang und Ende schon steht.

```bash
TOKEN=$(curl -s -X POST localhost:1500/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<admin-passwort>"}' | jq -r .token)
curl -s localhost:1500/api/holidays/preset-years -H "Authorization: Bearer $TOKEN"
# {"state":"SH","schoolYears":["2026/27","2027/28"]}
curl -s -X POST localhost:1500/api/holidays/preset -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"schoolYear":"2026/27"}'
# {"added":14,"skipped":0,"state":"SH","schoolYear":"2026/27"}   — zweiter Aufruf: added 0, skipped 14
```

Ein Bundesland oder Schuljahr ohne Vorlage antwortet **409 `preset-missing`** und nennt im `detail` die
vorhandenen Schuljahre.

### Was drin ist — und was von Hand nachgetragen wird

| | |
|---|---|
| **drin** | die amtlichen Ferienbloecke (KMK-Ferienkalender bzw. Landesquelle) und alle Feiertage mit `scope` `bundesweit` oder `land` |
| **drin, bewusst** | Feiertage, die auf einen Samstag oder Sonntag fallen (143 der 523 Zeilen). Sie schaden nicht — an diesen Tagen steht ohnehin kein Unterricht — und die Vorlage bleibt so eine nachpruefbare Kopie der Recherche. Eine Schule mit Samstagsunterricht verliert dadurch keinen Feiertag |
| **nicht drin: Teilgebiet** | zwoelf Feiertage gelten nur in Teilen eines Landes (`scope: "teilgebiet"`): Fronleichnam in ueberwiegend katholischen Gemeinden von Sachsen und Thueringen, Mariae Himmelfahrt in katholischen Gemeinden Bayerns, das Augsburger Friedensfest nur in der Stadt Augsburg. Landesweit sind sie **kein** schulfreier Tag; eine Schule im betroffenen Gebiet traegt ihn von Hand nach |
| **nicht drin: bewegliche Ferientage** | die schulspezifisch waehlbaren Tage (BW vier, BB einer, …) und einzelne unterrichtsfreie Tage nach Schulordnung (Gruendonnerstag in BW, Tag nach Christi Himmelfahrt in BE/BB). Sie sind laut Land **keine** amtlichen Ferientage und stehen deshalb nicht in der Vorlage — von Hand ergaenzen |

**Zuordnung zum Schuljahr.** Ein Feiertag gehoert zum Schuljahr, in dem sein Datum liegt; der Schnitt ist
der **1. August**. Ein Ferienblock gehoert zu dem Schuljahr, in dem er **beginnt** — Sommerferien, die
ueber den Wechsel laufen (BW 29.07.–11.09.2027), bleiben also beim beginnenden Schuljahr. Eine Ausnahme:
Bayerns Sommerferien 2026/27 beginnen erst am 02.08.2027 und bleiben trotzdem bei 2026/27, wie die KMK sie
fuehrt — sonst haette Bayern in diesem Schuljahr gar keine Sommerferien.

### Jaehrlich fortschreiben

Sobald die KMK das naechste Schuljahr veroeffentlicht:

1. `07-Konzept/recherche/ferien-2026-2028.json` und `feiertage-2026-2028.json` um das Schuljahr ergaenzen
   (Felder und Schreibweise wie bisher; `source` je Zeile ist Pflicht) und die Kopien in
   `scripts/` erneuern.
2. `SCHULJAHRE` in `scripts/gen-holiday-presets.mjs` erweitern.
3. `node scripts/gen-holiday-presets.mjs` — das Skript schreibt Migration, Attrappe und die Kopie im
   Starter neu und meldet die Zeilenzahl je Land und Schuljahr. `--check` schreibt nichts und meldet nur,
   ob die Dateien zum Stand der JSON passen.
4. **Achtung Flyway:** `V8` ist committet und darf nicht mehr geaendert werden. Die neu erzeugte Datei
   deshalb als `V<n>__holiday_presets_<schuljahr>.sql` ablegen (Diff gegen `V8` bilden, nur die neuen
   `INSERT`-Bloecke behalten) und die TS-Dateien einfach ersetzen.
5. `scripts/ci-backend-test.sh` — `HolidayPresetIT` zaehlt die Zeilen und faellt auf, wenn die Zahlen
   im Test nicht mitgezogen wurden.

Quellen, Vertrauen je Kategorie und offene Punkte stehen in den beiden Berichten
`07-Konzept/recherche/ferien-2026-2028.md` und `feiertage-2026-2028.md`. Offen dort: keine
Primaerquelle fuer das Augsburger Friedensfest und die genauen Gemeindelisten der Teilgebiete — fuer die
Vorlage folgenlos, weil `teilgebiet` gar nicht uebernommen wird.

## 3d. Android-App — APK, Bauen, Testfaelle

Die App liegt unter `android-starter/`, das fertige Paket unter **`app/stundenplan.apk`**
(33.380.315 Byte, **Debug-Signatur**, gleiche MD5 wie `android-starter/app/build/outputs/apk/debug/app-debug.apk` —
nachgerechnet 05.09.). Details zu jedem Punkt hier: `android-starter/README.md`.

### Bauen — ohne Android Studio, nur mit Docker

```bash
# im Ordner 08-Build/plattform
scripts/ci-android-build.sh                       # Unit-Tests + Debug-APK -> app/stundenplan.apk
scripts/ci-android-build.sh :app:testDebugUnitTest :app:assembleDebug :app:lintDebug   # dazu Lint
scripts/ci-android-build.sh :app:assembleRelease   # unsigniertes Release-APK (fuer die Abgabe signieren)
```

Das Skript startet den benannten Container `stundenplan-android-build` (ohne `--rm`, `docker logs` zeigt
den Lauf) und legt SDK und Gradle-Ablage in die Docker-Volumes `android-sdk` und `android-gradle`. Erster
Lauf ohne Cache rund 19 min auf Apple Silicon — das Android-SDK bringt fuer `aapt2`, `d8` und Lint nur
x86_64-Binaerdateien mit, sie laufen unter Emulation. **Wichtig fuer Rolle 6:** mit `composeBom 2026.08.00`
scheitert der Build (Compose 1.12.0 verlangt AGP ≥ 9.1.0 und `compileSdk 37`); `2026.06.01` laeuft.

In Android Studio ist nichts davon noetig: **`Open`** auf `android-starter/`, Gradle-JDK 17, SDK-Platform 36.

### Installieren und gegen den Stack fahren

```bash
docker compose -f deployment/docker-compose.yml up -d        # Stack auf :1500
adb install -r app/stundenplan.apk                           # Emulator API 30+
```

In der App als Serveradresse **`http://10.0.2.2:1500`** eintragen — `10.0.2.2` ist die Adresse, unter der
der Emulator den Rechner sieht; `localhost` waere das Telefon selbst. Nur Rechner und Port, **ohne `/api`**
(die App haengt die Vertragspfade selbst an; ein getipptes `/api` entfernt sie). Auf einem echten Geraet
im WLAN stattdessen die LAN-IP des Rechners, auf der VM `http://134.245.1.240:1500`.

**Konto.** Der Beispielbestand legt nur Klassenkonten an. Auf dem laufenden Stack stehen (gemessen
05.09. ueber `GET /api/users`) 14 Konten: 12 Klassenkonten `5a` … `10b` (`STUDENT`), ein Planungskonto
(`PLANNING`) und genau ein Lehrkraft-Konto, Benutzername `meyer`, id 2 (`TEACHER`). **Sein Passwort ist
hier nicht bekannt** — die oft genannte Kombination `meyer`/`meyer` wird mit 401 abgewiesen und kann auch
nicht stimmen, weil der Vertrag fuer Passwoerter `minLength: 8` vorschreibt. Vor dem Klickdurchlauf also
Passwort neu setzen (Web-App → Einstellungen → Benutzerkonten, oder `PUT /api/users/2/password`) und es
**nicht** in eine Datei dieses Ordners schreiben. Ein Planungskonto meldet sich zwar an, bekommt aber
„Dieses Konto ist keiner Lehrkraft zugeordnet." (404 des Vertrags, Absicht).

### Testfaelle T-A1.1 bis T-A3.1 — Belegstand

**Keiner der drei Testfaelle ist als Klickdurchlauf ausgefuehrt.** Auf dem Rechner, auf dem die App
entstanden ist, gibt es kein JDK, kein Android-SDK, kein Android Studio und keinen Emulator. Belegt ist
die Logik darunter — auf der JVM, gegen echte Antworten des Stacks (`app/src/test/.../SampleJson.kt`).

| Testfall | Was der Testfall verlangt | Belegt (JVM, gemessen) | Offen (braucht ein Geraet) |
|---|---|---|---|
| **T-A1.1** Anmeldung + Tagesansicht | Lehrkraft meldet sich an, sieht am 21.09. **nur eigene** Stunden mit Zeit, Fach, Klasse, Raum; keine Bearbeitung, keine fremde Lehrkraft waehlbar | `TimetableViewModelTest` (17): Erststart ohne Adresse → Einstellungen, ohne Token → Anmeldung, Anmeldung falsch/richtig, `Data` mit Raster und Zeitstempel, Tageswechsel innerhalb/ueber die Wochengrenze. `TimetableRepositoryTest` (17): Login 200/401, Woche 200/401/404/500 gegen MockWebServer. Die App hat **keinen** Schreibpfad — `TimetableApi` kennt nur `GET`, `POST /auth/login`, `DELETE /auth/token` | Bild der Liste, Tippen, Wischen, Kalender |
| **T-A2.1** Vertretung sichtbar | Vertretene Stunde ist als solche erkennbar; Detail nennt Anlass/Anlaesse und die vertretene Lehrkraft; abgegebene Stunde erscheint als „abgegeben an SCZ" | `EntryKindUiTest` (9): jeder `kind`-Wert auf Rahmen, Badge, Label, Durchstreichen, Abblenden — `SUBSTITUTED` „Vertretung" vs. nur-Raum „Raumwechsel", `NEEDS_SUBSTITUTION`, `GIVEN_AWAY`, `CANCELLED` inkl. Servertext, `MOVED`, `ADDED`. `WeekViewTest` (9): Sortierung, Listenschluessel, Zaehler „Heute geaendert" | Bottom Sheet in der Hand, Farben auf echtem Bildschirm, Dark Mode |
| **T-A3.1** Serveradresse + Offline | Unerreichbare Adresse warnt und laesst sich **trotzdem** speichern; sie ueberlebt den Neustart; ohne Netz bleibt der letzte Stand mit Zeitstempel sichtbar | `ApiFactoryTest` (6): Schema-Zwang, Schraegstrich, `/api` am Ende. `TimetableRepositoryTest`: Health zeigt den Schulnamen / Unsinn ist nicht erreichbar; Server aus → Offline mit lesbarem Cache. `OfflineCacheTest` (6): Zeitstempel, hoechstens vier Wochen, kein halber Stand, `clear` | „Trotzdem speichern" als Knopf, App-Neustart, Flugmodus |

Messwerte des letzten Laufs: **64 Unit-Tests in 6 Klassen, 0 Fehler** (nachgezaehlt in
`android-starter/app/build/test-results/testDebugUnitTest/TEST-*.xml`) und **Lint 0 Befunde**
(`lint-results-debug.xml` enthaelt kein einziges `<issue>`-Element). Die Klickdurchlaeufe gehoeren zu
Karte T-601 (Mo 07.09.) und T-608 (ab 14.09. taeglich gegen die VM); bis dahin steht in jedem Protokoll
„App-Anteil offen", so wie in Abschnitt 3.

**Debug-Signatur.** `app/stundenplan.apk` ist mit dem `debug.keystore` des SDK signiert — installierbar,
aber kein Ausweis der Gruppe, und nicht Play-Store-faehig. Zwei Folgen: wer die App vorher aus Android
Studio installiert hat, muss vor `adb install` einmal `adb uninstall de.wsp3_1.stundenplan` machen
(andere Signatur → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`); und **fuer die Abgabe wird neu signiert**
(`:app:assembleRelease`, dann `zipalign` + `apksigner` mit eigenem Keystore, Karte T-609).

---

## 4. Einen Teil in die eigene Arbeit uebernehmen

Der Vollausbau ist zum **Ausschlachten** gedacht. Vier Schritte, immer dieselben.

### 4.1 Modul kopieren

```bash
# Backend-Modul samt Tests
cp -r backend/src/main/java/de/wsp3_1/<modul>  <ziel>/backend/src/main/java/de/wsp3_1/
cp -r backend/src/test/java/de/wsp3_1/<modul>  <ziel>/backend/src/test/java/de/wsp3_1/
```

**Reihenfolge zaehlt.** `shared` zuerst — ohne `Weekday`, `Role`, `CurrentUser` und den
`ApiExceptionHandler` kompiliert kein anderes Modul. Danach die Module ohne Abhaengigkeiten (`config`,
`auth`, `masterdata`, `rules`), dann `solver` + `planning`, zuletzt `substitution` und `changelog`.
Erlaubte Richtungen stehen in `docs/VERTRAG.md` Abschnitt 4; `ArchitectureTest` prueft sie.

**Die Tabellen kommen mit.** Ein Modul ohne seine Flyway-Migration startet nicht
(`ddl-auto=validate`). Die Zuordnung: `V1` Grunddaten/Konfiguration/Konten, `V2` Regel-Voreinstellungen,
`V3` Planung, `V4` Vertretung und Verlauf, `V5` Ferien + `valid_from` + Solver-Zustand, `V6` Anlaesse als
`text`, `V7` Klassenkonto (Rolle STUDENT, `user_account.class_id`), `V8` Ferien- und Feiertagsvorlagen.
`ApplicationIT` prueft „V1 bis V8". **Eine committete Migration nie aendern** — Flyway prueft die
Pruefsumme; Korrekturen bekommen eine neue Nummer.

### 4.2 Tests mitnehmen — alle

Ein Modul ohne seine Tests ist keine Uebernahme, sondern eine Kopie mit unbekanntem Zustand. Drei Dinge
gehoeren immer dazu:

- `ArchitectureTest` — ohne ihn zerfaellt die Schichtung innerhalb von zwei Wochen.
- die Integrationstest-Basis (`IntegrationTestBase`) und das Profil `ci`, sonst laufen die `*IT`-Tests nicht.
- `scripts/ci-backend-test.sh` — es bringt sein JDK und seine Wegwerf-Datenbank im Container mit, ein
  lokales JDK ist nicht noetig.

Beim Testlauf **keinen Image-Build parallel starten**: die Wegwerf-Datenbank meldet sonst „nach 60 Sekunden
nicht bereit" (reproduziert am 05.09.).

### 4.3 Vertrag angleichen

Jede uebernommene Route braucht dieselbe Aenderung an drei Stellen, im selben Merge Request:

1. `api-contract/openapi.yaml` — Pfad, Schema, ein `example` je 2xx, die passenden Fehler als `$ref`.
2. `frontend/src/api/types.ts` — feldgenaue Abschrift; weicht sie ab, brechen die Seiten stumm.
3. eine Zeile in `docs/VERTRAG.md` (Abschnitt 2 fortsetzen, `V-15 …`).

Danach `cd api-contract && npm run lint` (0 Fehler) und einmal `curl` gegen `npm run mock`.
**Nur additiv**: neue Endpunkte, neue **optionale** Felder. Nie umbenennen, nie entfernen, nie einen Typ
aendern — und ein **neuer Enum-Wert zaehlt als brechend**, weil Web und App darueber verzweigen.

Wer eine Seite uebernimmt, braucht vier Teile, sonst laeuft `npm run dev:mock` nicht: die Route
(`src/routes/w*.tsx`), den Hook (`src/api/hooks.ts`), den Typ (`src/api/types.ts`) und die Attrappe
(`src/mock/`). Weicht die Attrappe vom Server ab, **gewinnt der Server**.

### 4.4 Wo Referenz und Plan auseinandergehen

Nicht alles im Rueckgrat und in der Entwurfsdoku ist so gebaut worden. Die vollstaendigen Listen stehen in
`docs/VERTRAG.md` Abschnitt 7 (D-1 bis D-11) und im Nachtrag von `docs/SOLVER-PLAN.md` (SV-1 bis SV-8).
Die sechs, die beim Weiterbauen am haeufigsten stolpern lassen:

| Plan sagt | Gebaut ist | Grund |
|---|---|---|
| Monorepo | getrennte Repos `web-app` und `android-app` | E-42, Ist-Stand seit 03.09. |
| MUI + TypeScript im Web | Tailwind 4 + shadcn/ui | Auftrag des Auftraggebers, modernes SaaS-Design |
| Paketwurzel `de.pushnpull.timetable` | `de.wsp3_1` | Startschablone des Teams |
| neun Module | zehn — `sampledata` kam dazu | der Beispielbestand enthaelt einen angenommenen Plan; `masterdata` darf `planning` nicht kennen |
| `SolveService` liegt in `solver` | `SolveService` in `planning`, nur `SolverRunner` in `solver` | `solver` darf keine Datenbank sehen (ArchUnit) |
| Pausenregel weich (E-47) | `NO_LESSONS_IN_BREAKS` ist **hart** | die Regel hat keinen Wertebereich, sie laesst sich nicht gewichten; Seed, Attrappe und Checkliste sagen alle drei hart |

Dazu die Terminlage: **API-Freeze ist Fr 11.09.**, nicht „Fr 12.09." — der 12.09.2026 ist ein Samstag.

---

## 5. Pipeline und Betrieb — Kurzfassung

**Vor dem Push lokal pruefen.** Jeder CI-Job hat ein Skript, das dieselben Befehle ausfuehrt:

```bash
scripts/ci-all.sh    # contract-lint · backend-test · frontend-build · image
```

Letzter gruener Durchlauf mit voller Ausgabe: `docs/betrieb/ci-all-2026-09-05.log` (05.09., alle vier ok).
Einzeln: `ci-contract-lint.sh`, `ci-backend-test.sh`, `ci-frontend-build.sh`, `ci-image.sh`,
dazu `ci-android-build.sh` (baut Android ohne Android Studio, im Container).

**Gruen hier heisst nur, dass die Befehle stimmen und der Code gruen ist.** Runner-Image, Netzzugang und
Docker-in-Docker sind damit nicht gezeigt.

Die Pipeline selbst steht in `.gitlab-ci.yml`: Vertrag zuerst (billigster Job, teuerster Fehlschlag),
danach Frontend-Bau und die beiden Backend-Testjobs, zuletzt Abbild bauen und ausliefern — **beide
manuell**, damit vor der Liveschaltung ein Mensch entscheidet, wann die Live-Umgebung neu startet.

**Betrieb:** ein Compose-Stack mit drei Diensten, nach aussen genau ein Port. Umzug oder Portwechsel ist
eine Aenderung an `deployment/.env` plus `docker compose up -d` — kein Bau, kein Neukompilieren; die
Web-App spricht die API relativ unter `/api`.

```bash
(cd deployment && ./beispieldaten.sh)   # Beispielbestand nachtraeglich laden (idempotent)
(cd deployment && ./backup.sh)          # Datenbank sichern -> deployment/backups/
DRY_RUN=1 scripts/deploy-vm.sh          # Auslieferung; DRY_RUN zeigt jeden Befehl, ohne etwas anzufassen
```

Achtung bei mehreren Staeckern: `beispieldaten.sh` und `backup.sh` lesen `.env` **nach** der Umgebung ein —
`WEB_PORT=1510 ./beispieldaten.sh` trifft trotzdem den Port aus `.env`.

Alles Weitere — VM anlegen, Deploy-Schluessel, Speichergrenzen, Sicherung, **Ruecksicherung und Rollback**,
Umzug auf einen anderen Server, Liveschaltung 20.09. — steht Schritt fuer Schritt in
[`deployment/README-VM.md`](deployment/README-VM.md). Die Ruecksicherung ist am 05.09. einmal vollstaendig
durchgespielt worden (Log: `docs/betrieb/ruecksicherung-2026-09-05.log`); der Deploy auf die VM
**noch nie**.

---

## 6. Wenn etwas klemmt

| Symptom | Ursache und Abhilfe |
|---|---|
| `ADMIN_INITIAL_PASSWORD fehlt` | `cp -n .env.example .env` im Ordner `deployment/` vergessen |
| Anmeldung scheitert, `admin`/`admin` geht | Konto stammt aus einem alten Lauf; `docker compose down -v` und neu starten (loescht alle Daten) |
| „Unable to locate a Java Runtime" | richtig so — es gibt kein JDK auf dem Rechner. `scripts/dev-backend.sh` benutzen |
| Port 8080 antwortet mit `Server: Apache` | macOS-Apache. `sudo apachectl stop` oder auf `BACKEND_PORT=8089` ausweichen |
| `no such service: mock` | die Attrappe steht in `docker-compose.dev.yml`, nicht im Produktions-Compose |
| Prism antwortet ueberall 401 | Bearer-Header fehlt; Prism prueft nur, **ob** einer da ist |
| Nach F5 wieder auf `/login` | nur bei geleertem `localStorage` oder abgelaufenem Token — seit F-5a haelt die Sitzung 30 Tage (Abschnitt 1) |
| Backend startet nicht, Flyway meckert | eine Migration wurde nachtraeglich geaendert. Nie tun — neue Nummer vergeben; lokal `docker compose down -v` |
| Wegwerf-Datenbank „nach 60 Sekunden nicht bereit" | ein `docker compose --build` laeuft parallel. Erst den Bau abwarten, dann testen |

Loeschen im Repo mit `git rm`. Nichts direkt auf `main` pushen — Branch, Merge Request, gruene Pipeline.
