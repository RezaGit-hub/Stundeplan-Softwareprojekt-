// Die Schritte des Rundgangs (#139) — eigene Datei wie nav.ts, damit TourOverlay.tsx nur Komponenten exportiert.
//
// Der Rundgang fuehrt einmal durch den ganzen Weg: Grunddaten anlegen, Plan rechnen lassen, Konflikt aufloesen,
// annehmen, Vertretung und Ereignisse. Jeder Schritt zeigt auf ein echtes Bedienelement, das hervorgehoben und
// als einziges klickbar bleibt.
//
// Wie ein Ziel gefunden wird:
//   `css`  — Selektor, fuer die Eintraege der Seitenleiste ueber ihr `href`. Die Seitenleiste gehoert Rolle 5,
//            der Rundgang soll sie nicht anfassen muessen.
//   `text` — erster Knopf oder Link, dessen Beschriftung so beginnt. Fuer Knoepfe innerhalb der Seiten; sie
//            tragen keine eigene Kennzeichnung, und eine einzufuehren hiesse, fremde Dateien anzufassen.
//
// Wann ein Schritt als getan gilt (`weiter`):
//   'route' — die Seite des Schritts ist offen (der Klick hat navigiert)
//   'klick' — das Ziel wurde angeklickt (Dialog geht auf, Berechnung startet …)
//   'lesen' — nichts zu tun, der Schritt erklaert nur; „Weiter" fuehrt hinaus
//
// Faellt ein Ziel weg (Knopf nur bei vorhandenem Entwurf, Seitenleiste eingeklappt), faengt das Overlay das ab:
// die Sperre bleibt aus, der Schritt bleibt lesbar, „Weiter" bleibt erreichbar.
export interface TourStep {
  /** Ueberschrift im Erklaerfenster */
  title: string
  /** Was die Planung hier tut und warum */
  text: string
  /** Ziel: Selektor oder Beschriftungsanfang */
  target: { css: string; text?: never } | { text: string; css?: never }
  /** Route, auf der der Schritt spielt — der Rundgang wartet darauf bei `weiter: 'route'` */
  route?: string
  /** Wie der Schritt abgeschlossen wird */
  weiter: 'route' | 'klick' | 'lesen'
  /** Aufforderung ueber dem Erklaerfenster */
  action: string
  /** Was danach zu sehen ist; steht als Bestaetigung da, bevor es weitergeht */
  result: string
}

export const TOUR_STEPS: TourStep[] = [
  {
    title: 'Grunddaten',
    text: 'Alles beginnt bei den Grunddaten: Lehrkräfte, Fächer, Klassen und Räume. Ohne sie gibt es nichts zu planen.',
    target: { css: 'a[href="/grunddaten/lehrkraefte"]' },
    route: '/grunddaten/lehrkraefte',
    weiter: 'route',
    action: 'Öffnen Sie die Lehrkräfte.',
    result: 'Die Tabelle listet jede Lehrkraft mit Kürzel, Fächern und Wochenstunden. Fächer, Klassen und Räume liegen daneben und funktionieren genauso.',
  },
  {
    title: 'Eine Lehrkraft anlegen',
    text: 'Jede Lehrkraft braucht Kürzel, ihre Fächer und die Wochenstunden. Sperrzeiten und bevorzugte Zeiten kommen im selben Dialog dazu.',
    target: { text: 'Neue Lehrkraft' },
    route: '/grunddaten/lehrkraefte',
    weiter: 'klick',
    action: 'Klicken Sie auf „Neue Lehrkraft".',
    result: 'Der Dialog erwartet Name, Kürzel, Fächer und Wochenstunden. Das Raster darunter sperrt Zeiten hart — in einer gesperrten Stunde plant der Solver diese Lehrkraft nie. Schließen Sie den Dialog mit Escape, es geht weiter.',
  },
  {
    title: 'Stundenraster',
    text: 'Das Raster legt Tage, Stunden und Uhrzeiten der Schule fest. Es gilt für jeden Plan und jede Sperrzeit.',
    target: { css: 'a[href="/grunddaten/raster"]' },
    route: '/grunddaten/raster',
    weiter: 'route',
    action: 'Öffnen Sie das Stundenraster.',
    result: 'Jede Zeile ist ein Zeitfenster mit Beginn und Ende. Pausen stehen als eigene Zeilen dazwischen — ohne sie kann keine Pausenregel greifen.',
  },
  {
    title: 'Regelwerk',
    text: 'Das Regelwerk ist Daten, kein Code: Stunden pro Tag, Pausen, Blockungen. Harte Regeln bricht kein Plan, weiche wägt der Solver ab.',
    target: { css: 'a[href="/regelwerk"]' },
    route: '/regelwerk',
    weiter: 'route',
    action: 'Öffnen Sie das Regelwerk.',
    result: 'Jede Zeile trägt Typ, Bezug, Wert, Härte und Gewicht. Der Satz zum Bundesland liegt bereit; eine Zeile für einen Jahrgang schlägt die schulweite Zeile.',
  },
  {
    title: 'Entwurf',
    text: 'Geplant wird nie direkt am gültigen Plan, sondern in einem Entwurf — einer Arbeitskopie daneben.',
    target: { css: 'a[href="/entwurf"]' },
    route: '/entwurf',
    weiter: 'route',
    action: 'Öffnen Sie den Entwurf.',
    result: 'Ohne Entwurf stehen hier zwei Wege: leer beginnen oder den aktiven Plan kopieren. Mit Entwurf zeigt das Wochenraster die Stunden, daneben offene Stunden und Konflikte.',
  },
  {
    title: 'Plan berechnen lassen',
    text: 'Der Solver füllt die offenen Stunden nach dem Regelwerk. Von Hand festgelegte Stunden bleiben dabei stehen — so lassen sich Vorgaben und Automatik mischen.',
    target: { text: 'Berechnen' },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Klicken Sie auf „Berechnen".',
    result: 'Die Rechenzeit steht in den Einstellungen; solange gerechnet wird, ist der Entwurf gesperrt. Danach stehen die Stunden im Raster, und die Konfliktliste zeigt, was nicht aufging.',
  },
  {
    title: 'Konflikt auflösen',
    text: 'Konflikte blockieren nie das Speichern — sie werden bei jedem Lesen neu berechnet und angezeigt. Auflösen lassen sie sich per Knopf.',
    target: { text: 'Lösen' },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Klicken Sie in der Konfliktliste auf „Lösen".',
    result: 'Der Server rechnet drei kurze Läufe mit wachsendem Spielraum: erst nur die Konfliktstunde frei, dann die Nachbarstunden der Klasse, dann auch Lehrkraft und Raum des Tages. Daraus werden bis zu drei Vorschläge, die Sie einzeln übernehmen.',
  },
  {
    title: 'Entwurf annehmen',
    text: 'Erst „Annehmen" macht aus dem Entwurf den aktiven Plan — den, den Lehrkräfte und Klassen zu sehen bekommen.',
    target: { text: 'Annehmen' },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Klicken Sie auf „Annehmen".',
    result: 'Der Dialog zeigt vorher, was sich ändert. Nach dem Annehmen ist der alte Plan archiviert, und es gibt wieder genau einen aktiven Plan.',
  },
  {
    title: 'Vertretung',
    text: 'Fällt jemand aus, wird der Plan nicht neu gerechnet. Stattdessen legt eine datierte Ausnahme sich über den Regelplan.',
    target: { css: 'a[href="/vertretung"]' },
    route: '/vertretung',
    weiter: 'route',
    action: 'Öffnen Sie die Vertretung.',
    result: 'Links stehen die offenen Stunden des Tages, rechts Vorschläge für Ersatz — mit Angabe, warum jemand passt oder nicht.',
  },
  {
    title: 'Ereignis erfassen',
    text: 'Ausfall einer Lehrkraft, Raumsperrung oder Zusatzstunde: Jedes Ereignis wird hier mit Datum und Grund erfasst und erzeugt die offenen Stunden, die daneben zu vertreten sind.',
    target: { text: 'Ereignis speichern' },
    route: '/vertretung',
    weiter: 'lesen',
    action: 'Sehen Sie sich das Formular an — ausfüllen können Sie es gleich selbst.',
    result: 'Jede Entscheidung landet im Änderungsverlauf: wer wann was aus welchem Anlass umgeplant hat. Damit ist der Rundgang durch.',
  },
]
