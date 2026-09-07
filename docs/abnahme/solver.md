# Abnahme — Modul `solver` (Timefold-Modell, Constraints, Übersetzer, Läufe; W-8, W-9)

Stand 05.09.2026, Bauphase backend2. Belegt sind die Solver-Anteile von **S-2, S-3, S-4** (und damit **T-W8.1**
Teil 1 und 2) der `docs/ABNAHME-CHECKLISTE.md`, die Vorbedingung `solving` (409) und der Abbruch
(`DELETE /api/draft/solve`), sowie `gradle test` / `gradle integrationTest` im Container. Die Endpunkte gehören dem
Modul `planning` (Stand des parallel bauenden Agenten am 05.09., 02:25 Uhr — kompiliert, seine Tests waren zu dem
Zeitpunkt noch im Umbau und blieben ausgeschlossen); der Solver liefert, was dahinter rechnet.

## Wie gemessen wurde

Kein JDK auf dem Mac; mehrere Module entstehen gleichzeitig, und `plattform/backend/build/` enthält ~120 Dateien
mit Suffix „ 2" im Modus 0600, die der Gradle-Nutzer im Container nicht lesen kann (`compileJava` bricht ab).
Deshalb lief alles auf einer rsync-Momentaufnahme von `backend/` ohne `build/` und `.gradle/` unter
`…/scratchpad/backend-solver` (Dateien dieses Moduls identisch mit dem Arbeitsbaum), mit eigenem
Gradle-Cache-Volume `stundenplan-gradle-solver` (Kopie von `stundenplan-gradle`) und eigener Wegwerf-Datenbank —
sonst genau die Befehle aus `scripts/ci-backend-test.sh`:

```
docker run -d --rm=true --name stundenplan-ci-db-solver --network stundenplan-ci \
  -e POSTGRES_DB=timetable -e POSTGRES_USER=tt -e POSTGRES_PASSWORD=tt postgres:16-alpine
docker run --rm=true --network stundenplan-ci -v <snapshot>:/w -w /w -v stundenplan-gradle-solver:/home/gradle/.gradle \
  -e SPRING_PROFILES_ACTIVE=ci -e SPRING_DATASOURCE_URL=jdbc:postgresql://stundenplan-ci-db-solver:5432/timetable \
  -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt -e ADMIN_INITIAL_PASSWORD=admin \
  gradle:9.7.1-jdk21 gradle --no-daemon test --rerun integrationTest --rerun
```

Für die Schritte gegen den laufenden Stack: dieselbe Momentaufnahme mit `gradle bootRun` (Profil `ci`, Port 18080
veröffentlicht), Datenbank frisch (Flyway V1–V5), `admin`/`admin`. Aufrufe per HTTP aus einem Python-Skript
(`urllib`, gleiche Anfragen wie die `curl`-Zeilen der Checkliste; `-A` = Token der Planung + JSON).

## 1. Tests

| Befehl | Ausgabe |
|---|---|
| `gradle --no-daemon test --rerun integrationTest --rerun` (Momentaufnahme, `src/test/…/planning` ausgeschlossen) | `BUILD SUCCESSFUL in 1m 13s`; `test`: **89** Fälle, 0 Fehler (davon Modul solver: `TimetableConstraintProviderTest` 33, `SolverTranslatorTest` 7, `SmallDatasetSolveTest` 3); `integrationTest`: **86** Fälle, 0 Fehler (davon solver: `SolverRunnerIT` 4, `FullDatasetSolveIT` 1); `ArchitectureTest` 5/5 grün (Regel 1: `solver` ohne Persistenz; Regel 4b: `solver → shared`) |
| `gradle integrationTest --rerun --tests 'de.wsp3_1.solver.SolverRunnerIT'` zweimal hintereinander | beide `BUILD SUCCESSFUL`; Laufzeiten je Fall: Abbruch 1,07 s (Start + 1 s + Abbruch, Ergebnis ≤ 30 ms danach), Zeitablauf 3,13 s bei `maxSolveSeconds = 3` aus `SystemConfig`, blockierend 2,0 s |
| `FullDatasetSolveIT` (360 Stunden, 12 Klassen, 24 Lehrkräfte, 18 Räume, 10 s) | `10175 ms, Score 0hard/-4medium/-2soft, offen 4 von 360`; `HardConflicts.of(...)` = `[]` |
| `SmallDatasetSolveTest` (Pflichtenheft-Daten, 8 Stunden, 2 s) | `0hard/0medium/…`, alle 8 belegt, keine Doppelbelegung, Sperrzeit Schulz Fr 5./6. eingehalten, Physik in P1; festgelegte Stunden bleiben (T-W9.1); festgelegte Stunde in der Pause bleibt stehen und zählt `-1hard` |
| Einmalige Messung (Probe, nicht im Repo): Basis-`spent-limit` 2 s, `SolverConfigOverride` 6 s | `dauer=6190 ms` — das Override ersetzt das Basis-Limit (VERTRAG SV-4) |

## 2. Schritte gegen den laufenden Stack (S-1 bis S-4, T-W8.1)

`maxSolveSeconds` vorher auf 10 gesetzt, damit der Durchlauf kurz bleibt. Voller Beispielbestand (D-1) geladen.

| Aufruf | Ausgabe |
|---|---|
| `POST /api/auth/login admin` | 200 role=PLANNING |
| `GET /api/config` | 200 maxSolveSeconds=60 |
| `PUT /api/config maxSolveSeconds=10` | 200 maxSolveSeconds=10 |
| `POST /api/sample-data {"replace":true}` | 200 {'classes': 12, 'teachers': 24, 'rooms': 18} |
| `GET /api/timetable (nach D-1)` | 200 lessons=0 |
| `POST /api/draft {"source":"EMPTY"}` | 201 status=EDITING lessons=0 |
| `POST /api/draft/accept {} (S-1)` | 200 lessons=0 |
| `GET /api/timetable (S-1)` | 200 lessons=0 |
| `POST /api/draft {"source":"EMPTY"} (S-2)` | 201 lessons=0 openLessons=144 |
| `POST /api/draft/solve {}` | 202 body=None |
| `GET /api/draft sofort danach` | 200 status=SOLVING solve={'startedAt': '2026-09-05T00:28:29.822508Z', 'limitSeconds': 10, 'lastScore': None, 'error': None} |
| `POST /api/draft/solve {} waehrend des Laufs` | 409 type=urn:pushnpull:problem:solving |
| `GET /api/draft alle 2 s bis status != SOLVING (6 Abfragen)` | 200 status=EDITING nach 12.6 s; solve={'startedAt': '2026-09-05T00:28:29.822508Z', 'limitSeconds': 10, 'lastScore': '0hard/-6medium/-9soft', 'error': None}; lessons=360, belegt=354, offen=6; Invariante belegt-oder-offen=ok; Konflikttypen=[]; harte=0; RULE_VIOLATION=0 (severity []); openLessons=6; warnings=[] |
| `GET /api/timetable (S-3)` | 200 lessons=0 |
| `POST /api/draft/solve {} (Abbruch-Probe)` | 202 |
| `DELETE /api/draft/solve nach 2 s` | 202 |
| `GET /api/draft nach Abbruch` | 200 status=EDITING 0.3 s nach DELETE (gesamt 2.4 s); belegt=355; lastScore=0hard/-5medium/-7soft |
| `DELETE /api/draft/solve ohne Lauf` | 202 |
| `POST /api/draft/accept {} (S-4)` | 200 lessons=360 conflicts=0 |
| `GET /api/timetable (S-4)` | 200 lessons=360; belegt=355; identisch mit Entwurf nach Abbruch-Lauf: True |
| `GET /api/draft nach Annehmen` | 404 type=urn:pushnpull:problem:not-found |

Ergebnis: S-2 erfüllt — Ende ≤ `maxSolveSeconds` + 5 s (12,6 s inkl. 2-s-Abfragetakt bei Limit 10 s), `status EDITING`,
`solve.lastScore` gesetzt, jede der 360 Stunden belegt oder offen (354/6), `openLessons` = 6, keine Konflikte der
Typen `ROOM_/TEACHER_/CLASS_DOUBLE_BOOKED`, keine `RULE_VIOLATION`. S-3: aktiver Plan bleibt leer. S-4: nach
`accept` hat `GET /api/timetable` genau die 360 Stunden des Entwurfs mit identischem `timeslotId/roomId`.
Zusatz: zweiter Start während des Laufs → 409 `solving`; Abbruch nach 2 s → `EDITING` 0,3 s später mit dem bisher
besten Ergebnis (355 belegt); Abbruch ohne Lauf → 202.

## 3. Nicht belegt / offen

- `POST /api/sample-data` liefert nach dem Laden `GET /api/timetable` mit `lessons: 0` — der gefüllte Plan (D-1,
  V-11, `SolveService.solveBlocking`) ist Sache von `sampledata`/`planning`, nicht dieses Moduls.
- T-W9.1 gegen den Stack (PATCH einer Stunde, dann erneut lösen) nicht gefahren — Solver-Seite per
  `SmallDatasetSolveTest.tW9_1_…` belegt, der Endpunkt-Teil gehört `planning` (`SolveServiceIT`).
- Kann-Option `classId` (E-40): im Solver nur über `pinned` im `SolveInput` abgebildet, nicht separat gemessen.
