#!/usr/bin/env bash
# Faehrt lokal genau das, was die Pipeline in den Jobs backend:test und backend:integration tut:
# eine Wegwerf-Datenbank starten, beide Testaufgaben im Container ausfuehren, Datenbank wieder anhalten.
# Es wird kein JDK und kein Gradle auf dem Rechner gebraucht — nur Docker.
#
#   scripts/ci-backend-test.sh                          # normaler Lauf
#   scripts/ci-backend-test.sh --tests 'de.wsp3_1.rules.*'   # Argumente gehen an Gradle durch
#
# Gradle haelt Testaufgaben bei unveraenderten Eingaben fuer UP-TO-DATE und fuehrt sie nicht erneut aus.
# Deshalb steht --rerun hier fest hinter BEIDEN Aufgaben: --rerun ist eine Aufgaben-Option und gilt nur
# fuer die Aufgabe davor — `gradle test integrationTest --rerun` laesst `test` UP-TO-DATE (gemessen
# 05.09.). Ein eigenes --rerun ist also nicht mehr noetig; wer alles neu bauen will, nimmt --rerun-tasks.
#
# Mehrere Laeufe gleichzeitig (sieben Personen, ein Runner): Netz, Wegwerf-Datenbank und Gradle-Ablage
# lassen sich ueber CI_NETWORK, CI_DB und CI_GRADLE_CACHE umbenennen. Der Standard fuer die Datenbank
# traegt die Prozessnummer, damit ein zweiter Lauf nicht den Container des ersten anhaelt.
#
# Ergebnis: "BUILD SUCCESSFUL". Die Testberichte liegen danach unter backend/build/reports/tests/.
set -euo pipefail
cd "$(dirname "$0")/.."

NETWORK=${CI_NETWORK:-stundenplan-ci}
DB_CONTAINER=${CI_DB:-stundenplan-ci-db-$$}
GRADLE_IMAGE=gradle:9.7.1-jdk21          # gleiche Gradle-Version wie der Wrapper im Repo
POSTGRES_IMAGE=postgres:16-alpine        # gleiche Datenbankversion wie im Betrieb
GRADLE_CACHE=${CI_GRADLE_CACHE:-stundenplan-gradle}   # Docker-Volume, damit der zweite Lauf schnell ist

echo "==> Netz $NETWORK bereitstellen"
docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK" >/dev/null
docker volume inspect "$GRADLE_CACHE" >/dev/null 2>&1 || docker volume create "$GRADLE_CACHE" >/dev/null

stop_db() {
    echo "==> Wegwerf-Datenbank anhalten"
    docker stop "$DB_CONTAINER" >/dev/null 2>&1 || true
}
trap stop_db EXIT

echo "==> Wegwerf-Datenbank starten ($POSTGRES_IMAGE)"
docker stop "$DB_CONTAINER" >/dev/null 2>&1 || true
# `docker stop` kehrt zurueck, bevor `--rm` den alten Container weggeraeumt hat. Startet man sofort
# neu, trifft man den halb abgeraeumten Vorgaenger — das sah einmal wie „rejecting connections" aus.
for _ in $(seq 1 30); do
    docker container inspect "$DB_CONTAINER" >/dev/null 2>&1 || break
    sleep 1
done
docker run -d --rm=true --name "$DB_CONTAINER" --network "$NETWORK" \
    -e POSTGRES_DB=timetable -e POSTGRES_USER=tt -e POSTGRES_PASSWORD=tt \
    "$POSTGRES_IMAGE" >/dev/null

echo "==> Auf die Datenbank warten"
for _ in $(seq 1 60); do
    if docker exec "$DB_CONTAINER" pg_isready -U tt -d timetable >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
if ! docker exec "$DB_CONTAINER" pg_isready -U tt -d timetable; then
    echo "Die Wegwerf-Datenbank war nach 60 Sekunden nicht bereit. Log:" >&2
    docker logs "$DB_CONTAINER" 2>&1 | tail -20 >&2
    exit 1
fi

echo "==> Tests im Container ausfuehren"
docker run --rm=true --network "$NETWORK" \
    -v "$PWD/backend":/w -w /w \
    -v "$GRADLE_CACHE":/home/gradle/.gradle \
    -e SPRING_PROFILES_ACTIVE=ci \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_CONTAINER:5432/timetable" \
    -e SPRING_DATASOURCE_USERNAME=tt \
    -e SPRING_DATASOURCE_PASSWORD=tt \
    -e ADMIN_INITIAL_PASSWORD=admin \
    "$GRADLE_IMAGE" gradle --no-daemon test --rerun integrationTest --rerun "$@"
