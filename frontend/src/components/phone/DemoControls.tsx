// Steuerleiste neben dem Telefon: Konto, Datum des Telefons, Server aus (offline), Telefon dunkel, Erststart.
// Werte leben in der URL (als, datum, offline, telefon) — die Seite setzt sie, hier nur Anzeige und onChange.
import type { ReactNode } from 'react'
import { CalendarDays, Moon, RotateCcw, WifiOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { cn } from '@/lib/utils'
import { PHONE_USERS, type PhoneUser } from './phone-state'

export interface DemoSettings { as: PhoneUser; date: string; offline: boolean; dark: boolean }

export interface DemoControlsProps {
  value: DemoSettings
  today: string
  onChange(patch: Partial<DemoSettings>): void
  onFirstStart(): void
}

const USER_LABEL: Record<PhoneUser, { name: string; hint: string }> = {
  meyer: { name: 'Meyer', hint: 'Ma · Ph, Deputat 25' },
  schulz: { name: 'Schulz', hint: 'Ma, nur 9c' },
  weber: { name: 'Weber', hint: 'De' },
}

/** Pflichtenheft-Testtag (Montag der Leitszenario-Woche) */
export const TEST_DATE = '2026-09-21'

function Row({ label, hint, htmlFor, children }: { label: string; hint?: string; htmlFor?: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <div className="min-w-0">
        <Label htmlFor={htmlFor} className="text-sm">{label}</Label>
        {hint && <p className="text-2xs text-foreground-subtle">{hint}</p>}
      </div>
      {children}
    </div>
  )
}

export function DemoControls({ value, today, onChange, onFirstStart }: DemoControlsProps) {
  return (
    <div className="rounded-lg border bg-card p-4 shadow-xs">
      <h2 className="text-2xs font-medium tracking-wide text-foreground-subtle uppercase">Steuerung</h2>

      <div className="mt-2 divide-y divide-border/70">
        <div className="py-2.5">
          <Label className="text-sm">Konto</Label>
          <RadioGroup
            value={value.as}
            onValueChange={(u) => onChange({ as: u as PhoneUser })}
            aria-label="Konto"
            className="mt-1.5 inline-flex rounded-md border bg-surface-sunken p-0.5"
          >
            {PHONE_USERS.map((u) => {
              const active = value.as === u
              return (
                <RadioGroupItem
                  key={u}
                  value={u}
                  className={cn(
                    'rounded-sm px-3 py-1 text-xs font-medium',
                    active ? 'bg-surface text-foreground shadow-xs' : 'text-foreground-subtle hover:text-foreground',
                  )}
                >
                  {USER_LABEL[u].name}
                </RadioGroupItem>
              )
            })}
          </RadioGroup>
          <p className="mt-1 text-2xs text-foreground-subtle">{USER_LABEL[value.as].hint} · Passwort = Benutzername</p>
        </div>

        <div className="py-2.5">
          <Label htmlFor="demo-date" className="text-sm">Datum des Telefons</Label>
          <div className="mt-1.5 flex flex-wrap items-center gap-2">
            <Input id="demo-date" type="date" value={value.date} onChange={(e) => e.target.value && onChange({ date: e.target.value })} className="h-8 w-40 tabular-nums" />
            <Button size="xs" variant="outline" onClick={() => onChange({ date: today })} disabled={value.date === today}><CalendarDays /> Heute</Button>
            <Button size="xs" variant="outline" onClick={() => onChange({ date: TEST_DATE })} disabled={value.date === TEST_DATE}>Testtag 21.09.</Button>
          </div>
        </div>

        <Row label="Server aus" hint="Abruf schlägt fehl, letzter Stand bleibt" htmlFor="demo-offline">
          <div className="flex items-center gap-2">
            <WifiOff className={cn('size-3.5', value.offline ? 'text-offline-foreground' : 'text-foreground-subtle/50')} aria-hidden />
            <Switch id="demo-offline" checked={value.offline} onCheckedChange={(v) => onChange({ offline: v })} />
          </div>
        </Row>

        <Row label="Telefon dunkel" hint="Theme nur im Telefon" htmlFor="demo-dark">
          <div className="flex items-center gap-2">
            <Moon className={cn('size-3.5', value.dark ? 'text-foreground' : 'text-foreground-subtle/50')} aria-hidden />
            <Switch id="demo-dark" checked={value.dark} onCheckedChange={(v) => onChange({ dark: v })} />
          </div>
        </Row>

        <Row label="Erststart" hint="Ohne Serveradresse und Anmeldung">
          <Button size="sm" variant="outline" onClick={onFirstStart}><RotateCcw /> Zurücksetzen</Button>
        </Row>
      </div>
    </div>
  )
}
