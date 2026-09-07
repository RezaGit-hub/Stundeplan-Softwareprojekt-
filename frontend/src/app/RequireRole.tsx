// Routenschutz (E-38, E-50): nicht angemeldet → /login?from=…; falsche Rolle → auf die Startseite der eigenen Rolle
// (PLANNING „/“, TEACHER „/mein-plan“, STUDENT „/klassenplan“). Damit landet ein Klassenkonto auf keiner Seite,
// deren Daten der Server ihm ohnehin verweigert — der Schutz selbst steht im Backend (SecurityConfig).
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Role } from '@/api/types'
import { useAuth } from './auth'
import { homeFor } from './nav'

export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, booting } = useAuth()
  const location = useLocation()
  if (booting) return null
  if (!user) {
    const from = `${location.pathname}${location.search}`
    return <Navigate to={`/login?from=${encodeURIComponent(from)}`} replace />
  }
  if (user.role !== role) return <Navigate to={homeFor(user.role)} replace />
  return <>{children}</>
}
