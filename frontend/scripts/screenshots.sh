#!/usr/bin/env bash
# Erzeugt PNGs aller Seiten in hell und dunkel aus der Ein-Datei-Demo. Ohne Playwright: Chrome headless.
# Vorher: VITE_MOCK_TODAY=2026-09-21 VITE_MOCK_LATENCY=0 VITE_MOCK_SCENARIO=leitszenario npm run build:demo
set -euo pipefail
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
HTML="$(cd "$(dirname "$0")/.." && pwd)/../demo/webapp-demo.html"
OUT="$(dirname "$HTML")/screenshots"; mkdir -p "$OUT"
routes=(
  "login|/login"
  "start|/?demo=admin"
  "plan-klasse|/plan?demo=admin&sicht=CLASS&id=1&woche=2026-09-21"
  "plan-lehrkraft|/plan?demo=admin&sicht=TEACHER&id=1&woche=2026-09-21"
  "plan-raum|/plan?demo=admin&sicht=ROOM&id=1&woche=2026-09-21"
  "plan-schule|/plan?demo=admin&sicht=SCHOOL&woche=2026-09-21"
  "entwurf|/entwurf?demo=admin"
  "vertretung|/vertretung?demo=admin&datum=2026-09-21"
  "verlauf|/verlauf?demo=admin"
  "lehrkraefte|/grunddaten/lehrkraefte?demo=admin"
  "klassen|/grunddaten/klassen?demo=admin"
  "klasse-7a|/grunddaten/klassen/1?demo=admin"
  "raeume|/grunddaten/raeume?demo=admin"
  "faecher|/grunddaten/faecher?demo=admin"
  "raster|/grunddaten/raster?demo=admin"
  "regelwerk|/regelwerk?demo=admin"
  "einstellungen|/einstellungen?demo=admin"
  "mein-plan|/mein-plan?demo=meyer&woche=2026-09-21"
  "app-demo|/app-demo?demo=admin&als=schulz&datum=2026-09-21"
)
for theme in light dark; do
  for r in "${routes[@]}"; do
    name="${r%%|*}"; path="${r#*|}"; sep='?'; [[ "$path" == *\?* ]] && sep='&'
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1440,900 \
      --virtual-time-budget=8000 --screenshot="$OUT/${name}-${theme}.png" \
      "file://${HTML}#${path}${sep}theme=${theme}" >/dev/null 2>&1
    echo "ok  ${name}-${theme}.png"
  done
done
