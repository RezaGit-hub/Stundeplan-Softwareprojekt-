#!/usr/bin/env bash
# Job "frontend:build" der Pipeline, lokal. Braucht nur Node (>= 20.19 oder >= 22.12), kein Docker.
# Gleiche Befehle wie .gitlab-ci.yml; Ergebnis liegt in frontend/dist/.
set -euo pipefail
cd "$(dirname "$0")/.."

cd frontend
npm ci
npm run typecheck
npm run lint
npm test
npm run build
echo "frontend:build grün — Artefakt: frontend/dist/"
