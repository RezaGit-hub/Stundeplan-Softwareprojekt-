#!/usr/bin/env bash
# Entspricht dem CI-Job `contract:lint`: prüft api-contract/openapi.yaml mit Redocly.
# Braucht nur Node (>= 20.19), kein Docker, kein JDK.
set -euo pipefail
cd "$(dirname "$0")/.."

cd api-contract
npm ci
npm run lint
