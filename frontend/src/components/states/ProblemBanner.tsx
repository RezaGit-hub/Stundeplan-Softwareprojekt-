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
                {violation.demand} Stunden geplant, {violation.capacity}{' '}
                {violation.type === 'CLASS_CAPACITY' || violation.type === 'TEACHER_CAPACITY'
                  ? 'Zeitfenster'
                  : 'Raumstunden'} verfügbar.
                {violation.dailyRuleLimitsCapacity && violation.dailyRuleLimit != null && (
                  <><br />Regel: maximal {violation.dailyRuleLimit} Stunden pro Tag.</>
                )}
                {violation.weeklyRuleLimitsCapacity && violation.weeklyRuleLimit != null && (
                  <><br />Regel: maximal {violation.weeklyRuleLimit} Stunden pro Woche.</>
                )}
              </li>
            ))}
          </ul>
        )}
      </AlertDescription>
    </Alert>
  )
}
