# Abnahme Schülersicht (Klassenkonto, E-50) — Belege

Stand 05.09.2026, 14:00–15:15 · Vollausbau `08-Build/plattform`, Branch `vollausbau`, nichts committet.
Gemessen gegen den laufenden Referenz-Stack (`deployment/docker-compose.yml`, Projekt `stundenplan`,
`http://localhost:1500`) mit dem Beispielbestand (12 Klassen, 24 Lehrkräfte, 360 Stunden im aktiven Plan).
Jede Zeile hier ist eine Ausgabe, die so auf dem Bildschirm stand.

**Passwörter stehen in dieser Datei nicht.** Der Abnahmelauf hält das Klassenpasswort in einer Variablen
und gibt es nie aus; der Screenshot tippt es in ein Feld vom Typ `password`.

## 1. Datenmodell und Start

`V1` bleibt byte-genau unverändert (Prüfsumme `1165618105`, nachgerechnet und gegen die Zeile in
`flyway_schema_history` geprüft). Die Schemaänderung steht in `V7__student_class_account.sql`.

```
o.f.core.internal.command.DbMigrate : Migrating schema "public" to version "7 - student class account"
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "public", now at version v7 (execution time 00:00.148s)
```

`docker compose up -d backend web` → `stundenplan-backend-1 Up (healthy)`, `stundenplan-web-1 Up`,
`GET /api/health` → `{"status":"UP","schoolName":"Gymnasium Musterstadt"}`, `GET /login` → 200.

## 2. Automatisierte Läufe

| Lauf | Ergebnis |
|---|---|
| `scripts/ci-backend-test.sh --rerun` (eigene Namen `CI_NETWORK`/`CI_DB`) | BUILD SUCCESSFUL · **134 Unit-Tests + 183 Integrationstests, 0 Fehler** (darunter neu `ClassWeekIT` 6, `UsersIT` 6, `SecurityConfigTest` +5) |
| `frontend`: `npm run typecheck` · `npm run lint` · `npm test` · `npm run build` | grün · grün · **59 Tests in 7 Dateien** · `dist` gebaut |
| `api-contract`: `npx redocly lint openapi.yaml` | „Your API description is valid", 1 vorbestehende Warnung (`operation-4xx-response` an `/api/health`) |
| `08-Build/web` (Demo-Kopie): `npm run typecheck` · `npm test` · `npm run lint` · `npm run build:demo` | grün · **66 Tests in 8 Dateien** · grün · `webapp-demo.html` 1,52 MB |
| `08-Build/starter`: `scripts/ci-backend-test.sh --rerun` (geändertes `V1` mit `STUDENT` und `class_id`) | BUILD SUCCESSFUL in 3 min 50 s, **12 Unit- + 12 Integrationstests, 0 Fehler** — die Schablone migriert weiter |
| Playwright `scripts/playwright.klassenplan.config.ts` | 1 passed — Anmeldung als Klasse → `/klassenplan`, Raster gefüllt, Seitenleiste nur „Klassenplan" + „Abmelden", fremde Route leitet zurück, 0 Seitenfehler |

## 3. Curl-Abnahme gegen den Stack

Skript: siehe unten. Ausgabe vom 05.09., 15:06 (Woche `2026-09-07` — der Plan gilt ab dem Annahmetag,
Befund F-8):

```
== 2. POST /api/users/class-accounts
   HTTP 201, 12 neue Konten: 5a, 5b, 6a, 6b, 7a, 7b, 8a, 8b, 9a, 9b, 10a, 10b
== 3. Zweiter Aufruf (idempotent)
   0 neue Konten (erwartet: 0)
== 4. Kontenliste nennt Klasse und Kuerzel
[{"username":"10a","role":"STUDENT","classShort":"10a","teacherId":null}]
== 5. Anmeldung als Klasse
   Rolle: STUDENT, Anzeigename: Klasse 10a
== 6. GET /api/me/class-week
   HTTP 200
{"monday":"2026-09-07","tage":5,"eintraege":30,"klassen":["10a"],"anlaesse":0,"arten":["REGULAR"]}
== 7. Alles andere ist 403
   /api/timetable               403
   /api/teachers                403
   /api/classes                 403
   /api/users                   403
   /api/me/timetable/week       403
   /api/changelog               403
   /api/timegrid (erlaubt)      200
   /api/me (erlaubt)            200
   /api/me/class-week ohne Token 401
   /api/me/class-week als admin 404
== 8. Ereignis anlegen: die Klassensicht zeigt NEEDS_SUBSTITUTION ohne Grund
   Ereignis 2 fuer Lehrkraft 24 am 2026-09-07 angelegt
   Planungssicht (mit Anlass):
{"offen":1,"anlaesse":["Krankmeldung"]}
   Klassensicht (ohne Anlass):
{"offen":1,"anlaesse":0}
== 9. Ereignis wieder loeschen
   DELETE /api/events/2 -> 204
   Klassensicht danach:
{"offen":0}
```

Damit belegt: **ein Konto je Klasse**, idempotent; **nur die eigene Klasse** (`klassen: ["10a"]`);
**kein einziger Anlass** (`anlaesse: 0`), auch nicht bei offenem Vertretungsbedarf — während die Planung
denselben Tag mit `["Krankmeldung"]` sieht; **403 auf allem anderen**, auch auf der Lehrkraft-Sicht;
**404 statt Daten** für ein Konto ohne Klasse.

## 4. Screenshot

`docs/smoke/klassenplan.png` — Konto der Klasse 7a, Woche KW 37 (07.–11.09.2026), Chrome headless
1440×900, Anmeldung über die Oberfläche. Zu sehen: Wochenraster mit Fach, Lehrkraft-**Kürzel** und Raum je
Kachel, Wochenwahl, Legende, Seitenleiste mit genau einem Eintrag („Klassenplan") plus „Abmelden".

## 5. Wiederholen

```bash
# Backend-Tests
cd 08-Build/plattform
CI_NETWORK=stundenplan-ci-student CI_DB=stundenplan-ci-db-student scripts/ci-backend-test.sh --rerun

# Screenshot und Oberflächenprüfung (Stack muss laufen)
cd frontend
ABNAHME_ADMIN_PW=<aus deployment/.env> npx playwright test -c scripts/playwright.klassenplan.config.ts

# Curl-Abnahme: Konten anlegen, als Klasse anmelden, Filter und 403 prüfen
#   (Die Abfolge steht in ANLEITUNG.md, Abschnitt „3b. Schuelersicht (Klassenkonto)".)
```
