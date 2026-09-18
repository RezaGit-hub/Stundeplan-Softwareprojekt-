import { AlertTriangle } from 'lucide-react'
import type { ProblemDetail } from '@/api/types'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

/** Inline fuer 409-Vorbedingungen vor der Berechnung. Kein Toast. */
export function ProblemBanner({ problem, className }: { problem: ProblemDetail; className?: string }) {
  const items = [...(problem.missing ?? []), ...(problem.conflicts ?? []).map((c) => c.message)]
  const capacityViolations = problem.capacityViolations ?? []
  return (
    <Alert variant="destructive" className={className}>
      <AlertTriangle />
      <AlertTitle>{problem.title}</AlertTitle>
      <AlertDescription>
        {problem.detail && <p>{problem.detail}</p>}
        {items.length > 0 && (
          <ul className="list-disc space-y-0.5 pl-4">
            {items.map((m, i) => <li key={i}>{m}</li>)}
          </ul>
        )}
        {capacityViolations.length > 0 && (
          <ul className="list-disc space-y-1 pl-4">
            {capacityViolations.map((violation, i) => (
              <li key={`${violation.type}-${violation.ownerId ?? violation.roomType ?? i}`}>
                <span className="font-medium">{violation.label}:</span>{' '}
                Bedarf {violation.demand}, Kapazität {violation.capacity}, es fehlen {violation.shortfall}.
                {violation.capacityFromDays != null && <> Tageskapazitäten zusammen: {violation.capacityFromDays}.</>}
                {violation.dailyRuleLimit != null && <> Tagesregel: maximal {violation.dailyRuleLimit}.</>}
                {violation.weeklyRuleLimit != null && <> Wochenregel: maximal {violation.weeklyRuleLimit}.</>}
                {violation.activeRoomCount != null && <> Aktive Räume: {violation.activeRoomCount}.</>}
                {violation.planningTimeslotCount != null && <> Planbare Zeitfenster: {violation.planningTimeslotCount}.</>}
              </li>
            ))}
          </ul>
        )}
      </AlertDescription>
    </Alert>
  )
}
