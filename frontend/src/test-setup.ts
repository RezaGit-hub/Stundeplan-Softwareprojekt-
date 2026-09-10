// Läuft vor jeder Testdatei (vitest.config.ts → setupFiles).
//
// Für die reinen Logiktests (*.test.ts, Umgebung `node`) passiert hier nichts: ohne `document` bleibt
// alles unangetastet. Für die Komponententests (*.test.tsx, Umgebung jsdom) räumt es nach jedem Fall auf
// und legt die zwei Browser-Fähigkeiten nach, die jsdom nicht mitbringt und die Radix (Dialog, Popover,
// Select) beim Öffnen aufruft — ohne sie schlägt jeder Dialogtest mit „is not a function" fehl.
import { afterEach } from 'vitest'

if (typeof document !== 'undefined') {
  const { cleanup } = await import('@testing-library/react')
  afterEach(cleanup)

  // jsdom kennt kein Layout: Radix fragt es beim Positionieren von Popover und Select.
  if (!window.matchMedia) {
    window.matchMedia = (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener: () => {}, removeListener: () => {},
      addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
    }) as MediaQueryList
  }
  if (!Element.prototype.hasPointerCapture) {
    Element.prototype.hasPointerCapture = () => false
    Element.prototype.setPointerCapture = () => {}
    Element.prototype.releasePointerCapture = () => {}
  }
  if (!Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = () => {}
  }
  if (!window.ResizeObserver) {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  }
}
