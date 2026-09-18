// Zeitachse links (64 px): Stundennummer groß, Uhrzeit klein darunter; Pausenzeile grau (ui.md).
import type { CSSProperties } from 'react'
import type { TimeslotDto } from '@/api/types'
import { cn } from '@/lib/utils'

/** "08:00" → "8:00", damit "8:00–8:45" in 64 px passt */
function short(t: string): string {
  return t.replace(/^0/, '')
}

export function TimeAxisCell({ slot, number, style, className }: { slot?: TimeslotDto; number: number | null; style?: CSSProperties; className?: string }) {
  if (!slot) return <div style={style} className={cn('border-b border-grid-line', className)} />
  if (slot.isBreak) {
    return (
      <div style={style} className={cn('flex flex-col items-center justify-center border-b border-grid-line bg-break-row text-break-foreground', className)}>
        <span className="text-xs font-medium">Pause</span>
        <span className="text-2xs tabular-nums whitespace-nowrap">{short(slot.startTime)}–{short(slot.endTime)}</span>
      </div>
    )
  }
  return (
    <div style={style} className={cn('flex flex-col items-center justify-center border-b border-grid-line', className)}>
      <span className="text-lg leading-6 font-semibold tabular-nums">{number ?? ''}</span>
      <span className="text-2xs tabular-nums whitespace-nowrap text-muted-foreground">{short(slot.startTime)}–{short(slot.endTime)}</span>
    </div>
  )
}
