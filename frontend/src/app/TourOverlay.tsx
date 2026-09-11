// Rundgang durch die Webanwendung (#139, T2-3.1, E-55): sechs Schritte von den Grunddaten bis zur Vertretung,
// als kleine Karte unten rechts über jeder Seite — nicht als sperrender Dialog, damit der Link „Seite öffnen"
// die Seite dahinter zeigt und der Rundgang beim Blättern stehen bleibt (die Shell bleibt über alle Routen hinweg).
//
// Ob er erscheint, entscheidet der Server: `GET /api/me` liefert `tourCompletedAt`; null (oder fehlend — der
// Server lässt leere Felder weg, wie bei teacherId) heißt „noch nie gesehen". „Überspringen" und „Fertig" setzen
// das Feld am Konto (PUT /api/me/tour), „Rundgang erneut zeigen" in den Einstellungen löscht es wieder. Nur die
// Planung bekommt ihn — für Lehrkraft und Klassenkonto gibt es genau eine Seite, da braucht es keinen Rundgang.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Compass } from 'lucide-react'
import { useMe, useSetTour } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { useAuth } from './auth'
import { TOUR_STEPS } from './tour-steps'

export function TourOverlay() {
  const { role } = useAuth()
  const planning = role === 'PLANNING'
  const me = useMe({ enabled: planning })
  const setTour = useSetTour()
  const [step, setStep] = useState(0)

  // != null statt !== null: der Server lässt leere Felder weg (siehe Kopfkommentar).
  if (!planning || !me.data || me.data.tourCompletedAt != null) return null

  const last = step === TOUR_STEPS.length - 1
  const current = TOUR_STEPS[step]
  // Nach dem Beenden auf Schritt 1 zurück: „Rundgang erneut zeigen" soll vorne anfangen, nicht beim letzten Schritt.
  const beenden = () => setTour.mutate({ completed: true }, { onSuccess: () => setStep(0) })

  return (
    <aside
      role="dialog"
      aria-label="Rundgang"
      className="print-hide fixed right-4 bottom-4 z-40 w-[22rem] max-w-[calc(100%-2rem)] rounded-xl bg-popover p-4 text-sm text-popover-foreground shadow-lg ring-1 ring-foreground/10"
    >
      <div className="flex items-center gap-2 text-xs text-foreground-subtle">
        <Compass className="size-3.5" aria-hidden />
        <span className="font-semibold tracking-wide uppercase">Rundgang</span>
        <span className="ml-auto tabular-nums">Schritt {step + 1} von {TOUR_STEPS.length}</span>
      </div>
      <Progress value={((step + 1) / TOUR_STEPS.length) * 100} className="mt-2" aria-label={`Schritt ${step + 1} von ${TOUR_STEPS.length}`} />
      <h2 className="mt-3 font-heading text-base font-medium">{current.title}</h2>
      <p className="mt-1 text-muted-foreground">{current.text}</p>
      <Link to={current.to} className="mt-2 inline-flex items-center gap-1 font-medium text-primary underline-offset-2 hover:underline">
        {current.linkLabel} <ArrowRight className="size-3" aria-hidden />
      </Link>
      <div className="mt-4 flex items-center justify-between gap-2">
        {last
          ? <span />
          : <Button variant="ghost" size="sm" onClick={beenden} disabled={setTour.isPending}>Überspringen</Button>}
        <div className="flex gap-2">
          {step > 0 && <Button variant="outline" size="sm" onClick={() => setStep((s) => s - 1)}>Zurück</Button>}
          {last
            ? <Button size="sm" onClick={beenden} disabled={setTour.isPending}>Fertig</Button>
            : <Button size="sm" onClick={() => setStep((s) => s + 1)}>Weiter</Button>}
        </div>
      </div>
    </aside>
  )
}
