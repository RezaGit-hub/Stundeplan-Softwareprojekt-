// Die sechs Schritte des Rundgangs (#139) — eigene Datei wie nav.ts, damit TourOverlay.tsx nur Komponenten exportiert.
export interface TourStep { title: string; text: string; to: string; linkLabel: string }

export const TOUR_STEPS: TourStep[] = [
  {
    title: 'Grunddaten',
    text: 'Lehrkräfte, Fächer, Klassen und Räume anlegen — mit Sperrzeiten, Raumbedarf und Wochenstunden je Klasse. Daraus entsteht alles Weitere.',
    to: '/grunddaten/lehrkraefte', linkLabel: 'Zu den Lehrkräften',
  },
  {
    title: 'Stundenraster',
    text: 'Tage, Stunden und Uhrzeiten der Schule festlegen. Das Raster gilt für jeden Plan und jede Sperrzeit.',
    to: '/grunddaten/raster', linkLabel: 'Zum Stundenraster',
  },
  {
    title: 'Regelwerk',
    text: 'Harte und weiche Regeln prüfen — der Beispielsatz zum Bundesland liegt bereit. Harte Regeln bricht kein Plan, weiche wägt der Solver ab.',
    to: '/regelwerk', linkLabel: 'Zum Regelwerk',
  },
  {
    title: 'Entwurf und Handaufbau',
    text: 'Einen Entwurf anlegen und Stunden von Hand ins Raster ziehen oder festlegen. Konflikte erscheinen sofort, gespeichert wird immer.',
    to: '/entwurf', linkLabel: 'Zum Entwurf',
  },
  {
    title: 'Berechnen',
    text: 'Der Solver füllt die offenen Stunden nach dem Regelwerk. Festgelegte Stunden bleiben stehen; die Rechenzeit steht in den Einstellungen.',
    to: '/entwurf', linkLabel: 'Zum Entwurf',
  },
  {
    title: 'Annehmen und Vertretung',
    text: 'Mit „Annehmen" wird der Entwurf zum aktiven Plan, den Lehrkräfte und Klassen sehen. Ausfälle und Vertretungen trägt danach die Seite Vertretung ein.',
    to: '/vertretung', linkLabel: 'Zur Vertretung',
  },
]
