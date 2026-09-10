// Eine Stunde als Material-Karte: Fachbalken links in subjectColor, drei Zeilen Fach · Klasse · Raum, Statusrahmen und
// Badge aus lib/entry-kind.ts (identische Beschriftung wie das Web-Raster), Textlabel unter der Karte.
import type { CSSProperties } from 'react'
import type { EntryDto } from '@/api/types'
import { ENTRY_KIND, entryLabel } from '@/lib/entry-kind'
import { cn } from '@/lib/utils'

const LABEL_CLASS: Partial<Record<EntryDto['kind'], string>> = {
  CANCELLED: 'text-cancelled',
  MOVED: 'text-cancelled',
  GIVEN_AWAY: 'text-given-away italic',
}

export function PhoneEntryCard({ entry, onClick }: { entry: EntryDto; onClick(): void }) {
  const ui = ENTRY_KIND[entry.kind]
  const label = entryLabel(entry)
  const cancelled = entry.kind === 'CANCELLED'
  const teacherChanged = entry.original?.teacherShort && entry.teacherShort
  const roomChanged = entry.original?.roomShort && entry.roomShort
  return (
    <div className="min-w-0 flex-1">
      <button
        type="button"
        onClick={onClick}
        className={cn(
          'lesson-tile relative w-full overflow-hidden rounded-xl border border-transparent border-l-0 px-3 py-2.5 pl-4 text-left shadow-xs transition-shadow hover:shadow-sm',
          ui.className,
        )}
        style={{ '--subject': entry.subjectColor } as CSSProperties}
        aria-label={`${entry.subjectShort} ${entry.classShort}${label ? `, ${label}` : ''}`}
      >
        <span aria-hidden className="absolute inset-y-0 left-0 w-1 bg-(--subject)" />
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className={cn('text-base leading-tight font-semibold', cancelled && 'line-through')}>{entry.subjectShort}</div>
            <div className="mt-0.5 text-xs">
              {entry.classShort}
              {teacherChanged && (
                <>
                  {' · '}
                  <span className="text-foreground-subtle line-through">{entry.original?.teacherShort}</span>
                  {' → '}
                  <span className="font-bold text-substitution-fg">{entry.teacherShort}</span>
                </>
              )}
            </div>
            <div className="text-xs tabular-nums">
              {roomChanged ? (
                <>
                  <span className="text-foreground-subtle line-through">{entry.original?.roomShort}</span>
                  {' → '}
                  <span className="font-bold text-substitution-fg">{entry.roomShort}</span>
                </>
              ) : (
                (entry.roomShort ?? '—')
              )}
            </div>
          </div>
          {ui.badge && (
            <span
              aria-hidden
              className={cn(
                'flex size-5 shrink-0 items-center justify-center rounded-sm text-2xs font-bold',
                cancelled ? 'bg-surface-sunken text-cancelled' : 'bg-substitution text-primary-foreground',
              )}
            >
              {ui.badge}
            </span>
          )}
        </div>
      </button>
      {label && <div className={cn('mt-1 pl-1 text-2xs font-medium text-substitution-fg', LABEL_CLASS[entry.kind])}>{label}</div>}
    </div>
  )
}
