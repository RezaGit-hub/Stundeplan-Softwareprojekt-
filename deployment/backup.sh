#!/usr/bin/env bash
# Sicherung der Datenbank aus dem laufenden Compose-Stack nach deployment/backups/.
#
#   cd deployment && ./backup.sh
#
# Ab dem 20.09. vor jedem Deploy von Hand ausfuehren (Regel aus backbone/pipeline.md).
# Zurueckspielen:
#   gunzip -c backups/timetable-2026-09-20-1830.sql.gz | docker compose exec -T db psql -U tt -d timetable
#
# Drei Aenderungen vom 05.09. (Befund BETRIEB-10):
#  * Erst in eine .tmp-Datei schreiben, dann pruefen, dann umbenennen. Vorher schrieb `pg_dump | gzip`
#    direkt ins Ziel: brach der Dump ab (Stack steht, falscher Benutzer, Neustart), blieb eine leere
#    oder halbe Datei mit gueltigem Namen liegen — und die Ruecksicherung waehlt nur nach Namen.
#  * Geprueft werden der Rueckgabewert von pg_dump (PIPESTATUS), die gzip-Pruefsumme und eine
#    Mindestgroesse; erst dann bekommt die Datei ihren endgueltigen Namen.
#  * Aufbewahrung: die juengsten BACKUP_KEEP (Standard 14) Sicherungen bleiben liegen, aeltere gehen weg.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "deployment/.env fehlt — erst 'cp -n .env.example .env' und Werte setzen." >&2
  exit 1
fi

# .env einlesen (Zeilen NAME=WERT; Kommentare und Leerzeilen ignoriert docker compose ebenso).
# Achtung: die Shell liest die Datei als Skript. Ein Kennwort mit Leerzeichen, `#`, `$` oder
# Anfuehrungszeichen bricht hier ab oder wird anders ausgelegt als von docker compose — deshalb steht
# die Zeichenregel (nur A-Za-z0-9._-) in .env.example.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

DB_USER="${DB_USER:-tt}"
DB_NAME="${DB_NAME:-timetable}"
KEEP="${BACKUP_KEEP:-14}"
# Kleiner als das kann kein echter Dump sein (ein leeres gzip sind 20 Byte).
MIN_BYTES=200

mkdir -p backups
target="backups/${DB_NAME}-$(date +%F-%H%M).sql.gz"
tmp="${target}.tmp"

aufraeumen() {
    [ -f "$tmp" ] && unlink "$tmp"
    return 0
}
trap aufraeumen EXIT

echo "Sichere Datenbank '${DB_NAME}' nach ${target} ..."
set +e
docker compose exec -T db pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$tmp"
status=("${PIPESTATUS[@]}")
set -e
if [ "${status[0]}" -ne 0 ] || [ "${status[1]}" -ne 0 ]; then
    echo "pg_dump oder gzip ist fehlgeschlagen (Exit ${status[0]}/${status[1]}) — nichts geschrieben." >&2
    exit 1
fi
if ! gzip -t "$tmp" 2>/dev/null; then
    echo "Die Sicherung ist unvollstaendig (gzip -t schlaegt fehl) — nichts geschrieben." >&2
    exit 1
fi
groesse=$(wc -c < "$tmp" | tr -d ' ')
if [ "$groesse" -lt "$MIN_BYTES" ]; then
    echo "Die Sicherung ist nur ${groesse} Byte gross — das ist kein vollstaendiger Dump." >&2
    exit 1
fi

mv "$tmp" "$target"
trap - EXIT

# Aufbewahrung: die juengsten $KEEP behalten, aeltere entfernen. Ohne das laeuft die 50-GB-VM in drei
# Wochen voll — und es faellt erst auf, wenn die naechste Sicherung scheitert.
if [ "$KEEP" -gt 0 ]; then
    ls -1t backups/"${DB_NAME}"-*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r alt; do
        echo "raeume auf: $alt"
        unlink "$alt"
    done
fi

echo "fertig: $(ls -lh "$target" | awk '{print $5, $9}')"
echo "Hinweis: die Sicherung liegt nur auf dieser Maschine — eine Kopie woanders (scp) gehoert dazu."
