#!/usr/bin/env python3
"""Abnahme des Moduls substitution gegen einen laufenden Stack.

Faehrt den Leitfaden-Durchlauf der Abnahme-Checkliste ab, so weit er dieses Modul betrifft:
P-7 (Sichten), V-1 bis V-6 (Ausfall, Sperrung, Vertretung, Verlauf), A-1 bis A-6 (Rolle Lehrkraft)
und T-110 (Ferien) — Testfaelle T-W4.1, T-W5.1, T-W6.1, T-W7.1, T-A1.1 und T-A2.1. Dazu die
Faelle, die im Pflichtenheft nicht als Schritt stehen, aber im Vertrag: Verlegung, Entfall,
Zusatzstunde und die zweite Ausnahme am selben Tag (409 `exception-exists`).

Jeder Schritt laeuft als echter curl-Aufruf; das Skript protokolliert Befehl, Statuscode und die
geprueften Werte und endet mit dem Exit-Code 1, sobald eine Zusicherung nicht haelt.

Voraussetzung: ein Stack mit LEERER Datenbank — die Grunddaten legt das Skript selbst an.
Am einfachsten ueber die Huelle, die auch den Stack frisch startet:

    scripts/abnahme.sh --frisch

Von Hand:

    cd deployment
    docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
    cd ..
    python3 scripts/abnahme-substitution.py

Zwei Werte kommen aus der Umgebung, beide mit dem Standard des regulaeren Stacks:

    ABNAHME_BASE       Adresse des Servers, Standard http://127.0.0.1:1500 (nginx aus
                       docker-compose.yml; das Backend direkt liegt mit dem dev-Zusatz auf
                       http://127.0.0.1:8080)
    ABNAHME_ADMIN_PW   Passwort des Kontos `admin` — Pflicht, kein Standardwert. Der Wert steht in
                       deployment/.env (ADMIN_INITIAL_PASSWORD); fehlt er, bricht das Skript ab.

Die Daten sind die des Pflichtenhefts und die Kalenderwoche die der Checkliste: Montag 21.09.2026.

Ausgabe ist die Vorlage fuer docs/abnahme/backend3.md.
"""
import json
import os
import subprocess
import sys

BASE = os.environ.get("ABNAHME_BASE", "http://127.0.0.1:1500")
ADMIN_PW = os.environ.get("ABNAHME_ADMIN_PW", "")
if not ADMIN_PW:
    sys.exit(
        "ABNAHME_ADMIN_PW fehlt. Kein Standardwert mehr im Skript (Befund SICHERHEIT-1, 05.09.):\n"
        "  ABNAHME_ADMIN_PW=$(grep ^ADMIN_INITIAL_PASSWORD= deployment/.env | cut -d= -f2-) scripts/abnahme-substitution.py"
    )
TOKEN = None
FAILED = []

MONTAG = "2026-09-21"
DIENSTAG = "2026-09-22"
MITTWOCH = "2026-09-23"
DONNERSTAG = "2026-09-24"
FREITAG = "2026-09-25"
NAECHSTER_MONTAG = "2026-09-28"


def curl(method, path, body=None, token=True, show=False, bearer=None):
    cmd = ["curl", "-s", "-w", "\\n%{http_code}", "-X", method, BASE + path]
    if bearer:
        cmd += ["-H", "Authorization: Bearer " + bearer]
    elif token and TOKEN:
        cmd += ["-H", "Authorization: Bearer " + TOKEN]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "-d", json.dumps(body, ensure_ascii=False)]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    raw, code = out.rsplit("\n", 1)
    data = json.loads(raw) if raw.strip() else None
    if show:
        pretty = " -d '%s'" % json.dumps(body, ensure_ascii=False) if body is not None else ""
        print("$ curl -s -X %s %s%s%s" % (method, "$B" + path, " -H \"$A\"" if (token or bearer) else "", pretty))
        print("  -> HTTP %s" % code)
    return int(code), data


def step(title):
    print()
    print("### " + title)


def line(*args):
    print("  " + " ".join(str(a) for a in args))


def check(label, ok):
    print("  [%s] %s" % ("ok" if ok else "ABWEICHUNG", label))
    if not ok:
        FAILED.append(label)


def day(week, index):
    """Ein Tag der Wochenantwort: 0 = Montag."""
    return week["days"][index]


def entries(week, index):
    return day(week, index)["entries"]


def entry_at(week, index, class_id, position):
    for e in entries(week, index):
        if e["classId"] == class_id and e["position"] == position:
            return e
    return {}


def kinds(week, index, kind):
    return [e for e in entries(week, index) if e["kind"] == kind]


def login(username, password):
    code, data = curl("POST", "/api/auth/login", {"username": username, "password": password},
                      token=False)
    if code != 200:
        print("Anmeldung als %s fehlgeschlagen: HTTP %s %s" % (username, code, data))
        sys.exit(1)
    return data["token"]


# ------------------------------------------------------------------ Anmeldung
step("L-1  Anmeldung als Planung")
code, data = curl("POST", "/api/auth/login", {"username": "admin", "password": ADMIN_PW},
                  token=False, show=True)
check("Login als Planung, Token erhalten", code == 200 and data and data.get("token"))
TOKEN = data["token"]

code, vorhandene = curl("GET", "/api/classes")
if vorhandene:
    print("\nDie Datenbank ist nicht leer (%d Klassen). Der Durchlauf braucht eine frische Datenbank:"
          % len(vorhandene))
    print("  cd deployment && docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v")
    sys.exit(1)

# ------------------------------------------------------------------ Grunddaten (G-1 bis G-7)
step("Vorbedingung: Grunddaten des Pflichtenhefts (Checkliste G-1 bis G-7)")
ids = {}
for name, short, room_type, color in [("Mathematik", "Ma", None, "#1976D2"),
                                      ("Deutsch", "De", None, "#388E3C"),
                                      ("Physik", "Ph", "Fachraum Physik", "#F57C00")]:
    _, d = curl("POST", "/api/subjects",
                {"name": name, "shortName": short, "requiredRoomType": room_type, "color": color})
    ids[short] = d["id"]
for name, room_type in [("R101", "Klassenraum"), ("R204", "Klassenraum"), ("P1", "Fachraum Physik")]:
    _, d = curl("POST", "/api/rooms", {"name": name, "roomType": room_type, "active": True})
    ids[name] = d["id"]

TIMES = [("08:00", "08:45"), ("08:50", "09:35"), ("09:35", "09:55"), ("09:55", "10:40"),
         ("10:45", "11:30"), ("11:50", "12:35"), ("12:40", "13:25")]
slots = [{"day": tag, "position": p, "startTime": TIMES[p - 1][0], "endTime": TIMES[p - 1][1],
          "isBreak": p == 3}
         for tag in ("MO", "TU", "WE", "TH", "FR") for p in range(1, 8)]
curl("PUT", "/api/timegrid", {"slots": slots})
_, grid = curl("GET", "/api/timegrid")
TS = [s["id"] for s in grid["slots"]]


def slot(tag, position):
    return TS[["MO", "TU", "WE", "TH", "FR"].index(tag) * 7 + position - 1]


for name, short, quota, subjects, blocked in [
        ("Meyer", "MEY", 25, ["Ma", "Ph"], []),
        ("Weber", "WEB", 25, ["De"], []),
        ("Schulz", "SCZ", 2, ["Ma"], [slot("FR", 6), slot("FR", 7)])]:
    _, d = curl("POST", "/api/teachers",
                {"name": name, "shortName": short, "weeklyHoursQuota": quota,
                 "subjectIds": [ids[s] for s in subjects], "preferredTimeslotIds": [],
                 "blockedTimeslotIds": blocked, "active": True})
    ids[short] = d["id"]
for name, grade in [("7a", 7), ("8b", 8), ("9c", 9)]:
    _, d = curl("POST", "/api/classes", {"name": name, "grade": grade, "active": True})
    ids[name] = d["id"]
curl("PUT", "/api/classes/%d/subjects" % ids["7a"],
     [{"subjectId": ids["Ma"], "weeklyHours": 2, "teacherId": ids["MEY"]},
      {"subjectId": ids["De"], "weeklyHours": 1, "teacherId": ids["WEB"]},
      {"subjectId": ids["Ph"], "weeklyHours": 1, "teacherId": ids["MEY"]}])
curl("PUT", "/api/classes/%d/subjects" % ids["8b"],
     [{"subjectId": ids["Ma"], "weeklyHours": 2, "teacherId": ids["MEY"]},
      {"subjectId": ids["De"], "weeklyHours": 1, "teacherId": ids["WEB"]}])
curl("PUT", "/api/classes/%d/subjects" % ids["9c"],
     [{"subjectId": ids["Ma"], "weeklyHours": 1, "teacherId": ids["SCZ"]}])
line("Grunddaten stehen:", json.dumps(ids))

# ------------------------------------------------------------------ P-1 und P-6
step("P-1 / P-6  Regelplan Montag/Dienstag eintragen und annehmen (gueltig ab %s)" % MONTAG)
curl("POST", "/api/draft", {"source": "EMPTY"})
for cls, subject, tag, position, room in [("7a", "Ma", "MO", 1, "R101"), ("7a", "De", "MO", 2, "R101"),
                                          ("7a", "Ph", "MO", 4, "P1"), ("8b", "Ma", "MO", 2, "R204"),
                                          ("8b", "De", "MO", 4, "R204"), ("8b", "Ma", "MO", 5, "R204"),
                                          ("7a", "Ma", "TU", 1, "R101")]:
    curl("POST", "/api/draft/lessons",
         {"classId": ids[cls], "subjectId": ids[subject], "timeslotId": slot(tag, position),
          "roomId": ids[room]})
code, plan = curl("POST", "/api/draft/accept", {"validFrom": MONTAG}, show=True)
check("200, sieben Stunden, keine Konflikte",
      code == 200 and len(plan["lessons"]) == 7 and plan["conflicts"] == [])
LESSONS = {(l["classShort"], l["timeslotId"]): l["id"] for l in plan["lessons"]}
sieben_a_mo1 = LESSONS[("7a", slot("MO", 1))]
acht_b_mo5 = LESSONS[("8b", slot("MO", 5))]
line("Stunde 7a Mo 1. =", sieben_a_mo1, "| 8b Mo 5. =", acht_b_mo5)

# ------------------------------------------------------------------ P-7 / T-W4.1
step("P-7 / T-W4.1  vier Sichten auf dieselbe Woche")
code, klasse = curl("GET", "/api/timetable/week?date=%s&view=CLASS&id=%d" % (MONTAG, ids["7a"]), show=True)
line("Montag 7a:", json.dumps([(e["position"], e["subjectShort"], e["teacherShort"], e["roomShort"],
                                e["kind"]) for e in entries(klasse, 0)], ensure_ascii=False))
check("Woche beginnt am Montag und hat fuenf Tage",
      code == 200 and klasse["monday"] == MONTAG and len(klasse["days"]) == 5)
erste = entry_at(klasse, 0, ids["7a"], 1)
check("7a Mo 1. Stunde: Ma / MEY / R101, kind REGULAR",
      (erste.get("subjectShort"), erste.get("teacherShort"), erste.get("roomShort"),
       erste.get("kind")) == ("Ma", "MEY", "R101", "REGULAR"))

_, lehrkraft = curl("GET", "/api/timetable/week?date=%s&view=TEACHER&id=%d" % (MONTAG, ids["MEY"]))
line("Montag Meyer:", [e["position"] for e in entries(lehrkraft, 0)],
     "| Dienstag:", [e["position"] for e in entries(lehrkraft, 1)])
check("Lehrkraftsicht Meyer: Montag vier Eintraege (1, 2, 4, 5), Dienstag einer",
      [e["position"] for e in entries(lehrkraft, 0)] == [1, 2, 4, 5]
      and len(entries(lehrkraft, 1)) == 1)

_, schule = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
line("Montag Schule:", [(e["classShort"], e["position"]) for e in entries(schule, 0)])
check("Schulsicht: Montag sechs Eintraege, in der 1. Stunde genau einer",
      len(entries(schule, 0)) == 6
      and len([e for e in entries(schule, 0) if e["position"] == 1]) == 1)

_, raum = curl("GET", "/api/timetable/week?date=%s&view=ROOM&id=%d" % (MONTAG, ids["R101"]), show=True)
line("Montag R101:", [e["position"] for e in entries(raum, 0)])
check("Raumsicht R101 (E-39): Montag Position 1 und 2 belegt, ab der 4. nichts",
      [e["position"] for e in entries(raum, 0)] == [1, 2])
check("alle Eintraege REGULAR",
      all(e["kind"] == "REGULAR" for e in entries(schule, 0)))

_, fehlt = curl("GET", "/api/timetable/week?date=%s&view=CLASS" % MONTAG)
code_404, _ = curl("GET", "/api/timetable/week?date=%s&view=ROOM&id=999999" % MONTAG)
check("Sicht ohne Kennung -> 400 mit Feld id; unbekannte Kennung -> 404",
      fehlt.get("errors", [{}])[0].get("field") == "id" and code_404 == 404)

# ------------------------------------------------------------------ A-1 bis A-3 / T-A1.1
step("A-1 bis A-3 / T-A1.1  Konto der Lehrkraft, eigene Woche, Zugriffsschutz (ohne Ereignisse)")
code, _ = curl("POST", "/api/users", {"username": "meyer", "password": "geheim-2026",
                                      "role": "TEACHER", "teacherId": ids["MEY"]}, show=True)
check("A-1: Konto meyer angelegt (201)", code == 201)
MEYER_TOKEN = login("meyer", "geheim-2026")

code, meine = curl("GET", "/api/me/timetable/week?date=%s" % MONTAG, bearer=MEYER_TOKEN, show=True)
line("Montag:", json.dumps([(e["position"], e["classShort"], e["subjectShort"], e["roomShort"],
                             e["kind"]) for e in entries(meine, 0)], ensure_ascii=False))
in_der_woche = sum(len(d["entries"]) for d in meine["days"])
check("T-A1.1: Montag vier REGULAR-Eintraege auf 1, 2, 4, 5",
      code == 200 and [e["position"] for e in entries(meine, 0)] == [1, 2, 4, 5]
      and all(e["kind"] == "REGULAR" for e in entries(meine, 0)))
check("T-A1.1: die Weber-Stunde (7a, 2. Stunde) fehlt",
      not any(e["classShort"] == "7a" and e["position"] == 2 for e in entries(meine, 0)))
check("A-3: genau fuenf Eintraege in der Woche", in_der_woche == 5)
code_403, _ = curl("GET", "/api/timetable", bearer=MEYER_TOKEN)
code_401, _ = curl("GET", "/api/me/timetable/week", token=False)
check("GET /api/timetable mit Lehrkraft-Token -> 403, ohne Token -> 401",
      code_403 == 403 and code_401 == 401)

# ------------------------------------------------------------------ V-1 / T-W5.1
step("V-1 / T-W5.1  Ausfall Meyer am %s, Anlass „Krankmeldung“" % MONTAG)
_, vorher = curl("GET", "/api/timetable")
code, ausfall = curl("POST", "/api/events",
                     {"kind": "TEACHER_ABSENCE", "teacherId": ids["MEY"], "dateFrom": MONTAG,
                      "dateTo": MONTAG, "reason": "Krankmeldung"}, show=True)
line("affected:", json.dumps(ausfall.get("affected"), ensure_ascii=False))
line("hint:", ausfall.get("hint"))
check("201 und genau vier betroffene Stunden (7a 1, 8b 2, 7a 4, 8b 5)",
      code == 201 and [(a["classShort"], a["position"]) for a in ausfall["affected"]]
      == [("7a", 1), ("8b", 2), ("7a", 4), ("8b", 5)])
check("hint ist leer, weil Stunden betroffen sind", ausfall["hint"] is None)

_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG, show=True)
line("Montag NEEDS_SUBSTITUTION:",
     [(e["classShort"], e["position"]) for e in kinds(woche, 0, "NEEDS_SUBSTITUTION")])
check("T-W5.1: am %s genau vier NEEDS_SUBSTITUTION" % MONTAG, len(kinds(woche, 0, "NEEDS_SUBSTITUTION")) == 4)
check("T-W5.1: die Weber-Stunde (7a, 2. Stunde) bleibt REGULAR",
      entry_at(woche, 0, ids["7a"], 2)["kind"] == "REGULAR")
check("T-W5.1: am %s kein Vertretungsbedarf" % DIENSTAG, len(kinds(woche, 1, "NEEDS_SUBSTITUTION")) == 0)
_, nachher = curl("GET", "/api/timetable")
check("T-W5.1: der Regelplan ist unveraendert", vorher["lessons"] == nachher["lessons"])

# ------------------------------------------------------------------ V-2
step("V-2  Sperrung R101 am %s, Anlass „Wasserschaden“" % MONTAG)
code, sperrung = curl("POST", "/api/events",
                      {"kind": "ROOM_BLOCKED", "roomId": ids["R101"], "dateFrom": MONTAG,
                       "dateTo": MONTAG, "reason": "Wasserschaden"}, show=True)
line("affected:", json.dumps(sperrung.get("affected"), ensure_ascii=False))
check("201 und zwei betroffene Stunden (7a 1, 7a 2)",
      code == 201 and [(a["classShort"], a["position"]) for a in sperrung["affected"]]
      == [("7a", 1), ("7a", 2)])

_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
check("jetzt fuenf NEEDS_SUBSTITUTION", len(kinds(woche, 0, "NEEDS_SUBSTITUTION")) == 5)
check("7a 1. Stunde nennt beide Anlaesse",
      entry_at(woche, 0, ids["7a"], 1)["reasons"] == ["Krankmeldung", "Wasserschaden"])
check("7a 2. Stunde nennt nur den Wasserschaden",
      entry_at(woche, 0, ids["7a"], 2)["reasons"] == ["Wasserschaden"])

# ------------------------------------------------------------------ V-3
step("V-3  offene Stunden und Vorschlaege fuer die 1. Stunde der 7a")
code, offen = curl("GET", "/api/substitutions/pending?from=%s&to=%s" % (MONTAG, FREITAG), show=True)
line("offene Stunden:", json.dumps([(o["date"], o["classShort"], o["position"], o["teacherShort"],
                                     o["roomShort"], o["reasons"]) for o in offen], ensure_ascii=False))
check("200 und fuenf Zeilen, die erste ist 7a Position 1",
      code == 200 and len(offen) == 5 and offen[0]["classShort"] == "7a"
      and offen[0]["position"] == 1 and offen[0]["lessonId"] == sieben_a_mo1)
check("die erste Zeile nennt beide Anlaesse und beide Ereignisse",
      offen[0]["reasons"] == ["Krankmeldung", "Wasserschaden"]
      and sorted(offen[0]["eventIds"]) == sorted([ausfall["id"], sperrung["id"]]))

code, optionen = curl("GET", "/api/substitutions/options?date=%s&lessonId=%d" % (MONTAG, sieben_a_mo1),
                      show=True)
line("teachers:", json.dumps(optionen.get("teachers"), ensure_ascii=False))
line("rooms:", json.dumps(optionen.get("rooms"), ensure_ascii=False))
check("Schulz steht als fachgleiche Lehrkraft an erster Stelle",
      code == 200 and optionen["teachers"][0]["name"] == "Schulz"
      and optionen["teachers"][0]["teachesSubject"] is True)
check("Meyer fehlt (ausgefallen)", all(t["id"] != ids["MEY"] for t in optionen["teachers"]))
check("R204 ist dabei, R101 nicht (gesperrt)",
      any(r["id"] == ids["R204"] for r in optionen["rooms"])
      and all(r["id"] != ids["R101"] for r in optionen["rooms"]))

# ------------------------------------------------------------------ V-4 / T-W6.1
step("V-4 / T-W6.1  Schulz und R204 zuweisen")
code, ausnahme = curl("POST", "/api/exceptions",
                      {"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
                       "substituteTeacherId": ids["SCZ"], "substituteRoomId": ids["R204"],
                       "eventIds": [ausfall["id"], sperrung["id"]]}, show=True)
line("Antwort:", json.dumps(ausnahme, ensure_ascii=False))
check("201 und conflicts leer", code == 201 and ausnahme["conflicts"] == [])

_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
vertreten = entry_at(woche, 0, ids["7a"], 1)
line("7a 1. Stunde:", json.dumps(vertreten, ensure_ascii=False))
check("T-W6.1: kind SUBSTITUTED, SCZ in R204, original MEY / R101",
      vertreten["kind"] == "SUBSTITUTED" and vertreten["teacherShort"] == "SCZ"
      and vertreten["roomShort"] == "R204"
      and vertreten["original"]["teacherShort"] == "MEY"
      and vertreten["original"]["roomShort"] == "R101")
_, offen = curl("GET", "/api/substitutions/pending?from=%s&to=%s" % (MONTAG, FREITAG))
check("pending hat jetzt vier Zeilen ohne diese",
      len(offen) == 4 and all(o["lessonId"] != sieben_a_mo1 for o in offen))
_, plan_jetzt = curl("GET", "/api/timetable")
regel = [l for l in plan_jetzt["lessons"] if l["id"] == sieben_a_mo1][0]
check("T-W6.1: der Regelplan kennt weiter Meyer in R101",
      regel["teacherShort"] == "MEY" and regel["roomShort"] == "R101")

# ------------------------------------------------------------------ V-5
step("V-5  Plansicht am %s" % NAECHSTER_MONTAG)
code, spaeter = curl("GET", "/api/timetable/week?date=%s&view=CLASS&id=%d"
                     % (NAECHSTER_MONTAG, ids["7a"]), show=True)
naechste = entry_at(spaeter, 0, ids["7a"], 1)
line("7a Mo 1. Stunde:", json.dumps(naechste, ensure_ascii=False))
check("eine Woche spaeter steht wieder REGULAR / MEY / R101",
      code == 200 and (naechste["kind"], naechste["teacherShort"], naechste["roomShort"])
      == ("REGULAR", "MEY", "R101"))

# ------------------------------------------------------------------ V-6 / T-W7.1
step("V-6 / T-W7.1  Aenderungsverlauf, Filter 7a")
code, verlauf = curl("GET", "/api/changelog?classId=%d" % ids["7a"], show=True)
line("Eintrag:", json.dumps(verlauf["items"][0] if verlauf["items"] else None, ensure_ascii=False))
eintrag = verlauf["items"][0] if verlauf["items"] else {}
check("genau ein Eintrag", code == 200 and verlauf["total"] == 1)
check("action EXCEPTION_CREATED, Datum, Position, Klasse",
      eintrag.get("action") == "EXCEPTION_CREATED" and eintrag.get("date") == MONTAG
      and eintrag.get("position") == 1 and eintrag.get("classShort") == "7a")
check("alte und neue Belegung als Text",
      eintrag.get("oldAssignment") == "Meyer / R101"
      and eintrag.get("newAssignment") == "Schulz / R204")
check("beide Anlaesse und der Benutzername",
      eintrag.get("reasons") == "Krankmeldung; Wasserschaden" and eintrag.get("username") == "admin")
check("Zeitstempel gesetzt", bool(eintrag.get("timestamp")))
code_del, _ = curl("DELETE", "/api/changelog/%d" % eintrag.get("id", 1))
check("T-W7.1: DELETE /api/changelog/{id} findet keine Route (404)", code_del == 404)

# ------------------------------------------------------------------ A-2 nach dem Leitszenario
step("A-2 (nach dem Leitszenario)  Meyer sieht die abgegebene Stunde")
_, meine = curl("GET", "/api/me/timetable/week?date=%s" % MONTAG, bearer=MEYER_TOKEN, show=True)
line("Montag:", json.dumps([(e["position"], e["kind"], e["teacherShort"],
                             (e["original"] or {}).get("teacherShort")) for e in entries(meine, 0)],
                           ensure_ascii=False))
montag = entries(meine, 0)
check("Montag vier Eintraege auf 1, 2, 4, 5",
      [e["position"] for e in montag] == [1, 2, 4, 5])
check("Position 1 ist GIVEN_AWAY: 7a, Ma, SCZ, original MEY",
      montag[0]["kind"] == "GIVEN_AWAY" and montag[0]["classShort"] == "7a"
      and montag[0]["subjectShort"] == "Ma" and montag[0]["teacherShort"] == "SCZ"
      and montag[0]["original"]["teacherShort"] == "MEY")
check("Positionen 2, 4 und 5 sind NEEDS_SUBSTITUTION",
      [e["kind"] for e in montag[1:]] == ["NEEDS_SUBSTITUTION"] * 3)

# ------------------------------------------------------------------ A-4, A-5 / T-A2.1
step("A-4 / A-5 / T-A2.1  Schulz sieht die uebernommene Stunde")
code, _ = curl("POST", "/api/users", {"username": "schulz", "password": "geheim-2026",
                                      "role": "TEACHER", "teacherId": ids["SCZ"]})
check("Konto schulz angelegt (201)", code == 201)
SCHULZ_TOKEN = login("schulz", "geheim-2026")

code, meine_s = curl("GET", "/api/me/timetable/week?date=%s" % MONTAG, bearer=SCHULZ_TOKEN, show=True)
uebernommen = entries(meine_s, 0)[0] if entries(meine_s, 0) else {}
line("Montag Schulz:", json.dumps(uebernommen, ensure_ascii=False))
check("A-4: genau ein Eintrag, kind SUBSTITUTED, 7a in R204",
      code == 200 and len(entries(meine_s, 0)) == 1
      and uebernommen.get("kind") == "SUBSTITUTED"
      and uebernommen.get("classShort") == "7a" and uebernommen.get("roomShort") == "R204")
check("A-5: beide Anlaesse und die vertretene Lehrkraft",
      uebernommen.get("reasons") == ["Krankmeldung", "Wasserschaden"]
      and uebernommen["original"]["teacherShort"] == "MEY")

code, zeitraster = curl("GET", "/api/timegrid", bearer=SCHULZ_TOKEN)
check("A-4: GET /api/timegrid mit Lehrkraft-Token -> 200, Zeitfenster 1 = 08:00–08:45",
      code == 200 and zeitraster["slots"][0]["startTime"][:5] == "08:00"
      and zeitraster["slots"][0]["endTime"][:5] == "08:45")

_, s_spaeter = curl("GET", "/api/me/timetable/week?date=%s" % NAECHSTER_MONTAG, bearer=SCHULZ_TOKEN)
_, m_spaeter = curl("GET", "/api/me/timetable/week?date=%s" % NAECHSTER_MONTAG, bearer=MEYER_TOKEN)
check("T-A2.1: am %s sieht Schulz nichts und Meyer nur REGULAR" % NAECHSTER_MONTAG,
      sum(len(d["entries"]) for d in s_spaeter["days"]) == 0
      and all(e["kind"] == "REGULAR" for d in m_spaeter["days"] for e in d["entries"]))

step("A-6  keine Planungsfunktion mit der Rolle Lehrkraft")
code, _ = curl("POST", "/api/draft/lessons",
               {"classId": ids["9c"], "subjectId": ids["Ma"], "timeslotId": slot("WE", 1),
                "roomId": ids["R204"]}, bearer=SCHULZ_TOKEN, show=True)
check("POST /api/draft/lessons mit Lehrkraft-Token -> 403", code == 403)

# ------------------------------------------------------------------ zweite Ausnahme und Aufheben
step("Zweite Ausnahme am selben Tag und Aufheben")
code, problem = curl("POST", "/api/exceptions",
                     {"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
                      "substituteTeacherId": ids["WEB"]}, show=True)
line("Antwort:", json.dumps(problem, ensure_ascii=False))
check("409 exception-exists",
      code == 409 and problem.get("type") == "urn:pushnpull:problem:exception-exists")

code, _ = curl("DELETE", "/api/exceptions/%d" % ausnahme["id"], show=True)
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
wieder_offen = entry_at(woche, 0, ids["7a"], 1)
check("204 und die Stunde ist wieder NEEDS_SUBSTITUTION bei MEY",
      code == 204 and wieder_offen["kind"] == "NEEDS_SUBSTITUTION"
      and wieder_offen["teacherShort"] == "MEY" and wieder_offen["exceptionId"] is None)
_, verlauf = curl("GET", "/api/changelog?classId=%d" % ids["7a"])
check("der Verlauf haelt EXCEPTION_DELETED fest (jetzt zwei Eintraege)",
      verlauf["total"] == 2 and verlauf["items"][0]["action"] == "EXCEPTION_DELETED"
      and verlauf["items"][0]["oldAssignment"] == "Schulz / R204"
      and verlauf["items"][0]["newAssignment"] == "Meyer / R101")

# ------------------------------------------------------------------ Verlegung
step("Verlegung: 8b Montag 5. Stunde auf Mittwoch 1. Stunde")
code, verlegung = curl("POST", "/api/exceptions",
                       {"date": MONTAG, "lessonId": acht_b_mo5, "kind": "MOVE",
                        "newDate": MITTWOCH, "newTimeslotId": slot("WE", 1)}, show=True)
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
ursprung = entry_at(woche, 0, ids["8b"], 5)
ziel = entries(woche, 2)
line("Montag 8b 5. Stunde:", json.dumps(ursprung, ensure_ascii=False))
line("Mittwoch:", json.dumps(ziel, ensure_ascii=False))
check("201; am Ursprungstag CANCELLED mit „verlegt → Mi 1.“",
      code == 201 and ursprung["kind"] == "CANCELLED"
      and ursprung["reasons"][0] == "verlegt → Mi 1.")
check("am Zieltag ein Eintrag MOVED auf Position 1 mit original vom %s" % MONTAG,
      len(ziel) == 1 and ziel[0]["kind"] == "MOVED" and ziel[0]["position"] == 1
      and ziel[0]["original"]["date"] == MONTAG and ziel[0]["original"]["position"] == 5)
_, verlauf = curl("GET", "/api/changelog?lessonId=%d" % acht_b_mo5)
check("der Verlauf nennt das Ziel als „Mi, 1. Stunde“",
      verlauf["items"][0]["newAssignment"] == "Mi, 1. Stunde")
code, _ = curl("DELETE", "/api/exceptions/%d" % verlegung["id"], show=True)
_, verlauf = curl("GET", "/api/changelog?lessonId=%d" % acht_b_mo5)
line("Aufheben:", json.dumps(verlauf["items"][0], ensure_ascii=False))
check("beim Aufheben nennt oldAssignment das aufgehobene Ziel, nicht zweimal dieselbe Belegung",
      code == 204 and verlauf["items"][0]["action"] == "EXCEPTION_DELETED"
      and verlauf["items"][0]["oldAssignment"] == "Mi, 1. Stunde"
      and verlauf["items"][0]["newAssignment"] == "Meyer / R204")

# ------------------------------------------------------------------ Zusatzstunde
step("Zusatzstunde: 9c Mathematik am %s" % DONNERSTAG)
code, zusatz = curl("POST", "/api/events",
                    {"kind": "EXTRA_LESSON", "dateFrom": DONNERSTAG, "dateTo": DONNERSTAG,
                     "reason": "Nachholstunde",
                     "extra": {"date": DONNERSTAG, "timeslotId": slot("TH", 1),
                               "classId": ids["9c"], "subjectId": ids["Ma"],
                               "teacherId": ids["SCZ"], "roomId": ids["R204"]}}, show=True)
line("hint:", zusatz.get("hint"))
check("201 mit Hinweis auf die Zusatzstunde",
      code == 201 and zusatz["hint"] == "Zusatzstunde für 9c am 24.09.2026 angelegt.")
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
donnerstag = entries(woche, 3)
line("Donnerstag:", json.dumps(donnerstag, ensure_ascii=False))
check("am %s steht ein Eintrag ADDED ohne lessonId" % DONNERSTAG,
      len(donnerstag) == 1 and donnerstag[0]["kind"] == "ADDED"
      and donnerstag[0]["lessonId"] is None and donnerstag[0]["classShort"] == "9c"
      and donnerstag[0]["teacherShort"] == "SCZ"
      and donnerstag[0]["reasons"] == ["Nachholstunde"])
code, _ = curl("DELETE", "/api/events/%d" % zusatz["id"], show=True)
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
check("204: das Ereignis nimmt die Zusatzstunde mit",
      code == 204 and len(entries(woche, 3)) == 0)

# ------------------------------------------------------------------ Nacharbeit 05.09.
step("Nacharbeit 05.09.  Pruefungen beim Anlegen und Aufheben (Befunde der Zweitpruefung)")
line("Stand: Ausfall Meyer und Sperrung R101 am %s, keine Ausnahme offen." % MONTAG)


def feld(problem, name):
    """Die Meldung zu einem Feld einer 400-Antwort."""
    for fehler in (problem or {}).get("errors", []):
        if fehler["field"] == name:
            return fehler["message"]
    return ""


code, problem = curl("POST", "/api/exceptions",
                     {"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
                      "substituteTeacherId": ids["MEY"]}, show=True)
check("Vertretung, die nichts aendert -> 400 substituteTeacherId",
      code == 400 and feld(problem, "substituteTeacherId") == "Lehrkraft oder Raum muss sich ändern")

code, problem = curl("POST", "/api/exceptions",
                     {"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
                      "substituteTeacherId": ids["SCZ"], "substituteRoomId": ids["R101"]}, show=True)
check("gesperrter Raum als Vertretungsraum -> 400 substituteRoomId",
      code == 400 and feld(problem, "substituteRoomId") == "ist an diesem Tag nicht verfügbar")

for rumpf, name, text in [
        ({"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
          "substituteTeacherId": 999999}, "substituteTeacherId", "Lehrkraft fehlt"),
        ({"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
          "substituteRoomId": 999999}, "substituteRoomId", "Raum fehlt"),
        ({"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "MOVE", "newDate": MITTWOCH,
          "newTimeslotId": 999999}, "newTimeslotId", "Zeitfenster fehlt"),
        ({"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "CANCEL",
          "eventIds": [999999]}, "eventIds", "Ereignis fehlt")]:
    code, problem = curl("POST", "/api/exceptions", rumpf)
    line("unbekannte Kennung in %-20s -> HTTP %s (%s)" % (name, code, feld(problem, name)))
    check("unbekannte Kennung %s -> 400 „%s“ (nicht 409 in-use)" % (name, text),
          code == 400 and feld(problem, name) == text)

code, problem = curl("POST", "/api/exceptions",
                     {"date": DIENSTAG, "lessonId": sieben_a_mo1, "kind": "CANCEL"}, show=True)
check("Montagsstunde am Dienstag -> 404, keine Zeile in der Datenbank",
      code == 404 and problem.get("type") == "urn:pushnpull:problem:not-found")

code, problem = curl("POST", "/api/exceptions",
                     {"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "MOVE",
                      "newDate": MITTWOCH, "newTimeslotId": slot("TU", 1)}, show=True)
check("Verlegung auf Mittwoch mit einem Dienstags-Zeitfenster -> 400 newTimeslotId",
      code == 400 and feld(problem, "newTimeslotId") == "passt nicht zum Wochentag von „newDate“")

code, _ = curl("GET", "/api/events?from=%s&to=%s" % (FREITAG, MONTAG), show=True)
check("GET /api/events mit verdrehtem Bereich -> 400 (nicht 200 mit leerer Liste)", code == 400)

code, problem = curl("GET", "/api/substitutions/options?lessonId=%d" % sieben_a_mo1, show=True)
check("GET /api/substitutions/options ohne date -> 400 mit Feld date (nicht 500)",
      code == 400 and feld(problem, "date") == "darf nicht leer sein")

step("Nacharbeit 05.09.  Faellt die Vertretungslehrkraft ebenfalls aus, ist die Stunde wieder offen")
code, ersatz = curl("POST", "/api/exceptions",
                    {"date": MONTAG, "lessonId": sieben_a_mo1, "kind": "SUBSTITUTION",
                     "substituteTeacherId": ids["SCZ"], "substituteRoomId": ids["R204"]}, show=True)
check("Vertretung Schulz/R204 erneut angelegt -> 201", code == 201)
code, schulz_faellt_aus = curl("POST", "/api/events",
                               {"kind": "TEACHER_ABSENCE", "teacherId": ids["SCZ"],
                                "dateFrom": MONTAG, "dateTo": MONTAG, "reason": "Fortbildung"},
                               show=True)
line("affected:", json.dumps(schulz_faellt_aus["affected"], ensure_ascii=False))
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
zelle = entry_at(woche, 0, ids["7a"], 1)
line("7a 1. Stunde:", json.dumps(zelle, ensure_ascii=False))
check("die Stunde ist wieder NEEDS_SUBSTITUTION und nennt beide Anlaesse",
      code == 201 and zelle["kind"] == "NEEDS_SUBSTITUTION" and "Fortbildung" in zelle["reasons"])
check("die Ausnahme bleibt an der Zelle, damit die Planung sie aufheben kann",
      zelle["exceptionId"] == ersatz["id"])
_, offen = curl("GET", "/api/substitutions/pending?from=%s&to=%s" % (MONTAG, FREITAG))
check("sie steht wieder unter den offenen Stunden",
      any(o["lessonId"] == sieben_a_mo1 and o["date"] == MONTAG for o in offen))
_, bei_schulz = curl("GET", "/api/me/timetable/week?date=%s" % MONTAG, bearer=SCHULZ_TOKEN)
check("Schulz fuehrt sie nicht mehr als uebernommene Stunde",
      all(e["kind"] != "SUBSTITUTED" for e in entries(bei_schulz, 0)))

code, problem = curl("POST", "/api/exceptions",
                     {"date": MONTAG, "lessonId": acht_b_mo5, "kind": "SUBSTITUTION",
                      "substituteTeacherId": ids["SCZ"]}, show=True)
check("Schulz laesst sich an diesem Tag nicht mehr als Vertretung eintragen -> 400",
      code == 400 and feld(problem, "substituteTeacherId") == "ist an diesem Tag nicht verfügbar")

curl("DELETE", "/api/events/%d" % schulz_faellt_aus["id"])
curl("DELETE", "/api/exceptions/%d" % ersatz["id"])

step("Nacharbeit 05.09.  Eine Zusatzstunde geht nur mit ihrem Ereignis zurueck")
_, zusatz2 = curl("POST", "/api/events",
                  {"kind": "EXTRA_LESSON", "dateFrom": DONNERSTAG, "dateTo": DONNERSTAG,
                   "reason": "Nachholstunde",
                   "extra": {"date": DONNERSTAG, "timeslotId": slot("TH", 1),
                             "classId": ids["9c"], "subjectId": ids["Ma"],
                             "teacherId": ids["SCZ"], "roomId": ids["R204"]}})
_, ausnahmen = curl("GET", "/api/exceptions?from=%s&to=%s" % (MONTAG, FREITAG))
added = next(a for a in ausnahmen if a["kind"] == "ADDED")
code, problem = curl("DELETE", "/api/exceptions/%d" % added["id"], show=True)
check("DELETE /api/exceptions auf eine Zusatzstunde -> 400 mit Verweis auf /api/events",
      code == 400 and feld(problem, "id")
      == "Zusatzstunden werden über DELETE /api/events/{id} zurückgenommen")
_, ereignisse = curl("GET", "/api/events?from=%s&to=%s" % (DONNERSTAG, DONNERSTAG))
check("das Ereignis behaelt seine Zusatzstunde (extra ist gefuellt)",
      any(e["extra"] is not None for e in ereignisse))
curl("DELETE", "/api/events/%d" % zusatz2["id"])

# ------------------------------------------------------------------ T-110 Ferien
step("T-110  Ferien und Feiertage (Kann, E-13)")
code, feiertag = curl("POST", "/api/holidays",
                      {"name": "Tag der Deutschen Einheit", "dateFrom": DIENSTAG, "dateTo": DIENSTAG,
                       "kind": "PUBLIC_HOLIDAY"}, show=True)
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
line("Tage:", json.dumps([(d["date"], d["schoolDay"], len(d["entries"])) for d in woche["days"]]))
check("201; am Feiertag schoolDay false und keine Eintraege",
      code == 201 and day(woche, 1)["schoolDay"] is False and entries(woche, 1) == [])
check("der Montag bleibt Unterrichtstag",
      day(woche, 0)["schoolDay"] is True and len(entries(woche, 0)) == 6)
_, offen = curl("GET", "/api/substitutions/pending?from=%s&to=%s" % (MONTAG, FREITAG))
check("am Feiertag entsteht kein Vertretungsbedarf",
      all(o["date"] != DIENSTAG for o in offen))
# Nacharbeit 05.09.: an einem unterrichtsfreien Tag gibt es auch nichts einzutragen.
sieben_a_di1 = LESSONS[("7a", slot("TU", 1))]
code, _ = curl("GET", "/api/substitutions/options?date=%s&lessonId=%d" % (DIENSTAG, sieben_a_di1),
               show=True)
check("am Feiertag liefert /api/substitutions/options 404 statt Vorschlaegen", code == 404)
code, _ = curl("POST", "/api/exceptions",
               {"date": DIENSTAG, "lessonId": sieben_a_di1, "kind": "CANCEL"}, show=True)
check("am Feiertag laesst sich keine Ausnahme anlegen -> 404", code == 404)
code, _ = curl("DELETE", "/api/holidays/%d" % feiertag["id"], show=True)
_, woche = curl("GET", "/api/timetable/week?date=%s&view=SCHOOL" % MONTAG)
check("204 und der Dienstag ist wieder Unterrichtstag",
      code == 204 and day(woche, 1)["schoolDay"] is True)

# ------------------------------------------------------------------ Ergebnis
print()
print("=" * 78)
if FAILED:
    print("ABWEICHUNGEN: %d" % len(FAILED))
    for label in FAILED:
        print("  - " + label)
    sys.exit(1)
print("Alle Zusicherungen halten.")
