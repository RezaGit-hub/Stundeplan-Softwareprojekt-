# Abnahme Modul `auth` — Belege

Stand 05.09.2026, 00:30–00:45 · Vollausbau `08-Build/plattform`, Branch `vollausbau`, nichts committet.
Gemessen gegen einen laufenden Stack, nicht behauptet: jede Zeile hier ist eine Ausgabe, die so auf dem
Bildschirm stand.

## Aufbau des Prüfstands

Andere Module wurden zur gleichen Zeit gebaut; der Prüfstand hat deshalb eigene Namen und einen eigenen
Port (`scripts/ci-backend-test.sh` und `deployment/docker-compose.yml` benutzen feste Namen und Port
8080 — zwei Agenten gleichzeitig nehmen sich sonst die Datenbank weg).

```
docker network create stundenplan-auth
docker run -d --rm=true --name stundenplan-auth-db --network stundenplan-auth \
  -e POSTGRES_DB=timetable -e POSTGRES_USER=tt -e POSTGRES_PASSWORD=tt postgres:16-alpine
docker run --rm=true -v "$PWD/backend":/w -w /w -v stundenplan-gradle:/home/gradle/.gradle \
  gradle:9.7.1-jdk21 gradle bootJar --no-daemon
docker run -d --rm=true --name stundenplan-auth-app --network stundenplan-auth -p 18080:8080 \
  -v "$PWD/backend/build/libs/app.jar":/app.jar -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://stundenplan-auth-db:5432/timetable \
  -e SPRING_DATASOURCE_USERNAME=tt -e SPRING_DATASOURCE_PASSWORD=tt \
  -e ADMIN_INITIAL_PASSWORD=geheim-start-2026 -e TZ=Europe/Berlin \
  eclipse-temurin:21-jre java -jar /app.jar
```

Start (`docker logs stundenplan-auth-app`):

```
o.f.core.internal.command.DbMigrate : Successfully applied 4 migrations to schema "public", now at version v4
de.wsp3_1.BackendApplication        : Started BackendApplication in 9.522 seconds
de.wsp3_1.auth.domain.AdminBootstrap: Konto "admin" mit der Rolle PLANNING angelegt (Passwort aus ADMIN_INITIAL_PASSWORD).
```

`B=http://localhost:18080`, `T` = Token der Planung, `M` = Token der Lehrkraft.

## Zeilen der Abnahme-Checkliste

| # | Befehl | Ausgabe | Ergebnis |
|---|---|---|---|
| **L-1** | `curl -s -X POST $B/api/auth/login -H 'content-type: application/json' -d '{"username":"admin","password":"geheim-start-2026"}'` | `role=PLANNING displayName=admin teacherId=None token-Länge=43` (Base64url aus 32 Byte) | ok |
| **L-2** | dieselbe Anfrage mit `"password":"falsch"` | `HTTP/1.1 401`, `Content-Type: application/problem+json`, `{"detail":"Benutzername oder Passwort ist falsch.","instance":"/api/auth/login","status":401,"title":"Nicht angemeldet","type":"urn:pushnpull:problem:unauthenticated"}` | ok |
| **A-1** | `curl -X POST $B/api/users -H "Authorization: Bearer $T" -d '{"username":"meyer","password":"start-passwort","role":"TEACHER","teacherId":1}'` | `HTTP 201` · `{"id":2,"username":"meyer","role":"TEACHER","teacherId":1}` | ok |
| **A-1** (Folge) | `curl -X POST $B/api/auth/login -d '{"username":"meyer","password":"start-passwort"}'` | `role=TEACHER displayName=Meyer teacherId=1` — der Anzeigename kommt aus der Lehrkraft, nicht aus dem Benutzernamen | ok |
| **A-6** | `curl -X POST $B/api/draft/lessons -H "Authorization: Bearer $M" -d '{"classId":1,…}'` | `HTTP 403` · `{"detail":"Diese Funktion ist der Planung vorbehalten.","status":403,"title":"Kein Zugriff","type":"urn:pushnpull:problem:forbidden"}` | ok |

## Negativfälle 401 und 403 (Auftrag: drei Stück)

| Fall | Befehl | Ausgabe |
|---|---|---|
| 401 ohne Token | `curl $B/api/users` | `401` · `{"detail":"Bitte erneut anmelden: das Token fehlt oder ist abgelaufen.",…,"type":"urn:pushnpull:problem:unauthenticated"}` |
| 401 nach dem Abmelden | `curl -X DELETE $B/api/auth/token -H "Authorization: Bearer $M"` → `204`, danach `curl $B/api/me -H "Authorization: Bearer $M"` | `401` mit demselben Text — das Token ist serverseitig weg |
| 403 Lehrkraft auf Planungspfad | `curl $B/api/timetable -H "Authorization: Bearer $M"` | `403` |
| 403 Lehrkraft in der Kontenverwaltung | `curl $B/api/users -H "Authorization: Bearer $M"` | `403` · `…"type":"urn:pushnpull:problem:forbidden"` |

Gegenprobe, dass die Rolle nicht zu viel sperrt:

| Fall | Befehl | Ausgabe |
|---|---|---|
| Lehrkraft liest die eigene Identität | `curl $B/api/me -H "Authorization: Bearer $M"` | `{"id":2,"username":"meyer","role":"TEACHER","displayName":"Meyer","teacherId":1}` |
| Lehrkraft liest das Stundenraster | `curl $B/api/timegrid -H "Authorization: Bearer $M"` | `HTTP 200` (Controller aus `masterdata` war zu dem Zeitpunkt schon da) |
| Erreichbarkeit ohne Token | `curl $B/api/health` | `{"status":"UP","schoolName":"Gymnasium Musterstadt"}` |

## Konten und Passwörter

| Fall | Befehl | Ausgabe |
|---|---|---|
| Rolle TEACHER ohne Lehrkraft | `POST /api/users {"username":"ohne","password":"start-passwort","role":"TEACHER"}` | `400` · `errors[0] = {"field":"teacherId","message":"Bitte die Lehrkraft waehlen, zu der dieses Konto gehoert."}` |
| Benutzername doppelt | `POST /api/users {"username":"meyer",…}` | `409` · `{"detail":"Den Benutzernamen \"meyer\" gibt es schon.",…,"type":"urn:pushnpull:problem:in-use"}` |
| Passwort zu kurz | `POST /api/users {"password":"1234",…}` | `400` · `errors[0] = {"field":"password","message":"Das Passwort braucht mindestens 8 Zeichen."}` |
| Liste der Konten | `GET /api/users -H "Authorization: Bearer $T"` | `[{"id":1,"username":"admin","role":"PLANNING","teacherId":null}]` |
| Planung setzt Passwort zurück | `PUT /api/users/2/password {"password":"neues-passwort"}` | `204`; Login mit dem alten Passwort `401`, mit dem neuen `200` |
| unbekanntes Konto | `PUT /api/users/999/password` | `404` |
| eigenes Passwort, altes falsch | `PUT /api/me/password {"oldPassword":"falsch",…}` | `400` · `errors[0] = {"field":"oldPassword","message":"Das bisherige Passwort ist falsch."}` |
| eigenes Passwort, altes richtig | `PUT /api/me/password {"oldPassword":"geheim-start-2026","newPassword":"noch-geheimer-2026"}` | `204`; Login mit dem neuen Passwort `200`, mit dem alten `401` |

## Abgelaufene Sitzungen aufräumen

Neustart mit `-e TOKEN_CLEANUP_CRON="*/5 * * * * *"` (statt täglich 03:15), dann eine abgelaufene Zeile
eingetragen:

```
INSERT INTO auth_token (token_hash, user_id, created_at, expires_at)
VALUES ('abgelaufen-zum-pruefen', 1, now() - interval '13 hours', now() - interval '1 hour');   -- INSERT 0 1
SELECT count(*) FROM auth_token WHERE token_hash='abgelaufen-zum-pruefen';                      -- 1
-- acht Sekunden später
SELECT count(*) FROM auth_token WHERE token_hash='abgelaufen-zum-pruefen';                      -- 0
SELECT count(*) FROM auth_token WHERE expires_at > now();                                       -- 3
```

Serverprotokoll: `de.wsp3_1.auth.domain.TokenCleanup : 1 abgelaufene Sitzungen geloescht.`
Die drei gültigen Sitzungen bleiben.

## Tests

```
docker run --rm=true -v "$PWD/backend":/w -w /w -v stundenplan-gradle:/home/gradle/.gradle \
  gradle:9.7.1-jdk21 gradle test integrationTest \
  --tests 'de.wsp3_1.auth.*' --tests 'de.wsp3_1.ApplicationIT' --tests 'de.wsp3_1.ArchitectureTest' --rerun
→ BUILD SUCCESSFUL in 1m
```

| Aufgabe | Testklasse | Tests | Fehler |
|---|---|---|---|
| `test` | `ArchitectureTest` | 5 | 0 |
| `test` | `auth.SecurityConfigTest` | 9 | 0 |
| `test` | `auth.TokenCleanupTest` | 1 | 0 |
| `test` | (übrige Module: config, masterdata, rules, solver) | 27 | 0 |
| `integrationTest` | `ApplicationIT` | 6 | 0 |
| `integrationTest` | `auth.AuthFlowIT` | 6 | 0 |
| `integrationTest` | `auth.UserControllerIT` | 9 | 0 |

`test` lief über alle Module (42 Tests, 0 Fehler), `integrationTest` über die Klassen des Moduls `auth`
plus `ApplicationIT` (21 Tests, 0 Fehler).

> **Nachtrag 05.09. (Prüfung backend1):** `--rerun` ist eine Aufgaben-Option und gilt nur für die Aufgabe davor.
> Im Aufruf oben hing es an `integrationTest`; die Zahl „42 Tests" für `test` stammt deshalb möglicherweise aus
> einem vorherigen Lauf (`> Task :test UP-TO-DATE`). Frisch belegt sind beide Aufgaben in
> `docs/abnahme/pruefung-backend1-nacharbeit.md` (42 bzw. 70 Tests, 0 Fehler); das Skript
> `scripts/ci-backend-test.sh` hängt `--rerun` seitdem an beide Aufgaben.

## Nicht geprüft

- `docker compose` mit dem echten Stack (nginx, Frontend): der Prüfstand lief mit dem Backend allein
  auf Port 18080, weil auf 8080 schon etwas anderes antwortete und andere Module gleichzeitig bauten.
- Alle Testfälle, die Grunddaten, Pläne oder Vertretungen brauchen (T-W…): andere Module, andere Phase.
- Der Browser-Anteil von L-1 (Startseite nach der Anmeldung): Phase `frontend`.
