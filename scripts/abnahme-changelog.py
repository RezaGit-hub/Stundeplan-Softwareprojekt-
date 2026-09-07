#!/usr/bin/env python3
"""Abnahme des Moduls changelog gegen einen laufenden Stack.

Faehrt die Checklistenzeile V-6 und den Testfall T-W7.1 ab: Aenderungsverlauf lesen, filtern,
seitenweise blaettern — und nachweisen, dass es keinen Weg gibt, einen Eintrag zu aendern oder zu
loeschen. Jeder Schritt laeuft als echter curl-Aufruf; das Skript protokolliert Befehl, Statuscode und
die geprueften Werte und endet mit Exit-Code 1, sobald eine Zusicherung nicht haelt.

Voraussetzung: ein Stack mit LEERER Datenbank — die Grunddaten legt das Skript selbst an.

    cd deployment
    BACKEND_PORT=8092 DB_PORT=15452 docker compose -p changelog-abnahme \\
        -f docker-compose.yml -f docker-compose.dev.yml up -d --build db backend
    cd ..
    ABNAHME_BASE=http://127.0.0.1:8092 python3 scripts/abnahme-changelog.py

Jeder Schritt geht ueber die Schnittstelle. Bis zur Bauphase backend3 setzte dieses Skript den
Eintrag zu einer Vertretung (`EXCEPTION_CREATED`) mit `psql` direkt in die Tabelle, weil es
`POST /api/exceptions` noch nicht gab; seit es den Endpunkt gibt, entsteht der Eintrag ueber ihn —
zusammen mit den Ereignissen, die ihn ausloesen. Ein Zugriff auf den Datenbank-Container ist nicht
mehr noetig.

Werte aus der Umgebung:

    ABNAHME_BASE       Adresse des Backends, Standard http://127.0.0.1:8092
    ABNAHME_ADMIN_PW   Passwort des Kontos `admin` — Pflicht, kein Standardwert. Der Wert steht in
                       deployment/.env (ADMIN_INITIAL_PASSWORD); fehlt er, bricht das Skript ab.

Ausgabe ist die Vorlage fuer docs/abnahme/changelog.md.
"""
import json
import os
import subprocess
import sys

BASE = os.environ.get("ABNAHME_BASE", "http://127.0.0.1:8092")
ADMIN_PW = os.environ.get("ABNAHME_ADMIN_PW", "")
if not ADMIN_PW:
    sys.exit(
        "ABNAHME_ADMIN_PW fehlt. Kein Standardwert mehr im Skript (Befund SICHERHEIT-1, 05.09.):\n"
        "  ABNAHME_ADMIN_PW=$(grep ^ADMIN_INITIAL_PASSWORD= deployment/.env | cut -d= -f2-) scripts/abnahme-changelog.py"
    )
TOKEN = None
FEHLER = []

# Der Kalendertag des Leitszenarios und der Beginn des Plans. Ohne "Gueltig ab" begaenne der Plan am
# Tag des Laufs — nach dem 21.09.2026 gaebe es an diesem Tag keinen Plan mehr, und POST /api/exceptions
# antwortete 404.
MONTAG = "2026-09-21"
PLANBEGINN = "2026-09-01"


def curl(method, path, body=None, token=True, show=False):
    cmd = ["curl", "-s", "-w", "\\n%{http_code}", "-X", method, BASE + path]
    if token and TOKEN:
        cmd += ["-H", "Authorization: Bearer " + TOKEN]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "-d", json.dumps(body, ensure_ascii=False)]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    raw, code = out.rsplit("\n", 1)
    data = json.loads(raw) if raw.strip() else None
    if show:
        pretty = " -d '%s'" % json.dumps(body, ensure_ascii=False) if body is not None else ""
        print("$ curl -s -X %s %s%s%s" % (method, "$B" + path, ' -H "$A"' if token else "", pretty))
        print("  -> HTTP %s" % code)
    return int(code), data


def step(title):
    print()
    print("### " + title)


def pruefe(bedingung, text):
    print("  %s %s" % ("OK  " if bedingung else "FEHL", text))
    if not bedingung:
        FEHLER.append(text)


# --------------------------------------------------------------------------- Grunddaten und Plan

def anmelden():
    global TOKEN
    code, data = curl("POST", "/api/auth/login", {"username": "admin", "password": ADMIN_PW}, token=False)
    if code != 200:
        sys.exit("Anmeldung fehlgeschlagen (HTTP %s). Passwort per ABNAHME_ADMIN_PW setzen." % code)
    TOKEN = data["token"]


def post_id(path, body):
    code, data = curl("POST", path, body)
    if code != 201:
        sys.exit("POST %s ergab HTTP %s: %s" % (path, code, data))
    return data["id"]


def grunddaten():
    """Der Testdatenbestand des Pflichtenhefts (G-1 bis G-7) — dieselben Werte wie in PlanningITBase."""
    d = {}
    d["ma"] = post_id("/api/subjects", {"name": "Mathematik", "shortName": "Ma",
                                        "requiredRoomType": None, "color": "#1976D2"})
    d["de"] = post_id("/api/subjects", {"name": "Deutsch", "shortName": "De",
                                        "requiredRoomType": None, "color": "#388E3C"})
    d["ph"] = post_id("/api/subjects", {"name": "Physik", "shortName": "Ph",
                                        "requiredRoomType": "Fachraum Physik", "color": "#F57C00"})
    d["r101"] = post_id("/api/rooms", {"name": "R101", "roomType": "Klassenraum", "active": True})
    d["r204"] = post_id("/api/rooms", {"name": "R204", "roomType": "Klassenraum", "active": True})
    d["p1"] = post_id("/api/rooms", {"name": "P1", "roomType": "Fachraum Physik", "active": True})

    zeiten = [("08:00", "08:45"), ("08:50", "09:35"), ("09:35", "09:55"), ("09:55", "10:40"),
              ("10:45", "11:30"), ("11:50", "12:35"), ("12:40", "13:25")]
    slots = [{"day": tag, "position": pos + 1, "startTime": zeiten[pos][0], "endTime": zeiten[pos][1],
              "isBreak": pos + 1 == 3}
             for tag in ["MO", "TU", "WE", "TH", "FR"] for pos in range(7)]
    curl("PUT", "/api/timegrid", {"slots": slots})
    _, raster = curl("GET", "/api/timegrid")
    d["slots"] = [s["id"] for s in raster["slots"]]

    def slot(tag, position):
        return d["slots"][["MO", "TU", "WE", "TH", "FR"].index(tag) * 7 + position - 1]

    d["slot"] = slot
    d["meyer"] = post_id("/api/teachers", {"name": "Meyer", "shortName": "MEY", "weeklyHoursQuota": 25,
                                           "subjectIds": [d["ma"], d["ph"]], "preferredTimeslotIds": [],
                                           "blockedTimeslotIds": [], "active": True})
    d["weber"] = post_id("/api/teachers", {"name": "Weber", "shortName": "WEB", "weeklyHoursQuota": 25,
                                           "subjectIds": [d["de"]], "preferredTimeslotIds": [],
                                           "blockedTimeslotIds": [], "active": True})
    d["schulz"] = post_id("/api/teachers", {"name": "Schulz", "shortName": "SCZ", "weeklyHoursQuota": 2,
                                            "subjectIds": [d["ma"]], "preferredTimeslotIds": [],
                                            "blockedTimeslotIds": [slot("FR", 6), slot("FR", 7)],
                                            "active": True})
    d["k7a"] = post_id("/api/classes", {"name": "7a", "grade": 7, "active": True})
    d["k8b"] = post_id("/api/classes", {"name": "8b", "grade": 8, "active": True})
    d["k9c"] = post_id("/api/classes", {"name": "9c", "grade": 9, "active": True})
    curl("PUT", "/api/classes/%d/subjects" % d["k7a"],
         [{"subjectId": d["ma"], "weeklyHours": 2, "teacherId": d["meyer"]},
          {"subjectId": d["de"], "weeklyHours": 1, "teacherId": d["weber"]},
          {"subjectId": d["ph"], "weeklyHours": 1, "teacherId": d["meyer"]}])
    curl("PUT", "/api/classes/%d/subjects" % d["k8b"],
         [{"subjectId": d["ma"], "weeklyHours": 2, "teacherId": d["meyer"]},
          {"subjectId": d["de"], "weeklyHours": 1, "teacherId": d["weber"]}])
    curl("PUT", "/api/classes/%d/subjects" % d["k9c"],
         [{"subjectId": d["ma"], "weeklyHours": 1, "teacherId": d["schulz"]}])
    return d


def plan_annehmen(d):
    """Regelplan Mo/Di von Hand (P-1) und annehmen (P-6) — das schreibt den ersten Verlaufseintrag."""
    curl("POST", "/api/draft", {"source": "EMPTY"})
    for klasse, fach, tag, pos, raum in [
            ("k7a", "ma", "MO", 1, "r101"), ("k7a", "de", "MO", 2, "r101"), ("k7a", "ph", "MO", 4, "p1"),
            ("k8b", "ma", "MO", 2, "r204"), ("k8b", "de", "MO", 4, "r204"), ("k8b", "ma", "MO", 5, "r204"),
            ("k7a", "ma", "TU", 1, "r101")]:
        curl("POST", "/api/draft/lessons", {"classId": d[klasse], "subjectId": d[fach],
                                            "timeslotId": d["slot"](tag, pos), "roomId": d[raum]})
    code, _ = curl("POST", "/api/draft/accept", {"validFrom": PLANBEGINN}, show=True)
    pruefe(code == 200, "POST /api/draft/accept -> 200")
    _, plan = curl("GET", "/api/timetable")
    for lesson in plan["lessons"]:
        if lesson["classId"] == d["k7a"] and lesson["timeslotId"] == d["slot"]("MO", 1):
            return lesson["id"]
    sys.exit("Die Stunde 7a Mo 1 steht nicht im aktiven Plan.")


# --------------------------------------------------------------------------- Abnahmeschritte

def main():
    global TOKEN
    print("# Abnahme changelog — V-6, T-W7.1")
    print("Stack: %s" % BASE)
    anmelden()

    step("Vorbereitung: Grunddaten, Regelplan Mo/Di, Plan annehmen (G-1 bis G-7, P-1, P-6)")
    d = grunddaten()
    stunde7a = plan_annehmen(d)
    print("  7a=%d 8b=%d Meyer=%d Weber=%d | Stunde 7a Mo 1 = %d"
          % (d["k7a"], d["k8b"], d["meyer"], d["weber"], stunde7a))

    step("Der Eintrag zum Annehmen (W-8 schreibt, W-7 liest)")
    code, seite = curl("GET", "/api/changelog", show=True)
    print("  total %s | page %s | size %s" % (seite["total"], seite["page"], seite["size"]))
    print("  " + json.dumps(seite["items"][0], ensure_ascii=False))
    pruefe(code == 200 and seite["total"] == 1, "genau ein Eintrag")
    e = seite["items"][0]
    pruefe(e["action"] == "TIMETABLE_ACCEPTED", "action TIMETABLE_ACCEPTED")
    pruefe(e["oldAssignment"] == "kein Plan" and e["newAssignment"] == "Plan angenommen (7 Stunden)",
           "alte und neue Belegung als Text")
    pruefe(e["username"] == "admin", "Verursacher admin")
    pruefe(sorted(e) == ["action", "classShort", "date", "id", "newAssignment", "oldAssignment",
                         "position", "reasons", "timestamp", "username"],
           "alle zehn Felder des Vertragsschemas ChangelogItem, kein Feld zuviel")

    step("Das Leitszenario ueber die Endpunkte: Ausfall, Raumsperrung, Vertretung (V-1 bis V-4)")
    ausfall = post_id("/api/events", {"kind": "TEACHER_ABSENCE", "teacherId": d["meyer"],
                                      "dateFrom": MONTAG, "dateTo": MONTAG, "reason": "Krankmeldung"})
    sperrung = post_id("/api/events", {"kind": "ROOM_BLOCKED", "roomId": d["r101"],
                                       "dateFrom": MONTAG, "dateTo": MONTAG, "reason": "Wasserschaden"})
    code, ausnahme = curl("POST", "/api/exceptions",
                          {"date": MONTAG, "lessonId": stunde7a, "kind": "SUBSTITUTION",
                           "substituteTeacherId": d["schulz"], "substituteRoomId": d["r204"],
                           "eventIds": [ausfall, sperrung]}, show=True)
    pruefe(code == 201, "POST /api/exceptions (Vertretung Schulz/R204) -> 201")
    # Eine zweite Ausnahme in der 8b — sie darf im Filter der 7a nicht auftauchen.
    _, plan = curl("GET", "/api/timetable")
    stunde8b = next(l["id"] for l in plan["lessons"]
                    if l["classId"] == d["k8b"] and l["timeslotId"] == d["slot"]("MO", 2))
    code, _ = curl("POST", "/api/exceptions", {"date": MONTAG, "lessonId": stunde8b, "kind": "CANCEL",
                                               "eventIds": [ausfall]})
    pruefe(code == 201, "POST /api/exceptions (Entfall 8b) -> 201")
    print("  zwei Ausnahmen ueber die Schnittstelle angelegt (7a und 8b)")

    step("V-6: Aenderungsverlauf, Filter Klasse 7a (T-W7.1)")
    code, seite = curl("GET", "/api/changelog?classId=%d" % d["k7a"], show=True)
    print("  total %s" % seite["total"])
    print("  " + json.dumps(seite["items"][0], ensure_ascii=False))
    pruefe(seite["total"] == 1, "total 1 — genau ein Eintrag zur 7a")
    e = seite["items"][0]
    pruefe(e["action"] == "EXCEPTION_CREATED", "action EXCEPTION_CREATED")
    pruefe(e["date"] == MONTAG, "date " + MONTAG)
    pruefe(e["position"] == 1, "position 1")
    pruefe(e["classShort"] == "7a", "classShort 7a (aus school_class nachgeschlagen)")
    pruefe(e["oldAssignment"] == "Meyer / R101", 'oldAssignment "Meyer / R101"')
    pruefe(e["newAssignment"] == "Schulz / R204", 'newAssignment "Schulz / R204"')
    pruefe(e["reasons"] == "Krankmeldung; Wasserschaden", "beide Anlaesse in einem Eintrag")
    pruefe(e["username"] == "admin", "username admin (aus user_account nachgeschlagen)")
    pruefe(bool(e["timestamp"]), "timestamp gesetzt")

    step("Reihenfolge und die uebrigen Filter")
    _, alle = curl("GET", "/api/changelog", show=True)
    print("  Reihenfolge: " + ", ".join(x["action"] for x in alle["items"]))
    pruefe(alle["total"] == 3, "ohne Filter drei Eintraege")
    pruefe(alle["items"][-1]["action"] == "TIMETABLE_ACCEPTED", "neueste zuerst, aelteste zuletzt")
    for pfad, erwartet, text in [
            ("/api/changelog?teacherId=%d" % d["meyer"], 2, "Filter Lehrkraft Meyer -> 2"),
            ("/api/changelog?teacherId=%d" % d["weber"], 0, "Filter Lehrkraft Weber -> 0"),
            ("/api/changelog?lessonId=%d" % stunde7a, 1, "Filter Stunde 7a Mo 1 -> 1"),
            ("/api/changelog?from=%s&to=%s" % (MONTAG, MONTAG), 2, "Zeitraum 21.09. -> 2"),
            ("/api/changelog?from=2026-09-22", 0, "ab 22.09. -> 0"),
            ("/api/changelog?classId=%d&teacherId=%d" % (d["k8b"], d["weber"]), 0,
             "Klasse 8b und Weber zusammen -> 0")]:
        code, seite = curl("GET", pfad)
        pruefe(code == 200 and seite["total"] == erwartet, "%s (ist %s)" % (text, seite["total"]))

    step("Seitenweise lesen")
    gesehen = []
    for nummer in range(3):
        code, seite = curl("GET", "/api/changelog?page=%d&size=1" % nummer, show=(nummer == 0))
        print("  Seite %d: total %s, items %d" % (nummer, seite["total"], len(seite["items"])))
        gesehen += [x["id"] for x in seite["items"]]
    pruefe(len(gesehen) == 3 and len(set(gesehen)) == 3, "drei Seiten, jeder Eintrag genau einmal")
    _, hinten = curl("GET", "/api/changelog?page=9&size=1")
    pruefe(hinten["items"] == [] and hinten["total"] == 3, "Seite hinter dem Ende: leer, aber total 3")

    step("T-W7.1: es gibt keinen Weg, einen Eintrag zu aendern oder zu loeschen")
    eintrag_id = alle["items"][0]["id"]
    for methode, pfad, erwartet in [("DELETE", "/api/changelog/%d" % eintrag_id, 404),
                                    ("PUT", "/api/changelog/%d" % eintrag_id, 404),
                                    ("PATCH", "/api/changelog/%d" % eintrag_id, 404),
                                    ("DELETE", "/api/changelog", 405),
                                    ("POST", "/api/changelog", 405),
                                    ("PUT", "/api/changelog", 405)]:
        code, _ = curl(methode, pfad, {} if methode in ("PUT", "PATCH", "POST") else None)
        print("  %-6s %-24s -> %s" % (methode, pfad, code))
        pruefe(code == erwartet, "%s %s -> %d" % (methode, pfad, erwartet))
    _, danach = curl("GET", "/api/changelog")
    pruefe(danach["total"] == 3, "nach allen Versuchen stehen die drei Eintraege unveraendert da")

    step("Falsche Eingaben und fehlende Anmeldung")
    for pfad, erwartet, feld in [("/api/changelog?size=501", 400, "size"),
                                 ("/api/changelog?size=0", 400, "size"),
                                 ("/api/changelog?page=-1", 400, "page"),
                                 ("/api/changelog?from=2026-09-30&to=2026-09-01", 400, "to")]:
        code, problem = curl("GET", pfad)
        print("  GET %s -> %s (%s)" % (pfad, code, problem["errors"][0]["field"] if code == 400 else ""))
        pruefe(code == erwartet and problem["errors"][0]["field"] == feld,
               "%s -> 400 mit Feld %s" % (pfad, feld))
    code, _ = curl("GET", "/api/changelog", token=False)
    pruefe(code == 401, "ohne Token -> 401")

    step("Rolle Lehrkraft darf den Verlauf nicht lesen")
    code, _ = curl("POST", "/api/users", {"username": "lehrkraft-abnahme", "password": "abnahme-2026",
                                          "role": "TEACHER", "teacherId": d["meyer"]})
    pruefe(code == 201, "Konto der Lehrkraft angelegt")
    planung = TOKEN
    _, login = curl("POST", "/api/auth/login",
                    {"username": "lehrkraft-abnahme", "password": "abnahme-2026"}, token=False)
    TOKEN = login["token"]
    code, _ = curl("GET", "/api/changelog", show=True)
    pruefe(code == 403, "als Lehrkraft -> 403")
    TOKEN = planung

    print()
    if FEHLER:
        print("FEHLGESCHLAGEN: %d Zusicherung(en)" % len(FEHLER))
        for text in FEHLER:
            print("  - " + text)
        sys.exit(1)
    print("Alle Zusicherungen halten.")


if __name__ == "__main__":
    main()
