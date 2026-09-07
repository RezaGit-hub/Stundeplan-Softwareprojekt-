// W5 · App-Vorschau (nur im Mock sichtbar, Sidebar filtert über IS_MOCK)
//
// Die Seite selbst wird erst beim Aufruf nachgeladen (AppDemoRoute): sie hängt an der Attrappe, und die
// soll nicht im Produktionsbundle liegen (Befund FRONTEND-3, 05.09.).
import type { RouteObject } from 'react-router-dom'
import { AppDemoRoute } from '@/pages/app-demo/AppDemoRoute'

export const w5Routes: RouteObject[] = [{ path: '/app-demo', element: <AppDemoRoute /> }]
