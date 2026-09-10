// Die eine Tabelle der Navigation (Sidebar, Breadcrumb, Command-Palette). Icons aus lucide-react.
import {
  BookOpen, CalendarClock, CalendarDays, DoorOpen, GraduationCap, History, LayoutDashboard, PencilRuler, Replace,
  Settings, SlidersHorizontal, Smartphone, Users, type LucideIcon,
} from 'lucide-react'
import type { Role } from '@/api/types'

export interface NavItem {
  group: string | null
  label: string
  icon: LucideIcon
  to: string
  /** nur im Mock sichtbar (App-Vorschau) */
  mockOnly?: boolean
}

export const PLANNING_NAV: NavItem[] = [
  { group: null, label: 'Übersicht', icon: LayoutDashboard, to: '/' },
  { group: 'Grunddaten', label: 'Lehrkräfte', icon: Users, to: '/grunddaten/lehrkraefte' },
  { group: 'Grunddaten', label: 'Fächer', icon: BookOpen, to: '/grunddaten/faecher' },
  { group: 'Grunddaten', label: 'Klassen', icon: GraduationCap, to: '/grunddaten/klassen' },
  { group: 'Grunddaten', label: 'Räume', icon: DoorOpen, to: '/grunddaten/raeume' },
  { group: 'Grunddaten', label: 'Stundenraster', icon: CalendarClock, to: '/grunddaten/raster' },
  { group: 'Planung', label: 'Aktiver Plan', icon: CalendarDays, to: '/plan' },
  { group: 'Planung', label: 'Entwurf', icon: PencilRuler, to: '/entwurf' },
  { group: 'Planung', label: 'Regelwerk', icon: SlidersHorizontal, to: '/regelwerk' },
  { group: 'Änderungen', label: 'Vertretung', icon: Replace, to: '/vertretung' },
  { group: 'Änderungen', label: 'Änderungsverlauf', icon: History, to: '/verlauf' },
  { group: 'System', label: 'Einstellungen', icon: Settings, to: '/einstellungen' },
  { group: 'System', label: 'App-Vorschau', icon: Smartphone, to: '/app-demo', mockOnly: true },
]

export const TEACHER_NAV: NavItem[] = [{ group: null, label: 'Mein Plan', icon: CalendarDays, to: '/mein-plan' }]

/** Klassenkonto (E-50): eine einzige Seite — der Plan der eigenen Klasse, nur lesend. */
export const STUDENT_NAV: NavItem[] = [{ group: null, label: 'Klassenplan', icon: CalendarDays, to: '/klassenplan' }]

/** Startseite je Rolle — Ziel nach dem Anmelden und Rückfallweg bei einer fremden Route. */
export function homeFor(role: Role | null): string {
  if (role === 'TEACHER') return '/mein-plan'
  if (role === 'STUDENT') return '/klassenplan'
  return '/'
}

export function navFor(role: Role | null, isMock: boolean): NavItem[] {
  if (role === 'TEACHER') return TEACHER_NAV
  if (role === 'STUDENT') return STUDENT_NAV
  return PLANNING_NAV.filter((n) => !n.mockOnly || isMock)
}

/** Eintrag zur aktuellen URL: längster passender Pfad ("/grunddaten/klassen/7" → Klassen) */
export function findNav(pathname: string, role: Role | null): NavItem | undefined {
  const items = role === 'TEACHER' ? TEACHER_NAV : role === 'STUDENT' ? STUDENT_NAV : PLANNING_NAV
  let best: NavItem | undefined
  for (const n of items) {
    const hit = n.to === '/' ? pathname === '/' : pathname === n.to || pathname.startsWith(`${n.to}/`)
    if (hit && (!best || n.to.length > best.to.length)) best = n
  }
  return best
}

export const ROLE_LABEL: Record<Role, string> = { PLANNING: 'Planung', TEACHER: 'Lehrkraft', STUDENT: 'Klasse' }
