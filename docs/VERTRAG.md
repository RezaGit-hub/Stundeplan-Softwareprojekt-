# VERTRAG — Vollausbau: API v1, Modulgrenzen, Bauphasen

Stand 05.09.2026 (Nacht auf den 05.09.), Architekt. Ort: `08-Build/plattform`, Branch `vollausbau`, **nichts committet,
nichts gepusht** — Philipp prüft und committet. Der Vollausbau ist Referenz für das Team, nicht die Abgabe.

**Ergebnis in einem Satz:** `api-contract/openapi.yaml` ist auf **v1 (`info.version 1.0.0`)** mit 63 Operationen (39 aus
v0 + 24 neu), Redocly-Lint 0 Fehler, Prism liefert jede neue Antwort samt allen sieben `kind`-Werten; die drei
Dokumente `docs/VERTRAG.md` (dieses), `docs/SOLVER-PLAN.md` und `docs/ABNAHME-CHECKLISTE.md` legen fest, was die
Bauphasen backend1/backend2/backend3/frontend bauen, wo die Modulgrenzen liegen und woran die Abnahme misst.

## 1. Wo der Vollausbau steht und wie er entstand

- `git clone 08-Build/starter 08-Build/plattform && git checkout -b vollausbau` liefert **nur** das Team-Gerüst
  (Commit `1552b63`), weil die gesamte B1–B5-Arbeit im Starter uncommitted ist (STARTSTAND Abschnitt 10, Lernnotiz 180).
  Deshalb wurde der Arbeitsbaum übertragen, ohne etwas zu committen: `git -C starter diff HEAD --binary` per
  `git apply` (übernimmt die `git mv`-Umbenennungen sauber, keine doppelten `ClientController`), danach die 282
  untracked Dateien aus `git ls-files -o --exclude-standard` per `rsync --files-from`. Ergebnis: `diff -rq` zwischen
  Starter und Plattform ist leer bis auf `deployment/.env` (Zugangsdaten, absichtlich nicht kopiert — vor dem ersten
  `docker compose` `cp deployment/.env.example deployment/.env`); `git diff --cached` ist leer (0 Zeilen gestaged).
- Geändert bzw. neu gegenüber dem Starter: `api-contract/openapi.yaml` (v1), `docs/VERTRAG.md`, `docs/SOLVER-PLAN.md`,
  `docs/ABNAHME-CHECKLISTE.md`, `api-contract/README.md` (Stand v1). Sonst nichts.
- `api-contract/node_modules` und `frontend/node_modules` sind nicht kopiert (ignoriert); Lint und Prism liefen mit
  den Werkzeugen aus `starter/api-contract/node_modules` (Belege in Abschnitt 9). Wer in `plattform` arbeitet:
  `cd api-contract && npm ci`, `cd frontend && npm ci`.

## 2. Vertrag v1 — was sich gegen v0 geändert hat und warum

Grundlage: `backbone/api.md` (Nachtrag 03.09.), Entwurfsdoku Kap. 5 Tabellen kl-api-2/kl-api-3/kl-dtos, `frontend/src/api/types.ts`
(feldgenaue Abschrift, gegen die die Web-App gebaut ist), `frontend/src/mock/*` (Fachverhalten der Attrappe).
Beispielwerte: Pflichtenheft-Testdaten **nach** dem Leitszenario (Woche 21.–25.09.2026), damit Mock, Attrappe und
Testfälle dieselben Zahlen zeigen.

| # | Änderung | Warum |
|---|---|---|
| V-1 | 24 neue Operationen, additiv: `GET /api/timetable`, `GET /api/timetable/week`, `GET /api/me/timetable/week`, `GET/POST/DELETE /api/draft`, `POST /api/draft/lessons`, `PATCH/DELETE /api/draft/lessons/{id}`, `POST/DELETE /api/draft/solve`, `POST /api/draft/accept`, `GET/POST /api/events`, `DELETE /api/events/{id}`, `GET /api/substitutions/pending`, `GET /api/substitutions/options`, `GET/POST /api/exceptions`, `DELETE /api/exceptions/{id}`, `GET /api/changelog`, `GET/POST /api/holidays`, `DELETE /api/holidays/{id}` | api.md-Tabelle v1 (Karte T-403); Rollen je Pfad als `x-role` und in der Beschreibung (E-45) |
| V-2 | 30 neue Schemas, Feldnamen 1:1 aus `types.ts`: `View`, `EntryKind`, `DraftStatus`, `DraftSource`, `EventKind`, `ExceptionKind`, `ChangelogAction`, `HolidayKind`, `LessonDto`, `TimetableDto`, `AcceptDraftResponse` (= `TimetableDto` + `warnings[]`), `SolveInfo`, `OpenLessonDto`, `DraftDto`, `CreateDraft`, `AddLessonInput`, `PatchLessonInput`, `StartSolveInput`, `AcceptDraftInput`, `EntryOriginal`, `EntryDto`, `DayDto`, `WeekDto`, `AffectedDto`, `ExtraLessonInput`, `EventDto`, `EventInput`, `PendingRow`, `TeacherOption`, `RoomOption`, `OptionsDto`, `ExceptionDto`, `ExceptionInput`, `ChangelogItem`, `ChangelogPage`, `HolidayDto`, `HolidayInput` | Web-App ist gegen `types.ts` fertig gebaut; jede Abweichung würde die 16 Seitenrouten brechen |
| V-3 | `DraftDto`, `EntryDto`, `ConflictDto`, `WeekDto`, `ProblemDetail` exakt wie in der Aufgabe gefordert; `ProblemDetail` unverändert (`errors[]`, `missing[]`, `conflicts[]`), `ConflictDto` unverändert, Beschreibung präzisiert (Text nennt Regel, Wert, Objekt, Tag; `objects[]` zum Anspringen) | Entwurfsdoku Tabelle kl-dtos; T-W2.1, T-W3.1 |
| V-4 | Kann-Felder **optional**: `StartSolveInput.classId`, `AcceptDraftInput.validFrom` (beide `required: false`-Bodies, Beschreibung „Kann (E-40, nach Rückfrage R1)") | Interview 1/E-40: erst nach R1 (08.09.) verbindlich; additiv nach Vertragsregel; `types.ts` führt beide schon optional |
| V-5 | **Nicht** enthalten: `TeacherOption.substitutionsThisWeek` | Belastungshinweis am 04.09. gestrichen (Auftrag). `types.ts` hat das Feld optional; die Oberfläche zeigt es nur, wenn es kommt — es kommt nicht |
| V-6 | Neu `DELETE /api/events/{id}` | Entwurfsdoku kl-api-3 („Ereignis zurücknehmen") und `ChangeEventService` (löscht); api.md und `client.ts` kennen es nicht → Frontend-Phase ergänzt `deleteEvent` (V-13) |
| V-7 | Neuer Slug `exception-exists` (409 bei zweiter Ausnahme je Stunde und Datum) | `UNIQUE(lesson_id, date)` in V4; die Attrappe ersetzt stillschweigend (dokumentierte Vereinfachung) — der Server darf das nicht: Verlauf und Anlässe würden verloren gehen |
| V-8 | Gemeinsame Antwort `Solving409` für alle schreibenden Entwurfs-Aufrufe; inline-409 mit Beispiel bei `POST /api/draft` (`draft-exists`), `POST /api/draft/solve` (`master-data-incomplete` mit `missing[]`), `POST /api/exceptions` (`exception-exists`) | Prism liefert per `Prefer: code=409` das richtige Beispiel je Endpunkt; das Web unterscheidet nach Slug |
| V-9 | `POST /api/draft/solve` und `DELETE /api/draft/solve` → 202 **ohne Body**; Stand nur über `GET /api/draft`; `DELETE /api/draft` während einer Berechnung → 409 `solving` | E-23 (asynchron, Polling 2 s); der Final-Consumer schreibt in den Entwurf — ein gelöschter Entwurf hätte keinen Ort für das Ergebnis |
| V-10 | `PATCH /api/draft/lessons/{id}`: Felder wirken nur, wenn gesendet; `timeslotId`/`roomId` setzen legt fest, außer `pinned` steht im Body; eine **offene** Stunde braucht `timeslotId` **und** `roomId` (sonst 400) | Invariante „belegt oder offen" (E-22); die Attrappe wählt sonst stillschweigend den ersten aktiven Raum — der Server rät nicht |
| V-11 | `POST /api/sample-data`: lädt 12/24/18 **plus** vom Solver gefüllten, angenommenen Plan (≈ 10 s), `replace: true` ersetzt Grunddaten, Pläne, Ereignisse, Ausnahmen, Verlauf **und Lehrkraft-Konten**; Regelwerk, Konfiguration, Planungs-Konten bleiben; **keine** Lehrkraft-Konten werden angelegt | E-46; `user_account.teacher_id` verweist auf `teacher` — ohne Löschen der Lehrkraft-Konten scheitert das Ersetzen; Konten mit Klartext-Passwort im Code verbieten die Projektregeln (keine Passwörter im Quelltext) — Konten legt die Planung an (`POST /api/users`, Abnahme A-1) |
| V-12 | `GET /api/me/timetable/week` mit `x-role: ANY` (jedes angemeldete Konto), Konto ohne Lehrkraft → 404 | Pfadregel `/api/me/**` = `authenticated` (E-45, Starter-`SecurityConfig`); `admin` ist keine Lehrkraft |
| V-13 | `ExceptionInput`/`ExceptionDto` **ohne** `addedClassId`/`addedSubjectId` (Entwurfsdoku Tabelle kl-dtos nennt sie); `kind: ADDED` über `POST /api/exceptions` → 400 | Zusatzstunden entstehen nur über `POST /api/events {kind: EXTRA_LESSON, extra}` (api.md, Attrappe, Web-App); zwei Wege für dieselbe Sache wären ein zweiter Formular- und Testpfad |
| V-14 | `info.description` und Konventionen ergänzt (Stand v1, 202-Regel, Rollenabsatz mit `GET /api/me/timetable/week`), sieben neue `tags` (Plan, Entwurf, Änderungen, Vertretung, Verlauf, Ferien), Pfad-Präfix `/api` in jedem Schlüssel beibehalten | Prism-Falle (Lernnotiz 160/167), Lesbarkeit im Redoc |
| V-15 | **v1.1.0 (11.09., #133, Rolle 3):** `RoomDto.blockedTimeslotIds` (Antwort, immer geliefert, Standard `[]`) und `RoomInput.blockedTimeslotIds` (optional, fehlend = keine Sperre, unbekanntes Zeitfenster → 400 `validation` Feld `blockedTimeslotIds`); neuer `ConflictType`-Wert `ROOM_BLOCKED_TIME` (hart, Text „Raum R101 ist am Montag in der 3. Stunde gesperrt."). Nachgezogen im selben Zug: `types.ts`, Attrappe (`mock/logic/conflicts.ts`, `mock-api.ts`), Raumdialog im Web | Wöchentliche Raumsperren (T2-3.3, E-54): Tabelle `room_blocked_timeslot` (V9) wie `teacher_blocked_timeslot`; Solver H8 `roomBlockedTime`, ConflictChecker Prüfung 4. Enum-Erweiterung = brechend (Regel 3.1), deshalb alle Clients zusammen |
| V-16 | **v1.2.0 (11.09., #110, Rolle 3):** neuer Pfad `POST /api/draft/repair` Body `RepairInput {lessonId}` → 200 `RepairResponse {proposals[]}` mit `RepairProposalDto {level 1..3, label, moves[{lessonId, timeslotId, roomId}], hardConflictsAfter, score|null}`; synchron, drei Solver-Läufe je 5 s (bis zu 15 s), **speichert nichts**; 404 `not-found` (Stunde nicht im Entwurf / kein Entwurf), 400 `validation` Feld `lessonId` (Stunde in keinem Konflikt oder fehlend), 409 `solving` (Lauf unterwegs; während der Serie gilt der Entwurf selbst als `SOLVING`). Übernehmen = je Zug `PATCH /api/draft/lessons/{id}` mit `pinned: false`. Nachgezogen im selben Zug: `types.ts`, `client.ts`/`hooks.ts` (`useRepairDraft`, Entwurfs-Mutationsoptionen), Attrappe (`mock-api.ts`, eine Stufe), Knopf „Lösen" in der Konfliktliste und `RepairDialog` im Web | Konflikt per Knopf lösen (T2-3.2, E-53): drei Freiheitsgrade ohne Regeländerung — (1) nur die Konfliktstunde, (2) + Stunden derselben Klasse am selben Tag, (3) + Stunden der Lehrkraft und des Raums am selben Tag; keine Stufe „weiche Regel lockern" (#108 unbesetzt). Ein Lauf gleichzeitig bleibt Gesetz: Vormerkung im `SolverRunner` vor der Serie, Freigabe im `finally` |
| V-17 | **v1.3.0 (16.09., #103, Rolle 3):** mehrere Entwürfe nebeneinander. Neue Pfade `GET /api/drafts` (Liste `DraftSummaryDto {id, name, createdAt, source, status, hardConflicts}`, jüngster zuerst), `POST /api/drafts` (`CreateDraft {name?, source}`), `GET/PATCH/DELETE /api/drafts/{id}` (`PATCH` mit `DraftNameInput {name}`), `POST /api/drafts/{id}/lessons`, `PATCH/DELETE /api/drafts/{id}/lessons/{lessonId}`, `POST/DELETE /api/drafts/{id}/solve`, `POST /api/drafts/{id}/accept`, `POST /api/drafts/{id}/repair` — Fachlogik wie die Singular-Pfade. `DraftDto.name` (immer geliefert), `CreateDraft.name` optional (Standard „Entwurf n", leer/>80 Zeichen → 400 `validation` Feld `name`). Die Singular-Pfade `/api/draft…` bleiben und meinen den **jüngsten** Entwurf (höchste `id`); `POST /api/draft` legt einen weiteren an — **409 `draft-exists` kommt nicht mehr vor**. Neuer Slug **`draft-has-conflicts`** (409 mit `conflicts[]`) beim Annehmen eines Entwurfs mit harten Konflikten (Singular und Plural); weiche blockieren nicht. Nachgezogen im selben Zug: `types.ts`, `client.ts`/`hooks.ts` (Query-Schlüssel je Kennung `qk.draft(id)`, `qk.drafts`), Attrappe (`mock-api.ts`, `store.ts`), Web: Entwurfsliste `/entwurf`, Entwurfsseite `/entwurf/:id`, Leiste „n Entwürfe" | Mehrere Entwürfe zwischenspeichern (T2-3.4, E-52): Migration V11 (`DROP INDEX timetable_one_draft`, `timetable.name varchar(80)`, Bestand → „Entwurf"); `timetable_one_active` bleibt. Additiv nach Freeze: alte Clients (App) laufen über den Singular-Alias unverändert weiter. Harte Konflikte sperren das Annehmen, weil der aktive Plan die Wahrheit für Lehrkräfte und App ist — ein Plan mit Doppelbelegung darf nicht gültig werden |

Unverändert aus v0 (bewusst): alle 39 v0-Operationen und -Schemas, `servers: /`, Slugs `validation`,
`unauthenticated`, `forbidden`, `not-found`, `in-use`, `draft-exists`, `solving`, `master-data-incomplete`,
`pinned-contradiction`, `data-exists`, `internal`; Wochentage `MO TU WE TH FR SA`; Rollen-Lesehilfe `x-role`.

## 3. Regel: Wie ein Modul den Vertrag erweitern darf

1. **Nur additiv, immer.** Neue Endpunkte, neue **optionale** Felder (in Antworten mit `null`-fähigem Typ oder
   sinnvollem Standard, in Requests ohne `required`). Nie umbenennen, nie entfernen, nie einen Typ ändern, nie ein
   Pflichtfeld hinzufügen. **Enum-Werte zählen als brechend** (`EntryKind`, `ConflictType`, `EventKind`, …): Web und
   App verzweigen darüber — ein neuer Wert braucht alle drei Clients im selben Zug und einen Eintrag hier.
2. **Ein Modul, seine Pfade.** Jedes Modul erweitert nur die Pfade seiner Zeile in Abschnitt 6 (Tabelle Pfad →
   Modul). Ein Modul, das ein Feld in einem fremden DTO braucht, stellt eine Karte an den Eigentümer.
3. **Derselbe Merge Request** enthält: `openapi.yaml` (Schema + `example` je 2xx + passende `$ref`-Fehler),
   `frontend/src/api/types.ts` (Abschrift), die Zeile in diesem Dokument (Abschnitt 2 als Fortsetzung „V-15 …")
   und den Controller. Vorher `npm run lint` (0 Fehler) und einmal `curl` gegen `npm run mock`. Label `api-change`,
   Reviewer Rolle 4 plus betroffene Frontend-Rolle, Post im Mattermost.
4. **Fehler nur als `ProblemDetail`** mit einem Slug aus der Liste in `components.schemas.ProblemDetail.description`.
   Ein neuer Slug ist eine Vertragsänderung (Zeile hier + Beispiel im Vertrag + Handler in `shared/api/ApiExceptionHandler`).
   Konflikte und Warnungen sind nie Fehler.
5. **Beispiele** kommen aus dem Testdatenbestand nach dem Leitszenario (IDs wie in `openapi.yaml`, Kommentarblock
   vor `/api/timetable`); ein Beispiel, das dem Pflichtenheft widerspricht, ist ein Fehler im Vertrag.
6. **Rolle** je Operation als `x-role` **und** als Pfadregel in `auth/api/SecurityConfig`: neue Pfade unter
   `/api/**` sind automatisch `PLANNING`; wer etwas für `TEACHER` öffnet, ändert `SecurityConfig` und `SecurityConfigTest`.
7. **Version**: jede additive Erweiterung hebt `info.version` um `0.1.0` (1.1.0, 1.2.0 …); `2.0.0` gibt es vor dem
   24.09. nicht. **API-Freeze Fr 11.09.** (Rückgrat sagt „Fr 12.09." — der 12.09.2026 ist ein Samstag, Lernnotiz 203):
   danach gilt nur noch Punkt 1–7, kein Umbau.
8. **Kann-Felder** stehen als optional im Vertrag mit Beschreibung „Kann (E-xx)". Ein Backend, das sie noch nicht
   baut, antwortet 400 `validation` mit `errors[{field, message: "noch nicht verfügbar"}]` — nie stillschweigend ignorieren.
9. Was der Vertrag nicht regelt, regelt der Mock nicht: weicht die Attrappe (`frontend/src/mock/**`) vom Server ab,
   gewinnt der Server und die Attrappe wird nachgezogen (eine Person, ein MR).

## 4. Module — Verantwortung, Schnittstellen, Tests

Paketwurzel `de.wsp3_1` (Starter-Abweichung A1, nicht `de.pushnpull.timetable`). Schichten `api → domain →
persistence` je Modul (ArchUnit 2, 3). Zehn Module: die neun aus `backbone/architektur.md` plus **`sampledata`**.

**`shared`** — Verantwortung: gemeinsame Typen ohne Fachlogik: `Weekday`, `Role` (zieht aus `auth.persistence` hierher),
`RuleType`, `RuleScope`, `Severity` (neu), `CurrentUser` (Record `id, username, role, teacherId` mit statischem
`require()` aus dem `SecurityContext`), `ApiExceptionHandler`/`ApiProblems` (RFC 9457), `NotFoundException`,
`ConflictException(slug, detail, extras)`. Schnittstellen: hängt von nichts ab; jedes Modul darf es benutzen; der
Token-Filter in `auth.api` legt `CurrentUser` als Principal ab, damit `planning`, `substitution`, `changelog` den
Verursacher kennen, ohne `auth` zu importieren. Tests: `ApiExceptionHandlerTest` (Bean-Validation → 400 mit `errors[]`,
`ConflictException` → 409 mit Slug und Extras, unbekannter Pfad → 404, `IllegalArgument`/Rest → 500 `internal`),
`CurrentUserTest` (ohne Authentication → Ausnahme).

**`config`** — Verantwortung: `SystemConfig` (eine Zeile), `GET /api/health`, `GET/PUT /api/config`
(Bean-Validation: `maxSolveSeconds ≥ 1`, `state` aus der 16er-Liste). Schnittstellen: `SystemConfigService.get()` für
`rules` (Bundesland beim Reset) und `planning` (Rechenzeit) — beide lesen nur; nichts anderes. Tests:
`ConfigControllerIT` (PUT `maxSolveSeconds=30` überlebt Kontext-Neustart, PUT `state=XX` → 400, TEACHER → 403),
`HealthControllerTest` (schon da, bleibt).

**`auth`** — Verantwortung: Konten, Token (SHA-256, 30 Tage seit F-5a — `AUTH_TOKEN_TTL`), `SecurityConfig` mit den Pfadregeln, `TokenAuthenticationFilter`,
`AdminBootstrap`, `POST /api/auth/login`, `DELETE /api/auth/token`, `GET /api/me`, `PUT /api/me/password`,
`GET/POST /api/users`, `PUT /api/users/{id}/password`, tägliches Löschen abgelaufener Token (`@Scheduled`).
Schnittstellen: benutzt `masterdata` (Konto ↔ `Teacher`, `displayName` = Name der Lehrkraft) und `shared`; liefert
nichts an andere Module außer `CurrentUser` über den Filter; `changelog` und `substitution` referenzieren
`UserAccount` per JPA (Abschnitt 6). Tests: `SecurityConfigTest` (da; erweitern: `GET /api/timegrid` mit TEACHER 200,
`GET /api/timetable` mit TEACHER 403, `POST /api/draft/lessons` mit TEACHER 403), `AuthFlowIT` (da), `UserControllerIT`
(TEACHER ohne `teacherId` → 400, doppelter Benutzername → 409 `in-use`, Passwort < 8 → 400, `PUT …/password`
danach Login), `TokenCleanupTest`.

**`masterdata`** — Verantwortung: `Teacher`, `Subject`, `Room`, `SchoolClass`, `ClassSubject`, `Timeslot` mit
Repositories, Controller für `/api/teachers`, `/api/subjects`, `/api/rooms`, `/api/classes` (+`/subjects`),
`/api/timegrid`; `WarningChecker` (T-W1.4/1.5), `plannedHours`, Löschen-vs-Deaktivieren (409 `in-use` mit Zählung),
`PUT /api/timegrid` als Upsert nach `(day, position)` mit 409 bei belegten Zeitfenstern. Schnittstellen: benutzt nur
`shared`; wird von `auth`, `rules` (nur Jahrgänge), `planning`, `substitution`, `sampledata` gelesen; die
In-use-Prüfung fragt **nicht** `planning`, sondern zählt `lesson`-Zeilen über eine native Repository-Abfrage
(`countLessonsByTeacher(teacherId)` in `ClassSubjectRepository`: `lesson` → `class_subject` → `teacher`), damit die
Abhängigkeitsrichtung bleibt. Tests: `WarningCheckerTest` (T-W1.4, T-W1.5, Meldungstexte wörtlich), `TeacherServiceIT`
(T-W1.1, T-W1.3 Schritte 1–3 und 5a), `ClassSubjectsIT` (400 mit zwei Feldfehlern bei leeren Pflichtangaben, Fach
doppelt → 400), `TimegridIT` (Upsert, 409 `in-use`, Sperrzeiten einer Lehrkraft verschwinden mit dem Zeitfenster),
`DeleteVsDeactivateIT` (Fach in Klasse → 409, Raum in Stunde → 409, Klasse mit Stunde → 409), je Ressource ein
`@WebMvcTest` (DTO-Form).

**`rules`** — Verantwortung: `Rule`, `RulePreset`, `GET /api/rules`, `PUT /api/rules/{id}`, `POST /api/rules/reset`,
`POST/DELETE /api/rules` (nur Jahrgangszeilen, `scope = CLASS`), `label` je Typ (deutsch), `RuleResolver`
(`EffectiveRule` je Klasse/Lehrkraft, Jahrgangszeile vor schulweiter; `docs/SOLVER-PLAN.md` Abschnitt 9).
Schnittstellen: benutzt `shared` (Enums) und `config` (Bundesland beim Reset); liefert `RuleResolver.resolve(...)` an
`planning` (`ConflictChecker`, `SolveService`). Tests: `RuleResolverTest` (Jahrgang schlägt schulweit; inaktive Regel
fehlt; Lehrkraft-Zeile je Lehrkraft; Pausenregel ohne Objekt), `RuleControllerIT` (`value 0` → 400 und alter Wert bleibt;
`reset` kopiert die Preset-Zeilen des eingestellten Bundeslands; `POST` ohne `grade` → 400; `DELETE` schulweit → 409;
Unique `(type, scope, grade)` → 409).

**`planning`** — Verantwortung: `Timetable`, `Lesson`, Plan-Lebenszyklus, `DraftService` (anlegen `EMPTY`/`COPY_ACTIVE`,
`addLesson`, `moveLesson`, `setPinned`, `removeLesson`, `syncRequiredLessons`, `storeResult`, `markFailed`, `accept`
als eine Transaktion mit Verlaufseintrag), `ConflictChecker` auf `Assignment`-Records mit `CheckContext`,
`ConflictService.check(List<Assignment>)` (baut den Kontext aus Repositories), `SolveService` (Vorbedingungen, Start,
Abbruch, `buildInput`), `TimetableController` (`GET /api/timetable`), `DraftController` (`/api/draft/**`),
`PlanReader.activeAssignmentsAt(LocalDate)` (berücksichtigt `validFrom`). Schnittstellen: benutzt `masterdata`,
`rules`, `config` (Rechenzeit), `solver` (`SolverRunner`, `SolveInput`, `SolveResult`), `changelog`
(`ChangelogService.record`), `shared`; liefert `ConflictService` und `PlanReader` an `substitution`, `DraftService`/
`SolveService` an `sampledata`. Tests: `ConflictCheckerTest` (T-W1.2, T-W2.1, T-W3.1, T-W3.2, Raumart, Pause,
Jahrgangsregel, gleiche Konstanten wie der ConstraintVerifier), `DraftServiceIT` (COPY_ACTIVE pinnt konfliktfreie
Stunden, zweiter Entwurf → 409 aus dem Teilindex, `syncRequiredLessons` ergänzt genau die Differenz, `accept` archiviert
und schreibt `TIMETABLE_ACCEPTED`, `warnings` bei Ausnahmen ab heute, `validFrom` gespeichert), `SolveServiceIT`
(alle Fälle aus `docs/SOLVER-PLAN.md` 10.3), `DraftControllerIT` (409 `solving` bei PATCH/POST/DELETE/accept während
eines Laufs; offene Stunde ohne `roomId` → 400; Antwort ist immer der ganze Entwurf).

**`solver`** — Verantwortung: Timefold-Modell (`SolverLesson`, `SolverTimetable`, Facts), `TimetableConstraintProvider`
(H1–H5, M1, R1–R3 hart/weich, S1, S2), `SolverTranslator`, `SolverRunner` (asynchron, blockierend, Abbruch, Status),
Records `SolveInput`/`SolveResult`. Schnittstellen: benutzt nur `shared` (Enums, `Weekday`); kein JPA, kein Spring
Data (ArchUnit 1); wird nur von `planning` aufgerufen. Tests: `TimetableConstraintProviderTest` (je Constraint
Positiv- und Negativfall, Tabelle in `docs/SOLVER-PLAN.md` 10.1), `SolverTranslatorTest` (10.2).

**`substitution`** — Verantwortung: `ChangeEvent`, `LessonException`, `Holiday`, `WeekResolver` (Regelplan + Ereignisse +
Ausnahmen + Ferien → `ResolvedLesson` je Tag, Sichten Schule/Klasse/Lehrkraft/Raum, `GIVEN_AWAY` in der Lehrkraftsicht),
`ChangeEventService`, `SubstitutionOptionsService` (keine Berechnung), `ExceptionService` (Folgekonflikte des Tages,
Verlauf), Controller `WeekController` (`GET /api/timetable/week`), `MyWeekController` (`GET /api/me/timetable/week`,
Lehrkraft aus `CurrentUser.teacherId`), `EventController`, `SubstitutionController` (`/pending`, `/options`),
`ExceptionController`, `HolidayController`; seit dem 05.09. zusätzlich `HolidayPreset` (mitgelieferte Ferien- und
Feiertagsvorlage je Bundesland, E-51) mit `GET /api/holidays/preset-years` und `POST /api/holidays/preset`.
Schnittstellen: benutzt `masterdata` (Namen, Kürzel, Farben, Sperrzeiten,
Fächer), `planning` (`PlanReader`, `ConflictService`), `changelog` (`record`), `auth` (`UserAccount` für `createdBy`),
`config` (`SystemConfigService.get().state()` — das Bundesland der Vorlage, nur lesend, wie `rules` beim Reset),
`shared`; liefert nichts an andere Module. Tests: `WeekResolverTest` (T-W5.1, T-A2.1, Verlegung erzeugt
`CANCELLED`+`MOVED`, Zusatzstunde `ADDED`, Ferientag ohne Einträge, Raumsicht zeigt nur belegte Zellen, `validFrom`
wählt den Plan), `SubstitutionOptionsTest` (T-W6.1; inaktive und abwesende Lehrkräfte fehlen; passende Raumart zuerst),
`SubstitutionServiceIT` (T-W6.1 Ende-zu-Ende über `options` → `exceptions`; `exception-exists` 409; Löschen schreibt
`EXCEPTION_DELETED` und macht die Stunde wieder `NEEDS_SUBSTITUTION`), `EventControllerIT` (`affected` je Art,
`hint` bei null Treffern, `EXTRA_LESSON` legt `ADDED` an, `DELETE` entfernt Zusatzstunde mit), `WeekControllerIT`
(T-W4.1 vier Sichten), `MeControllerIT` (T-A1.1 inkl. 401/403), `HolidayIT` (T-110), `HolidayPresetIT` (Zählung je
Land und Schuljahr aus V8, Übernehmen idempotent, Teilgebiet nicht enthalten, 409 `preset-missing`).

**`changelog`** — Verantwortung: `ChangelogEntry` (`@Immutable`), `ChangelogService.record(...)` und
`find(filter, page, size)`, `GET /api/changelog`; kein PUT/DELETE, Repository ohne `delete*`/`save`-Überschreibung
außer `save`. Schnittstellen: benutzt `auth` (`UserAccount` per JPA für `username`) und `shared`; wird von `planning`
(Annehmen) und `substitution` (Ausnahmen) beschrieben. Tests: `ChangelogIT` (T-W7.1 Filter Klasse 7a: genau ein
Eintrag mit beiden Anlässen; Paginierung `total`), `ChangelogControllerTest` (kein Mapping für PUT/DELETE unter
`/api/changelog` — `RequestMappingHandlerMapping` durchsuchen; `DELETE /api/changelog/1` → 404), ArchUnit-Zusatz: keine
Klasse in `..changelog..` ruft `deleteById`/`deleteAll`.

**`sampledata`** (neu, Paket `de.wsp3_1.sampledata`) — Verantwortung: `SampleDataLoader` mit `loadSmall()` (Pflichtenheft-
Testdaten inkl. aktivem Plan Mo/Di — nur für Tests, kein Endpunkt) und `loadFull()` (12/24/18, deterministisch, Konten
nur löschen), `POST /api/sample-data` (409 `data-exists`, `replace`) — **Einstieg ist `load(boolean ersetzen)`, das
Prüfung und Laden in EINER Transaktion hält**: stünde die Prüfung im Controller, könnte zwischen „sind schon
Grunddaten da?" und dem Laden jemand eine Klasse anlegen, die `clear()` dann mitnähme, obwohl `replace` nicht gesetzt
war; ohne `ersetzen` wird gar nicht geleert (Nacharbeit 05.09.) —, gefüllter aktiver Plan über
`SolveService.solveBlocking` (10 s). Schnittstellen: benutzt `masterdata` (Repositories), `planning` (`DraftService`,
`SolveService`), `shared`; das Leeren bei `replace` läuft über `JdbcTemplate` (`DELETE FROM user_account WHERE
role='TEACHER'`, `TRUNCATE lesson, timetable, change_event, lesson_exception, exception_event, changelog_entry,
class_subject, teacher_subject, teacher_preferred_timeslot, teacher_blocked_timeslot, teacher, subject, room,
school_class, timeslot, holiday RESTART IDENTITY CASCADE` — Regelwerk, Konfiguration, `admin` bleiben), damit das Modul
nicht von `substitution`, `changelog`, `auth` abhängt. Tests: `SampleDataIT` (FULL: 12/24/18, aktiver Plan mit ≥ 200
belegten Stunden ohne harte Konflikte, `admin` bleibt, zweiter Aufruf ohne `replace` → 409, mit `replace` gleiche
Zählung), `SmallFixtureTest` (SMALL: Meyer/Schulz/Weber, 7a/8b/9c, R101/R204/P1, 35 Zeitfenster, sieben Stunden Mo/Di,
Meyer Mo 4 / Di 1).

Erlaubte Modulabhängigkeiten (Tabelle für `ArchitectureTest`, ersetzt die Tabelle der Schablone):

| Modul | darf benutzen |
|---|---|
| `shared` | — |
| `config`, `masterdata` | `shared` |
| `rules` | `config`, `shared` |
| `auth` | `masterdata`, `shared` |
| `changelog` | `auth`, `shared` |
| `solver` | `shared` |
| `planning` | `masterdata`, `rules`, `config`, `solver`, `changelog`, `shared` |
| `substitution` | `masterdata`, `planning`, `changelog`, `auth`, `config`, `shared` |
| `sampledata` | `masterdata`, `planning`, `shared` |

Neu gegenüber der Schablone: `rules → config`, `planning → config` (Lernnotiz 66, Entwurfsdoku Kap. 3), `changelog → auth`
und `substitution → auth` (JPA-Verweis auf `UserAccount`), `substitution → config` (Bundesland der Ferienvorlage,
E-51, ergänzt 05.09.), `sampledata`. Zyklenfrei (geprüft von Hand: `auth` hängt
von nichts ab, das von `auth` abhängt). `example` bleibt ausgenommen.

**Wer fremde Tabellen liest (Stand 05.09.).** Die Modultabelle oben gilt für Java-Abhängigkeiten. Rohes SQL geht an
ihr vorbei — `JdbcTemplate` und `nativeQuery = true` lesen eine fremde Tabelle, ohne dass ArchUnit etwas sieht. Die
Grenze ist deshalb zusätzlich als **Regel 5** im `ArchitectureTest` festgehalten: `JdbcTemplate` darf nur in den hier
genannten Klassen vorkommen, jede weitere Stelle lässt den Test fehlschlagen.

| Klasse | liest/schreibt fremd | warum vereinbart |
|---|---|---|
| `sampledata.domain.SampleDataLoader` | 16 Tabellen aller Module (leeren und füllen) | M-1: das Modul legt keine eigenen Entities an und muss unabhängig vom Bauzustand der anderen laufen |
| `changelog.domain.ChangelogService` | `JOIN user_account` (auth), `LEFT JOIN school_class` (masterdata) | CH-2: die Antwort braucht `username` und `classShort`, beides aus Modulen, die `changelog` nicht benutzen darf |
| `masterdata.persistence.ClassSubjectRepository` | sechs `nativeQuery`-Zählungen auf `lesson`/`timetable` (planning) | die In-use-Prüfung muss wissen, ob eine Zeile in einem Plan steckt; `masterdata` darf `planning` nicht kennen |

Nicht mehr auf dieser Liste: `planning.domain.DraftService`. Die Warnung „n Vertretungen verfallen" las bis zum
05.09. `lesson_exception` per SQL (PL-2) und fragt jetzt `shared.domain.ExpiringExceptions`.

## 5. Bauphasen

Jede Phase liefert: Code + Tests grün (`scripts/ci-backend-test.sh` — das Skript wiederholt seit dem 05.09. beide
Testaufgaben selbst, ein angehängtes `--rerun` gälte nur für die letzte), die Zeilen der `docs/ABNAHME-CHECKLISTE.md`
ihrer Phase per `curl` belegt (Ausgabe in `docs/abnahme/<phase>.md`), Lernnotizen. Reihenfolge ist Abhängigkeit,
nicht Kalender.

| Phase | Module | baut | fertig, wenn |
|---|---|---|---|
| **backend1** | `shared`, `config`, `auth`, `masterdata`, `rules` | Enums + `CurrentUser` + `Role` nach `shared`; Entities/Repositories/Controller Grunddaten; `WarningChecker`; In-use-Prüfung; Timegrid-Upsert; `Rule`, Reset, Jahrgangszeilen, `RuleResolver`; `/api/config`; `/api/users`, `/api/me/password`; `SecurityConfig`-Pfadregeln vollständig, `/api/clients/**` aus `permitAll` raus (Team-Beispiel bleibt, wird nur nicht mehr freigegeben); `V5__holiday_valid_from_solve_state.sql` (Abschnitt 6); `SampleDataLoader.loadSmall()` als Testfixture in `sampledata` (Datenteil ohne Plan — der Plan kam in backend2, siehe M-3) | Checkliste I-1–I-3, L-1, L-2, G-1–G-11, R-1–R-3, A-1, A-6, K-3, K-4; Tests der fünf Module; `ArchitectureTest` mit neuer Tabelle grün |
| **backend2** | `planning`, `solver`, `changelog`, `sampledata`, `substitution` **Teil 1** (`WeekResolver` ohne Ereignisse/Ausnahmen, `WeekController`, `MyWeekController`, `Holiday`-Entity leer nutzbar) | alles aus `docs/SOLVER-PLAN.md`; `ConflictChecker`; Entwurf-Endpunkte; Annehmen mit Verlauf; `GET /api/timetable`, `/api/timetable/week`, `/api/me/timetable/week`; `GET /api/changelog`; `POST /api/sample-data` mit gefülltem Plan; Kann `classId` und `validFrom` | Checkliste P-1–P-7, D-1–D-5, S-1–S-4, A-2, A-3, V-6 (Endpunkt), T-W1.2, T-W1.3 (4), T-W2.1, T-W3.1, T-W3.2, T-W4.1, T-W7.1 (Format), T-W8.1, T-W9.1, T-A1.1 |
| **backend3** | `substitution` **Teil 2** | `ChangeEvent`, `LessonException`, `ExceptionService`, `SubstitutionOptionsService`, `WeekResolver` mit Ereignissen/Ausnahmen/Ferien (`NEEDS_SUBSTITUTION`, `SUBSTITUTED`, `GIVEN_AWAY`, `MOVED`, `CANCELLED`, `ADDED`), Controller Events/Pending/Options/Exceptions/Holidays, `scripts/abnahme.sh` | Checkliste V-1–V-6, A-4, A-5, T-W1.3 (5b), T-W5.1, T-W6.1, T-W7.1, T-A2.1; `scripts/abnahme.sh` läuft auf frischer Datenbank durch |
| **frontend** | `frontend/` | echter Client gegen das Backend (T-502): `deleteEvent` in `types.ts`/`client.ts`; Attrappe nachziehen, wo der Server abweicht (PATCH offene Stunde → 400 statt Raum raten; `exception-exists` 409 statt Ersetzen; `preset-missing` entfällt — `state` ist Enum; Passwort ≥ 8 Zeichen; `POST /api/sample-data` legt keine Konten an); Kann-Optionen (`classId`, `validFrom`) sichtbar, sobald backend2 sie liefert; Playwright-Durchlauf `frontend/scripts/abnahme.spec.ts` über Abschnitt 1 der Checkliste mit Screenshots (Chrome headless, `1440×900`) | `scripts/ci-frontend-build.sh` grün; Login/Logout/401-Redirect am echten Server; alle Browser-Schritte der Checkliste belegt; Rolle Lehrkraft sieht nur `/mein-plan` |

Was **keine** Phase baut: Android (Repo `android-app`), `substitutionsThisWeek`, Blockung, K1 `classGaps`, Push,
CSV-Import, Krankmeldung per App, Rollenverwaltung über zwei Rollen hinaus (die dritte Rolle `STUDENT` kam am
05.09. mit E-50 dazu — sie ist eine Sicht, keine Verwaltung).

**Offen und bewusst nicht gebaut (Befund SICHERHEIT-4, 05.09.):** Ein Konto lässt sich weder löschen noch
sperren — es gibt kein `DELETE /api/users/{id}` und kein `active` am Konto, und `Teacher.active = false`
(„Deaktivieren statt Löschen") wirkt **nicht** auf die Anmeldung. Eine ausgeschiedene Lehrkraft und eine
aufgelöste Klasse behalten damit Zugang, bis die Planung von Hand ein neues Passwort setzt. Der Fix ist klein
(`DELETE` mit dem vorhandenen `ON DELETE CASCADE` auf `auth_token`, oder eine `active`-Prüfung in
`AuthService.login`/`authenticate`), erweitert aber Vertrag **und** Datenmodell (`produktdaten.tex` kennt am
Benutzerkonto kein Aktiv-Merkmal) — deshalb eine Entscheidung des Auftraggebers, kein Korrekturlauf.

## 6. Gemeinsame Festlegungen für alle Phasen

**Pfad → Modul → Controller (die Zeile eines Moduls in Regel 3.2):**

| Pfade | Modul | Controller |
|---|---|---|
| `GET /api/health`, `GET/PUT /api/config` | `config` | `HealthController` (da), `ConfigController` |
| `/api/auth/*`, `/api/me`, `/api/me/password`, `/api/users`, `/api/users/{id}/password` | `auth` | `AuthController` (da), `UserController` |
| `/api/teachers**`, `/api/subjects**`, `/api/rooms**`, `/api/classes**`, `/api/timegrid` | `masterdata` | `TeacherController`, `SubjectController`, `RoomController`, `ClassController`, `TimegridController` |
| `/api/rules**` | `rules` | `RuleController` |
| `POST /api/sample-data` | `sampledata` | `SampleDataController` |
| `GET /api/timetable`, `/api/draft**` | `planning` | `TimetableController`, `DraftController` |
| `GET /api/timetable/week`, `GET /api/me/timetable/week`, `/api/events**`, `/api/substitutions/**`, `/api/exceptions**`, `/api/holidays**` | `substitution` | `WeekController`, `MyWeekController`, `EventController`, `SubstitutionController`, `ExceptionController`, `HolidayController` |
| `GET /api/changelog` | `changelog` | `ChangelogController` |

`GET /api/me/timetable/week` liegt in `substitution`, nicht in `auth`: es braucht den `WeekResolver`, und `auth →
substitution → … → auth` wäre ein Zyklus. Spring erlaubt zwei Controller mit demselben Pfadpräfix.

**Sicherheit** (`auth/api/SecurityConfig`, E-45, Stand 04.09.): `permitAll` `GET /api/health`, `POST /api/auth/login`;
`authenticated` `DELETE /api/auth/token`, `/api/me/**`, `GET /api/timegrid`; **alles Übrige unter `/api/**`
`hasRole("PLANNING")`**. Eigentum ist keine Rollenfrage: `/api/me/…` liest die Lehrkraft aus `CurrentUser`, nie aus
einem Parameter. `@PreAuthorize` an Dienstmethoden nur, wo ein Dienst von mehreren Controllern erreichbar ist.

**Aktor**: `CurrentUser.require()` in jedem schreibenden Dienst, der Verlauf oder `createdBy` schreibt; Entities
verweisen per `@ManyToOne(fetch = LAZY) UserAccount` (Spalten `created_by_id`, `user_id` aus V4).

**Migration V5** (`backend/src/main/resources/db/migration/V5__holiday_valid_from_solve_state.sql`, Phase backend1,
V1–V4 bleiben unverändert — Flyway-Prüfsumme):
`ALTER TABLE timetable ADD COLUMN valid_from date, ADD COLUMN solve_last_score varchar(60), ADD COLUMN solve_error varchar(500);`
`CREATE TABLE holiday (id bigserial PRIMARY KEY, name varchar(120) NOT NULL, date_from date NOT NULL, date_to date NOT NULL,
kind varchar(20) NOT NULL CHECK (kind IN ('VACATION','PUBLIC_HOLIDAY')), CONSTRAINT holiday_period CHECK (date_to >= date_from));`
**Migration V6** (`V6__changelog_reasons_text.sql`, Nacharbeit backend3 am 05.09., V1–V5 unverändert):
`ALTER TABLE changelog_entry ALTER COLUMN reasons TYPE text;` — ein Anlass ist Freitext bis 255 Zeichen
und eine Stunde darf mehrere tragen; ab fünf reichten die 1000 Zeichen aus V4 nicht, und der Dienst warf
ganze spätere Anlässe weg (siehe CH-6).

`ApplicationIT` prüft danach „V1 bis V8 gelaufen" (V7 Klassenkonto, V8 Ferienvorlagen). Wer eine Datenbank aus der Schablone hat: `docker compose down -v`.

**DTO-Regeln** (festgelegt 05.09. nach der Prüfung backend1 — vorher stand hier nur „Records in `<modul>/api`,
Mapper neben dem Controller", und das ist mit ArchUnit-Regel 2 und 3 nicht erfüllbar: `api` darf keine `@Entity`
sehen, `domain` kein `api`): **verbindlich für jedes neue Modul** sind die Antwort-Records in `<modul>/api`; der
Dienst gibt einen Record aus `<modul>/domain` zurück (z. B. `auth.domain.Account`, `rules.domain.RuleView`,
`config.domain.SystemConfigData`), der Controller bildet ihn mit einer statischen Methode neben sich auf den
api-Record ab. Kein MapStruct. Zweite, im Bestand vorhandene Form: `masterdata` legt seine Records ins
Modulwurzel-Paket (`de.wsp3_1.masterdata.MasterdataDtos`, MD-1) und spart sich damit die zweite Recordschicht —
zulässig, aber nicht der Weg für backend2 und backend3.

**Warum zwei Schichten, wenn die meisten Paare gleich aussehen** (Befund BACKEND-7, 05.09.): In fünf Modulen sind
`*View` und `*Dto` heute Feld für Feld identisch, und die `of()`-Methoden kopieren nur — rund 400 Zeilen, die bei
jeder Feldänderung zweimal angefasst werden. Das ist der Preis für eine Grenze, die genau dann zählt, wenn die
beiden auseinandergehen sollen, und dieser Fall steht im Bestand: `WeekViews.EntryView` führt `subjectId`,
`SubstitutionDtos.EntryDto` lässt es weg — der Wocheneintrag des Vertrags kennt vom Fach nur Kürzel und Farbe.
Ohne zweite Schicht gäbe es dafür nur zwei Wege, und beide sind schlechter: das Feld in den Vertrag aufnehmen
(eine Zusage, die niemand braucht und die bleibt) oder `@JsonIgnore` an den domain-Record hängen (dann trägt
`domain` Serialisierungswissen, und ArchUnit-Regel 3 verliert ihren Sinn). **Deshalb bleibt es bei zwei Schichten
— nicht aus Gewohnheit, sondern weil der Vertrag nicht der Domäne folgen soll.** Wo ein Modul diese Freiheit
absehbar nie braucht, ist MD-1 die ehrlichere Form; `masterdata` ist der Fall. Wer neu baut, nimmt die zwei
Schichten: ein Modul, das später eine Abweichung braucht, muss sonst alle Controller anfassen.

`label`
der Regeln, `message` der Konflikte und Warnungen, `detail` der Fehler: deutsch, Schulsprache, Wortlaut wie in
`openapi.yaml`-Beispielen und `frontend/src/mock/logic/*` (dort stehen die geprüften Texte, z. B. „Schulz ist mit 3
Wochenstunden über dem Deputat von 2.").

**Konflikte, Warnungen, Vertretungsbedarfe** werden nie gespeichert (E-31); jeder GET berechnet neu.

**Zeit**: Server rechnet in `Europe/Berlin` (`spring.jackson.time-zone` bzw. `TimeZone.setDefault` beim Start —
`docker compose` gibt `TZ=Europe/Berlin` mit), „heute" für `warnings` beim Annehmen ist das Serverdatum.

## 7. Abweichungen von Rückgrat und Entwurfsdoku (zusätzlich zu STARTSTAND Abschnitt 7 und SOLVER-PLAN Abschnitt 12)

| # | Quelle | hier | Grund |
|---|---|---|---|
| D-1 | `architektur.md`: `solver` darf `rules` (nur Enums) | Enums `RuleType`, `RuleScope`, `Severity` in `shared`; `solver → shared` bleibt wie in der Schablone | `ArchitectureTest` der Schablone erlaubt `solver` nur `shared`; drei Enums neben `Weekday` sind billiger als eine Modulabhängigkeit |
| D-2 | `architektur.md`: `SampleDataLoader` in `masterdata` | eigenes Modul `sampledata` | der volle Beispielbestand enthält einen angenommenen Plan (E-46) — `masterdata` darf `planning` nicht kennen |
| D-3 | `architektur.md`: `changelog → shared`, `substitution` ohne `auth`; E-45 `auth` ohne Abhängige | `changelog → auth`, `substitution → auth` | `changelog_entry.user_id` und `change_event.created_by_id` (V4) verweisen auf `user_account`; der JPA-Verweis braucht die Entity. Kein Zyklus |
| D-4 | Entwurfsdoku Kap. 5: `SolveService` in `solver` | `SolveService` in `planning`, `SolverRunner` in `solver` | siehe SOLVER-PLAN 12 |
| D-5 | Entwurfsdoku kl-dtos: `ExceptionRequest` mit `addedClassId`/`addedSubjectId` | nicht im Vertrag | V-13 |
| D-6 | `api.md`/Entwurfsdoku: `substitutionsThisWeek` (Soll) | nicht im Vertrag | am 04.09. gestrichen |
| D-7 | Pflichtenheft `testdurchlauf.tex`: kein Schritt „Entwurf annehmen"; die 9c-Stunde bleibt nach T-W3.2 im Plan | Checkliste P-5b (9c-Stunde löschen) und P-6 (annehmen) | ohne P-6 lesen W-5 bis A-2 einen leeren aktiven Plan; ohne P-5b ist R101 in T-W4.1 an Mo 5. Stunde belegt (Vorschlag mit Fundstelle an Rolle 1, nicht geändert) |
| D-8 | Rückgrat: API-Freeze „Fr 12.09." | Fr 11.09. | 12.09.2026 ist Samstag (Lernnotiz 203) |
| D-9 | `frontend/src/mock`: zweite Ausnahme ersetzt die erste; offene Stunde bekommt beim Verschieben den ersten Raum; `preset-missing`; Passwort ≥ 4 | Server: 409 `exception-exists`; 400 ohne Raum; kein `preset-missing`; ≥ 8 | Server gewinnt (Regel 3.9); Attrappe zieht in der Frontend-Phase nach |
| D-10 | `datenmodell.md`: Seed `rule` TEACHER-Woche 28 | V2 (erzeugt) hat 26 für SH | Recherche 04.09. (`bundesland-regelwerte.json`); die Vertragsbeispiele von `GET /api/rules` und `POST /api/rules/reset` sind am 05.09. auf 26 gezogen worden (Beispieländerung, kein Bruch) |
| D-11 | `entscheidungen.md` E-47: „Pausenregel weich" | `NO_LESSONS_IN_BREAKS` ist **hart** (`HARD`) | Seed V2, die erzeugte Attrappe `rule-presets.ts` und Checkliste R-3 („Pause hart") sagen alle drei hart; laut `SOLVER-PLAN.md` hat die Regel keinen Wertebereich (`value` ist fest 1), sie lässt sich also nicht sinnvoll gewichten. Am 05.09. auch im Vertragsbeispiel auf `HARD` gezogen; Nachtrag in `backbone/entscheidungen.md` steht aus (Vorschlag mit Fundstelle an Rolle 1, dort nichts geändert) |

## 8. Ungeprüft und offen

- ~~Kein Java auf diesem Mac~~ — **erledigt 05.09.**: `scripts/ci-backend-test.sh` läuft im Arbeitsbaum
  (Container `gradle:9.7.1-jdk21` + `postgres:16-alpine`), zuletzt `BUILD SUCCESSFUL in 2m 6s`,
  135 Unit- und 196 Integrationstests, 0 Fehler (Stand 05.09. 22:00). Timefold-Paketpfade und `groupBy`-Signaturen sind damit
  gemessen (SV-1 bis SV-8 im Modulabschnitt `solver`).
- Der Prism-Lauf lief mit `@stoplight/prism-cli` 5.14.2 aus `starter/api-contract/node_modules` auf Port 1509 (nicht
  1501); der Compose-Dienst `mock` mit derselben Datei ist nicht gestartet worden.
- ~~Docker lief nicht in dieser Sitzung~~ — **erledigt 05.09. (Nacharbeit backend2)**: der reguläre Compose-Stack
  lief (`cd deployment && BACKEND_PORT=8089 docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
  --build db backend`), D-1 bis A-3 sind dagegen gemessen. Nicht gestartet wurden die Dienste `web` (nginx) und
  `mock` — geprüft wurde am Backend direkt.
- **Bewusst nicht gebaut (Nacharbeit backend2):** Start und Bearbeitung des Entwurfs sind nicht gegeneinander
  serialisiert. Zwei gleichzeitige Berechnungen schließen sich seit 05.09. aus (SV-10), eine Änderung neben einem
  startenden Lauf nicht: wer `PATCH /api/draft/lessons/{id}` abschickt, während `POST /api/draft/solve` gerade
  committet, verliert seine Änderung mit dem Ergebnis des Laufs, und `syncRequiredLessons` kann eine Zeile zu viel
  anlegen (der Entwurf meldet sie als `warnings`). Beides beschädigt nichts in der Datenbank. Ein Zeilenschloss auf
  `timetable` wäre der vollständige Weg; er ist für eine Anwendung, in der eine Person plant, nicht bezahlt worden.
  Wer ihn geht, sperrt in `SolveService.start` und in jeder schreibenden Methode von `DraftService` dieselbe Zeile
  (`SELECT … FOR UPDATE`).
- **Ebenfalls bewusst so:** `DraftService.storeResult` übernimmt das beste Ergebnis eines Laufs auch dann, wenn es
  harte Konflikte enthält (etwa nach einem sofortigen Abbruch auf einer konfliktbehafteten Ausgangslage). Das ist
  die Linie des ganzen Moduls: Konflikte blockieren nie, sie stehen als Daten in der Antwort und werden bei jedem
  Lesen neu berechnet (E-31). Die Planung entscheidet. Repariert wurde nur der echte Fehler daneben — eine
  Platzierung auf ein Zeitfenster oder einen Raum, den jemand während des Laufs gelöscht hat (PL-13).
- R1 (08.09.) kann `classId`/`validFrom` auf Soll heben oder streichen; R2 kann V2-Werte ändern (dann V6-Korrektur,
  nie V2 ändern, sobald committet).
- ~~Frontend `types.ts` ist noch v0+E-40-Stand: `deleteEvent` fehlt.~~ **Erledigt 05.09.** (Befund FRONTEND-2): `deleteEvent` steht in `types.ts`, `client.ts` und `hooks.ts`, mit Aufrufern im Popover, auf aufgelösten Zeilen und in `EventList`.

## 9. Belege (05.09., 00:30–01:10)

| Befehl | Ausgabe |
|---|---|
| `git clone …/starter …/plattform && git checkout -b vollausbau` | `vollausbau`, HEAD `1552b63 add Licence Apache 2.0` |
| `git -C starter diff HEAD --binary > patch; git -C plattform apply --check patch; git apply patch` | `apply --check OK`, `apply OK` (453 387 Bytes) |
| `git -C starter ls-files -o --exclude-standard \| grep -v '^deployment/\.env 2$' \| rsync --files-from=-` | 282 Dateien |
| `diff -rq -x .git -x node_modules -x build -x dist -x .gradle starter plattform` | `Only in …/starter/deployment: .env` |
| `git -C plattform diff --cached --stat \| wc -l` | `0` |
| `python3 splice-v1.py` (Fragmente in v0 eingefügt) | `ok: 3294 Zeilen`; `grep -c operationId:` → `63` |
| `node …/@redocly/cli/bin/cli.js lint openapi.yaml` | `Woohoo! Your API description is valid.` — 0 Fehler, 1 Warnung (`operation-4xx-response` an `GET /api/health`, wie v0) |
| `prism mock -p 1509 openapi.yaml` + 18 Aufrufe (`scratchpad/prism-check.mjs`) | `GET /api/timetable/week` 200 → `ADDED,CANCELLED,MOVED,NEEDS_SUBSTITUTION,REGULAR,SUBSTITUTED`; `GET /api/me/timetable/week` 200 → `CANCELLED,GIVEN_AWAY,MOVED,NEEDS_SUBSTITUTION`; `GET /api/draft` 200 (7 Stunden, `openLessons` 9c Ma 1); `POST /api/draft/lessons` 201 → `ROOM_DOUBLE_BOOKED:11+18`; `POST /api/draft/solve` 202; mit `Prefer: code=409` → `master-data-incomplete`, `missing ["Klasse 9c hat kein Fach"]`; `POST /api/draft/accept` 200 → `warnings ["2 Vertretungen ab heute verfallen"]`, 8 Stunden; `POST /api/draft` 409 `draft-exists`; `POST /api/events` 201 (`affected` 4); `DELETE /api/events/1` 204; `GET /api/substitutions/pending` 200 (4 Zeilen); `GET /api/substitutions/options` 200 → `SCZ,WEB` / `P1,R204`; `POST /api/exceptions` 201; 409 → `exception-exists`; `GET /api/changelog?classId=1` 200 (`total 1`, `Meyer / R101 -> Schulz / R204`); `GET /api/holidays` 200 (1); `GET /api/timetable` 200 (7); `GET /api/draft` ohne Token 401 |
| Abgleich `operationId` ↔ `frontend/src/api/client.ts` | 63 IDs, fehlt nur `deleteEvent` (V-6) |

## 10. Änderungen durch Module

Fortlaufend, ein Absatz je Modul. Der Vertrag `api-contract/openapi.yaml` bleibt unberührt, solange
hier nichts anderes steht.

### `config` und `sampledata` (05.09., Vollausbau)

**Am Vertrag geändert: nichts.** `GET /api/health`, `GET/PUT /api/config` und `POST /api/sample-data`
sind so gebaut, wie v1 sie beschreibt (Pfade, Felder, Slugs, Beispielwerte). Gemessen gegen den
laufenden Stack, Belege in `docs/abnahme/config.md`.

Umsetzungsentscheidungen, die von Abschnitt 4 abweichen:

| # | Vertrag Abschnitt 4 | hier | Grund |
|---|---|---|---|
| M-1 | `sampledata` benutzt die Repositories aus `masterdata` | `SampleDataLoader` schreibt mit `JdbcTemplate` in `teacher`, `subject`, `room`, `school_class`, `timeslot`, `class_subject`, `teacher_subject`, `teacher_blocked_timeslot`, `timetable`, `lesson` | Das Modul legt keine eigenen Entities an, und mehrere Module entstehen gleichzeitig: über SQL übersetzt und testet sich `sampledata` unabhängig vom Bauzustand von `masterdata` und `planning`. Das Leeren lief laut Abschnitt 4 ohnehin schon per SQL. Die ArchUnit-Zeile bleibt `sampledata → masterdata, planning, shared`, damit backend2 den Solver-Aufruf ergänzen kann, ohne die Tabelle zu ändern |
| M-2 | Leeren mit einer kaskadierenden Leerung aller Tabellen | zeilenweises Löschen in Kindtabellen-Reihenfolge, danach `ALTER SEQUENCE IF EXISTS <tabelle>_id_seq RESTART` | Eine kaskadierende Leerung von `teacher` leert **auch `user_account`** — PostgreSQL räumt jede verweisende Tabelle mit, unabhängig von deren Zeilen. Das Konto `admin` wäre nach jedem „Ersetzen" weg. `holiday` wird übersprungen, solange V5 fehlt: die Liste der zu leerenden Tabellen wird gegen `information_schema.tables` gefiltert |
| M-3 | `loadFull()` enthält den vom Solver gefüllten, angenommenen Plan (V-11) | **erledigt am 05.09. (Nacharbeit backend2).** `loadFull()` = `loadFullMasterData()` + `draftService.create(EMPTY)` + `solveService.solveBlocking(…)` + `draftService.accept(null)`. Rechenzeit aus `app.sample-data.solve-seconds` (Betrieb 10 s, Pipeline 3 s). `loadFullMasterData()` ist der Einstieg ohne Plan — den braucht `SolveServiceIT` für T-W8.1 auf dem vollen Bestand | Vorher fehlte Checkliste D-1 („`GET /api/timetable` hat > 0 Stunden") und damit die Vorbedingung von T-W8.1. Belegt durch `SampleDataIT.loadedSampleHasAnAcceptedPlan` |
| M-4 | Testklasse `SmallFixtureTest` | `SmallFixtureIT` (Tag `integration`) | Der Bestand entsteht in der Datenbank; ohne PostgreSQL lässt er sich nicht anlegen |
| M-5 | — | `ArchitectureTest.ALLOWED` steht jetzt als `Map.ofEntries(…)` statt `Map.of(…)` | `Map.of` fasst höchstens zehn Paare; mit `sampledata` war die Grenze erreicht, und die Tabelle wächst noch (`substitution → auth`, `changelog → auth`, `planning → config`) |

Fachliche Setzungen des vollen Beispielbestands (Daten, keine Vorschrift — E-37): zwölf Klassen 5a–10b,
zwölf Fächer mit zusammen 30 Wochenstunden je Klasse, 24 Lehrkräfte mit je zwei Fächern und Deputat 25
(höchste Last 18), 18 Räume (12 Klassenräume, 2 Fachraum Physik, 1 Fachraum Chemie, 2 Sporthalle,
1 Musikraum), Raster Mo–Fr mit sieben Positionen und der Pause an Position 3. Jedes Fach mit
Raumanforderung findet mindestens einen passenden Raum, und keine Lehrkraft unterrichtet ein Fach,
das nicht in ihrer Fachliste steht — beides prüft `SampleDataIT`.

Für andere Module nutzbar: `SampleDataLoader.loadSmall()` / `loadSmallWithActivePlan()` als
Pflichtenheft-Fixture (Meyer/Schulz/Weber, 7a/8b/9c, R101/R204/P1, 35 Zeitfenster, sieben Stunden
Mo/Di) und `SampleDataLoader.clear()` als `@BeforeEach` für Integrationstests, die leere
Grunddatentabellen brauchen.

### `rules` (05.09., Vollausbau)

**Am Vertrag geändert: nichts.** `GET /api/rules`, `PUT /api/rules/{id}`, `POST /api/rules`,
`POST /api/rules/reset` und `DELETE /api/rules/{id}` sind gebaut, wie v1 sie beschreibt — Pfade,
Feldnamen, Beispielwerte und Reihenfolge der fünf Zeilen wie im `example` des Vertrags. Gemessen gegen
den laufenden Stack, Belege in `docs/abnahme/rules.md`.

Der Vertrag lässt bei den beiden 409-Antworten offen, welcher Slug gilt (`$ref` auf `Conflict409`).
Festgelegt sind jetzt:

| Fall | Antwort |
|---|---|
| `POST /api/rules` mit einem `(type, scope, grade)`, das es schon gibt | 409 `data-exists` — „Für Jahrgang 5 gibt es die Regel „Maximale Stunden pro Tag“ bereits." |
| `DELETE /api/rules/{id}` auf eine schulweite Zeile | 409 `in-use` — „Schulweite Regeln werden ausgeschaltet, nicht gelöscht." |

Umsetzungsentscheidungen:

| # | Quelle | hier | Grund |
|---|---|---|---|
| R-M1 | `frontend/src/mock`: zweiter Jahrgangswert → 400 mit Feldfehler an `grade` | 409 `data-exists` | VERTRAG Abschnitt 4 (`RuleControllerIT`: „Unique `(type, scope, grade)` → 409") und die Beschreibung von `POST /api/rules` im Vertrag. Server gewinnt (Regel 3.9), die Attrappe zieht in der Frontend-Phase nach |
| R-M2 | `frontend/src/mock`: `resetRules` hält eingeschaltete Lehrkraft-Regeln eingeschaltet | Nach dem Zurücksetzen gilt immer: alles an, nur die **schulweiten Lehrkraft-Zeilen** aus | `rule_preset` hat keine Spalte `active`; „Beispielsatz laden" heißt zurücksetzen. Die Setzung ist dieselbe wie im Seed V2 (sonst meldet das Testszenario des Pflichtenhefts sofort Deputatsverstöße). Ein Satz, keine Sonderfälle |
| R-M3 | V2-Kommentar: „Übernommen werden nur die 5 schulweiten Zeilen" | `reset` kopiert **alle** Zeilen des Bundeslands, also auch die Jahrgangszeilen | Sonst wären die 58 Jahrgangszeilen aus zehn Bundesländern nie erreichbar. Für SH (fünf Zeilen, keine Jahrgangszeilen) ist der Unterschied null — belegt mit der Gegenprobe Berlin in `docs/abnahme/rules.md` |
| R-M4 | — | `reset` behält die Kennung einer Zeile, die es schon gibt (Abgleich über `type`/`scope`/`grade`), und löscht nur, was der Beispielsatz nicht kennt | Die Kennung steht später in Konfliktmeldungen (`ConflictDto.ruleId`, T-W2.1) und in den Zeilenschlüsseln der Regeltabelle; ein Zurücksetzen soll sie nicht wandern lassen |
| R-M5 | — | `PUT /api/rules/{id}` liest nur `value`, `severity`, `weight`, `active`; bei `NO_LESSONS_IN_BREAKS` wird immer 1 gespeichert | Typ, Zähleinheit und Jahrgang zu ändern hieße, eine andere Regel zu meinen; die Pausenregel hat keinen Zahlenwert (die Oberfläche sperrt das Feld) |
| R-M6 | Abschnitt 4: `RuleResolver.resolve(List<Rule>, …)` statisch | zusätzlich eine Bean-Methode `resolve(classes, teacherIds)`, die die aktiven Regeln selbst lädt | `planning` bekommt so keine Entities zu sehen; die statische Methode bleibt und wird ohne Datenbank getestet (`RuleResolverTest`) |
| R-M7 | Abschnitt 4: `shared` trägt `RuleType`, `RuleScope`, `Severity` | angelegt | additive Ergänzung in `shared`; `solver` darf nur `shared` benutzen (Abweichung D-1) |
| R-M8 | — | `ArchitectureTest.ALLOWED`: `rules → config, shared` | `POST /api/rules/reset` liest das eingestellte Bundesland aus `SystemConfigService.get().state()` (E-48). Tabelle in Abschnitt 4 |

Für andere Module nutzbar: `RuleResolver.resolve(List<ClassRef>, List<Long>)` liefert je Klasse und je
Lehrkraft eine `EffectiveRule` (Jahrgangszeile schlägt schulweite Zeile) — die eine Regelquelle für
`ConflictChecker` und `SolveService` in Phase backend2 (`docs/SOLVER-PLAN.md` Abschnitt 9).

**Hinweis zu `shared/api/ApiExceptionHandler`:** `masterdata`, `auth` und `rules` haben am 05.09. jeweils
einen eigenen `@ExceptionHandler(ValidationException.class)` angelegt. Drei Methoden für dieselbe
Ausnahmeklasse lässt Spring nicht zu („Ambiguous @ExceptionHandler"); im Arbeitsbaum steht deshalb noch
genau eine (`onCustomValidation`, Form `errors[{field, message}]`). Beim Zusammenführen prüfen, dass es
dabei bleibt.

**Der Handler deckt seit der Nacharbeit backend1 (05.09.) auch die Fälle ab, die vorher 500 ergaben** —
`backend2` und `backend3` brauchen dafür nichts Eigenes und dürfen keinen zweiten Handler dafür anlegen:

| Fall | Antwort | Beispiel |
| --- | --- | --- |
| `MethodArgumentTypeMismatchException` | 400 `validation`, `errors[{field, "muss eine Zahl sein"}]` | `GET /api/teachers/abc` |
| `HttpRequestMethodNotSupportedException` | 405 `method-not-allowed` | `DELETE /api/timegrid` |
| `HttpMediaTypeNotSupportedException` | 415 `unsupported-media-type` | Rumpf als `text/plain` |
| `DataIntegrityViolationException`, SQLState 23505 | 409 `data-exists` | zwei gleichzeitige Anlagen derselben Zeile |
| `DataIntegrityViolationException`, SQLState 23503 | 409 `in-use` | Löschen einer Zeile, auf die noch ein Fremdschlüssel zeigt |

Der 23503-Fall ist der Auffangzweig für **archivierte Pläne**: die Zählabfragen in `ClassSubjectRepository`
lassen `timetable.status = 'ARCHIVED'` bewusst aus (löschen statt deaktivieren richtet sich nach dem aktiven
Plan und dem Entwurf), die Fremdschlüssel aus `lesson` bleiben aber bestehen. Gemessen 05.09.: Raum nur noch
in einem archivierten Plan → `DELETE /api/rooms/1` → 409 `in-use` statt 500.

**Längengrenzen in DTOs:** jedes Feld, das in eine `varchar(n)`-Spalte geht, trägt `@Size(max = n)` mit der
Zahl aus der Migration (`teacher.name`/`subject.name` 120, `room.name`/`room_type` 60, `school_class.name` 20,
`user_account.username` 60, `system_config.school_name` 120, `school_type` 40). Ohne die Grenze kommt ein zu
langer Wert durch die Prüfung und scheitert erst an der Datenbank — 500 statt 400 mit Feldliste. Wer eine
Spalte in einer späteren Migration verbreitert, zieht die Zahl im DTO mit.

### `auth` (05.09., Vollausbau)

**Am Vertrag geändert: nichts.** `POST /api/auth/login`, `DELETE /api/auth/token`, `GET /api/me`,
`PUT /api/me/password`, `GET/POST /api/users` und `PUT /api/users/{id}/password` sind gebaut, wie v1
sie beschreibt — Pfade, Feldnamen, Statuscodes und Beispielwerte. Gemessen gegen den laufenden Stack,
Belege in `docs/abnahme/auth.md`.

Der Vertrag lässt bei `POST /api/users` offen, welcher Slug die 409-Antwort trägt (`$ref` auf
`Conflict409`). Festgelegt ist jetzt:

| Fall | Antwort |
|---|---|
| Benutzername ist vergeben | 409 `in-use` — „Den Benutzernamen „meyer“ gibt es schon." |
| Die Lehrkraft hat schon ein Konto (`user_account.teacher_id` ist UNIQUE) | 409 `in-use` — „Diese Lehrkraft hat bereits ein Konto." |
| Rolle `TEACHER` ohne `teacherId`, Rolle `PLANNING` mit `teacherId`, unbekannte `teacherId` | 400 `validation` mit `errors[0].field = "teacherId"` |
| `PUT /api/me/password` mit falschem bisherigem Passwort | 400 `validation` mit `errors[0].field = "oldPassword"` — nicht 401: angemeldet ist die Person ja |

Umsetzungsentscheidungen:

| # | Quelle | hier | Grund |
|---|---|---|---|
| A-M1 | Abschnitt 4: `shared` trägt `Role` und `CurrentUser` | angelegt: `shared/Role.java` (aus `auth.persistence` hierher) und `shared/CurrentUser.java` (Record `id, username, role, teacherId` mit `require()` und `isTeacher()`) | `changelog` und `substitution` brauchen Rolle und Verursacher, ohne `auth` zu importieren. `auth.domain.Principal` ist damit weggefallen; der Token-Filter legt `CurrentUser` als Principal ab |
| A-M2 | Starter: `TokenAuthenticationFilter` als `@Component` | kein Bean mehr; `SecurityConfig` erzeugt ihn selbst (`new TokenAuthenticationFilter(authService)`) | Ein Filter-Bean hängt Spring Boot zusätzlich in die allgemeine Servlet-Kette **und** in jeden `@WebMvcTest` jedes anderen Moduls — dort fehlt der `AuthService` und der Kontext kommt nicht hoch (in `ConfigControllerTest` gesehen) |
| A-M3 | Starter: `SecurityConfigTest` als `@WebMvcTest` ohne Argument | `@WebMvcTest(controllers = {AuthController, UserController, HealthController})` | Ohne Aufzählung lädt der Test jeden Controller der Anwendung und scheitert, sobald ein anderes Modul einen Controller mit neuer Abhängigkeit anlegt. Für die Pfadregeln genügen die drei; Pfade ohne geladenen Controller antworten 404, was die Regel davor genauso belegt |
| A-M4 | Abschnitt 6: `/api/clients/**` aus `permitAll` raus | umgesetzt; `ApplicationIT.teamExampleStillAnswers` meldet sich jetzt an und prüft zusätzlich 401 ohne Token | Das Team-Beispiel bleibt bis zur Liveschaltung liegen, ist aber nicht mehr offen |
| A-M5 | Abschnitt 4: Anzeigename = Name der Lehrkraft | `AuthService.displayName` liest `masterdata.persistence.TeacherRepository`; `UserService.create` prüft die Lehrkraft mit `existsById` | Die erlaubte Kante `auth → masterdata` aus Abschnitt 4. Eine unbekannte `teacherId` ergibt so 400 statt eines Fremdschlüsselfehlers (500) |
| A-M6 | Abschnitt 4: „tägliches Löschen abgelaufener Token (`@Scheduled`)" | `TokenCleanup` mit `@Scheduled(cron = "${app.token-cleanup-cron:0 15 3 * * *}")`, `@EnableScheduling` an `BackendApplication` | Der Zeitplan steht als Eigenschaft (`TOKEN_CLEANUP_CRON`), damit die Abnahme das Aufräumen nicht zwölf Stunden lang beobachten muss — belegt mit `*/5 * * * * *` in `docs/abnahme/auth.md` |
| A-M7 | Abschnitt 6: „Mapper als statische Methoden neben dem Controller" | `UserService` gibt den Record `auth.domain.Account` zurück, `AuthDtos.UserDto.of(Account)` bildet ab | ArchUnit-Regel 2 verbietet jede Abhängigkeit von `..api..` auf eine `@Entity`; ein Mapper `UserDto.of(UserAccount)` verstößt dagegen (gemessen, fünf Verstöße) |
| A-M8 | Vertrag `PasswordSet` | `PUT /api/users/{id}/password` beendet zusätzlich alle Sitzungen dieses Kontos | Sonst bliebe ein Token nach dem Zurücksetzen bis zu zwölf Stunden gültig |

Für andere Module nutzbar: `CurrentUser.require()` liefert in jedem schreibenden Dienst den
Verursacher (`id`, `username`, `role`, `teacherId`) für Verlauf und `created_by_id`, ohne dass das
Modul `auth` importiert.

### `masterdata` (05.09., Vollausbau)

**Am Vertrag geändert: nichts.** `/api/teachers`, `/api/subjects`, `/api/rooms`, `/api/classes`
(+`/subjects`) und `/api/timegrid` sind so gebaut, wie v1 sie beschreibt — Pfade, Feldnamen, Status,
Slugs und Beispielwerte. Der Durchlauf G-1 bis G-11 gegen den laufenden Stack liefert genau die IDs
der Vertragsbeispiele (Fächer 1–3, Räume 1–3, Zeitfenster 1–35 mit `FR 6. = 34`, `FR 7. = 35`);
Belege in `docs/abnahme/backend1-masterdata.md`.

Umsetzungsentscheidungen, die von Abschnitt 4 oder 6 abweichen:

| # | Vertrag | hier | Grund |
|---|---|---|---|
| MD-1 | Abschnitt 6: „Records in `<modul>/api`, Mapper als statische Methoden neben dem Controller" | alle Records in `de.wsp3_1.masterdata.MasterdataDtos` (Modulwurzel), Abbildung Entity → Record in `masterdata/domain` | Beide Wege des Vertrags verletzen ArchUnit: ein Mapper in `..api..` fasst eine `@Entity` an (Regel 2), ein Dienst in `..domain..`, der `..api..`-Records zurückgibt, verletzt Regel 3. Das Wurzelpaket ist von keiner Regel erfasst — dieselbe Stelle wie `shared.Weekday`. So bleibt es bei **einer** Satzart statt zwei fast gleichen Schichten (`auth` löst dasselbe mit einer zweiten Schicht, A-M7) |
| MD-2 | Abschnitt 4: „In-use-Prüfung … `countLessonsByTeacher(teacherId)` in `ClassSubjectRepository`" | umgesetzt, dazu `countLessonsBySubject/ByClass/ByClassSubject/ByRoom/ByTimeslot`, alle nativ, alle mit `timetable.status <> 'ARCHIVED'` | Löschen ist für fünf Grunddaten verboten, nicht nur für die Lehrkraft. Archivierte Pläne zählen nicht mit — sie sind Geschichte, kein Verweis (gleiche Regel wie `lessonsInUse` in der Attrappe) |
| MD-3 | Abschnitt 4: `Teacher` mit `subjects: Set<Subject>`, `preferred/blockedTimeslots: Set<Timeslot>` | `@ElementCollection Set<Long>` auf dieselben Tabellen `teacher_subject`, `teacher_preferred_timeslot`, `teacher_blocked_timeslot` | Die API spricht ohnehin nur in IDs (`subjectIds`, `blockedTimeslotIds`), die Fremdschlüssel stehen in V1, und das Modul lädt keine ganzen Fach- und Zeitfenster-Objekte nach. Schema unverändert |
| MD-4 | `PUT /api/classes/{id}/subjects` „ersetzt die Fächerliste vollständig" | vorhandene Zeilen werden **geändert**, nicht gelöscht und neu angelegt; nur wirklich entfallene Fächer verschwinden, und ein entfallenes Fach mit Stunden ergibt 409 `in-use` | An `class_subject` hängt jede Unterrichtsstunde (`lesson.class_subject_id`). Ein Lehrkraftwechsel in der 9c würde sonst den Plan mitlöschen. Nach außen ist das Verhalten dasselbe (T-W1.4/T-W1.5 belegt) |
| MD-5 | — | neu `shared/domain/ValidationException` (Liste aus `FieldError(field, message)`) plus **ein** Handler in `ApiExceptionHandler` | Feldfehler wie `subjects[1].weeklyHours` (G-8) oder „Kürzel ist bereits vergeben" kann keine Annotation ausdrücken. `auth`, `rules` und `masterdata` haben am 05.09. unabhängig denselben Handler ergänzt; Spring erlaubt pro Ausnahmeklasse nur einen — es steht jetzt genau einer da |
| MD-6 | — | `build.gradle`: `tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }` | Ohne die Zeile nimmt javac die Zeichensatz-Einstellung des Rechners. Im CI-Container ist die US-ASCII, und die geforderten Meldungstexte („… über dem Deputat …", „Deaktivieren statt löschen.") kämen mit Fragezeichen zurück |
| MD-7 | — | Das Raster wird in Java nach `Weekday.ordinal()` sortiert, nicht per `ORDER BY day` | Der Wochentag steht als Text in der Datenbank; alphabetisch käme FR vor MO, und `GET /api/timegrid` läge in falscher Reihenfolge |
| MD-8 | Abschnitt 4: Testklasse `WarningCheckerTest`, `TeacherServiceIT`, `ClassSubjectsIT`, `TimegridIT`, `DeleteVsDeactivateIT`, „je Ressource ein `@WebMvcTest`" | alle fünf gebaut; die fünf `@WebMvcTest` liegen als **eine** Klasse `MasterdataControllerTest` (`controllers = {…}` mit allen fünf Controllern) | Fünf fast gleiche Dateien prüfen nichts, was eine nicht prüft. Sie prüft die Feldnamen des Vertrags, darunter `isBreak` (nicht `break`) und `"08:00"` (nicht `"08:00:00"`) |

`WarningChecker` rechnet auf dem Record `TeachingAssignment` statt auf Entities: so prüft
`WarningCheckerTest` T-W1.4 und T-W1.5 ohne Spring und ohne Datenbank (Teststrategie Ebene 1), und die
Meldungstexte stehen wörtlich im Test.

`MasterdataITBase` baut die Pflichtenheft-Testdaten **über die eigene API** auf (genau die Aufrufe
G-1 bis G-7) und nicht über `SampleDataLoader.loadSmall()` — der Aufbau prüft damit schon, was die
Abnahme später von Hand macht, und die Tests hängen nicht am Bauzustand von `sampledata`. Den aktiven
Plan Mo/Di legt die Basisklasse per `JdbcTemplate` an, weil `planning` erst in backend2 entsteht.

Für andere Module nutzbar: die Repositories in `masterdata/persistence` (`TeacherRepository`,
`SubjectRepository`, `RoomRepository`, `SchoolClassRepository`, `ClassSubjectRepository`,
`TimeslotRepository`) — `ClassSubjectRepository.findAllWithReferences()` liefert Klasse, Fach und
Lehrkraft jeder Zeile in einem Zug und ist die Grundlage für `syncRequiredLessons` in `planning`.

### `solver` (05.09., Vollausbau)

**Am Vertrag geändert: nichts.** Das Modul hat keinen Endpunkt; `POST /api/draft/solve`, `DELETE /api/draft/solve`
und `GET /api/draft` liegen in `planning`. Gebaut nach `docs/SOLVER-PLAN.md`: Facts `RuleFact`, `BlockedTimeFact`,
`PreferredTimeFact`; `SolverTimetable` mit drei weiteren Fact-Listen; `TimetableConstraintProvider` mit H1–H5, M1,
R1–R3 als Zwillinge, S1, S2 (18 Constraints, Namen wie in der Tabelle); `SolveInput`/`SolveResult`; `SolverTranslator`;
`SolverRunner` (`start`, `isSolving`, `cancel`, `solveBlocking`). Alles im Container kompiliert und getestet,
Belege in `docs/abnahme/solver.md`.

Umsetzungsentscheidungen, die von `docs/SOLVER-PLAN.md` abweichen (alle gemessen mit Timefold 2.6.0):

| # | SOLVER-PLAN sagt | hier | Grund |
|---|---|---|---|
| SV-1 | `RoomFact(id, roomType)` unverändert; `SolveInput.rooms` nur aktive Räume, `timeslots` nur ohne Pause | `RoomFact(id, roomType, active)`; `SolveInput.rooms` **alle** Räume, `SolveInput.timeslots` **alle** Zeitfenster; der Übersetzer bildet den Wertebereich (aktiv bzw. keine Pause) | Timefold 2.6.0 lehnt einen Startwert außerhalb des Wertebereichs ab („The value … is outside of the related value range", auch bei `@PlanningPin`). Der Übersetzer braucht deshalb die Fact-Instanz jeder gespeicherten Stunde — und er muss entscheiden, was mit ihr passiert (SV-2) |
| SV-2 | „Timefold verlangt nicht, dass ein Startwert im Wertebereich liegt" (Abschnitt 6, Tag-1-Prüfung) | Doch. Regel im Übersetzer: gespeicherte Stunde auf einer Pause oder in einem inaktiven Raum → **festgelegt**: Zeitfenster/Raum kommt zusätzlich in den Wertebereich, Stunde bleibt stehen (R3/H5 bewerten sie); **nicht festgelegt**: Stunde wird offen übergeben und neu geplant | gemessen `SmallDatasetSolveTest.pinnedLessonInBreak_isKeptAndCountsAsHardViolation` (Ergebnis `-1hard`, sieben übrige Stunden belegt). Nebenwirkung: solange eine festgelegte Stunde in der Pause liegt, ist die Pause Wertebereich für alle — bei harter Pausenregel (Seed) hält R3 sie frei |
| SV-3 | `SolverManager<SolverTimetable, Long>`, `withFinalBestSolutionConsumer(sol -> …)`, `new SolverConfigOverride<SolverTimetable>().withTerminationConfig(new TerminationConfig().withSpentLimit(…))` | `SolverManager<SolverTimetable>` (ein Typparameter), `withFinalBestSolutionEventConsumer(event -> … event.solution())`, `new SolverConfigOverride().withTerminationSpentLimit(Duration)` | Signaturen aus `timefold-solver-core-2.6.0.jar` (javap) |
| SV-4 | `application.properties` `spent-limit` als Sicherheitsnetz, B2 setzt 600 s; „wer `maxSolveSeconds` > 60 setzt, wird trotzdem bei 60 s beendet" | Property bleibt 60 s, Kommentar berichtigt: das Override **ersetzt** das Basis-Limit, es wird nicht gedeckelt | gemessen (Basis 2 s, Override 6 s → 6,19 s Laufzeit). Obergrenze für `maxSolveSeconds` ist damit allein die Bean-Validation von `PUT /api/config` |
| SV-5 | `solveBlocking(...) throws Exception` | wirft `IllegalStateException` mit deutschem Text, `InterruptedException` setzt das Interrupt-Flag | Aufrufer (`SolveService.solveBlocking`, `sampledata`) brauchen keinen `throws`-Rattenschwanz |
| SV-6 | `toResult`: `timeslot == null` → offen | `timeslot == null` **oder** `room == null` → offen (`Placement(id, null, null)`) | die Invariante „belegt oder offen" (DB-CHECK `lesson_assigned_or_open`) darf nicht an einem halb initialisierten Zustand scheitern |
| SV-7 | Tests 10.1/10.2 | zusätzlich `SmallDatasetSolveTest` (echter Lauf, Pflichtenheft-Daten, 2 s, Ebene 2), `FullDatasetSolveIT` (360 Stunden, 10 s, Tag `integration`, ohne Spring), `SolverRunnerIT` (Bean, Rechenzeit aus `SystemConfig`, Abbruch, blockierend) | „Lauf auf dem kleinen Datensatz ohne harten Verstoß" und die Reihenfolge Final-Consumer vor `NOT_SOLVING` (Abschnitt 8, 13) sind damit gemessen, nicht nur behauptet |

| SV-8 | Abschnitt 8: „Timefold ruft den Final-Consumer auf, bevor der Status auf `NOT_SOLVING` geht" | gilt beim Ablauf der Rechenzeit, **nicht** nach `terminateEarly`: da meldet `SolverManager.getSolverStatus` schon `NOT_SOLVING`, während der Final-Consumer noch läuft (erster Lauf von `SolverRunnerIT.cancel_deliversBestSoFar` fiel genau so: Status frei, Ergebnis `null`). `SolverRunner` führt deshalb die Kennungen laufender Aufträge selbst (`pending`, `Set<Long>`): `isSolving` bleibt `true`, bis `onFinished`/`onError` fertig ist | die Zusicherung „`EDITING` ⇒ `storeResult` ist durch" (409-Sperre der Mutationen, Abschnitt 8) muss auch für `DELETE /api/draft/solve` gelten |

Nachtrag aus der Prüfung vom 05.09. (Nacharbeit backend2):

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| SV-9 | 18 Constraints; die Nebenwirkung von SV-2 fange bei harter Pausenregel R3 auf | **20 Constraints**: neu `movableLessonInBreak` (H6) und `movableLessonInInactiveRoom` (H7), beide hart, beide nur für **nicht festgelegte** Stunden | Der zusätzliche Wert aus SV-2 steht im Wertebereich **aller** Stunden. R3 darf weich stehen, und H5 prüft nur die Raumart, nicht `active` — dann durfte der Solver in die Pause und in abgeschaltete Räume planen. Der Wertebereich ist eine bauliche Grenze und keine Regelwerksfrage; deshalb hart und unabhängig vom Regelwerk. Tests: `TimetableConstraintProviderTest.movableLessonInBreak_*`, `…InInactiveRoom_*` |
| SV-10 | — | `SolverRunner.reserve(draftId)` / `release(draftId)`: die Vormerkung ist der Punkt, an dem sich zwei gleichzeitige Starts sehen | `isSolving` allein reichte nicht — zwischen Prüfung und `start` liegt die Transaktion des Aufrufers. Zwei gleichzeitige `POST /api/draft/solve` ergaben 202 + **500** (`IllegalStateException: The problemId (2) is already solving`) statt 202 + 409 `solving`. Test: `SolveServiceIT.twoConcurrentStarts_exactlyOneRunsTheOtherIsConflict` |

Gemessen (05.09., Container `gradle:9.7.1-jdk21`): mit dieser Buchführung liegt das Ergebnis in `SolverRunnerIT`
unmittelbar nach dem Statuswechsel vor — beim Zeitablauf und nach Abbruch (zwei Wiederholungen grün).
Voller Bestand nach 10 s:
`0hard/-4medium/-2soft`, 4 von 360 Stunden offen — das ist die Qualität, die `sampledata` mit 10 s
bekommt; wer alle Stunden belegt haben will, gibt dem Lauf mehr Zeit (`maxSolveSeconds`).

Für `planning` nutzbar (Schnittstelle unverändert zu SOLVER-PLAN Abschnitt 8): `SolverRunner.start(draftId, input,
seconds, onFinished, onError)`, `isSolving(draftId)`, `cancel(draftId)`, `solveBlocking(problemId, input, seconds)`.
Testhelfer `SampleSolveInputs.small()/full()` (Testpaket `de.wsp3_1.solver`) liefern die beiden Beispielbestände
als `SolveInput` ohne Datenbank; `HardConflicts.of(input, result)` zählt H1–H5 mit Mengen nach.

### `changelog` (05.09., Nacharbeit backend2)

**Am Vertrag geändert: nichts.** `GET /api/changelog` ist so gebaut, wie v1 es beschreibt — Filter
`from`/`to`/`classId`/`teacherId`/`lessonId`, Seiten über `page`/`size` (Standard 50, höchstens 500),
Felder und Reihenfolge von `ChangelogItem` und `ChangelogPage`, Sortierung neueste zuerst. **Kein `PUT`,
kein `PATCH`, kein `POST`, kein `DELETE`** (T-W7.1): `DELETE /api/changelog/1` findet keine Route und
endet mit 404. Gemessen gegen den laufenden Stack, Belege in `docs/abnahme/backend2-nacharbeit.md`.

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| CH-1 | Modultabelle: `changelog → auth, shared` | die Entity hat **keine** JPA-Beziehung auf `UserAccount`; `user_id`, `lesson_id`, `class_id` und `teacher_id` sind einfache Spalten | Die Fremdschlüssel stehen in V4 und bleiben; die Entities gehören anderen Modulen. Ohne Beziehung braucht `changelog` gar keine Modulabhängigkeit — die Zeile in der Tabelle bleibt trotzdem stehen, sie erlaubt mehr als gebraucht wird |
| CH-2 | ein Modul liest über seine Repositories | geschrieben wird über die Entity, **gelesen mit `JdbcTemplate`** — eine Abfrage mit `JOIN user_account` und `LEFT JOIN school_class` | Die Antwort braucht `username` und `classShort`; beides gehört Modulen, die `changelog` nicht benutzen darf. Derselbe Weg wie M-1 (`sampledata`) und PL-2 (`planning`): eine Abfrage über die Tabellengrenze statt einer Modulabhängigkeit. Der Kommentar am Dienst sagt das |
| CH-3 | — | `ChangelogEntry` trägt `@Immutable` (Hibernate) | Ein Eintrag wird geschrieben und gelesen, nie geändert. Die Annotation hält die Regel auch dann, wenn später jemand einen Setter baut |
| CH-4 | — | `page`/`size` werden im Controller von Hand geprüft, nicht mit `@Validated` | An Abfrageparametern wirft Spring sonst eine `ConstraintViolationException`, für die es im `ApiExceptionHandler` keinen Fall gibt — der Client bekäme 500 statt 400 mit Feldliste |

#### Nachtrag 05.09. — Modul `changelog` vervollständigt

**Am Vertrag weiterhin geändert: nichts**; `api-contract/openapi.yaml` beschreibt `GET /api/changelog`
vollständig, es fehlte dort nichts. Ergänzt wurde der **Schreibweg für Ausnahmen**, den `substitution`
in backend3 benutzt, dazu Tests und die Modul-Regel. Belege: `docs/abnahme/changelog.md`.

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| CH-5 | Abschnitt 4: `substitution` schreibt Verlaufseinträge über `changelog` | die Aufrufpunkte heißen `ChangelogViews.NewEntry.exceptionCreated(...)` und `...exceptionDeleted(...)`, beide mit den Anlässen als `List<String>` | Die Trennung der Anlässe mit „; " (Vertrag, Feld `reasons`) steht damit **einmal** im Modul statt in jedem Aufrufer. `substitution` übergibt nur fertige Belegungstexte („Meyer / R101", „Schulz / R204", „entfällt", „Di, 3. Stunde") — die Namen kennt nur dieses Modul |
| CH-6 | — | `ChangelogService` kürzt zu lange **Belegungen** auf die Spaltenbreite (255 Zeichen), statt sie weiterzureichen | Sonst endete das Anlegen einer Vertretung mit einem Datenbankfehler statt mit einem gekürzten Eintrag. **Geändert am 05.09. (Nacharbeit backend3):** die **Anlässe** werden nicht mehr gekürzt — Migration V6 macht `changelog_entry.reasons` zu `text`. Die alte Grenze von 1000 Zeichen warf ab fünf Anlässen ganze spätere Anlässe weg, und Vertrag wie Rückgrat verlangen alle |
| CH-7 | Abschnitt 4: „ArchUnit-Zusatz: keine Klasse in `..changelog..` ruft `deleteById`/`deleteAll`" | die Regel steht in `changelog/ChangelogArchitectureTest`, nicht in `de.wsp3_1.ArchitectureTest` | `ArchitectureTest` trägt die vier **modulübergreifenden** Regeln und wird von allen Bauphasen gleichzeitig angefasst; eine Regel, die nur für dieses Modul gilt, liegt beim Modul. Sie greift nachweislich (Probe mit `deleteById` → `BUILD FAILED`, `docs/abnahme/changelog.md` Abschnitt 2) und prüft jeden Methodennamen mit `delete*`, nicht nur die zwei genannten |

~~Noch offen und ausdrücklich **nicht** in diesem Modul: die Einträge `EXCEPTION_CREATED` und
`EXCEPTION_DELETED` …~~ **Eingelöst am 05.09. (Nacharbeit backend3).** `POST`/`DELETE /api/exceptions`
gibt es; die vier `changelog.record(...)`-Aufrufe in `ChangelogIT` sind durch Aufrufe der Endpunkte
ersetzt, und `scripts/abnahme-changelog.py` legt das Leitszenario über die Schnittstelle an — der
`psql`-Zugriff und die Umgebungsvariable `ABNAHME_DB` sind aus dem Skript entfernt. Die Erwartungen
sind dabei unverändert geblieben.

### `substitution` Teil 1 (05.09., Nacharbeit backend2)

**Am Vertrag geändert: nichts.** Gebaut sind `GET /api/timetable/week` (vier Sichten, `date` beliebiger
Tag der Woche, Antwort ab Montag) und `GET /api/me/timetable/week` (Lehrkraft aus dem Token, Konto ohne
Lehrkraft → 404), dazu die Entity `Holiday` (V5) und der `WeekResolver`. Die Sicht `ROOM` ist
Musskriterium E-39 aus Interview 1. Belege in `docs/abnahme/backend2-nacharbeit.md`.

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| SU-1 | `EntryKind` mit sieben Werten | das Enum steht vollständig, der Resolver erzeugt in Teil 1 aber **nur `REGULAR`** (`original` leer, `reasons` leer, `exceptionId` leer) | Ereignisse (W-5) und datierte Ausnahmen (W-6) sind Teil 2. Die Form der Antwort steht damit schon fest — Web und Android-App müssen sich nicht noch einmal ändern |
| SU-2 | — | der Resolver fragt **je Tag** `PlanReader.plannedAt(date)`, nicht einmal je Woche | „Gültig ab“ (E-40) darf mitten in einer Woche greifen. Fünf Abfragen je Wochensicht sind bei Schulgröße kein Thema und ersparen eine Sonderbehandlung |
| SU-3 | — | die Tage der Woche sind die Wochentage, an denen das Raster Zeitfenster hat | Vertrag: „Mo–Fr, Sa nur wenn im Raster“. `EnumSet` zählt sie in der Reihenfolge des Enums auf |
| SU-4 | — | fehlendes `id` bei `CLASS`/`TEACHER`/`ROOM` ist 400 `validation` (Feld `id`), eine unbekannte Kennung 404 | So steht es im Vertrag am Endpunkt. Ohne die Existenzprüfung käme eine leere Woche zurück, und niemand sähe den Tippfehler |
| SU-5 | — | offene Stunden (ohne Zeitfenster) erscheinen in keiner Wochensicht | Sie liegen an keinem Tag. In der Planungssicht stehen sie in `openLessons` des Entwurfs, nicht im Kalender |

### `planning` (05.09., Vollausbau)

**Am Vertrag geändert (Korrektur 05.09.): eine additive Ergänzung**, PL-15 — `warnings[]` bei
`POST /api/draft/accept` nennt zusätzlich die offenen Stunden. Kein neues Feld, kein neuer Slug,
kein neuer Enum-Wert; `api-contract/openapi.yaml` beschreibt es an beiden Stellen. Sonst gilt weiter: `GET /api/timetable` und `/api/draft**` (GET/POST/DELETE, `/lessons`,
`/lessons/{id}`, `/solve`, `/accept`) sind so gebaut, wie v1 sie beschreibt — Pfade, Feldnamen und
-reihenfolge, Statuscodes, Fehler-Slugs (`draft-exists`, `solving`, `master-data-incomplete`,
`pinned-contradiction`, `validation`, `not-found`) und die Beispieltexte der Konflikte. Kein neuer Slug,
kein neues Feld, kein neuer Enum-Wert. Gemessen gegen den laufenden Stack, Belege in
`docs/abnahme/planning.md` (42 Zusicherungen, 0 Abweichungen), wiederholbar mit
`scripts/abnahme-planning.py`.

Umsetzungsentscheidungen, die eine Quelle ergänzen oder von ihr abweichen:

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| PL-1 | Abschnitt 4: `planning` schreibt den Verlaufseintrag über `changelog.ChangelogService.record(...)` | `DraftService.accept` schreibt die Zeile mit `JdbcTemplate` direkt in `changelog_entry` (V4), mit Kommentar an der Stelle | das Modul `changelog` gab es beim Bau von `planning` noch nicht; dieselbe Lösung nutzen `sampledata` (fremde Tabellen leeren) und `masterdata` (`lesson` zählen). **Erledigt am 05.09. (Nacharbeit backend2):** `DraftService.accept` ruft jetzt `changelogService.record(NewEntry.timetableAccepted(...))`; das SQL ist weg — Tabelle, Spalten und Werte sind dieselben |
| PL-2 | Abschnitt 4: `planning` darf `substitution` nicht benutzen | die Warnung „n Vertretungen ab heute verfallen" beim Annehmen zählte `lesson_exception` (V4) mit `JdbcTemplate` | ohne Java-Abhängigkeit auf `substitution`; die Modultabelle blieb unverändert. **Erledigt am 05.09. (Befund BACKEND-6):** die Zählung steht als `ExpiringExceptionsCounter` in `substitution` und wird über die Schnittstelle `shared.domain.ExpiringExceptions` gerufen — beide Module zeigen auf `shared`, die Richtung der Modultabelle stimmt wieder, und aus dem handgeschriebenen SQL ist eine Repository-Abfrage geworden. `planning` kennt seitdem kein `JdbcTemplate` mehr |
| PL-3 | SOLVER-PLAN 7: `SolveService.start` prüft, dann startet | die Prüfungen und das Schreiben von `solveStartedAt`/`solveLimitSeconds` laufen in der Transaktion, `solverRunner.start(...)` **erst nach dem Commit** (`TransactionSynchronization.afterCommit`) | sonst kann der Solver-Thread sein Ergebnis in einer eigenen Transaktion speichern, bevor die Start-Transaktion committet — deren Commit überschriebe `solveLastScore` sofort wieder. Fünf Zeilen, ein Kommentar an der Stelle |
| PL-4 | `frontend/src/mock`: `PATCH` einer offenen Stunde setzt den ersten freien Raum ein | 400 `validation` mit `errors[{field: "roomId", message: "Eine eingeplante Stunde braucht Zeitfenster und Raum"}]` | Abweichung D-9 („Server rät keinen Raum") — hier gemessen und damit bestätigt; die Attrappe zieht in der Frontend-Phase nach |
| PL-5 | `frontend/src/mock/logic/conflicts.ts` wertet die Maximalstunden-Regeln für **alle** Klassen aus | der `ConflictChecker` bekommt die Regeln aus `RuleResolver.resolve(aktive Klassen, Lehrkräfte im Plan)` und wertet sie damit nur für **aktive** Klassen aus | SOLVER-PLAN 9 schreibt genau diese Aufrufform vor, und der Solver bekommt dieselbe Liste. Solver und Prüfung dürfen nicht auseinanderlaufen (SOLVER-PLAN 10.4); eine deaktivierte Klasse ist aus der Planung heraus, ihre Altstunden bleiben aber in den Doppelbelegungen sichtbar |
| PL-6 | Abschnitt 4: `sampledata` benutzt `SolveService.solveBlocking` | `SolveService.solveBlocking(int seconds)` gibt es (Vorbedingungen, `syncRequiredLessons`, blockierender Lauf, `storeResult`), ist aber `@Transactional` und hält damit eine Datenbankverbindung für die ganze Rechenzeit | bei den zehn Sekunden des Beispielbestands vertretbar und viel einfacher als ein zweiter Weg durch die Vorbedingungen. **Seit 05.09. (Nacharbeit backend2) hat die Methode ihren Aufrufer** — `SampleDataLoader.loadFull()`. Dabei ergänzt: `requireNotSolving`, eine Vormerkung wie bei `start` und eine feste Obergrenze `Math.min(seconds, 60)`, damit ein blockierender Lauf niemals Transaktion und Datenbankverbindung stundenlang hält. Gemessen: `POST /api/sample-data` 11 s, danach 357 von 360 Stunden belegt, 0 harte Konflikte |
| PL-7 | Abschnitt 6, DTO-Regeln | eingehalten: Antwort-Records in `planning/api/PlanningDtos`, Dienst gibt `planning/domain/PlanViews`-Records zurück, Abbildung als statisches `of(...)` neben dem Controller. **Ausnahme**: bei 409 `pinned-contradiction` geht der Domain-Record `ConflictView` unverändert als `extras` in den `ProblemDetail` | `shared.ConflictException` nimmt `Map<String,Object>`, und der Wert entsteht in `domain` — von dort ist `api` nicht erreichbar (ArchUnit-Regel 3). Die Feldnamen von `ConflictView` und `ConflictDto` sind gleich, das JSON ist dasselbe (gemessen im Abnahmelauf) |
| PL-8 | — | `accept` schreibt den bisherigen aktiven Plan zuerst auf `ARCHIVED` und ruft `saveAndFlush`, bevor der Entwurf `ACTIVE` wird | der Teilindex `timetable_one_active` (V3) ist nicht aufschiebbar: in der falschen Reihenfolge gäbe es einen Augenblick mit zwei aktiven Plänen und die Datenbank lehnte ab |
| PL-9 | — | `PlanReader.plannedAt(LocalDate)` wählt aus **allen** angenommenen Plänen (dem aktiven und allen archivierten) den mit dem spätesten Beginn, der nicht nach diesem Tag liegt; Beginn ist `validFrom`, sonst der Tag der Annahme | Schnittstelle für `substitution` (Wochensichten, E-40). **Korrigiert am 05.09. (Nacharbeit backend2):** vorher gewann vor dem `validFrom` des aktiven Plans immer der zuletzt archivierte. Bei mehr als einem Wechsel war das falsch — gilt A heute, B ab 21.09. und C ab 28.09., dann lieferte die Frage nach dem 14.09. den Plan B statt A. Aufrufer ist jetzt `substitution.WeekResolver`; Test `WeekIT.threeValidFromDatesResolvePerDay` |

| PL-10 | — | `SolveService.buildInput` lässt Stunden weg, die dieser Lauf nicht bewegen darf **und** offen sind | Timefold lehnt eine festgelegte Stunde ohne Raum ab („has a variable pinned to null, even though unassigned values are not allowed“): der Raum erlaubt keine leeren Werte. Zwei Wege führten dorthin — eine von Hand festgelegte offene Stunde und, bei der Kann-Option `classId`, jede offene Stunde einer anderen Klasse. Da der Lauf sie ohnehin nicht bewegen dürfte, verliert er nichts. Test: `SolveServiceIT.startWithClassId_onlyThatClassMoves` |
| PL-11 | — | `DraftService.create(COPY_ACTIVE)` legt nur Stunden fest, die **belegt** und konfliktfrei sind | Eine offene Stunde hat keinen Konflikt, aber „festgelegt und offen“ hieße „bleib für immer offen“ — und läuft in PL-10. Ein angenommener Plan darf offene Stunden enthalten, also kam der Fall im Betrieb vor. Test: `SolveServiceIT.copyActiveLeavesOpenLessonsUnpinned` |
| PL-12 | — | `DraftService.view` liest **erst** den Zustand, dann die Stunden | In der anderen Reihenfolge kann der Solver-Thread dazwischen sein Ergebnis speichern: die Antwort meldete `EDITING` und zeigte die alten Lagen, das Web hörte auf zu fragen und blieb auf dem Stand von vor dem Lauf. So ist `EDITING` immer die Zusicherung „`storeResult` ist durch“ |
| PL-13 | — | `DraftService.storeResult` lässt eine Stunde offen, wenn ihr Zeitfenster oder Raum zwischenzeitlich gelöscht wurde | Die Eingabe eines Laufs kann veralten. Ohne diese Zeile scheiterte das Speichern am CHECK `lesson_assigned_or_open` der Datenbank, und der ganze Lauf ging verloren |
| PL-14 | — | `ApiExceptionHandler` übersetzt den Teilindex `timetable_one_draft` nach 409 `draft-exists` statt `data-exists` | Zwei gleichzeitige `POST /api/draft` bestehen beide die Prüfung im Dienst; der zweite läuft in den Index. Der Vertrag kennt für diesen Fall `draft-exists`. Der Indexname steht als Zeichenkette in `shared` — `shared` darf kein Modul kennen. Test: `ApiExceptionHandlerTest.duplicateDraftIsConflictDraftExists` |
| PL-15 | v1: `warnings[]` bei `POST /api/draft/accept` nennt die verfallenden Vertretungen | **zusätzlich** zuerst die offenen Stunden: „*n* Stunden bleiben unverplant: sie stehen im aktiven Plan, aber in keiner Woche." | Additiv (Regel 3.1: neuer Inhalt in einem bestehenden Feld, kein neues Feld, kein neuer Slug). Offene Stunden wandern beim Annehmen mit — verworfen werden sie nicht, ein angenommener Plan darf sie enthalten (PL-11) —, aber keine Wochensicht zeigt sie. Bis zum 05.09. antwortete `accept` in diesem Fall `warnings: []` und der Dialog schwieg (Befund 6 der Abnahme). Der Dialog nennt die Zahl jetzt schon **vor** dem Klick aus `openLessons` des Entwurfs; die Attrappe ist nachgezogen (Regel 3.9). Gemessen: `docs/abnahme/korrektur-05-09.md` |

| PL-16 | v1: jede datierte Ausnahme ab heute verfällt beim Annehmen | Ausnahmen **wandern mit**, wenn ihre Stunde im neuen Plan unverändert steht (gleiche Fachzuordnung, gleiches Zeitfenster, gleicher Raum); nur der Rest verfällt und nur der steht in `warnings[]`. Neues Feld `expiringExceptions` am `DraftDto` nennt die Zahl schon vor dem Klick | Befund PFLICHTENHEFT-9. `COPY_ACTIVE` legt neue `lesson`-Zeilen mit neuen Kennungen an, und `lesson_exception.lesson_id` zeigt auf genau eine Zeile — nach dem Annehmen war jede Vertretung verwaist und verschwand aus jeder Wochensicht. Damit löschte der Muss-Ablauf des Pflichtenhefts („Grunddaten korrigieren → neu rechnen → annehmen", zielbestimmungen.tex Z. 19–23) den gesamten Vertretungsplan der Folgewochen. Das Umhängen läuft in der Transaktion des Annehmens (`ExpiringExceptionsCounter.carryOver` über `shared.domain.ExpiringExceptions`, Richtung wie PL-2). Additiv: `expiringExceptions` ist ein neues Pflichtfeld an einer Antwort, die nur die Web-App liest (Regel 3.1); der Annehmen-Dialog zählt seitdem nicht mehr selbst über `GET /api/exceptions`, sondern nennt dieselbe Zahl wie der Server. Attrappe nachgezogen (Regel 3.9). Tests: `DraftServiceIT.acceptCopyKeepsFutureSubstitutions`, `acceptWarnsAboutExpiringSubstitutions`, `AcceptDialog.test.tsx` |

Für `substitution` nutzbar: `ConflictService.check(List<Assignment>)` (Konflikte einer gedachten Belegung,
ohne sie zu speichern), `ConflictService.context(List<Assignment>)`, `PlanReader.activePlan()`,
`PlanReader.planOf(Timetable)`, `PlanReader.plannedAt(LocalDate)`. Für `sampledata`:
`DraftService.create/accept/syncRequiredLessons`, `SolveService.solveBlocking(int)`.

### `substitution` Teil 2 (05.09., Vollausbau — Bauphase backend3)

**Am Vertrag geändert: nichts.** `GET/POST /api/events`, `DELETE /api/events/{id}`,
`GET /api/substitutions/pending`, `GET /api/substitutions/options`, `GET/POST /api/exceptions`,
`DELETE /api/exceptions/{id}` und `GET/POST/DELETE /api/holidays` sind so gebaut, wie v1 sie
beschreibt — Pfade, Feldnamen und -reihenfolge, Statuscodes, Fehler-Slugs (`exception-exists`,
`validation`, `not-found`) und die Beispieltexte. Kein neuer Pfad, kein neues Feld, kein neuer
Enum-Wert. Der `WeekResolver` erzeugt jetzt alle sieben `kind`-Werte (SU-1 aus Teil 1 ist damit
eingelöst). Gemessen gegen einen frisch gestarteten Stack, Belege in `docs/abnahme/backend3.md`
(62 Zusicherungen, 0 Abweichungen), wiederholbar mit `scripts/abnahme.sh`. **Nachtrag vom selben Tag:**
nach der Zweitprüfung sind es 83 Zusicherungen — die Ergänzungen stehen unten als SU-18 bis SU-24.

Umsetzungsentscheidungen, die eine Quelle ergänzen oder von ihr abweichen:

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| SU-6 | Abschnitt 4: `WeekResolver` als Dienst | die Auflösung eines Tages ist die **statische** Funktion `WeekResolver.resolveDay(DayFacts)`; der Dienst trägt nur die Tatsachen zusammen (`DayFacts` = Plan des Tages, Ereignisse, Ausnahmen, Zeitfenster, Kürzel) | Die Checkliste verlangt `WeekResolverTest` als Test der Ebene 1 (ohne Datenbank). Dieselbe Bauweise wie `ConflictChecker`/`ConflictCheckerTest` in `planning`: Fachlogik als Funktion, Laden daneben. Nur so sind T-W5.1 und T-A2.1 in Sekunden statt Minuten prüfbar |
| SU-7 | `frontend/src/mock/logic/exceptions.ts` rechnet die Folgekonflikte eines Tages selbst aus (nur die drei Doppelbelegungen) | `ExceptionService` ruft `planning.ConflictService.check(...)` auf den aufgelösten Tag | Eine zweite Konfliktprüfung mit eigenen Meldungstexten liefe irgendwann auseinander (SOLVER-PLAN 10.4). So gilt für Vertretungen wortgleich, was im Plan gilt — samt gesperrter Zeit und Raumanforderung des Fachs, die die Attrappe gar nicht prüft. Die Attrappe zieht in der Frontend-Phase nach (wie D-9) |
| SU-8 | — | ~~in diese Prüfung gehen **keine** Zusatzstunden ein~~ — **überholt am 05.09., siehe SU-21**: sie gehen mit ein | `Assignment` und `ConflictDto` sprechen in Stundenkennungen; eine Zusatzstunde hat keine. Die Zweitprüfung hat gezeigt, was das kostet: eine Vertretung durfte die Lehrkraft einer Zusatzstunde doppelt belegen und die Antwort meldete `conflicts: []` |
| SU-9 | Abschnitt 6, DTO-Regeln | eingehalten. **Zusatz**: der Domain-Record `WeekViews.EntryView` trägt ein Feld `subjectId`, das `EntryDto` **nicht** hat | Die Oberfläche braucht am Wocheneintrag nur Kürzel und Farbe (so steht es im Vertrag); die Konfliktprüfung einer Vertretung braucht die Kennung, um die Raumanforderung des Fachs zu prüfen. Ein internes Feld mehr ist billiger als eine zweite Abfrage je Eintrag |
| SU-10 | Teil 1: `Holiday.Kind` als geschachteltes Enum in der Entity | eigenes Enum `substitution.domain.HolidayKind` (wie `EventKind` und `ExceptionKind`) | ArchUnit-Regel 2 verbietet `..api..` jede Abhängigkeit auf eine `@Entity`; `HolidayDto` braucht den Typ. Alle drei neuen Enums liegen deshalb in `domain`, genau wie `EntryKind` und `View` |
| SU-11 | V4: `exception_event` als Verbindungstabelle | `LessonException.eventIds` ist eine `@ElementCollection` von Kennungen (wie `Teacher.subjectIds` in `masterdata`), kein `@ManyToMany` auf `ChangeEvent` | Die API spricht ohnehin nur in Kennungen. Ein gelöschtes Ereignis nimmt seine Zeile per `ON DELETE CASCADE` mit; die Ausnahme bleibt bestehen und verliert nur diesen Anlass — genau das steht am Endpunkt |
| SU-12 | Vertrag `DELETE /api/events/{id}`: „die Zusatzstunde eines `EXTRA_LESSON` wird mit gelöscht" | umgesetzt: `ChangeEventService.delete` löscht vorher die Ausnahmen der Art `ADDED`, die dieses Ereignis nennen | Ohne ihr Ereignis stünde die Zusatzstunde grundlos im Plan. Belegt in `docs/abnahme/backend3.md` und `EventControllerIT.extraLessonCreatesAnAddedException` |
| SU-13 | Vertrag `EventDto.extra` | die Angaben der Zusatzstunde stehen **nicht** in `change_event`, sondern in der Ausnahme `ADDED`, die zusammen mit dem Ereignis entsteht; `EventDto.extra` liest sie von dort | V4 hat in `change_event` keine Spalten dafür, und eine Zusatzstunde ist eine Stunde: Stunden eines Kalendertags stehen in `lesson_exception`. Klasse und Fach in `added_class_id`/`added_subject_id`, Lehrkraft und Raum in den `substitute…`-Spalten, Tag und Zeitfenster in `new_date`/`new_timeslot_id` |
| SU-14 | Checkliste Abschnitt 4: „`scripts/abnahme.sh` … prüft jede Erwartung mit `jq`" | `scripts/abnahme.sh` ist eine kurze Hülle (Voraussetzungen, optional `--frisch`) und ruft `scripts/abnahme-substitution.py` | Dieselbe Bauweise wie `scripts/abnahme-planning.py` und `scripts/abnahme-changelog.py`; `python3` liegt auf Mac und VM ohnehin vor, `jq` nicht sicher. Eine Sprache für alle Abnahmeskripte, ein Format der Ausgabe |
| SU-15 | — | `POST /api/exceptions` und `POST /api/events` prüfen die art-abhängigen Pflichtfelder im **Dienst**, nicht mit Bean-Validation | Die Bean-Validation kennt die Art nicht: `teacherId` ist nur bei `TEACHER_ABSENCE` Pflicht, `newDate` nur bei `MOVE`. Die Feldnamen der Antwort sind dieselben wie in der Attrappe (`extra.timeslotId`, `substituteTeacherId`, `newTimeslotId`) |
| SU-16 | — | `GET /api/substitutions/pending` und `GET /api/exceptions` prüfen `from`/`to` von Hand (Bereich verdreht → 400 `to`; mehr als `WeekResolver.MAX_DAYS` = 366 Tage → 400) | Wie CH-4: an Abfrageparametern wirft Spring sonst eine `ConstraintViolationException`, für die es im `ApiExceptionHandler` keinen Fall gibt — der Client bekäme 500. Die Obergrenze hält einen Tippfehler in `to` davon ab, jahrelang zu rechnen |
| SU-17 | Checkliste: Testklassen `WeekControllerIT` und `MeControllerIT` | die beiden Klassen heißen im Arbeitsbaum `WeekIT` und `MyWeekIT` (aus der Nacharbeit backend2, dort belegt) | Sie prüfen genau das, was die Checkliste ihnen zuschreibt (T-W4.1 mit allen vier Sichten, T-A1.1 mit 401/403). Umbenennen hieße, die schon geschriebenen Belege in `docs/abnahme/backend2-nacharbeit.md` ungültig zu machen — Vorschlag an Rolle 1: die Namen in der Checkliste nachziehen |

#### Nachtrag 05.09. — Prüfbefunde der Zweitprüfung eingearbeitet

**Am Vertrag geändert: nichts an Pfaden, Feldern, Slugs oder Enum-Werten.** Ergänzt wurden Prüfungen,
die vorher fehlten; `api-contract/openapi.yaml` nennt sie jetzt am Endpunkt (additiv, drei
Beschreibungstexte und eine `400`-Antwort an `DELETE /api/exceptions/{id}`). Belege in
`docs/abnahme/backend3.md`, Abschnitt „Nacharbeit 05.09." — 83 Zusicherungen, 0 Abweichungen,
129 Unit- und 171 Integrationstests.

| # | Quelle sagt | hier | Grund |
|---|---|---|---|
| SU-18 | SU-6: die Auflösung eines Tages in vier Schritten | `WeekResolver.resolveDay` hat einen **fünften**: dieselben Ereignisse noch einmal gegen die **aufgelöste** Belegung (Lehrkraft und Raum nach der Vertretung) | Schritt 2 sieht die ursprüngliche Belegung — genau die braucht er für die Anlässe (T-A2.1). Fällt aber die Vertretungslehrkraft am selben Tag ebenfalls aus, stand die Stunde weiter als `SUBSTITUTED` da und fehlte in den offenen Stunden: ein Vertretungsplan mit abwesender Lehrkraft, den niemand sieht. Die Ausnahme bleibt an der Zelle (`exceptionId`), damit die Planung sie aufheben kann. Ausgenommen: entfallene Stunden, Zusatzstunden (keine Stundenkennung, SU-8) und die **Ziele von Verlegungen** — für die gäbe es an diesem Kalendertag keine Ausnahme einzutragen (die Verlegung hängt am Ursprungstag, `UNIQUE (lesson_id, date)`). So zeigt die Woche nur das als offen, was über `POST /api/exceptions` auch zu schließen ist |
| SU-19 | Vertrag `POST /api/exceptions`: 400 `validation` | `ExceptionService.validate` prüft **vor** dem Speichern: unbekannte `substituteTeacherId`, `substituteRoomId`, `newTimeslotId`, `eventIds`; bei `MOVE` den Wochentag des Zielzeitfensters gegen `newDate` und `newDate` als Unterrichtstag; bei `SUBSTITUTION`, dass sich Lehrkraft oder Raum ändert und dass die Vertretung an dem Tag verfügbar ist | Ohne diese Prüfungen lief eine unbekannte Kennung in den Fremdschlüssel, und der Handler antwortete 409 `in-use` („wird noch verwendet und kann nicht entfernt werden") — für eine Neuanlage die falsche Meldung. Eine Verlegung mit einem Zeitfenster des falschen Wochentags stand je nach Feld an zwei Tagen; eine Vertretung ohne Änderung nahm die Stunde aus den offenen heraus, ohne dass jemand unterrichtete. Dieselbe Bauweise wie `ChangeEventService.validateExtra` |
| SU-20 | — | `ExceptionService.findLesson` verlangt zusätzlich, dass die Stunde am **selben Wochentag** liegt und der Tag **Unterrichtstag** ist; `SubstitutionOptionsService.options` prüft dasselbe (sonst 404) | Vorher nahm der Server eine Montagsstunde für einen Dienstag oder für einen Ferientag an: die Zeile stand in der Datenbank, wirkte in keiner Wochensicht und blockierte über `UNIQUE (lesson_id, date)` die richtige Eingabe. An einem Feiertag lieferte `options` weiter Vorschläge |
| SU-21 | SU-8: Zusatzstunden bleiben aus der Folgekonfliktprüfung heraus | sie gehen **mit ein**: `dayConflicts` gibt einer Zusatzstunde eine künstliche negative Kennung (die negierte Ausnahme) und entfernt sie aus `conflicts[].lessonIds` wieder | `Assignment` und `ConflictDto` sprechen in Stundenkennungen, echte sind positiv — also kollidiert nichts, und nach außen geht keine Kennung, die es nicht gibt. Ohne das durfte eine Vertretung die Lehrkraft einer Zusatzstunde doppelt belegen und die Antwort meldete `conflicts: []`. Genannt wird die Zusatzstunde über `objects` und den Meldungstext. **SU-8 ist damit überholt** |
| SU-22 | — | die Nebenläufigkeit zweier gleichzeitiger `POST /api/exceptions` auf **verschiedene** Stunden desselben Zeitfensters wird **nicht** serialisiert | Beide Aufrufe prüfen vor dem Commit des jeweils anderen und melden `conflicts: []`. Der Vertrag nennt `conflicts` ausdrücklich Daten und keinen Fehler („gespeichert wird immer, die Planung entscheidet"), und die aufgelöste Woche zeigt danach beide Stunden. Eine Serialisierung je Kalendertag (Sperre oder `SERIALIZABLE` mit Wiederholung) wäre die einzige vollständige Lösung — Aufwand und Lesbarkeit stehen für ein Zwei-Personen-Sekretariat nicht dafür. Bewusst offen, hier genannt statt vergessen. Für **dieselbe** Stunde am selben Tag fängt die Datenbank den Fall: `lesson_exception_unique` → 409 `exception-exists` (SU-24) |
| SU-23 | Vertrag `DELETE /api/exceptions/{id}` | eine Ausnahme der Art `ADDED` lässt sich hier **nicht** aufheben: 400 mit dem Verweis auf `DELETE /api/events/{id}`. Der Verlaufseintrag beim Aufheben nennt in `oldAssignment` je Art, was aufgehoben wird (`CANCEL` → „entfällt", `MOVE` → das Ziel, sonst die Belegung) | Der Vertrag führt Zusatzstunden nur über Ereignisse (`POST` mit `ADDED` ist schon 400). Wer sie hier löschte, ließ das Ereignis `EXTRA_LESSON` ohne Stunde zurück — `GET /api/events` meldete danach `extra: null` und den Hinweis „Zusatzstunde für ? …". Beim Aufheben einer Verlegung stand vorher zweimal dieselbe Belegung im Verlauf, weil der Ursprungseintrag `CANCELLED` mit unveränderter Lehrkraft und unverändertem Raum ist |
| SU-24 | SU-16: `pending` und `exceptions` prüfen `from`/`to` von Hand | `GET /api/events` prüft ebenso (verdreht → 400 `to`), `POST /api/events` begrenzt den Zeitraum auf `MAX_DAYS`, `requireRange` zählt beide Enden mit (`plusDays(MAX_DAYS - 1)`), `ApiExceptionHandler` beantwortet einen fehlenden Pflichtparameter mit 400 statt 500 und übersetzt `lesson_exception_unique` nach 409 `exception-exists` statt `data-exists` | Alles Fälle, in denen der Server vorher schwieg oder das Falsche sagte: 200 mit leerer Liste bei verdrehtem Bereich; eine still nach 366 Tagen abgeschnittene `affected`-Liste; 500 bei `GET /api/substitutions/options?lessonId=1` ohne `date`; und beim Wettrennen um dieselbe Ausnahme der Slug `data-exists`, den der Vertrag hier nicht kennt (wie PL-14 für `timetable_one_draft`) |

Nicht nachgezogen, weil Sache der Frontend-Phase: `frontend/src/mock/logic/exceptions.ts` kennt die
neuen 400-Fälle noch nicht (wie D-9 und SU-7).

Für andere Module nutzbar: nichts — `substitution` liefert an kein Modul (Abschnitt 4). Innerhalb des
Moduls sind `WeekResolver.days(from, to)` (aufgelöste Unterrichtstage eines Zeitraums, Sicht `SCHOOL`),
`WeekResolver.day(date)` und `WeekResolver.pending(from, to)` die eine Quelle für alles, was auf der
aufgelösten Woche steht: Wochensichten, „Mein Plan", offene Stunden, `affected` der Ereignisse,
Vorschlagslisten und die Folgekonflikte einer Ausnahme.

### `frontend` (05.09., Vollausbau — Bauphase frontend)

**Am Vertrag geändert: nichts** an Pfaden, Feldern, Slugs oder Enum-Werten. `api-contract/openapi.yaml` bleibt v1.
Die Abschrift `frontend/src/api/types.ts` ist mit einem Skript (js-yaml über alle 63 Operationen und Schemas) gegen
den Vertrag gelegt worden; drei Abweichungen, alle additiv behoben:

| # | Vertrag sagt | `types.ts`/`client.ts` vorher | jetzt |
|---|---|---|---|
| F-1 | `DELETE /api/events/{id}` (V-6) | fehlte | `Api.deleteEvent`, `httpApi.deleteEvent`, Hook `useDeleteEvent`; Attrappe löscht Ereignis samt Zusatzstunde, andere Ausnahmen verlieren nur den Anlass. Kein Knopf in der Oberfläche (Ereignis zurücknehmen ist Kann) |
| F-2 | `TeacherOption` ohne `substitutionsThisWeek` (V-5) | optionales Feld | entfernt; `OptionsPanel` zeigt keine Belastung mehr |
| F-3 | `ProblemDetail.instance?` | fehlte | ergänzt |

`ConfigDto.state` bleibt im Client `string` (der Vertrag führt das Enum der 16 Kürzel; die Attrappe prüft es jetzt
in `PUT /config` mit 400 am Feld `state`).

**Attrappe nachgezogen (Regel 3.9, Server gewinnt):** D-9 vollständig (PATCH einer offenen Stunde ohne Raum → 400
`roomId`; zweite Ausnahme je Stunde und Tag → 409 `exception-exists`; `preset-missing` gestrichen; Passwort ≥ 8),
V-11 (`POST /sample-data` löscht Lehrkraft-Konten, legt keine an), R-M1 (`POST /rules` doppelt → 409 `data-exists`),
R-M2 (`reset` schaltet nur die schulweiten Lehrkraft-Zeilen aus). **Nicht nachgezogen:** SU-7 (Folgekonflikte einer
Vertretung prüft die Attrappe weiter nur auf Doppelbelegung) und die 400-Fälle aus SU-19/SU-20 — die Demo braucht sie
nicht, und die Attrappe soll klein bleiben.

**Gemessen gegen das echte Backend** (Compose-Stack, Backend über nginx, Chrome headless 1440×900):
`frontend/scripts/abnahme.spec.ts` spielt die Web-Zeilen der `docs/ABNAHME-CHECKLISTE.md` Abschnitt 1 auf leerer
Datenbank nach — Belege `docs/smoke/PROTOKOLL.md` und `docs/smoke/*.png`. Was dabei am Zusammenspiel auffiel:

| # | Beobachtung | Folge |
|---|---|---|
| F-4 | Der Server liefert für alle geprüften Schritte genau die Texte der Vertragsbeispiele (Warnungen T-W1.4/1.5, 409-Texte G-9/T-W1.3, Konflikttexte T-W3.1/T-W1.2/T-W2.1, Verlaufseintrag T-W7.1) | keine Änderung an der Oberfläche |
| F-5 | Die Sitzung lebt nur im Speicher (api.md Auth-Fluss 5): jedes Neuladen ist ein neues Anmelden — auch nach einem Deploy | bewusst so gelassen; im Browser-Durchlauf läuft alles auf einer Seite (pushState) |
| F-6 | Nach `POST /sample-data {replace:true}` zeigt der Query-Cache die alte Woche, bis der Refetch da ist | Verhalten der Oberfläche ist richtig (Ladezustand, dann neue Daten); der Test wartet mit `expect.poll` |
| F-7 | `GET /api/changelog?classId=1` liefert `total 1` (nur die Ausnahme; „Plan angenommen" hat keine Klasse) — wie der Vertrag | keine Änderung |
| F-8 | Ein angenommener Plan gilt ab dem Tag der Annahme (`PlanReader`: spätester Beginn, der nicht nach dem Tag liegt). Wer am Samstag annimmt, sieht in der Woche „heute" (Mo–Fr davor) noch den vorherigen, oft leeren Stand — und hält das für Datenverlust | Oberfläche geändert: Standardwoche = max(Woche von heute, erste Woche ab „gilt ab"), Hinweisbanner mit Sprung in die erste gültige Woche, „gilt ab …" in Untertitel und Erfolgsmeldung |

**F-5a: Sitzung in localStorage, Token 30 Tage (Entscheidung Nutzer 05.09.).** F-5 bleibt als Befund richtig,
wird aber nicht mehr so gelassen: Das Token lag nur im Speicher, deshalb landete jedes Neuladen (F5) auf `/login` —
im Betrieb störend. Die Web-App legt Token und Principal nach dem Anmelden zusätzlich unter dem Schlüssel
`stundenplan.session` im **localStorage** ab; die Sitzung hält also über Neuladen UND Browser-Neustart, bis
abgemeldet wird oder das Token abläuft. Passend dazu steht die Lebensdauer eines Tokens im Backend auf **30 Tage**
statt 12 Stunden, einstellbar über `app.auth.token-ttl` (ISO-8601-Dauer, Umgebungsvariable `AUTH_TOKEN_TTL`;
`AuthService` liest sie, Standard `P30D`). Der gespeicherte Eintrag ist nur ein Vorschlag: beim App-Start prüft ihn
`GET /api/me`, und 401 oder Netzfehler löschen ihn und führen nach `/login`. Abmelden löscht ihn, und jede
401-Antwort des Clients ebenso. Am API-Vertrag ändert das nichts — der Auth-Fluss aus `api.md` bleibt, nur die
Aufbewahrung im Browser und die Frist weichen von der ursprünglichen Entscheidung ab (NACHTRAG 05.09. in
`07-Konzept/workflows/backbone/api.md`). Beleg: `frontend/src/api/session.test.ts` und der Browser-Durchlauf
(`page.reload()` bleibt auf `/plan`).

**Ungeprüft im Browser:** T-W9.1 Teil „festgelegte Stunde übersteht eine Neuberechnung" (nur `SolveServiceIT` und
`docs/abnahme/planning.md`), Kann-Optionen `classId`/`validFrom` im Dialog gegen den Server (nur Typen), Entfall/
Verlegen/Zusatzstunde gegen den Server (nur gegen die Attrappe geklickt, W4), Ferien-Dialog, 401-Weiterleitung nach
Token-Ablauf (30 Tage, `app.auth.token-ttl`, Standard `P30D`), Safari/Firefox, Ziehen mit echter Maus (D-4 im Beleglauf per Klick-Klick; Drag-and-Drop in Headless-Chrome unzuverlässig).

### `students` (05.09., NACHTRAG — Schülersicht als Klassenkonto, E-50)

**Am Vertrag geändert: additiv.** Neu sind der Enum-Wert `STUDENT` in `Role`, die Felder `classId`
und `classShort` in `UserDto`, `classId` in `CreateUser`, das Schema `ClassAccountDto`, der Endpunkt
`POST /api/users/class-accounts` und der Endpunkt `GET /api/me/class-week`. Klargestellt (keine
Änderung am Verhalten): jedes Feld in `EntryOriginal` ist nullable — so kommt es seit jeher über die
Leitung.

**Datenmodell: `V7__student_class_account.sql`, nicht `V1`.** Geplant war, die Spalte direkt in `V1`
zu ergänzen („nichts ist committet"). Gemessen am 05.09.: der Referenz-Stack hat `V1` längst
ausgeführt, seine `flyway_schema_history` kennt die Prüfsumme, und jeder Start bricht danach mit
`Migration checksum mismatch for migration version 1` ab. `V1` bleibt deshalb **byte-genau
unverändert**; dieselbe Schemaänderung steht additiv in `V7` (Rollen-CHECK erweitern, Spalte
`class_id` mit UNIQUE und Fremdschlüssel, CHECK `user_account_class_required`). Die Startschablone
(`08-Build/starter`) hat keine ausgelieferte Datenbank und trägt die Änderung wie geplant in `V1` —
gleiches Schema, anderer Weg dorthin.

**Warum ein Konto je Klasse und nicht je Person?** Die Recherche
`07-Konzept/recherche/sammelzugang-schueler.md` (Abschnitt „Bewertung") ist eindeutig: ein
schulweites Sammelkonto mit Sicht auf alle Klassen und Klarnamen ist **nicht** zu bauen — keine
Aufsichtsbehörde deckt das. Personalisierte Zugänge (SSO/IServ) wären die saubere Lösung, sind für
dieses Projekt aber weder beauftragt noch in vier Wochen zu bauen. Dazwischen liegt die von LDI NRW,
LfDI RLP und LfDI Sachsen ausdrücklich tolerierte Minimallösung: **klassenweise getrennte, regelmäßig
wechselnde Zugangsdaten**. Genau die ist gebaut. Abwesenheitsgründe sind überall unzulässig und
kommen deshalb gar nicht erst aus dem Server heraus.

| # | Regel | Wo sie steht |
|---|---|---|
| ST-1 | **Rolle `STUDENT` = Klassenkonto.** Ein Konto gehört zu genau einer Klasse (`user_account.class_id`), jede Klasse zu höchstens einem Konto (UNIQUE). CHECK: `role = 'STUDENT'` verlangt `class_id` (V7), `role = 'TEACHER'` verlangt `teacher_id` (V1) | `V7__student_class_account.sql`, `shared/Role.java`, `auth/persistence/UserAccount.java` |
| ST-2 | **Sechs Endpunkte, sonst 403.** Erlaubt sind `GET /api/health`, `POST /api/auth/login`, `DELETE /api/auth/token`, `GET /api/me`, `GET /api/timegrid`, `GET /api/me/class-week`. Alles andere ist 403 — auch `GET /api/me/timetable/week` (Tagesablauf einer einzelnen Lehrkraft) und `PUT /api/me/password` (das Passwort gehört der ganzen Klasse und wird zentral gesetzt) | `auth/api/SecurityConfig.java`, `SecurityConfigTest`, `ClassWeekIT.classAccountMayNotReadAnythingElse` |
| ST-3 | **Klasse aus dem Token, Antwort serverseitig gefiltert.** `GET /api/me/class-week?date=` nimmt nur ein Datum; die Klasse kommt aus dem Principal. Die Woche entsteht im gemeinsamen `WeekResolver` (Sicht `CLASS`) und geht danach durch `StudentView`: `reasons` wird leer, `original` behält nur `teacherShort`, `roomShort`, `date`, `position`. Ein Konto ohne Klasse bekommt 404 `not-found`. Ferien/unterrichtsfreie Tage wie in allen Sichten (`schoolDay: false`) | `substitution/api/ClassWeekController.java`, `substitution/domain/StudentView.java`, `ClassWeekIT` |
| ST-4 | **Konten in einem Zug.** `POST /api/users/class-accounts` legt für jede **aktive** Klasse **ohne** Konto genau ein Konto an: Benutzername = Klassenkürzel klein („7a"), Passwort zufällig (10 Zeichen, `SecureRandom`, ohne `0/O` und `1/l/I`). Das Passwort steht **einmal** in der Antwort im Klartext, danach nur noch als BCrypt-Hash. Idempotent: der zweite Aufruf liefert `[]`. Eine Klasse, deren Kürzel schon als Benutzername vergeben ist, wird übersprungen statt den ganzen Aufruf scheitern zu lassen | `auth/domain/UserService.createClassAccounts`, `UsersIT` |
| ST-5 | **Kein Klarname, nur Kürzel.** `EntryDto` kennt ohnehin nur `teacherShort`; die Grunddaten (`GET /api/teachers`) sind für ein Klassenkonto gesperrt. Die Oberfläche lädt auf `/klassenplan` deshalb nichts nach (`EntryDetailPopover lookup={false}`) | `pages/class-plan/ClassPlanPage.tsx` |

Festgelegt, wo der Vertrag offen ist:

| Fall | Antwort |
|---|---|
| Rolle `STUDENT` ohne `classId` (oder eine andere Rolle **mit** `classId`) | 400 `validation`, `errors[0].field = "classId"` |
| Unbekannte `classId` | 400 `validation` — „Diese Klasse gibt es nicht." |
| Zweites Konto für dieselbe Klasse (`class_id` ist UNIQUE) | 409 `in-use` — „Diese Klasse hat bereits ein Konto." |
| `GET /api/me/class-week` mit einem Konto ohne Klasse (Planung, Lehrkraft) | 404 `not-found` — „Dieses Konto gehört zu keiner Klasse." |
| Klasse löschen, an der ein Klassenkonto hängt | 409 `in-use` — „… wird noch verwendet (zum Beispiel von einem Klassenkonto). Deaktivieren statt löschen." |

Umsetzungsentscheidungen:

| # | Frage | hier | Grund |
|---|---|---|---|
| ST-M1 | Wer darf `GET /api/me/class-week` aufrufen — nur `STUDENT` oder jedes angemeldete Konto? | jedes angemeldete Konto; wer keine Klasse hat, bekommt 404 | Ein Datenleck ist damit ausgeschlossen (die Klasse kommt aus dem Token), und der 404-Fall bleibt prüfbar. Mit `hasRole("STUDENT")` wäre er unerreichbar gewesen: der CHECK in V1 lässt kein Klassenkonto ohne Klasse zu |
| ST-M2 | Filtern in der Oberfläche oder im Server? | im Server (`StudentView`) | Ein Filter im Browser ist keiner — die Anlässe kämen trotzdem über die Leitung. `StudentView` ist eine reine Funktion ohne Spring und ohne Datenbank, wie `WeekResolver.resolveDay` |
| ST-M3 | `CurrentUser` erweitern oder ein zweites Record? | erweitert: `CurrentUser(id, username, role, teacherId, classId)` | Zwei Stellen bauen ihn (`AuthService`, Test), alle Module lesen ihn schon. Ein zweites Record hätte jeden Aufrufer verzweigen lassen |
| ST-M4 | Passwörter der Klassenkonten wieder anzeigbar machen? | nein — genau einmal in der Antwort von `POST /api/users/class-accounts` | Gespeichert wird nur der BCrypt-Hash. Wer eines verliert, setzt über `PUT /api/users/{id}/password` ein neues; das beendet zugleich alle Sitzungen dieses Kontos (A-M8) |
| ST-M5 | `POST /api/sample-data` löschte bisher `role = 'TEACHER'` | löscht jetzt `role <> 'PLANNING'` | Sonst hält `user_account.class_id` die Zeilen in `school_class` fest und das Leeren scheitert am Fremdschlüssel |

**Ungeprüft/offen:** der halbjährliche Passwortwechsel ist eine organisatorische Zusage der Schule,
technisch erinnert nichts daran (keine Ablauffrist auf Klassenkonten). Ob adesso das Klassenkonto
will oder personalisierte Zugänge per SSO erwartet, ist Rückfrage **R5** in
`07-Konzept/besprechung/rueckfragen-interview-2.md`.

### `substitution` — Ferien- und Feiertagsvorlage je Bundesland (05.09., NACHTRAG, E-51)

**Am Vertrag geändert: additiv.** Neu sind zwei Endpunkte unter dem bestehenden Pfadstamm
`/api/holidays`, drei Schemas und ein Fehler-Slug. Kein vorhandener Pfad, kein vorhandenes Feld und
kein Enum-Wert ändert sich; `GET/POST/DELETE /api/holidays` bleibt Wort für Wort, wie v1 es
beschreibt.

| # | Neu | Form |
|---|---|---|
| HP-1 | `GET /api/holidays/preset-years` (Rolle `PLANNING`) | 200 → `HolidayPresetYearsDto {state, schoolYears[]}`. Die Schuljahre, für die es zum Bundesland aus `GET /api/config` eine Vorlage gibt, älteste zuerst. Leeres `schoolYears` ist **kein Fehler**, sondern heißt „für dieses Land liegt keine bei" — die Oberfläche blendet „Vorlage laden" dann aus |
| HP-2 | `POST /api/holidays/preset` (Rolle `PLANNING`) | Eingabe `HolidayPresetInput {schoolYear}` (Muster `^\d{4}/\d{2}$`, sonst 400 `validation` an `schoolYear`), 200 → `HolidayPresetResultDto {added, skipped, state, schoolYear}` |
| HP-3 | Fehler-Slug `preset-missing` (409) | Land oder Schuljahr ohne Vorlage. `detail` nennt die vorhandenen Schuljahre. Neu in der Liste erlaubter Slugs neben `in-use`, `draft-exists`, `solving`, `master-data-incomplete`, `pinned-contradiction`, `data-exists`, `exception-exists` |
| HP-4 | Antwort-Baustein `PresetMissing409` in `openapi.yaml` | eigener Eintrag unter `components/responses`, wie `Solving409` — der Beispieltext zeigt den Fall mit vorhandenen Schuljahren |
| HP-5 | Migration `V8__holiday_presets.sql` | `holiday_preset (id, state, school_year, kind, label, date_from, date_to, source)` mit `CHECK (date_to >= date_from)`, `UNIQUE (state, school_year, date_from, date_to, label)` und Index `(state, school_year, date_from)`; dazu 523 Zeilen. **Erzeugt**, nicht von Hand geschrieben |

Festgelegt, wo der Vertrag offen war:

| Fall | Antwort |
|---|---|
| Zweiter Aufruf mit demselben Schuljahr | 200, `added: 0`, `skipped: n`. Übersprungen wird, was mit **gleicher Bezeichnung und gleichem Anfang und Ende** schon in `holiday` steht — derselbe Schlüssel, über den `holiday_preset_unique` die Vorlage eindeutig hält |
| Ein von Hand geänderter Zeitraum | zählt nicht als Duplikat und kommt als zweite Zeile dazu. Das ist gewollt: der Server kann nicht wissen, ob die Änderung Absicht war |
| Ein von Hand gelöschter Zeitraum | kommt bei einem erneuten Aufruf wieder. Wer ihn dauerhaft los sein will, lädt die Vorlage nicht erneut |
| Bundesland ohne Vorlage | `preset-years` → `schoolYears: []`; `preset` → 409 `preset-missing` mit „Für dieses Bundesland liegt keine Vorlage bei." |

Umsetzungsentscheidungen:

| # | Frage | hier | Grund |
|---|---|---|---|
| HP-M1 | Woher kommt das Bundesland — aus dem Aufruf oder aus der Konfiguration? | aus `SystemConfigService.get().state()`; der Aufruf nennt nur das Schuljahr | Dieselbe Richtung wie `POST /api/rules/reset` (E-48). Ein Land im Aufruf wäre ein zweiter Ort, an dem es steht, und der erste, an dem es falsch stehen kann. Dafür bekommt `substitution` die Kante auf `config` (Tabelle in Abschnitt 4) |
| HP-M2 | Eigene Tabelle oder Zeilen mit `state` in `holiday`? | eigene Tabelle `holiday_preset` | `holiday` ist der Bestand **einer** Schule und wird geändert und gelöscht; die Vorlage ist unveränderliche Beigabe und trägt mit `source` ein Feld, das im Bestand nichts zu suchen hat. Genau die Trennung `rule` / `rule_preset` |
| HP-M3 | Erkennt der Server Duplikate über die Kennung der Vorlagenzeile? | nein, über `(date_from, date_to, label)` | Eine Kennung der Vorlage in `holiday` zu speichern hieße, die Beigabe zum Fremdschlüssel des Bestands zu machen — die nächste Migration mit neuen Zeilen bräche ihn. Der fachliche Schlüssel kostet eine Abfrage und hält den Bestand frei |
| HP-M4 | Migration `V8` oder Schema in `V1`? | `V8` legt die Tabelle **an und füllt sie** | `V1` bis `V7` sind im Referenz-Stack ausgeführt; eine Änderung an `V1` bricht mit `Migration checksum mismatch` (dieselbe Lage wie bei E-50/V7). Die Startschablone hat keine ausgelieferte Datenbank: dort steht die Tabelle im Schema von `V1` und die Zeilen in `V5__holiday_presets.sql` |
| HP-M5 | Feiertage, die auf Samstag oder Sonntag fallen — aufnehmen? | ja, alle (143 der 523 Zeilen) | Sie schaden nicht (an diesen Tagen steht ohnehin kein Unterricht), halten die Vorlage nachprüfbar gleich der Recherche und nehmen einer Schule mit Samstagsunterricht keinen Feiertag weg |
| HP-M6 | Feiertage mit `scope: "teilgebiet"` — aufnehmen? | nein (zwölf Zeilen fallen weg) | Fronleichnam in Teilen von Sachsen und Thüringen, Mariä Himmelfahrt in katholischen Gemeinden Bayerns und das Augsburger Friedensfest gelten **nicht** landesweit. Als Vorlage für ein ganzes Bundesland wären sie schlicht falsch; eine Schule im betroffenen Gebiet trägt den Tag von Hand nach (in `ANLEITUNG.md` 3c benannt) |
| HP-M7 | Zuordnung der Sommerferien zum Schuljahr | zum Schuljahr, in dem der Block **beginnt** (= das Feld `schoolYear` der Recherche). Ausnahme Bayern 2026/27: Beginn 02.08.2027, bleibt trotzdem bei 2026/27 | Sonst hätte Bayern in 2026/27 gar keine Sommerferien und in 2027/28 zwei. Die KMK führt den Block ebenso; die Ausnahme steht im Kopf der Migration und in `HolidayPresetIT` |
| HP-M8 | Bewegliche Ferientage | nicht in der Vorlage | Sie sind laut Land keine amtlichen Ferientage, sondern werden je Schule gewählt. Eine Vorlage kann sie nicht raten |

**Ungeprüft/offen:** die Vorlage deckt die Schuljahre 2026/27 und 2027/28. Für ein weiteres Jahr sind
die beiden JSON in `07-Konzept/recherche/` fortzuschreiben und
`scripts/gen-holiday-presets.mjs` erneut auszuführen; `V8` bleibt dann unverändert und die neuen
Zeilen kommen als eigene Migration (Weg in `ANLEITUNG.md` 3c). Ob adesso die Vorlage überhaupt will
oder die Schule ihre Ferien lieber selbst pflegt, ist im Interview 2 zu bestätigen.
