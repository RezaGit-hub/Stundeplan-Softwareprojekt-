// Mein Plan (E-38): eigener Plan der Lehrkraft, nur lesend — lädt ausschließlich GET /me/timetable/week und
// GET /timegrid (mehr darf die Rolle nicht, E-45). Gleiches Raster wie /plan in der Sicht Lehrkraft.
//
// Umschalter Tag | Woche (Befund PFLICHTENHEFT-8, 05.09.): das Musskriterium verlangt „dieselbe Tages- und
// Wochenansicht des eigenen Plans wie in der App" (zielbestimmungen.tex). Die Tagesliste zeigt dieselben
// Karten wie die App; die Auswahl steht in der URL (?ansicht=tag&tag=YYYY-MM-DD), nicht in einem Store.
//
// Den Rumpf teilt sich diese Seite mit /klassenplan: ReadOnlyWeekPage (Befund FRONTEND-8, 05.09.).
import { useMyWeek } from '@/api/hooks'
import { parseMonday, useUrlState } from '@/components/plan/plan-params'
import { ReadOnlyWeekPage } from '@/pages/plan/ReadOnlyWeekPage'

export function MyPlanPage() {
  const { params, set } = useUrlState()
  const monday = parseMonday(params.get('woche'))
  const week = useMyWeek(monday)

  return (
    <ReadOnlyWeekPage title="Mein Plan" view="TEACHER" monday={monday} week={week} params={params} set={set} dayView />
  )
}
