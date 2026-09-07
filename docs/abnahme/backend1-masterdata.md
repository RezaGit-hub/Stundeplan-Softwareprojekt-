# Abnahme masterdata (Bauphase backend1) — 2026-09-05 00:43
Basis: `B=http://localhost:8095` (Backend direkt, docker compose Projekt stundenplan-md), Token der Planung aus `POST /api/auth/login`.

## Ergebnis in einer Tabelle

Alle Zeilen der Abnahme-Checkliste, die dem Modul `masterdata` gehören, plus die masterdata-Anteile
von T-W1.3 und drei Zusatzfälle. Jede Zeile ist unten mit Befehl und Antwort belegt.

| # | Schritt | erwartet | gemessen | Ergebnis |
|---|---|---|---|---|
| G-1 | drei Fächer anlegen, Physik mit Raumanforderung | je 201, `GET` liefert genau drei | 201/201/201, drei Einträge, IDs 1–3 wie im Vertragsbeispiel | ok |
| G-2 | drei Räume | 201 ×3, genau diese drei | 201 ×3, IDs 1–3 | ok |
| G-3 | Raster Mo–Fr, sechs Stunden, Pause nach der 2. | 200, 35 Zeilen, je Tag eine Pause an Position 3 | 200, 35 Zeilen, 5 Pausen, alle Position 3; erste MO 1 `08:00–08:45`, letzte FR 7 | ok |
| G-4 | Meyer (25, Ma+Ph) | 201, `plannedHours 0`, `warnings []` | 201, `plannedHours 0`, `warnings []` | ok |
| G-5 | Weber (25, De) | 201 | 201 | ok |
| G-6 | Schulz (2, Ma, Fr 5./6. gesperrt) — **T-W1.1** | 201; genau drei Lehrkräfte; alle fünf Werte unverändert | 201; drei Einträge; `SCZ / 2 / subjectIds [1] / blockedTimeslotIds [34,35] / active true` | ok |
| G-7 | drei Klassen mit Fächerlisten | je 200/201, `Ma weeklyHours 2, teacherShort MEY`, alle `warnings []` | 201 ×3, PUT 200 ×3, 7a zeigt `Ma 2 MEY`, überall `warnings []` | ok |
| G-8 | 9c Deutsch ohne Wochenstunden und ohne Lehrkraft | 400 `validation`, `errors[]` mit `subjects[1].weeklyHours` und `subjects[1].teacherId`, Liste unverändert | 400, genau diese beiden Felder, 9c weiterhin nur `Ma 1 SCZ` | ok |
| G-9 | Fach Mathematik löschen | 409 `in-use`, „Mathematik wird in 7a, 8b, 9c unterrichtet.", Fach bleibt | 409, Text wörtlich, drei Fächer weiterhin da | ok |
| G-10 | 9c Ma Schulz → Weber → zurück — **T-W1.4** | genau eine Warnung `SUBJECT_NOT_TAUGHT`, danach `warnings []` | eine Warnung, `teacherId 2`, „Weber unterrichtet das Fach Mathematik nicht."; nach dem Zurücksetzen leer | ok |
| G-11 | 7a Ma Meyer → Schulz → zurück — **T-W1.5** | genau eine Warnung `QUOTA_EXCEEDED`, „Schulz ist mit 3 Wochenstunden über dem Deputat von 2.", danach `warnings []` | eine Warnung, Text wörtlich; `GET /api/teachers/3` zeigt `plannedHours 3`; nach dem Zurücksetzen `plannedHours 1`, `warnings []` | ok |
| T-W1.3 (1) | Krause ohne Verweise löschen | 204, in keiner Liste | 204, Liste hat wieder drei | ok |
| T-W1.3 (2) | Meyer löschen | 409 `in-use`, „Meyer ist in 5 Stunden eingeplant. Deaktivieren statt löschen." | 409, Text wörtlich | ok |
| T-W1.3 (3) | Meyer deaktivieren | 200 `active:false` | 200, `active false`, `plannedHours 5` | ok |
| T-W1.3 (5a) | Meyer bleibt in der Liste, Stunden behalten ihn | Liste zeigt `active:false`; Stunden unverändert | drei Lehrkräfte, Meyer `active false`; 5 Stunden zeigen weiter auf Meyer | ok |
| Z-1 | belegtes Zeitfenster entfernen | 409 `in-use`, Raster unverändert | 409 „4 Zeitfenster sind im aktiven Plan oder Entwurf belegt …", danach weiter 35 Zeilen | ok |
| Z-2 | belegten Raum löschen | 409 `in-use` mit Hinweis auf Deaktivieren | 409 „R101 ist in 3 Stunden eingeplant. Deaktivieren statt löschen." | ok |
| Z-3 | belegte Klasse löschen | 409 `in-use` mit Hinweis auf Deaktivieren | 409 „7a hat 4 eingeplante Stunden. Deaktivieren statt löschen." | ok |

Nicht hier belegt (andere Module oder spätere Phasen): I-1 bis I-3, L-1/L-2 (`auth`), R-1 bis R-3
(`rules`), A-1 (`auth`), K-3/K-4 (`deployment`), alles ab P-1 (backend2/backend3) und
T-W1.3 Schritt 4 und 5b (`substitution`, backend2/backend3).

**Vorbereitung:** frische Datenbank, `docker compose -p stundenplan-md -f docker-compose.yml -f
docker-compose.dev.yml up -d db backend` mit `BACKEND_PORT=8095`, `ADMIN_INITIAL_PASSWORD=admin`.
Der aktive Plan Mo/Di (sieben Stunden) entsteht per SQL — das Modul `planning` gibt es in dieser
Bauphase noch nicht.

## Protokoll

```
### L-1  POST /api/auth/login (Vorbedingung)
    -> Token erhalten, Laenge 43

### G-1a  POST /api/subjects
    Body: {"name":"Mathematik","shortName":"Ma","requiredRoomType":null,"color":"#1976D2"}
    -> HTTP 201
    {"id":1,"name":"Mathematik","shortName":"Ma","requiredRoomType":null,"color":"#1976D2"}

### G-1b  POST /api/subjects
    Body: {"name":"Deutsch","shortName":"De","requiredRoomType":null,"color":"#388E3C"}
    -> HTTP 201
    {"id":2,"name":"Deutsch","shortName":"De","requiredRoomType":null,"color":"#388E3C"}

### G-1c  POST /api/subjects
    Body: {"name":"Physik","shortName":"Ph","requiredRoomType":"Fachraum Physik","color":"#F57C00"}
    -> HTTP 201
    {"id":3,"name":"Physik","shortName":"Ph","requiredRoomType":"Fachraum Physik","color":"#F57C00"}

### G-1d  GET /api/subjects
    -> HTTP 200
    [{"id":1,"name":"Mathematik","shortName":"Ma","requiredRoomType":null,"color":"#1976D2"},{"id":2,"name":"Deutsch","shortName":"De","requiredRoomType":null,"color":"#388E3C"},{"id":3,"name":"Physik","shortName":"Ph","requiredRoomType":"Fachraum Physik","color":"#F57C00"}]

### G-2a  POST /api/rooms
    Body: {"name":"R101","roomType":"Klassenraum","active":true}
    -> HTTP 201
    {"id":1,"name":"R101","roomType":"Klassenraum","active":true}

### G-2b  POST /api/rooms
    Body: {"name":"R204","roomType":"Klassenraum","active":true}
    -> HTTP 201
    {"id":2,"name":"R204","roomType":"Klassenraum","active":true}

### G-2c  POST /api/rooms
    Body: {"name":"P1","roomType":"Fachraum Physik","active":true}
    -> HTTP 201
    {"id":3,"name":"P1","roomType":"Fachraum Physik","active":true}

### G-2d  GET /api/rooms
    -> HTTP 200
    [{"id":1,"name":"R101","roomType":"Klassenraum","active":true},{"id":2,"name":"R204","roomType":"Klassenraum","active":true},{"id":3,"name":"P1","roomType":"Fachraum Physik","active":true}]

### G-3a  PUT /api/timegrid
    Body: {"slots": [{"day": "MO", "position": 1, "startTime": "08:00", "endTime": "08:45", "isBreak": false}, {"day": "MO", "position": 2, "startTime": "08:50", "endTime": "09:35", "isBreak": false}, {"day": "MO", "position": 3, "startTime": "09:35", "endTime": "09:55", "isBreak": true}, {"day": "MO", "position": 4, "startTime": "09:55", "endTime": "10:40", "isBreak": false}, {"day": "MO", "position": 5, "startTime": "10:45", "endTime": "11:30", "isBreak": false}, {"day": "MO", "position": 6, "startTime": "11:50", "endTime": "12:35", "isBreak": false}, {"day": "MO", "position": 7, "startTime": "12:40", "endTime": "13:25", "isBreak": false}, {"day": "TU", "position": 1, "startTime": "08:00", "endTime": "08:45", "isBreak": false}, {"day": "TU", "position": 2, "startTime": "08:50", "endTime": "09:35", "isBreak": false}, {"day": "TU", "position": 3, "startTime": "09:35", "endTime": "09:55", "isBreak": true}, {"day": "TU", "position": 4, "startTime": "09:55", "endTime": "10:40", "isBreak": false}, {"day": "TU", "position": 5, "startTime": "10:45", "endTime": "11:30", "isBreak": false}, {"day": "TU", "position": 6, "startTime": "11:50", "endTime": "12:35", "isBreak": false}, {"day": "TU", "position": 7, "startTime": "12:40", "endTime": "13:25", "isBreak": false}, {"day": "WE", "position": 1, "startTime": "08:00", "endTime": "08:45", "isBreak": false}, {"day": "WE", "position": 2, "startTime": "08:50", "endTime": "09:35", "isBreak": false}, {"day": "WE", "position": 3, "startTime": "09:35", "endTime": "09:55", "isBreak": true}, {"day": "WE", "position": 4, "startTime": "09:55", "endTime": "10:40", "isBreak": false}, {"day": "WE", "position": 5, "startTime": "10:45", "endTime": "11:30", "isBreak": false}, {"day": "WE", "position": 6, "startTime": "11:50", "endTime": "12:35", "isBreak": false}, {"day": "WE", "position": 7, "startTime": "12:40", "endTime": "13:25", "isBreak": false}, {"day": "TH", "position": 1, "startTime": "08:00", "endTime": "08:45", "isBreak": false}, {"day": "TH", "position": 2, "startTime": "08:50", "endTime": "09:35", "isBreak": false}, {"day": "TH", "position": 3, "startTime": "09:35", "endTime": "09:55", "isBreak": true}, {"day": "TH", "position": 4, "startTime": "09:55", "endTime": "10:40", "isBreak": false}, {"day": "TH", "position": 5, "startTime": "10:45", "endTime": "11:30", "isBreak": false}, {"day": "TH", "position": 6, "startTime": "11:50", "endTime": "12:35", "isBreak": false}, {"day": "TH", "position": 7, "startTime": "12:40", "endTime": "13:25", "isBreak": false}, {"day": "FR", "position": 1, "startTime": "08:00", "endTime": "08:45", "isBreak": false}, {"day": "FR", "position": 2, "startTime": "08:50", "endTime": "09:35", "isBreak": false}, {"day": "FR", "position": 3, "startTime": "09:35", "endTime": "09:55", "isBreak": true}, {"day": "FR", "position": 4, "startTime": "09:55", "endTime": "10:40", "isBreak": false}, {"day": "FR", "position": 5, "startTime": "10:45", "endTime": "11:30", "isBreak": false}, {"day": "FR", "position": 6, "startTime": "11:50", "endTime": "12:35", "isBreak": false}, {"day": "FR", "position": 7, "startTime": "12:40", "endTime": "13:25", "isBreak": false}]}
    -> HTTP 200
    {"slots":[{"id":1,"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},{"id":2,"day":"MO","position":2,"startTime":"08:50","endTime":"09:35","isBreak":false},{"id":3,"day":"MO","position":3,"startTime":"09:35","endTime":"09:55","isBreak":true},{"id":4,"day":"MO","position":4,"startTime":"09:55","endTime":"10:40","isBreak":false},{"id":5,"day":"MO","position":5,"startTime":"10:45","endTime":"11:30","isBreak":false},{"id":6,"day":"MO","position":6,"startTime":"11:50","endTime":"12:35","isBreak":false},{"id":7,"day":"MO","position":7,"startTime":"12:40","endTime":"13:25","isBreak":false},{"id":8,"day":"TU","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},{"id":9,"day":"TU","position":2,"startTime":"08:50","endTime":"09:35","isBreak":false},{"id":10,"day":"TU","position":3,"startTime":"09:35","endTime":"09:55","isBreak":true},{"id":11,"day":"TU","

### G-3b  Auswertung des Rasters
    Zeilen: 35 | Pausen: 5 | Pausen-Positionen: [3]
    erste: {'id': 1, 'day': 'MO', 'position': 1, 'startTime': '08:00', 'endTime': '08:45', 'isBreak': False} | letzte: {'id': 35, 'day': 'FR', 'position': 7, 'startTime': '12:40', 'endTime': '13:25', 'isBreak': False}

### G-4  POST /api/teachers
    Body: {"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}
    -> HTTP 201
    {"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":0,"warnings":[]}

### G-5  POST /api/teachers
    Body: {"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[2],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}
    -> HTTP 201
    {"id":2,"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[2],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":0,"warnings":[]}

### G-6a  POST /api/teachers
    Body: {"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true}
    -> HTTP 201
    {"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":0,"warnings":[]}

### G-6b  GET /api/teachers
    -> HTTP 200
    [{"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":0,"warnings":[]},{"id":2,"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[2],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":0,"warnings":[]},{"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":0,"warnings":[]}]

### G-6c (T-W1.1)  GET /api/teachers/3
    -> HTTP 200
    {"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":0,"warnings":[]}

### G-7a  POST /api/classes
    Body: {"name":"7a","grade":7,"active":true}
    -> HTTP 201
    {"id":1,"name":"7a","grade":7,"active":true,"subjects":[],"warnings":[]}

### G-7b  POST /api/classes
    Body: {"name":"8b","grade":8,"active":true}
    -> HTTP 201
    {"id":2,"name":"8b","grade":8,"active":true,"subjects":[],"warnings":[]}

### G-7c  POST /api/classes
    Body: {"name":"9c","grade":9,"active":true}
    -> HTTP 201
    {"id":3,"name":"9c","grade":9,"active":true,"subjects":[],"warnings":[]}

### G-7d  PUT /api/classes/1/subjects
    Body: [{"subjectId":1,"weeklyHours":2,"teacherId":1},{"subjectId":2,"weeklyHours":1,"teacherId":2},{"subjectId":3,"weeklyHours":1,"teacherId":1}]
    -> HTTP 200
    {"id":1,"name":"7a","grade":7,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":2,"teacherId":1,"teacherShort":"MEY"},{"subjectId":2,"subjectShort":"De","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"},{"subjectId":3,"subjectShort":"Ph","weeklyHours":1,"teacherId":1,"teacherShort":"MEY"}],"warnings":[]}

### G-7e  PUT /api/classes/2/subjects
    Body: [{"subjectId":1,"weeklyHours":2,"teacherId":1},{"subjectId":2,"weeklyHours":1,"teacherId":2}]
    -> HTTP 200
    {"id":2,"name":"8b","grade":8,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":2,"teacherId":1,"teacherShort":"MEY"},{"subjectId":2,"subjectShort":"De","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"}],"warnings":[]}

### G-7f  PUT /api/classes/3/subjects
    Body: [{"subjectId":1,"weeklyHours":1,"teacherId":3}]
    -> HTTP 200
    {"id":3,"name":"9c","grade":9,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":1,"teacherId":3,"teacherShort":"SCZ"}],"warnings":[]}

### G-7g  GET /api/classes
    -> HTTP 200
    [{"id":1,"name":"7a","grade":7,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":2,"teacherId":1,"teacherShort":"MEY"},{"subjectId":2,"subjectShort":"De","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"},{"subjectId":3,"subjectShort":"Ph","weeklyHours":1,"teacherId":1,"teacherShort":"MEY"}],"warnings":[]},{"id":2,"name":"8b","grade":8,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":2,"teacherId":1,"teacherShort":"MEY"},{"subjectId":2,"subjectShort":"De","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"}],"warnings":[]},{"id":3,"name":"9c","grade":9,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":1,"teacherId":3,"teacherShort":"SCZ"}],"warnings":[]}]

### G-8  PUT /api/classes/3/subjects
    Body: [{"subjectId":1,"weeklyHours":1,"teacherId":3},{"subjectId":2,"weeklyHours":null,"teacherId":null}]
    -> HTTP 400
    {"detail":"Bitte die markierten Felder pruefen.","instance":"/api/classes/3/subjects","status":400,"title":"Eingabe unvollstaendig","type":"urn:pushnpull:problem:validation","errors":[{"message":"Wochenstunden fehlen","field":"subjects[1].weeklyHours"},{"message":"Lehrkraft fehlt","field":"subjects[1].teacherId"}]}

### G-8b (unveraendert)  GET /api/classes/3
    -> HTTP 200
    {"id":3,"name":"9c","grade":9,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":1,"teacherId":3,"teacherShort":"SCZ"}],"warnings":[]}

### G-9  DELETE /api/subjects/1
    -> HTTP 409
    {"detail":"Mathematik wird in 7a, 8b, 9c unterrichtet.","instance":"/api/subjects/1","status":409,"title":"Konflikt","type":"urn:pushnpull:problem:in-use"}

### G-9b (weiter vorhanden)  GET /api/subjects
    -> HTTP 200
    [{"id":1,"name":"Mathematik","shortName":"Ma","requiredRoomType":null,"color":"#1976D2"},{"id":2,"name":"Deutsch","shortName":"De","requiredRoomType":null,"color":"#388E3C"},{"id":3,"name":"Physik","shortName":"Ph","requiredRoomType":"Fachraum Physik","color":"#F57C00"}]

### G-10a (T-W1.4)  PUT /api/classes/3/subjects
    Body: [{"subjectId":1,"weeklyHours":1,"teacherId":2}]
    -> HTTP 200
    {"id":3,"name":"9c","grade":9,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"}],"warnings":[{"code":"SUBJECT_NOT_TAUGHT","message":"Weber unterrichtet das Fach Mathematik nicht.","teacherId":2}]}

### G-10b (zurueck)  PUT /api/classes/3/subjects
    Body: [{"subjectId":1,"weeklyHours":1,"teacherId":3}]
    -> HTTP 200
    {"id":3,"name":"9c","grade":9,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":1,"teacherId":3,"teacherShort":"SCZ"}],"warnings":[]}

### G-11a (T-W1.5)  PUT /api/classes/1/subjects
    Body: [{"subjectId":1,"weeklyHours":2,"teacherId":3},{"subjectId":2,"weeklyHours":1,"teacherId":2},{"subjectId":3,"weeklyHours":1,"teacherId":1}]
    -> HTTP 200
    {"id":1,"name":"7a","grade":7,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":2,"teacherId":3,"teacherShort":"SCZ"},{"subjectId":2,"subjectShort":"De","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"},{"subjectId":3,"subjectShort":"Ph","weeklyHours":1,"teacherId":1,"teacherShort":"MEY"}],"warnings":[{"code":"QUOTA_EXCEEDED","message":"Schulz ist mit 3 Wochenstunden über dem Deputat von 2.","teacherId":3}]}

### G-11b (Lehrkraft-Sicht)  GET /api/teachers/3
    -> HTTP 200
    {"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":3,"warnings":[{"code":"QUOTA_EXCEEDED","message":"Schulz ist mit 3 Wochenstunden über dem Deputat von 2.","teacherId":3}]}

### G-11c (zurueck)  PUT /api/classes/1/subjects
    Body: [{"subjectId":1,"weeklyHours":2,"teacherId":1},{"subjectId":2,"weeklyHours":1,"teacherId":2},{"subjectId":3,"weeklyHours":1,"teacherId":1}]
    -> HTTP 200
    {"id":1,"name":"7a","grade":7,"active":true,"subjects":[{"subjectId":1,"subjectShort":"Ma","weeklyHours":2,"teacherId":1,"teacherShort":"MEY"},{"subjectId":2,"subjectShort":"De","weeklyHours":1,"teacherId":2,"teacherShort":"WEB"},{"subjectId":3,"subjectShort":"Ph","weeklyHours":1,"teacherId":1,"teacherShort":"MEY"}],"warnings":[]}

### G-11d (keine Warnung mehr)  GET /api/teachers/3
    -> HTTP 200
    {"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":1,"warnings":[]}

### Vorbereitung T-W1.3: aktiver Plan Mo/Di (sieben Stunden) per SQL angelegt
    7 Stunden in lesson

### T-W1.3-1a (Krause)  POST /api/teachers
    Body: {"name":"Krause","shortName":"KRA","weeklyHoursQuota":10,"subjectIds":[],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}
    -> HTTP 201
    {"id":4,"name":"Krause","shortName":"KRA","weeklyHoursQuota":10,"subjectIds":[],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":0,"warnings":[]}

### T-W1.3-1b (loeschen)  DELETE /api/teachers/4
    -> HTTP 204
    

### T-W1.3-1c (Liste)  GET /api/teachers
    -> HTTP 200
    [{"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":5,"warnings":[]},{"id":2,"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[2],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":2,"warnings":[]},{"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":1,"warnings":[]}]

### T-W1.3-2 (Meyer loeschen)  DELETE /api/teachers/1
    -> HTTP 409
    {"detail":"Meyer ist in 5 Stunden eingeplant. Deaktivieren statt löschen.","instance":"/api/teachers/1","status":409,"title":"Konflikt","type":"urn:pushnpull:problem:in-use"}

### T-W1.3-3 (Meyer deaktivieren)  PUT /api/teachers/1
    Body: {"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":false}
    -> HTTP 200
    {"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":false,"plannedHours":5,"warnings":[]}

### T-W1.3-5a (Liste)  GET /api/teachers
    -> HTTP 200
    [{"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":false,"plannedHours":5,"warnings":[]},{"id":2,"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[2],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":2,"warnings":[]},{"id":3,"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true,"plannedHours":1,"warnings":[]}]

### T-W1.3-4 (Stunden behalten Meyer)
    Stunden mit Meyer in lesson: 5

### T-W1.3 Ruecknahme  PUT /api/teachers/1
    Body: {"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}
    -> HTTP 200
    {"id":1,"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true,"plannedHours":5,"warnings":[]}

### Z-1 (Raster kuerzen)  PUT /api/timegrid
    Body: {"slots":[{"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false}]}
    -> HTTP 409
    {"detail":"4 Zeitfenster sind im aktiven Plan oder Entwurf belegt und können nicht entfernt werden.","instance":"/api/timegrid","status":409,"title":"Konflikt","type":"urn:pushnpull:problem:in-use"}

### Z-2 (Raum belegt)  DELETE /api/rooms/1
    -> HTTP 409
    {"detail":"R101 ist in 3 Stunden eingeplant. Deaktivieren statt löschen.","instance":"/api/rooms/1","status":409,"title":"Konflikt","type":"urn:pushnpull:problem:in-use"}

### Z-3 (Klasse belegt)  DELETE /api/classes/1
    -> HTTP 409
    {"detail":"7a hat 4 eingeplante Stunden. Deaktivieren statt löschen.","instance":"/api/classes/1","status":409,"title":"Konflikt","type":"urn:pushnpull:problem:in-use"}

### Z-4 (Raster unveraendert)  GET /api/timegrid
    -> HTTP 200
    {"slots":[{"id":1,"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},{"id":2,"day":"MO","position":2,"startTime":"08:50","endTime":"09:35","isBreak":false},{"id":3,"day":"MO","position":3,"startTime":"09:35","endTime":"09:55","isBreak":true},{"id":4,"day":"MO","position":4,"startTime":"09:55","endTime":"10:40","isBreak":false},{"id":5,"day":"MO","position":5,"startTime":"10:45","endTime":"11:30","isBreak":false},{"id":6,"day":"MO","position":6,"startTime":"11:50","endTime":"12:35","isBreak":false},{"id":7,"day":"MO","position":7,"startTime":"12:40","endTime":"13:25","isBreak":false},{"id":8,"day":"TU","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},{"id":9,"day":"TU","position":2,"startTime":"08:50","endTime":"09:35","isBreak":false},{"id":10,"day":"TU","position":3,"startTime":"09:35","endTime":"09:55","isBreak":true},{"id":11,"day":"TU","

```
