#!/bin/sh
# Laedt den Beispielbestand (12 Klassen, 24 Lehrkraefte, 18 Raeume und einen gerechneten Plan)
# ueber die REST-API. Zwei Wege, beide fuehren hierher:
#
#   cd deployment && docker compose --profile beispieldaten up -d --build   # ein Befehl, alles drin
#   cd deployment && ./beispieldaten.sh                                     # nachtraeglich, vom Host
#
# Im Compose laeuft das Skript im Container `seed` (Abbild curlimages/curl) gegen http://backend:8080.
# Vom Host laeuft es gegen http://127.0.0.1:${WEB_PORT} — nginx reicht /api/ weiter.
#
# Absichtlich POSIX-sh und nur curl: das Abbild curlimages/curl bringt weder bash noch jq mit.
#
# Zweimal ausfuehren schadet nicht: sind schon Grunddaten da, antwortet die API 409 `data-exists`,
# und das gilt hier als „schon geladen". Ueberschrieben wird nichts — dafuer gibt es in der
# Oberflaeche die Rueckfrage „Ersetzen?".
set -eu

# Vom Host aufgerufen: deployment/.env einlesen (Passwort und Port stehen dort). Im Container `seed`
# gibt es die Datei nicht — dort setzt docker compose die Werte als Umgebungsvariablen.
if [ -f "$(dirname "$0")/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$(dirname "$0")/.env"
    set +a
fi

# Standard ist der Weg vom Host ueber nginx. Der Container `seed` setzt stattdessen
# SEED_BASE=http://backend:8080 und spricht das Backend direkt an.
BASE=${SEED_BASE:-http://127.0.0.1:${WEB_PORT:-1500}}
USER_NAME=${SEED_USER:-admin}
PASSWORD=${ADMIN_INITIAL_PASSWORD:?ADMIN_INITIAL_PASSWORD fehlt}

echo "==> Warten auf $BASE/api/health"
i=0
# Auf HTTP 200 pruefen, nicht auf den Exit-Code von curl: nginx antwortet beim Start eine Weile mit
# 502, und eine 502 ist fuer curl ein erfolgreicher Aufruf (Lernnotiz 05.09.).
while [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/health" || true)" != "200" ]; do
    i=$((i + 1))
    if [ "$i" -ge 90 ]; then
        echo "Der Server unter $BASE antwortet nach 90 Sekunden nicht." >&2
        exit 1
    fi
    sleep 1
done

echo "==> Anmelden als $USER_NAME"
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
    echo "Anmeldung fehlgeschlagen. Stimmt ADMIN_INITIAL_PASSWORD in deployment/.env?" >&2
    echo "Der Wert wirkt nur beim allerersten Start gegen eine leere Datenbank." >&2
    exit 1
fi

echo "==> Beispieldaten laden (POST /api/sample-data, rechnet rund 10 Sekunden)"
BODY=$(mktemp)
CODE=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$BASE/api/sample-data" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{}')

case "$CODE" in
    200|201)
        echo "geladen: $(cat "$BODY")"
        ;;
    409)
        echo "schon vorhanden — nichts geaendert (409 data-exists)."
        ;;
    *)
        echo "unerwartete Antwort $CODE: $(cat "$BODY")" >&2
        rm -f "$BODY"
        exit 1
        ;;
esac
rm -f "$BODY"
