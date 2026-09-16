// Rundgang durch die Webanwendung (#139, T2-3.1, E-55): eine gefuehrte Runde durch den ganzen Weg — Grunddaten
// anlegen, Plan rechnen lassen, Konflikt aufloesen, annehmen, Vertretung und Ereignisse.
//
// Je Schritt wird genau das gemeinte Bedienelement violett hervorgehoben, ein Pfeil wippt darauf zu, und der
// Rest der Seite liegt unter einer dunklen Flaeche mit rundem Ausschnitt. Anklicken laesst sich nur das Ziel.
// Einen „Weiter"-Knopf gibt es nicht: Sobald die Aufgabe getan ist — Seite offen oder Ziel geklickt — geht es
// von allein weiter. Wo etwas einzutragen ist, schreibt der Rundgang die Werte selbst ins Formular und zeigt
// danach auf „Speichern"; geklickt wird immer von Hand, damit niemand ueberrascht Daten anlegt.
//
// Niemand darf festsitzen. Drei Auswege, die ohne Server auskommen: Escape beendet jederzeit (ausser ein
// Dialog der Anwendung ist offen — dann gehoert Escape ihm), „Beenden" schliesst sofort im Browser (die Notiz
// am Konto laeuft hinterher), und faellt das Ziel weg, faellt auch die Sperre.
//
// Ob er ueberhaupt erscheint, entscheidet der Server: `GET /api/me` liefert `tourCompletedAt`; null (oder
// fehlend — der Server laesst leere Felder weg, wie bei teacherId) heisst „noch nie gesehen". „Beenden" und
// „Fertig" setzen das Feld am Konto (PUT /api/me/tour), „Rundgang erneut zeigen" in den Einstellungen loescht
// es wieder. Nur die Planung bekommt ihn — Lehrkraft und Klassenkonto haben genau eine Seite.
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { useLocation } from 'react-router-dom'
import { Check, Compass, MousePointerClick, PenLine } from 'lucide-react'
import { useMe, useSetTour } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { useAuth } from './auth'
import { TOUR_STEPS, type TourStep } from './tour-steps'

/** Breite des Erklaerfensters (22rem) — fuer die Begrenzung auf den sichtbaren Bereich. */
const PANEL_BREITE = 352
const RAND = 12
/** Darunter passt das Fenster weder ueber noch unter das Ziel; dann steht es unten rechts. */
const PANEL_PLATZ = 280
/** Rueckfall, falls sich die Rundung des Bedienelements nicht auslesen laesst. */
const LOCH_RUNDUNG = 8
/** Ruhe nach einem Klick, bevor der naechste Schritt anfaengt (Seite/Dialog kommen zur Ruhe). */
const NACHLAUF = 550
/**
 * Lesezeit, nachdem eine Seite aufgegangen ist. Ohne sie waere ein Navigationsschritt in dem Moment erledigt,
 * in dem die Seite erscheint — man saehe die Erklaerung nur, solange man noch woanders ist, und auf der Seite
 * selbst spraenge der Rundgang sofort weiter. Genau das wirkte wie ein Fehler.
 */
const LESEZEIT = 4500
/** Anschlag je Zeichen der Schreibanimation. */
const ANSCHLAG = 55
/** Ab so viel Platz rechts neben dem Ziel steht der Pfeil daneben statt darunter. */
const PFEIL_PLATZ = 90

interface Rect { top: number; left: number; width: number; height: number }

/**
 * Das Ziel eines Schritts im DOM finden: entweder ueber einen Selektor (Seitenleiste ueber ihr `href`,
 * Formularfelder ueber ihre id) oder ueber den Anfang der Beschriftung (Knoepfe in den Seiten — sie tragen
 * keine eigene Kennzeichnung, und eine einzufuehren hiesse, fremde Dateien anzufassen).
 */
function zielFinden(t: TourStep['target'], imDialog = false): HTMLElement | null {
  // Im Dialog suchen, wenn der Schritt es verlangt: „Berechnen" und „Annehmen" stehen auch auf der Seite
  // dahinter, gemeint ist aber der Knopf im Dialog.
  const bereich: ParentNode | null = imDialog
    ? document.querySelector('[role="dialog"]:not([aria-label="Rundgang"]), [role="alertdialog"]')
    : document
  if (!bereich) return null
  if (t.css) return bereich.querySelector<HTMLElement>(t.css)
  const beschriftungen = (Array.isArray(t.text) ? t.text : [t.text ?? '']).map((x) => x.toLowerCase())
  const kandidaten = Array.from(bereich.querySelectorAll<HTMLElement>('button, a'))
  for (const gesucht of beschriftungen) {
    const treffer = kandidaten.find((el) => (el.textContent ?? '').trim().toLowerCase().startsWith(gesucht))
    if (treffer) return treffer
  }
  return null
}

/**
 * Fuellt das ganze Fenster und stanzt das Loch aus: aeusseres Rechteck im Uhrzeigersinn, das abgerundete Loch
 * gegen den Uhrzeigersinn — mit `evenodd` bleibt innen ein Loch statt einer Flaeche.
 */
function lochPfad(l: Rect, r: number): string {
  const { top: t, left: x, width: w, height: h } = l
  const b = t + h, re = x + w
  const rr = Math.max(0, Math.min(r, w / 2, h / 2))
  const innen = [
    `M${x + rr} ${t}`, `H${re - rr}`, `A${rr} ${rr} 0 0 1 ${re} ${t + rr}`,
    `V${b - rr}`, `A${rr} ${rr} 0 0 1 ${re - rr} ${b}`,
    `H${x + rr}`, `A${rr} ${rr} 0 0 1 ${x} ${b - rr}`,
    `V${t + rr}`, `A${rr} ${rr} 0 0 1 ${x + rr} ${t}`, 'Z',
  ].join(' ')
  return `path(evenodd, 'M0 0 H100000 V100000 H0 Z ${innen}')`
}

/**
 * Schreibt einen Wert in ein kontrolliertes React-Feld. `el.value = …` allein genuegt nicht: React merkt sich
 * den letzten Wert am Knoten und haelt die Zuweisung fuer keine Aenderung. Deshalb ueber den nativen Setter
 * des Prototyps, dann ein `input`-Ereignis — derselbe Weg, den auch Testwerkzeuge nehmen.
 */
function setzeWert(el: HTMLInputElement, wert: string) {
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set
  setter?.call(el, wert)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

/**
 * Misst das Ziel, solange der Rundgang steht. Gemessen wird in jedem Bild statt ueber einen ResizeObserver:
 * der meldet Groessenaenderungen, aber keine Verschiebung — und verschieben tut sich hier fast alles
 * (Seitenleiste ein- und ausklappen, Entwurfsleiste taucht auf, Seite scrollt).
 */
function useZielRect(target: TourStep['target'], aktiv: boolean, imDialog = false): Rect | null {
  const [rect, setRect] = useState<Rect | null>(null)
  const letzte = useRef('')
  // Das Ziel-Objekt ist bei jedem Render neu; als Abhaengigkeit taugt nur sein Inhalt.
  const schluesselDesZiels = `${imDialog ? 'dlg:' : ''}${target.css ?? `text:${[target.text].flat().join('|')}`}`
  const zielRef = useRef(target)
  zielRef.current = target

  useLayoutEffect(() => {
    // Den Merker mit zuruecksetzen: sonst gilt beim naechsten Anlauf dieselbe Messung als „unveraendert",
    // und der Ausschnitt bliebe aus, obwohl das Ziel wieder da ist.
    letzte.current = ''
    if (!aktiv) { setRect(null); return }
    let frame = 0
    const messen = () => {
      const el = zielFinden(zielRef.current, imDialog)
      const r = el?.getBoundingClientRect()
      // Breite 0 heisst: nicht da oder nicht sichtbar (auch jsdom misst so) — dann kein Ausschnitt.
      const neu: Rect | null = r && r.width > 0 && r.height > 0
        ? { top: r.top, left: r.left, width: r.width, height: r.height }
        : null
      const schluessel = neu ? `${neu.top}|${neu.left}|${neu.width}|${neu.height}` : ''
      if (schluessel !== letzte.current) { letzte.current = schluessel; setRect(neu) }
      frame = requestAnimationFrame(messen)
    }
    messen()   // sofort einmal messen, damit das Erklaerfenster gleich am Ziel steht
    return () => cancelAnimationFrame(frame)
  }, [schluesselDesZiels, aktiv, imDialog])

  return rect
}

export function TourOverlay() {
  const { role } = useAuth()
  const planning = role === 'PLANNING'
  const me = useMe({ enabled: planning })
  const setTour = useSetTour()
  const location = useLocation()

  const [step, setStep] = useState(0)
  // Sofort zu im Browser; die Notiz am Konto laeuft hinterher (siehe Kopfkommentar).
  const [geschlossen, setGeschlossen] = useState(false)
  // Kurz nach einem Schrittwechsel wird noch gesucht; erst danach darf „nicht da" stehen.
  const [suchtNoch, setSuchtNoch] = useState(true)
  // Schreibanimation: laeuft gerade / ist durch. Solange sie laeuft, zeigt der Rundgang auf das Feld.
  const [tippt, setTippt] = useState(false)
  const [getippt, setGetippt] = useState(false)
  const panel = useRef<HTMLDivElement>(null)

  const current = TOUR_STEPS[step]
  const letzter = step === TOUR_STEPS.length - 1
  // != null statt !== null: der Server laesst leere Felder weg (siehe Kopfkommentar).
  const sichtbar = planning && !!me.data && me.data.tourCompletedAt == null && !geschlossen

  // Steht der Schritt auf einer anderen Seite (etwa „Berechnen" auf dem Entwurf), fuehrt der Rundgang zuerst
  // dorthin: Ziel ist dann der Eintrag der Seitenleiste, nicht der Knopf, den es hier gar nicht gibt.
  const hinfuehren = !!current.route && location.pathname !== current.route && current.weiter !== 'route'
  // Waehrend des Schreibens zeigt der Rundgang auf das Feld, danach auf „Speichern".
  const feldZiel = current.tippen && !getippt ? current.tippen[0] : null
  const ziel = useMemo(
    () => (hinfuehren ? ({ css: `a[href="${current.route}"]` } as const)
      : feldZiel ? ({ css: feldZiel.css } as const)
        : current.target),
    [hinfuehren, current.route, current.target, feldZiel],
  )
  // Im Dialog nur suchen, solange der Schritt nicht ohnehin erst zur Seite fuehrt.
  const imDialog = !!current.imDialog && !hinfuehren
  const rect = useZielRect(ziel, sichtbar, imDialog)

  const beenden = useCallback(() => {
    setGeschlossen(true)
    // Nach dem Beenden wieder auf Schritt 1: „Rundgang erneut zeigen" soll vorne anfangen.
    setStep(0)
    setTour.mutate({ completed: true })
  }, [setTour])

  /** Zu Schritt n; die Schreibanimation faengt dort von vorn an. */
  const geheZu = useCallback((n: number) => {
    setStep(Math.max(0, Math.min(n, TOUR_STEPS.length - 1)))
    setTippt(false)
    setGetippt(false)
  }, [])

  /**
   * „Überspringen" laesst nicht den einzelnen Schritt aus, sondern das ganze Thema: den naechsten Schritt,
   * der auf einer anderen Seite spielt. Wer die Grunddaten kennt, will nicht drei Erklaerungen wegklicken.
   */
  const naechsteSeite = useCallback(() => {
    const jetzt = TOUR_STEPS[step].route
    const n = TOUR_STEPS.findIndex((s2, i) => i > step && s2.route !== jetzt)
    return n === -1 ? TOUR_STEPS.length - 1 : n
  }, [step])

  /** Aufgabe erledigt — nach kurzer Ruhe weiter. Der Schrittvergleich verhindert Spruenge ueber zwei Schritte. */
  const weiterNachRuhe = useCallback((meiner: number) => window.setTimeout(() => {
    setStep((s) => (s === meiner && s < TOUR_STEPS.length - 1 ? s + 1 : s))
    setTippt(false)
    setGetippt(false)
  }, NACHLAUF), [])

  useEffect(() => {
    setSuchtNoch(true)
    const t = window.setTimeout(() => setSuchtNoch(false), 1500)
    return () => window.clearTimeout(t)
  }, [step, location.pathname, getippt])

  // Navigationsschritt: Die Seite ist offen — jetzt steht die Erklaerung noch einen Moment auf der Seite
  // selbst, dann geht es weiter.
  const [liest, setLiest] = useState(false)
  useEffect(() => {
    if (!sichtbar || current.weiter !== 'route' || location.pathname !== current.route) { setLiest(false); return }
    setLiest(true)
    const meiner = step
    const t = window.setTimeout(() => {
      setLiest(false)
      setStep((s2) => (s2 === meiner && s2 < TOUR_STEPS.length - 1 ? s2 + 1 : s2))
    }, LESEZEIT)
    return () => { window.clearTimeout(t); setLiest(false) }
  }, [sichtbar, current.weiter, current.route, location.pathname, step])

  // Klickschritt: am Ziel selbst horchen. Der Klick zaehlt, egal was er ausloest — Dialog, Berechnung, Wechsel.
  useEffect(() => {
    if (!sichtbar || current.weiter !== 'klick' || hinfuehren || feldZiel) return
    const el = zielFinden(current.target, imDialog)
    if (!el) return
    const meiner = step
    const onClick = () => weiterNachRuhe(meiner)
    el.addEventListener('click', onClick)
    return () => el.removeEventListener('click', onClick)
    // `rect` haengt mit drin, damit der Horcher nachgezogen wird, sobald das Ziel auftaucht.
  }, [sichtbar, current.weiter, current.target, hinfuehren, feldZiel, imDialog, rect, step, weiterNachRuhe])

  // Gibt es das Ziel nicht (und es kommt auch nicht mehr), ist der Schritt gegenstandslos: „Leer beginnen"
  // fehlt, wenn schon ein Entwurf da ist, „Lösen" fehlt ohne Konflikt. Dann still weiter, statt auf einen
  // Knopf zu warten, den es nie geben wird.
  useEffect(() => {
    if (!sichtbar || current.weiter !== 'klick' || hinfuehren || tippt) return
    if (rect !== null || suchtNoch || letzter) return
    const meiner = step
    const t = window.setTimeout(() => setStep((s2) => (s2 === meiner ? s2 + 1 : s2)), 400)
    return () => window.clearTimeout(t)
  }, [sichtbar, current.weiter, hinfuehren, tippt, rect, suchtNoch, letzter, step])

  // Schreibanimation: Der Rundgang fuellt die Felder selbst — Zeichen fuer Zeichen, damit man es mitliest.
  // Bricht ab, sobald der Schritt wechselt oder der Rundgang endet; ein halb getipptes Feld bleibt stehen
  // und laesst sich von Hand weiterschreiben.
  useEffect(() => {
    if (!sichtbar || !current.tippen || getippt || hinfuehren) return
    const felder = current.tippen
    let abbruch = false
    const pause = (ms: number) => new Promise((r) => window.setTimeout(r, ms))
    void (async () => {
      await pause(400)   // der Dialog baut sich gerade erst auf
      if (abbruch || !document.querySelector(felder[0].css)) return
      setTippt(true)
      for (const feld of felder) {
        const el = document.querySelector<HTMLInputElement>(feld.css)
        if (!el) continue
        el.focus()
        for (let i = 1; i <= feld.wert.length; i++) {
          if (abbruch) return
          setzeWert(el, feld.wert.slice(0, i))
          await pause(ANSCHLAG)
        }
        await pause(200)
      }
      if (abbruch) return
      setTippt(false)
      setGetippt(true)
    })()
    return () => { abbruch = true }
  }, [sichtbar, step, current.tippen, getippt, hinfuehren])

  // Ziel ins Bild holen, sonst zeigt der Rundgang auf etwas, das ausserhalb des Fensters liegt.
  useEffect(() => {
    if (!sichtbar) return
    zielFinden(ziel, imDialog)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [sichtbar, ziel, imDialog])

  // Das Ziel selbst faerben: violett und pulsierend, genau auf seiner eigenen Flaeche. Die Klasse sitzt am
  // echten Bedienelement und verschwindet mit dem Schritt — nichts bleibt zurueck, auch nicht beim Abbruch.
  const [rundung, setRundung] = useState<string | null>(null)
  useEffect(() => {
    if (!sichtbar) { setRundung(null); return }
    const el = zielFinden(ziel, imDialog)
    if (!el) { setRundung(null); return }
    el.classList.add('tour-ziel-aktiv')
    setRundung(getComputedStyle(el).borderRadius || null)
    return () => el.classList.remove('tour-ziel-aktiv')
  }, [sichtbar, ziel, imDialog, rect])

  // Escape beendet immer, solange der Rundgang steht — auch dann, wenn ein fremder Dialog die Maus abfaengt.
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
  // Waehrend der Rundgang selbst schreibt, bleibt die Tastatur in Ruhe.
  useEffect(() => {
    if (!sichtbar || tippt) return
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.stopPropagation(); e.preventDefault(); return }
      if (e.key !== 'Tab') return
      const zielEl = zielFinden(ziel, imDialog)
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
  }, [sichtbar, ziel, imDialog, tippt])

  if (!sichtbar) return null

  const loch = rect
  const eckenRadius = Number.parseFloat(rundung ?? '') || LOCH_RUNDUNG
  // Waehrend der Rundgang selbst schreibt, darf die Seite nicht gesperrt sein — sonst faengt die Sperre die
  // Eingabe ab. Die Sperre kommt zurueck, sobald auf „Speichern" gezeigt wird.
  const sperren = loch !== null && !tippt
  const pfeilQuer = !!loch && window.innerWidth - (loch.left + loch.width) > PFEIL_PLATZ

  // Erklaerfenster unter das Ziel, sonst darueber, sonst unten rechts.
  const platzUnten = loch ? window.innerHeight - (loch.top + loch.height) : 0
  const platzOben = loch ? loch.top : 0
  const links = loch ? Math.max(RAND, Math.min(loch.left, window.innerWidth - PANEL_BREITE - RAND)) : 0
  const lage: 'unten' | 'oben' | 'ecke' = loch && Math.max(platzUnten, platzOben) > PANEL_PLATZ
    ? (platzUnten > PANEL_PLATZ ? 'unten' : 'oben')
    : 'ecke'
  const panelStil: CSSProperties = loch && lage === 'unten'
    ? { top: loch.top + loch.height + 28, left: links, maxHeight: platzUnten - 2 * RAND, overflowY: 'auto' }
    : loch && lage === 'oben'
      ? { bottom: window.innerHeight - loch.top + 28, left: links, maxHeight: platzOben - 2 * RAND, overflowY: 'auto' }
      : { right: RAND, bottom: RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' }
  const zipfelLinks = loch ? Math.max(14, Math.min(loch.left + loch.width / 2 - links - 6, PANEL_BREITE - 26)) : 0
  // Solange nach dem Ziel gesucht wird, bleibt das Fenster durchsichtig statt kurz in der Ecke aufzublitzen.
  const sucht = loch === null && suchtNoch

  return createPortal(
    <>
      {sperren && loch && (
        <div aria-hidden className="print-hide fixed inset-0 z-50" style={{ pointerEvents: 'none' }}>
          {/* Eine dunkle Flaeche ueber allem, aus der das Loch mit der Rundung des Bedienelements ausgestanzt
              ist (clip-path, Fuellregel evenodd). Vier Rechtecke liessen an den runden Ecken helle Zipfel. */}
          <div
            className="absolute inset-0 bg-[var(--tour-dunkel)]"
            style={{ pointerEvents: 'none', clipPath: lochPfad(loch, eckenRadius) }}
          />
          {/* Die vier Flaechen sperren nur, sie faerben nicht: durchsichtig, aber klickfest. */}
          <div className="absolute" style={{ pointerEvents: 'auto', top: 0, left: 0, right: 0, height: loch.top }} />
          <div className="absolute" style={{ pointerEvents: 'auto', top: loch.top + loch.height, left: 0, right: 0, bottom: 0 }} />
          <div className="absolute" style={{ pointerEvents: 'auto', top: loch.top, left: 0, width: loch.left, height: loch.height }} />
          <div className="absolute" style={{ pointerEvents: 'auto', top: loch.top, left: loch.left + loch.width, right: 0, height: loch.height }} />
        </div>
      )}

      {/* Der Pfeil wippt auf das Ziel zu: rechts daneben, wenn dort Platz ist, sonst darunter. */}
      {loch && !sucht && (
        <div
          aria-hidden
          className={`print-hide fixed z-[60] text-primary ${pfeilQuer ? 'tour-pfeil-quer' : 'tour-pfeil-hoch'}`}
          style={pfeilQuer
            ? { top: loch.top + loch.height / 2 - 17, left: loch.left + loch.width + 6 }
            : { top: loch.top + loch.height + 6, left: loch.left + loch.width / 2 - 17 }}
        >
          <svg width="34" height="34" viewBox="0 0 24 24" fill="currentColor" className="drop-shadow-[0_1px_2px_rgb(0_0_0/.35)]" aria-hidden>
            {pfeilQuer
              ? <path d="M15 4 L7 12 L15 20 Z" />
              : <path d="M12 6 L20 15 L4 15 Z" />}
          </svg>
        </div>
      )}

      <div
        ref={panel}
        role="dialog"
        aria-label="Rundgang"
        // z-[100] statt z-50: Ein Dialog der Anwendung (Radix) liegt selbst auf z-50 samt eigener Verdunkelung.
        // `pointerEvents: auto` aus demselben Grund: ein modaler Dialog setzt `pointer-events: none` auf den Body.
        className="print-hide fixed z-[100] w-[22rem] max-w-[calc(100%-2rem)] rounded-xl bg-popover p-4 text-sm text-popover-foreground shadow-lg ring-1 ring-foreground/10"
        style={{ ...panelStil, pointerEvents: 'auto', opacity: sucht ? 0 : 1 }}
      >
        {lage !== 'ecke' && (
          <span
            aria-hidden
            className="absolute size-3 rotate-45 bg-popover ring-1 ring-foreground/10"
            style={lage === 'unten'
              ? { top: -6, left: zipfelLinks, clipPath: 'polygon(0 0, 100% 0, 0 100%)' }
              : { bottom: -6, left: zipfelLinks, clipPath: 'polygon(100% 0, 100% 100%, 0 100%)' }}
          />
        )}
        <div className="flex items-center gap-2 text-xs text-foreground-subtle">
          <Compass className="size-3.5" aria-hidden />
          <span className="font-semibold tracking-wide uppercase">Rundgang</span>
          <span className="ml-auto tabular-nums">Schritt {step + 1} von {TOUR_STEPS.length}</span>
        </div>
        <Progress value={((step + 1) / TOUR_STEPS.length) * 100} className="mt-2" aria-label={`Schritt ${step + 1} von ${TOUR_STEPS.length}`} />

        <h2 className="mt-3 font-heading text-base font-medium">{current.title}</h2>
        <p className="mt-1 text-muted-foreground">{current.text}</p>

        <p className="mt-3 flex items-start gap-2 rounded-lg bg-primary/10 p-2 font-medium text-primary">
          {tippt
            ? <PenLine className="mt-0.5 size-4 shrink-0 animate-pulse" aria-hidden />
            : liest
              ? <Check className="mt-0.5 size-4 shrink-0" aria-hidden />
              : <MousePointerClick className="mt-0.5 size-4 shrink-0" aria-hidden />}
          <span>
            {tippt
              ? `Der Rundgang füllt das Formular aus — ${current.tippen?.map((f) => f.beschriftung).join(' und ')}.`
              : liest
                ? 'Sie sind da — schauen Sie sich um. Gleich geht es weiter.'
                : hinfuehren ? 'Öffnen Sie zuerst die Seite, auf der das passiert.' : current.action}
            {loch === null && !suchtNoch && <span className="mt-1 block font-normal text-muted-foreground">Das Element ist gerade nicht da — es erscheint erst, wenn die Seite es anbietet. Mit „Überspringen" geht es zum nächsten Thema.</span>}
          </span>
        </p>

        {liest && (
          // Zeigt, dass gleich weitergeschaltet wird — ein Sprung ohne Vorwarnung wirkt wie ein Fehler.
          <span aria-hidden className="mt-2 block h-0.5 overflow-hidden rounded-full bg-primary/15">
            <span className="tour-lesebalken block h-full bg-primary" style={{ animationDuration: `${LESEZEIT}ms` }} />
          </span>
        )}

        <div className="mt-4 flex items-center justify-between gap-2">
          <Button variant="ghost" size="sm" onClick={beenden}>Beenden</Button>
          <div className="flex gap-2">
            {step > 0 && <Button variant="outline" size="sm" onClick={() => geheZu(step - 1)}>Zurück</Button>}
            {letzter
              ? <Button size="sm" onClick={beenden}>Fertig</Button>
              : <Button variant="ghost" size="sm" className="text-foreground-subtle" onClick={() => geheZu(naechsteSeite())}>Überspringen</Button>}
          </div>
        </div>
      </div>
    </>,
    document.body,
  )
}
