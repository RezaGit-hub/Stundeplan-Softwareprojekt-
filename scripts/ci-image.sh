#!/usr/bin/env bash
# Entspricht dem CI-Job `image:build`: baut die beiden Images des Compose-Stacks.
# Braucht Docker, sonst nichts. Dauert beim ersten Mal Minuten.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== image: docker compose build (backend + web) =="
cd deployment
# `docker compose build` liest .env nicht zwingend, aber `config` prueft sie — deshalb Vorlage anlegen,
# falls es sie noch nicht gibt. Die Datei ist per .gitignore vom Repo ausgeschlossen.
[ -f .env ] || cp .env.example .env
docker compose build backend web
