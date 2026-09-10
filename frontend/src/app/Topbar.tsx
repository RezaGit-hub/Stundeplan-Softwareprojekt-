// Topbar 52 px: Sidebar-Toggle, Breadcrumb, Suchfeld-Attrappe (⌘K), Theme, Benutzermenü.
import { useState, type ReactNode } from 'react'
import { Link, useLocation, useMatches, useNavigate } from 'react-router-dom'
import { KeyRound, LogOut, Search } from 'lucide-react'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { SidebarTrigger } from '@/components/ui/sidebar'
import { ThemeToggle } from '@/components/theme/theme-toggle'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { PasswordChangeFields } from '@/components/forms/PasswordChangeFields'
import { usePasswordChange } from '@/lib/password-change'
import { useAuth } from './auth'
import { findNav, ROLE_LABEL } from './nav'

function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  return (parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : name.slice(0, 2)).toUpperCase()
}

/** Dritte Brotkrume: Routen setzen handle: { crumb: string | (params) => ReactNode }, z. B. Klassen › 7a.
 *  Eine Komponente als Ergebnis darf Daten laden (ClassCrumb zeigt den Klassennamen statt der ID). */
function useCrumb(): ReactNode {
  const matches = useMatches()
  for (let i = matches.length - 1; i >= 0; i -= 1) {
    const h = matches[i].handle as { crumb?: string | ((params: Record<string, string | undefined>) => ReactNode) } | undefined
    if (h?.crumb) return typeof h.crumb === 'function' ? h.crumb(matches[i].params) : h.crumb
  }
  return null
}

function PasswordDialog({ open, onOpenChange }: { open: boolean; onOpenChange(o: boolean): void }) {
  const pw = usePasswordChange(() => onOpenChange(false))
  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Passwort ändern" onSubmit={pw.save} pending={pw.pending}>
      <PasswordChangeFields state={pw} idPrefix="pw-menu" />
    </FormDialog>
  )
}

export function Topbar({ onOpenPalette }: { onOpenPalette(): void }) {
  const { user, role, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const nav = findNav(location.pathname, role)
  const crumb = useCrumb()
  const [pwOpen, setPwOpen] = useState(false)
  const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

  return (
    <header className="print-hide sticky top-0 z-20 flex h-[52px] items-center gap-3 border-b bg-surface/85 px-4 backdrop-blur">
      <SidebarTrigger aria-label="Seitenleiste umschalten" />
      <Breadcrumb className="min-w-0">
        <BreadcrumbList className="flex-nowrap">
          {nav?.group && (
            <>
              <BreadcrumbItem className="hidden sm:inline-flex"><span className="text-foreground-subtle">{nav.group}</span></BreadcrumbItem>
              <BreadcrumbSeparator className="hidden sm:inline-flex" />
            </>
          )}
          <BreadcrumbItem>
            {crumb ? (
              <BreadcrumbLink asChild><Link to={nav?.to ?? '/'}>{nav?.label ?? 'Stundenplanung'}</Link></BreadcrumbLink>
            ) : (
              <BreadcrumbPage>{nav?.label ?? 'Stundenplanung'}</BreadcrumbPage>
            )}
          </BreadcrumbItem>
          {crumb && (
            <>
              <BreadcrumbSeparator />
              <BreadcrumbItem><BreadcrumbPage>{crumb}</BreadcrumbPage></BreadcrumbItem>
            </>
          )}
        </BreadcrumbList>
      </Breadcrumb>

      <div className="ml-auto hidden md:block">
        <div className="relative w-[280px]">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-foreground-subtle" aria-hidden />
          <Input
            readOnly
            value=""
            placeholder="Suchen…"
            aria-label="Suche öffnen"
            onClick={onOpenPalette}
            onFocus={(e) => {
              e.currentTarget.blur()
              onOpenPalette()
            }}
            className="h-8 cursor-pointer bg-surface-sunken/60 pr-12 pl-8 shadow-none hover:bg-accent"
          />
          <kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded-sm border bg-surface px-1.5 font-sans text-2xs text-foreground-subtle">
            {isMac ? '⌘K' : 'Strg K'}
          </kbd>
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" className="ml-auto md:hidden" onClick={onOpenPalette} aria-label="Suchen"><Search /></Button>

      <ThemeToggle />

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label="Benutzermenü">
            <Avatar size="sm">
              <AvatarFallback className="bg-primary-soft text-2xs font-semibold text-primary-soft-foreground">{initials(user?.displayName ?? '?')}</AvatarFallback>
            </Avatar>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="min-w-52">
          <DropdownMenuLabel>
            <div className="text-sm font-medium">{user?.displayName}</div>
            <div className="text-xs font-normal text-foreground-subtle">{role ? ROLE_LABEL[role] : ''}</div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          {/* Ein Klassenkonto ändert sein Passwort nicht selbst: das Passwort gehört der ganzen Klasse und
              wird von der Planung gesetzt (PUT /me/password ist für STUDENT gesperrt, E-50). */}
          {role !== 'STUDENT' && <DropdownMenuItem onSelect={() => setPwOpen(true)}><KeyRound /> Passwort ändern</DropdownMenuItem>}
          <DropdownMenuItem onSelect={() => { void logout().then(() => navigate('/login')) }}><LogOut /> Abmelden</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <PasswordDialog open={pwOpen} onOpenChange={setPwOpen} />
    </header>
  )
}
