// Annehmen (#116): der Dialog stellt genau eine Frage — was soll mit diesem Entwurf geschehen? Drei Wege:
//
//   1. Jetzt aktivieren      POST /api/drafts/{id}/accept {}                → gilt ab heute
//   2. Ab Datum aktivieren   POST /api/drafts/{id}/accept {validFrom[, validUntil]}
//   3. In die Ablage legen   PATCH /api/drafts/{id} {finished: true}        → eingefroren, nichts wird gültig
//
// Seit #115 trägt jeder angenommene Plan einen Zeitraum, und es dürfen mehrere nebeneinander gelten,
// solange die Zeiträume sich nicht überschneiden. Der bisherige Plan endet deshalb beim Weg 2 am Vortag
// statt sofort archiviert zu werden — „der alte Plan bleibt bis zu seinem Ende gültig".
// Belegt schon ein künftiger Plan den Zeitraum, antwortet der Server 409 `plan-overlap`; diese Meldung
// gehört in den Dialog, nicht in einen Toast, weil genau hier das Datum steht, das sie meint.
//
// Annehmen ist nicht rückgängig zu machen, deshalb steht die Folge VOR dem Klick: Vertretungen, deren
// Stunde der Entwurf verschiebt, umräumt oder streicht, verfallen mit der Annahme. Die Zahl rechnet der
// Server und liefert sie als `expiringExceptions` im Entwurf — dieselbe Rechnung, die er beim Annehmen
// anstellt (Befund PFLICHTENHEFT-9). Ist sie 0, geht nichts verloren und es braucht kein Häkchen.
// Zweite Folge: offene Stunden wandern unverplant in den aktiven Plan und tauchen danach in keiner
// Wochensicht auf (Befund 05.09.). Beide Warnungen gelten nur fürs Aktivieren, nicht für die Ablage.
import { useState } from 'react'
import { toast } from 'sonner'
import { Archive, CalendarClock, PlayCircle, TriangleAlert } from 'lucide-react'
import { isProblem, problemOf, toastValidation } from '@/api/errors'
import { PROBLEM_PREFIX, type OpenLessonDto, type ProblemDetail } from '@/api/types'
import { useAcceptDraft, usePatchDraft } from '@/api/hooks'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Button } from '@/components/ui/button'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { DateField } from '@/components/forms/DateField'
import { FieldError } from '@/components/forms/FieldError'
import { ProblemBanner } from '@/components/states/ProblemBanner'
import { formatDay, formatDayDate, planValidFrom } from '@/lib/dates'
import { openCount } from '@/lib/draft'
import { today } from '@/lib/today'
import { cn } from '@/lib/utils'

/** Die drei Wege des Dialogs. `park` nimmt nichts an — er legt den Entwurf nur beiseite. */
type Way = 'now' | 'from' | 'park'

const WAYS: { value: Way; icon: typeof PlayCircle; label: string; hint: string }[] = [
  { value: 'now', icon: PlayCircle, label: 'Jetzt aktivieren', hint: 'Der Plan gilt ab heute. Ein Plan, der heute schon gilt, endet gestern.' },
  { value: 'from', icon: CalendarClock, label: 'Ab Datum aktivieren', hint: 'Der bisherige Plan gilt bis zum Vortag weiter.' },
  { value: 'park', icon: Archive, label: 'In die Ablage legen', hint: 'Der Entwurf wird eingefroren und kann später aktiviert werden.' },
]

export function AcceptDialog({ open, onOpenChange, draftId, warnings, openLessons, expiringExceptions, hardConflicts = 0, onAccepted, onShowConflicts }: {
  open: boolean
  onOpenChange(open: boolean): void
  draftId: number
  warnings: string[]
  openLessons: OpenLessonDto[]
  expiringExceptions: number
  /** harte Konflikte im Entwurf — sie blockieren das Annehmen (409 `draft-has-conflicts`) */
  hardConflicts?: number
  onAccepted?(validFrom: string): void
  /** Zur Konfliktliste der Entwurfsseite springen (wird beim 409 `draft-has-conflicts` angeboten) */
  onShowConflicts?(): void
}) {
  // `quiet`: die beiden 409, die dieser Dialog selbst darstellt — sonst stünde die Meldung nur als Toast,
  // weit weg von dem Datumsfeld, um das es geht.
  const accept = useAcceptDraft(draftId, { quiet: (err) => isProblem(err, 'plan-overlap') || isProblem(err, 'draft-has-conflicts') })
  const park = usePatchDraft(draftId)
  const heute = today()
  const [way, setWay] = useState<Way>('now')
  const [validFrom, setValidFrom] = useState<string>(heute)
  const [useValidUntil, setUseValidUntil] = useState(false)
  const [validUntil, setValidUntil] = useState<string>(heute)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)

  const aktiviert = way !== 'park'
  /** Untergrenze der verfallenden Vertretungen: der Gültigkeitsbeginn, frühestens heute (PFLICHTENHEFT-6). */
  const from = way === 'from' && validFrom > heute ? validFrom : heute
  /** Das Datum, für das das Häkchen gesetzt wurde — ändert sich die Untergrenze, ist es wieder offen. */
  const [confirmedFor, setConfirmedFor] = useState<string | null>(null)
  const confirmed = confirmedFor === from
  /** Zeitraum verdreht: das prüft die Oberfläche selbst, damit niemand für ein 400 auf den Server wartet. */
  const rangeError = way === 'from' && useValidUntil && validUntil < validFrom ? 'Das Ende darf nicht vor dem Beginn liegen.' : undefined

  const openLessonCount = openCount(openLessons)

  // Der Server rechnet ab heute. Ein späterer Gültigkeitsbeginn kann die Zahl nur kleiner machen — bis
  // dahin gilt der bisherige Plan weiter und seine Vertretungen bleiben wirksam (PFLICHTENHEFT-6).
  const spaeter = from !== heute
  const ab = spaeter ? `ab dem ${formatDay(from)}` : 'ab heute'
  const notice = !aktiviert || expiringExceptions === 0
    ? null
    : expiringExceptions === 1
      ? `Eine Vertretung ${ab} hängt an einer Stunde, die der Entwurf verschiebt oder streicht${spaeter ? ' (höchstens, je nach Gültigkeitsbeginn)' : ''}. Sie verfällt mit der Annahme und muss danach neu eingetragen werden.`
      : `${spaeter ? 'Höchstens ' : ''}${expiringExceptions} Vertretungen ${ab} hängen an Stunden, die der Entwurf verschiebt oder streicht. Sie verfallen mit der Annahme und müssen danach neu eingetragen werden.`
  const submitDisabled = (notice !== null && !confirmed) || rangeError !== undefined || (aktiviert && hardConflicts > 0)

  /** Beim Schließen zurücksetzen: das nächste Öffnen fragt wieder nach (kein useEffect nötig). */
  const setOpen = (o: boolean) => {
    if (!o) { setConfirmedFor(null); setProblem(null) }
    onOpenChange(o)
  }

  const submit = async () => {
    setProblem(null)
    if (way === 'park') {
      try {
        await park.mutateAsync({ finished: true })
        toast.success('Entwurf in die Ablage gelegt', { description: 'Er bleibt erhalten und lässt sich jederzeit wieder öffnen.' })
        setOpen(false)
      } catch { /* Toast kam aus dem Standard-onError */ }
      return
    }
    try {
      const body = way === 'from' ? { validFrom, ...(useValidUntil ? { validUntil } : {}) } : undefined
      const res = await accept.mutateAsync(body)
      // Ab wann der Plan gilt, gehört in die Erfolgsmeldung: sonst wirkt die Woche davor wie ein Datenverlust.
      const gueltigAb = res.validFrom ?? (res.acceptedAt ? planValidFrom(res.acceptedAt) : heute)
      const bis = res.validUntil ? ` bis ${formatDayDate(res.validUntil)}` : ''
      const description = [`Gilt ab ${formatDayDate(gueltigAb)}${bis}.`, ...res.warnings].join(' · ')
      toast.success('Plan angenommen', { description })
      setOpen(false)
      onAccepted?.(gueltigAb)
    } catch (err) {
      // Die beiden Fälle, die dieser Dialog selbst zeigt (der Hook schweigt dazu).
      if (isProblem(err, 'plan-overlap') || isProblem(err, 'draft-has-conflicts')) setProblem(problemOf(err))
      // „Gültig ab/bis" sind Felder ohne Feldfehleranzeige; ohne diesen Aufruf bliebe eine 400-Antwort unsichtbar.
      else toastValidation(err)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={setOpen}
      title="Entwurf annehmen"
      description="Ein angenommener Plan gilt für Lehrkräfte und App. Wer noch nicht so weit ist, legt den Entwurf in die Ablage."
      onSubmit={submit}
      pending={accept.isPending || park.isPending}
      submitDisabled={submitDisabled}
      submitLabel={way === 'park' ? 'In die Ablage' : 'Annehmen'}
    >
      <RadioGroup value={way} onValueChange={(v) => { setWay(v as Way); setProblem(null) }} className="grid gap-2">
        {WAYS.map((w) => (
          <div key={w.value} className={cn('rounded-md border p-3 transition-colors', way === w.value ? 'border-primary/50 bg-primary-soft/40' : 'bg-surface-sunken/40')}>
            <div className="flex items-center gap-2">
              <RadioGroupItem
                value={w.value}
                id={`accept-way-${w.value}`}
                className="size-4 shrink-0 rounded-full border data-[state=checked]:border-[5px] data-[state=checked]:border-primary"
              />
              <Label htmlFor={`accept-way-${w.value}`} className="font-medium"><w.icon className="size-4 text-foreground-subtle" aria-hidden /> {w.label}</Label>
            </div>
            <p className="mt-1 pl-6 text-xs text-foreground-subtle">{w.hint}</p>
            {w.value === 'from' && way === 'from' && (
              <div className="mt-2 space-y-2 pl-6">
                <div className="flex flex-wrap items-center gap-2">
                  <Label htmlFor="accept-valid-from-date" className="text-xs font-normal text-foreground-subtle">Gültig ab</Label>
                  <DateField id="accept-valid-from-date" value={validFrom} onChange={setValidFrom} size="sm" />
                </div>
                <div className="flex items-center gap-2">
                  <Checkbox id="accept-use-valid-until" checked={useValidUntil} onCheckedChange={(v) => setUseValidUntil(v === true)} />
                  <Label htmlFor="accept-use-valid-until" className="font-normal">Auch ein Enddatum setzen</Label>
                </div>
                {useValidUntil && (
                  <div className="flex flex-wrap items-center gap-2">
                    <Label htmlFor="accept-valid-until-date" className="text-xs font-normal text-foreground-subtle">Gültig bis</Label>
                    <DateField id="accept-valid-until-date" value={validUntil} onChange={setValidUntil} size="sm" />
                    <span className="text-xs text-foreground-subtle">Danach gilt kein Plan, bis ein weiterer angenommen wird.</span>
                  </div>
                )}
                <FieldError message={rangeError} />
              </div>
            )}
          </div>
        ))}
      </RadioGroup>

      {problem && (
        <div className="space-y-2">
          <ProblemBanner problem={problem} />
          {problem.type === `${PROBLEM_PREFIX}draft-has-conflicts` && onShowConflicts && (
            <Button type="button" variant="outline" size="sm" onClick={() => { setOpen(false); onShowConflicts() }}>Zur Konfliktliste</Button>
          )}
        </div>
      )}

      {aktiviert && hardConflicts > 0 && (
        <p className="flex items-start gap-2 rounded-md bg-conflict-soft p-3 text-sm text-conflict">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            {hardConflicts === 1 ? 'Ein harter Konflikt blockiert das Annehmen.' : `${hardConflicts} harte Konflikte blockieren das Annehmen.`}
            {' '}Erst lösen — oder den Entwurf so lange in die Ablage legen.
            {onShowConflicts && <> <button type="button" className="font-medium underline underline-offset-2" onClick={() => { setOpen(false); onShowConflicts() }}>Zur Konfliktliste</button></>}
          </span>
        </p>
      )}

      {aktiviert && openLessonCount > 0 && (
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

      {aktiviert && warnings.length > 0 && (
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
    </FormDialog>
  )
}
