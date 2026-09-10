// Einstellungen (Erststart, Zahnrad): Serveradresse als Ganzes, „Verbindung prüfen" → GET /api/health, bei Fehler
// Warnung + „Trotzdem speichern" (A-3, T-A3.1); darunter Anmeldung bzw. „Angemeldet als …" + Abmelden; Neustart; Version.
import { useState, type FormEvent, type ReactNode } from 'react'
import { ArrowLeft, CheckCircle2, Loader2, LogOut, Power, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { PhoneAppBar } from './PhoneFrame'
import { APP_VERSION, DEFAULT_SERVER_URL, type Prefs, type UiState } from './phone-state'

export interface CheckResult { ok: boolean; schoolName?: string }

export interface PhoneSettingsScreenProps {
  ui: UiState
  prefs: Prefs
  onCheck(url: string): Promise<CheckResult>
  onSaveUrl(url: string): void
  /** liefert eine Fehlermeldung in Schulsprache oder null bei Erfolg */
  onLogin(username: string, password: string): Promise<string | null>
  onLogout(): void
  onRestart(): void
  onBack(): void
}

type Check = { status: 'idle' } | { status: 'checking' } | { status: 'ok'; schoolName: string } | { status: 'fail' }

function Section({ title, children, className }: { title: string; children: ReactNode; className?: string }) {
  return (
    <section className={cn('rounded-2xl border bg-surface p-4 shadow-xs', className)}>
      <h3 className="mb-3 text-2xs font-medium tracking-wide text-foreground-subtle uppercase">{title}</h3>
      {children}
    </section>
  )
}

export function PhoneSettingsScreen({ ui, prefs, onCheck, onSaveUrl, onLogin, onLogout, onRestart, onBack }: PhoneSettingsScreenProps) {
  const [url, setUrl] = useState(prefs.serverUrl ?? DEFAULT_SERVER_URL)
  const [check, setCheck] = useState<Check>({ status: 'idle' })
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [loginError, setLoginError] = useState<string | null>(null)
  const loggedIn = !!prefs.token
  const saved = prefs.serverUrl != null && prefs.serverUrl === url.trim()

  const runCheck = async () => {
    setCheck({ status: 'checking' })
    const r = await onCheck(url.trim())
    setCheck(r.ok ? { status: 'ok', schoolName: r.schoolName ?? '' } : { status: 'fail' })
  }
  const save = () => {
    onSaveUrl(url.trim())
    setCheck({ status: 'idle' })
  }
  const submitLogin = async (e: FormEvent) => {
    e.preventDefault()
    setPending(true)
    setLoginError(null)
    const err = await onLogin(username.trim(), password)
    setPending(false)
    if (err) setLoginError(err)
  }

  return (
    <div className="flex h-full flex-col">
      <PhoneAppBar
        title="Einstellungen"
        leading={loggedIn ? <Button variant="ghost" size="icon" className="rounded-full" onClick={onBack} aria-label="Zurück"><ArrowLeft /></Button> : undefined}
      />
      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-3 pb-4">
        <Section title="Server">
          {ui.kind === 'noServer' && <p className="mb-3 text-sm">Bitte zuerst die Serveradresse eintragen.</p>}
          <Label htmlFor="phone-url" className="text-xs">Serveradresse</Label>
          <Input
            id="phone-url"
            inputMode="url"
            autoComplete="off"
            spellCheck={false}
            value={url}
            onChange={(e) => {
              setUrl(e.target.value)
              setCheck({ status: 'idle' })
            }}
            className="mt-1 h-10 rounded-xl bg-background font-mono text-xs"
          />
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Button size="sm" variant="secondary" className="rounded-full" onClick={() => void runCheck()} disabled={check.status === 'checking' || !url.trim()}>
              {check.status === 'checking' ? <Loader2 className="animate-spin" /> : null}
              Verbindung prüfen
            </Button>
            {saved && check.status === 'idle' && <span className="text-2xs text-foreground-subtle">Gespeichert</span>}
          </div>
          {check.status === 'ok' && (
            <div className="mt-3 flex items-center justify-between gap-2 rounded-xl bg-success-soft px-3 py-2 text-xs text-success">
              <span className="flex items-center gap-1.5"><CheckCircle2 className="size-3.5 shrink-0" /> Erreichbar: {check.schoolName}</span>
              <Button size="xs" className="rounded-full" onClick={save}>Speichern</Button>
            </div>
          )}
          {check.status === 'fail' && (
            <div className="mt-3 rounded-xl bg-warning-soft px-3 py-2 text-xs text-warning">
              <p className="flex items-start gap-1.5"><TriangleAlert className="mt-0.5 size-3.5 shrink-0" /> Server nicht erreichbar. Adresse trotzdem speichern?</p>
              <Button size="xs" variant="outline" className="mt-2 rounded-full" onClick={save}>Trotzdem speichern</Button>
            </div>
          )}
        </Section>

        <Section title="Anmeldung">
          {loggedIn ? (
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-medium">Angemeldet als {prefs.displayName ?? prefs.username}</div>
                <div className="text-2xs text-foreground-subtle">Nur der eigene Plan, lesend</div>
              </div>
              <Button size="sm" variant="outline" className="rounded-full" onClick={onLogout}><LogOut /> Abmelden</Button>
            </div>
          ) : (
            <form onSubmit={(e) => void submitLogin(e)} className="space-y-3">
              {ui.kind === 'loginRequired' && ui.message && <p className="text-xs text-foreground-subtle">{ui.message}</p>}
              <div>
                <Label htmlFor="phone-user" className="text-xs">Benutzername</Label>
                <Input id="phone-user" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} className="mt-1 h-10 rounded-xl bg-background" />
              </div>
              <div>
                <Label htmlFor="phone-pass" className="text-xs">Passwort</Label>
                <Input id="phone-pass" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} className="mt-1 h-10 rounded-xl bg-background" />
              </div>
              {loginError && <p className="text-xs text-destructive" role="alert">{loginError}</p>}
              {!prefs.serverUrl && <p className="text-2xs text-foreground-subtle">Zuerst die Serveradresse speichern.</p>}
              <Button type="submit" className="w-full rounded-full" disabled={pending || !prefs.serverUrl || !username.trim()}>
                {pending ? 'Anmelden…' : 'Anmelden'}
              </Button>
            </form>
          )}
        </Section>

        <Section title="App">
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-foreground-subtle">Neustart der App: Adresse und Anmeldung bleiben gespeichert.</p>
            <Button size="sm" variant="outline" className="rounded-full" onClick={onRestart}><Power /> Neustart</Button>
          </div>
          <p className="mt-4 text-center text-2xs text-foreground-subtle">{APP_VERSION}</p>
        </Section>
      </div>
    </div>
  )
}
