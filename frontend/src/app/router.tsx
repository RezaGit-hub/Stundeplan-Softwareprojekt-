// Router: /login → Planungs-Shell → Lehrkraft-Shell (/mein-plan) → Klassen-Shell (/klassenplan) → * (Seite nicht gefunden).
// Die Routen stehen je Bereich in src/routes/w2..w5.tsx; `handle.role` entscheidet, in welche Shell eine Route gehört
// ('TEACHER', 'STUDENT'; ohne Angabe: Planung). Eine neue Seite braucht genau einen Eintrag dort — hier ist nichts nachzutragen.
import { Suspense } from 'react'
import { createBrowserRouter, createHashRouter, type RouteObject } from 'react-router-dom'
import type { Role } from '@/api/types'
import { LoginPage } from '@/pages/login/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { w2Routes } from '@/routes/w2'
import { w3Routes } from '@/routes/w3'
import { w4Routes } from '@/routes/w4'
import { w5Routes } from '@/routes/w5'
import { RequireRole } from './RequireRole'
import { lazyPage } from '@/routes/lazy-page'

// Auch die Shell wird erst nach der Anmeldung geladen: Seitenleiste, Kopfleiste, Befehlspalette und
// die Bewegungsbibliothek gehoeren nicht in das Buendel, das die Anmeldemaske zeichnet (FRONTEND-4).
const Shell = lazyPage(() => import('./Shell'), 'Shell')

const roleOf = (r: RouteObject): Role => (r.handle as { role?: Role } | undefined)?.role ?? 'PLANNING'
const appRoutes: RouteObject[] = [...w4Routes, ...w2Routes, ...w3Routes, ...w5Routes]
const shellFor = (role: Role): RouteObject => ({
  element: (
    <RequireRole role={role}>
      <Suspense fallback={<PageSkeleton />}><Shell /></Suspense>
    </RequireRole>
  ),
  children: appRoutes.filter((r) => roleOf(r) === role),
})

const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  shellFor('PLANNING'),
  shellFor('TEACHER'),
  shellFor('STUDENT'),
  { path: '*', element: <NotFoundPage /> },
]

const create = import.meta.env.VITE_HASH_ROUTER === 'true' ? createHashRouter : createBrowserRouter
export const router = create(routes)
