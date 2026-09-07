import { AlertCircle } from 'lucide-react'
import { problemOf } from '@/api/errors'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

/** Query isError: Titel fett, Detail, „Erneut laden". Nimmt ApiError, ProblemDetail oder irgendeinen Fehler. */
export function ErrorState({ problem, retry }: { problem: unknown; retry?: () => void }) {
  const p = problemOf(problem)
  return (
    <Alert variant="destructive">
      <AlertCircle />
      <AlertTitle>{p.title}</AlertTitle>
      <AlertDescription>
        {p.detail && <p>{p.detail}</p>}
        {retry && (
          <Button variant="outline" size="sm" className="mt-2 w-fit" onClick={retry}>
            Erneut laden
          </Button>
        )}
      </AlertDescription>
    </Alert>
  )
}
