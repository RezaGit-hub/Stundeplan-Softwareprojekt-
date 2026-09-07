# Nachweise Betrieb — Stand 05.09.2026

Was in `deployment/README-VM.md` und `README.md` als „gemessen" steht, liegt hier als Ausgabe. Alles auf
einem Mac (Apple Silicon, Docker Desktop 29.1.2, Compose v2.40.3). **Auf der VM ist nichts davon gelaufen.**

| Datei | Was sie zeigt |
|---|---|
| `compose-up-2026-09-05.log` | `docker compose --profile beispieldaten down -v` + `up -d --build` aus leerer Datenbank. `up` kehrt nach **34 s** zurueck. |
| `01-start-nach-compose-up.png` | Uebersicht nach dem Anmelden als `admin` ueber nginx auf Port 1500: 24/24 Lehrkraefte, 0 Konflikte. |
| `02-aktiver-plan-beispieldaten.png` | Aktiver Plan, Sicht Schule, KW 37: 360 Stunden, 0 harte und 0 weiche Konflikte. |
| `ci-all-2026-09-05.log` | `scripts/ci-all.sh`, **3 min 08 s**, alle vier Jobs `ok`. |
| `ruecksicherung-2026-09-05.log` | Ruecksicherung nach README-VM Abschnitt 10, Fall B — Bestand davor und danach identisch. |

## Ein Befehl, danach benutzbar

```
docker compose --profile beispieldaten up -d --build
```

Aus leerer Datenbank, gemessen 05.09.:

```
up -d --build              34 s   (Abbilder im Cache; ohne Cache Minuten)
seed bis "geladen: {...}"  ~25 s  (Solver rechnet 10 s, dazu Start und Anmeldung)
```

Danach beantwortet:

```
GET  http://127.0.0.1:1500/api/health   -> 200 {"status":"UP","schoolName":"Gymnasium Musterstadt"}
GET  http://127.0.0.1:1500/login        -> 200
GET  http://127.0.0.1:1501/api/health   -> 200 (Prism-Attrappe)
POST http://127.0.0.1:1500/api/auth/login -> Token, Rolle PLANNING
     /api/classes 12 · /api/teachers 24 · /api/rooms 18 · aktiver Plan mit 360 Stunden
docker compose exec backend date        -> CEST, nicht UTC
```

Container danach: `db healthy`, `backend healthy`, `web running`, `mock running`, `seed Exited (0)`.

## Pipeline lokal

`scripts/ci-all.sh`, 05.09., 07:31:38 bis 07:34:46:

```
  ok             scripts/ci-contract-lint.sh     Redocly: "Your API description is valid", 1 Warnung
  ok             scripts/ci-backend-test.sh      Gradle 9.7.1 im Container gegen Postgres 16, BUILD SUCCESSFUL in 2m 25s
  ok             scripts/ci-frontend-build.sh    tsc + eslint + vitest (3 Dateien, 28 Tests) + vite build
  ok             scripts/ci-image.sh             docker compose build backend web
```

Zwei Beobachtungen aus dem Lauf, beide harmlos, beide zum Wissen:

- `npm warn EBADENGINE vitest@5.0.0 required node ^22.12.0, current v20.20.1`. Das Node auf diesem Rechner
  ist aelter, als vitest gern haette; die Tests laufen trotzdem. Die Pipeline benutzt `node:22-alpine`,
  dort taucht die Warnung nicht auf.
- `ci-backend-test.sh` faehrt `test` und `integrationTest` mit je eigenem `--rerun`. Ohne das haelt Gradle
  die Aufgaben fuer UP-TO-DATE und fuehrt sie nicht aus — ein gruener Lauf, der nichts geprueft hat.

## Ruecksicherung

`ruecksicherung-2026-09-05.log`, Verfahren aus README-VM Abschnitt 10, Fall B:

```
Bestand vorher    Klassen=12 Lehrkraefte=24 Raeume=18 Stunden=360
Sicherung         backups/timetable-2026-09-05-0739.sql.gz, 11 KB
Schema geleert    Tabellen in public: 0
zurueckgespielt   Tabellen in public: 22
Backend gestartet healthy, "Completed initialization"
Bestand nachher   Klassen=12 Lehrkraefte=24 Raeume=18 Stunden=360
```

## Was NICHT geprueft ist

- **Alles auf der VM.** Kein Schritt aus README-VM Abschnitt 1, 2, 3 und 8 ist ausgefuehrt worden;
  `scripts/deploy-vm.sh` ist nur mit `DRY_RUN=1` gelaufen (beide Zweige, mit und ohne Registry).
- **Die Jobs `image:build`, `deploy:vm` und `runner:check` in `.gitlab-ci.yml`.** Die Datei ist als YAML
  gueltig (mit einem YAML-Parser geprueft), aber kein Runner hat sie je ausgefuehrt. Ob der Shared Runner
  des CAU-GitLab Docker-in-Docker kann und ob die Container-Registry eingeschaltet ist, ist **ungeprueft**;
  oeffentliche Dokumentation des Rechenzentrums dazu war nicht auffindbar. Der Job `runner:check`
  beantwortet beides in einem Klick — das ist Karte T-703 / offene Frage Q-B-09.
- **Rollback auf einen aelteren Stand** (README-VM Abschnitt 10, Fall A). Dafuer braucht es zwei Staende
  auf `main`.
- **Speicherbedarf auf 4 GB RAM.** Die `mem_limit`-Werte in `docker-compose.yml` sind Annahmen (Karte T-704).
