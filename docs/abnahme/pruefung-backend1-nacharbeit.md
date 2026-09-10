# Nacharbeit zur Prüfung der Phase backend1 — 05.09.2026

Eingearbeitet sind die neun Prüfbefunde vom 05.09. (Claude-Prüfer, Zweitmeinung Codex). Diese Datei hält fest,
was geändert wurde und womit es belegt ist. Nichts ist committet; der Arbeitsbaum bleibt zur Durchsicht liegen.

**Prüfstand:** macOS, Docker; Backend-Tests in `gradle:9.7.1-jdk21` gegen `postgres:16-alpine` (Profil `ci`),
Stack mit `deployment/docker-compose.yml` (Profil `prod`) bzw. zusätzlich `docker-compose.dev.yml` (Profil `dev`),
`WEB_PORT=1500`, `deployment/.env` mit `ADMIN_INITIAL_PASSWORD=<Kennwort aus deployment/.env>`.

## 1. Was geändert wurde

| # | Befund | Änderung |
|---|---|---|
| 1 (hoch) | Migration `V5` fehlte, `ApplicationIT` prüfte nur V1–V4 | `V5__holiday_valid_from_solve_state.sql` angelegt (Wortlaut aus VERTRAG Abschnitt 6); `ApplicationIT.allMigrationsApplied` prüft `"1".."5"` |
| 2 | `ArchitectureTest.ALLOWED` unvollständig | `planning → … config …`, `substitution → … auth …`, `changelog → auth, shared` ergänzt (VERTRAG Abschnitt 4) |
| 3 | `--rerun` galt nur für die letzte Aufgabe | `scripts/ci-backend-test.sh` ruft jetzt `gradle --no-daemon test --rerun integrationTest --rerun "$@"`; Kopfkommentar und VERTRAG Abschnitt 5 erklären es; `docs/abnahme/auth.md` trägt einen Nachtrag zur Zahl „42 Tests" |
| 4 | feste Container-Namen, Parallelläufe stören sich | `CI_NETWORK`, `CI_DB` (Standard mit Prozessnummer `…-$$`), `CI_GRADLE_CACHE` |
| 5 | `dev` überschrieb `ADMIN_INITIAL_PASSWORD` | `application-dev.properties`: `app.admin-initial-password=${ADMIN_INITIAL_PASSWORD:admin}`; Logzeile und Kommentare neutral |
| 6 | Vertragsbeispiele `SOFT`/`28` gegen Seed `HARD`/`26` | beide Beispiele auf `26` und `HARD` gezogen; Entscheidung als D-11 im VERTRAG festgehalten |
| 7 | 502-Fenster nach `docker compose up -d` | Healthcheck am `backend` (`curl -fsS …/api/health`), `web` wartet auf `service_healthy` |
| 8 | DTO-Regel mit ArchUnit nicht erfüllbar | VERTRAG Abschnitt 6 legt eine Form fest (api-Records + `domain`-Records + Mapper neben dem Controller); `masterdata` (MD-1) als vorhandene zweite Form benannt |
| 9 | Kommentar in `V2` beschrieb `reset` falsch | Kommentar in `V2` **und** in `scripts/gen-rule-presets.mjs` korrigiert (vor dem ersten Commit, Prüfsumme existiert noch nirgends) |

`node scripts/gen-rule-presets.mjs --check` → `unveraendert` für alle drei erzeugten Dateien: die Migration
stimmt weiter mit dem Generator überein.

## 2. Tests (beide Aufgaben frisch)

```
$ CI_DB=stundenplan-ci-db-pruef CI_NETWORK=stundenplan-ci-pruef scripts/ci-backend-test.sh
> Task :test
> Task :integrationTest
BUILD SUCCESSFUL in 45s
5 actionable tasks: 5 executed
```

Kein `UP-TO-DATE` bei `:test` oder `:integrationTest`. Aus den JUnit-XML desselben Laufs:

| Aufgabe | Klassen | Tests | Fehler | Übersprungen |
|---|---|---|---|---|
| `test` | 8 | 42 | 0 | 0 |
| `integrationTest` | 11 | 70 | 0 | 0 |

Gegenprobe, dass ein zusätzliches `--rerun` (so steht es in älteren Abnahmen) das Skript nicht bricht:

```
$ scripts/ci-backend-test.sh --rerun
> Task :compileJava UP-TO-DATE
> Task :test
> Task :integrationTest
BUILD SUCCESSFUL in 40s
5 actionable tasks: 3 executed, 2 up-to-date
```

## 3. Abnahme-Schritte erneut belegt

### I-2 — Stack startet, fünf Migrationen (Profil `prod`)

```
$ cd deployment && docker compose up -d
$ docker compose ps
backend  Up 11 seconds (healthy)
db       Up 16 seconds (healthy)
mock     Up 16 seconds
web      Up Less than a second

$ docker compose logs backend
… BackendApplication : The following 1 profile is active: "prod"
… o.f.core.internal.command.DbMigrate : Successfully applied 5 migrations to schema "public", now at version v5
… BackendApplication : Started BackendApplication in 5.53 seconds
```

Schema aus `V5` in der laufenden Datenbank:

```
$ docker compose exec -T db psql -U tt -d timetable -c "…"
 solve_error | solve_last_score | valid_from        (Spalten in timetable)
 Table "public.holiday": id, name, date_from, date_to, kind
 Check constraints: holiday_kind_check (VACATION/PUBLIC_HOLIDAY), holiday_period (date_to >= date_from)
```

### I-5 / K-3 — kein 502-Fenster mehr

`up -d` kehrt jetzt erst zurück, wenn `backend` gesund ist (`Container stundenplan-backend-1 Healthy`,
danach `web Starting`). Zehn Aufrufe unmittelbar danach:

```
01  01:19:19.98  HTTP 200
…
10  01:19:20.24  HTTP 200
```

Vorher meldete nginx in diesem Fenster 502 (Befund 7). In einem der Läufe traf der allererste `curl` noch
`000` (Verbindung abgelehnt, weil Docker den Host-Port erst in derselben Sekunde veröffentlicht) — kein 502,
und im Wiederholungslauf nicht mehr aufgetreten. Eine Warteschleife sollte weiterhin auf HTTP 200 prüfen.

### K-3 — Daten überleben `down && up`

```
$ docker compose down && docker compose up -d
erneute Anmeldung: 200
$ curl -s $B/api/teachers -H "Authorization: Bearer $T"
[{"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,…}]
… DbMigrate : Schema "public" is up to date. No migration necessary.
```

### L-1 / L-2 — Kennwort kommt aus `.env`, in beiden Profilen

Profil `prod` (`docker-compose.yml`):

```
richtig: 200  {"token":"…","role":"PLANNING","displayName":"admin","teacherId":null}
falsch:  401  {"detail":"Benutzername oder Passwort ist falsch.", … "type":"urn:pushnpull:problem:unauthenticated"}
```

Profil `dev` (`… -f docker-compose.dev.yml`, frische Datenbank, derselbe `.env`-Wert):

```
Kennwort aus .env (<Kennwort aus deployment/.env>): 200
alter dev-Festwert (admin):          401
… AdminBootstrap : Konto "admin" mit der Rolle PLANNING angelegt (Passwort aus app.admin-initial-password).
… DbMigrate      : Successfully applied 5 migrations to schema "public", now at version v5
```

Vor der Änderung war es umgekehrt (Befund 5): im Profil `dev` galt immer `admin`.

### R-3 — Regelwerk und Zurücksetzen stimmen mit dem Vertrag überein

`GET /api/rules` und `POST /api/rules/reset` liefern beide dieselben fünf Zeilen:

```
1 MAX_LESSONS_PER_DAY  CLASS   6  SOFT  aktiv
2 MAX_LESSONS_PER_WEEK CLASS  30  SOFT  aktiv
3 MAX_LESSONS_PER_DAY  TEACHER 6  SOFT  aus
4 MAX_LESSONS_PER_WEEK TEACHER 26 SOFT  aus
5 NO_LESSONS_IN_BREAKS  —      1  HARD  aktiv
```

Damit sagen Server, Seed `V2`, Attrappe `rule-presets.ts`, Checkliste R-3 und die Beispiele in
`api-contract/openapi.yaml` dasselbe. `scripts/ci-contract-lint.sh`: „Your API description is valid",
eine Warnung (`operation-4xx-response` an `/api/health`, wie vorher).

## 4. Nicht geprüft

- Die Belastung des Solvers, alle Endpunkte der Phasen backend2/backend3 — anderer Bauabschnitt.
- Browser-Schritte der Checkliste (I-3, L-1 in der Oberfläche): das Frontend läuft im Stack noch gegen die
  Attrappe, die echte Anbindung ist die Frontend-Phase.
- `backbone/entscheidungen.md` E-47 („Pausenregel weich") ist **nicht** geändert — das liegt außerhalb des
  Arbeitsorts. Der Nachtrag ist als D-11 im VERTRAG festgehalten und muss von Rolle 1 im Rückgrat nachgezogen
  werden.

---

# Nacharbeit 2 — Zweitmeinung Codex (05.09.2026, später Abend)

Die neun Befunde des Claude-Prüfers oben waren beim Beginn dieses Durchgangs bereits eingearbeitet
(nachgeprüft: `V5` liegt vor, `ApplicationIT` erwartet `"1".."5"`, `ArchitectureTest.ALLOWED` trägt
`planning → config`, `substitution → auth`, `changelog → auth`, das CI-Skript hängt `--rerun` an beide
Aufgaben und liest `CI_NETWORK`/`CI_DB`/`CI_GRADLE_CACHE`, `application-dev.properties` schreibt
`${ADMIN_INITIAL_PASSWORD:admin}`, der Healthcheck steht am `backend`, die Vertragsbeispiele zeigen
`HARD`/`26`, der `V2`-Kommentar ist korrigiert). Dieser Abschnitt arbeitet die zwölf Befunde der
Zweitmeinung von Codex ein.

**Prüfstand:** macOS, Docker; Tests in `gradle:9.7.1-jdk21` gegen `postgres:16-alpine` (Profil `ci`);
Stack `deployment/docker-compose.yml` (Profil `prod`, Compose-Projekt `nacharbeit2`, `WEB_PORT=1500`).

## 5. Was geändert wurde

| # | Befund | Schwere | Änderung |
|---|---|---|---|
| C1 | `POST /api/sample-data` konnte ohne `replace` löschen | hoch | `SampleDataLoader.load(boolean)` hält Prüfung und Laden in **einer** Transaktion; ohne Ersetzungsauftrag läuft `clear()` gar nicht. Controller ist nur noch Weiterleitung |
| C2 | Paralleler Login umging die Sitzungsbeendigung | hoch | `@Lock(PESSIMISTIC_WRITE)` auf `findWithLockByUsername` / `findWithLockById`; `AuthService.login` und `UserService.setPassword` greifen über dieselbe gesperrte Kontozeile |
| C3 | Löschprüfung sah archivierte Verweise nicht | mittel | `DataIntegrityViolationException` → 409 (SQLState 23503 `in-use`, 23505 `data-exists`) statt 500. **Abweichung vom Vorschlag** — Begründung unten |
| C4 | Raster erlaubte überlappende Zeitfenster | mittel | `TimegridService.overlapsEarlierSlot` prüft je Tag auf Überschneidung → 400 `slots[i].startTime` |
| C5 | verschachtelte Eingaben unvollständig geprüft | mittel | leere Zeilen in `slots[]` / `subjects[]` und leere Einträge in `subjectIds`/`preferredTimeslotIds`/`blockedTimeslotIds` → 400 mit Feldliste; fehlendes `isBreak` ist jetzt ein Fehler (Vertrag: `required`) |
| C6 | Längengrenzen fehlten vor dem Datenbankzugriff | mittel | `@Size(max = n)` mit den Spaltenbreiten aus `V1` in `MasterdataDtos`, `ConfigDto`, `AuthDtos.CreateUser`; Regel in VERTRAG Abschnitt 6 |
| C7 | parallele Doppelanlage ergab 500 | mittel | derselbe Zweig wie C3: SQLState 23505 → 409 `data-exists`. Die Vorprüfungen in `RuleService`/`UserService` bleiben — sie liefern die bessere Meldung |
| C8 | Auffanghandler machte Anfragefehler zu Serverfehlern | mittel | `MethodArgumentTypeMismatchException` → 400 `validation`, `HttpRequestMethodNotSupportedException` → 405, `HttpMediaTypeNotSupportedException` → 415 |
| C9 | Testfixture nutzte falsche Uhrzeiten | mittel | `SampleDataLoader.ZEITEN` auf die sieben Zeitpaare aus Vertrag / T-A1.1 / Attrappe gezogen |
| C10 | `dev-backend.sh` reichte das Kennwort nicht weiter | mittel | `-e ADMIN_INITIAL_PASSWORD` am `docker run`, Standard `admin` im Skript |
| C11 | Generator brauchte Philipps Verzeichnisstruktur | mittel | Quelle als `scripts/bundesland-regelwerte.json` ins Repo; externe TS-Ziele nur, wenn ihr Ordner existiert |
| C12 | Ablauf-Test prüfte kein abgelaufenes Token | mittel | `AuthFlowIT.expiredTokenIsRejected` (Zeile per SQL zurückdatiert) und `UserControllerIT.setPassword_endsExistingSession` |

### Abweichung bei C3 — bewusst anders gelöst als vorgeschlagen

Codex schlägt vor, die Zählabfragen in `ClassSubjectRepository` um archivierte Stunden zu erweitern.
Das würde die fachliche Regel ändern: „löschen statt deaktivieren" richtet sich nach dem **aktiven Plan und
dem Entwurf** (`t.status <> 'ARCHIVED'`), damit ein Raum, der vor zwei Jahren einmal belegt war, weiter
löschbar bleibt. Zählte das Archiv mit, wäre nach dem ersten Archivplan nichts mehr löschbar. Der Fehler ist
nicht die Zählung, sondern die Antwort darauf: der Fremdschlüssel schlägt zu und kam als 500 heraus. Deshalb
ist der Auffangzweig die Lösung — die fachliche Regel bleibt, der Serverfehler verschwindet. Der Zweig deckt
zugleich die von Codex genannten Verweise aus Ereignissen, Ausnahmen und Verlauf ab, ohne dass `masterdata`
die Tabellen aus backend2/backend3 kennen muss.

## 6. Belege

### Tests (beide Aufgaben frisch, `--rerun` an beiden)

```
$ CI_NETWORK=nacharbeit3-net CI_DB=nacharbeit3-db scripts/ci-backend-test.sh
> Task :test
> Task :integrationTest
BUILD SUCCESSFUL in 43s
```

Aus den JUnit-XML (Zeitstempel gleich dem Lauf): **`test` 49 Tests, `integrationTest` 81 Tests,
zusammen 130 — 0 Fehler, 0 Ausfälle, 0 übersprungen.** Neu in diesem Durchgang sind **18 Tests**
(7 Unit-, 11 Integrationstests); die 42 Unit-Tests aus dem Prüfbericht stimmen damit weiter (49 − 7).
Jeder neue Test ist im JUnit-XML namentlich nachgewiesen:

| Test | Klasse | Befund |
|---|---|---|
| „Ein Aufruf ohne replace loescht nichts, auch wenn er mit 409 endet" | `SampleDataIT` | C1 |
| „Ein Passwort zu setzen entwertet die bereits offene Sitzung des Kontos" | `UserControllerIT` | C2, C12 |
| „Ein doppelter Schluessel (23505) ergibt 409 data-exists" | `ApiExceptionHandlerTest` | C7 |
| „Ein verletzter Fremdschluessel (23503) ergibt 409 in-use" | `ApiExceptionHandlerTest` | C3 |
| „Ohne SQLState bleibt es bei 409 in-use" | `ApiExceptionHandlerTest` | C3 |
| „Zwei Zeitfenster am selben Tag duerfen sich zeitlich nicht ueberschneiden" | `TimegridIT` | C4 |
| „Gleiche Uhrzeiten an verschiedenen Tagen sind in Ordnung" | `TimegridIT` | C4 (Gegenprobe) |
| „Ein fehlendes isBreak ist ein Eingabefehler, keine stille Annahme" | `TimegridIT` | C5 |
| „Eine leere Zeile im Raster ergibt 400 mit Feldliste, nicht 500" | `TimegridIT` | C5 |
| „Ein leerer Eintrag in subjectIds ergibt 400 mit Feldliste, nicht 500" | `TeacherServiceIT` | C5 |
| „Ein leerer Eintrag in blockedTimeslotIds ergibt 400 mit Feldliste, nicht 500" | `TeacherServiceIT` | C5 |
| „Ein zu langer Name ergibt 400 mit Feldliste, nicht 500" | `TeacherServiceIT` | C6 |
| „Ein zu langer Klassenname ergibt 400 mit Feldliste, nicht 500" | `MasterdataControllerTest` | C6 |
| „GET /api/teachers/abc ergibt 400 und nicht 500" | `TeacherServiceIT` | C8 |
| „Ein falsches HTTP-Verb ergibt 405 im Problemformat" / „Ein falscher Inhaltstyp ergibt 415 …" | `MasterdataControllerTest` | C8 |
| „Ein abgelaufenes Token wird abgewiesen, obwohl seine Zeile noch in der Datenbank steht" | `AuthFlowIT` | C12 |

### Stack (`docker compose -p nacharbeit2 up -d --build`)

- **I-2:** `Successfully validated 5 migrations`; `flyway_schema_history` zeigt `1..5`, alle `success = t`.
- **I-5 / K-3:** nach der Rückkehr von `up -d` **20 von 20** Aufrufen auf `/api/health` → `200`, kein 502.
  (Ein Erstaufruf traf noch `000`, solange der Host-Port nicht veröffentlicht war — Warteschleifen weiter
  auf 200 prüfen, nicht auf den curl-Exitcode.)
- **L-1:** Login mit dem Kennwort aus `deployment/.env` → `200`; mit `admin` → `401`.
- **D-1:** `POST /api/sample-data` → `{"classes":12,"teachers":24,"rooms":18}`, HTTP 200. Zweiter Aufruf
  ohne `replace` → HTTP 409 `data-exists`; Zählung danach unverändert 12 / 24 / 18 / 35 (C1).
- **G-3 / C9:** `GET /api/timegrid` nach dem Beispielbestand, Montag:
  `08:00–08:45 · 08:50–09:35 · 09:35–09:55 (Pause) · 09:55–10:40 · 10:45–11:30 · 11:50–12:35 · 12:40–13:25`
  — Zeichen für Zeichen die Reihe aus `openapi.yaml` und T-A1.1, 35 Zeilen.

### Fehlerfälle am laufenden Stack (vorher jeweils 500)

| Aufruf | vorher | jetzt |
|---|---|---|
| `GET /api/teachers/abc` | 500 | **400** `validation`, `errors[0].field = "id"` |
| `DELETE /api/timegrid` | 500 | **405** `method-not-allowed` |
| `POST /api/classes` als `text/plain` | 500 | **415** `unsupported-media-type` |
| Klassenname mit 21 Zeichen | 500 | **400** `validation`, Feld `name` |
| `PUT /api/timegrid` mit `{"slots":[null]}` | 500 | **400** `validation`, Feld `slots[0]` |
| `PUT /api/timegrid` mit Überschneidung | 200 (still angenommen) | **400** `validation`, Feld `slots[1].startTime` |
| `PUT /api/timegrid` ohne `isBreak` | 200 (still als „keine Pause") | **400** `validation`, Feld `slots[0].isBreak` |

**C3 direkt gemessen:** ein archivierter Plan mit einer Stunde auf Raum 1 angelegt (`timetable.status =
'ARCHIVED'`), die In-use-Zählung meldet dafür `0` aktive Stunden. `DELETE /api/rooms/1` →
`{"type":"urn:pushnpull:problem:in-use","status":409,…}`. Im Backend-Log steht
`SQLState: 23503 … violates foreign key constraint "lesson_room_id_fkey"`, und `Unerwarteter Fehler`
(die Logzeile des Auffangzweigs) kommt **0-mal** vor — der Fall lief also genau in den neuen Zweig.

### Generator (C11)

`node scripts/gen-rule-presets.mjs --check` liest jetzt `scripts/bundesland-regelwerte.json` aus dem Repo:
`unveraendert` für `V2__rule_presets.sql`, `frontend/src/mock/rule-presets.ts` und die Arbeitskopie;
`138 Zeilen, 16 Bundeslaender, davon 10 mit Jahrgangszeilen`. Die Migration bleibt Zeichen für Zeichen gleich.

## 7. Nicht geprüft

- **C2 unter echter Nebenläufigkeit.** Dass die Sperre das Wettrennen schließt, ist aus der Reihenfolge der
  Anweisungen begründet und durch den Sitzungstest abgesichert; ein Lasttest mit zwei gleichzeitigen
  Anfragen im Millisekundenfenster wurde nicht gefahren. Verklemmen kann es nicht: beide Wege nehmen erst
  die Kontozeile und danach `auth_token`, also in derselben Reihenfolge.
- **C7 über die Schnittstelle.** Ein echter 23505 verlangt zwei gleichzeitige Anfragen; über `curl` fängt
  ihn immer die Vorprüfung im Dienst ab (gemessen: doppelter Raumname → 400 `validation`, nicht 409). Der
  Zweig ist deshalb mit `ApiExceptionHandlerTest` belegt, nicht am laufenden Stack.
- **Der „Vorher"-Zustand** ist nur für C3 unmittelbar gemessen (Logzeile mit SQLState). Für C8 und C6 folgt
  er aus dem Quelltext (kein Handler für die Ausnahme, `Exception.class` → 500) und aus den Messungen von
  Codex; er wurde nicht durch Zurücknehmen der Änderung nachgestellt.
- Browser-Schritte und alles aus backend2/backend3 — anderer Bauabschnitt.
