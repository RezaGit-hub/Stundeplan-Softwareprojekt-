// Anmeldung (Clerk-Vorbild): .auth-bg, Glaskarte 400 px, Fehler über den Feldern, Rollen-Redirect.
// Im Mock drei Chips für die Demo-Konten. Kein „Passwort vergessen", keine Registrierung.
import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'motion/react'
import { AlertCircle, Eye, EyeOff } from 'lucide-react'
import { Logo } from '@/components/Logo'
import { IS_MOCK } from '@/api/api'
import { problemOf } from '@/api/errors'
import { useHealth } from '@/api/hooks'
import type { LoginResponse } from '@/api/types'
import { useAuth } from '@/app/auth'
import { homeFor } from '@/app/nav'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ThemeToggle } from '@/components/theme/theme-toggle'
import { cardEnter } from '@/lib/motion'

const DEMO_ACCOUNTS: { label: string; username: string }[] = [
  { label: 'Als Planung anmelden (admin)', username: 'admin' },
  { label: 'Als Lehrkraft Meyer anmelden', username: 'meyer' },
  { label: 'Als Lehrkraft Schulz anmelden', username: 'schulz' },
  { label: 'Als Klasse 7a anmelden', username: '7a' },
]

function targetFor(res: LoginResponse, from: string | null): string {
  // Lehrkraft und Klassenkonto haben genau eine Seite — „from“ zeigt bei ihnen sonst auf eine Route,
  // die RequireRole ohnehin sofort wieder umleitet (E-38, E-50).
  if (res.role !== 'PLANNING') return homeFor(res.role)
  return from && from.startsWith('/') && !from.startsWith('/login') ? from : '/'
}

export function LoginPage() {
  const { user, login, booting } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const from = params.get('from')
  const health = useHealth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  if (booting) return null
  if (user) return <Navigate to={targetFor(user, from)} replace />

  const submit = async (u: string, p: string) => {
    setError(null)
    setPending(true)
    try {
      const res = await login(u.trim(), p)
      navigate(targetFor(res, from), { replace: true })
    } catch (err) {
      const problem = problemOf(err)
      if (problem.status === 401) setError(problem.detail ?? 'Anmeldung fehlgeschlagen. Benutzername oder Passwort ist falsch.')
      else if (problem.status === 0) setError('Server nicht erreichbar.')
      else setError(problem.detail ?? problem.title)
    } finally {
      setPending(false)
    }
  }
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void submit(username, password)
  }

  return (
    <div className="auth-bg relative flex min-h-svh items-center justify-center p-6">
      <div className="absolute top-4 right-4"><ThemeToggle /></div>
      <motion.div {...cardEnter} className="w-full max-w-[400px]">
        <Card className="gap-0 rounded-xl border-border/70 bg-surface/70 p-8 shadow-lg backdrop-blur-xl dark:bg-surface/50">
          <div className="mb-6 flex items-center gap-2">
            <Logo className="size-8" />
            <span className="font-semibold">StundenPlan+</span>
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">Anmelden</h1>
          <p className="mt-1 mb-6 text-sm text-foreground-subtle">{health.data?.schoolName ?? (health.isError ? 'Server nicht erreichbar' : ' ')}</p>

          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertCircle />
              <AlertTitle>Anmeldung nicht möglich</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <form onSubmit={onSubmit} className="space-y-4">
            <div>
              <Label htmlFor="login-user">Benutzername</Label>
              <Input id="login-user" autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)} className="mt-1 bg-surface" />
            </div>
              <div>
              <Label htmlFor="login-pass">Passwort</Label>
              <div className="relative mt-1">
                <Input
                  id="login-pass"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="bg-surface pr-9"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute inset-y-0 right-2 flex items-center text-foreground-subtle"
                  aria-label={showPassword ? 'Passwort verbergen' : 'Passwort anzeigen'}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>
            <Button type="submit" className="w-full" disabled={pending}>
              {pending ? 'Anmelden…' : 'Anmelden'}
            </Button>
          </form>

          {IS_MOCK && (
            <div className="mt-6 border-t pt-4">
              <p className="mb-2 text-2xs font-medium tracking-wide text-foreground-subtle uppercase">Demo-Konten</p>
              <div className="flex flex-wrap gap-1.5">
                {DEMO_ACCOUNTS.map((a) => (
                  <Button
                    key={a.username}
                    type="button"
                    variant="outline"
                    size="xs"
                    disabled={pending}
                    onClick={() => {
                      setUsername(a.username)
                      setPassword(a.username)
                      void submit(a.username, a.username)
                    }}
                  >
                    {a.label}
                  </Button>
                ))}
              </div>
            </div>
          )}
        </Card>
      </motion.div>
    </div>
  )
}
