import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function EmptyState({ icon: Icon, title, description, action, className }: {
  icon?: LucideIcon
  title: string
  description?: string
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center rounded-lg border border-dashed bg-surface/60 px-6 py-12 text-center', className)}>
      {Icon && <Icon className="mb-3 size-10 text-foreground-subtle" strokeWidth={1.5} aria-hidden />}
      <h2 className="text-base font-semibold">{title}</h2>
      {description && <p className="mt-1 max-w-sm text-sm text-foreground-subtle">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
