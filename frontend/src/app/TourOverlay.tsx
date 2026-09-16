// Rundgang durch die Webanwendung (#139, T2-3.1, E-55): eine gefuehrte Runde durch den ganzen Weg — Grunddaten
// anlegen, Plan rechnen lassen, Konflikt aufloesen, annehmen, Vertretung und Ereignisse. Je Schritt wird das
// gemeinte Bedienelement ausgeschnitten und umrandet, der Rest der Seite liegt unter vier Sperrflaechen:
// anklicken laesst sich nur das Ziel und der Rundgang selbst. Der Schritt gilt als getan, wenn die Seite offen
// ist oder das Ziel angeklickt wurde (`weiter` in tour-steps.ts); dann faellt die Sperre und das Erklaerfenster
// sagt, was gerade passiert ist. Erst „Weiter" fuehrt zum naechsten Schritt.
//
// Warum vier Rechtecke und keine SVG-Maske: ein durchsichtiges Loch in einer Maske laesst trotzdem keine Klicks
// durch — Sperre und Darstellung waeren zwei verschiedene Dinge. Vier Flaechen sperren genau das, was sie
// verdecken, und die Luecke dazwischen ist das echte Bedienelement.
//
// Niemand darf im Rundgang festsitzen. Deshalb drei Auswege, die ohne Server auskommen: Escape beendet ihn
// jederzeit, „Ueberspringen" schliesst ihn sofort im Browser (die Notiz am Konto laeuft hinterher; scheitert
// sie, meldet das der Toast der Mutation), und faellt das Ziel weg, faellt auch die Sperre.
//
// Ob er ueberhaupt erscheint, entscheidet der Server: `GET /api/me` liefert `tourCompletedAt`; null (oder
// fehlend — der Server laesst leere Felder weg, wie bei teacherId) heisst „noch nie gesehen". „Ueberspringen"
// und „Fertig" setzen das Feld am Konto (PUT /api/me/tour), „Rundgang erneut zeigen" in den Einstellungen
// loescht es wieder. Nur die Planung bekommt ihn — Lehrkraft und Klassenkonto haben genau eine Seite.
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { useLocation } from 'react-router-dom'
import { Check, Compass, MousePointerClick } from 'lucide-react'
import { useMe, useSetTour } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { useAuth } from './auth'
import { TOUR_STEPS, type TourStep } from './tour-steps'

/** Der Ausschnitt ist genau die Flaeche des Bedienelements — dieselbe, die beim Darueberfahren grau wird. */
const LUFT = 0
/** Breite des Erklaerfensters (22rem) — fuer die Begrenzung auf den sichtbaren Bereich. */
const PANEL_BREITE = 352
const RAND = 12
/** Darunter passt das Fenster weder ueber noch unter das Ziel; dann steht es unten rechts. */
const PANEL_PLATZ = 280
/** Rueckfall, falls sich die Rundung des Bedienelements nicht auslesen laesst. */
const LOCH_RUNDUNG = 8

interface Rect { top: number; left: number; width: number; height: number }

/**
 * Das Ziel eines Schritts im DOM finden: entweder ueber einen Selektor (Seitenleiste, ueber ihr `href`) oder
 * ueber den Anfang der Beschriftung (Knoepfe in den Seiten — sie tragen keine eigene Kennzeichnung, und eine
 * einzufuehren hiesse, fremde Dateien anzufassen).
 */
function zielFinden(t: TourStep['target']): HTMLElement | null {
  if (t.css) return document.querySelector<HTMLElement>(t.css)
  const gesucht = (t.text ?? '').toLowerCase()
  const kandidaten = Array.from(document.querySelectorAll<HTMLElement>('button, a'))
  return kandidaten.find((el) => (el.textContent ?? '').trim().toLowerCase().startsWith(gesucht)) ?? null
}

/**
 * Misst das Ziel, solange der Schritt laeuft. Gemessen wird in jedem Bild statt ueber einen ResizeObserver:
 * der meldet Groessenaenderungen, aber keine Verschiebung — und verschieben tut sich hier fast alles
 * (Seitenleiste ein- und ausklappen, Entwurfsleiste taucht auf, Seite scrollt).
 */
function useZielRect(target: TourStep['target'], aktiv: boolean): Rect | null {
  const [rect, setRect] = useState<Rect | null>(null)
  const letzte = useRef('')
  // Das Ziel-Objekt ist bei jedem Render neu; als Abhaengigkeit taugt nur sein Inhalt.
  const schluesselDesZiels = target.css ?? `text:${target.text}`
  const zielRef = useRef(target)
  zielRef.current = target

  useEffect(() => {
    // Den Merker mit zuruecksetzen: sonst gilt beim naechsten Anlauf dieselbe Messung als „unveraendert",
    // und der Ausschnitt bliebe aus, obwohl das Ziel wieder da ist (Zurueck, dann wieder Weiter).
    letzte.current = ''
    if (!aktiv) { setRect(null); return }
    let frame = 0
    const messen = () => {
      const el = zielFinden(zielRef.current)
      const r = el?.getBoundingClientRect()
      // Breite 0 heisst: nicht da oder nicht sichtbar (auch jsdom misst so) — dann kein Ausschnitt.
      const neu: Rect | null = r && r.width > 0 && r.height > 0
        ? { top: r.top, left: r.left, width: r.width, height: r.height }
        : null
      const schluessel = neu ? `${neu.top}|${neu.left}|${neu.width}|${neu.height}` : ''
      if (schluessel !== letzte.current) { letzte.current = schluessel; setRect(neu) }
      frame = requestAnimationFrame(messen)
    }
    frame = requestAnimationFrame(messen)
    return () => cancelAnimationFrame(frame)
  }, [schluesselDesZiels, aktiv])

  return rect
}

export function TourOverlay() {
  const { role } = useAuth()
  const planning = role === 'PLANNING'
  const me = useMe({ enabled: planning })
  const setTour = useSetTour()
  const location = useLocation()

  const [step, setStep] = useState(0)
  // „anleitung" = Ziel ist frei, alles andere gesperrt. „ergebnis" = Klick ist passiert, Seite wieder ganz bedienbar.
  const [phase, setPhase] = useState<'anleitung' | 'ergebnis'>('anleitung')
  // Sofort zu im Browser; die Notiz am Konto laeuft hinterher (siehe Kopfkommentar).
  const [geschlossen, setGeschlossen] = useState(false)
  const panel = useRef<HTMLDivElement>(null)

  const current = TOUR_STEPS[step]
  // Steht der Schritt auf einer anderen Seite (etwa „Berechnen" auf dem Entwurf), fuehrt der Rundgang zuerst
  // dorthin: Ziel ist dann der Eintrag der Seitenleiste, nicht der Knopf, den es hier gar nicht gibt.
  const hinfuehren = !!current.route && location.pathname !== current.route && current.weiter !== 'route'
  // Gemerkt, weil `ziel` sonst bei jedem Render ein neues Objekt waere — und damit jeder Effekt daran
  // (Scrollen, Tastatur) bei jedem Render neu liefe.
  const ziel = useMemo(
    () => (hinfuehren ? ({ css: `a[href="${current.route}"]` } as const) : current.target),
    [hinfuehren, current.route, current.target],
  )
  const aufforderung = hinfuehren ? `Öffnen Sie zuerst die Seite, auf der das passiert.` : current.action
  // != null statt !== null: der Server laesst leere Felder weg (siehe Kopfkommentar).
  const sichtbar = planning && !!me.data && me.data.tourCompletedAt == null && !geschlossen
  const rect = useZielRect(ziel, sichtbar && phase === 'anleitung')

  const beenden = useCallback(() => {
    setGeschlossen(true)
    // Nach dem Beenden wieder auf Schritt 1: „Rundgang erneut zeigen" soll vorne anfangen.
    setStep(0)
    setPhase('anleitung')
    setTour.mutate({ completed: true })
  }, [setTour])

  /** Schritt und Phase zusammen setzen, sonst zeigt ein Bild lang das Ergebnis des alten Schritts. */
  const geheZu = useCallback((n: number) => {
    const ziel = TOUR_STEPS[n]
    setStep(n)
    // Ein Navigationsschritt ist erledigt, wenn die Seite schon offen ist. Klick- und Leseschritte fangen an.
    setPhase(ziel.weiter === 'route' && location.pathname === ziel.route ? 'ergebnis' : 'anleitung')
  }, [location.pathname])

  // Navigationsschritt: Der Klick auf das Ziel fuehrt zur Seite des Schritts — das ist die Bestaetigung.
  // Gilt auch beim Start: wer schon dort steht, hat den Schritt hinter sich.
  useEffect(() => {
    if (phase === 'anleitung' && current.weiter === 'route' && location.pathname === current.route) setPhase('ergebnis')
  }, [location.pathname, phase, current.weiter, current.route])

  // Klickschritt: am Ziel selbst horchen. Der Klick zaehlt, egal was er ausloest — Dialog, Berechnung, Zweitfenster.
  useEffect(() => {
    if (!sichtbar || phase !== 'anleitung' || current.weiter !== 'klick' || hinfuehren) return
    const el = zielFinden(current.target)
    if (!el) return
    const onClick = () => setPhase('ergebnis')
    el.addEventListener('click', onClick)
    return () => el.removeEventListener('click', onClick)
    // `rect` haengt mit drin, damit der Horcher nachgezogen wird, sobald das Ziel auftaucht.
  }, [sichtbar, phase, current.weiter, current.target, hinfuehren, rect])

  // Das Ziel selbst faerben: violett und pulsierend, genau auf seiner eigenen Flaeche. Die Klasse sitzt am
  // echten Bedienelement und verschwindet mit dem Schritt — nichts bleibt zurueck, auch nicht beim Abbruch.
  const [rundung, setRundung] = useState<string | null>(null)
  useEffect(() => {
    if (!sichtbar || phase !== 'anleitung') { setRundung(null); return }
    const el = zielFinden(ziel)
    if (!el) { setRundung(null); return }
    el.classList.add('tour-ziel-aktiv')
    setRundung(getComputedStyle(el).borderRadius || null)
    return () => el.classList.remove('tour-ziel-aktiv')
    // `rect` haengt mit drin, damit die Klasse nachgezogen wird, sobald das Ziel auftaucht.
  }, [sichtbar, phase, ziel, rect])

  // Ziel ins Bild holen, sonst zeigt der Rundgang auf etwas, das ausserhalb des Fensters liegt.
  useEffect(() => {
    if (!sichtbar || phase !== 'anleitung') return
    zielFinden(ziel)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [sichtbar, phase, ziel])

  // Escape beendet immer, solange der Rundgang steht — auch im Ergebnis und auch dann, wenn ein fremder
  // Dialog die Maus abfaengt. Das ist der Ausweg, der nie versperrt ist.
  useEffect(() => {
    if (!sichtbar) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      // Steht ein anderer Dialog offen (etwa „Neue Lehrkraft"), gehoert Escape ihm — sonst schoesse der
      // Rundgang sich ab, waehrend der Nutzer nur das Formular zumachen wollte.
      if (document.querySelector('[role="dialog"]:not([aria-label="Rundgang"]), [role="alertdialog"]')) return
      e.preventDefault(); e.stopPropagation(); beenden()
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [sichtbar, beenden])

  // Waehrend der Sperre kreist Tab zwischen Ziel und den Knoepfen des Rundgangs, die Befehlspalette (⌘K) ruht.
  // Ohne den Kreis liefe Tab vom Ziel aus in die gesperrte Seite weiter — sichtbar fokussiert und mit Enter
  // sogar ausloesbar, obwohl die Maus dort nichts ausrichtet.
  useEffect(() => {
    if (!sichtbar || phase !== 'anleitung') return
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.stopPropagation(); e.preventDefault(); return }
      if (e.key !== 'Tab') return
      const zielEl = zielFinden(ziel)
      const knoepfe = Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not([disabled])') ?? [])
      const kette = [zielEl, ...knoepfe].filter(Boolean) as HTMLElement[]
      if (kette.length === 0) return
      e.preventDefault()
      const jetzt = kette.findIndex((el) => el === document.activeElement || el.contains(document.activeElement))
      const richtung = e.shiftKey ? -1 : 1
      kette[jetzt < 0 ? 0 : (jetzt + richtung + kette.length) % kette.length].focus()
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [sichtbar, phase, ziel])

  if (!sichtbar) return null

  const letzter = step === TOUR_STEPS.length - 1
  const sperren = phase === 'anleitung' && rect !== null
  const loch = rect && {
    top: Math.max(0, rect.top - LUFT), left: Math.max(0, rect.left - LUFT),
    width: rect.width + 2 * LUFT, height: rect.height + 2 * LUFT,
  }

  // Erklaerfenster unter das Ziel, sonst darueber, sonst unten rechts. Immer im sichtbaren Bereich: bei einem
  // schmalen Fenster ergaebe `innerWidth - Breite` sonst einen negativen Rand, und ohne Hoehenbegrenzung
  // ragte das Fenster oben heraus.
  const platzUnten = loch ? window.innerHeight - (loch.top + loch.height) : 0
  const platzOben = loch ? loch.top : 0
  const links = loch ? Math.max(RAND, Math.min(loch.left, window.innerWidth - PANEL_BREITE - RAND)) : 0
  const panelStil: CSSProperties = loch && sperren && Math.max(platzUnten, platzOben) > PANEL_PLATZ
    ? platzUnten > PANEL_PLATZ
      ? { top: loch.top + loch.height + RAND, left: links, maxHeight: platzUnten - 2 * RAND, overflowY: 'auto' }
      : { top: RAND, left: links, maxHeight: platzOben - 2 * RAND, overflowY: 'auto' }
    : { right: RAND, bottom: RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' }

  return createPortal(
    <>
      {sperren && loch && (
        // Vier Sperrflaechen um das Loch schlucken jeden Klick; das Ziel bleibt frei. Sie sind durchsichtig —
        // verdunkelt wird mit einem einzigen Schatten (unten), weil der sich runden laesst und vier Rechtecke
        // nicht: an den Ecken bliebe sonst je ein Zipfel stehen.
        // Stehen im Portal vor dem Erklaerfenster und liegen deshalb bei gleichem z-index darunter.
        <div aria-hidden className="print-hide fixed inset-0 z-50" style={{ pointerEvents: 'none' }}>
          <div className="absolute" style={{ pointerEvents: 'auto', top: 0, left: 0, right: 0, height: loch.top }} />
          <div className="absolute" style={{ pointerEvents: 'auto', top: loch.top + loch.height, left: 0, right: 0, bottom: 0 }} />
          <div className="absolute" style={{ pointerEvents: 'auto', top: loch.top, left: 0, width: loch.left, height: loch.height }} />
          <div className="absolute" style={{ pointerEvents: 'auto', top: loch.top, left: loch.left + loch.width, right: 0, height: loch.height }} />
          {/* Die Verdunkelung: ein riesiger Schatten nach aussen, innen das abgerundete Loch. Faengt selbst
              nichts ab (ein Schatten ist nicht anklickbar) — dafuer sind die vier Flaechen da. */}
          <div
            className="absolute"
            style={{
              top: loch.top, left: loch.left, width: loch.width, height: loch.height,
              borderRadius: rundung ?? LOCH_RUNDUNG, boxShadow: '0 0 0 9999px color-mix(in oklab, var(--foreground) 55%, transparent)',
            }}
          />
        </div>
      )}

      <div
        ref={panel}
        role="dialog"
        aria-label="Rundgang"
        // z-[100] statt z-50: Ein Dialog der Anwendung (Radix) liegt selbst auf z-50 samt eigener Verdunkelung —
        // darunter waere die Anleitung grau und unlesbar, genau wenn sie gebraucht wird. `pointerEvents: auto`
        // aus demselben Grund: ein modaler Dialog setzt `pointer-events: none` auf den Body, sonst waeren die
        // Knoepfe des Rundgangs tot, solange das Formular offen ist.
        className="print-hide fixed z-[100] w-[22rem] max-w-[calc(100%-2rem)] rounded-xl bg-popover p-4 text-sm text-popover-foreground shadow-lg ring-1 ring-foreground/10"
        style={{ ...panelStil, pointerEvents: 'auto' }}
      >
        <div className="flex items-center gap-2 text-xs text-foreground-subtle">
          <Compass className="size-3.5" aria-hidden />
          <span className="font-semibold tracking-wide uppercase">Rundgang</span>
          <span className="ml-auto tabular-nums">Schritt {step + 1} von {TOUR_STEPS.length}</span>
        </div>
        <Progress value={((step + 1) / TOUR_STEPS.length) * 100} className="mt-2" aria-label={`Schritt ${step + 1} von ${TOUR_STEPS.length}`} />

        <h2 className="mt-3 font-heading text-base font-medium">{current.title}</h2>
        <p className="mt-1 text-muted-foreground">{current.text}</p>

        {phase === 'anleitung' ? (
          <p className="mt-3 flex items-start gap-2 rounded-lg bg-primary/10 p-2 font-medium text-primary">
            <MousePointerClick className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>
              {aufforderung}
              {rect === null && <span className="mt-1 block font-normal text-muted-foreground">Das Element ist gerade nicht da — es erscheint erst, wenn die Seite es anbietet (etwa „Berechnen" nur bei vorhandenem Entwurf). Mit „Weiter" geht es zum nächsten Schritt.</span>}
            </span>
          </p>
        ) : (
          <p className="mt-3 flex items-start gap-2 rounded-lg bg-success-soft p-2 text-foreground">
            <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />
            <span><span className="font-medium">Das ist passiert: </span>{current.result}</span>
          </p>
        )}

        <div className="mt-4 flex items-center justify-between gap-2">
          <Button variant="ghost" size="sm" onClick={beenden}>Rundgang beenden</Button>
          <div className="flex gap-2">
            {step > 0 && <Button variant="outline" size="sm" onClick={() => geheZu(step - 1)}>Zurück</Button>}
            {/* Am letzten Schritt immer „Fertig": sonst haengt fest, wer das Ziel nicht anklicken kann.
                Solange die Aufgabe offen ist, tritt der Knopf zurueck — sonst klickt jeder ihn statt des Ziels. */}
            {letzter
              ? <Button size="sm" onClick={beenden}>Fertig</Button>
              : phase === 'anleitung'
                ? <Button variant="ghost" size="sm" className="text-foreground-subtle" onClick={() => geheZu(step + 1)}>Schritt überspringen</Button>
                : <Button size="sm" onClick={() => geheZu(step + 1)}>Weiter</Button>}
          </div>
        </div>
      </div>
    </>,
    document.body,
  )
}
