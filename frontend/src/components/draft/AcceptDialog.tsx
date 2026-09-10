// Annehmen: der Entwurf wird zum aktiven Plan, der bisherige wird archiviert. Zeigt warnings[] des Entwurfs;
// Kann-Option „Gültig ab" (R1) → POST /draft/accept {validFrom?}.
//
// Annehmen ist nicht rückgängig zu machen, deshalb steht die Folge VOR dem Klick: Vertretungen, deren
// Stunde der Entwurf verschiebt, umräumt oder streicht, verfallen mit der Annahme
// (datenmodell.md: „POST /api/draft/accept liefert eine warning … und die Web-App fragt nach").
// Die Zahl rechnet der Server und liefert sie als `expiringExceptions` im Entwurf — dieselbe Rechnung,
// die er beim Annehmen anstellt, also dieselbe Zahl, die danach in warnings[] steht (Befund
// PFLICHTENHEFT-9, 05.09.). Bis dahin zählte der Dialog hier alle Ausnahmen ab heute; seit Vertretungen
// an unverändert übernommenen Stunden mitwandern, wären das fast immer zu viele gewesen.
// Ist sie 0, geht nichts verloren und es braucht kein Häkchen.
//
// Zweite Folge, die vorher sichtbar sein muss: offene Stunden wandern unverplant in den aktiven Plan und
// tauchen danach in keiner Wochensicht auf (Befund 05.09.). Ihre Zahl kommt aus openLessons des Entwurfs;
// der Server nennt sie nach dem Annehmen noch einmal in warnings[].
import { useState } from 'react'
import { toast } from 'sonner'
import { TriangleAlert } from 'lucide-react'
import { toastValidation } from '@/api/errors'
import type { OpenLessonDto } from '@/api/types'
import { useAcceptDraft } from '@/api/hooks'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { DateField } from '@/components/forms/DateField'
import { formatDay, formatDayDate, planValidFrom } from '@/lib/dates'
import { openCount } from '@/lib/draft'
import { today } from '@/lib/today'

export function AcceptDialog({ open, onOpenChange, warnings, openLessons, expiringExceptions, onAccepted }: { open: boolean; onOpenChange(open: boolean): void; warnings: string[]; openLessons: OpenLessonDto[]; expiringExceptions: number; onAccepted?(validFrom: string): void }) {
  const accept = useAcceptDraft()
  const heute = today()
  const [useValidFrom, setUseValidFrom] = useState(false)
  const [validFrom, setValidFrom] = useState<string>(heute)
  /** Untergrenze der verfallenden Vertretungen: der Gültigkeitsbeginn, frühestens heute (PFLICHTENHEFT-6). */
  const from = useValidFrom && validFrom > heute ? validFrom : heute
  /** Das Datum, für das das Häkchen gesetzt wurde — ändert sich die Untergrenze, ist es wieder offen. */
  const [confirmedFor, setConfirmedFor] = useState<string | null>(null)
  const confirmed = confirmedFor === from

  // Offene Stunden wandern beim Annehmen in den aktiven Plan, ohne Zeitfenster und Raum — danach zeigt
  // sie keine Wochensicht mehr. Deshalb steht die Zahl VOR dem Klick hier; der Server nennt sie danach
  // noch einmal in warnings[] (Befund 05.09.). Dieselbe Definition wie überall (lib/draft.ts).
  const openLessonCount = openCount(openLessons)

  // Der Server rechnet ab heute. Ein späterer Gültigkeitsbeginn kann die Zahl nur kleiner machen — bis
  // dahin gilt der bisherige Plan weiter und seine Vertretungen bleiben wirksam (PFLICHTENHEFT-6).
  // Deshalb steht dann „höchstens" davor, statt eine zweite Abfrage zu stellen.
  const spaeter = from !== heute
  const ab = spaeter ? `ab dem ${formatDay(from)}` : 'ab heute'
  const notice = expiringExceptions === 0
    ? null
    : expiringExceptions === 1
      ? `Eine Vertretung ${ab} hängt an einer Stunde, die der Entwurf verschiebt oder streicht${spaeter ? ' (höchstens, je nach Gültigkeitsbeginn)' : ''}. Sie verfällt mit der Annahme und muss danach neu eingetragen werden.`
      : `${spaeter ? 'Höchstens ' : ''}${expiringExceptions} Vertretungen ${ab} hängen an Stunden, die der Entwurf verschiebt oder streicht. Sie verfallen mit der Annahme und müssen danach neu eingetragen werden.`
  const submitDisabled = notice !== null && !confirmed

  /** Beim Schließen zurücksetzen: das nächste Öffnen fragt wieder nach (kein useEffect nötig). */
  const setOpen = (o: boolean) => {
    if (!o) setConfirmedFor(null)
    onOpenChange(o)
  }

  const submit = async () => {
    try {
      const res = await accept.mutateAsync(useValidFrom ? { validFrom } : undefined)
      // Ab wann der Plan gilt, gehört in die Erfolgsmeldung: sonst wirkt die Woche davor wie ein Datenverlust.
      const gueltigAb = res.validFrom ?? (res.acceptedAt ? planValidFrom(res.acceptedAt) : heute)
      const description = [`Gilt ab ${formatDayDate(gueltigAb)}.`, ...res.warnings].join(' · ')
      toast.success('Plan angenommen', { description })
      setOpen(false)
      onAccepted?.(gueltigAb)
    } catch (err) {
      // „Gueltig ab" ist ein Feld ohne Feldfehleranzeige; ohne diesen Aufruf bliebe eine 400-Antwort unsichtbar.
      toastValidation(err)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={setOpen}
      title="Entwurf annehmen"
      description="Der Entwurf wird zum aktiven Plan. Der bisherige Plan wird archiviert; Lehrkräfte sehen die Änderung beim nächsten Abruf."
      onSubmit={submit}
      pending={accept.isPending}
      submitDisabled={submitDisabled}
      submitLabel="Annehmen"
    >
      {openLessonCount > 0 && (
        <p className="flex items-start gap-2 rounded-md bg-warning-soft p-3 text-sm text-warning">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            {openLessonCount === 1
              ? 'Eine Stunde ist noch offen und bleibt unverplant. Sie steht danach im aktiven Plan, aber in keiner Woche.'
              : `${openLessonCount} Stunden sind noch offen und bleiben unverplant. Sie stehen danach im aktiven Plan, aber in keiner Woche.`}
            {' '}Vorher schließen: berechnen lassen oder von Hand einplanen.
          </span>
        </p>
      )}

      {warnings.length > 0 && (
        <ul className="space-y-1 rounded-md bg-warning-soft p-3 text-sm text-warning">
          {warnings.map((w, i) => (
            <li key={i} className="flex items-start gap-2"><TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />{w}</li>
          ))}
        </ul>
      )}

      {notice !== null && (
        <div className="space-y-2 rounded-md bg-warning-soft p-3 text-sm text-warning">
          <p className="flex items-start gap-2">
            <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>{notice}</span>
          </p>
          <div className="flex items-center gap-2">
            <Checkbox id="accept-confirm" checked={confirmed} onCheckedChange={(v) => setConfirmedFor(v === true ? from : null)} />
            <Label htmlFor="accept-confirm" className="font-normal text-warning">Verstanden — trotzdem annehmen</Label>
          </div>
        </div>
      )}

      <div className="rounded-md border bg-surface-sunken/50 p-3">
        <div className="flex items-center gap-2">
          <Checkbox id="accept-valid-from" checked={useValidFrom} onCheckedChange={(v) => setUseValidFrom(v === true)} />
          <Label htmlFor="accept-valid-from" className="font-normal">Erst ab einem Datum gültig</Label>
        </div>
        {useValidFrom && (
          <div className="mt-2 flex items-center gap-2">
            <DateField id="accept-valid-from-date" value={validFrom} onChange={setValidFrom} size="sm" />
            <span className="text-xs text-foreground-subtle">Bis dahin gilt der bisherige Plan.</span>
          </div>
        )}
      </div>
    </FormDialog>
  )
}
