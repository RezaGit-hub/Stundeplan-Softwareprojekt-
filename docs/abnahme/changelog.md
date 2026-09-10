# Abnahme — Modul `changelog` (Änderungsverlauf, W-7)

Stand 05.09.2026, 02:00–02:15 · Vollausbau `08-Build/plattform`, Branch `vollausbau`, **nichts committet**.
Belegt sind die Checklistenzeile **V-6** und der Testfall **T-W7.1** aus `docs/ABNAHME-CHECKLISTE.md`.
Jede Zeile hier ist eine Ausgabe, die so auf dem Bildschirm stand.

**Was in dieser Runde dazukam** (das Modul stand seit der Nacharbeit backend2 mit dem Lesepfad und dem
Eintrag zum Annehmen — `docs/abnahme/backend2-nacharbeit.md` Abschnitt 5):

| | |
|---|---|
| Schreibweg für Ausnahmen | `NewEntry.exceptionCreated(...)` und `NewEntry.exceptionDeleted(...)` — die Aufrufpunkte, die `substitution` in backend3 benutzt; die Anlässe werden dort einmal mit „; " verbunden, nicht in jedem Aufrufer |
| Kürzung statt Datenbankfehler | `ChangelogService.cut(...)` schneidet zu lange Belegungen auf die Spaltenbreite (255 Zeichen). **Seit 05.09.** gilt das nur noch für die Belegungen: die Anlässe stehen in einer Spalte ohne Längengrenze (Migration V6) |
| ArchUnit-Regel des Moduls | `ChangelogArchitectureTest`: keine Klasse in `..changelog..` ruft eine Methode mit Namen `delete*`; das Repository erklärt selbst nichts Löschendes |
| Fünf neue Integrationstests | T-W7.1 mit **beiden** Anlässen und Filter 7a, Rücknahme, Filter Lehrkraft/Stunde, Paginierung, vollständige Anlässe. **Seit 05.09.** legen alle fünf ihre Einträge über `POST`/`DELETE /api/exceptions` an, nicht mehr über `ChangelogService` |
| Abnahmeskript | `scripts/abnahme-changelog.py` — fährt V-6 und T-W7.1 gegen einen laufenden Stack ab, Exit-Code 1 bei jeder verletzten Zusicherung |

## 1. Prüfstand

Zwei Prüfstände, beide im Container — auf diesem Rechner gibt es weder JDK noch Gradle. Eigener
Compose-Projektname und eigene Ports, damit der Lauf keinem anderen Stack ins Gehege kommt.

```
# a) Tests
scripts/ci-backend-test.sh                                  # gradle:9.7.1-jdk21 + postgres:16-alpine

# b) Laufender Stack, leere Datenbank
cd deployment && BACKEND_PORT=8092 DB_PORT=15452 docker compose -p changelog-abnahme \
  -f docker-compose.yml -f docker-compose.dev.yml up -d --build db backend
cd .. && ABNAHME_BASE=http://127.0.0.1:8092 python3 scripts/abnahme-changelog.py
```

```
$ curl -s http://127.0.0.1:8092/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}
```

## 2. Testlauf

```
$ scripts/ci-backend-test.sh
==> Netz stundenplan-ci bereitstellen
==> Wegwerf-Datenbank starten (postgres:16-alpine)
/var/run/postgresql:5432 - accepting connections
==> Tests im Container ausfuehren
> Task :test
> Task :integrationTest
BUILD SUCCESSFUL in 2m 21s
```

Aus `backend/build/test-results/` (ganze Testmenge, nicht nur dieses Modul):

| Aufgabe | Klassen | Tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `test` (Ebene 1+2) | 14 | 112 | 0 | 0 | 0 |
| `integrationTest` (Ebene 3+4) | 19 | 138 | 0 | 0 | 0 |

Die Klassen dieses Moduls:

| Klasse | Tests | Zeit |
|---|---|---|
| `changelog.ChangelogIT` | 10 (vorher 5) | 3,9 s |
| `changelog.ChangelogControllerTest` | 4 | 0,9 s |
| `changelog.ChangelogArchitectureTest` | 2 (neu) | 1,3 s |

### Die ArchUnit-Regel greift wirklich

Eine Regel, die nie zuschlägt, belegt nichts. Deshalb wurde sie einmal absichtlich verletzt
(`probeVerstoss()` mit `entries.deleteById(1L)` in `ChangelogService`, danach zurückgenommen):

```
$ docker run --rm -v "$PWD/backend":/w -w /w gradle:9.7.1-jdk21 \
    gradle --no-daemon test --rerun --tests 'de.wsp3_1.changelog.ChangelogArchitectureTest'
ChangelogArchitectureTest > T-W7.1: keine Klasse des Moduls ruft eine loeschende Methode auf FAILED
    java.lang.AssertionError at ChangelogArchitectureTest.java:42
BUILD FAILED in 12s
```

Nach dem Zurücknehmen ist die Datei wieder Zeichen für Zeichen die alte (`grep -c probeVerstoss` → 0),
und beide Regeln sind grün.

## 3. V-6 und T-W7.1 gegen den laufenden Stack

Vollständige Ausgabe von `scripts/abnahme-changelog.py`; `$B` = `http://127.0.0.1:8092`, `$A` =
`Authorization: Bearer <Token>`.

### Vorbereitung (G-1 bis G-7, P-1, P-6)

Grunddaten des Pflichtenhefts über die eigene API, Regelplan Mo/Di von Hand, dann annehmen.

```
$ curl -s -X POST $B/api/draft/accept -H "$A" -d '{}'
  -> HTTP 200
  7a=1 8b=2 Meyer=1 Weber=2 | Stunde 7a Mo 1 = 1
```

### Der Eintrag zum Annehmen

```
$ curl -s -X GET $B/api/changelog -H "$A"
  -> HTTP 200
  total 1 | page 0 | size 50
  {"id": 1, "timestamp": "2026-09-05T02:08:40.715090Z", "action": "TIMETABLE_ACCEPTED", "date": null,
   "position": null, "classShort": null, "oldAssignment": "kein Plan",
   "newAssignment": "Plan angenommen (7 Stunden)", "reasons": "", "username": "admin"}
```

Geprüft: genau ein Eintrag · `action TIMETABLE_ACCEPTED` · Belegungen als Text · Verursacher `admin` ·
**alle zehn Felder** des Vertragsschemas `ChangelogItem` und kein Feld zuviel (`sorted(item)` gegen die
Feldliste aus `openapi.yaml`).

### Der Eintrag zur Vertretung — seit der Nacharbeit 05.09. über die Schnittstelle

`EXCEPTION_CREATED` schreibt das Modul `substitution`. Als diese Datei entstand, gab es
`POST /api/exceptions` noch nicht, und das Skript setzte die Zeile mit `psql` direkt in
`changelog_entry` — im Protokoll als **PLATZHALTER** gekennzeichnet, mit dem Vermerk: „Wer backend3
baut, ersetzt beides."

**Eingelöst am 05.09.** Der Endpunkt ist da; `scripts/abnahme-changelog.py` legt das Leitszenario jetzt
über die Schnittstelle an — zwei Ereignisse und zwei Ausnahmen —, der Zugriff auf den
Datenbank-Container ist aus dem Skript entfernt. Dieselbe Ablösung in `ChangelogIT`: die vier
`changelog.record(...)`-Aufrufe sind durch `POST /api/events`, `POST /api/exceptions` und
`DELETE /api/exceptions/{id}` ersetzt. Die Erwartungen unten sind unverändert geblieben — genau das
war der Zweck des Platzhalters.

```
$ curl -s -X POST $B/api/events -H "$A" \
    -d '{"kind": "TEACHER_ABSENCE", "teacherId": 1, "dateFrom": "2026-09-21",
         "dateTo": "2026-09-21", "reason": "Krankmeldung"}'                      # -> 201, id 1
$ curl -s -X POST $B/api/events -H "$A" \
    -d '{"kind": "ROOM_BLOCKED", "roomId": 1, "dateFrom": "2026-09-21",
         "dateTo": "2026-09-21", "reason": "Wasserschaden"}'                     # -> 201, id 2
$ curl -s -X POST $B/api/exceptions -H "$A" \
    -d '{"date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION",
         "substituteTeacherId": 3, "substituteRoomId": 2, "eventIds": [1, 2]}'
  -> HTTP 201
  OK   POST /api/exceptions (Vertretung Schulz/R204) -> 201
  OK   POST /api/exceptions (Entfall 8b) -> 201
  zwei Ausnahmen ueber die Schnittstelle angelegt (7a und 8b)
```

Der Plan wird dafür mit `{"validFrom":"2026-09-01"}` angenommen: ohne „Gültig ab" begänne er am Tag des
Laufs, und nach dem 21.09.2026 gäbe es an diesem Kalendertag keinen Plan mehr — `POST /api/exceptions`
antwortete dann 404. Der Lauf vom 05.09. gegen einen frischen Stack (Port 8094) meldet
**42 Zusicherungen, alle halten**.

Zweite Änderung derselben Runde: die Anlässe werden **nicht mehr gekürzt**. `changelog_entry.reasons`
ist seit Migration **V6** vom Typ `text`; bei sechs Anlässen à 255 Zeichen standen vorher nur die
ersten 1000 Zeichen da, und ganze spätere Anlässe fielen weg — der Vertrag verlangt alle. Beleg:
`ChangelogIT.allReasonsSurviveEvenWhenTheyAreLong`.

### V-6: Filter Klasse 7a

```
$ curl -s -X GET $B/api/changelog?classId=1 -H "$A"
  -> HTTP 200
  total 1
  {"id": 2, "timestamp": "2026-09-05T02:08:40.986038Z", "action": "EXCEPTION_CREATED",
   "date": "2026-09-21", "position": 1, "classShort": "7a", "oldAssignment": "Meyer / R101",
   "newAssignment": "Schulz / R204", "reasons": "Krankmeldung; Wasserschaden", "username": "admin"}
```

Alle zehn Zusicherungen der Checkliste halten: `total 1` · `action EXCEPTION_CREATED` ·
`date 2026-09-21` · `position 1` · `classShort 7a` · `oldAssignment "Meyer / R101"` ·
`newAssignment "Schulz / R204"` · `reasons "Krankmeldung; Wasserschaden"` (**beide** Anlässe in **einem**
Eintrag) · `username admin` · `timestamp` gesetzt. `classShort` kommt aus `school_class`, `username` aus
`user_account` — beides über eine Abfrage, weil `changelog` diese Module nicht benutzen darf.

### Reihenfolge und die übrigen Filter

```
$ curl -s -X GET $B/api/changelog -H "$A"
  Reihenfolge: EXCEPTION_CREATED, EXCEPTION_CREATED, TIMETABLE_ACCEPTED     # neueste zuerst

  Filter Lehrkraft Meyer      -> 2
  Filter Lehrkraft Weber      -> 0
  Filter Stunde 7a Mo 1       -> 1
  Zeitraum from/to = 21.09.   -> 2
  ab 22.09.                   -> 0
  Klasse 8b UND Weber         -> 0        # Filter greifen zusammen, nicht wahlweise
```

### Seitenweise lesen

```
$ curl -s -X GET $B/api/changelog?page=0&size=1 -H "$A"
  Seite 0: total 3, items 1
  Seite 1: total 3, items 1
  Seite 2: total 3, items 1
  -> drei Seiten, jeder Eintrag genau einmal (keine Dublette, keine Lücke)
  -> page=9: items [], total 3 — hinter dem Ende ist leer, kein Fehler
```

### T-W7.1: kein Weg zum Ändern oder Löschen

```
  DELETE /api/changelog/3         -> 404      # es gibt keine Route auf einen einzelnen Eintrag
  PUT    /api/changelog/3         -> 404
  PATCH  /api/changelog/3         -> 404
  DELETE /api/changelog           -> 405      # die Liste kennt nur GET
  POST   /api/changelog           -> 405
  PUT    /api/changelog           -> 405
  -> danach: total 3, unverändert
```

### Falsche Eingaben, fehlende Anmeldung, falsche Rolle

```
  GET /api/changelog?size=501                       -> 400, errors[0].field = size
  GET /api/changelog?size=0                         -> 400, errors[0].field = size
  GET /api/changelog?page=-1                        -> 400, errors[0].field = page
  GET /api/changelog?from=2026-09-30&to=2026-09-01  -> 400, errors[0].field = to
  GET /api/changelog ohne Token                     -> 401
  GET /api/changelog als Rolle Lehrkraft            -> 403
```

Die vier 400er prüft der Controller von Hand, nicht mit `@Validated`: an Abfrageparametern wirft Spring
sonst eine `ConstraintViolationException`, für die es im `ApiExceptionHandler` keinen Fall gibt — der
Client bekäme 500 statt 400 mit Feldliste.

## 4. Was dieses Modul nicht belegen kann

| Punkt | Warum | Wer |
|---|---|---|
| `EXCEPTION_CREATED`/`EXCEPTION_DELETED` **über die Schnittstelle** (`POST`/`DELETE /api/exceptions`) | den Endpunkt baut `substitution` Teil 2 | backend3 |
| V-1 bis V-5 (Ereignis anlegen, Vertretung zuweisen) | dasselbe | backend3 |
| Browser: keine Schaltfläche „Löschen"/„Bearbeiten" im Verlauf (zweite Hälfte von V-6) | Weboberfläche | frontend |

Für backend3 ist der Anschluss fertig: `ChangelogService.record(NewEntry.exceptionCreated(...))` bzw.
`...exceptionDeleted(...)`, die Anlässe als `List<String>`. Mehr braucht es nicht — die Trennung mit „; ",
die Kürzung und der Zeitstempel entstehen im Modul.
