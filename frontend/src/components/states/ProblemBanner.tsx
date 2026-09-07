import { AlertTriangle } from 'lucide-react'
import type { ProblemDetail } from '@/api/types'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

/** Inline für 409 mit missing[] / conflicts[] (Berechnen-Vorbedingung). Kein Toast. */
export function ProblemBanner({ problem, className }: { problem: ProblemDetail; className?: string }) {
  const items = [...(problem.missing ?? []), ...(problem.conflicts ?? []).map((c) => c.message)]
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
      </AlertDescription>
    </Alert>
  )
}
