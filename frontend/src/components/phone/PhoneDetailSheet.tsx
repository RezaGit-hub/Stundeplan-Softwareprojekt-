// Stundendetail als Bottom Sheet (Material 3 ModalBottomSheet): Art der Änderung als Überschrift, alle Anlässe,
// vertretene bzw. vertretende Lehrkraft, alter → neuer Raum, bei verlegten Stunden Ursprung/Ziel (A-2, T-A2.1).
//
// Kein Radix-Dialog: das Blatt gehört in den Telefonrahmen (absolute Lage), Radix hängt seinen Inhalt an <body>
// und läge damit über der ganzen Seite. Was ein Dialog mitbringt, steht deshalb hier: Fokus hinein, Fokusfalle
// (Tab läuft im Blatt im Kreis), Escape schließt, Fokus zurück auf das Element von vorher.
import { useEffect, useRef, type ReactNode } from 'react'
import { motion } from 'motion/react'
import { X } from 'lucide-react'
import type { EntryDto, TimeslotDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { formatDate, formatDay } from '@/lib/dates'
import { ENTRY_KIND, entryLabel } from '@/lib/entry-kind'
import { EASE, FAST, SLOW } from '@/lib/motion'
import { DAY_LABEL, dayOfDate, lessonNumber, slotById } from '@/lib/timegrid'
import { cn } from '@/lib/utils'

function lessonAt(slots: TimeslotDto[], date: string, position: number): string {
  const day = dayOfDate(date)
  const slot = slots.find((s) => s.day === day && s.position === position)
  const n = slot ? lessonNumber(slot, slots) : null
  return `${formatDay(date)}, ${n != null ? `${n}. Stunde` : `Position ${position}`}`
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-border/60 py-2 last:border-0">
      <dt className="shrink-0 text-xs text-foreground-subtle">{label}</dt>
      <dd className="text-right text-sm">{children}</dd>
    </div>
  )
}

/** Sichtbare, bedienbare Elemente eines Bereichs in Dokumentreihenfolge */
function focusables(root: HTMLElement): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')]
    .filter((el) => !el.hasAttribute('disabled') && el.offsetParent !== null)
}

export function PhoneDetailSheet({ entry, slots, onClose }: { entry: EntryDto; slots: TimeslotDto[]; onClose(): void }) {
  const sheet = useRef<HTMLDivElement>(null)

  // Fokus hinein, Escape schließt, Tab bleibt im Blatt, beim Schließen Fokus zurück.
  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    focusables(sheet.current!)[0]?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.stopPropagation(); onClose(); return }
      if (e.key !== 'Tab' || !sheet.current) return
      const items = focusables(sheet.current)
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      const active = document.activeElement
      if (e.shiftKey && (active === first || !sheet.current.contains(active))) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && active === last) { e.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      opener?.focus()
    }
  }, [onClose])

  const ui = ENTRY_KIND[entry.kind]
  const slot = slotById(slots, entry.timeslotId)
  const n = slot ? lessonNumber(slot, slots) : null
  const day = dayOfDate(entry.date)
  const headline = entry.kind === 'REGULAR' ? 'Unterricht' : entryLabel(entry)
  const cancelled = entry.kind === 'CANCELLED'

  let teacherRow: ReactNode
  if (entry.kind === 'SUBSTITUTED' && entry.original?.teacherShort) {
    teacherRow = (
      <>
        <span className="text-foreground-subtle line-through">{entry.original.teacherShort}</span> → <span className="font-bold text-substitution-fg">{entry.teacherShort}</span>
        <div className="text-xs text-foreground-subtle">Sie vertreten {entry.original.teacherShort}</div>
      </>
    )
  } else if (entry.kind === 'GIVEN_AWAY') {
    teacherRow = (
      <>
        <span className="font-bold text-substitution-fg">{entry.teacherShort ?? '—'}</span>
        <div className="text-xs text-foreground-subtle">übernimmt die Stunde</div>
      </>
    )
  } else if (entry.kind === 'NEEDS_SUBSTITUTION') {
    teacherRow = <span className="text-substitution-fg">noch keine Vertretung</span>
  } else {
    teacherRow = entry.teacherShort ?? '—'
  }

  return (
    <>
      <motion.button
        type="button"
        aria-label="Schließen"
        className="absolute inset-0 z-20 bg-black/40"
        onClick={onClose}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: FAST }}
      />
      <motion.div
        ref={sheet}
        role="dialog"
        aria-modal="true"
        aria-labelledby="phone-detail-title"
        className="absolute inset-x-0 bottom-0 z-30 rounded-t-3xl bg-surface-elevated px-5 pt-2 pb-6 shadow-lg"
        initial={{ y: '100%' }}
        animate={{ y: 0 }}
        exit={{ y: '100%' }}
        transition={{ duration: SLOW, ease: EASE }}
      >
        <div aria-hidden className="mx-auto mb-3 h-1 w-8 rounded-full bg-border-strong" />
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              {ui.badge && (
                <span aria-hidden className={cn('flex size-5 items-center justify-center rounded-sm text-2xs font-bold', cancelled ? 'bg-surface-sunken text-cancelled' : 'bg-substitution text-primary-foreground')}>
                  {ui.badge}
                </span>
              )}
              <h2 id="phone-detail-title" className={cn('text-lg font-semibold', entry.kind === 'GIVEN_AWAY' && 'italic text-given-away')}>{headline}</h2>
            </div>
            <p className="mt-0.5 text-xs text-foreground-subtle">
              {day ? `${DAY_LABEL[day]}, ` : ''}{formatDate(entry.date)}{n != null ? ` · ${n}. Stunde` : ''}
            </p>
          </div>
          <Button variant="ghost" size="icon-sm" className="rounded-full" onClick={onClose} aria-label="Schließen"><X /></Button>
        </div>

        <dl className="mt-3">
          <Row label="Fach"><span className={cn('font-semibold', cancelled && 'line-through')}>{entry.subjectShort}</span></Row>
          <Row label="Klasse">{entry.classShort}</Row>
          <Row label="Raum">
            {entry.original?.roomShort && entry.roomShort ? (
              <>
                <span className="text-foreground-subtle line-through">{entry.original.roomShort}</span> → <span className="font-bold text-substitution-fg">{entry.roomShort}</span>
              </>
            ) : (
              (entry.roomShort ?? '—')
            )}
          </Row>
          <Row label="Zeit">
            <span className="tabular-nums">{slot ? `${slot.startTime}–${slot.endTime}` : '—'}</span>
          </Row>
          <Row label="Lehrkraft">{teacherRow}</Row>
          {entry.kind === 'MOVED' && entry.original?.date != null && entry.original.position != null && (
            <Row label="Verlegt von">{lessonAt(slots, entry.original.date, entry.original.position)}</Row>
          )}
        </dl>

        {entry.reasons.length > 0 && (
          <div className="mt-3 rounded-xl bg-surface-sunken px-3 py-2.5">
            <div className="text-2xs font-medium tracking-wide text-foreground-subtle uppercase">{entry.reasons.length === 1 ? 'Anlass' : 'Anlässe'}</div>
            <ul className="mt-1 space-y-0.5 text-sm">
              {entry.reasons.map((r, i) => (
                <li key={i} className="flex gap-2"><span aria-hidden className="text-foreground-subtle">•</span>{r}</li>
              ))}
            </ul>
          </div>
        )}
      </motion.div>
    </>
  )
}
