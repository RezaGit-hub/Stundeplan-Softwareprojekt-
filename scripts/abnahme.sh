#!/usr/bin/env bash
# Abnahme des Leitfaden-Durchlaufs gegen einen laufenden Stack (Bauphase backend3).
#
# Faehrt die Schritte der Abnahme-Checkliste ab, die ohne Browser pruefbar sind: Grunddaten, Regelplan,
# Sichten (P-7 / T-W4.1), Leitszenario Ausfall und Vertretung (V-1 bis V-6), Rolle Lehrkraft
# (A-1 bis A-6) und Ferien (T-110). Die Arbeit macht scripts/abnahme-substitution.py; diese Huelle
# sorgt fuer die Voraussetzung, die dabei am haeufigsten fehlt — eine LEERE Datenbank.
#
#   scripts/abnahme.sh                # gegen den laufenden Stack, Datenbank muss leer sein
#   scripts/abnahme.sh --frisch       # Stack vorher mit leerer Datenbank neu starten
#
# Umgebung (Standardwerte wie im regulaeren Stack):
#   ABNAHME_BASE       Adresse, Standard http://127.0.0.1:1500 (nginx)
#   ABNAHME_ADMIN_PW   Passwort des Kontos `admin` — Pflicht, kein Standardwert. Der Wert steht in
#                      deployment/.env (ADMIN_INITIAL_PASSWORD); fehlt er, bricht das Skript ab.
#
# Ergebnis: „Alle Zusicherungen halten." und Exit-Code 0, sonst die Liste der Abweichungen und 1.
set -euo pipefail
cd "$(dirname "$0")/.."

# Kein Standardkennwort im Skript (Befund SICHERHEIT-1, 05.09.) — der Wert kommt aus der Umgebung.
# Die Meldung nennt bewusst nur den Weg, nie den Wert.
: "${ABNAHME_ADMIN_PW:?fehlt. Wert aus deployment/.env (ADMIN_INITIAL_PASSWORD) setzen und voranstellen.}"
export ABNAHME_ADMIN_PW

BASE=${ABNAHME_BASE:-http://127.0.0.1:1500}
FRISCH=false
if [ "${1:-}" = "--frisch" ]; then
    FRISCH=true
fi

if [ "$FRISCH" = true ]; then
    echo "==> Stack mit leerer Datenbank neu starten"
    # `down -v` wirft das Datenbankvolumen weg — genau das ist hier gewollt und der Grund, warum die
    # Option ausgeschrieben werden muss. Ohne --frisch faesst dieses Skript keinen Container an.
    ( cd deployment \
      && docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v \
      && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build )
fi

echo "==> Auf den Server warten ($BASE/api/health)"
# Auf HTTP 200 pruefen, nicht auf den Exit-Code von curl: nginx antwortet beim Start eine Weile mit
# 502, und das ist fuer curl ein erfolgreicher Aufruf (Lernnotiz 05.09.).
for _ in $(seq 1 60); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/health" || true)" = "200" ]; then
        break
    fi
    sleep 1
done
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/health" || true)" != "200" ]; then
    echo "Der Server unter $BASE antwortet nicht. Laeuft der Stack?" >&2
    echo "  cd deployment && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d" >&2
    exit 1
fi

echo "==> Durchlauf gegen $BASE"
exec python3 scripts/abnahme-substitution.py
