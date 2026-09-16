// Die Schritte des Rundgangs (#139) — eigene Datei wie nav.ts, damit TourOverlay.tsx nur Komponenten exportiert.
//
// Der Rundgang fuehrt einmal durch den ganzen Weg: Grunddaten anlegen, Plan rechnen lassen, Konflikt aufloesen,
// annehmen, Vertretung und Ereignisse. Jeder Schritt zeigt auf ein echtes Bedienelement, hebt es hervor und
// laesst nur dieses eine anklicken. Einen „Weiter"-Knopf gibt es nicht: Sobald die Aufgabe getan ist, geht es
// von allein zum naechsten Schritt.
//
// Wie ein Ziel gefunden wird:
//   `css`  — Selektor, fuer die Eintraege der Seitenleiste ueber ihr `href` und fuer Formularfelder ueber ihre id.
//   `text` — erster Knopf oder Link, dessen Beschriftung so beginnt; mehrere Beschriftungen = erste vorhandene.
//
// Wann ein Schritt als getan gilt (`weiter`):
//   'route' — die Seite des Schritts ist offen (der Klick hat navigiert)
//   'klick' — das Ziel wurde angeklickt (Dialog geht auf, Berechnung startet …)
//   'lesen' — nichts zu tun, der Schritt erklaert nur; er ist der letzte
//
// `tippen` fuellt ein Formular vor: Der Rundgang schreibt die Werte selbst ins Feld, Zeichen fuer Zeichen.
// Erst danach zeigt er auf das Ziel des Schritts (meist „Speichern").
export interface TourStep {
  /** Ueberschrift im Erklaerfenster */
  title: string
  /** Was die Planung hier tut und warum */
  text: string
  /** Ziel: Selektor oder Beschriftungsanfang; mehrere Beschriftungen = erste vorhandene gewinnt */
  target: { css: string; text?: never } | { text: string | string[]; css?: never }
  /** Route, auf der der Schritt spielt */
  route?: string
  /** Wie der Schritt abgeschlossen wird */
  weiter: 'route' | 'klick' | 'lesen'
  /** Aufforderung ueber dem Erklaerfenster */
  action: string
  /** Felder, die der Rundgang selbst ausfuellt, bevor er auf das Ziel zeigt */
  tippen?: { css: string; wert: string; beschriftung: string }[]
}

export const TOUR_STEPS: TourStep[] = [
  {
    title: 'Grunddaten',
    text: 'Alles beginnt bei den Grunddaten: Lehrkräfte, Fächer, Klassen und Räume. Ohne sie gibt es nichts zu planen.',
    target: { css: 'a[href="/grunddaten/lehrkraefte"]' },
    route: '/grunddaten/lehrkraefte',
    weiter: 'route',
    action: 'Öffnen Sie die Lehrkräfte.',
  },
  {
    title: 'Eine Lehrkraft anlegen',
    text: 'Jede Lehrkraft braucht einen Namen, ein Kürzel und ihre Wochenstunden. Fächer, Sperrzeiten und bevorzugte Zeiten kommen im selben Dialog dazu.',
    target: { text: 'Neue Lehrkraft' },
    route: '/grunddaten/lehrkraefte',
    weiter: 'klick',
    action: 'Klicken Sie auf „Neue Lehrkraft".',
  },
  {
    title: 'Formular ausfüllen',
    text: 'Das Kürzel steht später in jeder Planzelle, die Wochenstunden begrenzen, wie viel der Solver dieser Lehrkraft zumutet.',
    target: { text: 'Speichern' },
    route: '/grunddaten/lehrkraefte',
    weiter: 'klick',
    action: 'Klicken Sie auf „Speichern".',
    tippen: [
      { css: '#teacher-name', wert: 'Sommer', beschriftung: 'Name' },
      { css: '#teacher-short', wert: 'SOM', beschriftung: 'Kürzel' },
    ],
  },
  {
    title: 'Stundenraster',
    text: 'Das Raster legt Tage, Stunden und Uhrzeiten der Schule fest. Es gilt für jeden Plan und jede Sperrzeit.',
    target: { css: 'a[href="/grunddaten/raster"]' },
    route: '/grunddaten/raster',
    weiter: 'route',
    action: 'Öffnen Sie das Stundenraster.',
  },
  {
    title: 'Regelwerk',
    text: 'Das Regelwerk ist Daten, kein Code: Stunden pro Tag, Pausen, Blockungen. Harte Regeln bricht kein Plan, weiche wägt der Solver ab.',
    target: { css: 'a[href="/regelwerk"]' },
    route: '/regelwerk',
    weiter: 'route',
    action: 'Öffnen Sie das Regelwerk.',
  },
  {
    title: 'Entwurf',
    text: 'Geplant wird nie direkt am gültigen Plan, sondern in einem Entwurf — einer Arbeitskopie daneben.',
    target: { css: 'a[href="/entwurf"]' },
    route: '/entwurf',
    weiter: 'route',
    action: 'Öffnen Sie den Entwurf.',
  },
  {
    title: 'Entwurf anlegen',
    text: 'Zwei Wege: leer beginnen, oder den aktiven Plan kopieren und daran weiterarbeiten.',
    target: { text: ['Leer beginnen', 'Aktiven Plan kopieren'] },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Legen Sie einen Entwurf an.',
  },
  {
    title: 'Plan berechnen lassen',
    text: 'Der Solver füllt die offenen Stunden nach dem Regelwerk. Von Hand festgelegte Stunden bleiben stehen — so lassen sich Vorgaben und Automatik mischen.',
    target: { text: 'Berechnen' },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Klicken Sie auf „Berechnen".',
  },
  {
    title: 'Konflikt auflösen',
    text: 'Konflikte blockieren nie das Speichern — sie werden bei jedem Lesen neu berechnet. Auflösen lassen sie sich per Knopf: Der Server rechnet drei kurze Läufe mit wachsendem Spielraum.',
    target: { text: 'Lösen' },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Klicken Sie in der Konfliktliste auf „Lösen".',
  },
  {
    title: 'Entwurf annehmen',
    text: 'Erst „Annehmen" macht aus dem Entwurf den aktiven Plan — den, den Lehrkräfte und Klassen zu sehen bekommen.',
    target: { text: 'Annehmen' },
    route: '/entwurf',
    weiter: 'klick',
    action: 'Klicken Sie auf „Annehmen".',
  },
  {
    title: 'Vertretung',
    text: 'Fällt jemand aus, wird der Plan nicht neu gerechnet. Stattdessen legt sich eine datierte Ausnahme über den Regelplan.',
    target: { css: 'a[href="/vertretung"]' },
    route: '/vertretung',
    weiter: 'route',
    action: 'Öffnen Sie die Vertretung.',
  },
  {
    title: 'Ereignis erfassen',
    text: 'Ausfall, Raumsperrung oder Zusatzstunde: Jedes Ereignis wird hier mit Datum und Grund erfasst und erzeugt die offenen Stunden, die daneben zu vertreten sind. Jede Entscheidung landet im Änderungsverlauf.',
    target: { text: 'Ereignis speichern' },
    route: '/vertretung',
    weiter: 'lesen',
    action: 'Damit ist der Rundgang durch — ausfüllen können Sie das Formular gleich selbst.',
  },
]
