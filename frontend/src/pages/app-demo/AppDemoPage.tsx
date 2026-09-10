// /app-demo?als=meyer|schulz|weber&datum=&offline=1&telefon=dunkel|hell — Android-Prototyp im Browser (nur Attrappe).
// Links das Telefon (PhoneFrame + PhoneApp gegen mockApi.asUser), rechts Titel, Steuerung und Rundgang.
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Smartphone } from 'lucide-react'
import { IS_MOCK } from '@/api/api'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { useTheme } from '@/components/theme/theme-provider'
import { DemoControls, TEST_DATE, type DemoSettings } from '@/components/phone/DemoControls'
import { PhoneApp } from '@/components/phone/PhoneApp'
import { PhoneFrame } from '@/components/phone/PhoneFrame'
import { isPhoneUser, type PhoneUser } from '@/components/phone/phone-state'
import { today } from '@/lib/today'

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

const STEPS: { id: string; text: string }[] = [
  { id: 'T-A3.1', text: 'Erststart → Einstellungen: Adresse http://192.0.2.1:9999 eintragen, „Verbindung prüfen" → Warnung → „Trotzdem speichern". Neustart: Adresse bleibt.' },
  { id: 'T-A1.1', text: `Konto Meyer, Testtag ${TEST_DATE.slice(8, 10)}.${TEST_DATE.slice(5, 7)}.: Tagesansicht zeigt die eigenen Stunden, Woche die Kachelmatrix — keine Bearbeitung möglich.` },
  { id: 'T-A2.1', text: 'Nach dem Ausfall Meyer (Vertretung): Schulz sieht „Vertretung" 7a mit Anlass und vertretener Lehrkraft, Meyer sieht „abgegeben an SCZ" und „noch nicht vertreten".' },
  { id: 'E-40', text: '„Server aus": Offline-Banner mit Stand des letzten Abrufs; „Heute geändert: n Stunden" nach einem Abruf am Tag mit Änderungen.' },
]

export function AppDemoPage() {
  const [params, setParams] = useSearchParams()
  const { resolved } = useTheme()
  const [bootKey, setBootKey] = useState(0)
  const [firstStart, setFirstStart] = useState(false)

  const asParam = params.get('als')
  const value: DemoSettings = {
    as: isPhoneUser(asParam) ? asParam : 'meyer',
    date: ISO_DATE.test(params.get('datum') ?? '') ? (params.get('datum') as string) : today(),
    offline: params.get('offline') === '1',
    dark: params.get('telefon') ? params.get('telefon') === 'dunkel' : resolved === 'dark',
  }

  const update = (patch: Partial<DemoSettings>) => {
    const next = new URLSearchParams(params)
    if (patch.as !== undefined) {
      next.set('als', patch.as)
      setFirstStart(false) // Kontowechsel nach einem Erststart meldet wieder still an
    }
    if (patch.date !== undefined) next.set('datum', patch.date)
    if (patch.offline !== undefined) {
      if (patch.offline) next.set('offline', '1')
      else next.delete('offline')
    }
    if (patch.dark !== undefined) next.set('telefon', patch.dark ? 'dunkel' : 'hell')
    setParams(next, { replace: true })
  }
  const onAccountChange = (u: PhoneUser) => {
    if (u !== value.as) update({ as: u })
  }
  const firstStartNow = () => {
    setFirstStart(true)
    setBootKey((k) => k + 1)
  }

  if (!IS_MOCK) {
    return (
      <>
        <PageHeader title="App-Vorschau" />
        <EmptyState icon={Smartphone} title="Nur in der Demo verfügbar" description="Die App-Vorschau läuft gegen die eingebaute Attrappe (VITE_MOCK=true)." />
      </>
    )
  }

  return (
    <div className="flex flex-wrap items-start gap-8">
      <PhoneFrame dark={value.dark} title="Android-App (Vorschau)">
        <PhoneApp key={bootKey} as={value.as} date={value.date} offline={value.offline} firstStart={firstStart} onAccountChange={onAccountChange} />
      </PhoneFrame>

      <div className="flex min-w-[300px] flex-1 flex-col gap-4 lg:max-w-md">
        <PageHeader
          title="App-Vorschau"
          description="Die Android-App als Klick-Prototyp gegen dieselben Daten wie die Web-App: nur der eigene Plan, nur lesend."
        />
        <DemoControls value={value} today={today()} onChange={update} onFirstStart={firstStartNow} />
        <div className="rounded-lg border bg-card p-4 shadow-xs">
          <h2 className="text-2xs font-medium tracking-wide text-foreground-subtle uppercase">Rundgang</h2>
          <ol className="mt-2 space-y-2 text-sm">
            {STEPS.map((s) => (
              <li key={s.id} className="flex gap-2">
                <span className="shrink-0 rounded-sm bg-surface-sunken px-1.5 py-0.5 text-2xs font-medium tabular-nums">{s.id}</span>
                <span className="text-foreground-muted">{s.text}</span>
              </li>
            ))}
          </ol>
          <p className="mt-3 text-2xs text-foreground-subtle">
            Ausfall und Vertretung entstehen unter „Vertretung" in der Web-App (oder mit VITE_MOCK_SCENARIO=leitszenario); das Telefon holt sie beim nächsten Abruf.
          </p>
        </div>
      </div>
    </div>
  )
}
