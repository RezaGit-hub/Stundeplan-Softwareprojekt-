# Abnahme backend3 — Modul `substitution` Teil 2

Stand 05.09.2026. Bauphase **backend3** (`docs/VERTRAG.md` Abschnitt 5): Ereignisse, offene Stunden,
Vorschläge, datierte Ausnahmen, Ferien. Belegt sind die Checklistenzeilen **V-1 bis V-6, A-1 bis A-6,
P-7** und die Testfälle **T-W4.1, T-W5.1, T-W6.1, T-W7.1, T-A1.1, T-A2.1, T-110** — dazu Verlegung,
Entfall, Zusatzstunde und die zweite Ausnahme am selben Tag (409 `exception-exists`).

**Ergebnis: 62 Zusicherungen, 0 Abweichungen.** Wiederholbar mit `scripts/abnahme.sh`.

> **Nachtrag 05.09. (später am Tag):** Zwei Prüfungen haben den Stand durchgesehen; die Befunde sind
> eingearbeitet und das Skript um 21 Zusicherungen erweitert. Der wiederholte Lauf steht am Ende
> dieser Datei unter „Nacharbeit 05.09." — **83 Zusicherungen, 0 Abweichungen**, dazu
> 129 Unit- und 171 Integrationstests ohne Fehler.

## 1. Wie der Lauf entstand

Ein eigener Stack neben dem laufenden (Projektname `stundenplan-b3`, eigenes Datenbankvolumen, Backend
auf Port 8093), damit der Durchlauf niemandem die Datenbank wegnimmt und trotzdem auf einer **frischen**
Datenbank beginnt:

```
$ cd deployment && BACKEND_PORT=8093 DB_PORT=15433 \
    docker compose -p stundenplan-b3 -f docker-compose.yml -f docker-compose.dev.yml up -d --build backend
 Container stundenplan-b3-db-1  Healthy
 Container stundenplan-b3-backend-1  Started
$ curl -s http://127.0.0.1:8093/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}          # health 200 nach 7 s

$ ABNAHME_BASE=http://127.0.0.1:8093 ABNAHME_ADMIN_PW=<Kennwort aus deployment/.env> scripts/abnahme.sh
```

Auf dem regulären Stack lautet der Aufruf ohne Umgebungsvariablen `scripts/abnahme.sh --frisch`
(startet den Stack mit `down -v` neu). `--frisch` ist die einzige Stelle, an der das Skript Container
anfasst; ohne die Option prüft es nur, ob die Datenbank leer ist, und bricht sonst mit einem Hinweis ab.

Die Kalenderwoche ist die der Checkliste: Montag **21.09.2026**. Der Plan wird mit
`{"validFrom":"2026-09-21"}` angenommen, damit er an diesem Tag gilt (E-40). Die Kennungen entstehen in
der Reihenfolge der Vertragsbeispiele — Fächer Ma 1, De 2, Ph 3 · Räume R101 1, R204 2, P1 3 · Klassen
7a 1, 8b 2, 9c 3 · Lehrkräfte **Meyer 1, Weber 2, Schulz 3** (der Browser-Durchlauf des Pflichtenhefts
legt Weber vor Schulz an, `scripts/abnahme-planning.py` genauso).

## 2. Gradle im Container

```
$ docker run --rm=true --network stundenplan-sub -v "$PWD/backend":/w -w /w \
    -v stundenplan-gradle:/home/gradle/.gradle -e SPRING_PROFILES_ACTIVE=ci \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://stundenplan-sub-db:5432/timetable" \
    -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt -e ADMIN_INITIAL_PASSWORD=admin \
    gradle:9.7.1-jdk21 gradle --no-daemon test --rerun integrationTest --rerun
BUILD SUCCESSFUL in 2m 21s
```

| Aufgabe | Tests | Fehler | Fehlschläge |
|---|---|---|---|
| `test` (Ebene 1+2) | 125 | 0 | 0 |
| `integrationTest` (Ebene 3+4, PostgreSQL) | 157 | 0 | 0 |

Davon neu in diesem Modul: `WeekResolverTest` (9), `SubstitutionOptionsTest` (4),
`SubstitutionServiceIT` (10), `EventControllerIT` (6), `HolidayIT` (3) — dazu die schon vorhandenen
`WeekIT` (8) und `MyWeekIT` (4). `ArchitectureTest` bleibt mit fünf Regeln grün.

## 3. Protokoll des Durchlaufs

Letzter Lauf: frisch gebautes Abbild, leere Datenbank (`docker compose -p stundenplan-b3 … down -v` davor).

```
==> Auf den Server warten (http://127.0.0.1:8093/api/health)
==> Durchlauf gegen http://127.0.0.1:8093

### L-1  Anmeldung als Planung
$ curl -s -X POST $B/api/auth/login -d '{"username": "admin", "password": "<Kennwort aus deployment/.env>"}'
  -> HTTP 200
  [ok] Login als Planung, Token erhalten

### Vorbedingung: Grunddaten des Pflichtenhefts (Checkliste G-1 bis G-7)
  Grunddaten stehen: {"Ma": 1, "De": 2, "Ph": 3, "R101": 1, "R204": 2, "P1": 3, "MEY": 1, "WEB": 2, "SCZ": 3, "7a": 1, "8b": 2, "9c": 3}

### P-1 / P-6  Regelplan Montag/Dienstag eintragen und annehmen (gueltig ab 2026-09-21)
$ curl -s -X POST $B/api/draft/accept -H "$A" -d '{"validFrom": "2026-09-21"}'
  -> HTTP 200
  [ok] 200, sieben Stunden, keine Konflikte
  Stunde 7a Mo 1. = 1 | 8b Mo 5. = 6

### P-7 / T-W4.1  vier Sichten auf dieselbe Woche
$ curl -s -X GET $B/api/timetable/week?date=2026-09-21&view=CLASS&id=1 -H "$A"
  -> HTTP 200
  Montag 7a: [[1, "Ma", "MEY", "R101", "REGULAR"], [2, "De", "WEB", "R101", "REGULAR"], [4, "Ph", "MEY", "P1", "REGULAR"]]
  [ok] Woche beginnt am Montag und hat fuenf Tage
  [ok] 7a Mo 1. Stunde: Ma / MEY / R101, kind REGULAR
  Montag Meyer: [1, 2, 4, 5] | Dienstag: [1]
  [ok] Lehrkraftsicht Meyer: Montag vier Eintraege (1, 2, 4, 5), Dienstag einer
  Montag Schule: [('7a', 1), ('7a', 2), ('8b', 2), ('7a', 4), ('8b', 4), ('8b', 5)]
  [ok] Schulsicht: Montag sechs Eintraege, in der 1. Stunde genau einer
$ curl -s -X GET $B/api/timetable/week?date=2026-09-21&view=ROOM&id=1 -H "$A"
  -> HTTP 200
  Montag R101: [1, 2]
  [ok] Raumsicht R101 (E-39): Montag Position 1 und 2 belegt, ab der 4. nichts
  [ok] alle Eintraege REGULAR
  [ok] Sicht ohne Kennung -> 400 mit Feld id; unbekannte Kennung -> 404

### A-1 bis A-3 / T-A1.1  Konto der Lehrkraft, eigene Woche, Zugriffsschutz (ohne Ereignisse)
$ curl -s -X POST $B/api/users -H "$A" -d '{"username": "meyer", "password": "geheim-2026", "role": "TEACHER", "teacherId": 1}'
  -> HTTP 201
  [ok] A-1: Konto meyer angelegt (201)
$ curl -s -X GET $B/api/me/timetable/week?date=2026-09-21 -H "$A"
  -> HTTP 200
  Montag: [[1, "7a", "Ma", "R101", "REGULAR"], [2, "8b", "Ma", "R204", "REGULAR"], [4, "7a", "Ph", "P1", "REGULAR"], [5, "8b", "Ma", "R204", "REGULAR"]]
  [ok] T-A1.1: Montag vier REGULAR-Eintraege auf 1, 2, 4, 5
  [ok] T-A1.1: die Weber-Stunde (7a, 2. Stunde) fehlt
  [ok] A-3: genau fuenf Eintraege in der Woche
  [ok] GET /api/timetable mit Lehrkraft-Token -> 403, ohne Token -> 401

### V-1 / T-W5.1  Ausfall Meyer am 2026-09-21, Anlass „Krankmeldung“
$ curl -s -X POST $B/api/events -H "$A" -d '{"kind": "TEACHER_ABSENCE", "teacherId": 1, "dateFrom": "2026-09-21", "dateTo": "2026-09-21", "reason": "Krankmeldung"}'
  -> HTTP 201
  affected: [{"date": "2026-09-21", "lessonId": 1, "classShort": "7a", "position": 1}, {"date": "2026-09-21", "lessonId": 4, "classShort": "8b", "position": 2}, {"date": "2026-09-21", "lessonId": 3, "classShort": "7a", "position": 4}, {"date": "2026-09-21", "lessonId": 6, "classShort": "8b", "position": 5}]
  hint: None
  [ok] 201 und genau vier betroffene Stunden (7a 1, 8b 2, 7a 4, 8b 5)
  [ok] hint ist leer, weil Stunden betroffen sind
$ curl -s -X GET $B/api/timetable/week?date=2026-09-21&view=SCHOOL -H "$A"
  -> HTTP 200
  Montag NEEDS_SUBSTITUTION: [('7a', 1), ('8b', 2), ('7a', 4), ('8b', 5)]
  [ok] T-W5.1: am 2026-09-21 genau vier NEEDS_SUBSTITUTION
  [ok] T-W5.1: die Weber-Stunde (7a, 2. Stunde) bleibt REGULAR
  [ok] T-W5.1: am 2026-09-22 kein Vertretungsbedarf
  [ok] T-W5.1: der Regelplan ist unveraendert

### V-2  Sperrung R101 am 2026-09-21, Anlass „Wasserschaden“
$ curl -s -X POST $B/api/events -H "$A" -d '{"kind": "ROOM_BLOCKED", "roomId": 1, "dateFrom": "2026-09-21", "dateTo": "2026-09-21", "reason": "Wasserschaden"}'
  -> HTTP 201
  affected: [{"date": "2026-09-21", "lessonId": 1, "classShort": "7a", "position": 1}, {"date": "2026-09-21", "lessonId": 2, "classShort": "7a", "position": 2}]
  [ok] 201 und zwei betroffene Stunden (7a 1, 7a 2)
  [ok] jetzt fuenf NEEDS_SUBSTITUTION
  [ok] 7a 1. Stunde nennt beide Anlaesse
  [ok] 7a 2. Stunde nennt nur den Wasserschaden

### V-3  offene Stunden und Vorschlaege fuer die 1. Stunde der 7a
$ curl -s -X GET $B/api/substitutions/pending?from=2026-09-21&to=2026-09-25 -H "$A"
  -> HTTP 200
  offene Stunden: [["2026-09-21", "7a", 1, "MEY", "R101", ["Krankmeldung", "Wasserschaden"]], ["2026-09-21", "7a", 2, "WEB", "R101", ["Wasserschaden"]], ["2026-09-21", "8b", 2, "MEY", "R204", ["Krankmeldung"]], ["2026-09-21", "7a", 4, "MEY", "P1", ["Krankmeldung"]], ["2026-09-21", "8b", 5, "MEY", "R204", ["Krankmeldung"]]]
  [ok] 200 und fuenf Zeilen, die erste ist 7a Position 1
  [ok] die erste Zeile nennt beide Anlaesse und beide Ereignisse
$ curl -s -X GET $B/api/substitutions/options?date=2026-09-21&lessonId=1 -H "$A"
  -> HTTP 200
  teachers: [{"id": 3, "shortName": "SCZ", "name": "Schulz", "teachesSubject": true}, {"id": 2, "shortName": "WEB", "name": "Weber", "teachesSubject": false}]
  rooms: [{"id": 3, "name": "P1", "roomType": "Fachraum Physik", "matchesRequirement": true}, {"id": 2, "name": "R204", "roomType": "Klassenraum", "matchesRequirement": true}]
  [ok] Schulz steht als fachgleiche Lehrkraft an erster Stelle
  [ok] Meyer fehlt (ausgefallen)
  [ok] R204 ist dabei, R101 nicht (gesperrt)

### V-4 / T-W6.1  Schulz und R204 zuweisen
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION", "substituteTeacherId": 3, "substituteRoomId": 2, "eventIds": [1, 2]}'
  -> HTTP 201
  Antwort: {"id": 1, "date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION", "substituteTeacherId": 3, "substituteRoomId": 2, "newDate": null, "newTimeslotId": null, "eventIds": [1, 2], "conflicts": []}
  [ok] 201 und conflicts leer
  7a 1. Stunde: {"date": "2026-09-21", "timeslotId": 1, "day": "MO", "position": 1, "lessonId": 1, "classId": 1, "classShort": "7a", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 3, "teacherShort": "SCZ", "roomId": 2, "roomShort": "R204", "kind": "SUBSTITUTED", "original": {"teacherId": 1, "teacherShort": "MEY", "roomId": 1, "roomShort": "R101", "date": null, "position": null}, "reasons": ["Krankmeldung", "Wasserschaden"], "exceptionId": 1}
  [ok] T-W6.1: kind SUBSTITUTED, SCZ in R204, original MEY / R101
  [ok] pending hat jetzt vier Zeilen ohne diese
  [ok] T-W6.1: der Regelplan kennt weiter Meyer in R101

### V-5  Plansicht am 2026-09-28
$ curl -s -X GET $B/api/timetable/week?date=2026-09-28&view=CLASS&id=1 -H "$A"
  -> HTTP 200
  7a Mo 1. Stunde: {"date": "2026-09-28", "timeslotId": 1, "day": "MO", "position": 1, "lessonId": 1, "classId": 1, "classShort": "7a", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 1, "teacherShort": "MEY", "roomId": 1, "roomShort": "R101", "kind": "REGULAR", "original": null, "reasons": [], "exceptionId": null}
  [ok] eine Woche spaeter steht wieder REGULAR / MEY / R101

### V-6 / T-W7.1  Aenderungsverlauf, Filter 7a
$ curl -s -X GET $B/api/changelog?classId=1 -H "$A"
  -> HTTP 200
  Eintrag: {"id": 2, "timestamp": "2026-09-05T02:41:19.755377Z", "action": "EXCEPTION_CREATED", "date": "2026-09-21", "position": 1, "classShort": "7a", "oldAssignment": "Meyer / R101", "newAssignment": "Schulz / R204", "reasons": "Krankmeldung; Wasserschaden", "username": "admin"}
  [ok] genau ein Eintrag
  [ok] action EXCEPTION_CREATED, Datum, Position, Klasse
  [ok] alte und neue Belegung als Text
  [ok] beide Anlaesse und der Benutzername
  [ok] Zeitstempel gesetzt
  [ok] T-W7.1: DELETE /api/changelog/{id} findet keine Route (404)

### A-2 (nach dem Leitszenario)  Meyer sieht die abgegebene Stunde
$ curl -s -X GET $B/api/me/timetable/week?date=2026-09-21 -H "$A"
  -> HTTP 200
  Montag: [[1, "GIVEN_AWAY", "SCZ", "MEY"], [2, "NEEDS_SUBSTITUTION", "MEY", null], [4, "NEEDS_SUBSTITUTION", "MEY", null], [5, "NEEDS_SUBSTITUTION", "MEY", null]]
  [ok] Montag vier Eintraege auf 1, 2, 4, 5
  [ok] Position 1 ist GIVEN_AWAY: 7a, Ma, SCZ, original MEY
  [ok] Positionen 2, 4 und 5 sind NEEDS_SUBSTITUTION

### A-4 / A-5 / T-A2.1  Schulz sieht die uebernommene Stunde
  [ok] Konto schulz angelegt (201)
$ curl -s -X GET $B/api/me/timetable/week?date=2026-09-21 -H "$A"
  -> HTTP 200
  Montag Schulz: {"date": "2026-09-21", "timeslotId": 1, "day": "MO", "position": 1, "lessonId": 1, "classId": 1, "classShort": "7a", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 3, "teacherShort": "SCZ", "roomId": 2, "roomShort": "R204", "kind": "SUBSTITUTED", "original": {"teacherId": 1, "teacherShort": "MEY", "roomId": 1, "roomShort": "R101", "date": null, "position": null}, "reasons": ["Krankmeldung", "Wasserschaden"], "exceptionId": 1}
  [ok] A-4: genau ein Eintrag, kind SUBSTITUTED, 7a in R204
  [ok] A-5: beide Anlaesse und die vertretene Lehrkraft
  [ok] A-4: GET /api/timegrid mit Lehrkraft-Token -> 200, Zeitfenster 1 = 08:00–08:45
  [ok] T-A2.1: am 2026-09-28 sieht Schulz nichts und Meyer nur REGULAR

### A-6  keine Planungsfunktion mit der Rolle Lehrkraft
$ curl -s -X POST $B/api/draft/lessons -H "$A" -d '{"classId": 3, "subjectId": 1, "timeslotId": 15, "roomId": 2}'
  -> HTTP 403
  [ok] POST /api/draft/lessons mit Lehrkraft-Token -> 403

### Zweite Ausnahme am selben Tag und Aufheben
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION", "substituteTeacherId": 2}'
  -> HTTP 409
  Antwort: {"detail": "Für diese Stunde gibt es am 21.09.2026 schon eine Ausnahme. Erst aufheben, dann neu anlegen.", "instance": "/api/exceptions", "status": 409, "title": "Konflikt", "type": "urn:pushnpull:problem:exception-exists"}
  [ok] 409 exception-exists
$ curl -s -X DELETE $B/api/exceptions/1 -H "$A"
  -> HTTP 204
  [ok] 204 und die Stunde ist wieder NEEDS_SUBSTITUTION bei MEY
  [ok] der Verlauf haelt EXCEPTION_DELETED fest (jetzt zwei Eintraege)

### Verlegung: 8b Montag 5. Stunde auf Mittwoch 1. Stunde
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 6, "kind": "MOVE", "newDate": "2026-09-23", "newTimeslotId": 15}'
  -> HTTP 201
  Montag 8b 5. Stunde: {"date": "2026-09-21", "timeslotId": 5, "day": "MO", "position": 5, "lessonId": 6, "classId": 2, "classShort": "8b", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 1, "teacherShort": "MEY", "roomId": 2, "roomShort": "R204", "kind": "CANCELLED", "original": null, "reasons": ["verlegt → Mi 1.", "Krankmeldung"], "exceptionId": 2}
  Mittwoch: [{"date": "2026-09-23", "timeslotId": 15, "day": "WE", "position": 1, "lessonId": 6, "classId": 2, "classShort": "8b", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 1, "teacherShort": "MEY", "roomId": 2, "roomShort": "R204", "kind": "MOVED", "original": {"teacherId": null, "teacherShort": null, "roomId": null, "roomShort": null, "date": "2026-09-21", "position": 5}, "reasons": ["Krankmeldung"], "exceptionId": 2}]
  [ok] 201; am Ursprungstag CANCELLED mit „verlegt → Mi 1.“
  [ok] am Zieltag ein Eintrag MOVED auf Position 1 mit original vom 2026-09-21
  [ok] der Verlauf nennt das Ziel als „Mi, 1. Stunde“

### Zusatzstunde: 9c Mathematik am 2026-09-24
$ curl -s -X POST $B/api/events -H "$A" -d '{"kind": "EXTRA_LESSON", "dateFrom": "2026-09-24", "dateTo": "2026-09-24", "reason": "Nachholstunde", "extra": {"date": "2026-09-24", "timeslotId": 22, "classId": 3, "subjectId": 1, "teacherId": 3, "roomId": 2}}'
  -> HTTP 201
  hint: Zusatzstunde für 9c am 24.09.2026 angelegt.
  [ok] 201 mit Hinweis auf die Zusatzstunde
  Donnerstag: [{"date": "2026-09-24", "timeslotId": 22, "day": "TH", "position": 1, "lessonId": null, "classId": 3, "classShort": "9c", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 3, "teacherShort": "SCZ", "roomId": 2, "roomShort": "R204", "kind": "ADDED", "original": null, "reasons": ["Nachholstunde"], "exceptionId": 3}]
  [ok] am 2026-09-24 steht ein Eintrag ADDED ohne lessonId
$ curl -s -X DELETE $B/api/events/3 -H "$A"
  -> HTTP 204
  [ok] 204: das Ereignis nimmt die Zusatzstunde mit

### T-110  Ferien und Feiertage (Kann, E-13)
$ curl -s -X POST $B/api/holidays -H "$A" -d '{"name": "Tag der Deutschen Einheit", "dateFrom": "2026-09-22", "dateTo": "2026-09-22", "kind": "PUBLIC_HOLIDAY"}'
  -> HTTP 201
  Tage: [["2026-09-21", true, 6], ["2026-09-22", false, 0], ["2026-09-23", true, 0], ["2026-09-24", true, 0], ["2026-09-25", true, 0]]
  [ok] 201; am Feiertag schoolDay false und keine Eintraege
  [ok] der Montag bleibt Unterrichtstag
  [ok] am Feiertag entsteht kein Vertretungsbedarf
$ curl -s -X DELETE $B/api/holidays/1 -H "$A"
  -> HTTP 204
  [ok] 204 und der Dienstag ist wieder Unterrichtstag

==============================================================================
Alle Zusicherungen halten.
```

---

## Nacharbeit 05.09. — Prüfbefunde der Zweitprüfung eingearbeitet

Nach dem Lauf oben haben zwei Prüfungen (Claude, Codex) den Stand durchgesehen. Die Befunde sind
eingearbeitet; dieser Abschnitt belegt sie. **Ergebnis des wiederholten Laufs: 83 Zusicherungen,
0 Abweichungen** (vorher 62 — die 21 neuen prüfen genau die gemeldeten Fälle). Der Lauf entstand
auf einem eigenen Stack mit frischer Datenbank:

```
$ cd deployment && BACKEND_PORT=8093 DB_PORT=15453 \
    docker compose -p backend3-nacharbeit -f docker-compose.yml -f docker-compose.dev.yml \
    up -d --build db backend
 Container backend3-nacharbeit-db-1  Healthy
 Container backend3-nacharbeit-backend-1  Started
$ curl -s http://127.0.0.1:8093/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}

$ ABNAHME_BASE=http://127.0.0.1:8093 python3 scripts/abnahme-substitution.py
...
Alle Zusicherungen halten.                       # 83 [ok], 0 ABWEICHUNG
```

### Was geändert wurde

| Befund | Änderung | Beleg |
|---|---|---|
| Ereignisse wirkten nur auf den Regelplan, nie auf die Vertretung: fiel die Vertretungslehrkraft am selben Tag ebenfalls aus, blieb die Stunde `SUBSTITUTED` | `WeekResolver.resolveDay` hat einen **fünften Schritt**: dieselben Ereignisse noch einmal gegen die aufgelöste Belegung. Zusätzlich lehnt `POST /api/exceptions` eine abwesende Vertretungslehrkraft und einen gesperrten Vertretungsraum mit 400 ab | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| Unbekannte Kennungen liefen in den Fremdschlüssel: 409 `in-use` mit dem Text „wird noch verwendet“ | `ExceptionService.validate` prüft `substituteTeacherId`, `substituteRoomId`, `newTimeslotId` und `eventIds` vorher -> 400 mit dem Feldnamen | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| Eine Ausnahme wurde für Tage angenommen, an denen die Stunde nicht liegt (falscher Wochentag, Ferientag) | `findLesson` verlangt denselben Wochentag **und** einen Unterrichtstag; `GET /api/substitutions/options` prüft dasselbe | Abschnitte „Prüfungen“ und „T-110“ |
| `MOVE` nahm ein Zielzeitfenster eines anderen Wochentags an | `validate` vergleicht den Wochentag des Zeitfensters mit `newDate` -> 400 `newTimeslotId` | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| Eine Vertretung, die nichts ändert, wurde angenommen | 400 `substituteTeacherId` „Lehrkraft oder Raum muss sich ändern“ | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| `DELETE /api/exceptions/{id}` löschte auch die Zusatzstunde und ließ ihr Ereignis verwaist zurück | 400 mit Verweis auf `DELETE /api/events/{id}` | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| Beim Aufheben einer Verlegung war der Verlaufseintrag inhaltsleer („Meyer / R204 -> Meyer / R204“) | `oldAssignment` je Art gebildet wie in `newAssignmentText` | Abschnitt „Verlegung aufheben“ |
| `GET /api/events` mit verdrehtem Bereich lieferte 200 mit leerer Liste | 400 `to` wie an `/api/substitutions/pending` (SU-16) | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| Ein fehlender Pflichtparameter ergab 500 | eigener Handler für `MissingServletRequestParameterException` -> 400 mit dem Feldnamen | Abschnitt „Prüfungen beim Anlegen und beim Lesen“ |
| Zusatzstunden fielen aus der Folgekonfliktprüfung heraus | sie gehen mit ein (künstliche negative Kennung, aus der Antwort wieder entfernt) | `SubstitutionServiceIT.aSubstitutionClashingWithAnExtraLessonIsReported` |
| Der Verlauf verlor ab fünf Anlässen ganze Anlässe (Spalte `varchar(1000)`) | Migration **V6**: `changelog_entry.reasons` ist `text`; die Kürzung ist weg | `ChangelogIT.allReasonsSurviveEvenWhenTheyAreLong` |
| Der Platzhalter aus der Phase `changelog` stand noch (`ChangelogIT` schrieb direkt, `abnahme-changelog.py` per `psql`) | beide gehen jetzt über `POST`/`DELETE /api/exceptions`; der `psql`-Zugriff ist aus dem Skript entfernt | `docs/abnahme/changelog.md` |

Nicht geändert: die Nebenläufigkeit zweier gleichzeitiger `POST /api/exceptions` — Begründung in
`docs/VERTRAG.md`, SU-22.

### Gradle im Container — beide Testaufgaben

```
$ scripts/ci-backend-test.sh
==> Wegwerf-Datenbank starten (postgres:16-alpine)
==> Tests im Container ausfuehren
> Task :test
> Task :integrationTest
BUILD SUCCESSFUL in 2m 24s
```

Aus den Berichten unter `backend/build/test-results` gezählt:

```
test: 129 Tests, 0 Fehler, 0 uebersprungen
integrationTest: 171 Tests, 0 Fehler, 0 uebersprungen
```

Vor der Nacharbeit waren es 125 und 157.

**Eine Beobachtung zum Mitschreiben:** ein Lauf von fünf fiel mit zwei Fehlern aus —
`SolveServiceIT.cancellingARunKeepsTheBestResultSoFar` („Die Berechnung war nach 22 Sekunden nicht
fertig") und danach `WeekIT.schoolViewShowsEverything` mit 409 `draft-exists` beim Anlegen des
Entwurfs. Beides hängt zusammen und hat mit dieser Nacharbeit nichts zu tun: alle Integrationstests
teilen eine Datenbank, und wenn der Solver-Faden über die Wartezeit hinausläuft, speichert er sein
Ergebnis erst, nachdem der nächste Test die Tabellen geleert hat — der Entwurf steht dann wieder da.
Der unmittelbar folgende Lauf war grün. Wer den Fehler wiedersieht: die Wartezeit in
`SolveServiceIT.waitForRunToFinish` ist die Stellschraube, nicht die Fachlogik.

### Verlegung aufheben — der Verlauf nennt das aufgehobene Ziel

```
$ curl -s -X DELETE $B/api/exceptions/2 -H "$A"
  -> HTTP 204
  Aufheben: {"id": 5, "timestamp": "2026-09-05T03:26:50.395853Z", "action": "EXCEPTION_DELETED", "date": "2026-09-21", "position": 5, "classShort": "8b", "oldAssignment": "Mi, 1. Stunde", "newAssignment": "Meyer / R204", "reasons": "Krankmeldung", "username": "admin"}
  [ok] beim Aufheben nennt oldAssignment das aufgehobene Ziel, nicht zweimal dieselbe Belegung
```

### Prüfungen beim Anlegen und beim Lesen

```
### Nacharbeit 05.09.  Pruefungen beim Anlegen und Aufheben (Befunde der Zweitpruefung)
  Stand: Ausfall Meyer und Sperrung R101 am 2026-09-21, keine Ausnahme offen.
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION", "substituteTeacherId": 1}'
  -> HTTP 400
  [ok] Vertretung, die nichts aendert -> 400 substituteTeacherId
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION", "substituteTeacherId": 3, "substituteRoomId": 1}'
  -> HTTP 400
  [ok] gesperrter Raum als Vertretungsraum -> 400 substituteRoomId
  unbekannte Kennung in substituteTeacherId  -> HTTP 400 (Lehrkraft fehlt)
  [ok] unbekannte Kennung substituteTeacherId -> 400 „Lehrkraft fehlt“ (nicht 409 in-use)
  unbekannte Kennung in substituteRoomId     -> HTTP 400 (Raum fehlt)
  [ok] unbekannte Kennung substituteRoomId -> 400 „Raum fehlt“ (nicht 409 in-use)
  unbekannte Kennung in newTimeslotId        -> HTTP 400 (Zeitfenster fehlt)
  [ok] unbekannte Kennung newTimeslotId -> 400 „Zeitfenster fehlt“ (nicht 409 in-use)
  unbekannte Kennung in eventIds             -> HTTP 400 (Ereignis fehlt)
  [ok] unbekannte Kennung eventIds -> 400 „Ereignis fehlt“ (nicht 409 in-use)
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-22", "lessonId": 1, "kind": "CANCEL"}'
  -> HTTP 404
  [ok] Montagsstunde am Dienstag -> 404, keine Zeile in der Datenbank
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 1, "kind": "MOVE", "newDate": "2026-09-23", "newTimeslotId": 8}'
  -> HTTP 400
  [ok] Verlegung auf Mittwoch mit einem Dienstags-Zeitfenster -> 400 newTimeslotId
$ curl -s -X GET $B/api/events?from=2026-09-25&to=2026-09-21 -H "$A"
  -> HTTP 400
  [ok] GET /api/events mit verdrehtem Bereich -> 400 (nicht 200 mit leerer Liste)
$ curl -s -X GET $B/api/substitutions/options?lessonId=1 -H "$A"
  -> HTTP 400
  [ok] GET /api/substitutions/options ohne date -> 400 mit Feld date (nicht 500)

### Nacharbeit 05.09.  Faellt die Vertretungslehrkraft ebenfalls aus, ist die Stunde wieder offen
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 1, "kind": "SUBSTITUTION", "substituteTeacherId": 3, "substituteRoomId": 2}'
  -> HTTP 201
  [ok] Vertretung Schulz/R204 erneut angelegt -> 201
$ curl -s -X POST $B/api/events -H "$A" -d '{"kind": "TEACHER_ABSENCE", "teacherId": 3, "dateFrom": "2026-09-21", "dateTo": "2026-09-21", "reason": "Fortbildung"}'
  -> HTTP 201
  affected: [{"date": "2026-09-21", "lessonId": 1, "classShort": "7a", "position": 1}]
  7a 1. Stunde: {"date": "2026-09-21", "timeslotId": 1, "day": "MO", "position": 1, "lessonId": 1, "classId": 1, "classShort": "7a", "subjectShort": "Ma", "subjectColor": "#1976D2", "teacherId": 3, "teacherShort": "SCZ", "roomId": 2, "roomShort": "R204", "kind": "NEEDS_SUBSTITUTION", "original": {"teacherId": 1, "teacherShort": "MEY", "roomId": 1, "roomShort": "R101", "date": null, "position": null}, "reasons": ["Krankmeldung", "Wasserschaden", "Fortbildung"], "exceptionId": 4}
  [ok] die Stunde ist wieder NEEDS_SUBSTITUTION und nennt beide Anlaesse
  [ok] die Ausnahme bleibt an der Zelle, damit die Planung sie aufheben kann
  [ok] sie steht wieder unter den offenen Stunden
  [ok] Schulz fuehrt sie nicht mehr als uebernommene Stunde
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-21", "lessonId": 6, "kind": "SUBSTITUTION", "substituteTeacherId": 3}'
  -> HTTP 400
  [ok] Schulz laesst sich an diesem Tag nicht mehr als Vertretung eintragen -> 400

### Nacharbeit 05.09.  Eine Zusatzstunde geht nur mit ihrem Ereignis zurueck
$ curl -s -X DELETE $B/api/exceptions/5 -H "$A"
  -> HTTP 400
  [ok] DELETE /api/exceptions auf eine Zusatzstunde -> 400 mit Verweis auf /api/events
  [ok] das Ereignis behaelt seine Zusatzstunde (extra ist gefuellt)
```

### T-110 — am unterrichtsfreien Tag gibt es nichts einzutragen

```
### T-110  Ferien und Feiertage (Kann, E-13)
$ curl -s -X POST $B/api/holidays -H "$A" -d '{"name": "Tag der Deutschen Einheit", "dateFrom": "2026-09-22", "dateTo": "2026-09-22", "kind": "PUBLIC_HOLIDAY"}'
  -> HTTP 201
  Tage: [["2026-09-21", true, 6], ["2026-09-22", false, 0], ["2026-09-23", true, 0], ["2026-09-24", true, 0], ["2026-09-25", true, 0]]
  [ok] 201; am Feiertag schoolDay false und keine Eintraege
  [ok] der Montag bleibt Unterrichtstag
  [ok] am Feiertag entsteht kein Vertretungsbedarf
$ curl -s -X GET $B/api/substitutions/options?date=2026-09-22&lessonId=7 -H "$A"
  -> HTTP 404
  [ok] am Feiertag liefert /api/substitutions/options 404 statt Vorschlaegen
$ curl -s -X POST $B/api/exceptions -H "$A" -d '{"date": "2026-09-22", "lessonId": 7, "kind": "CANCEL"}'
  -> HTTP 404
  [ok] am Feiertag laesst sich keine Ausnahme anlegen -> 404
$ curl -s -X DELETE $B/api/holidays/1 -H "$A"
  -> HTTP 204
  [ok] 204 und der Dienstag ist wieder Unterrichtstag

==============================================================================
Alle Zusicherungen halten.
```
