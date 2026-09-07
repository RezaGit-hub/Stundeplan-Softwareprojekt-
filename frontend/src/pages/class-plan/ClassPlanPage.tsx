// Klassenplan (E-50): die Woche der eigenen Klasse für ein Klassenkonto, nur lesend. Lädt ausschließlich
// GET /me/class-week und GET /timegrid — mehr darf die Rolle nicht (SecurityConfig). Gleiches Raster wie /plan
// in der Sicht Klasse; die Kachel zeigt Fach, Raum und Lehrkraft-Kürzel, das Popover zusätzlich die Art der Änderung.
//
// Datenschutz: Anlässe („Krankmeldung") kommen gar nicht erst an — der Server liefert reasons immer leer
// (07-Konzept/recherche/sammelzugang-schueler.md, Abschnitt „Bewertung"). Hier wird deshalb nichts weggefiltert;
// es gibt nichts zu filtern. Klarnamen von Lehrkräften gibt es in dieser Sicht nicht: ReadOnlyWeekPage setzt
// lookup={false} am Popover und verhindert damit jedes Nachladen der Grunddaten (die der Server ohnehin mit
// 403 ablehnt).
//
// Ohne Tag-Umschalter: den verlangt das Pflichtenheft nur für die Lehrkraft (PFLICHTENHEFT-8), und diese
// Sicht steht ohnehin unter Rückfrage R5.
import { useMyClassWeek } from '@/api/hooks'
import { parseMonday, useUrlState } from '@/components/plan/plan-params'
import { ReadOnlyWeekPage } from '@/pages/plan/ReadOnlyWeekPage'

export function ClassPlanPage() {
  const { params, set } = useUrlState()
  const monday = parseMonday(params.get('woche'))
  const week = useMyClassWeek(monday)

  return (
    <ReadOnlyWeekPage title="Klassenplan" view="CLASS" monday={monday} week={week} params={params} set={set} />
  )
}
