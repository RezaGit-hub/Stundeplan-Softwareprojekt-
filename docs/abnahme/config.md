# Abnahme — Modul `config` und `sampledata` (Beispieldatenbestand)

Stand 05.09.2026, Vollausbau (`08-Build/plattform`, Branch `vollausbau`, nichts committet).
Gebaut: `GET /api/health`, `GET/PUT /api/config`, `POST /api/sample-data` samt Beispielbestand
(voll 12/24/18) und Pflichtenheft-Fixture (Meyer/Schulz/Weber, 7a/8b/9c, R101/R204/P1).

## 1. Wie die Belege entstanden sind

Kein JDK auf diesem Mac; alles lief in Containern (Lernnotizen 171–174):

| Schritt | Befehl |
|---|---|
| Übersetzen | `docker run --rm=true -v "$PWD/backend":/w -w /w -v stundenplan-gradle:/home/gradle/.gradle gradle:9.7.1-jdk21 gradle compileJava compileTestJava --no-daemon` |
| Tests | `scripts/ci-backend-test.sh --rerun` bzw. für die eigenen Klassen `gradle integrationTest --rerun --tests 'de.wsp3_1.config.ConfigIT' --tests 'de.wsp3_1.sampledata.*'` im selben Bild gegen `postgres:16-alpine` |
| Laufender Stack | `gradle bootJar`, dann `eclipse-temurin:21-jre java -jar /app/app.jar` neben `postgres:16-alpine` im Netz `stundenplan-abnahme`; `curl` aus einem Nachbarcontainer (`curlimages/curl`), **keine** veröffentlichten Ports |

Backend-Log beim Start: `Successfully applied 4 migrations to schema "public", now at version v4` ·
`Started BackendApplication in 8.786 seconds`. (Checkliste I-2 nennt fünf Migrationen — V5 gehört
nicht zu diesem Modul und fehlt am 05.09. noch.)

## 2. Belegte Zeilen der Checkliste

### I-5 — `GET /api/health`, ohne Token

```
$ curl -s -w '\n-> %{http_code}\n' $B/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}
-> 200
```

### Systemkonfiguration (Vorbedingung für R-3, Rechenzeit für S-2)

```
$ curl -s -H "Authorization: Bearer $T" $B/api/config
{"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":60}
-> 200

$ curl -s $B/api/config                                  # ohne Token
{"detail":"Bitte erneut anmelden: das Token fehlt oder ist abgelaufen.","status":401,
 "title":"Nicht angemeldet","type":"urn:pushnpull:problem:unauthenticated"}
-> 401

$ curl -s -X PUT -A -d '{"schoolName":"Gymnasium Musterstadt","state":"SH",
                         "schoolType":"GYMNASIUM","maxSolveSeconds":30}' $B/api/config
{"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":30}
-> 200

$ curl -s -X PUT -A -d '… "state":"XX" …' $B/api/config
{"detail":"Bitte die markierten Felder pruefen.","instance":"/api/config","status":400,
 "title":"Eingabe unvollstaendig","type":"urn:pushnpull:problem:validation",
 "errors":[{"field":"state","message":"muss eines der 16 Bundesland-Kuerzel sein"}]}
-> 400

$ curl -s -H "Authorization: Bearer $T" $B/api/config     # danach: 30 steht, SH unverändert
{"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":30}
-> 200
```

Eine Lehrkraft bekommt auf `/api/config` 403 — belegt im Integrationstest `ConfigIT`
(`.with(user("meyer").roles("TEACHER"))` gegen die echte Filterkette), nicht per curl: der
Beispielbestand legt bewusst keine Lehrkraft-Konten an.

### D-1 — `POST /api/sample-data`

```
$ curl -s -X POST -A -d '{}' $B/api/sample-data          # leere Datenbank
{"classes":12,"teachers":24,"rooms":18}
-> 200 in 0.257689s

$ curl -s -X POST -A -d '{}' $B/api/sample-data          # zweiter Aufruf
{"detail":"Die Datenbank enthält bereits Grunddaten. Zum Ersetzen „replace“ setzen.",
 "instance":"/api/sample-data","status":409,"title":"Konflikt",
 "type":"urn:pushnpull:problem:data-exists"}
-> 409

$ curl -s -X POST -A -d '{"replace":true}' $B/api/sample-data
{"classes":12,"teachers":24,"rooms":18}
-> 200 in 0.198199s

$ curl -s -X POST -H "Authorization: Bearer $T" $B/api/sample-data   # ganz ohne Rumpf
-> 409                                                   # Rumpf ist laut Vertrag optional

$ curl -s -X POST $B/api/sample-data                     # ohne Token
-> 401

$ curl -s -X POST $B/api/auth/login -d '{"username":"admin","password":"admin"}'
-> 200                                                   # Konto der Planung überlebt „replace"
```

Der geladene Bestand in der Datenbank (`psql` im Datenbankcontainer):

```
 klassen | lehrkraefte | raeume | zeitfenster | klasse_fach | regeln | konten
      12 |          24 |     18 |          35 |         144 |      5 |      1

 hoechstlast (Wochenstunden je Lehrkraft)   18     (Deputat 25 → keine Warnung)
 wochenstunden_je_klasse                    30     (genau der Wert der Regel MAX_LESSONS_PER_WEEK)
 room_type: Klassenraum 12 · Fachraum Physik 2 · Fachraum Chemie 1 · Sporthalle 2 · Musikraum 1
```

**Offen an D-1:** `GET /api/timetable` liefert nach dem Laden noch keinen gefüllten, angenommenen
Plan. Der Plan entsteht laut Vertrag (V-11) über `DraftService`/`SolveService` — beides gehört zu
`planning` und wird in Phase backend2 gebaut. Die Stelle ist in `SampleDataLoader.loadFull()`
markiert. Die Zeitangabe „≤ 30 s" der Checkliste ist damit noch nicht gemessen; der Datenteil
allein braucht 0,2 s.

## 3. Tests

```
$ gradle --no-daemon test --rerun --tests de.wsp3_1.config.ConfigControllerTest \
      --tests de.wsp3_1.ArchitectureTest \
      integrationTest --rerun --tests de.wsp3_1.config.ConfigIT --tests 'de.wsp3_1.sampledata.*'
BUILD SUCCESSFUL in 34s
```

| Testklasse | Aufgabe | Tests | Fehler |
|---|---|---|---|
| `de.wsp3_1.ArchitectureTest` | `test` | 5 | 0 |
| `de.wsp3_1.config.ConfigControllerTest` | `test` | 6 | 0 |
| `de.wsp3_1.config.ConfigIT` | `integrationTest` | 4 | 0 |
| `de.wsp3_1.sampledata.SampleDataIT` | `integrationTest` | 5 | 0 |
| `de.wsp3_1.sampledata.SmallFixtureIT` | `integrationTest` | 3 | 0 |

`ArchitectureTest` läuft mit der neuen Zeile `sampledata → masterdata, planning, shared` durch (Regel
4b) und ohne Verstoß gegen Regel 1 bis 3. Ein Zwischenstand um 00:31 hatte an Regel 2 fünf Verstöße
in `de.wsp3_1.auth.api.AuthDtos` gemeldet — fremdes Modul, beim Lauf um 00:37 behoben.

**Nicht gelaufen:** der gemeinsame Lauf `scripts/ci-backend-test.sh --rerun` über *alle* Testklassen —
zur selben Zeit bauen andere Rollen an `auth`, `masterdata` und `rules`, ein Gesamtlauf misst deren
Zwischenstand mit. Die fünf Klassen oben liefen deshalb einzeln gegen dieselbe Umgebung
(`gradle:9.7.1-jdk21`, `postgres:16-alpine`, Profil `ci`), die der Gesamtlauf benutzt.

> **Nachtrag 05.09.:** Der Gesamtlauf ist nachgeholt und steht in `docs/abnahme/pruefung-backend1-nacharbeit.md`
> (`test` 42 Tests, `integrationTest` 70 Tests, 0 Fehler).

## 4. Was dieses Modul dem Team gibt

- `SampleDataLoader.loadSmall()` und `loadSmallWithActivePlan()` sind die **Testfixture des
  Pflichtenhefts** für alle anderen Module: drei Lehrkräfte, drei Klassen, drei Räume, 35
  Zeitfenster, Sperrzeiten von Schulz (Fr Position 6 und 7) und der angenommene Plan mit den sieben
  Stunden aus Schritt P-1 (Meyer montags 4, dienstags 1; Mathematik der 9c offen).
- `SampleDataLoader.clear()` leert Grunddaten, Pläne, Ereignisse, Ausnahmen, Verlauf und
  Lehrkraft-Konten und lässt Regelwerk, Systemkonfiguration und das Konto `admin` stehen — gedacht
  als `@BeforeEach` jedes Integrationstests, der mit leeren Tabellen anfangen will.
