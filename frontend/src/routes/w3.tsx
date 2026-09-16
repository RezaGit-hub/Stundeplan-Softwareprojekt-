// W3 · WeekGrid · Aktiver Plan · Entwurf · Mein Plan · Klassenplan
import type { RouteObject } from 'react-router-dom'
import { lazyPage } from './lazy-page'

const PlanPage = lazyPage(() => import('@/pages/plan/PlanPage'), 'PlanPage')
const DraftListPage = lazyPage(() => import('@/pages/draft/DraftListPage'), 'DraftListPage')
const DraftPage = lazyPage(() => import('@/pages/draft/DraftPage'), 'DraftPage')
const MyPlanPage = lazyPage(() => import('@/pages/my-plan/MyPlanPage'), 'MyPlanPage')
const ClassPlanPage = lazyPage(() => import('@/pages/class-plan/ClassPlanPage'), 'ClassPlanPage')

export const w3Routes: RouteObject[] = [
  { path: '/plan', element: <PlanPage /> },
  // Seit #103 mehrere Entwürfe: /entwurf ist die Liste, /entwurf/:id der einzelne Entwurf.
  { path: '/entwurf', element: <DraftListPage /> },
  { path: '/entwurf/:id', element: <DraftPage /> },
  { path: '/mein-plan', element: <MyPlanPage />, handle: { role: 'TEACHER' } }, // Pflicht: handle.role für die Lehrkraft-Shell
  { path: '/klassenplan', element: <ClassPlanPage />, handle: { role: 'STUDENT' } }, // Klassenkonto-Shell (E-50)
]
