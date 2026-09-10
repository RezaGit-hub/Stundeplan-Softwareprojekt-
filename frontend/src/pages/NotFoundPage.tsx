import { Link } from 'react-router-dom'
import { Compass } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/states/EmptyState'

export function NotFoundPage() {
  return (
    <div className="flex min-h-svh items-center justify-center bg-background p-6">
      <EmptyState
        className="w-full max-w-md bg-surface"
        icon={Compass}
        title="Seite nicht gefunden"
        description="Die Adresse gibt es nicht oder sie wurde verschoben."
        action={<Button asChild><Link to="/">Zur Übersicht</Link></Button>}
      />
    </div>
  )
}
