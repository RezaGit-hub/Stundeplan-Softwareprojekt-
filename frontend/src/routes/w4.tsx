// W4 · Übersicht (/) · Vertretung · Änderungsverlauf
import type { RouteObject } from 'react-router-dom'
import { lazyPage } from './lazy-page'

const StartPage = lazyPage(() => import('@/pages/start/StartPage'), 'StartPage')
const SubstitutionPage = lazyPage(() => import('@/pages/substitution/SubstitutionPage'), 'SubstitutionPage')
const ChangelogPage = lazyPage(() => import('@/pages/changelog/ChangelogPage'), 'ChangelogPage')

export const w4Routes: RouteObject[] = [
  { index: true, element: <StartPage /> },
  { path: '/vertretung', element: <SubstitutionPage /> },
  { path: '/verlauf', element: <ChangelogPage /> },
]
