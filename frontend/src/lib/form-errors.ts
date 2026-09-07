// Feldfehler aus einem 400 "validation" (errors[{field,message}]) für Formulare.
import { useCallback, useState } from 'react'
import { ApiError, type ProblemDetail } from '@/api/types'

export function useFieldErrors() {
  const [errors, setErrors] = useState<Record<string, string>>({})

  const setFromProblem = useCallback((p: ProblemDetail) => {
    const next: Record<string, string> = {}
    for (const e of p.errors ?? []) next[e.field] = e.message
    setErrors(next)
  }, [])

  const clear = useCallback(() => setErrors({}), [])

  /**
   * In catch eines Mutationsaufrufs: übernimmt Feldfehler eines 400 und liefert true;
   * alle anderen Fehler liefern false (der Toast kommt dann aus dem Standard-onError der Mutation).
   */
  const handle = useCallback(
    (err: unknown): boolean => {
      if (err instanceof ApiError && err.status === 400 && err.problem.errors?.length) {
        setFromProblem(err.problem)
        return true
      }
      return false
    },
    [setFromProblem],
  )

  return { errors, setFromProblem, clear, handle }
}
