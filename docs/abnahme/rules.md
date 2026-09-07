# Abnahme — Modul `rules` (Regelwerk, W-2)

Stand 05.09.2026, Bauphase backend1. Belegt sind die Zeilen **R-1, R-2, R-3** der
`docs/ABNAHME-CHECKLISTE.md` und Schritt 1–2 von **T-W2.1** (den Konfliktteil von T-W2.1 baut
backend2). Alles gegen einen laufenden Stack, nichts aus dem Gedächtnis.

## Wie gemessen wurde

Der Arbeitsbaum ist unversioniert und mehrere Module entstehen gleichzeitig; deshalb lief die Prüfung
auf einer Arbeitskopie unter `/private/tmp/rules-verify/backend` (Stand 05.09., `diff` gegen den
Arbeitsbaum ohne Abweichung in allen Dateien dieses Moduls), mit eigener Datenbank und eigenem
Gradle-Zwischenspeicher, damit sie keinem anderen Lauf ins Gehege kommt:

```
docker run -d --rm=true --name rules-ci-db --network rules-ci \
  -e POSTGRES_DB=timetable -e POSTGRES_USER=tt -e POSTGRES_PASSWORD=tt postgres:16-alpine
docker run -d --rm=true --name rules-app --network rules-ci \
  -v /private/tmp/rules-verify/backend:/w -w /w -v rules-gradle-cache:/home/gradle/.gradle \
  -e SPRING_PROFILES_ACTIVE=ci -e SPRING_DATASOURCE_URL=jdbc:postgresql://rules-ci-db:5432/timetable \
  -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt -e ADMIN_INITIAL_PASSWORD=admin \
  gradle:9.7.1-jdk21 gradle bootRun --no-daemon
```

Datenbank frisch, Flyway V1–V4 gelaufen, `admin`/`admin` von `AdminBootstrap` angelegt. Gecurlt wurde
aus einem Nachbarcontainer im selben Netz (`curlimages/curl`), kein Port veröffentlicht — Skript
`/private/tmp/rules-verify/abnahme.sh`. `B=http://rules-app:8080`, `T` = Token aus
`POST /api/auth/login`.

```
curl -s http://rules-app:8080/api/health
{"status":"UP","schoolName":"Gymnasium Musterstadt"}
```

## R-1 — die beiden Regeln des Pflichtenhefts sind vorhanden und aktiv

```
curl -s $B/api/rules -H "Authorization: Bearer $T"
```

```json
[{"id":1,"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","grade":null,"value":6,"severity":"SOFT","weight":1,"active":true,"label":"Maximale Stunden pro Tag"},
 {"id":2,"type":"MAX_LESSONS_PER_WEEK","scope":"CLASS","grade":null,"value":30,"severity":"SOFT","weight":1,"active":true,"label":"Maximale Stunden pro Woche"},
 {"id":3,"type":"MAX_LESSONS_PER_DAY","scope":"TEACHER","grade":null,"value":6,"severity":"SOFT","weight":1,"active":false,"label":"Maximale Stunden pro Tag"},
 {"id":4,"type":"MAX_LESSONS_PER_WEEK","scope":"TEACHER","grade":null,"value":26,"severity":"SOFT","weight":1,"active":false,"label":"Maximale Stunden pro Woche"},
 {"id":5,"type":"NO_LESSONS_IN_BREAKS","scope":null,"grade":null,"value":1,"severity":"HARD","weight":1,"active":true,"label":"Keine Stunden in Pausen"}]
```

Erwartet laut Checkliste: `MAX_LESSONS_PER_DAY/CLASS/6/SOFT/active true` und
`MAX_LESSONS_PER_WEEK/CLASS/30/SOFT/active true` — beides da. Reihenfolge und Feldnamen wie im
`example` des Vertrags.

## R-2 — Wert 0 wird abgelehnt, der alte Wert bleibt stehen

```
curl -s -X PUT $B/api/rules/1 -H "Authorization: Bearer $T" -H 'content-type: application/json' \
     -d '{"value":0,"severity":"SOFT","weight":1,"active":true}'
```

```
HTTP 400
{"detail":"Bitte die markierten Felder pruefen.","instance":"/api/rules/1","status":400,
 "title":"Eingabe unvollstaendig","type":"urn:pushnpull:problem:validation",
 "errors":[{"message":"muss mindestens 1 sein","field":"value"}]}
```

`GET /api/rules` danach: `{"id":1,…,"value":6,…}` — die 6 steht unverändert.

## T-W2.1 Schritt 1–2 — ein gültiger Wert wird gespeichert

```
curl -s -X PUT $B/api/rules/1 … -d '{"value":3,"severity":"SOFT","weight":1,"active":true}'
HTTP 200
{"id":1,"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","grade":null,"value":3,"severity":"SOFT","weight":1,"active":true,"label":"Maximale Stunden pro Tag"}

curl -s $B/api/rules …   → {"id":1,…,"value":3,…}
```

Schritt 3 von T-W2.1 (`POST /api/draft/lessons` erzeugt genau einen Konflikt `RULE_VIOLATION` mit
`ruleId` = 1) gehört zu `planning` und wird in backend2 belegt. Was `rules` dafür liefert, steht als
`EffectiveRule(ruleId=1, type=MAX_LESSONS_PER_DAY, scope=CLASS, ownerId=<Klasse>, value=3, SOFT, 1,
"Maximale Stunden pro Tag")` bereit und ist in `RuleResolverTest` geprüft.

## Jahrgangswerte (Soll, E-47)

```
POST $B/api/rules {"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","grade":5,"value":5,"severity":"SOFT","weight":2,"active":true}
HTTP 201  {"id":6,…,"grade":5,"value":5,"weight":2,"label":"Maximale Stunden pro Tag"}

dieselbe Anfrage noch einmal
HTTP 409  {"type":"urn:pushnpull:problem:data-exists",
           "detail":"Für Jahrgang 5 gibt es die Regel „Maximale Stunden pro Tag“ bereits."}

POST $B/api/rules ohne grade
HTTP 400  {"type":"urn:pushnpull:problem:validation",
           "errors":[{"message":"Bitte einen Jahrgang angeben.","field":"grade"}]}

DELETE $B/api/rules/1   (schulweite Zeile)
HTTP 409  {"type":"urn:pushnpull:problem:in-use",
           "detail":"Schulweite Regeln werden ausgeschaltet, nicht gelöscht."}
```

## R-3 — Voreinstellung des eingestellten Bundeslands laden

```
curl -s $B/api/config -H "Authorization: Bearer $T"
{"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":60}

curl -s -X POST $B/api/rules/reset -H "Authorization: Bearer $T"
HTTP 200
```

Ergebnis: wieder genau die fünf Zeilen von oben — Wert 6 statt der eben gesetzten 3, der Jahrgangswert
Jahrgang 5 ist weg, die Kennungen 1–5 sind unverändert, Klasse aktiv, Lehrkraft aus, Pause hart. Das ist
die Erwartung der Checkliste („SH: 6/30 Klasse aktiv, 6/26 Lehrkraft inaktiv, Pause hart").

**Gegenprobe, dass kein Bundesland im Code steht:** Bundesland auf Berlin stellen und erneut laden.

```
PUT $B/api/config {"schoolName":"Gymnasium Musterstadt","state":"BE","schoolType":"GYMNASIUM","maxSolveSeconds":60}   → 200
POST $B/api/rules/reset → 200
```

```json
[{"id":1,"type":"MAX_LESSONS_PER_DAY","scope":"CLASS","grade":null,"value":6,…,"active":true},
 {"id":2,"type":"MAX_LESSONS_PER_WEEK","scope":"CLASS","grade":null,"value":34,…,"active":true},
 {"id":3,"type":"MAX_LESSONS_PER_DAY","scope":"TEACHER","grade":null,"value":6,…,"active":false},
 {"id":4,"type":"MAX_LESSONS_PER_WEEK","scope":"TEACHER","grade":null,"value":26,…,"active":false},
 {"id":5,"type":"NO_LESSONS_IN_BREAKS","scope":null,"grade":null,"value":1,…,"active":true},
 {"id":7,"type":"MAX_LESSONS_PER_WEEK","scope":"CLASS","grade":7,"value":33,…},
 {"id":8,…,"grade":8,"value":33,…},{"id":9,…,"grade":9,"value":34,…},{"id":10,…,"grade":10,"value":34,…}]
```

Berlins Wochenwert 34 statt Schleswig-Holsteins 30, dazu die vier Jahrgangszeilen 7–10 aus der
Stundentafel des Landes. Zurück auf `SH` und erneut geladen: wieder die fünf Zeilen mit 6/30/6/26/1 —
die Berliner Jahrgangszeilen sind weg.

**Wichtig für das Gespräch am 08.09. (R2):** Die Zahlen sind begründete Startwerte, kein Rechtsstand.
Herkunft je Zeile steht als Kommentar in `V2__rule_presets.sql`; wo dort „Standard" steht, ist der Wert
eine Setzung des Projekts (das betrifft beide Tagesobergrenzen außer Brandenburg und die Pausenregel).
Jeder Wert bleibt in der Oberfläche einzeln änderbar.

## Ohne Anmeldung

```
curl -s $B/api/rules
HTTP 401  {"type":"urn:pushnpull:problem:unauthenticated",
           "detail":"Bitte erneut anmelden: das Token fehlt oder ist abgelaufen."}
```

## Tests

```
docker run --rm=true --network rules-ci -v /private/tmp/rules-verify/backend:/w -w /w \
  -v rules-gradle-cache:/home/gradle/.gradle -e SPRING_PROFILES_ACTIVE=ci \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://rules-ci-db:5432/timetable \
  -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt -e ADMIN_INITIAL_PASSWORD=admin \
  gradle:9.7.1-jdk21 gradle test --no-daemon --rerun
BUILD SUCCESSFUL in 29s
```

Letzter Lauf 05.09., 00:41 Uhr, gegen den Arbeitsbaum in seinem damaligen Stand (andere Module bauen
weiter; ihre Testzahlen wachsen noch):

| Testklasse | Tests | Fehler |
|---|---|---|
| `de.wsp3_1.ArchitectureTest` | 5 | 0 |
| `de.wsp3_1.auth.SecurityConfigTest` | 9 | 0 |
| `de.wsp3_1.auth.TokenCleanupTest` | 1 | 0 |
| `de.wsp3_1.config.ConfigControllerTest` | 6 | 0 |
| `de.wsp3_1.masterdata.MasterdataControllerTest` | 7 | 0 |
| `de.wsp3_1.masterdata.WarningCheckerTest` | 5 | 0 |
| **`de.wsp3_1.rules.RuleResolverTest`** | **6** | **0** |
| `de.wsp3_1.solver.TimetableConstraintProviderTest` | 3 | 0 |
| Summe | 42 | 0 |

```
… gradle integrationTest --no-daemon --rerun --tests 'de.wsp3_1.rules.*' --tests 'de.wsp3_1.ApplicationIT'
BUILD SUCCESSFUL in 18s
```

| Testklasse | Tests | Fehler |
|---|---|---|
| `de.wsp3_1.ApplicationIT` | 6 | 0 |
| **`de.wsp3_1.rules.RuleControllerIT`** | **13** | **0** |
| Summe | 19 | 0 |

`ArchitectureTest` ist mit der ergänzten Zeile `rules → config, shared` grün; `ApplicationIT` prüft
weiterhin die 138 Voreinstellungszeilen über 16 Bundesländer und dass das aktive Regelwerk den
SH-Werten entspricht — das gilt auch nach den Läufen von `RuleControllerIT`, weil dieser Test den
Beispielsatz vor und nach jedem Testfall wiederherstellt.

## Nicht geprüft

- Kein Lauf über `deployment/docker-compose.yml` und nicht über nginx (Port 1500): der Compose-Stack
  hätte den gleichzeitig laufenden Arbeiten anderer Module ins Gehege kommen können.
- Kein Browser-Durchlauf: die Web-App läuft in dieser Phase noch gegen die Attrappe.
- Rolle Lehrkraft gegen `/api/rules` (403) nur über `SecurityConfigTest` (Pfadregel `/api/**`), nicht
  per curl — dafür braucht es ein Lehrkraft-Konto samt Lehrkraft aus `masterdata`.
- `gradle integrationTest` ohne `--tests`-Filter, also der vollständige Satz aller Module, lief nicht:
  zum Prüfzeitpunkt entstanden `masterdata`, `auth` und `sampledata` noch.
