#!/usr/bin/env bash
# Auslieferung auf die VM der Gruppe 5. Genau das ruft der CI-Job `deploy:vm` auf; von Hand geht es
# auch, wenn GitLab gerade nicht mitspielt.
#
# ACHTUNG: Dieses Skript ist bis heute NIE gelaufen. Der VM-Zugang wird erst in der ersten Woche
# eingerichtet (Karte T-702). Wer es zum ersten Mal ausfuehrt, traegt das Ergebnis in
# deployment/README-VM.md ein und ersetzt dort „noch nicht geprueft" durch die gesehene Ausgabe.
#
#   scripts/deploy-vm.sh              # rsync + Neustart auf der VM
#   DRY_RUN=1 scripts/deploy-vm.sh    # nur zeigen, was passieren wuerde — beruehrt nichts
#
# Voraussetzungen von Hand: ein SSH-Schluessel, mit dem `ssh -p 1522 deploy@134.245.1.240` ohne
# Passwort durchkommt. In der Pipeline legt der Job `deploy:vm` ihn aus SSH_PRIVATE_KEY in den Agenten.
#
# Umgebung (alles hat Standardwerte, nichts muss gesetzt werden):
#   VM_HOST         Adresse der VM, Standard 134.245.1.240
#   VM_PORT         SSH-Port von aussen, Standard 1522
#   VM_USER         Unix-Benutzer auf der VM, Standard deploy
#   VM_PATH         Zielverzeichnis, Standard /opt/timetable
#   REGISTRY_IMAGE  Registry-Pfad. Gesetzt -> `docker compose pull` statt Bau auf der VM.
#   IMAGE_TAG       Marke der Abbilder, Standard latest
set -euo pipefail
cd "$(dirname "$0")/.."

VM_HOST=${VM_HOST:-134.245.1.240}
VM_PORT=${VM_PORT:-1522}
VM_USER=${VM_USER:-deploy}
VM_PATH=${VM_PATH:-/opt/timetable}
IMAGE_TAG=${IMAGE_TAG:-latest}
SSH="ssh -p $VM_PORT $VM_USER@$VM_HOST"

# Mit Registry: die Abbilder kommen fertig aus der Pipeline, die VM holt sie nur. Das ist der Weg,
# der auf 4 GB RAM sicher durchlaeuft. Ohne Registry baut die VM selbst — funktioniert, kann aber am
# Speicher scheitern (README-VM.md, Abschnitt 9).
if [ -n "${REGISTRY_IMAGE:-}" ]; then
    # `export` und nicht `VAR=wert docker compose …`: ein vorangestelltes Zuweisungspaar gilt nur fuer
    # den EINEN Befehl dahinter. `docker compose up` nach dem && saehe die Werte sonst nicht und suchte
    # die Abbilder unter ihrem lokalen Namen (gemessen 05.09. mit DRY_RUN=1).
    REMOTE_UP="export REGISTRY_IMAGE='$REGISTRY_IMAGE' IMAGE_TAG='$IMAGE_TAG'; docker compose pull backend web && docker compose up -d"
    echo "==> Abbilder aus der Registry: $REGISTRY_IMAGE (Marke $IMAGE_TAG)"
else
    # Nacheinander bauen statt `up -d --build` (Befund BETRIEB-3, 05.09.): BuildKit baut sonst den
    # Gradle-Bau des Backends und den Vite-Bau des Frontends gleichzeitig, waehrend der alte Stack mit
    # 512 MB + 1536 MB Grenze weiterlaeuft — auf einer 4-GB-VM ist das der wahrscheinlichste Grund fuer
    # einen abgebrochenen Deploy. Der alte Stack bleibt dabei bis zum Tausch oben.
    REMOTE_UP="docker compose build backend && docker compose build web && docker compose up -d"
    echo "==> Kein REGISTRY_IMAGE gesetzt — die VM baut selbst (nacheinander)."
fi

# --delete raeumt auf der VM alles weg, was nicht im Checkout liegt. deployment/.env (Passwoerter)
# und deployment/backups/ stehen im .gitignore, sind also nie im Checkout — ohne diese beiden
# Ausnahmen loescht der erste Deploy das Passwort samt aller Sicherungen, und `docker compose up`
# bricht danach mit „ADMIN_INITIAL_PASSWORD fehlt in deployment/.env" ab.
RSYNC=(rsync -az --delete -e "ssh -p $VM_PORT"
       --exclude .git --exclude node_modules
       --exclude deployment/.env --exclude deployment/backups
       # Nichts uebertragen, was auf der VM niemand braucht (BETRIEB-7): das 33-MB-APK, die
       # Abnahme-Bildschirmfotos, die Android-Quellen und lokale Baustaende — zusammen rund 130 MB.
       --exclude /app --exclude /docs --exclude /android-starter
       --exclude "**/build" --exclude "**/.gradle" --exclude "**/.demo-dist"
       ./ "$VM_USER@$VM_HOST:$VM_PATH/")

if [ -n "${DRY_RUN:-}" ]; then
    echo "== DRY_RUN: nichts wird ausgefuehrt =="
    printf '%q ' "${RSYNC[@]}"; echo
    echo "$SSH \"cd $VM_PATH/deployment && ./backup.sh || true\""
    echo "$SSH \"cd $VM_PATH/deployment && $REMOTE_UP && docker compose ps\""
    echo "$SSH \"cd $VM_PATH/deployment && curl -sf http://localhost:\$(docker compose port web 8080 | cut -d: -f2)/api/health\""
    exit 0
fi

# Sichern, bevor irgendetwas neu startet. Schlaegt es fehl (erster Deploy, noch keine Datenbank),
# geht es weiter — nach dem ersten erfolgreichen Start ist die Sicherung Pflicht (README-VM, Abschnitt 9).
echo "==> Datenbank sichern (falls der Stack schon laeuft)"
$SSH "cd $VM_PATH/deployment 2>/dev/null && ./backup.sh" || echo "   keine Sicherung moeglich — vermutlich der erste Deploy."

echo "==> Stand nach $VM_USER@$VM_HOST:$VM_PATH kopieren"
"${RSYNC[@]}"

echo "==> Stack neu starten"
$SSH "cd $VM_PATH/deployment && $REMOTE_UP && docker compose ps"

echo "==> Pruefen"
# Den veroeffentlichten Port fragt Compose selbst ab (WEB_PORT aus der .env der VM). Fest verdrahtete
# 8080 hiessen: steht dort etwas anderes, endet dieses Skript rot, obwohl der Stack laeuft (BETRIEB-6).
$SSH "cd $VM_PATH/deployment && curl -sf http://localhost:\$(docker compose port web 8080 | cut -d: -f2)/api/health"
echo
# Aufraeumen nach dem Bau: alte, unbenutzte Abbilder belegen sonst nach ein paar Deploys mehrere
# Gigabyte auf der 50-GB-VM (Befund BETRIEB-9, 05.09.). `prune -f` fasst nur, was kein Container
# benutzt; die laufenden Abbilder bleiben.
echo "==> Unbenutzte Abbilder aufraeumen"
$SSH "docker image prune -f" || echo "   Aufraeumen uebersprungen."
echo "fertig — von aussen: http://$VM_HOST:1500"
