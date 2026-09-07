#!/usr/bin/env python3
"""Abnahme des Moduls planning gegen einen laufenden Stack.

Faehrt die Checklistenzeilen P-1 bis P-6, D-4, D-5, S-1 bis S-4 sowie T-W1.2, T-W3.1, T-W3.2, T-W8.1 und
T-W9.1 ab. Jeder Schritt laeuft als echter curl-Aufruf; das Skript protokolliert Befehl, Statuscode und
die geprueften Werte und endet mit dem Exit-Code 1, sobald eine Zusicherung nicht haelt.

Voraussetzung: ein Stack mit LEERER Datenbank — die Grunddaten legt das Skript selbst an.

    cd deployment
    docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
    cd ..
    python3 scripts/abnahme-planning.py

`docker-compose.dev.yml` ist nur ein Zusatz und startet allein nichts; die Basisdatei muss davor stehen
(so steht es auch oben in der Datei selbst). Meldet Docker „port is already allocated", hoert auf dem
Rechner schon etwas auf 8080 — dann davor `BACKEND_PORT=8089` setzen und ABNAHME_BASE anpassen.

Zwei Werte kommen aus der Umgebung, beide mit dem Standard des regulaeren Stacks:

    ABNAHME_BASE       Adresse des Servers, Standard http://127.0.0.1:1500 (nginx aus docker-compose.yml;
                       das Backend direkt liegt mit dem dev-Zusatz auf http://127.0.0.1:8080)
    ABNAHME_ADMIN_PW   Passwort des Kontos `admin` — Pflicht, kein Standardwert. Der Wert steht in
                       deployment/.env (ADMIN_INITIAL_PASSWORD); fehlt er, bricht das Skript ab.

Ausgabe ist die Vorlage fuer docs/abnahme/planning.md.
"""
import json
import os
import subprocess
import sys
import time

BASE = os.environ.get("ABNAHME_BASE", "http://127.0.0.1:1500")
ADMIN_PW = os.environ.get("ABNAHME_ADMIN_PW", "")
if not ADMIN_PW:
    sys.exit(
        "ABNAHME_ADMIN_PW fehlt. Kein Standardwert mehr im Skript (Befund SICHERHEIT-1, 05.09.):\n"
        "  ABNAHME_ADMIN_PW=$(grep ^ADMIN_INITIAL_PASSWORD= deployment/.env | cut -d= -f2-) scripts/abnahme-planning.py"
    )
TOKEN = None
LOG = []


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
        print("$ curl -s -X %s %s%s%s" % (method, "$B" + path, " -H \"$A\"" if token else "", pretty))
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
        LOG.append(label)


# ------------------------------------------------------------------ Anmeldung
step("L-1  Anmeldung")
code, data = curl("POST", "/api/auth/login", {"username": "admin", "password": ADMIN_PW}, token=False, show=True)
TOKEN = data["token"]
check("Login als Planung, Token erhalten", code == 200 and TOKEN)

# ------------------------------------------------------------------ Grunddaten
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
slots = [{"day": day, "position": p, "startTime": TIMES[p - 1][0], "endTime": TIMES[p - 1][1],
          "isBreak": p == 3}
         for day in ("MO", "TU", "WE", "TH", "FR") for p in range(1, 8)]
code, _ = curl("PUT", "/api/timegrid", {"slots": slots}, show=True)
_, grid = curl("GET", "/api/timegrid")
TS = [s["id"] for s in grid["slots"]]


def slot(day, position):
    return TS[["MO", "TU", "WE", "TH", "FR"].index(day) * 7 + position - 1]


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
line("Zeitfenster MO1=%d MO2=%d MO4=%d MO5=%d MO6=%d TU1=%d FR6=%d" % (
    slot("MO", 1), slot("MO", 2), slot("MO", 4), slot("MO", 5), slot("MO", 6), slot("TU", 1), slot("FR", 6)))
line("Grunddaten stehen:", json.dumps(ids))


def add(cls, subject, day, position, room, show=False):
    return curl("POST", "/api/draft/lessons",
                {"classId": ids[cls], "subjectId": ids[subject], "timeslotId": slot(day, position),
                 "roomId": ids[room]}, show=show)


# ------------------------------------------------------------------ P-1
step("P-1  Regelplan Montag/Dienstag von Hand eintragen")
code, d = curl("POST", "/api/draft", {"source": "EMPTY"}, show=True)
check("POST /api/draft EMPTY -> 201, status EDITING", code == 201 and d["status"] == "EDITING")
plan = [("7a", "Ma", "MO", 1, "R101"), ("7a", "De", "MO", 2, "R101"), ("7a", "Ph", "MO", 4, "P1"),
        ("8b", "Ma", "MO", 2, "R204"), ("8b", "De", "MO", 4, "R204"), ("8b", "Ma", "MO", 5, "R204"),
        ("7a", "Ma", "TU", 1, "R101")]
codes = []
for cls, subject, day, position, room in plan:
    c, d = add(cls, subject, day, position, room, show=(cls, subject, day) == ("7a", "Ma", "MO"))
    codes.append(c)
check("siebenmal 201", codes == [201] * 7)
_, d = curl("GET", "/api/draft", show=True)
line("lessons:", len(d["lessons"]), "| alle pinned:", all(l["pinned"] for l in d["lessons"]))
line("teacherShort:", [l["teacherShort"] for l in d["lessons"]])
line("conflicts:", d["conflicts"])
line("openLessons:", json.dumps(d["openLessons"], ensure_ascii=False))
line("warnings:", d["warnings"])
check("sieben Stunden, alle festgelegt, keine Konflikte",
      len(d["lessons"]) == 7 and all(l["pinned"] for l in d["lessons"]) and d["conflicts"] == [])
check("Lehrkraft folgt aus der Fachzuordnung (MEY/WEB)",
      [l["teacherShort"] for l in d["lessons"]] == ["MEY", "WEB", "MEY", "MEY", "WEB", "MEY", "MEY"])
check("openLessons = 9c Ma missing 1",
      d["openLessons"] == [{"classId": ids["9c"], "classShort": "9c", "subjectShort": "Ma", "missing": 1}])

# ------------------------------------------------------------------ P-2 / T-W3.1
step("P-2 / T-W3.1  9c Mathematik Montag 1. Stunde in R101")
code, d = add("9c", "Ma", "MO", 1, "R101", show=True)
neun_c = [l for l in d["lessons"] if l["classShort"] == "9c"][0]
line("neue Stunde:", json.dumps({k: neun_c[k] for k in
                                 ("id", "classShort", "subjectShort", "teacherShort", "roomShort", "pinned")}))
line("conflicts:", json.dumps(d["conflicts"], ensure_ascii=False))
c0 = d["conflicts"][0] if d["conflicts"] else {}
sieben_a = [l for l in d["lessons"] if l["classShort"] == "7a" and l["timeslotId"] == slot("MO", 1)][0]
check("201 und genau ein Konflikt", code == 201 and len(d["conflicts"]) == 1)
check("type ROOM_DOUBLE_BOOKED, objects[0].label R101",
      c0.get("type") == "ROOM_DOUBLE_BOOKED" and c0["objects"][0]["label"] == "R101")
check("lessonIds = 7a-Stunde und 9c-Stunde",
      sorted(c0.get("lessonIds", [])) == sorted([sieben_a["id"], neun_c["id"]]))
check("message nennt R101, Montag, 1. Stunde",
      c0.get("message") == "Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).")
check("kein TEACHER_DOUBLE_BOOKED", all(c["type"] != "TEACHER_DOUBLE_BOOKED" for c in d["conflicts"]))
check("neue Stunde traegt SCZ", neun_c["teacherShort"] == "SCZ")

# ------------------------------------------------------------------ P-3 / T-W3.2
step("P-3 / T-W3.2  dieselbe Stunde auf Montag 5. Stunde verschieben")
code, d = curl("PATCH", "/api/draft/lessons/%d" % neun_c["id"], {"timeslotId": slot("MO", 6)}, show=True)
moved = [l for l in d["lessons"] if l["id"] == neun_c["id"]][0]
line("9c-Stunde:", json.dumps({k: moved[k] for k in
                               ("timeslotId", "position", "subjectShort", "teacherShort", "roomShort", "pinned")}))
line("conflicts:", d["conflicts"])
check("200, Stunde liegt auf Position 6, Fach/Lehrkraft/Raum unveraendert",
      code == 200 and moved["position"] == 6 and moved["subjectShort"] == "Ma"
      and moved["teacherShort"] == "SCZ" and moved["roomShort"] == "R101")
check("conflicts leer — Markierung an beiden Stunden weg", d["conflicts"] == [])

# ------------------------------------------------------------------ P-4 / T-W1.2
step("P-4 / T-W1.2  9c Mathematik Freitag 5. Stunde (Sperrzeit von Schulz)")
code, d = add("9c", "Ma", "FR", 6, "R101", show=True)
line("conflicts:", json.dumps(d["conflicts"], ensure_ascii=False))
c0 = d["conflicts"][0] if d["conflicts"] else {}
freitag = [l for l in d["lessons"] if l["timeslotId"] == slot("FR", 6)][0]
check("201 und genau ein Konflikt TEACHER_BLOCKED_TIME",
      code == 201 and len(d["conflicts"]) == 1 and c0.get("type") == "TEACHER_BLOCKED_TIME")
check("objects[0] ist Schulz", c0["objects"][0]["label"] == "SCZ")
check("message nennt Freitag 5. Stunde",
      c0.get("message") == "Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde")
check("Stunde gespeichert mit SCZ", freitag["teacherShort"] == "SCZ")

# ------------------------------------------------------------------ P-5 / P-5b
step("P-5 und P-5b  Freitagsstunde und 9c-Montagsstunde loeschen")
code, d = curl("DELETE", "/api/draft/lessons/%d" % freitag["id"], show=True)
check("200, conflicts leer", code == 200 and d["conflicts"] == [])
code, d = curl("DELETE", "/api/draft/lessons/%d" % neun_c["id"], show=True)
check("200, genau die sieben Stunden aus P-1", code == 200 and len(d["lessons"]) == 7)

# ------------------------------------------------------------------ P-6
step("P-6  Entwurf annehmen")
code, d = curl("POST", "/api/draft/accept", {}, show=True)
line("id:", d["id"], "acceptedAt:", d["acceptedAt"], "lessons:", len(d["lessons"]),
     "conflicts:", d["conflicts"], "warnings:", d["warnings"])
check("200, id gesetzt, sieben Stunden, keine Konflikte, keine Warnungen",
      code == 200 and d["id"] and len(d["lessons"]) == 7 and d["conflicts"] == [] and d["warnings"] == [])
code, problem = curl("GET", "/api/draft", show=True)
check("GET /api/draft -> 404 not-found",
      code == 404 and problem["type"] == "urn:pushnpull:problem:not-found")
code, active = curl("GET", "/api/timetable", show=True)
check("GET /api/timetable -> dieselben sieben Stunden", code == 200 and len(active["lessons"]) == 7)

# ------------------------------------------------------------------ D-4
step("D-4  Kopie des aktiven Plans, Stunde auf ein belegtes Zeitfenster derselben Klasse")
code, d = curl("POST", "/api/draft", {"source": "COPY_ACTIVE"}, show=True)
check("201, sieben Stunden, alle festgelegt (konfliktfreie Kopie)",
      code == 201 and len(d["lessons"]) == 7 and all(l["pinned"] for l in d["lessons"]))
x = [l for l in d["lessons"] if l["classShort"] == "7a" and l["timeslotId"] == slot("MO", 1)][0]
code, d = curl("PATCH", "/api/draft/lessons/%d" % x["id"], {"timeslotId": slot("MO", 2)}, show=True)
line("conflicts:", json.dumps([{"type": c["type"], "message": c["message"]} for c in d["conflicts"]],
                              ensure_ascii=False))
check("Antwort traegt sofort Konflikte", code == 200 and d["conflicts"] != [])

# ------------------------------------------------------------------ D-5
step("D-5  Entwurf verwerfen")
code, _ = curl("DELETE", "/api/draft", show=True)
_, active2 = curl("GET", "/api/timetable")
check("204 und der aktive Plan ist unveraendert",
      code == 204 and [l["timeslotId"] for l in active2["lessons"]] == [l["timeslotId"] for l in active["lessons"]])

# ------------------------------------------------------------------ S-1
step("S-1  Aktiven Plan leeren (Grunddaten bleiben)")
curl("POST", "/api/draft", {"source": "EMPTY"}, show=True)
code, _ = curl("POST", "/api/draft/accept", {}, show=True)
_, empty = curl("GET", "/api/timetable", show=True)
_, classes = curl("GET", "/api/classes")
check("aktiver Plan leer, Klassen bleiben", empty["lessons"] == [] and len(classes) == 3)

# ------------------------------------------------------------------ S-2 / T-W8.1
step("S-2 / T-W8.1  Vorschlag erzeugen (Rechenzeit 5 s)")
_, config = curl("GET", "/api/config")
config["maxSolveSeconds"] = 5
code, config = curl("PUT", "/api/config", config, show=True)
curl("POST", "/api/draft", {"source": "EMPTY"})
begin = time.time()
code, _ = curl("POST", "/api/draft/solve", {}, show=True)
check("POST /api/draft/solve -> 202", code == 202)
status = None
for _ in range(30):
    _, d = curl("GET", "/api/draft")
    status = d["status"]
    if status != "SOLVING":
        break
    time.sleep(2)
elapsed = time.time() - begin
line("Laufzeit bis status != SOLVING: %.1f s (Grenze maxSolveSeconds 5 s + 5 s)" % elapsed)
line("solve:", json.dumps(d["solve"], ensure_ascii=False))
line("lessons:", len(d["lessons"]), "openLessons:", json.dumps(d["openLessons"], ensure_ascii=False))
line("Konfliktarten:", sorted({c["type"] for c in d["conflicts"]}))
check("Ende innerhalb maxSolveSeconds + 5 s, status EDITING, lastScore gesetzt",
      elapsed <= 10 and status == "EDITING" and d["solve"]["lastScore"] is not None)
check("jede Stunde ist belegt oder offen (kein dritter Zustand)",
      all((l["timeslotId"] is None) == (l["roomId"] is None) for l in d["lessons"]))
check("keine Doppelbelegung von Raum, Lehrkraft oder Klasse",
      all(c["type"] not in ("ROOM_DOUBLE_BOOKED", "TEACHER_DOUBLE_BOOKED", "CLASS_DOUBLE_BOOKED")
          for c in d["conflicts"]))
draft_count = len(d["lessons"])
draft_places = {l["id"]: (l["timeslotId"], l["roomId"]) for l in d["lessons"]}

# ------------------------------------------------------------------ S-3 / S-4
step("S-3  Aktiven Plan ansehen, ohne anzunehmen")
_, still_empty = curl("GET", "/api/timetable", show=True)
check("weiter lessons: []", still_empty["lessons"] == [])

step("S-4  Vorschlag annehmen")
code, accepted = curl("POST", "/api/draft/accept", {}, show=True)
_, now_active = curl("GET", "/api/timetable", show=True)
check("aktiver Plan hat genau die Stunden des Entwurfs",
      code == 200 and len(now_active["lessons"]) == draft_count
      and {l["id"]: (l["timeslotId"], l["roomId"]) for l in now_active["lessons"]} == draft_places)

# ------------------------------------------------------------------ T-W9.1
step("T-W9.1  Festgelegte Stunde ueberlebt die Neuberechnung")
curl("POST", "/api/draft", {"source": "COPY_ACTIVE"}, show=True)
_, d = curl("GET", "/api/draft")
for lesson in d["lessons"]:
    if lesson["pinned"]:
        curl("PATCH", "/api/draft/lessons/%d" % lesson["id"], {"pinned": False})
_, d = curl("GET", "/api/draft")
used = {l["timeslotId"] for l in d["lessons"] if l["timeslotId"]}
free = [s["id"] for s in grid["slots"] if not s["isBreak"] and s["id"] not in used][0]
x = [l for l in d["lessons"] if l["timeslotId"] and not l["pinned"]][0]
code, d = curl("PATCH", "/api/draft/lessons/%d" % x["id"], {"timeslotId": free}, show=True)
moved = [l for l in d["lessons"] if l["id"] == x["id"]][0]
line("Stunde %d: timeslotId=%s roomId=%s pinned=%s" % (
    x["id"], moved["timeslotId"], moved["roomId"], moved["pinned"]))
check("Verschieben legt die Stunde fest, ohne dass pinned im Rumpf steht",
      code == 200 and moved["pinned"] is True and moved["timeslotId"] == free)
y_z = [l["id"] for l in d["lessons"] if not l["pinned"]][:2]
code, _ = curl("POST", "/api/draft/solve", {}, show=True)
for _ in range(30):
    _, d = curl("GET", "/api/draft")
    if d["status"] != "SOLVING":
        break
    time.sleep(2)
after = [l for l in d["lessons"] if l["id"] == x["id"]][0]
line("nach dem Lauf: timeslotId=%s roomId=%s pinned=%s" % (
    after["timeslotId"], after["roomId"], after["pinned"]))
line("Konfliktarten:", sorted({c["type"] for c in d["conflicts"]}))
check("x unveraendert (gleiches timeslotId und roomId)",
      after["timeslotId"] == moved["timeslotId"] and after["roomId"] == moved["roomId"])
check("keine *_DOUBLE_BOOKED-Konflikte",
      all(c["type"] not in ("ROOM_DOUBLE_BOOKED", "TEACHER_DOUBLE_BOOKED", "CLASS_DOUBLE_BOOKED")
          for c in d["conflicts"]))
code, d = curl("PATCH", "/api/draft/lessons/%d" % x["id"], {"pinned": False}, show=True)
check("pinned:false hebt die Festlegung auf",
      [l for l in d["lessons"] if l["id"] == x["id"]][0]["pinned"] is False)

# ------------------------------------------------------------------ 409 solving
step("409 solving  Waehrend eines Laufs wird nichts geaendert")
_, config = curl("GET", "/api/config")
config["maxSolveSeconds"] = 30
curl("PUT", "/api/config", config)
code, _ = curl("POST", "/api/draft/solve", {}, show=True)
code_add, problem = curl("POST", "/api/draft/lessons",
                         {"classId": ids["7a"], "subjectId": ids["Ma"], "timeslotId": slot("WE", 1),
                          "roomId": ids["R101"]}, show=True)
line("type:", problem["type"])
line("detail:", problem["detail"])
code_accept, _ = curl("POST", "/api/draft/accept", {}, show=True)
code_delete, _ = curl("DELETE", "/api/draft", show=True)
_, d = curl("GET", "/api/draft")
check("POST/accept/DELETE waehrend des Laufs -> 409 solving, GET meldet SOLVING",
      code_add == 409 and problem["type"] == "urn:pushnpull:problem:solving"
      and code_accept == 409 and code_delete == 409 and d["status"] == "SOLVING")
code, _ = curl("DELETE", "/api/draft/solve", show=True)
for _ in range(20):
    _, d = curl("GET", "/api/draft")
    if d["status"] != "SOLVING":
        break
    time.sleep(2)
line("status nach Abbruch:", d["status"], "lastScore:", d["solve"]["lastScore"])
check("Abbruch 202, Ergebnis uebernommen",
      code == 202 and d["status"] == "EDITING" and d["solve"]["lastScore"] is not None)

# ------------------------------------------------------------------ 409 pinned-contradiction
step("409 pinned-contradiction  Zwei festgelegte Stunden im selben Raum")
_, d = curl("GET", "/api/draft")
a = [l for l in d["lessons"] if l["timeslotId"]][0]
curl("PATCH", "/api/draft/lessons/%d" % a["id"], {"pinned": True})
code, d = curl("POST", "/api/draft/lessons",
               {"classId": ids["9c"], "subjectId": ids["Ma"], "timeslotId": a["timeslotId"],
                "roomId": a["roomId"]}, show=True)
code, problem = curl("POST", "/api/draft/solve", {}, show=True)
line("type:", problem["type"])
line("detail:", problem["detail"])
line("conflicts:", json.dumps(problem.get("conflicts"), ensure_ascii=False))
check("409 pinned-contradiction mit conflicts[]",
      code == 409 and problem["type"] == "urn:pushnpull:problem:pinned-contradiction"
      and problem.get("conflicts"))

# ------------------------------------------------------------------ 409 master-data-incomplete
step("409 master-data-incomplete  Aktive Klasse ohne Fach")
curl("DELETE", "/api/draft")
curl("POST", "/api/classes", {"name": "10d", "grade": 10, "active": True})
curl("POST", "/api/draft", {"source": "EMPTY"})
code, problem = curl("POST", "/api/draft/solve", {}, show=True)
line("type:", problem["type"])
line("detail:", problem["detail"])
line("missing:", json.dumps(problem.get("missing"), ensure_ascii=False))
check("409 master-data-incomplete mit missing[]",
      code == 409 and problem["type"] == "urn:pushnpull:problem:master-data-incomplete"
      and "Klasse 10d hat kein Fach" in problem.get("missing", []))

# ------------------------------------------------------------------ Zugriffsschutz
step("Zugriffsschutz")
saved, TOKEN = TOKEN, None
code_tt, _ = curl("GET", "/api/timetable", token=False, show=True)
code_dr, _ = curl("GET", "/api/draft", token=False, show=True)
TOKEN = saved
check("ohne Token 401 auf /api/timetable und /api/draft", code_tt == 401 and code_dr == 401)

print()
print("### Ergebnis")
print("  Abweichungen: %d" % len(LOG))
for entry in LOG:
    print("  - " + entry)
sys.exit(1 if LOG else 0)
