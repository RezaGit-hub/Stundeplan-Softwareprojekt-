// W2 · Grunddaten · Regelwerk · Stundenraster · Einstellungen
import type { RouteObject } from 'react-router-dom'
import { ClassCrumb } from '@/pages/masterdata/ClassCrumb'
import { lazyPage } from './lazy-page'

const TeachersPage = lazyPage(() => import('@/pages/masterdata/TeachersPage'), 'TeachersPage')
const SubjectsPage = lazyPage(() => import('@/pages/masterdata/SubjectsPage'), 'SubjectsPage')
const ClassesPage = lazyPage(() => import('@/pages/masterdata/ClassesPage'), 'ClassesPage')
const ClassDetailPage = lazyPage(() => import('@/pages/masterdata/ClassDetailPage'), 'ClassDetailPage')
const RoomsPage = lazyPage(() => import('@/pages/masterdata/RoomsPage'), 'RoomsPage')
const TimegridPage = lazyPage(() => import('@/pages/masterdata/TimegridPage'), 'TimegridPage')
const RulesPage = lazyPage(() => import('@/pages/rules/RulesPage'), 'RulesPage')
const SettingsPage = lazyPage(() => import('@/pages/settings/SettingsPage'), 'SettingsPage')

export const w2Routes: RouteObject[] = [
  { path: '/grunddaten/lehrkraefte', element: <TeachersPage /> },
  { path: '/grunddaten/faecher', element: <SubjectsPage /> },
  { path: '/grunddaten/klassen', element: <ClassesPage /> },
  { path: '/grunddaten/klassen/:id', element: <ClassDetailPage />, handle: { crumb: (p: Record<string, string | undefined>) => <ClassCrumb id={p.id} /> } },
  { path: '/grunddaten/raeume', element: <RoomsPage /> },
  { path: '/grunddaten/raster', element: <TimegridPage /> },
  { path: '/regelwerk', element: <RulesPage /> },
  { path: '/einstellungen', element: <SettingsPage /> },
]
