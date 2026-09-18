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
import { Compass, MousePointerClick, PenLine } from 'lucide-react'
import { useMe, useSetTour } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { useAuth } from './auth'
import { TOUR_STEPS, type TourAktion, type TourStep } from './tour-steps'

/** Breite des Erklaerfensters (22rem) — fuer die Begrenzung auf den sichtbaren Bereich. */
const PANEL_BREITE = 352
const RAND = 12
/** Darunter passt das Fenster weder ueber noch unter das Ziel; dann steht es unten rechts. */
const PANEL_PLATZ = 280
/** Rueckfall, falls sich die Rundung des Bedienelements nicht auslesen laesst. */
const LOCH_RUNDUNG = 8
/** Ruhe nach einem Klick, bevor der naechste Schritt anfaengt (Seite/Dialog kommen zur Ruhe). */
const NACHLAUF = 550
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
  // Tabellenzeilen zaehlen mit: Die Klassenliste navigiert ueber einen Klick auf die Zeile, nicht ueber einen
  // Link. Ohne sie waere „Klasse 7c oeffnen" kein erreichbares Ziel.
  const kandidaten = Array.from(bereich.querySelectorAll<HTMLElement>('button, a, tbody tr'))
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
function setzeWert(el: HTMLInputElement | HTMLTextAreaElement, wert: string) {
  const proto = el instanceof HTMLTextAreaElement ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
  Object.getOwnPropertyDescriptor(proto, 'value')?.set?.call(el, wert)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

const pause = (ms: number) => new Promise((r) => window.setTimeout(r, ms))

/**
 * Fuehrt eine Vorbereitung aus: tippen (Zeichen fuer Zeichen), waehlen (Auswahlfeld oeffnen und Eintrag
 * nehmen) oder klicken. Gibt zurueck, ob sie geklappt hat — fehlt das Feld, geht der Rundgang trotzdem weiter.
 */
async function fuehreAus(a: TourAktion, abbruch: () => boolean): Promise<void> {
  if (a.art === 'tippen') {
    const el = document.querySelector<HTMLInputElement | HTMLTextAreaElement>(a.css)
    if (!el) return
    el.focus()
    for (let i = 1; i <= a.wert.length; i++) {
      if (abbruch()) return
      setzeWert(el, a.wert.slice(0, i))
      await pause(ANSCHLAG)
    }
    return
  }
  if (a.art === 'waehlen') {
    // Auswahlfelder sind keine <select>: Der Knopf oeffnet eine Liste, aus der ein Eintrag geklickt wird.
    const trigger = document.querySelector<HTMLElement>(a.css)
    if (!trigger) return
    trigger.click()
    await pause(320)
    if (abbruch()) return
    const eintraege = Array.from(document.querySelectorAll<HTMLElement>('[role="option"]'))
    const treffer = a.option ? eintraege.find((o) => (o.textContent ?? '').includes(a.option!)) : eintraege[0]
    treffer?.click()
    await pause(260)
    return
  }
  document.querySelector<HTMLElement>(a.css)?.click()
  await pause(260)
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
  // Nachfuehren erst nach dem Rendern: waehrend des Renderns ist ein Ref tabu. Dieser Effekt steht
  // bewusst VOR dem Mess-Effekt — React fuehrt sie in der Reihenfolge ihrer Deklaration aus, der
  // Merker ist unten also schon auf dem neuesten Stand.
  useLayoutEffect(() => {
    zielRef.current = target
  })

  useLayoutEffect(() => {
    // Den Merker mit zuruecksetzen: sonst gilt beim naechsten Anlauf dieselbe Messung als „unveraendert",
    // und der Ausschnitt bliebe aus, obwohl das Ziel wieder da ist.
    letzte.current = ''
    // Gemessen wird der echte Seitenzustand — genau der Fall, fuer den ein Effekt da ist.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (!aktiv) { setRect(null); return }
    let frame = 0
    const messen = () => {
      const el = zielFinden(zielRef.current, imDialog)
      const r = el?.getBoundingClientRect()
      // Breite 0 heisst: nicht da oder nicht sichtbar (auch jsdom misst so) — dann kein Ausschnitt.
      // Auf ganze Pixel runden: Waehrend Animationen wackeln die Messwerte um Bruchteile, und jedes Wackeln
      // waere ein neues Rendern — das Fenster zittert dann sichtbar.
      const neu: Rect | null = r && r.width > 0 && r.height > 0
        ? { top: Math.round(r.top), left: Math.round(r.left), width: Math.round(r.width), height: Math.round(r.height) }
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

/** Ist die Seite des Schritts offen? Detailseiten zaehlen mit: /grunddaten/klassen/7 liegt auf /grunddaten/klassen. */
function aufSeite(pfad: string, route?: string): boolean {
  if (!route) return true
  return route === '/' ? pfad === '/' : pfad === route || pfad.startsWith(`${route}/`)
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
  const [aktuelleAktion, setAktuelleAktion] = useState<string | null>(null)
  const [feldIndex, setFeldIndex] = useState(0)
  // Wer neben das Ziel klickt, will etwas anderes tun. Dann faellt die Sperre fuer diesen Schritt — der
  // Rundgang bleibt sichtbar und zeigt weiter aufs Ziel, haelt aber niemanden mehr fest.
  const [freigegeben, setFreigegeben] = useState(false)
  const panel = useRef<HTMLDivElement>(null)

  const current = TOUR_STEPS[step]
  const letzter = step === TOUR_STEPS.length - 1
  // != null statt !== null: der Server laesst leere Felder weg (siehe Kopfkommentar).
  const sichtbar = planning && !!me.data && me.data.tourCompletedAt == null && !geschlossen

  // Steht der Schritt auf einer anderen Seite (etwa „Berechnen" auf dem Entwurf), fuehrt der Rundgang zuerst
  // dorthin: Ziel ist dann der Eintrag der Seitenleiste, nicht der Knopf, den es hier gar nicht gibt.
  const hinfuehren = !!current.route && !aufSeite(location.pathname, current.route) && current.weiter !== 'route'
  // Waehrend des Schreibens zeigt der Rundgang auf das Feld, danach auf „Speichern".
  // Beim Ausfuellen wandert die Hervorhebung mit: immer auf das Feld, das gerade geschrieben wird.
  const feldZiel = current.vorbereiten && !getippt ? current.vorbereiten[Math.min(feldIndex, current.vorbereiten.length - 1)] : null
  // Ein reiner Erklaerschritt hat nichts auf der Seite zum Anklicken — dann auch nichts hervorheben und
  // nichts verdunkeln. Sonst leuchtet ein Knopf, bei dem ein Klick gar nichts bewirkt.
  const nurLesen = current.weiter === 'lesen'
  const ziel = useMemo(
    () => (hinfuehren ? ({ css: `a[href="${current.route}"]` } as const)
      : feldZiel ? ({ css: feldZiel.css } as const)
        : current.target),
    [hinfuehren, current.route, current.target, feldZiel],
  )
  // Im Dialog nur suchen, solange der Schritt nicht ohnehin erst zur Seite fuehrt.
  const imDialog = !!current.imDialog && !hinfuehren
  const rect = useZielRect(ziel, sichtbar && !nurLesen, imDialog)

  const beenden = useCallback(() => {
    setGeschlossen(true)
    // Nach dem Beenden wieder auf Schritt 1: „Rundgang erneut zeigen" soll vorne anfangen.
    setStep(0)
    setTour.mutate({ completed: true })
  }, [setTour])

  // „Rundgang erneut zeigen" loescht den Zeitpunkt am Konto. Stand der Rundgang zu dem Zeitpunkt noch offen
  // (etwa auf dem letzten Schritt), muss er trotzdem vorne anfangen — sonst bleibt er stehen, wo er war.
  const vorherFertig = useRef<string | null | undefined>(undefined)
  useEffect(() => {
    const jetzt = me.data?.tourCompletedAt ?? null
    if (vorherFertig.current !== undefined && vorherFertig.current != null && jetzt == null) {
      setStep(0)
      setTippt(false)
      setGetippt(false)
      setAktuelleAktion(null)
      setFeldIndex(0)
      setGeschlossen(false)
    }
    vorherFertig.current = jetzt
  }, [me.data?.tourCompletedAt])

  /** Zu Schritt n; die Schreibanimation faengt dort von vorn an. */
  const geheZu = useCallback((n: number) => {
    setStep(Math.max(0, Math.min(n, TOUR_STEPS.length - 1)))
    setFreigegeben(false)
    setTippt(false)
    setGetippt(false)
    setAktuelleAktion(null)
    setFeldIndex(0)
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
    // Der Suchlauf gehoert zum Schritt, nicht zum Rendern: bei jedem Schritt- und Seitenwechsel
    // faengt er neu an und laeuft nach 1,5 s von selbst aus.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSuchtNoch(true)
    const t = window.setTimeout(() => setSuchtNoch(false), 1500)
    return () => window.clearTimeout(t)
  }, [step, location.pathname, getippt])

  // Navigationsschritt: Die Seite ist offen — weiter zum Handgriff, der auf dieser Seite dran ist.
  // Keine Wartezeit, die von selbst ablaeuft: Jede Seite hat einen eigenen Schritt, der auf ihr stattfindet.
  useEffect(() => {
    if (!sichtbar || current.weiter !== 'route' || !aufSeite(location.pathname, current.route)) return
    const t = weiterNachRuhe(step)
    return () => window.clearTimeout(t)
  }, [sichtbar, current.weiter, current.route, location.pathname, step, weiterNachRuhe])

  // Klickschritt: am Ziel selbst horchen. Der Klick zaehlt, egal was er ausloest — Dialog, Berechnung, Wechsel.
  useEffect(() => {
    // Auch waehrend des Ausfuellens horchen: Wer frueher auf „Speichern" klickt, soll trotzdem weiterkommen.
    if (!sichtbar || current.weiter !== 'klick' || hinfuehren) return
    const el = zielFinden(current.target, imDialog)
    if (!el) return
    const meiner = step
    const onClick = () => weiterNachRuhe(meiner)
    el.addEventListener('click', onClick)
    return () => el.removeEventListener('click', onClick)
    // `rect` haengt mit drin, damit der Horcher nachgezogen wird, sobald das Ziel auftaucht.
  }, [sichtbar, current.weiter, current.target, hinfuehren, imDialog, rect, step, weiterNachRuhe])

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

  // Vorbereitung: Der Rundgang fuellt die Felder selbst — tippt, waehlt aus, drueckt Hilfsknoepfe. Bricht ab,
  // sobald der Schritt wechselt oder der Rundgang endet; halb Getipptes bleibt stehen und laesst sich von Hand
  // weiterschreiben. Abgeschickt wird nie automatisch.
  useEffect(() => {
    if (!sichtbar || !current.vorbereiten || getippt || hinfuehren) return
    const schritte = current.vorbereiten
    let abbruch = false
    void (async () => {
      await pause(420)   // Dialog oder Seite baut sich gerade erst auf
      if (abbruch || !document.querySelector(schritte[0].css)) return
      setTippt(true)
      for (let i = 0; i < schritte.length; i++) {
        if (abbruch) return
        const a = schritte[i]
        setFeldIndex(i)
        setAktuelleAktion(a.was)
        await pause(260)             // kurz auf das Feld zeigen, bevor geschrieben wird
        await fuehreAus(a, () => abbruch)
        // Kurzes gruenes Aufleuchten: „dieses Feld ist fertig".
        const el = document.querySelector<HTMLElement>(a.css)
        el?.classList.add('tour-feld-fertig')
        await pause(520)
        el?.classList.remove('tour-feld-fertig')
      }
      if (abbruch) return
      setTippt(false)
      setAktuelleAktion(null)
      setGetippt(true)
    })()
    return () => { abbruch = true }
  }, [sichtbar, step, current.vorbereiten, getippt, hinfuehren])

  // Ziel ins Bild holen, sonst zeigt der Rundgang auf etwas, das ausserhalb des Fensters liegt.
  useEffect(() => {
    if (!sichtbar || nurLesen) return
    zielFinden(ziel, imDialog)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [sichtbar, nurLesen, ziel, imDialog])

  // Das Ziel selbst faerben: violett und pulsierend, genau auf seiner eigenen Flaeche. Die Klasse sitzt am
  // echten Bedienelement und verschwindet mit dem Schritt — nichts bleibt zurueck, auch nicht beim Abbruch.
  const [rundung, setRundung] = useState<string | null>(null)
  useEffect(() => {
    // Die Rundung wird am echten Bedienelement abgelesen — ohne DOM gibt es sie nicht.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (!sichtbar || nurLesen) { setRundung(null); return }
    const el = zielFinden(ziel, imDialog)
     
    if (!el) { setRundung(null); return }
    el.classList.add('tour-ziel-aktiv')
    setRundung(getComputedStyle(el).borderRadius || null)
    return () => el.classList.remove('tour-ziel-aktiv')
  }, [sichtbar, nurLesen, ziel, imDialog, rect])

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
  const sperren = loch !== null && !tippt && !nurLesen && !freigegeben
  const pfeilQuer = !!loch && window.innerWidth - (loch.left + loch.width) > PFEIL_PLATZ

  // Steht ein Dialog der Anwendung offen, weicht das Erklaerfenster ihm aus: daneben, wo Platz ist.
  const dialogRect = typeof document !== 'undefined'
    ? document.querySelector('[role="dialog"]:not([aria-label="Rundgang"]), [role="alertdialog"]')?.getBoundingClientRect() ?? null
    : null

  // Erklaerfenster unter das Ziel, sonst darueber, sonst unten rechts.
  const platzUnten = loch ? window.innerHeight - (loch.top + loch.height) : 0
  const platzOben = loch ? loch.top : 0
  const links = loch ? Math.max(RAND, Math.min(loch.left, window.innerWidth - PANEL_BREITE - RAND)) : 0
  const lage: 'unten' | 'oben' | 'ecke' = loch && Math.max(platzUnten, platzOben) > PANEL_PLATZ
    ? (platzUnten > PANEL_PLATZ ? 'unten' : 'oben')
    : 'ecke'
  // Neben dem Dialog: rechts, wenn dort Platz ist, sonst links; senkrecht an seiner Oberkante ausgerichtet.
  const nebenDialog: CSSProperties | null = dialogRect && (dialogRect.width > 0)
    ? (window.innerWidth - dialogRect.right > PANEL_BREITE + 2 * RAND
      ? { top: Math.max(RAND, dialogRect.top), left: dialogRect.right + RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' }
      : dialogRect.left > PANEL_BREITE + 2 * RAND
        ? { top: Math.max(RAND, dialogRect.top), left: dialogRect.left - PANEL_BREITE - RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' }
        : { right: RAND, bottom: RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' })
    : null

  // Ein Erklaerschritt ohne Ziel steht unten rechts: Er spricht ueber die ganze Seite und soll sie nicht
  // verdecken.
  const ecke: CSSProperties | null = nurLesen && !nebenDialog
    ? { right: RAND, bottom: RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' }
    : null

  const panelStil: CSSProperties = ecke ?? nebenDialog ?? (loch && lage === 'unten'
    ? { top: loch.top + loch.height + 28, left: links, maxHeight: platzUnten - 2 * RAND, overflowY: 'auto' }
    : loch && lage === 'oben'
      ? { bottom: window.innerHeight - loch.top + 28, left: links, maxHeight: platzOben - 2 * RAND, overflowY: 'auto' }
      : { right: RAND, bottom: RAND, maxHeight: `calc(100vh - ${2 * RAND}px)`, overflowY: 'auto' })
  const zipfelLinks = loch ? Math.max(14, Math.min(loch.left + loch.width / 2 - links - 6, PANEL_BREITE - 26)) : 0
  // Solange nach dem Ziel gesucht wird, bleibt das Fenster durchsichtig statt kurz in der Ecke aufzublitzen.
  // Nur warten, wenn es ueberhaupt ein Ziel zu finden gibt. Ein Erklaerschritt hat keins — der blieb sonst
  // bis zu anderthalb Sekunden durchsichtig, und der Wechsel wirkte wie ein Aussetzer.
  const sucht = !nurLesen && loch === null && suchtNoch

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
          <div className="absolute" onClick={() => setFreigegeben(true)} style={{ pointerEvents: 'auto', top: 0, left: 0, right: 0, height: loch.top }} />
          <div className="absolute" onClick={() => setFreigegeben(true)} style={{ pointerEvents: 'auto', top: loch.top + loch.height, left: 0, right: 0, bottom: 0 }} />
          <div className="absolute" onClick={() => setFreigegeben(true)} style={{ pointerEvents: 'auto', top: loch.top, left: 0, width: loch.left, height: loch.height }} />
          <div className="absolute" onClick={() => setFreigegeben(true)} style={{ pointerEvents: 'auto', top: loch.top, left: loch.left + loch.width, right: 0, height: loch.height }} />
        </div>
      )}

      {/* Der Pfeil wippt auf das Ziel zu: rechts daneben, wenn dort Platz ist, sonst darunter. */}
      {loch && !sucht && (
        <div
          aria-hidden
          className={`print-hide fixed z-[60] text-primary ${pfeilQuer ? 'tour-pfeil-quer' : 'tour-pfeil-hoch'}`}
          style={{
            transition: 'top .16s ease-out, left .16s ease-out',
            ...(pfeilQuer
              ? { top: loch.top + loch.height / 2 - 17, left: loch.left + loch.width + 6 }
              : { top: loch.top + loch.height + 6, left: loch.left + loch.width / 2 - 17 }),
          }}
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
        // Gleiten statt springen: Beim Schrittwechsel und beim Scrollen wandert das Fenster weich mit.
        style={{ ...panelStil, pointerEvents: 'auto', opacity: sucht ? 0 : 1, transition: 'top .16s ease-out, left .16s ease-out, bottom .16s ease-out, opacity .12s linear' }}
      >
        {lage !== 'ecke' && !nebenDialog && !ecke && (
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
            : <MousePointerClick className="mt-0.5 size-4 shrink-0" aria-hidden />}
          <span>
            {tippt
              ? `Der Rundgang füllt das Formular aus${aktuelleAktion ? ` — ${aktuelleAktion}` : ''} …`
              : hinfuehren ? 'Öffnen Sie zuerst die Seite, auf der das passiert.' : current.action}
            {freigegeben && <span className="mt-1 block font-normal text-muted-foreground">Die Sperre ist aufgehoben — Sie können die Seite frei bedienen. Der Rundgang wartet, bis Sie das hervorgehobene Element anklicken, oder springt mit „Überspringen" weiter.</span>}
            {loch === null && !suchtNoch && !nurLesen && <span className="mt-1 block font-normal text-muted-foreground">Das Element ist gerade nicht da — es erscheint erst, wenn die Seite es anbietet. Mit „Überspringen" geht es zum nächsten Thema.</span>}
          </span>
        </p>

        {/* Hoechstens drei Knoepfe: links der Ausstieg, rechts zurueck und vorwaerts. Bei einem Erklaerschritt
            heisst der vordere „Weiter", bei einem Handgriff „Überspringen" — beides ist derselbe Platz. */}
        <div className="mt-4 flex items-center justify-between gap-2">
          <Button variant="ghost" size="sm" onClick={beenden}>Beenden</Button>
          <div className="flex gap-2">
            {step > 0 && <Button variant="outline" size="sm" onClick={() => geheZu(step - 1)}>Zurück</Button>}
            {letzter
              ? <Button size="sm" onClick={beenden}>Fertig</Button>
              : current.weiter === 'lesen'
                // Reine Erklaerschritte haben nichts zum Anklicken auf der Seite — hier fuehrt „Weiter" weiter.
                ? <Button size="sm" className="tour-ziel" onClick={() => geheZu(step + 1)}>Weiter</Button>
                : <Button variant="ghost" size="sm" className="text-foreground-subtle" onClick={() => geheZu(naechsteSeite())}>Überspringen</Button>}
          </div>
        </div>
      </div>
    </>,
    document.body,
  )
}
