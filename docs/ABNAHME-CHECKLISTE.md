# ABNAHME-CHECKLISTE — Testdurchlauf und Testfälle T-W1.1 bis T-A3.1 als prüfbare Schritte

Stand 05.09.2026, Vollausbau (`08-Build/plattform`, Branch `vollausbau`). **Abschnitt 5: Abnahmedurchlauf 05.09. auf frischem Klon — Ergebnis nicht bestanden (Android offen, Vollausbau uncommitted), Web/Server vollständig grün.** Maßstab für die Abnahme des Vollausbaus
und Vorlage für die 30-Minuten-Checkliste des Teams (T-108). Quellen: Pflichtenheft `kapitel/testdurchlauf.tex` und
`kapitel/produktfunktionen.tex` (Stand 04.09., 17 Testfälle), `api-contract/openapi.yaml` v1, `docs/VERTRAG.md`
(Module und Bauphasen), `docs/SOLVER-PLAN.md`.

## 0. Lesehilfe

- **Phase**: `backend1` = shared, config, auth, masterdata, rules · `backend2` = planning, solver, changelog,
  sampledata, Wochensichten (`substitution` Teil 1: `WeekResolver`, `/api/timetable/week`, `/api/me/timetable/week`)
  · `backend3` = substitution Teil 2 (Ereignisse, offene Stunden, Optionen, Ausnahmen, Ferien) · `frontend` =
  Web-App gegen das echte Backend · `android` = Repo `android-app` (nicht Teil des Vollausbaus; hier nur der
  Server-Anteil und die Web-Vorschau `/app-demo` gegen die Attrappe).
- **Aktion** steht zweifach: Browser (Web-App, Rolle Planung angemeldet) und API (`curl`). Basis `B=http://localhost:8080`
  (Backend direkt) oder `B=http://localhost:1500` (über nginx). `T` = Token der Planung aus
  `POST /api/auth/login`; `-H "Authorization: Bearer $T" -H "content-type: application/json"` wird als `-A` abgekürzt.
- **IDs**: Der Durchlauf beginnt auf einer leeren Datenbank (`cd deployment && docker compose down -v && docker compose
  up -d --build`). Legt man die Daten in der Reihenfolge der Vertragsbeispiele an, gelten: Fächer Mathematik 1,
  Deutsch 2, Physik 3 · Räume R101 1, R204 2, P1 3 · Lehrkräfte **Meyer 1, Schulz 2, Weber 3** · Klassen 7a 1, 8b 2,
  9c 3 · Zeitfenster Mo 1–7, Di 8–14, Mi 15–21, Do 22–28, Fr 29–35 (Position 3 = Pause; „5. Stunde" = Position 6).
  Der Browser-Durchlauf nach Pflichtenheft legt Weber vor Schulz an (dann Weber 2, Schulz 3). **IDs immer aus der
  Antwort lesen**, nie raten.
- **Erwartet** ist die Bedingung, an der die Testperson allein entscheidet. Alles, was nicht messbar ist, gilt als
  nicht erfüllt (Pflichtenheft).
- Autotest = Testklasse und -methode im Backend (`backend/src/test`), Namen aus `backbone/testen.md`, Kennung des
  Testfalls im Methodennamen.

## 1. Testdurchlauf (Pflichtenheft Kapitel „Testdurchlauf"), Schritt für Schritt

### 1.1 Installation und Start

| # | Schritt (Pflichtenheft) | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| I-1 | Repository klonen, `cd deployment` | `git clone … && cd web-app/deployment && cp -n .env.example .env` | `.env` vorhanden **und `ADMIN_INITIAL_PASSWORD` auf einen eigenen Wert gesetzt** — mit dem Platzhalter `bitte-aendern` bricht `backend` im Profil `prod` ab (SICHERHEIT-6) | deployment | backend1 |
| I-2 | `docker compose up -d` (nach dem Kennwortschritt aus I-1) | `docker compose up -d --build && docker compose ps` | drei Dienste `running` (db, backend, web), keiner `exited`; Backend-Log `Successfully applied 8 migrations` (V1–V8; V7 Klassenkonto, V8 Ferienvorlagen) und `Started BackendApplication` | deployment | backend1 |
| I-3 | Browser `http://<host>:1500` | `curl -s -o /dev/null -w '%{http_code}' $B/login` | `200`, Seite zeigt Anmeldemaske mit Schulname aus `GET /api/health` | frontend | frontend |
| I-4 | APK installieren, App fragt nach Serveradresse | Android-Gerät | Ersteinrichtung erscheint | android | android |
| I-5 | Adresse `http://<host>:1500` eintragen (A-3) | App „Verbindung prüfen" = `GET /api/health` | App meldet „Server erreichbar", zeigt Anmeldemaske; API: `curl -s $B/api/health` → `{"status":"UP","schoolName":"…"}` | config | backend1 (API) / android |

### 1.2 Anmeldung

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| L-1 | Anmelden als `admin` | Browser: Benutzername `admin`, Kennwort aus `.env`. API: `curl -s -X POST $B/api/auth/login -H 'content-type: application/json' -d '{"username":"admin","password":"…"}'` | 200 `{token, role: "PLANNING", displayName, teacherId: null}`; Startseite zeigt Bereiche Grunddaten, Regelwerk, Plansicht (Aktiver Plan, Entwurf), Änderungen (Vertretung), Änderungsverlauf | auth, frontend | backend1 / frontend |
| L-2 | Negativ: falsches Kennwort | wie L-1 mit falschem Kennwort | 401 `application/problem+json`, `type …:unauthenticated`, `detail` „Benutzername oder Passwort ist falsch." | auth | backend1 |

### 1.3 Grunddaten anlegen (W-1)

| # | Schritt | Aktion (API-Beispiel) | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| G-1 | Fächer Mathematik, Deutsch, Physik (Physik: Raumanforderung „Fachraum Physik") | `POST /api/subjects` dreimal: `{"name":"Mathematik","shortName":"Ma","requiredRoomType":null,"color":"#1976D2"}`, `{"name":"Deutsch","shortName":"De",…,"color":"#388E3C"}`, `{"name":"Physik","shortName":"Ph","requiredRoomType":"Fachraum Physik","color":"#F57C00"}` | je 201; `GET /api/subjects` liefert genau drei Einträge mit diesen Werten | masterdata | backend1 |
| G-2 | Räume R101, R204 (Klassenraum), P1 (Fachraum Physik) | `POST /api/rooms` `{"name":"R101","roomType":"Klassenraum","active":true}` usw. | 201 ×3; `GET /api/rooms` = genau diese drei | masterdata | backend1 |
| G-3 | Stundenraster Mo–Fr, sechs Stunden, große Pause nach der 2. | `PUT /api/timegrid` mit 35 Zeilen in der Reihenfolge Mo…Fr, Position 1–7 (Zeiten wie Beispiel in `openapi.yaml` `GET /api/timegrid`) | 200, `slots` hat 35 Zeilen, je Tag genau eine mit `isBreak: true` an Position 3; Browser: fünf Tage, sechs Unterrichtszeilen, Pausenzeile grau | masterdata | backend1 |
| G-4 | Lehrkraft Meyer (25, Ma+Ph) | `POST /api/teachers` `{"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[1,3],"preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}` | 201, `plannedHours: 0`, `warnings: []` | masterdata | backend1 |
| G-5 | Lehrkraft Weber (25, De) | analog, `subjectIds:[2]` | 201 | masterdata | backend1 |
| G-6 | Lehrkraft Schulz (2, Ma, gesperrt Fr 5. und 6. Stunde) | `{"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[1],"preferredTimeslotIds":[],"blockedTimeslotIds":[34,35],"active":true}` | 201; `GET /api/teachers` = genau drei; `GET /api/teachers/{schulz}` zeigt alle fünf Werte unverändert (**T-W1.1**) | masterdata | backend1 |
| G-7 | Klassen 7a, 8b, 9c mit Fächerlisten | `POST /api/classes` `{"name":"7a","grade":7,"active":true}` → id; `PUT /api/classes/{7a}/subjects` `[{"subjectId":1,"weeklyHours":2,"teacherId":<Meyer>},{"subjectId":2,"weeklyHours":1,"teacherId":<Weber>},{"subjectId":3,"weeklyHours":1,"teacherId":<Meyer>}]`; 8b `[Ma 2 Meyer, De 1 Weber]`; 9c `[Ma 1 Schulz]` | je 200/201; `GET /api/classes` = drei Klassen; 7a zeigt `Ma weeklyHours 2, teacherShort MEY`; alle `warnings: []` | masterdata | backend1 |
| G-8 | 9c: Deutsch ohne Wochenstunden und ohne Lehrkraft speichern | `PUT /api/classes/{9c}/subjects` `[{"subjectId":1,"weeklyHours":1,"teacherId":<Schulz>},{"subjectId":2,"weeklyHours":null,"teacherId":null}]` | 400 `type …:validation`, `errors[]` enthält `subjects[1].weeklyHours` und `subjects[1].teacherId`; Fächerliste der 9c unverändert (Ausnahme 1 aus W-1) | masterdata | backend1 |
| G-9 | Fach Mathematik löschen | `DELETE /api/subjects/1` | 409 `type …:in-use`, `detail` nennt die Klassen („Mathematik wird in 7a, 8b, 9c unterrichtet."); Fach weiter vorhanden (Ausnahme 2) | masterdata | backend1 |
| G-10 | 9c Mathematik: Lehrkraft Schulz → Weber, speichern; zurück | `PUT /api/classes/{9c}/subjects` `[{"subjectId":1,"weeklyHours":1,"teacherId":<Weber>}]`, danach wieder `<Schulz>` | erst 200 mit genau **einer** Warnung `{code: SUBJECT_NOT_TAUGHT, teacherId: <Weber>, message: "Weber unterrichtet das Fach Mathematik nicht."}`; nach dem Zurücksetzen `warnings: []` (**T-W1.4**) | masterdata | backend1 |
| G-11 | 7a Mathematik: Meyer → Schulz, speichern; zurück | `PUT /api/classes/{7a}/subjects` mit `Ma teacherId <Schulz>` (übrige Zeilen unverändert), danach zurück auf Meyer | erst 200 mit genau **einer** Warnung `{code: QUOTA_EXCEEDED, teacherId: <Schulz>, message: "Schulz ist mit 3 Wochenstunden über dem Deputat von 2."}`; danach `warnings: []` (**T-W1.5**) | masterdata | backend1 |

### 1.4 Regelwerk pflegen (W-2)

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| R-1 | Regeln „Maximale Stunden pro Tag" (6) und „… pro Woche" (30) vorhanden und aktiv | `GET /api/rules` | Zeilen `MAX_LESSONS_PER_DAY/CLASS/6/SOFT/active true` und `MAX_LESSONS_PER_WEEK/CLASS/30/SOFT/active true` (Seed V2 für SH); Browser zeigt beide mit diesen Werten | rules | backend1 |
| R-2 | Wert „pro Tag" auf 0 setzen | `PUT /api/rules/{id}` `{"value":0,"severity":"SOFT","weight":1,"active":true}` | 400 `validation`, `errors[0].field = "value"`, Meldung „muss mindestens 1 sein"; `GET /api/rules` zeigt weiter 6 | rules | backend1 |
| R-3 | (Zusatz) Voreinstellung laden | `POST /api/rules/reset` | 200, fünf Zeilen mit den Werten der `rule_preset`-Zeilen für `state` aus `GET /api/config` (SH: 6/30 Klasse aktiv, 6/26 Lehrkraft inaktiv, Pause hart) | rules | backend1 |

### 1.5 Stundenplan erstellen und Konflikte erkennen (W-3, W-4)

Alle Schritte laufen im **Entwurf** (Handaufbau ist nur dort möglich); die „Plansicht der Klasse 9c" ist die
Klassensicht des Entwurfs (Entwurfsdoku Kap. 6). Vorher: `POST /api/draft {"source":"EMPTY"}` → 201 (Browser:
Entwurf → „Leer beginnen").

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| P-1 | Regelplan Montag/Dienstag eintragen | `POST /api/draft/lessons` siebenmal: 7a Ma ts 1 R101 · 7a De ts 2 R101 · 7a Ph ts 4 P1 · 8b Ma ts 2 R204 · 8b De ts 4 R204 · 8b Ma ts 5 R204 · 7a Ma ts 8 R101 (`{"classId":1,"subjectId":1,"timeslotId":1,"roomId":1}` usw.) | je 201, alle `pinned: true`, `teacherShort` folgt aus der Fachzuordnung (MEY/WEB), `conflicts: []`; Klassensicht 7a: Mo drei Stunden, Di eine; 8b: Mo drei; `openLessons` = `[{9c, Ma, missing 1}]` | planning | backend2 |
| P-2 | 9c Mo 1. Stunde Mathematik in R101 (Lehrkraft automatisch Schulz) | `POST /api/draft/lessons {"classId":3,"subjectId":1,"timeslotId":1,"roomId":1}` | 201; die neue Stunde hat `teacherShort: "SCZ"`; `conflicts` = genau **ein** Eintrag `type ROOM_DOUBLE_BOOKED`, `objects[0].label "R101"`, `lessonIds` = die 7a-Stunde **und** die 9c-Stunde, `message` nennt R101, Montag, 1. Stunde; **kein** `TEACHER_DOUBLE_BOOKED` (**T-W3.1**) | planning | backend2 |
| P-3 | diese Stunde auf Mo 5. Stunde verschieben | `PATCH /api/draft/lessons/{9c-Stunde} {"timeslotId":6}` | 200; Stunde hat `timeslotId 6, position 6`, gleiche `subjectShort/teacherShort/roomShort`; `conflicts: []` — Markierung an beiden Stunden weg (**T-W3.2**) | planning | backend2 |
| P-4 | 9c Fr 5. Stunde Mathematik in R101 | `POST /api/draft/lessons {"classId":3,"subjectId":1,"timeslotId":34,"roomId":1}` | 201; genau ein Konflikt `type TEACHER_BLOCKED_TIME`, `objects[0]` = Schulz, `message` „Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde"; Stunde vorhanden und `teacherShort SCZ` (**T-W1.2**) | planning | backend2 |
| P-5 | Freitagsstunde löschen | `DELETE /api/draft/lessons/{id}` | 200, Stunde fehlt, `conflicts: []` | planning | backend2 |
| P-5b | **Zusatz (nicht im Pflichtenheft)**: auch die 9c-Stunde vom Montag löschen | `DELETE /api/draft/lessons/{9c-Stunde}` | 200; Entwurf enthält genau die sieben Stunden aus P-1. Grund: T-W4.1 erwartet R101 „ab der 3. Stunde frei" und die Testdatentabelle sagt „9c hat an keinem Tag eine eingeplante Stunde" — mit der Stunde aus P-3 (Mo 5. Stunde, R101) wäre die Raumsicht anders. Vorschlag an Rolle 1 für `testdurchlauf.tex` Schritt 5 | planning | backend2 |
| P-6 | **Entwurf annehmen (nicht im Pflichtenheft, aber nötig — Lernnotiz 88/99)** | `POST /api/draft/accept {}` | 200, `id` gesetzt, sieben Stunden, `conflicts: []`, `warnings: []`; `GET /api/draft` → 404; `GET /api/timetable` = dieselben sieben Stunden. W-4 bis W-7 und die App lesen nur den aktiven Plan | planning, changelog | backend2 |
| P-7 | Sichten: Klasse 7a → Lehrkraft Meyer → Schule → Raum R101 | `GET /api/timetable/week?date=2026-09-21&view=CLASS&id=1`, `…view=TEACHER&id=<Meyer>`, `…view=SCHOOL`, `…view=ROOM&id=1` | 7a Mo Position 1: `subjectShort Ma, teacherShort MEY, roomShort R101`; Lehrkraftsicht Meyer: dieselbe Stunde an derselben Stelle, Montag genau **4** Einträge (Pos. 1, 2, 4, 5), Dienstag genau **1**; Schule: Mo Pos. 1 genau ein Eintrag; Raum R101: Mo Pos. 1 und 2 belegt, Pos. 4–7 ohne Eintrag (**T-W4.1**). Alle `kind: REGULAR` | substitution (WeekResolver) | backend2 |

### 1.6 Leitszenario: Ausfall und Vertretung (W-5, W-6, W-7)

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| V-1 | Ausfall Meyer am 21.09.2026, Anlass „Krankmeldung" | `POST /api/events {"kind":"TEACHER_ABSENCE","teacherId":<Meyer>,"dateFrom":"2026-09-21","dateTo":"2026-09-21","reason":"Krankmeldung"}` | 201, `affected` = genau vier Zeilen (7a Pos. 1, 8b Pos. 2, 7a Pos. 4, 8b Pos. 5), `hint: null`; `GET /api/timetable/week?date=2026-09-21&view=SCHOOL`: am 21.09. genau vier `NEEDS_SUBSTITUTION` (7a Pos. 2 Weber bleibt `REGULAR`), am 22.09. keiner; `GET /api/timetable` unverändert (**T-W5.1**) | substitution | backend3 |
| V-2 | Sperrung R101 am 21.09., Anlass „Wasserschaden" | `POST /api/events {"kind":"ROOM_BLOCKED","roomId":1,"dateFrom":"2026-09-21","dateTo":"2026-09-21","reason":"Wasserschaden"}` | 201, `affected` = 7a Pos. 1 und 7a Pos. 2; Woche: jetzt **fünf** `NEEDS_SUBSTITUTION`, 7a Pos. 1 mit `reasons ["Krankmeldung","Wasserschaden"]`, 7a Pos. 2 mit `["Wasserschaden"]` | substitution | backend3 |
| V-3 | offene Stunde 7a 21.09. 1. Stunde öffnen | `GET /api/substitutions/pending?from=2026-09-21&to=2026-09-25` → erste Zeile ist 7a Pos. 1; `GET /api/substitutions/options?date=2026-09-21&lessonId=<7a-Mo-1>` | `teachers` enthält Schulz (`teachesSubject true`, erster Eintrag) und **nicht** Meyer; `rooms` enthält R204 und **nicht** R101 | substitution | backend3 |
| V-4 | Schulz und R204 zuweisen | `POST /api/exceptions {"date":"2026-09-21","lessonId":<7a-Mo-1>,"kind":"SUBSTITUTION","substituteTeacherId":<Schulz>,"substituteRoomId":2,"eventIds":[1,2]}` | 201, `conflicts: []`; Woche 21.09.: Eintrag 7a Pos. 1 `kind SUBSTITUTED, teacherShort SCZ, roomShort R204, original {teacherShort MEY, roomShort R101}`; `pending` hat jetzt vier Zeilen ohne diese (**T-W6.1**) | substitution, changelog | backend3 |
| V-5 | Plansicht 28.09. | `GET /api/timetable/week?date=2026-09-28&view=CLASS&id=1` | 7a Mo Pos. 1: `REGULAR, MEY, R101` | substitution | backend3 |
| V-6 | Änderungsverlauf, Filter 7a | `GET /api/changelog?classId=1` | `total 1`; Eintrag: `action EXCEPTION_CREATED, date 2026-09-21, position 1, classShort 7a, oldAssignment "Meyer / R101", newAssignment "Schulz / R204", reasons "Krankmeldung; Wasserschaden", username "admin", timestamp` gesetzt; `curl -s -o /dev/null -w '%{http_code}' -X DELETE -A $B/api/changelog/1` → **404** (keine Route); Browser: keine Schaltfläche Löschen/Bearbeiten (**T-W7.1**) | changelog | backend2 (Endpunkt) / backend3 (Eintrag) |

### 1.7 Beispieldatenbestand und Bedienbarkeit

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| D-1 | „Vollständige Schule" laden | `POST /api/sample-data {}` → 409 `data-exists` (Daten vorhanden); Browser fragt „Ersetzen?"; `POST /api/sample-data {"replace":true}` | 200 `{classes: 12, teachers: 24, rooms: 18}` nach ≤ 30 s; `GET /api/classes` 12, `/api/teachers` 24, `/api/rooms` 18; `GET /api/timetable` hat > 0 Stunden (gefüllter, angenommener Plan); Konto `admin` weiter anmeldbar | sampledata | backend2 |
| D-2 | Plansicht einer Klasse | `GET /api/timetable/week?date=2026-09-21&view=CLASS&id=<eine Klasse>` | 200 mit Einträgen; Browser: Raster vollständig, Ladezustand sichtbar bis dahin | frontend | frontend |
| D-3 | Lehrkraftsicht | `…view=TEACHER&id=…` | vollständig | frontend | frontend |
| D-4 | Stunde in ein belegtes Zeitfenster verschieben | Browser: Entwurf → „Aktiven Plan kopieren", Kachel auf belegte Zelle ziehen (oder Klick-Klick). API: `POST /api/draft {"source":"COPY_ACTIVE"}`, `PATCH /api/draft/lessons/{a} {"timeslotId": <Zeitfenster einer anderen Stunde derselben Klasse>}` | Antwort sofort mit `conflicts` ≠ [] (`CLASS_DOUBLE_BOOKED` oder `ROOM_/TEACHER_…`), Konflikttext am Element sichtbar | planning, frontend | backend2 / frontend |
| D-5 | Änderung zurücknehmen | `DELETE /api/draft` (Browser: „Verwerfen") | 204; `GET /api/timetable` gleich dem Stand nach D-1 | planning | backend2 |

### 1.8 Stundenplanvorschlag (W-8)

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| S-1 | Aktiven Plan leeren (Grunddaten bleiben) | Browser: Einstellungen → „Aktiven Plan leeren". API: `POST /api/draft {"source":"EMPTY"}` → `POST /api/draft/accept {}` | `GET /api/timetable` → `lessons: []`; `GET /api/classes` weiter 12 | planning | backend2 |
| S-2 | Vorschlag erzeugen | `POST /api/draft {"source":"EMPTY"}`; `POST /api/draft/solve {}` → 202; alle 2 s `GET /api/draft` bis `status != "SOLVING"`; Laufzeit messen | Ende ≤ `maxSolveSeconds` (`GET /api/config`) + 5 s, `status EDITING`, `solve.lastScore` gesetzt; **jede** Stunde erfüllt `(timeslotId == null) == (roomId == null)`; offene Stunden stehen in `openLessons` (`missing` je Klasse/Fach); `conflicts` enthält **keinen** Typ `ROOM_DOUBLE_BOOKED`, `TEACHER_DOUBLE_BOOKED`, `CLASS_DOUBLE_BOOKED`; weiche Verstöße (`RULE_VIOLATION`, `severity SOFT`) sind einzeln gelistet (**T-W8.1** Teil 1) | planning, solver | backend2 |
| S-3 | Aktiven Plan ansehen ohne anzunehmen | `GET /api/timetable` | weiter `lessons: []` | planning | backend2 |
| S-4 | Vorschlag annehmen | `POST /api/draft/accept {}` | 200; `GET /api/timetable` hat genau die Stunden des Entwurfs (Anzahl und je Stunde `timeslotId/roomId` gleich) (**T-W8.1** Teil 2) | planning | backend2 |

### 1.9 Android-App (A-1, A-2) — Server-Anteil hier, App-Anteil im Repo `android-app`

| # | Schritt | Aktion (Server-Anteil) | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| A-1 | Konto für Meyer anlegen (Vorbedingung) | `POST /api/users {"username":"meyer","password":"<8+ Zeichen>","role":"TEACHER","teacherId":<Meyer>}` | 201 | auth | backend1 |
| A-2 | Als Meyer anmelden, Tag 21.09.2026 | `POST /api/auth/login` (meyer) → `$M`; `GET /api/me/timetable/week?date=2026-09-21 -H "Authorization: Bearer $M"` | 200; Tag 2026-09-21 hat genau **vier** Einträge, alle mit `teacherId <Meyer>` oder `original.teacherId <Meyer>`: Pos. 1 `GIVEN_AWAY` (7a, Ma, `teacherShort SCZ`, `original.teacherShort MEY`), Pos. 2, 4, 5 `NEEDS_SUBSTITUTION`; die Weber-Stunde (7a Pos. 2) fehlt (**T-A1.1**, nach dem Leitszenario). Zusatz: `GET /api/timetable -H "Authorization: Bearer $M"` → **403** `forbidden`; ohne Token → **401** | substitution, auth | backend2 (Woche) / backend3 (kinds) |
| A-3 | Wochenansicht | dieselbe Antwort, alle Tage | genau **fünf** Einträge in der Woche (vier Mo, einer Di), keine fremde Lehrkraft-ID | substitution | backend2 |
| A-4 | Als Schulz anmelden, 21.09. | Konto `schulz` anlegen; Login → `$S`; `GET /api/me/timetable/week?date=2026-09-21` | Eintrag Pos. 1: `kind SUBSTITUTED`, `classShort 7a`, `roomShort R204`, `reasons` enthält „Krankmeldung"; `GET /api/timegrid -H "Authorization: Bearer $S"` → 200, Zeitfenster 1 = `08:00–08:45` (Anzeige 8:00–8:45) (**T-A2.1** Teil 1) | substitution | backend3 |
| A-5 | Detail: Anlass und vertretene Lehrkraft | aus demselben Eintrag | `reasons ["Krankmeldung","Wasserschaden"]`, `original.teacherShort "MEY"`; 28.09.: beide Konten `REGULAR` (**T-A2.1** Teil 2) | substitution | backend3 |
| A-6 | Keine Planungsfunktion in der App | `POST /api/draft/lessons … -H "Authorization: Bearer $S"` | **403** `forbidden` (serverseitig, unabhängig von der App) | auth | backend1 |

### 1.10 Konfigurierbarkeit und Dauerhaftigkeit (A-3)

| # | Schritt | Aktion | Erwartet | Modul | Phase |
|---|---|---|---|---|---|
| K-1 | App: Adresse `http://192.0.2.1:9999`, speichern, Neustart | Android-Gerät (bzw. Web-Vorschau `/app-demo` gegen die Attrappe) | Warnung „nicht erreichbar", „Trotzdem speichern", nach Neustart steht die Adresse noch; kein Absturz (**T-A3.1**) | android | android |
| K-2 | gültige Adresse zurück, anmelden | App | Tagesansicht mit Meyers Stunden | android | android |
| K-3 | `docker compose down && docker compose up -d`, erneut anmelden | `cd deployment && docker compose down && docker compose up -d`; danach `GET /api/teachers`, `/api/rules`, `/api/timetable`, `/api/events`, `/api/changelog` | alle Daten wie vor dem Neustart (Zählungen und der Verlaufseintrag aus V-6 unverändert); Volume `stundenplan_pgdata` wurde **nicht** gelöscht | deployment | backend1 |
| K-4 | Port ändern | `WEB_PORT=1503` in `deployment/.env`, `docker compose up -d` | `curl -s -o /dev/null -w '%{http_code}' http://localhost:1503/login` → 200; kein Build, keine Quelltextänderung | deployment | backend1 |

## 2. Die 17 Testfälle einzeln (Vorbedingung · Aktion · Erwartet · Modul · Phase · Autotest)

Jeder Testfall ist auf dem Stand nach Abschnitt 1.3 (Grunddaten) und 1.5 P-1/P-6 (aktiver Plan Mo/Di) prüfbar,
sofern die Vorbedingung nichts anderes sagt. Mutationen werden am Ende zurückgenommen, damit der nächste Testfall
denselben Ausgangszustand hat.

| Testfall | Vorbedingung | Aktion (API) | Erwartet | Modul | Phase | Autotest |
|---|---|---|---|---|---|---|
| **T-W1.1** | Fächer Ma, Ph und Raster angelegt; keine Lehrkraft „Schulz" | G-6 | Liste enthält genau einen „Schulz"; `GET /api/teachers/{id}`: `name Schulz, shortName SCZ, weeklyHoursQuota 2, subjectIds [1], blockedTimeslotIds [34,35]`; Meyer und Weber unverändert | masterdata | backend1 | `TeacherServiceIT.tW1_1_createSchulz_persistsAllFiveValues_othersUnchanged` |
| **T-W1.2** | Testdaten vollständig, Freitag frei; Entwurf = `COPY_ACTIVE` | P-4 im Entwurf | genau **ein** Konflikt: `TEACHER_BLOCKED_TIME`, Objekt Schulz, Text nennt Freitag 5. Stunde; Stunde gespeichert mit `SCZ`; keine weiteren Konflikte. Rücknahme: `DELETE /api/draft` | planning | backend2 | `ConflictCheckerTest.tW1_2_blockedTime_exactlyOneConflict_lessonSaved` |
| **T-W1.3** | Testdaten vollständig; zusätzlich Lehrkraft „Krause" ohne Fach und ohne Stunde; aktiver Plan Mo/Di | 1. `DELETE /api/teachers/{Krause}` 2. `DELETE /api/teachers/{Meyer}` 3. `PUT /api/teachers/{Meyer}` mit `active:false` (übrige Felder gleich) 4. `GET /api/timetable/week?date=2026-09-21&view=CLASS&id=1` 5. `GET /api/teachers` und `GET /api/substitutions/options?date=2026-09-21&lessonId=<8b-Mo-2>` | 1 → 204, Krause in keiner Liste; 2 → 409 `in-use`, `detail` „Meyer ist in 5 Stunden eingeplant. Deaktivieren statt löschen."; 3 → 200 `active:false`; 4 → die vier Montagsstunden weiter mit `teacherShort MEY`; 5 → Meyer `active:false` (Web bietet ihn im Lehrkraft-Select der Klassenfächer und in den Optionen nicht an; Optionen enthalten ihn nicht). Rücknahme: `active:true` | masterdata, substitution | backend1 (1–3, 5a) / backend2 (4) / backend3 (5b) | `TeacherServiceIT.tW1_3_deleteKrause_deactivateMeyer_lessonsKeepMeyer_notSelectable` |
| **T-W1.4** | Weber unterrichtet nur Deutsch; 9c Ma bei Schulz | G-10 | genau eine Warnung `SUBJECT_NOT_TAUGHT` mit Weber und Mathematik im Text; Speichern nicht verhindert; nach Zurücksetzen `warnings: []` | masterdata | backend1 | `WarningCheckerTest.tW1_4_subjectNotTaught_exactlyOneWarning_resetRemovesIt` |
| **T-W1.5** | Schulz Deputat 2, mit 1 Stunde (9c) verplant; 7a Ma 2 Std. bei Meyer | G-11 | genau eine Warnung `QUOTA_EXCEEDED`: Schulz, Deputat 2, jetzt 3; Speichern nicht verhindert; nach Zurücksetzen keine Warnung | masterdata | backend1 | `WarningCheckerTest.tW1_5_quotaExceeded_3of2_exactlyOneWarning` |
| **T-W2.1** | 7a hat Mo genau drei Stunden (Pos. 1, 2, 4); Regel max/Tag Klasse = 6 aktiv; Entwurf `COPY_ACTIVE` | 1. `PUT /api/rules/{maxDayClass} {"value":3,"severity":"SOFT","weight":1,"active":true}` 2. `GET /api/rules` 3. `POST /api/draft/lessons {"classId":1,"subjectId":2,"timeslotId":5,"roomId":1}` (Mo 4. Stunde = Pos. 5, Deutsch, R101; Weber und R101 frei) | 2 → Wert 3; 3 → 201 mit genau einem Konflikt `RULE_VIOLATION`, `severity SOFT`, `ruleId` = diese Regel, `objects[0]` = Klasse 7a, `message` nennt „Maximale Stunden pro Tag", **3**, **7a**, **Montag** („… (3) überschritten: Klasse 7a, Montag: 4 Stunden"); alle vier Montagsstunden gespeichert. Rücknahme: Wert 6, `DELETE /api/draft` | rules, planning | backend2 | `ConflictCheckerTest.tW2_1_maxPerDay3_violationNamesRuleValueClassAndDay_fourLessonsKept` |
| **T-W3.1** | R101 Mo 1. durch 7a belegt; 9c Mo 1. frei; Schulz frei; Entwurf `COPY_ACTIVE` | P-2 | genau ein Konflikt `ROOM_DOUBLE_BOOKED`, R101, beide Stunden in `lessonIds`; kein `TEACHER_DOUBLE_BOOKED`; Stunde gespeichert mit `SCZ` | planning | backend2 | `ConflictCheckerTest.tW3_1_roomDoubleBooked_reportsExactlyOneConflict`, `TimetableConstraintProviderTest.tW3_1_roomDoubleBooked_penalizesExactlyOnce` |
| **T-W3.2** | T-W3.1 ausgeführt; R101 Mo ab 3. Stunde frei | P-3 | Stunde bei Pos. 6 mit unveränderten Fach/Lehrkraft/Raum; Pos. 1 der 9c frei; `conflicts: []` (auch an der 7a-Stunde). Rücknahme: `DELETE /api/draft` | planning | backend2 | `ConflictCheckerTest.tW3_2_moveLesson_conflictGoneOnBoth` |
| **T-W4.1** | Testdaten vollständig, aktiver Plan Mo/Di, keine Konflikte, keine Ereignisse | P-7 | wie P-7 | substitution (WeekResolver) | backend2 | `WeekIT.tW4_1_classViewShowsTheLessonsOfThatClass`, `.tW4_1_teacherViewShowsOnlyThatTeacher`, `.tW4_1_roomViewShowsOnlyThatRoom`, `.tW4_1_schoolViewShowsEverything` (die vier Sichten, inkl. ROOM) |
| **T-W5.1** | kein Ereignis erfasst; Meyer Mo 4, Di 1 | V-1 | wie V-1: genau vier `NEEDS_SUBSTITUTION` am 21.09. (7a Pos. 1 und 4, 8b Pos. 2 und 5), 7a Pos. 2 nicht; 22.09. keine; Regelplan unverändert | substitution | backend3 | `WeekResolverTest.tW5_1_absence21st_fourPending_noneOn22nd_regularPlanUnchanged` |
| **T-W6.1** | T-W5.1 ausgeführt; Sperrung R101 21.09. „Wasserschaden" erfasst (V-2); R204 Mo 1. frei; Schulz frei, Mathe | V-3, V-4 | Optionen: Schulz ja/Meyer nein, R204 ja/R101 nein; nach dem Speichern 21.09. 7a Pos. 1 = Schulz/R204 `SUBSTITUTED`, `conflicts: []`; Regelplan (`GET /api/timetable`) weiter Meyer/R101 | substitution | backend3 | `SubstitutionOptionsTest.tW6_1_listsSchulzNotMeyer_R204NotR101`, `SubstitutionServiceIT.tW6_1_assign_markedAsSubstitution_noConflict_regularPlanKeepsMeyerR101` |
| **T-W7.1** | T-W6.1 erfolgreich; keine weitere Umplanung | V-6 | genau ein Eintrag mit allen genannten Feldern und **beiden** Anlässen „Krankmeldung" und „Wasserschaden"; kein Endpunkt zum Ändern/Löschen (`DELETE`/`PUT` auf `/api/changelog…` → 404/405, nie 2xx) | changelog | backend2 / backend3 | `ChangelogIT.tW7_1_filter7a_exactlyOneEntryWithBothReasons`, `ChangelogControllerTest.noDeleteOrPutMapping` |
| **T-W8.1** | FULL geladen (D-1); aktiver Plan leer (S-1) | S-2 bis S-4 | wie S-2 bis S-4; Laufzeit notieren | planning, solver, sampledata | backend2 | `SolveServiceIT.tW8_1_fullSample_terminatesByLimit_noHardConflict_everyLessonAssignedOrOpen_activeEmptyUntilAccept` (voller Bestand, Rechenzeit 10 s) und `SolveServiceIT.tW8_1_everyLessonAssignedOrOpen_noDoubleBooking_activePlanEmptyUntilAccept` (Pflichtenheft-Bestand, 2 s); Solver-Seite: `SmallDatasetSolveTest.tW8_1_smallDataset_noHardConflict_everyLessonAssigned`, `FullDatasetSolveIT.fullDataset_tenSeconds_noHardConflict` (360 Stunden, 10 s), `SolverRunnerIT.start_isAsync_endsByConfiguredLimit_resultBeforeNotSolving` (Rechenzeit aus SystemConfig), `SolverRunnerIT.cancel_deliversBestSoFar` |
| **T-W9.1** | Vorschlag nach T-W8.1 liegt vor, **nicht** angenommen (vor S-4); ≥ 3 Stunden, keine festgelegt | 1. eine Stunde `x` mit `pinned false` wählen, Lage notieren 2. `PATCH /api/draft/lessons/{x} {"timeslotId": <Zielzeitfenster>}` — Zielzeitfenster ist ein **freies** Zeitfenster derselben Klasse; nach S-2 auf dem vollen Bestand ist keines frei (12 Klassen × 30 Zeitfenster sind belegt), dann das Zeitfenster einer **ungepinnten** Stunde `y` derselben Klasse nehmen 3. Antwort: `pinned` und `conflicts` ablesen 4. zwei weitere ungepinnte Stunden `y`, `z` notieren 5. `POST /api/draft/solve {}`, warten, `GET /api/draft` 6. `PATCH /api/draft/lessons/{x} {"pinned":false}` | 2 → `x` im neuen Zeitfenster; 3 → `pinned true`, beim belegten Zielzeitfenster zusätzlich `CLASS_DOUBLE_BOOKED` (je nach Lage auch `ROOM_`/`TEACHER_…`) in `conflicts`; 5 → Start ist **202**, auch mit diesem Konflikt (nur festgelegte Stunden zählen für `pinned-contradiction`); nach dem Lauf `x` unverändert (gleiches `timeslotId`, `roomId`), `y` liegt anderswo, `z` darf anders liegen, keine `*_DOUBLE_BOOKED`-Konflikte mehr; 6 → `pinned false` | planning, solver | backend2 | `SolveServiceIT.tW9_1_pinnedLessonSurvivesResolve_unpinRemovesFlag` (freies Zeitfenster) und `SolveServiceIT.tW9_1_moveOntoOccupiedSlotOfSameClass_pinnedStays_otherOneMoves` (belegtes Zeitfenster); Solver-Seite: `SmallDatasetSolveTest.tW9_1_pinnedLessonsStay_othersGetAssigned` |
| **T-A1.1** | Testdaten, **keine** Ereignisse (vor V-1 oder nach `DELETE /api/events/{1,2}` und `DELETE /api/exceptions/1`); Konto `meyer` mit Rolle TEACHER | A-1, Login meyer, `GET /api/me/timetable/week?date=2026-09-21` | Montag genau vier `REGULAR`-Einträge: Pos. 1 Ma 7a R101 · Pos. 2 Ma 8b R204 · Pos. 4 Ph 7a P1 · Pos. 5 Ma 8b R204 (Zeiten aus `GET /api/timegrid`: 08:00–08:45, 08:50–09:35, 09:55–10:40, 10:45–11:30); 7a Pos. 2 (Weber) fehlt; Woche genau fünf Einträge; `GET /api/timetable` mit Meyer-Token → 403; ohne Token → 401 | substitution, auth | backend2 | `MyWeekIT.tA1_1_teacherSeesOnlyHerOwnWeek` (vier am 21.09., eine am 22.09., fünf in der Woche) und `MyWeekIT.tA1_1_teacherMayNotReadThePlanningEndpoints` (403/401); Android `TimetableViewModelTest` (Wochenabruf, 17 Fälle) — der Geräteanteil ist **offen** |
| **T-A2.1** | T-W6.1 ausgeführt; Konten `schulz` und `meyer` | A-4, A-5 | Schulz: zusätzlicher Eintrag Pos. 1 `SUBSTITUTED`, 7a, R204, 08:00–08:45, `reasons` und `original.teacherShort MEY`; Meyer: Pos. 1 `GIVEN_AWAY`, drei weitere `NEEDS_SUBSTITUTION`; 28.09.: beide nur `REGULAR` | substitution | backend3 | `WeekResolverTest.tA2_1_kinds_substitutedForSchulz_givenAwayAndPendingForMeyer_regularOn28th`; Android `EntryKindUiTest` (Kennzeichnung SUBSTITUTED/GIVEN_AWAY, 9 Fälle) — der Geräteanteil ist **offen** |
| **T-A3.1** | App installiert und eingerichtet; Konto `meyer` | K-1, K-2 | Warnung, Speichern auf Bestätigung, Adresse überlebt Neustart, Fehlermeldung statt Absturz; danach Plan wieder da; keine Neuinstallation | android; Server: `GET /api/health` | android (Server-Anteil backend1) | Android: **kein Autotest** für den Fluss „Verbindung prüfen → Warnung → Trotzdem speichern → Neustart"; `ApiFactoryTest`/`OfflineCacheTest` decken nur Adressprüfung und Zwischenspeicher. Geräteanteil **offen** (T-608). Web-Vorschau `/app-demo` (Mock) |

## 3. Matrix: Testfall → Phase → Endpunkte → Autotest

| Testfall | Phase | Endpunkte (Vertrag v1) | Autotest-Ebene (testen.md) |
|---|---|---|---|
| T-W1.1 | backend1 | `POST/GET /api/teachers` | 3 |
| T-W1.2 | backend2 | `POST /api/draft`, `POST /api/draft/lessons` | 1 (+2) |
| T-W1.3 | backend1/2/3 | `DELETE/PUT /api/teachers/{id}`, `GET /api/timetable/week`, `GET /api/substitutions/options` | 3 |
| T-W1.4, T-W1.5 | backend1 | `PUT /api/classes/{id}/subjects` | 1 |
| T-W2.1 | backend2 | `PUT /api/rules/{id}`, `POST /api/draft/lessons` | 1 |
| T-W3.1, T-W3.2 | backend2 | `POST /api/draft/lessons`, `PATCH /api/draft/lessons/{id}` | 1 (+2) |
| T-W4.1 | backend2 | `GET /api/timetable/week` (vier Sichten) | 3+4 |
| T-W5.1 | backend3 | `POST /api/events`, `GET /api/timetable/week` | 1 |
| T-W6.1 | backend3 | `GET /api/substitutions/pending`, `GET /api/substitutions/options`, `POST /api/exceptions` | 1+3 |
| T-W7.1 | backend2/3 | `GET /api/changelog` | 3+4 |
| T-W8.1 | backend2 | `POST /api/sample-data`, `POST /api/draft`, `POST /api/draft/solve`, `GET /api/draft`, `POST /api/draft/accept`, `GET /api/timetable` | 2+3 |
| T-W9.1 | backend2 | `PATCH /api/draft/lessons/{id}`, `POST /api/draft/solve` | 3 |
| T-A1.1 | backend2 | `POST /api/users`, `POST /api/auth/login`, `GET /api/me/timetable/week`, `GET /api/timetable` (403) | 4+5 |
| T-A2.1 | backend3 | `GET /api/me/timetable/week`, `GET /api/timegrid` | 1+5 |
| T-A3.1 | android | `GET /api/health` | 5 + manuell |

## 4. Abnahme des Vollausbaus — Reihenfolge und Protokoll

1. `scripts/ci-all.sh` grün (Lint, `test` + `integrationTest`, Frontend-Build, Images) — Ausgabe anhängen.
2. Frische Datenbank, Abschnitt 1 vollständig per API (Skript `scripts/abnahme.sh`, das die Aktionen dieser Datei in
   der Reihenfolge ausführt und jede Erwartung mit `jq` prüft — entsteht in Phase backend3, Ergebnis als Tabelle).
3. Abschnitt 1 einmal im Browser (Playwright-Durchlauf `frontend/scripts/abnahme.spec.ts`, Screenshots je Schritt —
   Phase frontend; **erledigt 05.09.**, Ergebnis `docs/smoke/PROTOKOLL.md`. Nicht im Browser prüfbar: I-1, I-2, K-3,
   K-4 (Deployment, in `docs/abnahme/backend1-*.md` und `config.md` belegt), T-W9.1 Teil „Neuberechnung lässt die
   festgelegte Stunde stehen" (nur per API/`SolveServiceIT`), A-2/A-3/A-6 als reine API-Zeilen (`backend3.md`)).
4. Protokoll: je Zeile `# · Datum · Prüfer · Ergebnis (ok / abweichend: Text) · Beleg (Datei/Screenshot)`. Ein Schritt
   ohne Beleg gilt als nicht geprüft.

Offen (nicht im Vollausbau prüfbar): Android-Schritte I-4, I-5 (App), K-1, K-2 und die App-Anteile von T-A1.1,
T-A2.1, T-A3.1 — im Repo `android-app` gegen die VM (Rolle 6, T-608); CAU-Runner und VM-Deploy (T-703, T-705).

**Bisherige Belege** (jede Datei nennt Befehl und Ausgabe, nicht nur das Ergebnis):

| Datei | Umfang |
|---|---|
| `docs/abnahme/planning.md` | P-1 bis P-6, D-4, D-5, S-1 bis S-4, T-W1.2, T-W3.1, T-W3.2, T-W8.1 (Entwurf/Annehmen), T-W9.1 |
| `docs/abnahme/solver.md` | Constraints, Übersetzer, Läufe auf kleinem und vollem Bestand |
| `docs/abnahme/backend1-masterdata.md`, `auth.md`, `rules.md`, `config.md`, `pruefung-backend1-nacharbeit.md` | G-1 bis G-11, L-1, L-2, R-1 bis R-3, I-1 bis I-3 |
| `docs/abnahme/backend2-nacharbeit.md` | **D-1 mit gefülltem Plan, V-6, T-W7.1, P-7, T-W4.1, A-2, A-3, T-A1.1**, Zeitzone, Nebenläufigkeit — Nacharbeit 05.09. |
| `docs/abnahme/backend3.md` | **V-1 bis V-6, A-1 bis A-6, P-7, T-W4.1, T-W5.1, T-W6.1, T-W7.1, T-A1.1, T-A2.1, T-110** sowie Verlegung, Entfall, Zusatzstunde und 409 `exception-exists` — Phase backend3; nach der Nacharbeit vom 05.09. **83 Zusicherungen**, wiederholbar mit `scripts/abnahme.sh` |
| `docs/smoke/PROTOKOLL.md` + `docs/smoke/*.png` | **Browser-Durchlauf (Phase frontend, 05.09.)**: I-3, L-1, L-2, G-1 bis G-11, R-1 bis R-3, P-1 bis P-7, T-W1.1 bis T-W1.5, T-W2.1, T-W3.1, T-W3.2, T-W4.1, T-W5.1, T-W6.1, T-W7.1, V-1 bis V-6, A-1, E-38/T-A1.1 und T-A2.1 (Lehrkraft im Web), D-1 bis D-5, S-1 bis S-4, T-W8.1, T-W9.1 (Festlegen/Aufheben) und **W-4 Druckansicht** (Seitenleiste ausgeblendet, alle fünf Tage auf A4, Kopfzeile Sicht/Objekt/Woche, PDF `docs/smoke/W-4-druck-A4.pdf`) — `frontend/scripts/abnahme.spec.ts` gegen nginx `:1500` mit Chrome headless 1440×900; jede Zeile mit Screenshot |
| `docs/abnahme/changelog.md` | **V-6 und T-W7.1 vollständig** (Filter 7a mit beiden Anlässen, alle übrigen Filter, Paginierung, `DELETE`/`PUT`/`PATCH`/`POST` → 404/405, 400er, 401, 403) · Skript `scripts/abnahme-changelog.py`. Der Eintrag `EXCEPTION_CREATED` entsteht seit dem 05.09. über `POST /api/exceptions`; der Platzhalter mit `psql` ist eingelöst |

## 5. Abnahmedurchlauf 05.09. — frischer Klon, so wie die Betreuenden am 24.09.

**Ergebnis: nicht bestanden** (offene Muss-Schritte: Android-App I-4/I-5/K-1/K-2 und die App-Anteile von T-A1.1, T-A2.1,
T-A3.1; dazu liefert `git clone` heute nur den Starter). Alles Web- und Server-seitige aus Abschnitt 1 und alle 17
Testfälle in ihrem Server-/Web-Anteil sind **bestanden**, jede Zeile mit Beleg. Prüfer: Claude (Subagent Abnahme), 08:00–08:15.

**Ablage der Belege** (alles unter `08-Build/_scratch/plattform/abnahme/`, im Folgenden `ABN/`):

| Datei | Inhalt |
|---|---|
| `ABN/klon-nur-commits/` | Ergebnis von `git clone …/08-Build/plattform`: nur `backend/ frontend/ LICENSE README.md` — kein `deployment/` |
| `ABN/web-app/` | rsync des Arbeitsbaums (ohne `.git`, `node_modules`, `build`, `dist`, `deployment/.env`), Grundlage des Durchlaufs |
| `ABN/compose-up.log`, `ABN/I-2-compose-ps-und-log.txt` | `docker compose up -d` (22 s mit Build-Cache), `compose ps` (vier Dienste `Up`, db/backend `healthy`), Backend-Log (6 Migrationen, `Started BackendApplication`, Konto `admin` angelegt) |
| `ABN/abnahme-api.py`, `ABN/api-ergebnis-vor-neustart.md`, `ABN/api-log-vor-neustart.md` | API-Durchlauf Abschnitt 1.2–1.6, 1.9, T-W1.3, T-W2.1, T-A1.1, Zugriffsschutz, Hash; 43/43 Prüfungen (die eine Abweichung im Ergebnisfile war ein Skriptfehler — Uhrzeit steht nicht im Wocheneintrag, sondern in `/api/timegrid`; nachgeprüft in `ABN/T-A2.1-timegrid-schulz.txt`); Log = jede Anfrage mit Antwort |
| `ABN/api-ergebnis-nach-neustart.md` | K-3, D-1 bis D-5, S-1 bis S-4, T-W8.1, T-W9.1 nach `docker compose down && up -d` |
| `ABN/browser-run.log`, `ABN/browser-PROTOKOLL.md`, `ABN/shots/01…56-*.png`, `ABN/shots/W-4-druck-A4.pdf` | Browser-Durchlauf `frontend/scripts/abnahme.spec.ts` (Chrome headless 1440×900, unverändert bis auf Ausgabeordner und Host-Zähler) auf leerer Datenbank: **43/43 grün in 1,4 min**, 0 Seitenfehler, alle 399 Anfragen des Browsers an `localhost:1510` (keine externe Adresse) |
| `ABN/shots/I-3-login.png`, `ABN/shots/K-4-port-1513.png` | Chrome-headless-Screenshots der Anmeldemaske unter dem Erst- und dem geänderten Port |

**Abweichungen zum Pflichtenheft-Text**, die der Prüfer kennen muss (nichts davon ein Fehler der Software, aber
`testdurchlauf.tex` muss laut eigener Pflegeregel nachziehen): Handaufbau nur im **Entwurf**, danach **Annehmen** (P-6),
sonst sehen W-4 bis W-7 und die Lehrkraft nichts · P-5b (9c-Montagsstunde entfernen), sonst stimmt T-W4.1 „R101 ab
der 3. Stunde frei" nicht · T-W1.3 Schritt 5 „Auswahlliste der Lehrkräfte beim Einplanen" gibt es nicht (E-18: Lehrkraft
folgt aus der Fachzuordnung) — geprüft an der Lehrkraft-Auswahl der Klassenfächer und an den Vertretungsoptionen ·
Beispieldaten liegen unter Einstellungen → Beispieldaten · Ports hier 1510/1513 statt 1500 (zweiter Stack neben dem
Dev-Stack; `-p abnahme`, `REGISTRY_IMAGE=abnahme`).

### 5.1 Testdurchlauf (Kapitel „Testdurchlauf"), je Schritt

| # | Ergebnis | Beleg |
|---|---|---|
| I-1 Klonen, `cd deployment`, `.env` | **nicht bestanden** — `git clone` liefert nur den Starter, `deployment/` fehlt (`ABN/klon-nur-commits/`). Mit rsync-Kopie: `cp -n .env.example .env` **genügt seit dem 05.09. nicht mehr** — mit dem Platzhalter `bitte-aendern` verweigert `AdminBootstrap` im Profil `prod` den Start (SICHERHEIT-6); ein eigener Wert ist Pflicht | `ABN/klon-nur-commits/`, `ABN/web-app/deployment/.env` |
| I-2 `docker compose up -d` | ok — vier Container `Up`, keiner beendet; damals 6 Migrationen (seit V7/V8 sind es **8**), `Started BackendApplication in 7.7 s`; `up` kehrte nach 22 s zurück (Abbilder aus dem Build-Cache; Erstbau ohne Cache **ungeprüft**). **Seit der Korrektur vom 05.09. sind es drei Container** — die Attrappe `mock` ist nach `docker-compose.dev.yml` gewandert; neu gemessen in `docs/abnahme/korrektur-05-09.md` | `ABN/compose-up.log`, `ABN/I-2-compose-ps-und-log.txt`, `docs/abnahme/korrektur-05-09.md` |
| I-3 Browser `/login` | ok — 200, Anmeldemaske mit Schulname aus `/api/health` | `ABN/shots/I-3-login.png`, `01-I_3.png` |
| I-4, I-5 App installieren, Adresse | **offen am Gerät** — `app/stundenplan.apk` liegt seit dem 05.09. vor (33.380.315 Byte, **Debug-Signatur**, 64 Unit-Tests grün), ist aber auf keinem Gerät und in keinem Emulator installiert worden (T-608); Server-Anteil `GET /api/health` → `{"status":"UP","schoolName":"Gymnasium Musterstadt"}` ok | `ABN/I-2-compose-ps-und-log.txt` |
| L-1 Anmelden admin | ok — Übersicht mit Grunddaten, Regelwerk, Aktiver Plan, Entwurf, Vertretung, Änderungsverlauf | `03-L_1.png`; API `api-ergebnis-vor-neustart.md` L-1 |
| L-2 falsches Kennwort | ok — 401 `unauthenticated`, „Benutzername oder Passwort ist falsch." | `02-L_2.png`; API L-2 |
| G-1 Fächer | ok — genau drei, Physik mit Raumanforderung | `05-G_1.png`; API G-1 |
| G-2 Räume | ok — genau drei | `06-G_2.png`; API G-2 |
| G-3 Raster | ok — 35 Zeitfenster, Pause je Tag an Position 3, fünf Tage mit „6 Stunden · 1 Pause" | `04-G_3.png`; API G-3 |
| G-4/5/6 Lehrkräfte | ok — Schulz erneut geöffnet: SCZ, 2, Mathematik, Fr 5./6. gesperrt (**T-W1.1**) | `07-G_6.png`, `08-T_W1_1.png`; API G-6 |
| G-7 Klassen | ok — 7a: Ma 2 Wochenstunden bei Meyer | `09-G_7.png`; API G-7 |
| G-8 Deutsch ohne Angaben | ok — 400 mit `subjects[1].weeklyHours` und `subjects[1].teacherId`, Liste unverändert | `10-G_8.png`; API G-8 |
| G-9 Mathematik löschen | ok — 409 „Mathematik wird in 7a, 8b, 9c unterrichtet." | `11-G_9.png`; API G-9 |
| G-10 Schulz→Weber | ok — genau eine Warnung SUBJECT_NOT_TAUGHT, nach Zurücksetzen keine (**T-W1.4**) | `12-T_W1_4.png`; API G-10 |
| G-11 Meyer→Schulz | ok — „Schulz ist mit 3 Wochenstunden über dem Deputat von 2.", danach keine (**T-W1.5**) | `13-T_W1_5.png`; API G-11 |
| R-1 Regeln 6 / 30 aktiv | ok | `14-R_1.png`; API R-1 |
| R-2 Wert 0 | ok — 400 „muss mindestens 1 sein", 6 bleibt | `15-R_2.png`; API R-2 |
| P-1 Regelplan Mo/Di | ok — 7a Mo drei, Di eine; 8b Mo drei; keine Konflikte; 9c Ma offen | `18-P_1.png`; API P-1 |
| P-2 9c Mo 1. in R101 | ok — Schulz automatisch; genau ein Konflikt „Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c)" mit beiden Stunden (**T-W3.1**) | `19-T_W3_1.png`; API P-2 |
| P-3 auf Mo 5. verschieben | ok — Konflikt an beiden Stunden weg, Angaben unverändert (**T-W3.2**) | `20-T_W3_2.png`; API P-3 |
| P-4 9c Fr 5. | ok — „Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde" (**T-W1.2**) | `21-T_W1_2.png`; API P-4 |
| P-5 / P-5b löschen, P-6 annehmen | ok — Entwurf sieben Stunden, angenommen, `GET /api/draft` 404, aktiver Plan 7 | `22-P_5b.png`, `23-P_6.png`; API P-5, P-6 |
| P-7 Sichten | ok — Klasse/Lehrkraft/Schule/Raum zeigen dieselbe Stunde; Meyer Mo vier, Di eine; R101 Mo 1.+2. belegt, ab 3. frei (**T-W4.1**) | `24…27-T_W4_1_*.png`; API P-7 |
| V-1 Ausfall Meyer 21.09. | ok — genau vier Stunden vertretungsbedürftig, 22.09. keine, Weber-Stunde regulär (**T-W5.1**) | `31-T_W5_1.png`, `32-T_W5_1_plan.png`; API V-1 |
| V-2 Sperrung R101 | ok — zwei zusätzlich betroffen, 7a 1. Stunde mit „Krankmeldung; Wasserschaden" | `33-V_2.png`; API V-2 |
| V-3 offene Stunde öffnen | ok — Schulz ja (Mathematik, erster), Meyer nein; R204 ja, R101 nein, in derselben Ansicht | `34-T_W6_1_optionen.png`; API V-3 |
| V-4 zuweisen | ok — Schulz/R204 als Vertretung, „ohne Folgekonflikt", nächste offene Stunden bleiben angeboten (**T-W6.1**) | `35-T_W6_1_zugewiesen.png`; API V-4 |
| V-5 28.09. | ok — wieder Meyer/R101, regulär | `36`, `37-V_5_28_09.png`; API V-5 |
| V-6 Verlauf, Filter 7a | ok — genau ein Eintrag mit Datum, Zeitpunkt, beiden Anlässen, Meyer/R101 → Schulz/R204, `admin`; `DELETE`/`PUT /api/changelog/1` → 404 (**T-W7.1**) | `38-T_W7_1.png`; API V-6 |
| D-1 Vollständige Schule | ok — Rückfrage „Ersetzen?", dann 12/24/18 in 10,8 s, 360 Stunden im aktiven Plan | `46-D_1_rueckfrage.png`, `47-D_1.png`; API D-1 |
| D-2 / D-3 Sichten voller Bestand | ok — Raster vollständig; Ladezustand ist ein Skelett-Raster (`WeekGridSkeleton`, `aria-busy`) — im Screenshot nicht eingefangen, im Code belegt | `48-D_2.png`, `49-D_3.png`; API D-2/D-3 |
| D-4 Stunde auf belegtes Fenster | ok — Konflikt sofort nach dem Ablegen (Drag-and-Drop), benennt Raum/Lehrkraft/Klasse | `44-D_4.png`; API D-4 |
| D-5 zurücknehmen | ok — Verwerfen, aktiver Plan wie nach D-1 (Lage je Stunde verglichen) | `45-D_5.png`; API D-5 |
| S-1 aktiven Plan leeren | ok — 0 Stunden, 12 Klassen bleiben | `50-S_1.png`; API S-1 |
| S-2 Vorschlag erzeugen | ok — Fortschrittsbalken „0 s / 10 s", Ende nach 10,7–10,9 s bei Rechenzeit 10 s; 357 belegt / 3 offen (Liste), belegt-oder-offen ohne dritten Zustand, keine Doppelbelegung (eigene Prüfung Raum/Lehrkraft/Klasse: 0); weiche Verstöße einzeln (**T-W8.1**) | `51-S_2_laeuft.png`, `52-T_W8_1_berechnet.png`; API S-2 |
| S-3 aktiver Plan ohne Annehmen | ok — weiter leer | `53-S_3.png`; API S-3 |
| S-4 annehmen | ok — aktiver Plan = Vorschlag (belegte Stunden identisch); **die 3 offenen Stunden stehen mit `timeslotId: null` im aktiven Plan** (Befund 6) | `55-S_4.png`; API S-4 |
| A-1/A-2 App Meyer, Schulz | **offen** (App); Server-/Web-Anteil ok: Meyer 21.09. vier Einträge, 1. Stunde abgegeben, drei „noch nicht vertreten", Weber-Stunde fehlt; Schulz sieht Vertretung 7a/R204/8:00–8:45 mit Anlass und MEY; Lehrkraft hat im Web nur „Mein Plan", `/plan` leitet um; Schreibzugriffe mit Lehrkraft-Token 403 (**T-A1.1, T-A2.1** Server/Web) | `39…43-*.png`; API T-A1.1, A-2…A-6 |
| K-1 / K-2 App-Adresse | **offen** (App) | — |
| K-3 `down` / `up -d` | ok — alle Zählstände, die Ereignisse, Konten und der Verlaufseintrag unverändert; Login meyer weiter möglich | `api-ergebnis-nach-neustart.md` K-3 |
| K-4 Port ändern | ok — `WEB_PORT=1513` in `.env`, `docker compose up -d` (nur `web` neu erstellt, 1 s), `/login` 200, 1510 tot, Daten da; keine Quelltextänderung, kein Build | `ABN/shots/K-4-port-1513.png` |

### 5.2 Die 17 Testfälle

| Testfall | Ergebnis | Beleg |
|---|---|---|
| T-W1.1 | ok | `08-T_W1_1.png`; API G-6 |
| T-W1.2 | ok | `21-T_W1_2.png`; API P-4 |
| T-W1.3 | ok — Krause weg (204); Meyer 409 „in 5 Stunden eingeplant. Deaktivieren statt löschen." → deaktiviert; Montagsstunden weiter MEY; nicht mehr wählbar (Klassenfächer, Vertretungsoptionen) | `28-T_W1_3_in_use.png`, `29-T_W1_3_nicht_waehlbar.png`; API T-W1.3 |
| T-W1.4 | ok | `12-T_W1_4.png`; API G-10 |
| T-W1.5 | ok | `13-T_W1_5.png`; API G-11 |
| T-W2.1 | ok — „Maximale Stunden pro Tag (3) überschritten: Klasse 7a, Montag: 4 Stunden", weich, vier Stunden gespeichert | `30-T_W2_1.png`; API T-W2.1 |
| T-W3.1 | ok | `19-T_W3_1.png`; API P-2 |
| T-W3.2 | ok | `20-T_W3_2.png`; API P-3 |
| T-W4.1 | ok | `24…27-T_W4_1_*.png`; API P-7 |
| T-W5.1 | ok | `31-T_W5_1.png`; API V-1 |
| T-W6.1 | ok | `34`, `35-T_W6_1_*.png`; API V-3, V-4 |
| T-W7.1 | ok | `38-T_W7_1.png`; API V-6 |
| T-W8.1 | ok (Laufzeit 10,7–10,9 s bei 10 s Rechenzeit; 3 offene Stunden in der Liste; Vorbehalt Befund 6) | `51`, `52`, `53`, `55`; API S-2…S-4 |
| T-W9.1 | ok — verschobene Stunde festgelegt und nach erneuter Berechnung unverändert; zwei andere gewandert; keine Doppelbelegung; Festlegung aufgehoben | `54-T_W9_1_pin.png`; API T-W9.1 |
| T-A1.1 | Server/Web ok — App **offen** | `40-T_A1_1_meyer.png`, `41-E_38_redirect.png`; API T-A1.1 |
| T-A2.1 | Server/Web ok — App **offen** | `42-T_A2_1_schulz.png`, `43-T_A2_1_28_09.png`; API T-A2.1 |
| T-A3.1 | **offen** (App); Server-Anteil `/api/health` ok | — |

### 5.3 Nichtfunktionale Musskriterien

| Kriterium | Ergebnis | Beleg |
|---|---|---|
| Zugriffsschutz | ok — Lehrkraft-Token: fremde Lehrkraftsicht, Klassensicht, `/api/users`, `/api/teachers`, `/api/changelog`, `POST /api/draft` → 403; ohne/mit unsinnigem Token → 401; Spring-Standardbenutzer aus dem Log per Basic-Auth → 401 | API NF-Zugriff, NF-Zugriff-Basic, A-6 |
| Passwörter nur als Hash | ok — `user_account.password_hash` beginnt mit `$2a$10$` (60 Zeichen, BCrypt), `auth_token.token_hash` 64 Hex; kein Klartext in DB oder Backend-Log. Das früher gemeldete `Using generated security password: <uuid>` (Befund 3) ist seit dem 05.09. weg, neu gemessen (0 Treffer) | API NF-Hash; `ABN/I-2-compose-ps-und-log.txt`; `docs/abnahme/korrektur-05-09.md` |
| Rückmeldung des Systems | ok — Fortschrittsbalken mit Sekunden bei der Berechnung; Skelett-Raster beim Aufbau der Plansicht; Konfliktprüfung antwortet unmittelbar | `51-S_2_laeuft.png`; `frontend/src/components/week-grid/WeekGridSkeleton.tsx` |
| Dauerhaftigkeit | ok | K-3 |
| Bedienbarkeit Leitszenario | ok — Ereignis, offene Stunden, Optionen und Zuweisung auf einer Seite; Meldungen in Schulsprache | `34`, `35` |
| Nachvollziehbarkeit | ok — Eintrag mit Zeitpunkt, Anlass, Konto; kein Ändern/Löschen (404) | `38`; API V-6 |
| Eigenständiger Betrieb | ok — Fonts gebündelt (`inter-*.woff2` in `assets/`), im Bundle keine externe Lade-Adresse; Browser sprach im ganzen Durchlauf nur `localhost:1510` | `ABN/browser-PROTOKOLL.md` (Hosts), `docker exec abnahme-web-1 ls assets` |
| Inbetriebnahme in einem Vorgang | ok (mit rsync-Kopie; per `git clone` heute nicht) | I-1, I-2 |
| Ortsunabhängigkeit | ok — Port über `.env`, API relativ `/api` | K-4 |

### 5.4 Befunde (das Schwerste zuerst)

**Stand nach der Phase Korrektur (05.09., 09:00) — Belege in `docs/abnahme/korrektur-05-09.md`:**
3, 6 und 8 sind **erledigt und neu gemessen**; 4 ist zur Hälfte erledigt (Warnung im Log steht, der
Eintrag in `testdurchlauf.tex` liegt im anderen Repository); 2 ist **teilweise** bearbeitet — das
Gerüst baut jetzt nachweislich, die App-Musskriterien bleiben Bauphase W3; 5 ist als fertiger
Ersatztext in `docs/PFLICHTENHEFT-NACHTRAG.md` vorbereitet; 1 ist bewusst offen (Commit ist Philipps
Entscheidung).

**Stand nach dem Korrekturlauf zum Audit (05.09., 18:00) — Belege im Audit
`07-Konzept/audit/AUDIT-2026-09-05.md`, Abschnitt „Korrekturstand":** 7 ist **erledigt** (F-5a, siehe
unten); 4 ist erledigt, soweit es dieses Repository betrifft (im Profil `prod` bricht der Start jetzt
mit dem Platzhalter ab, statt nur zu warnen); 2 ist **teilerledigt** (APK gebaut, Geräteanteil offen).

1. **hoch** — Vollausbau ist nicht committet: `git clone` ergibt den Starter ohne `deployment/`, Schritt 1–2 des Testdurchlaufs sind für Betreuende nicht ausführbar. → Nach Philipps Prüfung auf einem Branch committen; vor der Liveschaltung im GitLab.
2. **hoch, teilerledigt** — Android-App: das APK liegt seit dem 05.09. (16:58) unter `app/stundenplan.apk` (33.380.315 Byte), 64 Unit-Tests in 6 Klassen grün, Lint „No issues found". **Offen bleibt der Geräteanteil:** I-4, I-5, K-1, K-2 und die App-Anteile von T-A1.1/T-A2.1/T-A3.1 sind auf keinem Gerät und in keinem Emulator nachgespielt worden, und das APK trägt die **Debug-Signatur** — für die Abgabe neu signieren. → Karte T-608 (Emulator-Lauf mit Screenshots nach `docs/smoke/`), danach diese Zeile und Abschnitt 2 nachziehen. Der frühere Grund (`composeBom 2026.08.00` verlangt AGP ≥ 9.1.0 und `compileSdk 37`) ist mit `2026.06.01` erledigt; `scripts/ci-android-build.sh` baut ohne Android Studio.
3. ~~**mittel** — Backend-Log beim Start: `Using generated security password: <uuid>`.~~ **Erledigt 05.09.:** `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)` (in Boot 4 liegt die Klasse unter `org.springframework.boot.security.autoconfigure`). Gemessen am neu gebauten Stack: 0 Treffer im Log.
4. **mittel, halb erledigt** — Admin-Kennwort. **Erledigt 05.09. (zweite Fassung):** Im Profil `prod` **bricht der Start ab**, wenn `ADMIN_INITIAL_PASSWORD` noch auf `bitte-aendern` steht (`app.admin-allow-placeholder-password=false`, `AdminBootstrap` wirft `IllegalStateException`). Die Profile `dev` und `ci` lassen den Platzhalter mit einer WARN-Zeile durch. Der dokumentierte Startweg in `ANLEITUNG.md` und `README.md` hat dafür seit dem 05.09. abends einen Kennwortschritt zwischen `cp -n` und `up`. **Offen:** Kennwort in `testdurchlauf.tex` Tabelle Abgabeangaben — anderes Repository, Vorschlag in `docs/PFLICHTENHEFT-NACHTRAG.md` P-5.
5. **mittel, vorbereitet** — `testdurchlauf.tex` weicht vom Produkt ab. Fertig formulierte Ersatztexte für alle sieben Stellen: `docs/PFLICHTENHEFT-NACHTRAG.md` (P-1 bis P-7). Die Datei selbst liegt im Repository `SoSe2026_WSP3_1` und wird von hier aus nicht angefasst.
6. ~~**mittel** — Annehmen übernimmt offene Stunden stumm.~~ **Erledigt 05.09.:** `POST /api/draft/accept` antwortet jetzt `warnings: ["3 Stunden bleiben unverplant: sie stehen im aktiven Plan, aber in keiner Woche."]`, und der Annehmen-Dialog nennt dieselbe Zahl **vor** dem Klick (aus `openLessons` des Entwurfs). Verworfen werden die Stunden nicht — ein angenommener Plan darf sie enthalten (PL-11). Beides gemessen; Screenshot `docs/smoke/K-annehmen-offene-stunden.png`.
7. ~~**mittel** — Sitzung nur im Seitenspeicher (F-5).~~ **Erledigt 05.09. (F-5a, Entscheidung des Auftraggebers):** Das Token liegt im `localStorage`, gilt 30 Tage (`AUTH_TOKEN_TTL`), Abmelden über das Benutzermenü. Neuladen führt nicht mehr zur Anmeldemaske. Der Nachtragspunkt P-6 in `docs/PFLICHTENHEFT-NACHTRAG.md` ist deshalb **zurückgezogen** — er beschrieb das alte Verhalten.
8. ~~**mittel** — Prism-Attrappe `mock` im Produktions-Compose.~~ **Erledigt 05.09.:** Der Dienst steht in `docker-compose.dev.yml`; `docker compose up -d` startet drei Dienste (db, backend, web), nach außen nur `web`. README, README-VM und Zeile I-2 nachgezogen; gemessen.
