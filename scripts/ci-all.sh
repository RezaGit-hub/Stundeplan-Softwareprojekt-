#!/usr/bin/env bash
# Fuehrt alle vier CI-Jobs lokal aus, in derselben Reihenfolge wie .gitlab-ci.yml.
# Bricht beim ersten Fehler ab und druckt danach eine Zeile je Skript.
# Gruen hier = gruen im Runner, sofern der Runner Images ziehen darf.
set -uo pipefail
cd "$(dirname "$0")/.."

SCRIPTS=(
  "scripts/ci-contract-lint.sh"
  "scripts/ci-backend-test.sh"
  "scripts/ci-frontend-build.sh"
  "scripts/ci-image.sh"
)

declare -a RESULT=()
failed=0

for s in "${SCRIPTS[@]}"; do
  if [ ! -f "$s" ]; then
    echo ""
    echo "### $s — FEHLT (noch nicht gebaut)"
    RESULT+=("fehlt          $s")
    failed=1
    break
  fi
  if [ ! -x "$s" ]; then
    echo ""
    echo "### $s — nicht ausfuehrbar; 'chmod +x $s' nachholen"
    RESULT+=("nicht ausfuehrbar  $s")
    failed=1
    break
  fi

  echo ""
  echo "=============================================================="
  echo "### $s"
  echo "=============================================================="
  if "./$s"; then
    RESULT+=("ok             $s")
  else
    code=$?
    RESULT+=("fehlgeschlagen $s (Exit $code)")
    failed=1
    break
  fi
done

# Was nach dem Abbruch nicht mehr an die Reihe kam, wird als solches genannt.
for s in "${SCRIPTS[@]}"; do
  seen=0
  for line in "${RESULT[@]}"; do
    case "$line" in *"$s"*) seen=1 ;; esac
  done
  [ "$seen" -eq 0 ] && RESULT+=("nicht gelaufen $s")
done

echo ""
echo "=============================================================="
echo "Ergebnis"
echo "=============================================================="
for line in "${RESULT[@]}"; do
  echo "  $line"
done

if [ "$failed" -ne 0 ]; then
  echo ""
  echo "ci-all: NICHT gruen." >&2
  exit 1
fi
echo ""
echo "ci-all: alles gruen."
