// Eine Shell für alle Seiten: Sidebar + Topbar + Entwurfs-Hinweis + animierter Seiteninhalt + Command-Palette (⌘K)
// + Rundgang (#139, nur Planung und nur bis zum ersten „Fertig"/„Überspringen").
import { useEffect, useState, type CSSProperties } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ArrowRight, PencilRuler } from 'lucide-react'
import { useDrafts } from '@/api/hooks'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { AnimatedOutlet } from './AnimatedOutlet'
import { AppSidebar } from './AppSidebar'
import { CommandPalette } from './CommandPalette'
import { Topbar } from './Topbar'
import { TourOverlay } from './TourOverlay'
import { useAuth } from './auth'

/**
 * Schmale Leiste über dem Inhalt, solange es Entwürfe gibt (auf /entwurf zeigt W3 die Liste bzw. den
 * vollen DraftBanner). Seit #103 stehen mehrere nebeneinander: die Leiste nennt ihre Zahl und führt bei
 * genau einem direkt dorthin, sonst in die Liste. Entwürfe in der Ablage (#116) zählen nicht mit —
 * sie sind absichtlich beiseitegelegt und sollen nicht mahnen.
 */
function DraftStrip() {
  const { role } = useAuth()
  const location = useLocation()
  const drafts = useDrafts({ enabled: role === 'PLANNING' })
  const offen = (drafts.data ?? []).filter((d) => !d.finished)
  if (offen.length === 0 || location.pathname.startsWith('/entwurf')) return null
  const solving = offen.filter((d) => d.status === 'SOLVING').length
  const einer = offen.length === 1 ? offen[0] : null
  const detail = solving > 0 ? (solving === 1 ? ', eine Berechnung läuft' : `, ${solving} Berechnungen laufen`) : ''
  return (
    <div className="print-hide flex items-center gap-2 border-b border-draft-border bg-draft-bg px-6 py-1.5 text-xs text-draft-fg">
      <PencilRuler className="size-3.5" aria-hidden />
      <span className="font-semibold tracking-wide">ENTWURF</span>
      <span>{einer ? `„${einer.name}" — nicht aktiv` : `${offen.length} Entwürfe — keiner ist aktiv`}{detail}</span>
      <Link to={einer ? `/entwurf/${einer.id}` : '/entwurf'} className="ml-auto inline-flex items-center gap-1 font-medium underline-offset-2 hover:underline">
        {einer ? 'Zum Entwurf' : 'Zu den Entwürfen'} <ArrowRight className="size-3" />
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
