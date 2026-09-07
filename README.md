# Stundenplanung — Web-App (Gruppe WSP3_1 „PushnPull")

Spring-Boot-Backend, React-Frontend, PostgreSQL, alles in Containern. Ausfuehrlich steht alles in
**[STARTSTAND.md](STARTSTAND.md)** — Ordnerbaum, Versionen, Regeln, erste Karte je Rolle.

Voraussetzung: Docker laeuft; Node ≥ 20.19 (oder ≥ 22.12). Ein JDK braucht man **nicht** — Java laeuft im Container.
Alle Befehle aus der Repo-Wurzel.

## Drei Befehle

```bash
# 1) Backend mit Datenbank (Container, kein JDK noetig)
(cd deployment && cp -n .env.example .env && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db)
scripts/dev-backend.sh                      # Backend auf http://localhost:8080

# 2) Attrappe der API aus dem Vertrag
(cd api-contract && npm ci && npm run mock)  # Prism auf http://localhost:1501

# 3) Frontend
cd frontend && npm ci && npm run dev:mock    # http://localhost:5173, Konto admin/admin, kein Backend noetig
```

`npm run dev` statt `dev:mock` spricht das echte Backend aus Schritt 1, `npm run dev:prism` die Attrappe aus Schritt 2.

## Alles in einem Vorgang (so laeuft es auch auf der VM)

```bash
cd deployment
cp -n .env.example .env
# PFLICHT: eigenes Kennwort setzen. Mit dem Platzhalter `bitte-aendern` bricht das Backend
# im Profil prod ab (Befund SICHERHEIT-6, 05.09.) — der Stack startet dann NICHT.
$EDITOR .env            # ADMIN_INITIAL_PASSWORD=<eigener Wert>
docker compose --profile beispieldaten up -d --build
```

Danach: Web-App auf <http://localhost:1500>. Die Attrappe gehoert nicht zum Stack und laeuft nur mit
`docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mock` (oder ueber Schritt 2 oben) —
sie ist kein Teil des Produkts und hat bei der Abnahme nichts zu suchen. Angemeldet wird man mit
`admin` und dem `ADMIN_INITIAL_PASSWORD` aus `deployment/.env`.

> **Der Platzhalter reicht nicht.** `cp -n .env.example .env` allein genuegt seit dem 05.09. nicht mehr:
> mit `ADMIN_INITIAL_PASSWORD=bitte-aendern` bricht das Backend im Profil `prod` ab (SICHERHEIT-6).
> Erst einen eigenen Wert in `deployment/.env` eintragen, dann `up`. Details in `ANLEITUNG.md`.

Das Profil `beispieldaten` haengt einen Container `seed` an, der sich anmeldet und den Beispielbestand laedt
(12 Klassen, 24 Lehrkraefte, 18 Raeume, dazu ein gerechneter Plan). **Ohne das Profil** startet derselbe Stack
mit leerer Datenbank — so faengt eine Schule an. Beides ist derselbe Befehl, nur mit oder ohne `--profile`.

Gemessen am 05.09. (Mac, Docker Desktop 29.1.2, Abbilder im Cache): `up` kehrt nach 34 s zurueck, die
Beispieldaten sind rund 25 s spaeter fertig gerechnet. Ohne Cache dauert der erste Build Minuten.
VM-Schritte: [deployment/README-VM.md](deployment/README-VM.md).

## Pruefen wie die Pipeline (ohne Runner)

```bash
scripts/ci-all.sh        # contract-lint, backend-test, frontend-build, image — dieselben Befehle wie .gitlab-ci.yml
```

Einzeln: `scripts/ci-contract-lint.sh`, `scripts/ci-backend-test.sh`, `scripts/ci-frontend-build.sh`, `scripts/ci-image.sh`.
Letzter gruener Durchlauf mit Ausgabe: [docs/betrieb/](docs/betrieb/) (05.09., 3 min 08 s, alle vier ok).

Den Deploy gibt es ebenfalls als Skript — `scripts/deploy-vm.sh`, mit `DRY_RUN=1` zeigt es jeden Befehl,
ohne etwas anzufassen. Auf der VM ist es **nie gelaufen**.

## Ordner

| Ordner | Inhalt |
|---|---|
| `backend/` | Spring Boot 4.1.1, Java 21, Gradle — REST-API, Solver, Flyway-Migrationen |
| `frontend/` | React 19, Vite 8, Tailwind 4, TypeScript — die Web-App |
| `api-contract/` | `openapi.yaml`: die eine Wahrheit der REST-API, dazu Attrappe und Lint |
| `deployment/` | docker compose, nginx, Sicherung, VM-Anleitung |
| `scripts/` | jeder CI-Job auch lokal, ohne Runner |

## Wenn etwas klemmt

- „Unable to locate a Java Runtime" — richtig so, es gibt kein JDK auf dem Rechner: `scripts/dev-backend.sh` benutzen.
- **8080 antwortet mit `Server: Apache`** (`curl -s -i localhost:8080/`): auf macOS laeuft dort der mitgelieferte Apache.
  Der Vite-Proxy liefert dann dessen HTML-404 statt des Backends, ohne Hinweis. Abhilfe: `sudo apachectl stop` — oder
  ausweichen mit `BACKEND_PORT=8089 scripts/dev-backend.sh` und `(cd frontend && VITE_PROXY_TARGET=http://localhost:8089 npm run dev)`.
- `no such service: mock`: die Attrappe steht seit dem 05.09. in `docker-compose.dev.yml`, nicht mehr im
  Produktions-Compose. Start: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mock`.
- Port belegt: die Attrappe laeuft entweder ueber `npm run mock` **oder** ueber den Compose-Dienst, nie beides.
- Attrappe antwortet nicht, `docker compose ps mock` zeigt `Restarting`: Version im Compose pruefen — nur
  `stoplight/prism:5.14.2` mit `mock -m false` laeuft hier.
- Prism antwortet ueberall mit 401: der Bearer-Header fehlt. `curl -H 'Authorization: Bearer x' localhost:1501/api/teachers`
  (Prism prueft nur, *ob* ein Header da ist).
- `ADMIN_INITIAL_PASSWORD fehlt in deployment/.env`: `cp -n .env.example .env` im Ordner `deployment/` vergessen.
- Anmeldung mit dem Passwort aus `.env` schlaegt fehl, `admin`/`admin` geht: das Konto stammt noch aus einem Lauf im Profil
  `dev`. Frisch anfangen mit `cd deployment && docker compose down -v` (loescht alle Daten).
- **Nach F5 bleibt man angemeldet** (seit F-5a, 05.09.): das Token liegt im `localStorage` und gilt 30 Tage
  (`app.auth.token-ttl`, Umgebungsvariable `AUTH_TOKEN_TTL`). Ein neuer Tab und ein Lesezeichen auf `/mein-plan`
  fuehren direkt in die Anwendung; abgemeldet wird ueber die Schaltflaeche im Benutzermenue. Der aeltere Befund F-5
  („Neuladen = neue Anmeldung") steht in `docs/VERTRAG.md` und beschreibt den Stand bis zum 05.09.
- Loeschen im Repo mit `git rm`. Nichts direkt auf `main` pushen — Branch, Merge Request, gruene Pipeline.
