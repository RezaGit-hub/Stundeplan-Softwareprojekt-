import { Skeleton } from '@/components/ui/skeleton'

/** Erstaufbau einer Seite: Titelzeile + drei Blöcke. Kein Spinner. */
export function PageSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Lädt">
      <Skeleton className="h-7 w-56 bg-surface-sunken" />
      <Skeleton className="h-24 w-full bg-surface-sunken" />
      <Skeleton className="h-24 w-full bg-surface-sunken" />
      <Skeleton className="h-24 w-3/4 bg-surface-sunken" />
    </div>
  )
}
