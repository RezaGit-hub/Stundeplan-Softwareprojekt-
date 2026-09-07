#!/usr/bin/env bash
# Startet das Backend zum Entwickeln im Container — ohne JDK und ohne Gradle auf dem Rechner.
# Voraussetzung: die Compose-Datenbank laeuft.
#
#   (cd deployment && cp -n .env.example .env && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db)
#   scripts/dev-backend.sh
#
# Danach antwortet http://localhost:8080/api/health. Beenden mit Strg-C.
# Wer ein eigenes JDK 21 hat, nimmt stattdessen: cd backend && ./gradlew bootRun
#
# Ist 8080 belegt (auf macOS hoert dort oft der mitgelieferte Apache), gibt es zwei Wege:
#   sudo apachectl stop
# oder einen anderen Port waehlen — dann muss der Vite-Proxy mitziehen:
#   BACKEND_PORT=8089 scripts/dev-backend.sh
#   (cd frontend && VITE_PROXY_TARGET=http://localhost:8089 npm run dev)
set -euo pipefail
cd "$(dirname "$0")/.."

NETWORK=stundenplan_default              # Compose-Projekt heisst "stundenplan" (deployment/docker-compose.yml)
GRADLE_IMAGE=gradle:9.7.1-jdk21
GRADLE_CACHE=stundenplan-gradle
BACKEND_PORT=${BACKEND_PORT:-8080}       # veroeffentlichter Port; im Container bleibt es 8080

# Dieselben Datenbankwerte wie docker compose: erst deployment/.env, dann die Standardwerte aus
# .env.example. Sonst faende das Skript die Datenbank nicht mehr, sobald jemand DB_NAME oder
# DB_PASSWORD in seiner .env aendert.
if [ -f deployment/.env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./deployment/.env
    set +a
fi
DB_NAME=${DB_NAME:-timetable}
DB_USER=${DB_USER:-tt}
DB_PASSWORD=${DB_PASSWORD:-tt}
# Auch das Kennwort der Planung kommt aus deployment/.env und muss an den Container weitergereicht
# werden — `docker run` gibt nur weiter, was hier mit -e steht. Fehlt die Zeile, faellt das Profil dev
# auf seinen Standard `admin` zurueck, und wer .env pflegt, kaeme mit dem dortigen Kennwort nicht hinein.
# Der Standard gilt nur beim allerersten Start gegen eine leere Datenbank (AdminBootstrap).
ADMIN_INITIAL_PASSWORD=${ADMIN_INITIAL_PASSWORD:-admin}

if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
    echo "Das Docker-Netz $NETWORK fehlt." >&2
    echo "Bitte zuerst die Datenbank starten:" >&2
    echo "  (cd deployment && cp -n .env.example .env && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db)" >&2
    exit 1
fi

docker volume inspect "$GRADLE_CACHE" >/dev/null 2>&1 || docker volume create "$GRADLE_CACHE" >/dev/null

TTY_FLAG=()
if [ -t 0 ]; then
    TTY_FLAG=(-it)
fi

echo "==> Backend starten (Profil dev, Port $BACKEND_PORT, Datenbank db:5432/$DB_NAME im Netz $NETWORK)"
# ${TTY_FLAG[@]+"${TTY_FLAG[@]}"} statt "${TTY_FLAG[@]}": die macOS-Systembash 3.2 haelt ein leeres
# Array unter `set -u` fuer unbelegt und bricht ab, sobald kein Terminal angeschlossen ist.
docker run --rm=true ${TTY_FLAG[@]+"${TTY_FLAG[@]}"} --name stundenplan-dev-backend \
    --network "$NETWORK" -p "$BACKEND_PORT:8080" \
    -v "$PWD/backend":/w -w /w \
    -v "$GRADLE_CACHE":/home/gradle/.gradle \
    -e SPRING_PROFILES_ACTIVE=dev \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/$DB_NAME" \
    -e SPRING_DATASOURCE_USERNAME="$DB_USER" \
    -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    -e ADMIN_INITIAL_PASSWORD="$ADMIN_INITIAL_PASSWORD" \
    "$GRADLE_IMAGE" gradle bootRun --no-daemon
