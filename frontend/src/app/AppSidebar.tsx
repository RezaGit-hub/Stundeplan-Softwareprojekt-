// Sidebar 240 px, Icon-Modus 3rem (⌘B). Einträge aus nav.ts; TEACHER sieht nur „Mein Plan“, STUDENT nur
// „Klassenplan“ plus „Abmelden“ (E-50) — ein Klassenkonto hat sonst nichts zu bedienen.
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { CalendarDays, LogOut, PanelLeftClose } from 'lucide-react'
import { IS_MOCK } from '@/api/api'
import { useDraft, useHealth, usePending } from '@/api/hooks'
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader,
  SidebarMenu, SidebarMenuBadge, SidebarMenuButton, SidebarMenuItem, SidebarRail, useSidebar,
} from '@/components/ui/sidebar'
import { weekRange } from '@/lib/dates'
import { today } from '@/lib/today'
import { useAuth } from './auth'
import { homeFor, navFor, type NavItem } from './nav'

const GROUP_ORDER = [null, 'Grunddaten', 'Planung', 'Änderungen', 'System']

export function AppSidebar() {
  const { role, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const { toggleSidebar, state } = useSidebar()
  const items = navFor(role, IS_MOCK)
  const health = useHealth()
  const planning = role === 'PLANNING'
  const draft = useDraft({ enabled: planning })
  const week = weekRange(today())
  const pending = usePending(week.from, week.to, { enabled: planning })
  const pendingCount = pending.data?.length ?? 0

  const isActive = (n: NavItem) => (n.to === '/' ? location.pathname === '/' : location.pathname === n.to || location.pathname.startsWith(`${n.to}/`))
  const groups = GROUP_ORDER.filter((g) => items.some((n) => n.group === g))

  return (
    <Sidebar collapsible="icon" className="print-hide">
      <SidebarHeader className="px-3 pt-3 group-data-[collapsible=icon]:px-2">
        <Link to={homeFor(role)} className="flex items-center gap-2 overflow-hidden rounded-md outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring">
          <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary text-primary-foreground group-data-[collapsible=icon]:size-8"><CalendarDays className="size-4" /></span>
          <span className="min-w-0 group-data-[collapsible=icon]:hidden">
            <span className="block truncate text-sm font-semibold text-foreground">Stundenplanung</span>
            <span className="block truncate text-xs text-foreground-subtle">{health.data?.schoolName ?? ' '}</span>
          </span>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        {groups.map((g) => (
          <SidebarGroup key={g ?? '_'} className="py-1">
            {g && <SidebarGroupLabel className="text-2xs tracking-wide uppercase">{g}</SidebarGroupLabel>}
            <SidebarGroupContent>
              <SidebarMenu>
                {items.filter((n) => n.group === g).map((n) => {
                  const active = isActive(n)
                  return (
                    <SidebarMenuItem key={n.to}>
                      {active && <motion.span layoutId="active-nav" className="absolute inset-y-1.5 left-0 w-0.5 rounded-r bg-sidebar-primary" aria-hidden />}
                      <SidebarMenuButton asChild isActive={active} tooltip={n.label} className="font-medium">
                        <Link to={n.to}>
                          <n.icon />
                          <span>{n.label}</span>
                        </Link>
                      </SidebarMenuButton>
                      {n.to === '/entwurf' && draft.data && (
                        <SidebarMenuBadge><span className="size-1.5 rounded-full bg-draft-fg" title="Entwurf vorhanden" /></SidebarMenuBadge>
                      )}
                      {n.to === '/vertretung' && pendingCount > 0 && (
                        <SidebarMenuBadge className="rounded-full bg-substitution-soft text-substitution-fg">{pendingCount}</SidebarMenuBadge>
                      )}
                    </SidebarMenuItem>
                  )
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          {role === 'STUDENT' && (
            <SidebarMenuItem>
              <SidebarMenuButton onClick={() => { void logout().then(() => navigate('/login')) }} tooltip="Abmelden" className="text-foreground-subtle">
                <LogOut />
                <span>Abmelden</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          )}
          <SidebarMenuItem>
            <SidebarMenuButton onClick={toggleSidebar} tooltip="Seitenleiste ausklappen" className="text-foreground-subtle">
              <PanelLeftClose className={state === 'collapsed' ? 'rotate-180' : ''} />
              <span>Einklappen</span>
              <kbd className="ml-auto rounded-sm border px-1 font-sans text-2xs">⌘B</kbd>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  )
}
