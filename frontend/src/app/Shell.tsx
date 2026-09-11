// Eine Shell für alle Seiten: Sidebar + Topbar + Entwurfs-Hinweis + animierter Seiteninhalt + Command-Palette (⌘K)
// + Rundgang (#139, nur Planung und nur bis zum ersten „Fertig"/„Überspringen").
import { useEffect, useState, type CSSProperties } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ArrowRight, PencilRuler } from 'lucide-react'
import { useDraft } from '@/api/hooks'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { AnimatedOutlet } from './AnimatedOutlet'
import { AppSidebar } from './AppSidebar'
import { CommandPalette } from './CommandPalette'
import { Topbar } from './Topbar'
import { TourOverlay } from './TourOverlay'
import { useAuth } from './auth'
import { openCount } from '@/lib/draft'

/** Schmale Leiste über dem Inhalt, solange ein Entwurf existiert (auf /entwurf zeigt W3 den vollen DraftBanner) */
function DraftStrip() {
  const { role } = useAuth()
  const location = useLocation()
  const draft = useDraft({ enabled: role === 'PLANNING' })
  if (!draft.data || location.pathname.startsWith('/entwurf')) return null
  // Dieselbe Zahl wie im Entwurfsbanner und auf der Übersicht (lib/draft.ts, Befund FRONTEND-9).
  const open = openCount(draft.data.openLessons)
  const detail = draft.data.status === 'SOLVING' ? ', Berechnung läuft' : open > 0 ? `, ${open} offene Stunden` : ''
  return (
    <div className="print-hide flex items-center gap-2 border-b border-draft-border bg-draft-bg px-6 py-1.5 text-xs text-draft-fg">
      <PencilRuler className="size-3.5" aria-hidden />
      <span className="font-semibold tracking-wide">ENTWURF</span>
      <span>vorhanden — nicht aktiv{detail}</span>
      <Link to="/entwurf" className="ml-auto inline-flex items-center gap-1 font-medium underline-offset-2 hover:underline">
        Zum Entwurf <ArrowRight className="size-3" />
      </Link>
    </div>
  )
}

export function Shell() {
  const [paletteOpen, setPaletteOpen] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((o) => !o)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <SidebarProvider style={{ '--sidebar-width': '15rem' } as CSSProperties}>
      <AppSidebar />
      <SidebarInset className="min-w-0">
        <Topbar onOpenPalette={() => setPaletteOpen(true)} />
        <DraftStrip />
        <div className="mx-auto flex w-full max-w-[1440px] flex-1 flex-col px-6 py-5">
          <AnimatedOutlet />
        </div>
      </SidebarInset>
      <CommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
      <TourOverlay />
    </SidebarProvider>
  )
}
