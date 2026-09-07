// Ladehülle der App-Vorschau: die Seite hängt an der Attrappe und wird deshalb erst beim Aufruf
// nachgeladen — sonst läge die Attrappe im Produktionsbundle (Befund FRONTEND-3, 05.09.).
import { lazy, Suspense } from 'react'
import { PageSkeleton } from '@/components/states/PageSkeleton'

const AppDemoPage = lazy(() => import('./AppDemoPage').then((m) => ({ default: m.AppDemoPage })))

export function AppDemoRoute() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <AppDemoPage />
    </Suspense>
  )
}
