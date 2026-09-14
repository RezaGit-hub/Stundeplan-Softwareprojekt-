#!/usr/bin/env python3
"""Versionsverwaltung fuer das Projekt.

Aufruf aus dem Repo-Wurzelverzeichnis:
    python scripts/version.py            # aktuelle Version anzeigen
    python scripts/version.py next       # naechste Version vorschlagen
    python scripts/version.py release    # Release anlegen (fragt nach Version, pusht)

Das Skript wechselt selbst ins Repo-Wurzelverzeichnis, egal von wo es
aufgerufen wird.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "VERSION"

# Erlaubt: 1.2.3, optional mit Suffix -rc1, -beta, -alpha, etc.
VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+(-[A-Za-z0-9.]+)?$")


def current_version() -> str:
    if VERSION_FILE.exists():
        return VERSION_FILE.read_text().strip()
    return "0.0.0"


def next_version(current: str) -> str:
    """Schlaegt die naechste Patch-Version vor: 0.0.35 -> 0.0.36."""
    # Falls die aktuelle Version ein Suffix hat, wird es fuer den Vorschlag entfernt.
    base = current.split("-")[0]
    major, minor, patch = (int(x) for x in base.split("."))
    return f"{major}.{minor}.{patch + 1}"


def ask_version(current: str) -> str | None:
    """Fragt den Benutzer nach der neuen Version.

    Enter        -> Vorschlag (next_version)
    gueltige Nr. -> diese Nummer
    'abbrechen'  -> None
    """
    suggestion = next_version(current)

    print(f"Aktuelle Version:   {current}")
    print(f"Vorschlag:          {suggestion}")
    print()
    print("Enter druecken fuer den Vorschlag, oder eigene Version eingeben.")
    print("Erlaubt: 1.2.3")
    print("Zum Abbrechen: 'abbrechen' eingeben.")
    print()

    raw = input("Neue Version: ").strip()

    if raw.lower() in ("abbrechen", "abort", "quit", "q"):
        return None

    if raw == "":
        return suggestion

    if not VERSION_PATTERN.match(raw):
        print(f"FEHLER: '{raw}' ist keine gueltige Version.")
        print("Erwartet: drei Zahlen durch Punkte z. B.")
        print("  1.0.0     0.0.36     2.3.14   ")
        return None

    if raw == current:
        print(f"FEHLER: '{raw}' ist bereits die aktuelle Version.")
        return None

    return raw


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def current_branch() -> str:
    """Name des aktuell ausgecheckten Branches."""
    result = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
    return result.stdout.strip()


def cmd_show() -> None:
    print(current_version())


def cmd_next() -> None:
    print(next_version(current_version()))


def cmd_release() -> None:
    current = current_version()
    new = ask_version(current)

    if new is None:
        print("Abgebrochen. Nichts geaendert.")
        return

    branch = current_branch()

    print()
    print("Geplant:")
    print(f"  Branch:   {branch}")
    print(f"  Version:  {new}  (Tag: v{new})")
    print()

    answer = input(f"Release v{new} anlegen und pushen? [j/N] ").strip().lower()
    if answer not in ("j", "ja", "y", "yes"):
        print("Abgebrochen. Nichts geaendert.")
        return

    # 1. VERSION-Datei schreiben
    VERSION_FILE.write_text(new + "\n")

    # 2. Commit und Tag anlegen
    run("git", "add", "VERSION")
    run("git", "commit", "-m", f"Release {new}")
    run("git", "tag", "-a", f"v{new}", "-m", f"Release {new}")

    # 3. Branch pushen
    print()
    print(f"Pushe {branch} nach origin …")
    run("git", "push", "origin", branch)

    # 4. Tag pushen — erst danach startet die Tag-Pipeline in GitLab.
    print()
    print(f"Pushe Tag v{new} nach origin …")
    run("git", "push", "origin", f"v{new}")

    print()
    print("Fertig.")
    print()
    print("Naechster Schritt in GitLab:")
    print("  CI/CD -> Pipelines -> Filter auf 'Tag' stellen.")
    print(f"  Die Tag-Pipeline v{new} laeuft jetzt automatisch durch.")
    print("  Der Deploy-Job startet von selbst, sobald die Tests gruen sind.")


def main() -> None:
    action = sys.argv[1] if len(sys.argv) > 1 else "show"
    handlers = {"show": cmd_show, "next": cmd_next, "release": cmd_release}

    if action not in handlers:
        print(f"Unbekannter Befehl: {action}")
        print("Erlaubt: show, next, release")
        sys.exit(1)

    handlers[action]()


if __name__ == "__main__":
    main()