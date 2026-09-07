# Abnahme Modul `planning` — Belege

Stand 05.09.2026, 02:10–02:45 · Vollausbau `08-Build/plattform`, Branch `vollausbau`, **nichts committet**.
Jede Zeile hier ist eine Ausgabe, die so auf dem Bildschirm stand — gemessen gegen einen laufenden Stack
(Backend als `app.jar` im Container, PostgreSQL 16 daneben), nicht behauptet.

Modulumfang: Plan-Lebenslauf `DRAFT`/`ACTIVE`/`ARCHIVED`, der eine Entwurf (leer oder Kopie), Stunden mit
Festlegung, Handaufbau mit `ConflictChecker`, Annehmen mit Vorbedingungen, Verwerfen, Konfliktliste,
Berechnung über das Modul `solver`. Checklistenzeilen P-1 bis P-6, D-4, D-5, S-1 bis S-4 und die Testfälle
T-W1.2, T-W3.1, T-W3.2, T-W8.1 (Teil Entwurf/Annehmen), T-W9.1.

## 1. Prüfstand

Andere Module entstanden zur selben Zeit im selben Arbeitsbaum; der Prüfstand hat deshalb eigene Namen,
einen eigenen Port und eine eigene Arbeitskopie (Lernnotiz 05.09.: `scripts/ci-backend-test.sh` benutzt
feste Container- und Volume-Namen, zwei Läufe gleichzeitig reißen sich die Wegwerf-Datenbank weg).

```
rsync -a --delete --exclude .git --exclude build --exclude .gradle \
      08-Build/plattform/ /private/tmp/plan-backend2/
diff -rq --exclude=.git --exclude=build --exclude=.gradle 08-Build/plattform /private/tmp/plan-backend2
# (keine Ausgabe — Arbeitskopie und Arbeitsbaum sind gleich)

docker network create plan-b2-net
docker run -d --rm=true --name plan-b2-abn-db --network plan-b2-net \
  -e POSTGRES_DB=timetable -e POSTGRES_USER=tt -e POSTGRES_PASSWORD=tt postgres:16-alpine
docker run --rm=true -v "$PWD/backend":/w -w /w -v plan-b2-gradle:/home/gradle/.gradle \
  gradle:9.7.1-jdk21 gradle --no-daemon bootJar
docker run -d --rm=true --name plan-b2-app --network plan-b2-net -p 18091:8080 \
  -v "$PWD/backend/build/libs":/app -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://plan-b2-abn-db:5432/timetable \
  -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt \
  -e ADMIN_INITIAL_PASSWORD=admin -e TZ=Europe/Berlin \
  eclipse-temurin:21-jre java -jar /app/app.jar
```

```
curl -s http://127.0.0.1:18091/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}
```

`B=http://127.0.0.1:18091`, `A="Authorization: Bearer $TOKEN"` (Token aus `POST /api/auth/login`,
`admin`/`admin`). Vorbedingung sind die Grunddaten des Pflichtenhefts (Checkliste G-1 bis G-7), angelegt
über die eigene API: Fächer Ma/De/Ph, Räume R101/R204/P1, Raster Mo–Fr mit sieben Positionen (Position 3
ist die große Pause), Lehrkräfte Meyer/Weber/Schulz (Schulz Fr 5./6. gesperrt = Zeitfenster 34 und 35),
Klassen 7a/8b/9c mit Fächerlisten. Zeitfenster-Kennungen: `MO1=1 MO2=2 MO4=4 MO5=5 MO6=6 TU1=8 FR6=34`.

## 2. Gemessene Schritte

### P-1 — Regelplan Montag/Dienstag von Hand (siebenmal `POST /api/draft/lessons`)

```
curl -s -X POST $B/api/draft -H "$A" -H 'Content-Type: application/json' -d '{"source":"EMPTY"}'
-> HTTP 201, status EDITING

curl -s -X POST $B/api/draft/lessons -H "$A" -H 'Content-Type: application/json' \
     -d '{"classId":1,"subjectId":1,"timeslotId":1,"roomId":1}'   (und sechs weitere)
-> HTTP 201 (siebenmal)

curl -s -X GET $B/api/draft -H "$A"
lessons: 7 | alle pinned: True
teacherShort: ['MEY', 'WEB', 'MEY', 'MEY', 'WEB', 'MEY', 'MEY']
conflicts: []
openLessons: [{"classId": 3, "classShort": "9c", "subjectShort": "Ma", "missing": 1}]
warnings: []
```

Erwartet und erfüllt: je 201, alle `pinned: true`, `teacherShort` folgt aus der Fachzuordnung (E-18 — im
Rumpf steht keine Lehrkraft), `conflicts: []`, `openLessons` = genau `[{9c, Ma, missing 1}]`.

### P-2 / T-W3.1 — 9c Mathematik Montag 1. Stunde in R101

```
curl -s -X POST $B/api/draft/lessons -H "$A" -H 'Content-Type: application/json' \
     -d '{"classId":3,"subjectId":1,"timeslotId":1,"roomId":1}'
-> HTTP 201

neue Stunde: {"id": 8, "classShort": "9c", "subjectShort": "Ma", "teacherShort": "SCZ",
              "roomShort": "R101", "pinned": true}
conflicts: [{"type": "ROOM_DOUBLE_BOOKED", "severity": "HARD",
             "message": "Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).",
             "lessonIds": [1, 8], "ruleId": null,
             "objects": [{"kind": "ROOM", "id": 1, "label": "R101"}]}]
```

Erwartet und erfüllt: genau **ein** Konflikt, `type ROOM_DOUBLE_BOOKED`, `objects[0].label "R101"`,
`lessonIds` = die 7a-Stunde **und** die 9c-Stunde, Text nennt R101/Montag/1. Stunde, **kein**
`TEACHER_DOUBLE_BOOKED`, die Stunde trägt `SCZ`.

### P-3 / T-W3.2 — dieselbe Stunde auf Montag 5. Stunde (Position 6)

```
curl -s -X PATCH $B/api/draft/lessons/8 -H "$A" -H 'Content-Type: application/json' -d '{"timeslotId":6}'
-> HTTP 200

9c-Stunde: {"timeslotId": 6, "position": 6, "subjectShort": "Ma", "teacherShort": "SCZ",
            "roomShort": "R101", "pinned": true}
conflicts: []
```

Die Markierung ist an **beiden** Stunden weg; Fach, Lehrkraft und Raum sind unverändert.

### P-4 / T-W1.2 — 9c Mathematik Freitag 5. Stunde (Sperrzeit von Schulz)

```
curl -s -X POST $B/api/draft/lessons -H "$A" -H 'Content-Type: application/json' \
     -d '{"classId":3,"subjectId":1,"timeslotId":34,"roomId":1}'
-> HTTP 201

conflicts: [{"type": "TEACHER_BLOCKED_TIME", "severity": "HARD",
             "message": "Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde",
             "lessonIds": [9], "ruleId": null,
             "objects": [{"kind": "TEACHER", "id": 3, "label": "SCZ"}]}]
```

### P-5 und P-5b — beide Zusatzstunden löschen

```
curl -s -X DELETE $B/api/draft/lessons/9 -H "$A"   -> HTTP 200, conflicts: []
curl -s -X DELETE $B/api/draft/lessons/8 -H "$A"   -> HTTP 200, lessons: 7
```

### P-6 — Entwurf annehmen

```
curl -s -X POST $B/api/draft/accept -H "$A" -H 'Content-Type: application/json' -d '{}'
-> HTTP 200
id: 1  acceptedAt: 2026-09-05T00:38:58.146097259Z  lessons: 7  conflicts: []  warnings: []

curl -s -X GET $B/api/draft -H "$A"       -> HTTP 404 (urn:pushnpull:problem:not-found)
curl -s -X GET $B/api/timetable -H "$A"   -> HTTP 200, dieselben sieben Stunden
```

### D-4 — Stunde in ein belegtes Zeitfenster verschieben

```
curl -s -X POST $B/api/draft -H "$A" -H 'Content-Type: application/json' -d '{"source":"COPY_ACTIVE"}'
-> HTTP 201, sieben Stunden, alle pinned (die Kopie eines konfliktfreien Plans legt alles fest, W-1)

curl -s -X PATCH $B/api/draft/lessons/10 -H "$A" -H 'Content-Type: application/json' -d '{"timeslotId":2}'
-> HTTP 200
conflicts:
  ROOM_DOUBLE_BOOKED     Raum R101 ist am Montag in der 2. Stunde doppelt belegt (7a).
  TEACHER_DOUBLE_BOOKED  Lehrkraft Meyer ist am Montag in der 2. Stunde doppelt belegt (7a, 8b).
  CLASS_DOUBLE_BOOKED    Klasse 7a ist am Montag in der 2. Stunde doppelt belegt (Ma, De).
```

### D-5 — Änderung zurücknehmen

```
curl -s -X DELETE $B/api/draft -H "$A"    -> HTTP 204
curl -s -X GET $B/api/timetable -H "$A"   -> unverändert (gleiche Zeitfenster wie nach P-6)
```

### S-1 — Aktiven Plan leeren, Grunddaten bleiben

```
POST /api/draft {"source":"EMPTY"}  -> 201
POST /api/draft/accept {}           -> 200
GET  /api/timetable                 -> lessons: []
GET  /api/classes                   -> 3 Klassen
```

### S-2 / T-W8.1 — Vorschlag erzeugen (Rechenzeit 5 s aus `GET /api/config`)

```
curl -s -X PUT $B/api/config -H "$A" -H 'Content-Type: application/json' \
     -d '{"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":5}'
-> HTTP 200

curl -s -X POST $B/api/draft/solve -H "$A" -H 'Content-Type: application/json' -d '{}'
-> HTTP 202

# alle 2 s GET /api/draft, bis status != SOLVING
Laufzeit bis status != SOLVING: 6.4 s   (Grenze: maxSolveSeconds 5 s + 5 s)
solve: {"startedAt": "2026-09-05T00:38:58.512734Z", "limitSeconds": 5,
        "lastScore": "0hard/0medium/0soft", "error": null}
lessons: 8   openLessons: []   Konfliktarten: []
jede Stunde belegt oder offen: True
keine ROOM_/TEACHER_/CLASS_DOUBLE_BOOKED: True
```

`syncRequiredLessons` hat aus dem leeren Entwurf genau acht Stunden gemacht (7a 2+1+1, 8b 2+1, 9c 1).

### S-3 und S-4 — ansehen, dann annehmen

```
GET  /api/timetable            -> lessons: []      (S-3: der Vorschlag ist noch keiner)
POST /api/draft/accept {}      -> HTTP 200
GET  /api/timetable            -> genau die acht Stunden des Entwurfs, je Stunde gleiches
                                  timeslotId und roomId
```

### T-W9.1 — festgelegte Stunde überlebt die Neuberechnung

```
POST /api/draft {"source":"COPY_ACTIVE"}                   -> 201
(alle Festlegungen der Kopie mit PATCH {"pinned":false} aufheben)
curl -s -X PATCH $B/api/draft/lessons/25 -H "$A" -d '{"timeslotId":7}'
-> HTTP 200, Stunde 25: timeslotId=7 roomId=1 pinned=True

curl -s -X POST $B/api/draft/solve -H "$A" -d '{}'         -> HTTP 202
nach dem Lauf: timeslotId=7 roomId=1 pinned=True           (unverändert)
Konfliktarten: []

curl -s -X PATCH $B/api/draft/lessons/25 -H "$A" -d '{"pinned":false}'
-> HTTP 200, pinned=False
```

Schritt 3 der Checkliste ist damit belegt: **Verschieben legt fest**, ohne dass `pinned` im Rumpf steht.

### 409 `solving` — während eines Laufs wird nichts geändert

```
POST /api/draft/solve {}                     -> 202
POST /api/draft/lessons {...}                -> 409
    type:   urn:pushnpull:problem:solving
    detail: Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen.
POST /api/draft/accept {}                    -> 409
DELETE /api/draft                            -> 409
GET  /api/draft                              -> status SOLVING
DELETE /api/draft/solve                      -> 202
danach: status EDITING, lastScore 0hard/0medium/0soft   (das bis dahin beste Ergebnis ist übernommen)
```

### 409 `pinned-contradiction`

```
POST /api/draft/lessons  (9c auf Zeitfenster und Raum einer festgelegten 7a-Stunde)  -> 201
POST /api/draft/solve {}                                                             -> 409
    type:   urn:pushnpull:problem:pinned-contradiction
    detail: Festgelegte Stunden stehen im Konflikt. Bitte auflösen oder Festlegung aufheben.
    conflicts: [{"type":"ROOM_DOUBLE_BOOKED","severity":"HARD",
                 "message":"Raum R101 ist am Montag in der 6. Stunde doppelt belegt (7a, 9c).",
                 "lessonIds":[25,33],"ruleId":null,
                 "objects":[{"kind":"ROOM","id":1,"label":"R101"}]}]
```

### 409 `master-data-incomplete`

```
POST /api/classes {"name":"10d","grade":10,"active":true}   -> 201 (Klasse ohne Fächerliste)
POST /api/draft/solve {}                                    -> 409
    type:   urn:pushnpull:problem:master-data-incomplete
    detail: Vor der Berechnung fehlen Angaben.
    missing: ["Klasse 10d hat kein Fach"]
```

### Zugriffsschutz

```
curl -s -o /dev/null -w '%{http_code}' $B/api/timetable   -> 401
curl -s -o /dev/null -w '%{http_code}' $B/api/draft       -> 401
```

**Ergebnis des Durchlaufs: 0 Abweichungen bei 42 geprüften Zusicherungen.**

Der Durchlauf ist wiederholbar: das Skript liegt als `scripts/abnahme-planning.py` im Repo (es legt die
Grunddaten selbst an und braucht deshalb eine **leere** Datenbank und das Konto `admin`/`admin`).

```
ABNAHME_BASE=http://127.0.0.1:18091 python3 scripts/abnahme-planning.py
…
### Ergebnis
  Abweichungen: 0
$ echo $?
0
```

## 3. Automatische Tests

```
docker run --rm=true --network plan-b2-net -v "$PWD/backend":/w -w /w \
  -v plan-b2-gradle:/home/gradle/.gradle -e SPRING_PROFILES_ACTIVE=ci \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://plan-b2-db:5432/timetable \
  -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt -e ADMIN_INITIAL_PASSWORD=admin \
  gradle:9.7.1-jdk21 gradle --no-daemon test --rerun integrationTest --rerun

BUILD SUCCESSFUL in 1m 9s
```

| Aufgabe | Tests | Fehler |
|---|---|---|
| `test` (Ebene 1+2, alle Module) | 100 | 0 |
| `integrationTest` (Ebene 3+4, alle Module) | 110 | 0 |

Davon aus dem Modul `planning`:

| Testklasse | Tests | Laufzeit | Deckt ab |
|---|---|---|---|
| `ConflictCheckerTest` (Ebene 1, ohne Spring) | 11 | 0,1 s | T-W1.2, T-W2.1, T-W3.1, T-W3.2, Klassen-/Lehrkraft-Doppelbelegung, Raumart, Pausenregel, Wochenregel, offene Stunden |
| `DraftControllerIT` | 9 | 1,9 s | P-1 bis P-6, D-5, 409 `draft-exists`, 400 mit Feldliste, „belegt oder offen" (D-9) |
| `DraftServiceIT` | 6 | 1,2 s | `COPY_ACTIVE` mit Konflikt, Archivieren, Verlaufseintrag, `validFrom`, verfallende Vertretungen, überzählige Stunden |
| `SolveServiceIT` | 9 | 10,8 s | T-W8.1, T-W9.1, `master-data-incomplete`, `pinned-contradiction`, 409 `solving`, Abbruch, Lauf ohne Rumpf |

`ArchitectureTest` (fünf Regeln, inkl. Modultabelle mit `planning → masterdata, rules, config, solver,
changelog, shared`) ist Teil der 100 grünen `test`-Fälle.

## 4. Ungeprüft

- **`changelog`**: Der Verlaufseintrag beim Annehmen entsteht über `JdbcTemplate` direkt in
  `changelog_entry` (V4), weil das Modul `changelog` beim Bau von `planning` noch nicht existierte.
  Geprüft ist die geschriebene Zeile (`DraftServiceIT`: `action TIMETABLE_ACCEPTED`,
  `old_assignment "kein Plan"` bzw. `"Plan vom …"`, `new_assignment "Plan angenommen (7 Stunden)"`,
  `date` = `validFrom`). **Nicht** geprüft ist, ob `GET /api/changelog` diese Zeile so ausliefert, wie
  V-6 der Checkliste es erwartet — das entscheidet sich erst mit dem Modul `changelog`.
- **`substitution`**: `PlanReader.plannedAt(LocalDate)` (Plan mit `validFrom` an einem Datum) hat noch
  keinen Aufrufer. Gespeichert und gelesen wird `validFrom` (belegt in `DraftServiceIT`); die Auswahl
  „aktiver Plan oder letzter archivierter" ist **nicht** gemessen.
- **`sampledata`**: `SolveService.solveBlocking(int)` ist der im VERTRAG zugesagte Einstieg für den
  gefüllten Beispielplan (E-46) und hat heute keinen Aufrufer. Nicht gemessen.
- Der volle Beispielbestand (12/24/18) ist im Modul `planning` nicht durchgerechnet worden; T-W8.1
  ist hier mit dem Testdatenbestand des Pflichtenhefts (acht Stunden) belegt. Der große Fall gehört zu
  `sampledata` / `solver` (dort `FullDatasetSolveIT`).
- Kein Browser, kein Frontend: alle Belege sind HTTP-Antworten.
