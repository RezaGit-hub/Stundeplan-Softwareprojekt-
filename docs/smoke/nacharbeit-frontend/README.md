# Belege der Nacharbeit Frontend (05.09.)

Erzeugt gegen den laufenden Stack (nginx `:1500`, voller Beispielbestand), Chrome headless.
Die Zeilen der Abnahme-Checkliste selbst stehen in `docs/smoke/PROTOKOLL.md` (43 Schritte, frische Datenbank).

| Datei | Zeigt |
|---|---|
| `mein-plan-druck-A4.pdf` | `/mein-plan` im Druck: keine Seitenleiste, keine Wochenwahl, keine Legende, Kopfzeile „KW 36 · 31.–04.09.2026", alle fünf Tage |
| `entwurfsbanner-ohne-score.png` | Entwurfsbanner nach einer Berechnung: „Letzte Berechnung abgeschlossen." statt `0hard/-2medium/-8soft` |
| `vertretung-400-feldfehler.png` | „Zuweisen" nach einer 400-Antwort: der Feldfehler steht jetzt als Meldung („Die Vertretungslehrkraft fällt an diesem Tag selbst aus"); vorher blieb der Knopf stumm |

Der Druck von `/plan` ist als Schritt **W-4-druck** in `abnahme.spec.ts` festgehalten
(`docs/smoke/W-4-druck-A4.pdf`, `docs/smoke/56-W_4_druck.png`).
