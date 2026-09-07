# Betrieb auf der VM (Gruppe 5) — Schritt fuer Schritt

Diese Anleitung gehoert Rolle 7 (Karten T-702, T-703, T-704). Sie ist so geschrieben, dass man sie von oben
nach unten abarbeiten kann, ohne den Rest des Projekts zu kennen.

**Was geprueft ist und was nicht — Stand 05.09.2026:**

| | |
|---|---|
| Abschnitte 4, 5, 7, 9 (starten, pruefen, Beispieldaten, sichern) | **auf einem Mac gemessen** am 05.09.: `docker compose --profile beispieldaten up -d --build` von leerer Datenbank bis benutzbarer Web-App in 34 s + rund 25 s Solver. Belege: `docs/betrieb/` |
| Abschnitt 10, Fall B (Ruecksicherung) | **lokal gemessen** am 05.09.: sichern, Schema leeren, zurueckspielen, Bestand danach identisch |
| Abschnitte 1 bis 3 (VM, Benutzer `deploy`, Schluessel) | **nie ausgefuehrt** — der VM-Zugang wird erst in der ersten Woche eingerichtet |
| Abschnitt 8 (Deploy) | **nie ausgefuehrt** — weder `scripts/deploy-vm.sh` noch der Job `deploy:vm` sind je gelaufen |
| Abschnitt 10, Fall A (Rollback auf einen aelteren Stand) | **nirgends ausprobiert** — dafuer braucht es zwei Staende auf `main` |

Wer einen ungeprueften Schritt zum ersten Mal ausfuehrt, traegt hier ein, was tatsaechlich herauskam, und
ersetzt den Vermerk durch die gesehene Ausgabe. Nichts hier behaupten, was man nicht gesehen hat.

## 0. Was die VM ist

| | |
|---|---|
| Adresse | `134.245.1.240` |
| System | Ubuntu 24.04, 4 Kerne, 4 GB RAM, 50 GB Platte |
| vorinstalliert | docker, docker compose, git, OpenJDK 21, gradle |
| SSH | VM-Port 22 → **1522** von aussen |
| HTTP | VM-Ports 8080–8083 → **1500–1503** von aussen |
| HTTPS | VM-Ports 8444–8449 → **1504–1509** von aussen |

Quelle: Infoblatt der Gruppe (`07-Konzept/WISSENSSTAND.md`, Abschnitt 6). Die allgemeine Wiki-Seite
„VM Konfiguration" nennt ein anderes Schema — **gueltig ist das Infoblatt.**

Daraus folgt die Portwahl: In `deployment/.env` steht auf der VM `WEB_PORT=8080`. Die NAT der VM macht
daraus nach aussen 1500. Auf dem eigenen Rechner bleibt es bei 1500, weil dort keine NAT dazwischen liegt.
`MOCK_PORT` (8081 bzw. nach aussen 1501) wirkt nur noch mit `docker-compose.dev.yml`; der regulaere
Stack startet die Attrappe seit dem 05.09. nicht mehr.

## 1. Anmelden und Benutzer `deploy` anlegen

```bash
ssh -p 1522 <eigener-account>@134.245.1.240

# Benutzer fuer die Auslieferung, ohne sudo, aber in der docker-Gruppe
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy

# Docker vorhanden und startbar?
docker --version && docker compose version && systemctl is-active docker
```

Danach einmal `sudo -u deploy -H docker ps` — laeuft das ohne „permission denied", stimmt die Gruppe.
Neue Gruppen greifen erst nach neuer Anmeldung.

## 2. Deploy-Schluessel einrichten

Auf dem eigenen Rechner ein **eigenes Schluesselpaar ohne Passphrase** erzeugen (nicht den persoenlichen Schluessel nehmen):

```bash
ssh-keygen -t ed25519 -f ~/.ssh/wsp3_1-deploy -N "" -C "gitlab-ci deploy wsp3_1"
```

Auf der VM den oeffentlichen Teil hinterlegen:

```bash
sudo -u deploy mkdir -p /home/deploy/.ssh && sudo -u deploy chmod 700 /home/deploy/.ssh
# Inhalt von ~/.ssh/wsp3_1-deploy.pub in /home/deploy/.ssh/authorized_keys eintragen
sudo -u deploy chmod 600 /home/deploy/.ssh/authorized_keys
```

In GitLab unter **Settings → CI/CD → Variables** zwei Variablen anlegen:

| Variable | Wert | Flags |
|---|---|---|
| `SSH_PRIVATE_KEY` | Inhalt von `~/.ssh/wsp3_1-deploy` (privater Teil, mit Zeilenumbruechen) | Protected, Masked nicht moeglich (mehrzeilig) |
| `SSH_KNOWN_HOSTS` | Ausgabe von `ssh-keyscan -p 1522 134.245.1.240` | Protected |

Der Job `deploy:vm` in `.gitlab-ci.yml` liest genau diese beiden Namen. Zugangsdaten stehen **nie** im Repo.

## 3. Verzeichnis und Konfiguration

```bash
sudo mkdir -p /opt/timetable && sudo chown deploy:deploy /opt/timetable
sudo -u deploy git clone <repo-url> /opt/timetable        # oder: der Deploy-Job rsynct dorthin
cd /opt/timetable/deployment
cp -n .env.example .env
```

`.env` auf der VM anpassen — **echtes Passwort**, VM-Ports:

```
ADMIN_INITIAL_PASSWORD=<langes zufaelliges Passwort, im Passwortspeicher des Teams>
DB_NAME=timetable
DB_USER=tt
DB_PASSWORD=<eigenes Passwort, nicht tt>
WEB_PORT=8080
MOCK_PORT=8081        # wirkt nur mit docker-compose.dev.yml (Attrappe); der Stack braucht ihn nicht
```

`ADMIN_INITIAL_PASSWORD` wirkt nur beim allerersten Start gegen eine leere Datenbank. Fehlt der Wert,
bricht `docker compose` sofort ab („ADMIN_INITIAL_PASSWORD fehlt in deployment/.env") — Absicht.
`deployment/.env` ist per `.gitignore` vom Repo ausgeschlossen und bleibt auf der VM.

**Deploy-fest.** `deployment/.env` und `deployment/backups/` sind die zwei Pfade auf der VM, die kein Deploy
anfassen darf: Der Job `deploy:vm` fährt `rsync --delete`, und was nicht im Checkout liegt, löscht er sonst.
Beide stehen darum in `scripts/deploy-vm.sh` als `--exclude`. Wer die rsync-Zeile ändert, prüft zuerst, ob
die beiden Ausnahmen noch drin sind — sonst ist nach dem nächsten Deploy das Passwort weg und der Stack
startet nicht mehr. Weitere Dateien, die nur auf der VM entstehen, gehören in dieselbe Liste.

## 4. Starten

Ein Befehl, danach ist die Web-App im Browser benutzbar:

```bash
cd /opt/timetable/deployment
docker compose --profile beispieldaten up -d --build
docker compose ps
```

Was dabei passiert, in dieser Reihenfolge: `db` startet und meldet sich gesund → `backend` startet, Flyway
legt das Schema an, `AdminBootstrap` legt das Konto `admin` mit `ADMIN_INITIAL_PASSWORD` an → `web` (nginx)
startet erst, wenn `/api/health` antwortet → `seed` meldet sich als `admin` an, laedt den Beispielbestand
(12 Klassen, 24 Lehrkraefte, 18 Raeume), laesst den Solver rechnen, nimmt den Plan an und beendet sich mit 0.

**Ohne `--profile beispieldaten` bleibt die Datenbank leer.** Das ist der richtige Weg fuer eine Schule, die
ihre eigenen Daten eingibt: dann faellt `seed` weg, alles andere ist gleich. Fuer die Abnahme und fuer die
Liveschaltung am 20.09. will man die Beispieldaten.

Gemessen am 05.09. auf einem Mac (Apple Silicon, Docker Desktop 29.1.2), Abbilder im Cache: `up` kehrt nach
**34 s** zurueck, der Beispielbestand ist rund **25 s** spaeter fertig gerechnet. Ohne Cache dauert der
erste Build Minuten, weil Gradle und npm im Container laden. **Auf der VM ist das nicht gemessen** — 4 GB
RAM sind fuer zwei Container-Builds knapp, siehe Abschnitt 6 und 12.

Sind die Abbilder schon aus der Pipeline in der Registry (Job `image:build` gelaufen), baut die VM gar nicht:

```bash
cd /opt/timetable/deployment
docker compose pull backend web              # REGISTRY_IMAGE steht in .env
docker compose --profile beispieldaten up -d
```

`docker-compose.dev.yml` wird auf der VM **nicht** benutzt: Datenbank und Backend bleiben unveroeffentlicht,
nach aussen sichtbar ist nur `web`. Seit dem 05.09. steht auch die Prism-Attrappe `mock` in dieser Datei und
laeuft damit auf der VM gar nicht mehr — sie gehoert nicht zum Produkt (Befund 05.09.). Wer sie vor dem 20.09.
doch auf der VM braucht:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mock
```
## 5. Pruefen

Auf der VM:

```bash
curl -s localhost:8080/api/health          # {"status":"UP","schoolName":"..."} aus dem Backend ueber nginx
docker compose logs -f backend | head -50  # Flyway: "Successfully applied 5 migrations"; "Started BackendApplication"
docker compose exec backend date           # muss CEST/CET zeigen, nicht UTC
```

**Zeitzone.** Der letzte Befehl ist keine Spielerei. Alles Datierte haengt am Kalendertag: der
Verlaufseintrag beim Annehmen, die Zaehlung verfallender Vertretungen, die laufende Woche in „Mein Plan".
Lief der Container in UTC (so war es bis zum 05.09.), lag zwischen Mitternacht und 2 Uhr Berliner Zeit das
Datum einen Tag daneben. Gesetzt wird die Zeitzone an zwei Stellen: `ENV TZ=Europe/Berlin` im
`backend/Dockerfile` und `TZ` beim Dienst `backend` in `docker-compose.yml` — die zweite gewinnt und
laesst sich ohne Neubau aendern (`TZ=…` in `.env`).

Von aussen (eigener Rechner):

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://134.245.1.240:1500/login    # 200
curl -s http://134.245.1.240:1500/api/health
curl -s http://134.245.1.240:1501/api/health
```

Erreicht der Browser die Seite, aber `/api/health` nicht, liegt es an nginx → Backend (`docker compose logs web`).
Antwortet gar nichts, ist die Portfreigabe der VM die erste Verdaechtige — nicht die Software.

Die Attrappe laeuft auf der VM nicht mehr mit (siehe Abschnitt 4). Wer sie mit `docker-compose.dev.yml`
doch startet und `docker compose ps mock` zeigt `Restarting`: Log ansehen (`docker compose logs mock`).
Der Dienst ist auf `stoplight/prism:5.14.2` festgelegt und startet mit `-m false` in einem Prozess; der
bewegliche Tag `5` brach mit „Cannot read properties of undefined (reading 'isPrimary')" ab. Wer die
Version anhebt, prueft zuerst `curl -s localhost:8081/api/health` (nur bei gestarteter Attrappe).

## 6. Speicher — 4 GB sind knapp

Startwerte in `docker-compose.yml` (`mem_limit`): db 512 MB, backend 1536 MB, web 64 MB (die Attrappe mit
ihren 256 MB laeuft auf der VM nicht mehr mit).
Das sind **Annahmen, keine Messungen**. Am Tag der Einrichtung messen (Karte T-704) und das Ergebnis als
eine Zeile in `07-Konzept/workflows/lernnotizen.md` schreiben:

```bash
free -m
docker stats --no-stream
```

Ist es eng: Die Attrappe faellt schon weg — sie laeuft seit dem 05.09. nur noch mit `docker-compose.dev.yml`
und damit auf der VM gar nicht. Erster Hebel: `backend` auf 1024 MB, dafuer
`timefold.solver-manager.parallel-solver-count=1` beibehalten (steht schon so in `application.properties`).

## 7. Beispieldaten nachtraeglich laden

Wenn der Stack schon ohne Profil laeuft:

```bash
cd /opt/timetable/deployment
./beispieldaten.sh                      # liest .env, spricht nginx auf WEB_PORT an
```

oder ueber Compose, mit demselben Ergebnis:

```bash
docker compose --profile beispieldaten up seed
docker compose logs seed                # geladen: {"classes":12,"teachers":24,"rooms":18}
```

Zweimal ausfuehren schadet nicht: sind schon Grunddaten da, antwortet die API 409 `data-exists`, das Skript
meldet „schon vorhanden" und endet mit 0. Ueberschrieben wird nichts. Wer den Bestand wirklich ersetzen will,
nimmt in der Oberflaeche die Rueckfrage „Ersetzen?" — die schickt `{"replace": true}`.

## 8. Ausliefern im Alltag

1. Merge Request gemergt, Pipeline auf `main` gruen.
2. Ab 20.09.: **erst sichern** (Abschnitt 9).
3. Job `deploy:vm` in GitLab von Hand ausloesen.
4. Danach Abschnitt 5 wiederholen.

Der Job ruft nur `scripts/deploy-vm.sh` auf. Dasselbe Skript laeuft von Hand, wenn GitLab klemmt:

```bash
# auf dem eigenen Rechner, im Repo-Verzeichnis
DRY_RUN=1 scripts/deploy-vm.sh     # zeigt jeden Befehl, beruehrt nichts
scripts/deploy-vm.sh               # sichern, kopieren, neu starten, pruefen
```

Das Skript macht vier Dinge: es sichert die Datenbank auf der VM, kopiert den Stand per `rsync --delete`
nach `/opt/timetable`, startet den Stack neu und ruft am Ende `/api/health` auf. Ist `REGISTRY_IMAGE`
gesetzt, holt es die Abbilder (`docker compose pull`), statt sie auf der VM zu bauen — der Weg, der auf
4 GB RAM sicher durchlaeuft.

Ganz von Hand geht dasselbe direkt auf der VM:

```bash
cd /opt/timetable && git pull --ff-only
cd deployment && ./backup.sh && docker compose up -d --build && docker compose ps
```

**Deploy-fest.** `deployment/.env` und `deployment/backups/` sind die zwei Pfade, die kein Deploy anfassen
darf — sie stehen im `.gitignore`, liegen also nie im Checkout, und `rsync --delete` wuerde sie entfernen.
Beide stehen darum in `scripts/deploy-vm.sh` als `--exclude`. Wer die rsync-Zeile aendert, prueft zuerst,
ob die beiden Ausnahmen noch drin sind — sonst ist nach dem naechsten Deploy das Passwort weg, alle
Sicherungen sind weg, und der Stack startet mit „ADMIN_INITIAL_PASSWORD fehlt in deployment/.env" nicht mehr.

## 8a. Was der Deploy seit dem 05.09. zusaetzlich tut (Befunde BETRIEB-3, 6, 7, 9, 10)

* Er baut `backend` und `web` **nacheinander** statt gleichzeitig — auf 4 GB RAM ist der parallele Bau
  neben dem laufenden Stack der wahrscheinlichste Grund fuer einen Abbruch.
* Er kopiert `app/` (33-MB-APK), `docs/` (Abnahmeunterlagen, ~8 MB), `android-starter/` und lokale
  Baustaende **nicht** mehr mit — rund 130 MB weniger je Deploy.
* Er fragt den veroeffentlichten Port bei Compose ab, statt 8080 anzunehmen; steht in der `.env` der VM
  ein anderer `WEB_PORT`, endet der Job nicht mehr faelschlich rot.
* Er raeumt am Ende unbenutzte Abbilder auf (`docker image prune -f`).
* Die Sicherung (`backup.sh`) schreibt erst in eine `.tmp`-Datei, prueft `pg_dump`-Rueckgabewert,
  gzip-Pruefsumme und Mindestgroesse und benennt erst dann um; sie behaelt die juengsten
  `BACKUP_KEEP` (Standard 14) Staende.
* Die Container protokollieren mit Obergrenze (`json-file`, 3 x 10 MB) — vorher wuchsen die Logdateien
  unbegrenzt.

## 9. Datenbank sichern

```bash
cd /opt/timetable/deployment
./backup.sh
```

Das Skript ruft `pg_dump` im Container `db` auf und schreibt nach `backups/timetable-JJJJ-MM-TT-hhmm.sql.gz`.
Benutzer und Datenbankname liest es aus `.env`. Laeuft der Stack nicht, bricht es mit einer Meldung ab.

Gemessen am 05.09. gegen den Beispielbestand: die Datei ist **11 KB** gross (12 Klassen, 24 Lehrkraefte,
18 Raeume, 360 Stunden, 22 Tabellen). Ein Schuljahr echter Daten wird groesser sein, aber nicht um
Groessenordnungen — Platz ist auf 50 GB kein Thema.

Wann sichern:

| Wann | Warum |
|---|---|
| vor **jedem** Deploy ab dem 20.09. | ab da sind es Daten, die jemand eingegeben hat |
| vor jeder Migration, die etwas entfernt oder umbaut | additive Migrationen sind rueckwaerts sicher, andere nicht |
| am Abend des 19.09. und des 23.09. | vor der Liveschaltung und vor der Anforderungsaenderung |

`scripts/deploy-vm.sh` ruft `backup.sh` von sich aus als erstes auf. Schlaegt das fehl (erster Deploy, es
gibt noch keinen Stack), laeuft der Deploy weiter — danach nicht mehr ignorieren.

Sicherungen liegen nur auf der VM. Wer eine wegschaffen will:

```bash
scp -P 1522 deploy@134.245.1.240:/opt/timetable/deployment/backups/timetable-2026-09-20-1830.sql.gz .
```

## 10. Ruecksicherung und Rollback

Drei Faelle. Erst feststellen, welcher vorliegt — die Antworten sind verschieden.

### Fall A: die neue Fassung ist kaputt, die Daten sind in Ordnung

Der haeufigste Fall: ein Deploy ging schief, in der Datenbank steht aber nichts Falsches. **Nur den Code
zurueckdrehen, die Datenbank bleibt unberuehrt.**

> **Achtung, Befund BETRIEB-5 (05.09.): auf der VM liegt kein Git-Arbeitsbaum.** `scripts/deploy-vm.sh`
> kopiert den Stand per `rsync --delete --exclude .git` — ein `git log`/`git checkout` dort scheitert
> entweder mit „not a repository" oder dreht einen Arbeitsbaum zurueck, den rsync danach wieder
> ueberschreibt. **Der einzige Weg, der ohne Vorarbeit funktioniert:** den Deploy vom eigenen Rechner
> (oder als GitLab-Pipeline) auf dem alten Stand noch einmal ausfuehren —
>
> ```bash
> git checkout <kennung>              # auf dem eigenen Rechner
> scripts/deploy-vm.sh                # kopiert genau diesen Stand auf die VM und startet ihn
> ```
>
> Beide Wege unten sind **nirgends erprobt**; vor dem 20.09. einmal mit zwei Staenden durchspielen
> (Karte T-702 bzw. T-71x).

```bash
# nur, wenn auf der VM wirklich ein Klon liegt (nicht der rsync-Stand)
cd /opt/timetable
git log --oneline -5                       # Kennung des letzten guten Standes suchen
git checkout <kennung>
cd deployment && docker compose build backend && docker compose build web && docker compose up -d && docker compose ps
curl -s "localhost:$(docker compose port web 8080 | cut -d: -f2)/api/health"
```

Mit Abbildern aus der Registry geht es ohne Bau — jedes Abbild traegt die kurze Commit-Kennung als Marke.
**Beide Werte sind noetig:** ohne `REGISTRY_IMAGE` sucht Compose das Abbild unter dem lokalen Namen
`stundenplan/backend:<marke>` und findet nichts (Befund BETRIEB-5).

```bash
cd /opt/timetable/deployment
export REGISTRY_IMAGE=<registry>/<pfad> IMAGE_TAG=<kurze-kennung>
docker compose pull backend web
docker compose up -d
```

Voraussetzung dafuer ist, dass `image:build` fuer diesen Commit einmal gelaufen ist — der Job ist
`when: manual` und wurde bisher nie ausgefuehrt.

Das funktioniert, **weil die Migrationen additiv sind**: eine aeltere Fassung der Anwendung findet in der
neueren Datenbank alle Spalten vor, die sie kennt, und die neuen stoeren sie nicht. Genau dafuer gilt ab dem
Vertrags-Freeze am 12.09.: **nur additive Migrationen** — neue Tabellen, neue Spalten mit Standardwert;
nichts umbenennen, nichts loeschen. Wer diese Regel bricht, nimmt dem Team den Rollback.

### Fall B: die Daten sind kaputt

Falsche Daten eingespielt, versehentlich geleert, eine Migration hat etwas zerstoert. **Sicherung
zurueckspielen.** Der Stack muss laufen, `db` muss erreichbar sein.

```bash
cd /opt/timetable/deployment

# 1. Erst den JETZIGEN Stand sichern — auch einen kaputten. Man braucht ihn, wenn die
#    Ruecksicherung schiefgeht oder jemand spaeter fragt, was eigentlich drinstand.
./backup.sh

# 2. Anwendung anhalten, damit waehrenddessen niemand schreibt. `db` bleibt an.
docker compose stop backend web

# 3. Schema leeren, dann die Sicherung einspielen. `-T` ist noetig: ohne das Flag legt docker
#    ein Terminal an, und die Datei kommt nicht durch die Pipe.
docker compose exec -T db psql -q -U tt -d timetable \
  -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
gunzip -c backups/timetable-2026-09-20-1830.sql.gz \
  | docker compose exec -T db psql -q -U tt -d timetable

# 4. Anwendung wieder starten. Flyway prueft beim Start, dass das Schema zur Fassung passt.
docker compose start backend web
docker compose logs backend --tail 20

# 5. Pruefen
curl -s localhost:8080/api/health
```

Benutzer und Datenbankname sind die Werte aus `.env` (`DB_USER`, `DB_NAME`); oben stehen die Standardwerte
`tt` und `timetable`.

Zwischenpruefung, wenn man wissen will, ob Schritt 3 gegriffen hat — die Zahl muss von 0 auf 22 springen:

```bash
docker compose exec -T db psql -tA -U tt -d timetable \
  -c "select count(*) from information_schema.tables where table_schema='public'"
```

**Der haeufigste Fehler dabei:** eine Sicherung einspielen, die aus einer **aelteren** Fassung des Schemas
stammt, ohne den Code mitzudrehen. Dann startet das Backend nicht, weil `spring.jpa.hibernate.ddl-auto=validate`
Tabellen erwartet, die in der Sicherung fehlen. Abhilfe: zuerst Fall A (Code auf den Stand von damals),
dann Fall B.

### Fall C: ganz von vorn

Nur, wenn nichts zu retten ist und die Daten egal sind — also vor dem 20.09., nicht danach.

```bash
cd /opt/timetable/deployment
./backup.sh                                       # trotzdem; kostet nichts
docker compose --profile beispieldaten down -v    # -v wirft das Volume pgdata weg. Endgueltig.
docker compose --profile beispieldaten up -d --build
```

`down -v` ist der einzige Befehl in dieser Anleitung, der Daten unwiederbringlich loescht. Ausschreiben,
nicht in ein Skript stecken, nie ohne vorherige Sicherung.

### Was davon ausprobiert ist

Fall B ist am 05.09. vollstaendig gegen den Compose-Stack auf einem Mac gelaufen: sichern (11 KB), Schema
leeren (`public` hatte danach 0 Tabellen), zurueckspielen (22 Tabellen), Backend und nginx wieder
hochfahren. Bestand davor und danach identisch — 12 Klassen, 24 Lehrkraefte, 18 Raeume, 360 Stunden im
aktiven Plan. **Auf der VM ist keiner dieser Schritte gelaufen.** Fall A ist nirgends ausprobiert; dafuer
braucht es zwei Staende auf `main`.

## 11. Umzug auf einen anderen Server (auch: die Schule)

Es gibt keine Adresse im Build. Web-App und App sprechen den relativen Pfad `/api`; nginx kennt das Backend
unter dem Compose-Dienstnamen. Umzug heisst deshalb:

1. Repo auf den neuen Rechner holen.
2. `deployment/.env` schreiben (Ports, Passwoerter).
3. `docker compose up -d --build`.

Kein Neubau des Frontends, kein Neukompilieren. Nur die Android-App braucht die neue Adresse in ihren
Einstellungen (A-3) — dort steht sie als Ganzes und ist ohne Neubau aenderbar.

## 11a. Offene Entscheidung: TLS (Befund SICHERHEIT-2, 05.09.)

**Der ganze Weg laeuft heute unverschluesselt.** nginx lauscht auf 8080 ohne `ssl`, die Zieladresse ist
`http://134.245.1.240:1500`, und die Android-App erlaubt Klartext global
(`network_security_config.xml`, `cleartextTrafficPermitted="true"`). Damit gehen Anmeldedaten, die
Erstkennwoerter der Klassenkonten (`POST /api/users/class-accounts`) und das 30-Tage-Token im Klartext
durchs Uni-Netz. Im Rueckgrat ist TLS ausdruecklich abgegrenzt („Aufgabe der Schul-Infrastruktur",
`backbone/api.md`) — im Uni-WLAN ist Mitlesen aber realistisch.

**Der Auftraggeber entscheidet vor der Abnahme eines von dreien:**

1. **So lassen** und die Abgrenzung im Pflichtenheft/der Entwurfsdoku als Annahme benennen. Dann
   wenigstens die Token-Frist kuerzen: `AUTH_TOKEN_TTL=PT12H` in `deployment/.env` der VM.
2. **Selbstsigniertes Zertifikat** auf der VM: zweiter `server`-Block in `nginx.conf` mit
   `listen 8444 ssl`, Zertifikat als Volume, `WEB_PORT_TLS=8444` in `.env` — die NAT-Tabelle hat mit
   8444–8449 → 1504–1509 freie HTTPS-Ports (Abschnitt 0). Android: `base-config` auf `false`,
   Klartext nur fuer `10.0.2.2`/`localhost`. Aufwand: ein halber Tag, Browser zeigt eine Warnung.
3. **Zertifikat der Uni/Schule** besorgen (dann ohne Warnung) — braucht eine Zusage von aussen.

Nichts davon ist gebaut; solange nichts entschieden ist, gilt Weg 1 ohne die Kuerzung.

## 12. Rueckfall, falls der Build auf der VM kippt

4 GB RAM koennen fuer einen Gradle- und einen npm-Build im Container zu wenig sein. Wenn
`docker compose up -d --build` mit „Killed" oder einem OOM-Fehler abbricht, gibt es zwei Wege.

**Der bessere: auf der VM gar nicht bauen.** Den Job `image:build` auf `main` ausloesen, dann holt die VM die
fertigen Abbilder (Abschnitt 4, letzter Block). Dafuer muss der Runner Docker-in-Docker koennen. Was er
kann, sagt der Job `runner:check` in `.gitlab-ci.yml` — ein Klick, keine Nebenwirkung. Kann er es nicht,
steht im Kommentar bei `image:build` der Weg ueber Kaniko (ungeprueft).

**Der zweite: fertige Artefakte von Hand.**

1. In GitLab die Artefakte der gruenen Pipeline holen: `backend/build/libs/app.jar` (Job `backend:test`)
   und `frontend/dist` (Job `frontend:build`).
2. Beide auf die VM legen (`/opt/timetable/backend/build/libs/`, `/opt/timetable/frontend/dist/`).
3. Die Dockerfiles voruebergehend auf reines Kopieren umstellen — `backend/Dockerfile`:
   `FROM eclipse-temurin:21-jre` + `COPY backend/build/libs/app.jar /app/app.jar`;
   `frontend/Dockerfile`: `FROM nginx:alpine` + `COPY frontend/dist /usr/share/nginx/html` +
   `COPY deployment/nginx.conf /etc/nginx/conf.d/default.conf`.
4. `docker compose up -d` (ohne `--build` fuer die Stufen, die nicht mehr bauen).

Das ist der in `backbone/pipeline.md` festgehaltene Rueckfall. Er kostet die Reproduzierbarkeit auf der VM,
nicht die Auslieferung. Ob er noetig ist, stellt T-704 am Tag 1 fest — nicht vorher raten.

## 13. Liveschaltung 20.09.

- `docker compose ps` zeigt drei Dienste `running` (db, backend, web — die Attrappe `mock` gehoert seit dem
  05.09. nur noch zu `docker-compose.dev.yml`).
  `seed` steht auf `Exited (0)` — das ist richtig, er laeuft einmal und ist dann fertig.
- Beispieldaten geladen (Abschnitt 4 oder 7), Konten fuer die Betreuenden angelegt (Karte T-405).
- Infoseite im GitLab-Wiki: Adresse `http://134.245.1.240:1500`, Konten, Hinweis auf die Beispieldaten.
- URL im Mattermost posten.
- Sicherung vor jedem weiteren Deploy (Abschnitt 9).
