// Die Schritte des Rundgangs (#139) — eigene Datei wie nav.ts, damit TourOverlay.tsx nur Komponenten exportiert.
//
// Der Rundgang fuehrt einmal durch den ganzen Weg und laesst dabei auf JEDER Seite etwas tun: Grunddaten
// anlegen (Lehrkraft, Fach, Klasse, Raum, Stundenraster), Regelwerk erweitern und wieder aufraeumen, Entwurf
// rechnen und annehmen, Vertretung erfassen. Jeder Schritt endet mit einem Klick des Nutzers — es gibt weder
// einen „Weiter"-Knopf noch eine Wartezeit, die von selbst ablaeuft.
//
// Wo etwas einzutragen ist, erledigt das der Rundgang: `vorbereiten` tippt Texte, waehlt Auswahlfelder und
// drueckt Hilfsknoepfe. Danach zeigt er auf „Speichern" — abgeschickt wird immer von Hand, damit niemand
// ueberrascht Daten anlegt.
//
// Wie ein Ziel gefunden wird:
//   `css`  — Selektor: Seitenleiste ueber `href`, Formularfelder ueber ihre id, Knoepfe ueber `aria-label`.
//   `text` — erster Knopf oder Link, dessen Beschriftung so beginnt; mehrere Beschriftungen = erste vorhandene.
//   `imDialog` — nur im offenen Dialog suchen; „Speichern" und „Annehmen" stehen oft auch auf der Seite dahinter.
//
// Wann ein Schritt als getan gilt (`weiter`):
//   'route' — die Seite des Schritts ist offen
//   'klick' — das Ziel wurde angeklickt
//   'lesen' — Schlusswort, nur der letzte Schritt
export type TourAktion =
  | { art: 'tippen'; css: string; wert: string; was: string }
  | { art: 'waehlen'; css: string; option?: string; was: string }
  | { art: 'klick'; css: string; was: string }

export interface TourStep {
  /** Ueberschrift im Erklaerfenster */
  title: string
  /** Was die Planung hier tut und warum */
  text: string
  /** Ziel: Selektor oder Beschriftungsanfang */
  target: { css: string; text?: never } | { text: string | string[]; css?: never }
  /** Route, auf der der Schritt spielt */
  route?: string
  /** Wie der Schritt abgeschlossen wird */
  weiter: 'route' | 'klick' | 'lesen'
  /** Aufforderung ueber dem Erklaerfenster */
  action: string
  /** Was der Rundgang selbst erledigt, bevor er auf das Ziel zeigt */
  vorbereiten?: TourAktion[]
  /** Ziel nur im offenen Dialog suchen */
  imDialog?: boolean
}

/** Kuerzel fuer einen Schritt „Seite oeffnen". */
function seite(title: string, text: string, route: string, action: string): TourStep {
  return { title, text, target: { css: `a[href="${route}"]` }, route, weiter: 'route', action }
}

export const TOUR_STEPS: TourStep[] = [
  // ---------------------------------------------------------------- Grunddaten
  seite('Lehrkräfte', 'Alles beginnt bei den Grunddaten. Lehrkräfte zuerst: Sie tragen Fächer, Wochenstunden und Sperrzeiten.',
    '/grunddaten/lehrkraefte', 'Öffnen Sie die Lehrkräfte.'),
  {
    title: 'Lehrkraft anlegen',
    text: 'Die Tabelle zeigt jede Lehrkraft mit Kürzel, Fächern und Deputat. Neue kommen über den Knopf oben rechts dazu.',
    target: { text: 'Neue Lehrkraft' }, route: '/grunddaten/lehrkraefte', weiter: 'klick',
    action: 'Klicken Sie auf „Neue Lehrkraft".',
  },
  {
    title: 'Formular der Lehrkraft',
    text: 'Das Kürzel steht später in jeder Planzelle. Das Deputat begrenzt, wie viele Stunden der Solver dieser Lehrkraft zumutet; das Raster darunter sperrt Zeiten hart.',
    target: { text: 'Speichern' }, route: '/grunddaten/lehrkraefte', weiter: 'klick', imDialog: true,
    action: 'Klicken Sie auf „Speichern".',
    vorbereiten: [
      { art: 'tippen', css: '#teacher-name', wert: 'Sommer', was: 'Name' },
      { art: 'tippen', css: '#teacher-short', wert: 'SOM', was: 'Kürzel' },
    ],
  },

  seite('Fächer', 'Jedes Fach trägt ein Kürzel und kann eine Raumanforderung haben — etwa Chemie, das einen Fachraum braucht.',
    '/grunddaten/faecher', 'Öffnen Sie die Fächer.'),
  {
    title: 'Fach anlegen',
    text: 'Fächer sind die Bausteine der Stundentafel: Erst mit ihnen lässt sich einer Klasse sagen, wie viele Stunden wovon sie bekommt.',
    target: { text: 'Neues Fach' }, route: '/grunddaten/faecher', weiter: 'klick',
    action: 'Klicken Sie auf „Neues Fach".',
  },
  {
    title: 'Formular des Fachs',
    text: 'Die Raumanforderung bleibt hier leer — dann passt jeder Raum. Trägt man eine ein, plant der Solver das Fach nur in Räume dieser Art.',
    target: { text: 'Speichern' }, route: '/grunddaten/faecher', weiter: 'klick', imDialog: true,
    action: 'Klicken Sie auf „Speichern".',
    vorbereiten: [
      { art: 'tippen', css: '#subject-name', wert: 'Kunst', was: 'Name' },
      { art: 'tippen', css: '#subject-short', wert: 'Ku', was: 'Kürzel' },
    ],
  },

  seite('Klassen', 'Klassen tragen ihren Jahrgang — daran hängen die Regeln, die nur für bestimmte Jahrgänge gelten.',
    '/grunddaten/klassen', 'Öffnen Sie die Klassen.'),
  {
    title: 'Klasse anlegen',
    text: 'Zu jeder Klasse gehört später die Stundentafel: welches Fach wie viele Wochenstunden bekommt und wer es unterrichtet.',
    target: { text: 'Neue Klasse' }, route: '/grunddaten/klassen', weiter: 'klick',
    action: 'Klicken Sie auf „Neue Klasse".',
  },
  {
    title: 'Formular der Klasse',
    text: 'Der Jahrgang ist mehr als eine Zahl: Eine Regel für Jahrgang 7 schlägt später die schulweite Regel — genau dafür steht er hier.',
    target: { text: 'Speichern' }, route: '/grunddaten/klassen', weiter: 'klick', imDialog: true,
    action: 'Klicken Sie auf „Speichern".',
    vorbereiten: [
      { art: 'tippen', css: '#class-name', wert: '7c', was: 'Name' },
      { art: 'tippen', css: '#class-grade', wert: '7', was: 'Jahrgang' },
    ],
  },

  seite('Räume', 'Räume tragen eine Art und können wiederkehrend gesperrt werden — etwa die Aula, die mittwochs belegt ist.',
    '/grunddaten/raeume', 'Öffnen Sie die Räume.'),
  {
    title: 'Raum anlegen',
    text: 'Die Raumart verbindet Raum und Fach: Ein Fach mit Anforderung „Fachraum Chemie" landet nur in Räumen dieser Art.',
    target: { text: 'Neuer Raum' }, route: '/grunddaten/raeume', weiter: 'klick',
    action: 'Klicken Sie auf „Neuer Raum".',
  },
  {
    title: 'Formular des Raums',
    text: 'Das Raster im Dialog sperrt Zeiten hart: In einer gesperrten Stunde plant der Solver diesen Raum nie — dieselbe Maske wie bei der Lehrkraft.',
    target: { text: 'Speichern' }, route: '/grunddaten/raeume', weiter: 'klick', imDialog: true,
    action: 'Klicken Sie auf „Speichern".',
    vorbereiten: [
      { art: 'tippen', css: '#room-name', wert: 'R204', was: 'Name' },
      { art: 'tippen', css: '#room-type', wert: 'Klassenraum', was: 'Raumart' },
    ],
  },

  seite('Stundenraster', 'Das Raster legt fest, wann Unterricht stattfindet: je Tag eine Folge von Zeitfenstern mit Beginn und Ende, dazwischen Pausen.',
    '/grunddaten/raster', 'Öffnen Sie das Stundenraster.'),
  {
    title: 'Zeitfenster hinzufügen',
    text: 'Jede Zeile ist ein Zeitfenster. Die Stundennummern werden gezählt, nicht gespeichert — schiebt man eine Pause dazwischen, rutscht alles dahinter mit.',
    target: { text: 'Zeile hinzufügen' }, route: '/grunddaten/raster', weiter: 'klick',
    action: 'Klicken Sie bei Montag auf „Zeile hinzufügen".',
  },
  {
    title: 'Raster speichern',
    text: 'Die neue Zeile hängt am Ende des Tages. Beginn und Ende lassen sich direkt in der Zeile ändern; das Kaffee-Symbol macht daraus eine Pause.',
    target: { text: 'Speichern' }, route: '/grunddaten/raster', weiter: 'klick',
    action: 'Klicken Sie auf „Speichern".',
  },

  // ---------------------------------------------------------------- Regelwerk
  seite('Regelwerk', 'Das Regelwerk ist Daten, kein Code: Stunden pro Tag, Pausen, Blockungen. Harte Regeln bricht kein Plan, weiche wägt der Solver ab.',
    '/regelwerk', 'Öffnen Sie das Regelwerk.'),
  {
    title: 'Regel für einen Jahrgang',
    text: 'Die Tabelle zeigt die schulweiten Werte. Für einzelne Jahrgänge lässt sich ein eigener Wert daneben stellen — er schlägt den schulweiten.',
    target: { text: 'Jahrgangswert hinzufügen' }, route: '/regelwerk', weiter: 'klick',
    action: 'Klicken Sie auf „Jahrgangswert hinzufügen".',
  },
  {
    title: 'Jahrgangswert eintragen',
    text: 'Hart heißt: Kein Plan darf die Regel brechen. Weich heißt: Der Solver wägt sie gegen andere Wünsche ab, das Gewicht sagt wie stark.',
    target: { text: 'Speichern' }, route: '/regelwerk', weiter: 'klick', imDialog: true,
    action: 'Klicken Sie auf „Speichern".',
    vorbereiten: [
      { art: 'tippen', css: '#rule-grade', wert: '7', was: 'Jahrgang' },
      { art: 'tippen', css: '#rule-value', wert: '6', was: 'Wert' },
    ],
  },
  {
    title: 'Regel wieder entfernen',
    text: 'Jahrgangswerte lassen sich einzeln zurücknehmen. Danach gilt für diesen Jahrgang wieder der schulweite Wert — nichts geht verloren.',
    target: { css: '[aria-label="Jahrgangsregel löschen"]' }, route: '/regelwerk', weiter: 'klick',
    action: 'Klicken Sie auf das Papierkorb-Symbol der neuen Zeile.',
  },
  {
    title: 'Löschen bestätigen',
    text: 'Löschen fragt immer nach. Das gilt überall dort, wo etwas verschwindet, das der Plan benutzt.',
    target: { text: 'Löschen' }, route: '/regelwerk', weiter: 'klick', imDialog: true,
    action: 'Bestätigen Sie mit „Löschen".',
  },

  // ---------------------------------------------------------------- Planung
  seite('Entwurf', 'Geplant wird nie direkt am gültigen Plan, sondern in einem Entwurf — einer Arbeitskopie daneben.',
    '/entwurf', 'Öffnen Sie den Entwurf.'),
  {
    title: 'Entwurf anlegen',
    text: 'Zwei Wege: leer beginnen, oder den aktiven Plan kopieren und daran weiterarbeiten.',
    target: { text: ['Leer beginnen', 'Aktiven Plan kopieren'] }, route: '/entwurf', weiter: 'klick',
    action: 'Legen Sie einen Entwurf an.',
  },
  {
    title: 'Plan berechnen lassen',
    text: 'Der Solver füllt die offenen Stunden nach dem Regelwerk. Von Hand festgelegte Stunden bleiben stehen — so lassen sich Vorgaben und Automatik mischen.',
    target: { text: 'Berechnen' }, route: '/entwurf', weiter: 'klick',
    action: 'Klicken Sie auf „Berechnen".',
  },
  {
    title: 'Rechenzeit bestätigen',
    text: 'Die Rechenzeit steht in den Einstellungen. Solange gerechnet wird, ist der Entwurf gesperrt; danach stehen die Stunden im Raster.',
    target: { text: 'Berechnen' }, route: '/entwurf', weiter: 'klick', imDialog: true,
    action: 'Bestätigen Sie mit „Berechnen".',
  },
  {
    title: 'Konflikt auflösen',
    text: 'Konflikte blockieren nie das Speichern — sie werden bei jedem Lesen neu berechnet. Auflösen geht per Knopf: Der Server rechnet drei kurze Läufe mit wachsendem Spielraum.',
    target: { text: 'Lösen' }, route: '/entwurf', weiter: 'klick',
    action: 'Klicken Sie in der Konfliktliste auf „Lösen".',
  },
  {
    title: 'Entwurf annehmen',
    text: 'Erst „Annehmen" macht aus dem Entwurf den aktiven Plan — den, den Lehrkräfte und Klassen zu sehen bekommen.',
    target: { text: 'Annehmen' }, route: '/entwurf', weiter: 'klick',
    action: 'Klicken Sie auf „Annehmen".',
  },
  {
    title: 'Annehmen bestätigen',
    text: 'Der Dialog zeigt vorher, was sich ändert. Danach ist der alte Plan archiviert, und es gibt wieder genau einen aktiven Plan.',
    target: { text: 'Annehmen' }, route: '/entwurf', weiter: 'klick', imDialog: true,
    action: 'Bestätigen Sie mit „Annehmen".',
  },

  // ---------------------------------------------------------------- Vertretung
  seite('Vertretung', 'Fällt jemand aus, wird der Plan nicht neu gerechnet. Stattdessen legt sich eine datierte Ausnahme über den Regelplan.',
    '/vertretung', 'Öffnen Sie die Vertretung.'),
  {
    title: 'Ereignis erfassen',
    text: 'Links wird das Ereignis erfasst — Ausfall, Raumsperrung oder Zusatzstunde. Daraus entstehen die offenen Stunden, die rechts zu vertreten sind.',
    target: { text: 'Ereignis speichern' }, route: '/vertretung', weiter: 'klick',
    action: 'Klicken Sie auf „Ereignis speichern".',
    vorbereiten: [
      { art: 'waehlen', css: '#ev-teacher', was: 'Lehrkraft' },
      { art: 'waehlen', css: '#ev-slot', was: 'Stunde' },
      { art: 'tippen', css: '#ev-reason', wert: 'Fortbildung', was: 'Anlass' },
    ],
  },
  {
    title: 'Geschafft',
    text: 'Das war der Weg von den Grunddaten bis zur Vertretung. Jede Entscheidung landet im Änderungsverlauf: wer wann was aus welchem Anlass umgeplant hat. Den Rundgang können Sie in den Einstellungen jederzeit neu starten.',
    target: { css: 'a[href="/verlauf"]' }, route: '/vertretung', weiter: 'lesen',
    action: 'Der Änderungsverlauf steht in der Seitenleiste.',
  },
]
