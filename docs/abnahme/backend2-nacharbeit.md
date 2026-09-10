# Nacharbeit Phase backend2 — Belege

Stand 05.09.2026, 03:10–03:50 · Vollausbau `08-Build/plattform`, Branch `vollausbau`, **nichts committet**.
Anlass: die Prüfbefunde vom 05.09. (Claude-Prüfer, zehn Zeilen; Codex GPT-5.4, neun Zeilen). Jede Zeile hier
ist eine Ausgabe, die so auf dem Bildschirm stand.

Neu in dieser Nacharbeit: der gefüllte Beispielplan (D-1), das Modul `changelog` (V-6, T-W7.1) und
`substitution` Teil 1 (P-7, T-W4.1, A-2, A-3, T-A1.1) — beides gehört laut `docs/VERTRAG.md` Abschnitt 5
zur Phase backend2 und fehlte.

## 1. Prüfstand

Zwei Prüfstände, beide im Container — auf diesem Rechner gibt es weder JDK noch Gradle.

```
# a) Tests: die Pflichtreihenfolge aus ABNAHME-CHECKLISTE Abschnitt 4, Schritt 1
mv backend/build ~/.Trash/plattform-backend-build-1788570782      # 120 Dateien „ … 2" (Finder-Dubletten), Modus 0600
find . -name "* 2*" -not -path "./.git/*" | wc -l               # 0
scripts/ci-backend-test.sh                                      # gradle:9.7.1-jdk21 + postgres:16-alpine

# b) Laufender Stack: der regulaere Compose-Stack, nicht mehr ein eigener Container
cd deployment && BACKEND_PORT=8089 \
  docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build db backend
```

Zu (a): das Skript lief bisher **nicht** im Arbeitsbaum. Im geteilten `backend/build/` lagen 120 Dateien mit
dem Suffix „ 2" und Modus 0600; der Gradle-Nutzer im Container (uid 1000) konnte sie nicht lesen. Das
Verzeichnis ist generiert und von `.gitignore` gedeckt — verschoben nach `~/.Trash`, seitdem läuft es.

## 2. Testlauf (Checkliste Abschnitt 4, Schritt 1)

```
$ scripts/ci-backend-test.sh
==> Netz stundenplan-ci bereitstellen
==> Wegwerf-Datenbank starten (postgres:16-alpine)
==> Auf die Datenbank warten
/var/run/postgresql:5432 - accepting connections
==> Tests im Container ausfuehren
> Task :test
> Task :integrationTest
BUILD SUCCESSFUL in 2m 6s
==> Wegwerf-Datenbank anhalten
```

Aus `backend/build/test-results/`:

| Aufgabe | Klassen | Tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `test` (Ebene 1+2) | 13 | 110 | 0 | 0 | 0 |
| `integrationTest` (Ebene 3+4) | 19 | 133 | 0 | 0 | 0 |

Neue und geänderte Testklassen (Laufzeit aus den XML-Berichten):

| Klasse | Tests | Zeit |
|---|---|---|
| `planning.SolveServiceIT` | 14 (vorher 8) | 36,9 s |
| `sampledata.SampleDataIT` | 7 (vorher 6) | 20,6 s |
| `substitution.WeekIT` | 8 (neu) | 1,7 s |
| `substitution.MyWeekIT` | 4 (neu) | 1,3 s |
| `changelog.ChangelogIT` | 5 (neu) | 3,1 s |
| `changelog.ChangelogControllerTest` | 4 (neu) | 0,6 s |
| `solver.TimetableConstraintProviderTest` | 37 (vorher 33) | 0,3 s |
| `config.ConfigControllerTest` | 7 (vorher 6) | 0,7 s |
| `shared.ApiExceptionHandlerTest` | 4 (vorher 3) | 0,0 s |

Einzelne Fälle, die die Befunde abdecken:

```
 10,62s  T-W8.1 auf dem vollen Bestand: 360 Stunden, keine Doppelbelegung, Ende innerhalb der Rechenzeit
  4,32s  T-W9.1: auf ein belegtes Zeitfenster derselben Klasse verschieben — die andere Stunde weicht
  6,39s  COPY_ACTIVE legt offene Stunden nicht fest — der naechste Lauf plant sie ein
  2,21s  Kann (E-40): classId bewegt nur diese Klasse — die offenen Stunden der anderen bleiben liegen
  1,21s  Zwei gleichzeitige Berechnungen: genau ein 202 und ein 409 solving — nie 500
  3,40s  D-1: nach dem Laden steht ein angenommener Plan ohne harte Konflikte
  0,19s  Drei Plaene mit Gueltigkeitsbeginn: jeder Tag bekommt den Plan, der an ihm gilt
  1,17s  T-W7.1: es gibt weder DELETE noch PUT auf einen Eintrag
```

## 3. Zeitzone (Befund: Container lief in UTC)

```
$ docker exec stundenplan-backend-1 date
Sat Sep  5 03:44:16 AM CEST 2026
```

Gesetzt an zwei Stellen: `ENV TZ=Europe/Berlin` im `backend/Dockerfile` und `TZ: ${TZ:-Europe/Berlin}` beim
Dienst `backend` in `deployment/docker-compose.yml`. Die zweite gewinnt und lässt sich ohne Neubau ändern.
In `deployment/README-VM.md` Abschnitt 5 steht `docker compose exec backend date` jetzt als Prüfschritt.

## 4. D-1 — „Vollständige Schule" laden, mit gefülltem Plan

```
$ curl -s -X POST $B/api/sample-data -H "$A" -d '{"replace":true}'
{"classes":12,"teachers":24,"rooms":18}
Dauer: 11 s                                    # Checkliste D-1: „nach ≤ 30 s"

$ curl -s $B/api/timetable -H "$A"
timetableId 1 | Stunden 360 | belegt 357 | harte Konflikte 0
Beispielstunde: 5a Ma ALB R103 TU 6
```

Vorher lieferte `GET /api/timetable` an dieser Stelle **0 Stunden** — `loadFull()` enthielt nur einen
TODO-Kommentar. Jetzt läuft der Weg, den auch eine Person im Browser geht: Entwurf anlegen, rechnen lassen,
annehmen. 357 von 360 Stunden bei 10 s Rechenzeit; drei bleiben offen und stehen in `openLessons`.

## 5. V-6 und T-W7.1 — Änderungsverlauf

```
$ curl -s $B/api/changelog -H "$A"
total 1 | page 0 | size 50
{"id": 1, "timestamp": "2026-09-05T01:45:40.587711Z", "action": "TIMETABLE_ACCEPTED", "date": null,
 "position": null, "classShort": null, "oldAssignment": "kein Plan",
 "newAssignment": "Plan angenommen (357 Stunden)", "reasons": "", "username": "admin"}

DELETE /api/changelog/1 -> 404
PUT    /api/changelog/1 -> 404
```

Der Eintrag entsteht in `DraftService.accept` über `ChangelogService.record(...)` — die Zwischenlösung mit
`JdbcTemplate` (PL-1) ist damit abgelöst. Alle zehn Felder des Vertragsschemas `ChangelogItem` sind besetzt
oder bewusst leer; `classShort` kommt aus `school_class`, `username` aus `user_account` (beides über eine
Abfrage, weil `changelog` diese Module nicht benutzen darf).

## 6. P-7, T-W4.1 — Wochensichten

Der Plan wurde am 05.09. angenommen und gilt ab diesem Tag; der Montag **dieser** Woche (31.08.) liegt davor,
die Woche ist deshalb zu Recht leer. Geprüft wurde die Woche ab dem 07.09.

```
view=SCHOOL      -> monday 2026-09-07 | Eintraege je Tag [71, 71, 72, 71, 72] | kinds ['REGULAR']
view=CLASS&id=1  -> monday 2026-09-07 | Eintraege je Tag [6, 6, 6, 6, 6]      | kinds ['REGULAR']
view=TEACHER&id=1-> monday 2026-09-07 | Eintraege je Tag [3, 4, 4, 4, 3]      | kinds ['REGULAR']
view=ROOM&id=1   -> monday 2026-09-07 | Eintraege je Tag [5, 4, 4, 1, 6]      | kinds ['REGULAR']

Klasse 5a, Montag: Positionen [1, 2, 4, 5, 6, 7]      # Position 3 ist die grosse Pause
  2026-09-07 MO 1 De ENG R107 REGULAR original None reasons [] exceptionId None

Raumsicht R101: doppelt belegte Zeitfenster 0
view=CLASS ohne id      -> 400
view=ROOM&id=999999     -> 404
```

Ferien (`holiday`, V5) werden beachtet:

```
schoolDay je Tag [False, True, True, True, True] | Eintraege je Tag [0, 71, 72, 71, 72]
```

**Stand Teil 1:** jeder Eintrag ist `REGULAR`. Ereignisse (W-5) und datierte Ausnahmen (W-6) legen sich in
Teil 2 darüber; die Form der Antwort ändert sich dadurch nicht mehr.

## 7. A-2, A-3, T-A1.1 — „Mein Plan" und Zugriffsschutz

```
Konto albrecht (teacherId 1):
monday 2026-09-07 | Eintraege je Tag [3, 4, 4, 4, 3] | Summe 18 | teacherIds [1] | kinds ['REGULAR']

als Lehrkraft GET /api/timetable         -> 403
als Lehrkraft GET /api/timetable/week    -> 403
ohne Token    GET /api/me/timetable/week -> 401
Planung       GET /api/me/timetable/week -> 404
```

18 Wochenstunden — genau die höchste Last des Beispielbestands. In der Antwort steht keine fremde
Lehrkraft-Kennung; die Lehrkraft kommt aus dem Token, nie aus einem Parameter.

## 8. Nebenläufigkeit und Grenzen

```
Zwei gleichzeitige POST /api/draft/solve:   A 409   B 202          # vorher: 202 + 500
Zwei gleichzeitige POST /api/draft:         201 409
Nach dem Abbruch: Entwurf EDITING | Stunden 360 | Fehler None

PUT /api/config maxSolveSeconds 601
-> 400 urn:pushnpull:problem:validation [{'field': 'maxSolveSeconds', 'message': 'höchstens 600'}]

D-5 danach: timetableId 1 | Stunden 360 | belegt 357 | harte Konflikte 0
```

## 9. Was hier nicht geprüft wurde

- **Browser.** Kein Schritt dieser Nacharbeit lief in der Oberfläche; das Web hat die neuen Endpunkte noch
  nicht angebunden (Phase `frontend`).
- **Der Dienst `web` und `mock`** des Compose-Stacks wurden nicht gestartet — geprüft wurde gegen das
  Backend direkt (`BACKEND_PORT=8089`). Der nginx-Weg ist unverändert und in `docs/abnahme/planning.md` belegt.
- **`scripts/abnahme-planning.py`** wurde auf den regulären Stack umgestellt (Standardadresse
  `http://127.0.0.1:1500`, Passwort aus `ABNAHME_ADMIN_PW`), aber in dieser Sitzung nicht erneut ganz
  durchlaufen — dafür fehlt der Dienst `web`.
- **`substitution` Teil 2** (Ereignisse, Vertretungen, `kind`-Werte außer `REGULAR`) ist nicht gebaut und
  gehört laut Vertrag in backend3.
