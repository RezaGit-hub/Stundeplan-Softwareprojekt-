// ⌘K: Springe zu · Klassen · Lehrkräfte · Räume · Aktionen (SPEZIFIKATION 3.3). Listen aus den Query-Hooks.
import { useNavigate } from 'react-router-dom'
import { Calculator, CheckCheck, LogOut, PencilRuler, Plus, Replace, SunMoon } from 'lucide-react'
import { IS_MOCK } from '@/api/api'
import { useClasses, useDrafts, useRooms, useTeachers } from '@/api/hooks'
import { Command, CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator } from '@/components/ui/command'
import { Skeleton } from '@/components/ui/skeleton'
import { useTheme } from '@/components/theme/theme-provider'
import { today } from '@/lib/today'
import { useAuth } from './auth'
import { navFor } from './nav'

export function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange(open: boolean): void }) {
  const navigate = useNavigate()
  const { role, logout } = useAuth()
  const { resolved, setTheme } = useTheme()
  const planning = role === 'PLANNING'
  const classes = useClasses({ enabled: planning })
  const teachers = useTeachers({ enabled: planning })
  const rooms = useRooms({ enabled: planning })
  const drafts = useDrafts({ enabled: planning })
  // Seit #103 gibt es mehrere Entwürfe: die Aktionen meinen den jüngsten, der nicht in der Ablage liegt —
  // dieselbe Wahl wie die Singular-Pfade des Vertrags. Gibt es keinen, führen sie in die Liste.
  const zuletzt = (drafts.data ?? []).filter((d) => !d.finished)[0] ?? null
  const draftAction = (aktion: string) => (zuletzt ? `/entwurf/${zuletzt.id}?aktion=${aktion}` : '/entwurf')

  const go = (to: string) => {
    onOpenChange(false)
    navigate(to)
  }
  const toggleTheme = () => {
    onOpenChange(false)
    setTheme(resolved === 'dark' ? 'light' : 'dark')
  }
  const loading = <div className="px-2 py-1.5"><Skeleton className="h-4 w-40 bg-surface-sunken" /></div>
  // eine Definition fuer beide Rollen; „Darstellung“ ist derselbe Begriff wie im Umschalter oben rechts
  const themeItem = <CommandItem value="aktion darstellung wechseln hell dunkel" onSelect={toggleTheme}><SunMoon /> Darstellung wechseln</CommandItem>

  return (
    <CommandDialog open={open} onOpenChange={onOpenChange} title="Suche" description="Seite, Klasse, Lehrkraft, Raum oder Aktion">
      <Command loop>
        <CommandInput placeholder="Suchen…" autoFocus />
        <CommandList>
          <CommandEmpty>Nichts gefunden.</CommandEmpty>
          <CommandGroup heading="Springe zu">
            {navFor(role, IS_MOCK).map((n) => (
              <CommandItem key={n.to} value={`seite ${n.label}`} onSelect={() => go(n.to)}>
                <n.icon /> {n.label}
              </CommandItem>
            ))}
          </CommandGroup>
          {planning && (
            <>
              <CommandSeparator />
              <CommandGroup heading="Klassen">
                {classes.isPending ? loading : classes.data?.filter((c) => c.active).map((c) => (
                  <CommandItem key={c.id} value={`klasse ${c.name}`} onSelect={() => go(`/plan?sicht=CLASS&id=${c.id}`)}>{c.name}</CommandItem>
                ))}
              </CommandGroup>
              <CommandGroup heading="Lehrkräfte">
                {teachers.isPending ? loading : teachers.data?.filter((t) => t.active).map((t) => (
                  <CommandItem key={t.id} value={`lehrkraft ${t.name} ${t.shortName}`} onSelect={() => go(`/plan?sicht=TEACHER&id=${t.id}`)}>
                    {t.name} <span className="text-foreground-subtle">({t.shortName})</span>
                  </CommandItem>
                ))}
              </CommandGroup>
              <CommandGroup heading="Räume">
                {rooms.isPending ? loading : rooms.data?.filter((r) => r.active).map((r) => (
                  <CommandItem key={r.id} value={`raum ${r.name}`} onSelect={() => go(`/plan?sicht=ROOM&id=${r.id}`)}>{r.name}</CommandItem>
                ))}
              </CommandGroup>
              {(drafts.data?.length ?? 0) > 0 && (
                <CommandGroup heading="Entwürfe">
                  {drafts.data?.map((d) => (
                    <CommandItem key={d.id} value={`entwurf ${d.name}`} onSelect={() => go(`/entwurf/${d.id}`)}>
                      <PencilRuler /> {d.name}{d.finished && <span className="text-foreground-subtle">(Ablage)</span>}
                    </CommandItem>
                  ))}
                </CommandGroup>
              )}
              <CommandSeparator />
              <CommandGroup heading="Aktionen">
                <CommandItem value="aktion entwurf berechnen" onSelect={() => go(draftAction('berechnen'))}><Calculator /> Entwurf berechnen</CommandItem>
                <CommandItem value="aktion entwurf annehmen" onSelect={() => go(draftAction('annehmen'))}><CheckCheck /> Entwurf annehmen</CommandItem>
                <CommandItem value="aktion neuen entwurf anlegen" onSelect={() => go('/entwurf')}><Plus /> Neuen Entwurf anlegen</CommandItem>
                <CommandItem value="aktion neue vertretung" onSelect={() => go(`/vertretung?datum=${today()}`)}><Replace /> Neue Vertretung</CommandItem>
                {themeItem}
              </CommandGroup>
            </>
          )}
          {!planning && (
            <>
              <CommandSeparator />
              <CommandGroup heading="Aktionen">
                {themeItem}
                <CommandItem value="aktion abmelden" onSelect={() => { onOpenChange(false); void logout().then(() => navigate('/login')) }}><LogOut /> Abmelden</CommandItem>
              </CommandGroup>
            </>
          )}
        </CommandList>
      </Command>
    </CommandDialog>
  )
}
